package com.uta.tunnel.core.ssh

import com.uta.tunnel.core.model.HostAddress
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/** 認証方式（秘密の実体を保持する。UI で入力し、将来は Keystore から復号して渡す）。 */
sealed interface SshAuth {
    data class Password(val password: String) : SshAuth

    /** PEM/OpenSSH 形式の秘密鍵テキスト。パスフレーズ無しなら null。 */
    data class PrivateKey(val pem: String, val passphrase: String?) : SshAuth
}

/** 1 接続を張るためのパラメータ。cols/rows は初回 PTY サイズ（後で resize で更新）。 */
data class SshConnectionRequest(
    val host: String,
    val port: Int,
    val username: String,
    val auth: SshAuth,
    val cols: Int,
    val rows: Int,
)

/**
 * sshj で 1 本の SSH シェルセッションを張り、PTY 上のシェルを駆動する（Android 非依存）。
 *
 * - ホスト鍵は [TofuHostKeyVerifier] で TOFU 検証（初回保存・不一致で例外）。
 * - stdout/stderr は専用スレッドで読み、[setOnOutput] のコールバックへ渡す
 *   （**コールバックは読み取りスレッド上で呼ばれる**。UI 更新側でメインスレッドへ移すこと）。
 * - [connect] はブロッキング。**必ずメインスレッド以外から呼ぶ**。
 */
class SshShellSession(private val verifier: TofuHostKeyVerifier) {

    @Volatile private var onOutput: ((ByteArray, Int) -> Unit)? = null
    @Volatile private var onClosed: ((Throwable?) -> Unit)? = null

    private var ssh: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var stdin: OutputStream? = null

    @Volatile private var closed = false
    @Volatile private var closedNotified = false
    private val writeLock = Any()

    fun setOnOutput(cb: (ByteArray, Int) -> Unit) { onOutput = cb }
    fun setOnClosed(cb: (Throwable?) -> Unit) { onClosed = cb }

    /** ブロッキング接続。認証・PTY・シェル開始まで行い、読み取りスレッドを起動する。 */
    fun connect(req: SshConnectionRequest) {
        val config = DefaultConfig().apply { keepAliveProvider = KeepAliveProvider.KEEP_ALIVE }
        val client = SSHClient(config)
        ssh = client
        client.addHostKeyVerifier(TofuVerifierAdapter())
        client.connect(req.host, req.port)
        runCatching { client.connection.keepAlive.keepAliveInterval = 30 }
        authenticate(client, req)

        val s = client.startSession()
        session = s
        s.allocatePTY("xterm-256color", req.cols, req.rows, 0, 0, emptyMap<PTYMode, Int>())
        val sh = s.startShell()
        shell = sh
        stdin = sh.outputStream
        // stdout がチャネル EOF（＝シェル終了）を検知してクローズ判定する。
        // PTY では stderr は stdout にマージされ errorStream は EOF タイミングが不定なので、
        // errorStream はデータがあれば流すだけでクローズ判定はさせない。
        startReader(sh.inputStream, signalsClose = true)
        startReader(sh.errorStream, signalsClose = false)
    }

    private fun authenticate(client: SSHClient, req: SshConnectionRequest) {
        when (val a = req.auth) {
            is SshAuth.Password -> client.authPassword(req.username, a.password)
            is SshAuth.PrivateKey -> {
                val provider = if (a.passphrase.isNullOrEmpty()) {
                    client.loadKeys(a.pem, null, null)
                } else {
                    client.loadKeys(a.pem, null, PasswordUtils.createOneOff(a.passphrase.toCharArray()))
                }
                client.authPublickey(req.username, provider)
            }
        }
    }

    private fun startReader(stream: InputStream, signalsClose: Boolean) {
        val t = Thread({
            val buf = ByteArray(8192)
            try {
                while (!closed) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    if (n > 0) onOutput?.invoke(buf.copyOf(n), n)
                }
                if (signalsClose) notifyClosed(null)
            } catch (e: Throwable) {
                if (signalsClose && !closed) notifyClosed(e)
            }
        }, "ssh-reader")
        t.isDaemon = true
        t.start()
    }

    private fun notifyClosed(err: Throwable?) {
        synchronized(this) {
            if (closedNotified) return
            closedNotified = true
        }
        onClosed?.invoke(err)
    }

    /** ユーザー入力（stdin 相当）を書き出す。送出スレッドは呼び出し側任意。 */
    fun write(data: ByteArray, offset: Int, count: Int) {
        val os = stdin ?: return
        try {
            synchronized(writeLock) {
                os.write(data, offset, count)
                os.flush()
            }
        } catch (e: Throwable) {
            if (!closed) notifyClosed(e)
        }
    }

    /** PTY のウィンドウサイズ変更を通知する（SIGWINCH 相当）。 */
    fun resize(cols: Int, rows: Int) {
        runCatching { shell?.changeWindowDimensions(cols, rows, 0, 0) }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { shell?.close() }
        runCatching { session?.close() }
        runCatching { ssh?.disconnect() }
        runCatching { ssh?.close() }
    }

    /** OpenSSH 互換の SHA256 フィンガープリント（`SHA256:` + base64 no-pad）。 */
    private fun fingerprintOf(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** sshj の [HostKeyVerifier] を [TofuHostKeyVerifier] へ橋渡しする。 */
    private inner class TofuVerifierAdapter : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val keyType = KeyType.fromKey(key).toString()
            // 不一致なら HostKeyChangedException を投げて接続を中断する（TOFU）。
            verifier.verify(HostAddress(hostname, port), keyType, fingerprintOf(key))
            return true
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
}
