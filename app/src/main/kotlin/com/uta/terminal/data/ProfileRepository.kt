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

/** UI が扱う保存済みプロファイル（秘密は含まない）。KEY のとき [keyId] が鍵ストアを指す。 */
data class HostProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    val authKind: AuthKind,
    val keyId: String? = null,
)

/** プロファイルに保存する認証情報の指定。 */
sealed interface AuthInput {
    /** パスワード（行内に暗号化保存）。 */
    data class Password(val password: String) : AuthInput

    /** 鍵ストアの鍵参照（秘密は ssh_keys 側にあり、行内には持たない）。 */
    data class KeyRef(val keyId: String) : AuthInput
}

/**
 * 接続プロファイルの保存・取得。非秘密情報は Room、パスワードは [SecretStore] で暗号化して
 * 同じ行に保持する。鍵は鍵ストア（[SshKeyRepository]）への参照（keyId）で持つ。
 * 一覧には秘密を出さず、接続時に [resolveAuth] で復号して [SshAuth] を組み立てる。
 */
class ProfileRepository(
    private val dao: ProfileDao,
    private val keyRepository: SshKeyRepository,
) {

    val profiles: Flow<List<HostProfile>> =
        dao.observeAll().map { list -> list.map { it.toHostProfile() } }

    suspend fun save(
        label: String,
        host: String,
        port: Int,
        username: String,
        auth: AuthInput,
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val sealed = seal(auth)
        // 新規は一覧の先頭に出す（既存の最小 sortOrder より 1 小さくする）。
        val sortOrder = (dao.minSortOrder() ?: 0) - 1
        dao.upsert(
            ProfileEntity(
                id = id,
                label = label,
                host = host,
                port = port,
                username = username,
                authKind = sealed.kind,
                secretIv = sealed.secretIv,
                secretCipher = sealed.secretCipher,
                passIv = null,
                passCipher = null,
                createdAt = System.currentTimeMillis(),
                sortOrder = sortOrder,
                keyId = sealed.keyId,
            ),
        )
        id
    }

    /** 保存済みプロファイルを 1 件取得する（秘密は含まない）。 */
    suspend fun get(id: String): HostProfile? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toHostProfile()
    }

    /**
     * プロファイルを更新する。[auth] が null のときは認証情報を変更せず、
     * 非秘密情報だけを更新する（編集画面で認証を触らなかった場合）。
     */
    suspend fun update(
        id: String,
        label: String,
        host: String,
        port: Int,
        username: String,
        auth: AuthInput?,
    ) = withContext(Dispatchers.IO) {
        val e = dao.getById(id) ?: return@withContext
        val updated = if (auth == null) {
            e.copy(label = label, host = host, port = port, username = username)
        } else {
            val sealed = seal(auth)
            e.copy(
                label = label,
                host = host,
                port = port,
                username = username,
                authKind = sealed.kind,
                secretIv = sealed.secretIv,
                secretCipher = sealed.secretCipher,
                passIv = null,
                passCipher = null,
                keyId = sealed.keyId,
            )
        }
        dao.upsert(updated)
    }

    /** プロファイルを複製する（ラベルに「(コピー)」を付け、一覧の先頭に置く）。秘密ごと複製される。 */
    suspend fun duplicate(id: String) = withContext(Dispatchers.IO) {
        val e = dao.getById(id) ?: return@withContext
        dao.upsert(
            e.copy(
                id = UUID.randomUUID().toString(),
                label = "${e.label} (コピー)",
                createdAt = System.currentTimeMillis(),
                sortOrder = (dao.minSortOrder() ?: 0) - 1,
            ),
        )
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    /** 一覧の並び順を永続化する（渡した id 順に sortOrder=0,1,2… を振る）。 */
    suspend fun reorder(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        dao.applyOrder(orderedIds)
    }

    /** 保存済み秘密を復号して認証情報を組み立てる。存在しなければ null。 */
    suspend fun resolveAuth(id: String): SshAuth? = withContext(Dispatchers.IO) {
        val e = dao.getById(id) ?: return@withContext null
        when (e.authKind) {
            "PASSWORD" -> {
                val secret = String(SecretStore.decrypt(e.secretIv, e.secretCipher), Charsets.UTF_8)
                SshAuth.Password(secret)
            }
            "KEY" -> {
                val keyId = e.keyId
                if (keyId != null) {
                    keyRepository.resolveAuth(keyId)
                } else {
                    // 昇格前のインライン鍵（旧形式）フォールバック。
                    resolveInlineKey(e)
                }
            }
            else -> null
        }
    }

    /**
     * 旧形式（行内に PEM を暗号化保存）の鍵プロファイルを鍵ストアへ昇格する。
     * アプリ起動時に一度呼ぶ。個々の失敗はスキップし、インラインのまま動かし続ける。
     */
    suspend fun promoteInlineKeys() = withContext(Dispatchers.IO) {
        for (e in dao.inlineKeyProfiles()) {
            runCatching {
                val auth = resolveInlineKey(e) ?: return@runCatching
                val keyId = keyRepository.add("${e.label} の鍵", auth.pem, auth.passphrase)
                // 参照へ切り替え、行内の秘密は空にする（以後は鍵ストア側が正）。
                dao.upsert(
                    e.copy(
                        keyId = keyId,
                        secretIv = ByteArray(0),
                        secretCipher = ByteArray(0),
                        passIv = null,
                        passCipher = null,
                    ),
                )
            }
        }
    }

    private fun resolveInlineKey(e: ProfileEntity): SshAuth.PrivateKey? {
        if (e.secretCipher.isEmpty()) return null
        val pem = String(SecretStore.decrypt(e.secretIv, e.secretCipher), Charsets.UTF_8)
        val pass = if (e.passIv != null && e.passCipher != null) {
            String(SecretStore.decrypt(e.passIv, e.passCipher), Charsets.UTF_8)
        } else {
            null
        }
        return SshAuth.PrivateKey(pem, pass)
    }
}

/** 暗号化済みの認証情報（Room 行に入れる形）。 */
private class SealedAuth(
    val kind: String,
    val secretIv: ByteArray,
    val secretCipher: ByteArray,
    val keyId: String?,
)

/** [AuthInput] を行形式に変換する（パスワードは [SecretStore] で暗号化、鍵は参照のみ）。 */
private fun seal(auth: AuthInput): SealedAuth = when (auth) {
    is AuthInput.Password -> {
        val sealed = SecretStore.encrypt(auth.password.toByteArray(Charsets.UTF_8))
        SealedAuth(kind = "PASSWORD", secretIv = sealed.iv, secretCipher = sealed.ciphertext, keyId = null)
    }
    is AuthInput.KeyRef -> {
        SealedAuth(kind = "KEY", secretIv = ByteArray(0), secretCipher = ByteArray(0), keyId = auth.keyId)
    }
}

private fun ProfileEntity.toHostProfile() = HostProfile(
    id = id,
    label = label,
    host = host,
    port = port,
    username = username,
    authKind = if (authKind == "KEY") AuthKind.KEY else AuthKind.PASSWORD,
    keyId = keyId,
)
