package com.uta.terminal.terminal

import android.os.Handler
import android.os.Looper
import com.uta.terminal.core.ssh.SshShellSession
import java.util.concurrent.Executors

/**
 * SSH シェルチャネルを [Transport] として提供する。
 *
 * - エミュレータ出力（ユーザー入力）→ [SshShellSession.write]（stdin 相当）
 * - SSH の stdout/stderr（読み取りスレッド上で届く）→ [deliver] でメインスレッドへ移し、
 *   [setOnReceive] で登録されたコールバック（＝[EmulatorHost.feed]）へ渡す。
 * - 端末リサイズ → [SshShellSession.resize]（PTY window-change）。
 *
 * スレッド: send/resize/close は**ソケット I/O を伴う**ため、メインスレッドで実行すると
 * `NetworkOnMainThreadException` になる。単一スレッドの executor へ直列にオフロードして
 * 送信順序を保ちつつメインスレッドを塞がない。
 */
class SshTransport(
    private val session: SshShellSession,
    private val main: Handler = Handler(Looper.getMainLooper()),
) : Transport {

    private var onReceive: ((ByteArray, Int) -> Unit)? = null

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ssh-write").apply { isDaemon = true }
    }

    override fun setOnReceive(onReceive: (ByteArray, Int) -> Unit) {
        this.onReceive = onReceive
    }

    /** SSH 読み取りスレッドから呼ばれる。メインスレッドへ渡してエミュレータへ流す。 */
    fun deliver(data: ByteArray, length: Int) {
        main.post { onReceive?.invoke(data, length) }
    }

    override fun send(data: ByteArray, offset: Int, count: Int) {
        // executor は別スレッドで後から実行するため、呼び出し元のバッファ再利用に備えてコピーする。
        val copy = data.copyOfRange(offset, offset + count)
        runCatching { io.execute { session.write(copy, 0, copy.size) } }
    }

    override fun resize(cols: Int, rows: Int) {
        runCatching { io.execute { session.resize(cols, rows) } }
    }

    override fun close() {
        runCatching { io.execute { session.close() } }
        io.shutdown()
    }
}
