package com.uta.terminal.data

import com.uta.terminal.core.ssh.SshAuth
import com.uta.terminal.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** 認証種別（UI 表示・分岐用）。 */
enum class AuthKind { PASSWORD, KEY }

/** UI が扱う保存済みプロファイル（秘密は含まない）。 */
data class HostProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    val authKind: AuthKind,
)

/**
 * 接続プロファイルの保存・取得。非秘密情報は Room、秘密は [SecretStore] で暗号化して同じ行に保持する。
 * 一覧には秘密を出さず、接続時に [resolveAuth] で復号して [SshAuth] を組み立てる。
 */
class ProfileRepository(private val dao: ProfileDao) {

    val profiles: Flow<List<HostProfile>> =
        dao.observeAll().map { list -> list.map { it.toHostProfile() } }

    suspend fun save(
        label: String,
        host: String,
        port: Int,
        username: String,
        auth: SshAuth,
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val kind: String
        val secretPlain: String
        val passPlain: String?
        when (auth) {
            is SshAuth.Password -> { kind = "PASSWORD"; secretPlain = auth.password; passPlain = null }
            is SshAuth.PrivateKey -> { kind = "KEY"; secretPlain = auth.pem; passPlain = auth.passphrase }
        }
        val sealedSecret = SecretStore.encrypt(secretPlain.toByteArray(Charsets.UTF_8))
        val sealedPass = passPlain?.takeIf { it.isNotEmpty() }
            ?.let { SecretStore.encrypt(it.toByteArray(Charsets.UTF_8)) }
        // 新規は一覧の先頭に出す（既存の最小 sortOrder より 1 小さくする）。
        val sortOrder = (dao.minSortOrder() ?: 0) - 1
        dao.upsert(
            ProfileEntity(
                id = id,
                label = label,
                host = host,
                port = port,
                username = username,
                authKind = kind,
                secretIv = sealedSecret.iv,
                secretCipher = sealedSecret.ciphertext,
                passIv = sealedPass?.iv,
                passCipher = sealedPass?.ciphertext,
                createdAt = System.currentTimeMillis(),
                sortOrder = sortOrder,
            ),
        )
        id
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    /** 一覧の並び順を永続化する（渡した id 順に sortOrder=0,1,2… を振る）。 */
    suspend fun reorder(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        dao.applyOrder(orderedIds)
    }

    /** 保存済み秘密を復号して認証情報を組み立てる。存在しなければ null。 */
    suspend fun resolveAuth(id: String): SshAuth? = withContext(Dispatchers.IO) {
        val e = dao.getById(id) ?: return@withContext null
        val secret = String(SecretStore.decrypt(e.secretIv, e.secretCipher), Charsets.UTF_8)
        when (e.authKind) {
            "PASSWORD" -> SshAuth.Password(secret)
            "KEY" -> {
                val pass = if (e.passIv != null && e.passCipher != null) {
                    String(SecretStore.decrypt(e.passIv, e.passCipher), Charsets.UTF_8)
                } else {
                    null
                }
                SshAuth.PrivateKey(secret, pass)
            }
            else -> null
        }
    }
}

private fun ProfileEntity.toHostProfile() = HostProfile(
    id = id,
    label = label,
    host = host,
    port = port,
    username = username,
    authKind = if (authKind == "KEY") AuthKind.KEY else AuthKind.PASSWORD,
)
