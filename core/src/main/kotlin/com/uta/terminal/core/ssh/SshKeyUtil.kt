package com.uta.terminal.core.ssh

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.security.MessageDigest
import java.util.Base64

/**
 * 秘密鍵テキスト（PEM/OpenSSH 形式）から公開鍵情報を導出するユーティリティ。
 * 鍵ストアに登録した鍵の公開鍵コピー（authorized_keys 用）と識別表示に使う。
 */
object SshKeyUtil {

    /**
     * 導出した公開鍵情報。
     * @property keyType 例 "ssh-ed25519"・"ssh-rsa"
     * @property authorizedKey authorized_keys に貼れる 1 行（コメント無し）
     * @property fingerprint OpenSSH 互換の "SHA256:" + base64(no-pad)
     */
    data class PublicKeyInfo(
        val keyType: String,
        val authorizedKey: String,
        val fingerprint: String,
    )

    /**
     * 秘密鍵から公開鍵情報を導出する。パスフレーズ付き鍵は [passphrase] が必要。
     * 解析できない鍵・パスフレーズ不一致は例外を投げる（呼び出し側で失敗を許容すること）。
     */
    fun derivePublicKey(pem: String, passphrase: String?): PublicKeyInfo {
        // SSHClient は鍵ファイル形式のファクトリ（Config）を持つローダとしてだけ使う（接続しない）。
        val client = SSHClient(DefaultConfig())
        try {
            val provider = if (passphrase.isNullOrEmpty()) {
                client.loadKeys(pem, null, null)
            } else {
                client.loadKeys(pem, null, PasswordUtils.createOneOff(passphrase.toCharArray()))
            }
            val pub = provider.public
            val keyType = KeyType.fromKey(pub).toString()
            val blob = Buffer.PlainBuffer().putPublicKey(pub).compactData
            val b64 = Base64.getEncoder().encodeToString(blob)
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            val fp = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
            return PublicKeyInfo(keyType = keyType, authorizedKey = "$keyType $b64", fingerprint = fp)
        } finally {
            runCatching { client.close() }
        }
    }
}
