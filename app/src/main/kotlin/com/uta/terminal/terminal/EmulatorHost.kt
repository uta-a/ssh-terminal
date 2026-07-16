package com.uta.terminal.terminal

import com.termux.terminal.TerminalEmulator

/**
 * Termux [TerminalEmulator] を SSH channel で駆動する橋渡し（スケルトン）。
 *
 * 本アプリはローカル PTY（TerminalSession）を使わず、SSH の stdout バイトを
 * [TerminalEmulator.append] に流し込み、画面バッファを描画側（自作 Compose Canvas）へ渡す。
 * ここでは依存解決の確認用に型参照のみを置く。実処理は PoC フェーズで実装する。
 */
class EmulatorHost {
    // TODO(PoC): TerminalOutput（stdin へ書き戻す）と columns/rows を与えて生成し、
    //   SSH stdout を append、画面更新を snapshot state として公開する。
    private var emulator: TerminalEmulator? = null

    fun isInitialized(): Boolean = emulator != null
}
