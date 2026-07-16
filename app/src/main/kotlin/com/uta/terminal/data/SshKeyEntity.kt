package com.uta.terminal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 鍵ストアに登録された名前付き SSH 秘密鍵の永続化行。
 * 秘密（PEM、任意でパスフレーズ）は [com.uta.terminal.security.SecretStore] で暗号化した
 * IV＋暗号文だけを保持する。公開鍵・フィンガープリントは秘密ではないので平文で持つ
 * （導出に失敗した鍵は null のまま登録を許す）。
 */
@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val secretIv: ByteArray,
    val secretCipher: ByteArray,
    /** パスフレーズ（任意）。無ければ null。 */
    val passIv: ByteArray?,
    val passCipher: ByteArray?,
    /** 例 "ssh-ed25519"。導出失敗時 null。 */
    val keyType: String?,
    /** authorized_keys に貼れる 1 行。導出失敗時 null。 */
    val publicKey: String?,
    /** "SHA256:..."。導出失敗時 null。 */
    val fingerprint: String?,
    val createdAt: Long,
)
