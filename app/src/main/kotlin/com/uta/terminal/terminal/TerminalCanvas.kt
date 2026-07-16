package com.uta.terminal.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import kotlin.math.ceil

/**
 * TerminalEmulator の画面バッファを Compose Canvas で自前描画する（Level B の核）。
 *
 * - 既定色（色指定なし）の文字はテーマの淡いグレー（onSurfaceVariant）で描き、
 *   浮きカードの上で calm に見せる。SGR で色付けされた文字は実際の色で描く。
 * - 描画は `host.frame`（再描画トリガ）を購読し、変化のたびに引き直す。
 *
 * 性能: PoC ではセル単位に drawText する素朴実装。run 単位バッチ・dirty 再描画は後続最適化。
 */
@Composable
fun TerminalCanvas(
    host: EmulatorHost,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 14.sp.toPx() }

    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    }
    paint.textSize = textSizePx
    val fm = remember(textSizePx) { paint.fontMetrics }
    val cellW = remember(textSizePx) { paint.measureText("M").coerceAtLeast(1f) }
    val cellH = remember(textSizePx) { ceil(fm.descent - fm.ascent).coerceAtLeast(1f) }

    // 端末エリアは参照デザインに合わせた calm な固定パレット（UI クロムの Dynamic Color とは分離）。
    val defaultFgArgb = TerminalPalette.DIM_FOREGROUND
    val surfaceArgb = TerminalPalette.BACKGROUND
    val cursorArgb = TerminalPalette.CURSOR

    Canvas(
        modifier = modifier.onSizeChanged { size ->
            if (size.width <= 0 || size.height <= 0) return@onSizeChanged
            val cols = (size.width / cellW).toInt().coerceAtLeast(2)
            val rows = (size.height / cellH).toInt().coerceAtLeast(2)
            host.resize(cols, rows)
        },
    ) {
        // 再描画トリガの購読（値の変化で DrawScope が再実行される）。
        @Suppress("UNUSED_EXPRESSION")
        host.frame
        drawIntoCanvas { canvas ->
            renderScreen(
                canvas.nativeCanvas,
                host.emulator,
                paint,
                cellW,
                cellH,
                baseline = -fm.ascent,
                defaultFgArgb = defaultFgArgb,
                surfaceArgb = surfaceArgb,
                cursorArgb = cursorArgb,
            )
        }
    }
}

private const val TRUECOLOR_MASK = 0xff000000.toInt()

private fun renderScreen(
    canvas: android.graphics.Canvas,
    emu: TerminalEmulator,
    paint: Paint,
    cellW: Float,
    cellH: Float,
    baseline: Float,
    defaultFgArgb: Int,
    surfaceArgb: Int,
    cursorArgb: Int,
) {
    val screen = emu.screen
    val rows = emu.mRows
    val cols = emu.mColumns
    val palette = emu.mColors.mCurrentColors
    val reverseVideo = emu.isReverseVideo

    for (row in 0 until rows) {
        val internal = screen.externalToInternalRow(row)
        val line = screen.allocateFullLineIfNecessary(internal)
        val text = line.mText
        var prevIdx = -1
        val topY = row * cellH
        for (col in 0 until cols) {
            val idx = line.findStartOfColumn(col)
            if (idx == prevIdx) continue // wide 文字の 2 セル目はスキップ
            prevIdx = idx

            val style = line.getStyle(col)
            val effect = TextStyle.decodeEffect(style)
            val bold = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
            val italic = (effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0
            val underline = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
            val invisible = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0
            val inverse = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0

            var fgArgb = resolveFg(TextStyle.decodeForeColor(style), palette, defaultFgArgb, bold)
            var bgArgb = resolveBg(TextStyle.decodeBackColor(style), palette, surfaceArgb)
            if (inverse xor reverseVideo) {
                val t = fgArgb; fgArgb = bgArgb; bgArgb = t
            }

            val left = col * cellW
            // 背景（既定背景はカード色と一致するので実質透過に見える）。
            paint.style = Paint.Style.FILL
            paint.color = bgArgb
            paint.alpha = (bgArgb ushr 24)
            canvas.drawRect(left, topY, left + cellW, topY + cellH, paint)

            // 文字
            if (!invisible && idx < text.size) {
                val c = text[idx]
                if (c != ' ' && c.code != 0) {
                    val cp = if (Character.isHighSurrogate(c) && idx + 1 < text.size) {
                        String(charArrayOf(c, text[idx + 1]))
                    } else {
                        c.toString()
                    }
                    paint.color = fgArgb
                    paint.alpha = (fgArgb ushr 24)
                    paint.isFakeBoldText = bold
                    paint.textSkewX = if (italic) -0.25f else 0f
                    paint.isUnderlineText = underline
                    canvas.drawText(cp, left, topY + baseline, paint)
                    paint.isFakeBoldText = false
                    paint.textSkewX = 0f
                    paint.isUnderlineText = false
                }
            }
        }
    }

    // カーソル
    if (emu.shouldCursorBeVisible()) {
        val cc = emu.cursorCol
        val cr = emu.cursorRow
        if (cc in 0 until cols && cr in 0 until rows) {
            val left = cc * cellW
            val topY = cr * cellH
            paint.style = Paint.Style.FILL
            paint.color = cursorArgb
            paint.alpha = 0x66 // 文字が透けるブロックカーソル
            when (emu.cursorStyle) {
                TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE ->
                    canvas.drawRect(left, topY + cellH - 3f, left + cellW, topY + cellH, paint)
                TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR ->
                    canvas.drawRect(left, topY, left + 3f, topY + cellH, paint)
                else ->
                    canvas.drawRect(left, topY, left + cellW, topY + cellH, paint)
            }
            paint.alpha = 0xff
        }
    }
}

private fun resolveFg(color: Int, palette: IntArray, defaultFgArgb: Int, bold: Boolean): Int = when {
    (color and TRUECOLOR_MASK) == TRUECOLOR_MASK -> color
    color == TextStyle.COLOR_INDEX_FOREGROUND -> defaultFgArgb
    color in 0..255 -> {
        val i = if (bold && color in 0..7) color + 8 else color
        palette[i]
    }
    else -> palette.getOrElse(color) { defaultFgArgb }
}

private fun resolveBg(color: Int, palette: IntArray, surfaceArgb: Int): Int = when {
    (color and TRUECOLOR_MASK) == TRUECOLOR_MASK -> color
    color == TextStyle.COLOR_INDEX_BACKGROUND -> surfaceArgb
    color in 0..255 -> palette[color]
    else -> surfaceArgb
}
