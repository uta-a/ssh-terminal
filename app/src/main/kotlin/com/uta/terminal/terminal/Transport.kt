package com.uta.terminal.terminal

/**
 * 端末の入出力チャネル抽象。
 * `TerminalEmulator` の出力（ユーザー入力の送出先＝stdin 相当）を受け取り、
 * 受信バイト（stdout 相当）を [onReceive] で戻す。
 *
 * PoC では [LocalEchoTransport] がループバックする。後で SSH channel 実装に差し替える。
 */
interface Transport {
    /** エミュレータから来た送出バイト（ユーザー入力）を下流（リモート）へ書き出す。 */
    fun send(data: ByteArray, offset: Int, count: Int)

    /** 受信バイトを上流（エミュレータ）へ渡すコールバックを登録する。 */
    fun setOnReceive(onReceive: (ByteArray, Int) -> Unit)

    /** 端末サイズ変更を下流（リモート PTY）へ通知する。既定は無視（ローカルエコー等）。 */
    fun resize(cols: Int, rows: Int) {}

    fun close()
}

/**
 * ローカルエコーのループバック実装（PoC 用）。
 * 送られたバイトをそのまま受信側に返す。ただし cooked-mode 端末らしく見せるため
 * CR(\r) は CRLF に、DEL(0x7f) は "バックスペース＋空白＋バックスペース" に変換して
 * 画面上で改行・文字消去が成立するようにする。
 */
class LocalEchoTransport : Transport {
    private var onReceive: ((ByteArray, Int) -> Unit)? = null

    override fun setOnReceive(onReceive: (ByteArray, Int) -> Unit) {
        this.onReceive = onReceive
    }

    override fun send(data: ByteArray, offset: Int, count: Int) {
        val cb = onReceive ?: return
        val out = ArrayList<Byte>(count + 4)
        for (i in offset until offset + count) {
            when (val b = data[i]) {
                CR -> { out.add(CR); out.add(LF) }
                DEL -> { out.add(BS); out.add(SPACE); out.add(BS) }
                else -> out.add(b)
            }
        }
        val arr = out.toByteArray()
        cb(arr, arr.size)
    }

    override fun close() {
        onReceive = null
    }

    private companion object {
        const val CR: Byte = 0x0d
        const val LF: Byte = 0x0a
        const val DEL: Byte = 0x7f
        const val BS: Byte = 0x08
        const val SPACE: Byte = 0x20
    }
}
