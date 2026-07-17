package com.uta.tunnel.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 保存された接続プロファイルの永続化行。
 * 秘密（パスワード or 秘密鍵 PEM、任意でパスフレーズ）は [com.uta.tunnel.security.SecretStore] で
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
    /** 一覧の表示順（昇順）。小さいほど上。手動並び替えで更新する。 */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /**
     * 鍵ストア（ssh_keys）の参照。authKind="KEY" で非 null なら秘密は鍵ストア側にあり、
     * 行内の secret 列は使わない（旧インライン鍵は起動時に鍵ストアへ昇格される）。
     */
    val keyId: String? = null,
    /** ピン留め（一覧上部に固定）。true が上。 */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
)
