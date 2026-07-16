package com.uta.terminal.data

import com.uta.terminal.core.ssh.SshAuth
import com.uta.terminal.core.ssh.SshKeyUtil
import com.uta.terminal.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID

/** UI が扱う鍵ストアの 1 鍵（秘密は含まない）。 */
data class SshKeyItem(
    val id: String,
    val name: String,
    val keyType: String?,
    val publicKey: String?,
    val fingerprint: String?,
    val createdAt: Long,
    /** この鍵を参照している接続先の数。 */
    val usageCount: Int,
)

/** [SshKeyRepository.delete] の結果。 */
sealed interface KeyDeleteResult {
    data object Deleted : KeyDeleteResult
    data class InUse(val count: Int) : KeyDeleteResult
}

/**
 * 名前付き SSH 鍵の保存・取得（鍵ストア）。秘密（PEM/パスフレーズ）は [SecretStore] で
 * 暗号化して保持し、公開鍵・フィンガープリントは登録時に導出して平文で持つ。
 */
class SshKeyRepository(
    private val dao: SshKeyDao,
    private val profileDao: ProfileDao,
) {

    val keys: Flow<List<SshKeyItem>> =
        combine(dao.observeAll(), profileDao.observeKeyUsage()) { keys, usage ->
            val counts = usage.associate { it.keyId to it.count }
            keys.map { it.toItem(counts[it.id] ?: 0) }
        }

    /**
     * 鍵を登録する。公開鍵の導出に失敗しても登録は続行する（種別・公開鍵は null になる）。
     * @return 登録した鍵の id
     */
    suspend fun add(name: String, pem: String, passphrase: String?): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val info = runCatching { SshKeyUtil.derivePublicKey(pem, passphrase) }.getOrNull()
            val sealedPem = SecretStore.encrypt(pem.toByteArray(Charsets.UTF_8))
            val sealedPass = passphrase?.takeIf { it.isNotEmpty() }
                ?.let { SecretStore.encrypt(it.toByteArray(Charsets.UTF_8)) }
            dao.upsert(
                SshKeyEntity(
                    id = id,
                    name = name,
                    secretIv = sealedPem.iv,
                    secretCipher = sealedPem.ciphertext,
                    passIv = sealedPass?.iv,
                    passCipher = sealedPass?.ciphertext,
                    keyType = info?.keyType,
                    publicKey = info?.authorizedKey,
                    fingerprint = info?.fingerprint,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            id
        }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        dao.rename(id, name)
    }

    /** 使用中（参照プロファイルあり）の鍵は削除しない。 */
    suspend fun delete(id: String): KeyDeleteResult = withContext(Dispatchers.IO) {
        val used = profileDao.keyUsageCount(id)
        if (used > 0) {
            KeyDeleteResult.InUse(used)
        } else {
            dao.delete(id)
            KeyDeleteResult.Deleted
        }
    }

    /** 秘密を復号して認証情報を組み立てる。存在しなければ null。 */
    suspend fun resolveAuth(id: String): SshAuth.PrivateKey? = withContext(Dispatchers.IO) {
        val e = dao.getById(id) ?: return@withContext null
        val pem = String(SecretStore.decrypt(e.secretIv, e.secretCipher), Charsets.UTF_8)
        val pass = if (e.passIv != null && e.passCipher != null) {
            String(SecretStore.decrypt(e.passIv, e.passCipher), Charsets.UTF_8)
        } else {
            null
        }
        SshAuth.PrivateKey(pem, pass)
    }
}

private fun SshKeyEntity.toItem(usageCount: Int) = SshKeyItem(
    id = id,
    name = name,
    keyType = keyType,
    publicKey = publicKey,
    fingerprint = fingerprint,
    createdAt = createdAt,
    usageCount = usageCount,
)
