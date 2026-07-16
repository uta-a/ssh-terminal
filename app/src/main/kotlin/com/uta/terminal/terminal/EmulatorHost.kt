package com.uta.terminal.terminal

import androidx.compose.runtime.mutableIntStateOf
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput

/**
 * Termux [TerminalEmulator] を PTY ではなく [Transport]（PoC ではローカルエコー、
 * 後に SSH channel）で駆動する橋渡し。
 *
 * - 出力（ユーザー入力の送出）: エミュレータの [TerminalOutput] → [transport].send
 * - 受信（stdout 相当）: [transport] のコールバック → [feed] → [TerminalEmulator.append]
 * - 画面更新のたびに [frame] を進め、Compose Canvas の再描画を促す。
 *
 * スレッド: PoC は単一スレッド（入力→エコー→append）。SSH 化時は受信スレッドから
 * [feed] を呼ぶため、メインスレッドへマーシャリングして [frame] を更新すること。
 */
class EmulatorHost(
    columns: Int,
    rows: Int,
    private val transport: Transport,
) {
    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            transport.send(data, offset, count)
        }
        override fun titleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }

    val emulator: TerminalEmulator =
        TerminalEmulator(output, columns, rows, TRANSCRIPT_ROWS, null)

    private val frameState = mutableIntStateOf(0)

    /** Compose から購読する再描画トリガ。値が変わると Canvas が引き直される。 */
    val frame: Int get() = frameState.intValue

    init {
        // ビビッドな既定 ANSI 色を calm な muted パレットへ差し替える。
        TerminalPalette.applyTo(emulator.mColors.mCurrentColors)
        transport.setOnReceive { data, len -> feed(data, len) }
    }

    /** 受信バイト（stdout 相当）をエミュレータへ流し込む。 */
    fun feed(data: ByteArray, length: Int) {
        emulator.append(data, length)
        invalidate()
    }

    fun resize(columns: Int, rows: Int) {
        if (columns < 2 || rows < 2) return
        if (columns == emulator.mColumns && rows == emulator.mRows) return
        emulator.resize(columns, rows)
        invalidate()
    }

    // ---- 入力 ----

    /** 通常文字（コードポイント）を送る。Ctrl/Alt の合成はここで行う（KeyHandler の管轄外）。 */
    fun sendCodePoint(codePoint: Int, ctrl: Boolean, alt: Boolean) {
        var cp = codePoint
        if (ctrl) cp = applyCtrl(cp)
        val text = String(Character.toChars(cp))
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (alt) {
            output.write(byteArrayOf(ESC), 0, 1)
        }
        output.write(bytes, 0, bytes.size)
    }

    /**
     * 特殊キー（矢印/Tab/Enter/F1..等）を Android keyCode から送る。
     * KeyHandler がエスケープ列を返せば送出して true、非特殊キーなら false。
     */
    fun sendKeyCode(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): Boolean {
        var keyMode = 0
        if (ctrl) keyMode = keyMode or KeyHandler.KEYMOD_CTRL
        if (alt) keyMode = keyMode or KeyHandler.KEYMOD_ALT
        if (shift) keyMode = keyMode or KeyHandler.KEYMOD_SHIFT
        val code = KeyHandler.getCode(
            keyCode,
            keyMode,
            emulator.isCursorKeysApplicationMode,
            emulator.isKeypadApplicationMode,
        ) ?: return false
        output.write(code)
        return true
    }

    /** 生バイト送出（補助キー行の Esc/Tab など）。 */
    fun sendBytes(bytes: ByteArray) {
        output.write(bytes, 0, bytes.size)
    }

    private fun invalidate() {
        frameState.intValue = frameState.intValue + 1
    }

    private fun applyCtrl(cp: Int): Int = when (cp) {
        in 'a'.code..'z'.code -> cp - 'a'.code + 1        // Ctrl-A..Z → 0x01..0x1a
        in 'A'.code..'Z'.code -> cp - 'A'.code + 1
        ' '.code, '@'.code -> 0                            // Ctrl-Space / Ctrl-@ → NUL
        '['.code -> 27
        '\\'.code -> 28
        ']'.code -> 29
        '^'.code -> 30
        '_'.code -> 31
        else -> cp
    }

    private companion object {
        val TRANSCRIPT_ROWS: Int = 2000
        const val ESC: Byte = 0x1b
    }
}
