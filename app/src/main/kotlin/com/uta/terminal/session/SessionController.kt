package com.uta.terminal.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.uta.terminal.service.SshForegroundService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uta.terminal.core.model.SessionId
import com.uta.terminal.core.model.SessionState
import com.uta.terminal.core.session.SessionInfo
import com.uta.terminal.core.session.SessionManager
import com.uta.terminal.core.ssh.HostKeyStore
import com.uta.terminal.core.ssh.SshConnectionRequest
import com.uta.terminal.core.ssh.SshShellSession
import com.uta.terminal.core.ssh.TofuHostKeyVerifier
import com.uta.terminal.ssh.SshSecurity
import com.uta.terminal.terminal.EmulatorHost
import com.uta.terminal.terminal.SshTransport
import java.util.UUID
import kotlin.concurrent.thread

/**
 * アクティブな SSH セッションのライフサイクルを保持する（MVP は 1 セッション）。
 *
 * [EmulatorHost] をここで生成・保持することで、画面遷移（NavHost）で TerminalScreen が
 * 破棄・再生成されてもスクロールバックと接続が維持される。UI は [host]/[state] を購読する。
 */
class SessionController(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val hostKeyStore: HostKeyStore,
) {
    private val main = Handler(Looper.getMainLooper())

    /** 現在のエミュレータ（未接続なら null）。Compose が購読して描画する。 */
    var host by mutableStateOf<EmulatorHost?>(null)
        private set

    var state by mutableStateOf<SessionState>(SessionState.Disconnected)
        private set

    /** TopAppBar 用の現在セッション名（user@host）。 */
    var label by mutableStateOf<String?>(null)
        private set

    /** リモートが出力中か（上部ローディングライン用）。出力が途切れて一定時間で false に戻す。 */
    var busy by mutableStateOf(false)
        private set

    private var session: SshShellSession? = null
    private var transport: SshTransport? = null
    private var currentId: SessionId? = null

    private val clearBusy = Runnable { busy = false }

    private fun onOutputActivity() {
        busy = true
        main.removeCallbacks(clearBusy)
        main.postDelayed(clearBusy, BUSY_IDLE_MS)
    }

    /** 現在セッションの表示名を変更する。 */
    fun rename(newLabel: String) {
        val name = newLabel.trim()
        if (name.isEmpty()) return
        label = name
        val id = currentId ?: return
        sessionManager.upsert(SessionInfo(id, name, state))
        SshForegroundService.start(appContext, name) // 通知の表示名も更新
    }

    /** 接続を開始する。ネットワーク処理は別スレッドで行い、状態更新はメインへ戻す。 */
    fun connect(req: SshConnectionRequest, label: String) {
        SshSecurity.ensureBouncyCastle()
        closeCurrent()

        val id = SessionId(UUID.randomUUID().toString())
        currentId = id
        this.label = label

        val ssh = SshShellSession(TofuHostKeyVerifier(hostKeyStore))
        session = ssh
        val transport = SshTransport(ssh)
        transport.onActivity = { onOutputActivity() }
        this.transport = transport
        val emu = EmulatorHost(req.cols.coerceAtLeast(2), req.rows.coerceAtLeast(2), transport)
        host = emu

        ssh.setOnOutput { data, len -> transport.deliver(data, len) }
        ssh.setOnClosed { err -> main.post { onClosed(id, err) } }

        publish(id, SessionState.Connecting, label)
        // 生存中は常駐通知（バックグラウンドでも接続中と分かる）。
        SshForegroundService.start(appContext, label)
        thread(name = "ssh-connect") {
            try {
                ssh.connect(req)
                main.post {
                    if (currentId == id) {
                        publish(id, SessionState.Connected, label)
                        // 接続前に canvas が計測した実サイズを PTY へ再同期する
                        // （connect 完了前の resize は shell 未生成で握り潰されるため）。
                        // transport 経由でオフロードし、メインスレッドでソケット I/O しない。
                        host?.let { transport.resize(it.cols, it.rows) }
                    }
                }
            } catch (e: Throwable) {
                main.post { if (currentId == id) onFailed(id, e) }
            }
        }
    }

    /** ユーザー操作による切断。 */
    fun disconnect() {
        closeCurrent()
        host = null
        state = SessionState.Disconnected
        label = null
    }

    private fun onClosed(id: SessionId, err: Throwable?) {
        if (currentId != id) return
        Log.w(TAG, "session closed (err=${err?.javaClass?.simpleName}: ${err?.message})", err)
        // サーバ主導のクローズ（シェル終了/回線断）：sshj クライアントを解放して keepAlive 等を止める。
        // 端末面には切断メッセージを残したいので host は保持する。
        session?.close()
        SshForegroundService.stop(appContext)
        main.removeCallbacks(clearBusy)
        busy = false
        feedNotice(if (err?.message != null) "切断されました: ${err.message}" else "接続が閉じられました")
        state = SessionState.Disconnected
        sessionManager.upsert(SessionInfo(id, label ?: "", SessionState.Disconnected))
    }

    private fun onFailed(id: SessionId, e: Throwable) {
        if (currentId != id) return
        Log.w(TAG, "connect failed", e)
        feedNotice("接続に失敗しました: ${e.message ?: e.javaClass.simpleName}")
        state = SessionState.Failed(e.message ?: "接続失敗")
        sessionManager.upsert(SessionInfo(id, label ?: "", state))
    }

    private fun publish(id: SessionId, s: SessionState, label: String) {
        state = s
        sessionManager.upsert(SessionInfo(id, label, s))
    }

    /** 接続失敗・切断メッセージを端末面へ赤字で表示する。 */
    private fun feedNotice(text: String) {
        val emu = host ?: return
        val msg = "\r\n[31m[$text][0m\r\n"
        val bytes = msg.toByteArray(Charsets.UTF_8)
        emu.feed(bytes, bytes.size)
    }

    /** 現在セッションを閉じ、レジストリからも除去する（再接続時のゴースト残留を防ぐ）。 */
    private fun closeCurrent() {
        SshForegroundService.stop(appContext)
        // close は ssh.disconnect（ソケット I/O）を伴うため transport の executor へオフロードする。
        transport?.onActivity = null
        transport?.close()
        transport = null
        session = null
        main.removeCallbacks(clearBusy)
        busy = false
        currentId?.let { sessionManager.remove(it) }
        currentId = null
    }

    private companion object {
        const val TAG = "SSHTerm"

        /** 最後の出力からこの時間出力が無ければ「実行中」を解除する。 */
        const val BUSY_IDLE_MS = 600L
    }
}
