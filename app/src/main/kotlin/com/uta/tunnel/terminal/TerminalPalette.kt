package com.uta.tunnel.terminal

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * 端末の配色パレット。参照デザイン（calm な muted ダークカードに淡い文字）に合わせ、
 * ビビッドな既定 ANSI 色を彩度の落ちた pastel/muted パレットへ差し替える。
 *
 * UI クロム（TopAppBar・下タブ・補助キー）は Dynamic Color のままにし、端末エリアだけ
 * ここで選んだパレットで統一する（[TerminalPalettes.Dynamic] を選んだ場合のみ端末も壁紙連動）。
 *
 * [ansi16] は配列なので data class の equals/hashCode は参照比較になる。プリセットは
 * [TerminalPalettes] のシングルトンを使い回すため実用上の問題は無いが、比較には [id] を使うこと。
 */
data class TerminalPalette(
    /** DataStore に保存する識別子。 */
    val id: String,
    /** 設定画面に出す表示名。 */
    val label: String,
    /** カード地色＝端末の既定背景。 */
    val background: Int,
    /** 色指定なしテキストの淡いフォア。 */
    val dimForeground: Int,
    val cursor: Int,
    /** ANSI 16 色（index 0..15）。 */
    val ansi16: IntArray,
) {
    /** エミュレータの現在パレットに ANSI 16 色を適用する。 */
    fun applyTo(currentColors: IntArray) {
        val n = minOf(ansi16.size, currentColors.size)
        for (i in 0 until n) currentColors[i] = ansi16[i]
    }
}

/** 選べる端末パレット。既定は従来どおり Catppuccin Mocha。 */
object TerminalPalettes {

    /** 従来の固定パレット（既定）。 */
    val CatppuccinMocha = TerminalPalette(
        id = "catppuccin_mocha",
        label = "Catppuccin Mocha",
        background = 0xFF1E1E2E.toInt(), // base
        dimForeground = 0xFFA6ADC8.toInt(), // subtext0
        cursor = 0xFFB4BEFE.toInt(), // lavender
        ansi16 = intArrayOf(
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
        ),
    )

    val GruvboxDark = TerminalPalette(
        id = "gruvbox_dark",
        label = "Gruvbox Dark",
        background = 0xFF282828.toInt(), // bg0
        dimForeground = 0xFFBDAE93.toInt(), // fg3
        cursor = 0xFFEBDBB2.toInt(), // fg1
        ansi16 = intArrayOf(
            0xFF3C3836.toInt(), // 0 black   (bg1)
            0xFFCC241D.toInt(), // 1 red
            0xFF98971A.toInt(), // 2 green
            0xFFD79921.toInt(), // 3 yellow
            0xFF458588.toInt(), // 4 blue
            0xFFB16286.toInt(), // 5 magenta
            0xFF689D6A.toInt(), // 6 cyan   (aqua)
            0xFFA89984.toInt(), // 7 white  (fg4)
            0xFF665C54.toInt(), // 8 bright black  (bg3)
            0xFFFB4934.toInt(), // 9 bright red
            0xFFB8BB26.toInt(), // 10 bright green
            0xFFFABD2F.toInt(), // 11 bright yellow
            0xFF83A598.toInt(), // 12 bright blue
            0xFFD3869B.toInt(), // 13 bright magenta
            0xFF8EC07C.toInt(), // 14 bright cyan
            0xFFEBDBB2.toInt(), // 15 bright white (fg1)
        ),
    )

    val Nord = TerminalPalette(
        id = "nord",
        label = "Nord",
        background = 0xFF2E3440.toInt(), // nord0
        dimForeground = 0xFFD8DEE9.toInt(), // nord4
        cursor = 0xFF88C0D0.toInt(), // nord8
        ansi16 = intArrayOf(
            0xFF3B4252.toInt(), // 0 black   (nord1)
            0xFFBF616A.toInt(), // 1 red     (nord11)
            0xFFA3BE8C.toInt(), // 2 green   (nord14)
            0xFFEBCB8B.toInt(), // 3 yellow  (nord13)
            0xFF81A1C1.toInt(), // 4 blue    (nord9)
            0xFFB48EAD.toInt(), // 5 magenta (nord15)
            0xFF88C0D0.toInt(), // 6 cyan    (nord8)
            0xFFE5E9F0.toInt(), // 7 white   (nord5)
            0xFF4C566A.toInt(), // 8 bright black (nord3)
            0xFFBF616A.toInt(), // 9 bright red
            0xFFA3BE8C.toInt(), // 10 bright green
            0xFFEBCB8B.toInt(), // 11 bright yellow
            0xFF81A1C1.toInt(), // 12 bright blue
            0xFFB48EAD.toInt(), // 13 bright magenta
            0xFF8FBCBB.toInt(), // 14 bright cyan (nord7)
            0xFFECEFF4.toInt(), // 15 bright white (nord6)
        ),
    )

    /** Dynamic Color を選んだことを示すマーカー。実体は [dynamic] が [ColorScheme] から作る。 */
    const val DYNAMIC_ID = "dynamic"

    val Default = CatppuccinMocha

    /** 固定プリセット（Dynamic を除く）。 */
    val presets = listOf(CatppuccinMocha, GruvboxDark, Nord)

    /**
     * 壁紙連動（Material You）のパレット。ANSI 16 色は Dynamic では作れないため、
     * 既定プリセットの色相を保ったまま背景・フォア・カーソルだけテーマ由来にする。
     */
    fun dynamic(scheme: ColorScheme): TerminalPalette = TerminalPalette(
        id = DYNAMIC_ID,
        label = "Dynamic Color（壁紙連動）",
        background = scheme.surfaceContainerLowest.toArgb(),
        dimForeground = scheme.onSurfaceVariant.toArgb(),
        cursor = scheme.primary.toArgb(),
        ansi16 = Default.ansi16,
    )

    /** DataStore 値の解決。Dynamic と不明な id は null（呼び出し側で解決/既定へ）。 */
    fun byId(id: String): TerminalPalette? = presets.firstOrNull { it.id == id }
}
