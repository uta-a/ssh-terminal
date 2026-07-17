package com.uta.tunnel.terminal

/**
 * 端末の配色パレット。参照デザイン（calm な muted ダークカードに淡い文字）に合わせ、
 * ビビッドな既定 ANSI 色を彩度の落ちた pastel/muted パレット（Catppuccin Mocha 準拠）へ差し替える。
 *
 * UI クロム（TopAppBar・ドロワー・補助キー）は Dynamic Color のままにし、
 * 端末エリアだけこの固定の calm パレットで統一する。
 */
object TerminalPalette {
    /** カード地色＝端末の既定背景（Catppuccin base）。 */
    const val BACKGROUND: Int = 0xFF1E1E2E.toInt()

    /** 色指定なしテキストの淡いフォア（Catppuccin subtext0）。 */
    const val DIM_FOREGROUND: Int = 0xFFA6ADC8.toInt()

    /** カーソル色（Catppuccin lavender）。 */
    const val CURSOR: Int = 0xFFB4BEFE.toInt()

    // 入力中（未送信・確定済み）テキストのインライン色は Material You 由来の明色を
    // 呼び出し側（TerminalScreen）で算出して渡す。端末カードは常にダークなので明るめに寄せる。

    /**
     * ANSI 16 色（index 0..15）。Catppuccin Mocha。
     * 既定の Termux パレット（純赤/純緑など）より彩度を落とし calm にする。
     */
    val ANSI_16: IntArray = intArrayOf(
        0xFF45475A.toInt(), // 0 black   (surface1)
        0xFFF38BA8.toInt(), // 1 red
        0xFFA6E3A1.toInt(), // 2 green
        0xFFF9E2AF.toInt(), // 3 yellow
        0xFF89B4FA.toInt(), // 4 blue
        0xFFF5C2E7.toInt(), // 5 magenta (pink)
        0xFF94E2D5.toInt(), // 6 cyan    (teal)
        0xFFBAC2DE.toInt(), // 7 white   (subtext1)
        0xFF585B70.toInt(), // 8 bright black   (surface2)
        0xFFF38BA8.toInt(), // 9 bright red
        0xFFA6E3A1.toInt(), // 10 bright green
        0xFFF9E2AF.toInt(), // 11 bright yellow
        0xFF89B4FA.toInt(), // 12 bright blue
        0xFFF5C2E7.toInt(), // 13 bright magenta
        0xFF94E2D5.toInt(), // 14 bright cyan
        0xFFA6ADC8.toInt(), // 15 bright white  (subtext0)
    )

    /** エミュレータの現在パレットに ANSI 16 色を適用する。 */
    fun applyTo(currentColors: IntArray) {
        val n = minOf(ANSI_16.size, currentColors.size)
        for (i in 0 until n) currentColors[i] = ANSI_16[i]
    }
}
