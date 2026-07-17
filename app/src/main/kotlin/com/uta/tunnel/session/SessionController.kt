package com.uta.tunnel.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uta.tunnel.core.model.SessionId
import com.uta.tunnel.core.model.SessionState
import com.uta.tunnel.core.session.SessionInfo
import com.uta.tunnel.core.session.SessionManager
import com.uta.tunnel.core.ssh.HostKeyChangedException
import com.uta.tunnel.core.ssh.HostKeyStore
import com.uta.tunnel.core.ssh.SshConnectionRequest
import com.uta.tunnel.core.ssh.SshShellSession
import com.uta.tunnel.core.ssh.TofuHostKeyVerifier
import com.uta.tunnel.service.SshForegroundService
import com.uta.tunnel.ssh.SshSecurity
import com.uta.tunnel.terminal.EmulatorHost
import com.uta.tunnel.terminal.SshTransport
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
        /** 由来した保存済みホスト（アドレス帳）の id。クイック接続や非保存は null。 */
        val profileId: String?,
        /** この接続の元リクエスト。切断後の再接続で同じ宛先・認証へ繋ぎ直すために保持する。 */
        val req: SshConnectionRequest,
    ) {
        var label by mutableStateOf("")
        var state by mutableStateOf<SessionState>(SessionState.Connecting)
        var busy by mutableStateOf(false)
        val clearBusy = Runnable { busy = false }
    }

    private val sessions = mutableStateMapOf<SessionId, Session>()

    /**
     * ホスト鍵の変化を検知して承認待ちになっている接続。UI は MITM 警告ダイアログを出し、
     * [approveHostKeyChange]（上書きして再接続）か [dismissHostKeyChange]（何もしない）を呼ぶ。
     * 再接続に必要な材料を保持する。
     */
    data class PendingHostKeyChange(
        val failedId: SessionId,
        val cause: HostKeyChangedException,
        val req: SshConnectionRequest,
        val label: String,
        val profileId: String?,
    )

    var pendingHostKeyChange by mutableStateOf<PendingHostKeyChange?>(null)
        private set

    var activeId by mutableStateOf<SessionId?>(null)
        private set

    private val active: Session? get() = activeId?.let { sessions[it] }

    val host: EmulatorHost? get() = active?.host
    val state: SessionState get() = active?.state ?: SessionState.Disconnected
    val label: String? get() = active?.label
    val busy: Boolean get() = active?.busy ?: false

    /** 新しいセッションを開始する（既存セッションは閉じない）。[profileId] は由来した保存ホスト。 */
    fun connect(req: SshConnectionRequest, label: String, profileId: String? = null) {
        SshSecurity.ensureBouncyCastle()
        // 同一表示名が既にあれば「name (2)」「name (3)」… と一意化する。
        val displayLabel = uniqueLabel(label)
        val id = SessionId(UUID.randomUUID().toString())
        val ssh = SshShellSession(TofuHostKeyVerifier(hostKeyStore))
        val transport = SshTransport(ssh)
        val emu = EmulatorHost(req.cols.coerceAtLeast(2), req.rows.coerceAtLeast(2), transport)
        val session = Session(id, emu, ssh, transport, profileId, req).apply {
            this.label = displayLabel
            this.state = SessionState.Connecting
        }
        transport.onActivity = { onOutputActivity(id) }
        ssh.setOnOutput { data, len -> transport.deliver(data, len) }
        ssh.setOnClosed { err -> main.post { onClosed(id, err) } }

        sessions[id] = session
        activeId = id
        sessionManager.upsert(SessionInfo(id, displayLabel, SessionState.Connecting, profileId))
        // アクティブの真実の源を一本化：SessionManager にも新規セッションをアクティブとして伝える。
        sessionManager.setActive(id)
        // 常駐通知の開始はフォアグラウンド（ユーザー操作）である connect() でのみ行う。
        SshForegroundService.start(appContext, notificationText())

        thread(name = "ssh-connect") {
            try {
                ssh.connect(req)
                main.post {
                    val s = sessions[id] ?: return@post
                    s.state = SessionState.Connected
                    sessionManager.upsert(SessionInfo(id, s.label, SessionState.Connected, s.profileId))
                    // 接続前に canvas が計測した実サイズを PTY へ再同期。
                    transport.resize(emu.cols, emu.rows)
                    refreshNotification()
                }
            } catch (e: Throwable) {
                main.post { onFailed(id, e, req, displayLabel, profileId) }
            }
        }
    }

    /**
     * ホスト鍵の変化をユーザーが承認した：新しい鍵で上書きし、同じ宛先へ繋ぎ直す。
     * 上書きは Room への書き込み＝メインスレッド不可なので、再接続スレッドの中で行う。
     */
    fun approveHostKeyChange() {
        val p = pendingHostKeyChange ?: return
        pendingHostKeyChange = null
        // 承認された鍵で上書きしてから接続し直す。上書きが失敗すれば TOFU 照合で再び弾かれる。
        thread(name = "hostkey-approve") {
            runCatching { hostKeyStore.save(p.cause.presentedEntry) }
            main.post {
                removeSession(p.failedId)
                connect(p.req, p.label, p.profileId)
            }
        }
    }

    /** 承認しない：鍵は書き換えず、接続もしない（失敗したセッションはそのまま残す）。 */
    fun dismissHostKeyChange() {
        pendingHostKeyChange = null
    }

    /** 表示するセッションを切り替える。 */
    fun setActive(id: SessionId) {
        if (sessions.containsKey(id)) {
            activeId = id
            sessionManager.setActive(id)
            refreshNotification()
        }
    }

    /**
     * 指定ホスト（保存プロファイル）由来の生存セッションがあればアクティブにして true を返す。
     * 無ければ false（呼び出し側で新規 connect する）。複数一致時は挿入順の先頭を選ぶ。
     */
    fun activateExistingForProfile(profileId: String): Boolean {
        val order = sessionManager.sessions.value.map { it.id }
        val target = order.firstOrNull { id ->
            sessions[id]?.profileId == profileId
        } ?: return false
        setActive(target)
        return true
    }

    /** 切断/失敗したアクティブセッションを、同じ接続情報で繋ぎ直す。 */
    fun reconnectActive() {
        val id = activeId ?: return
        reconnect(id)
    }

    /**
     * 指定セッションを同じ宛先・認証で繋ぎ直す。生存中（接続中/確立済み）のセッションには何もしない。
     * 端末バッファは作り直す（サーバー側も新しいシェルになるため）。表示名は引き継ぐ。
     */
    fun reconnect(id: SessionId) {
        val s = sessions[id] ?: return
        if (s.state is SessionState.Connecting || s.state is SessionState.Connected) return
        val req = s.req
        val label = s.label
        val profileId = s.profileId
        // 旧セッションを破棄してから同じ表示名で繋ぎ直す（破棄後なので name の一意化は衝突しない）。
        removeSession(id)
        connect(req, label, profileId)
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
        // 次のアクティブは SessionManager の挿入順（ドロワー表示順）に合わせる。
        // SnapshotStateMap.keys の反復順に依存しないことで、削除後の選択が決定的になる。
        if (activeId == id) {
            val next = sessionManager.sessions.value.firstOrNull()?.id
            activeId = next
            if (next != null) sessionManager.setActive(next)
        }
        refreshNotification()
    }

    /** アクティブセッションの表示名を変更する。 */
    fun rename(newLabel: String) {
        val id = activeId ?: return
        rename(id, newLabel)
    }

    /** 指定セッションの表示名を変更する（一覧からの操作用。アクティブでなくてよい）。 */
    fun rename(id: SessionId, newLabel: String) {
        val name = newLabel.trim()
        if (name.isEmpty()) return
        val s = sessions[id] ?: return
        s.label = name
        sessionManager.upsert(SessionInfo(s.id, name, s.state, s.profileId))
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
        // close() は disconnect パケット送信＝ソケット I/O を伴うため、メインスレッドで呼ぶと
        // NetworkOnMainThreadException で途中失敗する。バックグラウンドで確実に解放する。
        thread(name = "ssh-close") { runCatching { s.ssh.close() } }
        main.removeCallbacks(s.clearBusy)
        s.busy = false
        feedNotice(s, if (err?.message != null) "切断されました: ${err.message}" else "接続が閉じられました")
        s.state = SessionState.Disconnected
        sessionManager.upsert(SessionInfo(id, s.label, SessionState.Disconnected, s.profileId))
        refreshNotification()
    }

    private fun onFailed(
        id: SessionId,
        e: Throwable,
        req: SshConnectionRequest,
        label: String,
        profileId: String?,
    ) {
        val s = sessions[id] ?: return
        // sshj が検証例外を包む場合があるため cause チェーンを辿って TOFU の不一致を拾う。
        val hostKeyChanged = generateSequence(e) { it.cause }
            .filterIsInstance<HostKeyChangedException>()
            .firstOrNull()
        if (hostKeyChanged != null) {
            // フィンガープリントはログに出さない（UI のみで提示し、ユーザーの承認を求める）。
            Log.w(TAG, "host key changed for ${req.host}:${req.port}")
            feedNotice(s, "ホスト鍵が変化しています。承認するまで接続しません")
            s.state = SessionState.Failed("ホスト鍵が変化")
            sessionManager.upsert(SessionInfo(id, s.label, s.state, s.profileId))
            pendingHostKeyChange = PendingHostKeyChange(id, hostKeyChanged, req, label, profileId)
            refreshNotification()
            return
        }
        Log.w(TAG, "connect failed", e)
        feedNotice(s, "接続に失敗しました: ${e.message ?: e.javaClass.simpleName}")
        s.state = SessionState.Failed(e.message ?: "接続失敗")
        sessionManager.upsert(SessionInfo(id, s.label, s.state, s.profileId))
        refreshNotification()
    }

    private fun feedNotice(s: Session, text: String) {
        val msg = "\r\n[31m[$text][0m\r\n"
        val bytes = msg.toByteArray(Charsets.UTF_8)
        s.host.feed(bytes, bytes.size)
    }

    /** 既存セッションと重複しない表示名を返す（重複時は「name (2)」…）。 */
    private fun uniqueLabel(base: String): String {
        val existing = sessions.values.map { it.label }.toSet()
        if (base !in existing) return base
        var n = 2
        while ("$base ($n)" in existing) n++
        return "$base ($n)"
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
