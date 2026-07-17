package com.uta.tunnel.data

import androidx.room.Entity

/**
 * TOFU で信頼した既知ホスト鍵の永続化行（`~/.ssh/known_hosts` 相当）。
 *
 * フィンガープリントは公開鍵のハッシュであり秘密ではないため、[SshKeyEntity] と違い
 * 暗号化せず平文で保持する。主キーは接続先を一意にする host + port。
 */
@Entity(tableName = "host_keys", primaryKeys = ["host", "port"])
data class HostKeyEntity(
    val host: String,
    val port: Int,
    /** 例 "ssh-ed25519"。 */
    val keyType: String,
    /** "SHA256:..."（OpenSSH 互換）。 */
    val fingerprint: String,
    /** 初回に信頼した時刻。一覧の並び順に使う。 */
    val createdAt: Long,
)
