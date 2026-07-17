package com.uta.tunnel.core.model

/**
 * 保存された接続プロファイル（アドレス帳の 1 エントリ）。
 * 秘密（パスワード・鍵）そのものは保持せず、`:app` 側の暗号ストア参照（[AuthMethod]）だけを持つ。
 */
data class ConnectionProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int = DEFAULT_SSH_PORT,
    val username: String,
    val auth: AuthMethod,
) {
    companion object {
        const val DEFAULT_SSH_PORT = 22
    }
}

/**
 * 認証方式。MVP は公開鍵（ed25519/RSA）とパスワードのみ。
 * 秘密の実体は `:app` の Keystore 暗号ストアにあり、ここでは参照キーだけを持つ。
 */
sealed interface AuthMethod {
    /** Keystore 暗号ストア上の秘密鍵を指す参照キー。 */
    data class PublicKey(val keyRef: String) : AuthMethod

    /** Keystore 暗号ストア上のパスワードを指す参照キー。 */
    data class Password(val secretRef: String) : AuthMethod
}

/** 接続先を一意に識別する host:port（TOFU の照合キーにも使う）。 */
data class HostAddress(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

/**
 * TOFU で保存する既知ホスト鍵エントリ。
 * [fingerprint] は OpenSSH 互換の SHA256 フィンガープリント文字列。
 */
data class HostKeyEntry(
    val address: HostAddress,
    val keyType: String,
    val fingerprint: String,
)
