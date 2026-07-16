package com.uta.terminal.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import kotlinx.coroutines.delay
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
    pendingInput: String,
    composingStart: Int,
    composingEnd: Int,
    defaultFgArgb: Int,
    composingColorArgb: Int,
    fontScale: Float,
    scrollOffset: Int,
    onCellHeight: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    // 端末フォント：JetBrainsMono Nerd Font（Mono 版）。Starship 等の Nerd Font アイコン字形を含む。
    // Mono 版はアイコンも 1 セル幅なので固定グリッドと整合する。読み込み失敗時は等幅にフォールバック。
    val typeface = remember {
        runCatching {
            Typeface.createFromAsset(context.assets, "fonts/JetBrainsMonoNerdFontMono-Regular.ttf")
        }.getOrDefault(Typeface.MONOSPACE)
    }
    // Nerd Font にも無い記号（例: ⏵ U+23F5）はシステムフォントの字形にフォールバックして描く。
    val fallbackTypeface = remember { Typeface.DEFAULT }
    // 基準 14sp にピンチ操作の倍率を掛けた実サイズ。倍率が変わるとセル寸法も再計算される。
    val textSizePx = with(density) { (14f * fontScale).sp.toPx() }

    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    paint.typeface = typeface
    paint.textSize = textSizePx
    val fm = remember(textSizePx) { paint.fontMetrics }
    val cellW = remember(textSizePx) { paint.measureText("M").coerceAtLeast(1f) }
    val cellH = remember(textSizePx) { ceil(fm.descent - fm.ascent).coerceAtLeast(1f) }

    // スクロール（px→行）の換算に使うため、セル高をホイストする。
    LaunchedEffect(cellH) { onCellHeight(cellH) }

    // 端末の地色・カーソルは calm な固定色。色指定なしの既定文字色は Material You 由来（呼び出し側から受け取る）。
    val surfaceArgb = TerminalPalette.BACKGROUND
    val cursorArgb = TerminalPalette.CURSOR

    // ビューポート（カード内寸）に合わせて桁数/行数を算出しリサイズする。
    // キーボード表示でカードが縮むと行数も減り、シェル下端（プロンプト）がキーボード上端に収まる。
    var pendingSize by remember { mutableStateOf(IntSize.Zero) }
    // cellW/cellH をキーに含め、ピンチでフォントサイズ（=セル寸法）が変わったら桁数/行数を再計算する。
    LaunchedEffect(pendingSize, cellW, cellH) {
        val s = pendingSize
        if (s.width <= 0 || s.height <= 0) return@LaunchedEffect
        delay(60)
        val cols = (s.width / cellW).toInt().coerceAtLeast(2)
        val rows = (s.height / cellH).toInt().coerceAtLeast(2)
        host.resize(cols, rows)
    }

    Canvas(
        modifier = modifier.onSizeChanged { size -> pendingSize = size },
    ) {
        // 再描画トリガの購読（frame と pendingInput の変化で DrawScope を再実行）。
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
                pendingInput = pendingInput,
                composingStart = composingStart,
                composingEnd = composingEnd,
                composingColorArgb = composingColorArgb,
                scrollOffset = scrollOffset,
                typeface = typeface,
                fallbackTypeface = fallbackTypeface,
            )
        }
    }
}

private const val TRUECOLOR_MASK = 0xff000000.toInt()

// コードポイントごとに「primary フォントが字形を持つか」をキャッシュ（フォントは固定なので有効）。
private val glyphInPrimary = HashMap<Int, Boolean>()

/**
 * 与えたグリフを描くのに使うべき Typeface を返す。primary に字形が無ければ fallback（システム）を返す。
 * ASCII は primary が必ず持つので判定を省く。判定結果は [glyphInPrimary] にキャッシュする。
 */
private fun glyphTypeface(paint: Paint, s: String, primary: Typeface, fallback: Typeface): Typeface {
    val cp = s.codePointAt(0)
    if (cp <= 0x7F) return primary
    val has = glyphInPrimary.getOrPut(cp) {
        val prev = paint.typeface
        paint.typeface = primary
        val h = paint.hasGlyph(s)
        paint.typeface = prev
        h
    }
    return if (has) primary else fallback
}

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
    pendingInput: String,
    composingStart: Int,
    composingEnd: Int,
    composingColorArgb: Int,
    scrollOffset: Int,
    typeface: Typeface,
    fallbackTypeface: Typeface,
) {
    val screen = emu.screen
    val rows = emu.mRows
    val cols = emu.mColumns
    val palette = emu.mColors.mCurrentColors
    val reverseVideo = emu.isReverseVideo
    // 履歴スクロール量（行）。0＝ライブ画面。上限は履歴行数。
    val back = scrollOffset.coerceIn(0, screen.activeTranscriptRows)

    for (row in 0 until rows) {
        // back だけ上（過去）にずらした外部行を描く。外部行が負なら履歴（transcript）を参照する。
        val internal = screen.externalToInternalRow(row - back)
        val line = screen.allocateFullLineIfNecessary(internal)
        // 空行（印字文字なし）は既定背景で透過するので走査ごとスキップし、毎フレームの負荷を下げる。
        if (line.spaceUsed == 0) continue
        val text = line.mText
        var prevIdx = -1
        val topY = row * cellH
        for (col in 0 until cols) {
            val idx = line.findStartOfColumn(col)
            if (idx == prevIdx) continue // wide 文字の 2 セル目はスキップ
            prevIdx = idx

            // 表示幅（全角＝2 セル）。次列が同じ idx を指すなら 2 セル幅とみなす。
            // 背景は幅ぶん塗らないと、全角文字の右半分に地色が残る。
            val cellSpan = if (col + 1 < cols && line.findStartOfColumn(col + 1) == idx) 2 else 1

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
            // 背景：既定背景（カード地色と一致）は塗らずにカードを透けさせる。
            // 大半のセルはこれに該当するため、毎フレームの drawRect を大幅に削減できる。
            if (bgArgb != surfaceArgb) {
                paint.style = Paint.Style.FILL
                paint.color = bgArgb
                paint.alpha = (bgArgb ushr 24)
                canvas.drawRect(left, topY, left + cellSpan * cellW, topY + cellH, paint)
            }

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
                    paint.typeface = glyphTypeface(paint, cp, typeface, fallbackTypeface)
                    canvas.drawText(cp, left, topY + baseline, paint)
                    paint.typeface = typeface
                    paint.isFakeBoldText = false
                    paint.textSkewX = 0f
                    paint.isUnderlineText = false
                }
            }
        }
    }

    // 履歴をスクロール表示中（back>0）は、ライブ画面に属する入力中テキストとカーソルは描かない。
    if (back != 0) return

    // 入力中テキスト（未送信）をカーソル位置からインライン表示し、キャレットをその末尾に置く。
    // 全角（ワイド）文字は 2 セル幅で進める（1 セルだと次の文字が重なる）。
    var caretCol = emu.cursorCol
    var caretRow = emu.cursorRow
    if (pendingInput.isNotEmpty()) {
        var i = 0
        while (i < pendingInput.length) {
            val startOffset = i
            val cp = pendingInput.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == '\n'.code || cp == '\r'.code) {
                caretCol = 0; caretRow += 1
                continue
            }
            // 入力中（IME 変換中・未確定）は明るめ、変換確定した文字は既定 Material You 色に落ち着く。
            // 送信するとテキストは端末バッファへ移り、そこでも同じ既定色で描かれる（確定=送信で同色）。
            val composing = startOffset in composingStart until composingEnd
            paint.color = if (composing) composingColorArgb else defaultFgArgb
            val w = if (isWideCodePoint(cp)) 2 else 1
            if (caretCol + w > cols) { caretCol = 0; caretRow += 1 }
            if (caretRow in 0 until rows) {
                val s = String(Character.toChars(cp))
                paint.typeface = glyphTypeface(paint, s, typeface, fallbackTypeface)
                canvas.drawText(s, caretCol * cellW, caretRow * cellH + baseline, paint)
                paint.typeface = typeface
            }
            caretCol += w
        }
        if (caretCol >= cols) { caretCol = 0; caretRow += 1 }
    }

    // キャレット（ブロックカーソル）
    if ((emu.shouldCursorBeVisible() || pendingInput.isNotEmpty()) &&
        caretCol in 0 until cols && caretRow in 0 until rows
    ) {
        val left = caretCol * cellW
        val topY = caretRow * cellH
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

/**
 * 全角（2 セル幅）文字かの近似判定（East Asian Width 相当）。
 * Termux の WcWidth は package-private で使えないため、主要な CJK/かな/全角/絵文字の範囲で判定する。
 */
private fun isWideCodePoint(cp: Int): Boolean = when {
    cp < 0x1100 -> false
    cp in 0x1100..0x115F -> true // Hangul Jamo
    cp in 0x2E80..0x303E -> true // CJK 部首・康熙・CJK 記号
    cp in 0x3041..0x33FF -> true // ひらがな・カタカナ・CJK 記号等
    cp in 0x3400..0x4DBF -> true // CJK 拡張 A
    cp in 0x4E00..0x9FFF -> true // CJK 統合漢字
    cp in 0xA000..0xA4CF -> true // 彝(Yi)
    cp in 0xAC00..0xD7A3 -> true // ハングル音節
    cp in 0xF900..0xFAFF -> true // CJK 互換漢字
    cp in 0xFE30..0xFE4F -> true // CJK 互換形
    cp in 0xFF00..0xFF60 -> true // 全角形
    cp in 0xFFE0..0xFFE6 -> true // 全角記号
    cp in 0x1F300..0x1FAFF -> true // 絵文字（概ね全角）
    cp in 0x20000..0x3FFFD -> true // CJK 拡張 B 以降
    else -> false
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
