package com.uta.terminal.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 保存された接続プロファイルの永続化行。
 * 秘密（パスワード or 秘密鍵 PEM、任意でパスフレーズ）は [com.uta.terminal.security.SecretStore] で
 * 暗号化した IV＋暗号文だけを保持する（平文は保存しない）。
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val username: String,
    /** "PASSWORD" または "KEY"。 */
    val authKind: String,
    val secretIv: ByteArray,
    val secretCipher: ByteArray,
    /** パスフレーズ（秘密鍵の場合のみ・任意）。無ければ null。 */
    val passIv: ByteArray?,
    val passCipher: ByteArray?,
    val createdAt: Long,
)
