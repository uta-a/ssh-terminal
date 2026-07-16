package com.uta.terminal.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.uta.terminal.service.SshForegroundService
import com.uta.terminal.ssh.SshSecurity
import com.uta.terminal.terminal.EmulatorHost
import com.uta.terminal.terminal.SshTransport
import java.util.UUID
import kotlin.concurrent.thread

/**
 * 複数の SSH セッションを保持する。各セッションが自分の [EmulatorHost] を持ち、[activeId] が
 * 現在 UI に表示するセッションを決める。同一ホストにも複数接続できる（新規接続で既存は閉じない）。
 *
 * [host]/[state]/[label]/[busy] はアクティブセッションの状態を返す（Compose が購読）。
 */
class SessionController(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val hostKeyStore: HostKeyStore,
) {
    private val main = Handler(Looper.getMainLooper())

    private class Session(
        val id: SessionId,
        val host: EmulatorHost,
        val ssh: SshShellSession,
        val transport: SshTransport,
    ) {
        var label by mutableStateOf("")
        var state by mutableStateOf<SessionState>(SessionState.Connecting)
        var busy by mutableStateOf(false)
        val clearBusy = Runnable { busy = false }
    }

    private val sessions = mutableStateMapOf<SessionId, Session>()

    var activeId by mutableStateOf<SessionId?>(null)
        private set

    private val active: Session? get() = activeId?.let { sessions[it] }

    val host: EmulatorHost? get() = active?.host
    val state: SessionState get() = active?.state ?: SessionState.Disconnected
    val label: String? get() = active?.label
    val busy: Boolean get() = active?.busy ?: false

    /** 新しいセッションを開始する（既存セッションは閉じない）。 */
    fun connect(req: SshConnectionRequest, label: String) {
        SshSecurity.ensureBouncyCastle()
        val id = SessionId(UUID.randomUUID().toString())
        val ssh = SshShellSession(TofuHostKeyVerifier(hostKeyStore))
        val transport = SshTransport(ssh)
        val emu = EmulatorHost(req.cols.coerceAtLeast(2), req.rows.coerceAtLeast(2), transport)
        val session = Session(id, emu, ssh, transport).apply {
            this.label = label
            this.state = SessionState.Connecting
        }
        transport.onActivity = { onOutputActivity(id) }
        ssh.setOnOutput { data, len -> transport.deliver(data, len) }
        ssh.setOnClosed { err -> main.post { onClosed(id, err) } }

        sessions[id] = session
        activeId = id
        sessionManager.upsert(SessionInfo(id, label, SessionState.Connecting))
        // 常駐通知の開始はフォアグラウンド（ユーザー操作）である connect() でのみ行う。
        SshForegroundService.start(appContext, notificationText())

        thread(name = "ssh-connect") {
            try {
                ssh.connect(req)
                main.post {
                    val s = sessions[id] ?: return@post
                    s.state = SessionState.Connected
                    sessionManager.upsert(SessionInfo(id, s.label, SessionState.Connected))
                    // 接続前に canvas が計測した実サイズを PTY へ再同期。
                    transport.resize(emu.cols, emu.rows)
                    refreshNotification()
                }
            } catch (e: Throwable) {
                main.post { onFailed(id, e) }
            }
        }
    }

    /** 表示するセッションを切り替える。 */
    fun setActive(id: SessionId) {
        if (sessions.containsKey(id)) {
            activeId = id
            refreshNotification()
        }
    }

    /** アクティブセッションを切断・破棄する。他に生きたセッションがあればそれをアクティブにする。 */
    fun disconnect() {
        val id = activeId ?: return
        removeSession(id)
    }

    fun disconnect(id: SessionId) {
        removeSession(id)
    }

    private fun removeSession(id: SessionId) {
        val s = sessions.remove(id) ?: return
        main.removeCallbacks(s.clearBusy)
        s.transport.onActivity = null
        s.transport.close()
        sessionManager.remove(id)
        if (activeId == id) activeId = sessions.keys.firstOrNull()
        refreshNotification()
    }

    /** アクティブセッションの表示名を変更する。 */
    fun rename(newLabel: String) {
        val name = newLabel.trim()
        if (name.isEmpty()) return
        val s = active ?: return
        s.label = name
        sessionManager.upsert(SessionInfo(s.id, name, s.state))
        refreshNotification()
    }

    private fun onOutputActivity(id: SessionId) {
        val s = sessions[id] ?: return
        s.busy = true
        main.removeCallbacks(s.clearBusy)
        main.postDelayed(s.clearBusy, BUSY_IDLE_MS)
    }

    private fun onClosed(id: SessionId, err: Throwable?) {
        val s = sessions[id] ?: return
        Log.w(TAG, "session closed (err=${err?.javaClass?.simpleName}: ${err?.message})", err)
        // サーバ主導のクローズ：sshj クライアントを解放。端末面には切断メッセージを残す（host は保持）。
        s.ssh.close()
        main.removeCallbacks(s.clearBusy)
        s.busy = false
        feedNotice(s, if (err?.message != null) "切断されました: ${err.message}" else "接続が閉じられました")
        s.state = SessionState.Disconnected
        sessionManager.upsert(SessionInfo(id, s.label, SessionState.Disconnected))
        refreshNotification()
    }

    private fun onFailed(id: SessionId, e: Throwable) {
        val s = sessions[id] ?: return
        Log.w(TAG, "connect failed", e)
        feedNotice(s, "接続に失敗しました: ${e.message ?: e.javaClass.simpleName}")
        s.state = SessionState.Failed(e.message ?: "接続失敗")
        sessionManager.upsert(SessionInfo(id, s.label, s.state))
        refreshNotification()
    }

    private fun feedNotice(s: Session, text: String) {
        val msg = "\r\n[31m[$text][0m\r\n"
        val bytes = msg.toByteArray(Charsets.UTF_8)
        s.host.feed(bytes, bytes.size)
    }

    private fun notificationText(): String {
        val activeLabel = active?.label ?: "セッション"
        val count = sessions.size
        return if (count > 1) "$activeLabel  (+${count - 1})" else activeLabel
    }

    /** 表示中の常駐通知を更新／停止する（バックグラウンドからも安全）。開始は connect() で行う。 */
    private fun refreshNotification() {
        val anyAlive = sessions.values.any {
            it.state is SessionState.Connecting || it.state is SessionState.Connected
        }
        if (anyAlive) SshForegroundService.update(appContext, notificationText())
        else SshForegroundService.stop(appContext)
    }

    private companion object {
        const val TAG = "SSHTerm"

        /** 最後の出力からこの時間出力が無ければ「実行中」を解除する。 */
        const val BUSY_IDLE_MS = 600L
    }
}
