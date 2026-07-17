package com.uta.tunnel.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.uta.tunnel.data.SettingsStore
import com.uta.tunnel.terminal.TerminalPalette
import com.uta.tunnel.terminal.TerminalPalettes

/*
 * Tunnel ブランドカラー。
 * 「静かなダークカードに、彩度を抑えた色が乗る」という端末ビジュアル方針に合わせ、
 *   primary   = 落ち着いたティール／シアン（操作・カーソル）
 *   secondary = 淡いグリーン（接続成功・アクティブ）
 *   tertiary  = 控えめなモーヴ（アクセント）
 * を軸に、暗所でも眩しくない沈んだ背景と低コントラストの前景で calm に構成する。
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1E4E45),
    onPrimaryContainer = Color(0xFFA8EEE0),

    secondary = Color(0xFF9CD3A0),
    onSecondary = Color(0xFF07380F),
    secondaryContainer = Color(0xFF234E29),
    onSecondaryContainer = Color(0xFFB7F0BB),

    tertiary = Color(0xFFC7B7E8),
    onTertiary = Color(0xFF31234C),
    tertiaryContainer = Color(0xFF483A64),
    onTertiaryContainer = Color(0xFFE4D8FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0E1116),
    onBackground = Color(0xFFDDE2E7),
    surface = Color(0xFF12161C),
    onSurface = Color(0xFFDDE2E7),
    surfaceVariant = Color(0xFF3A424B),
    onSurfaceVariant = Color(0xFFBAC2CC),
    outline = Color(0xFF848D97),
    outlineVariant = Color(0xFF3A424B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8EEE0),
    onPrimaryContainer = Color(0xFF00201B),

    secondary = Color(0xFF356A3B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7F0BB),
    onSecondaryContainer = Color(0xFF002105),

    tertiary = Color(0xFF5A4A7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4D8FF),
    onTertiaryContainer = Color(0xFF160438),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFAFBFC),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDBE4E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBFC8C6),
)

/** アクセント色に対して読みやすい前景（明るい色には暗、暗い色には白）を選ぶ。 */
private fun onColor(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF10131A) else Color.White

/**
 * 端末パレットからアプリ全体の Material 3 スキームを生成する。ANSI 色を意味づけしてロールへ
 * 割り当て（cyan→primary / green→secondary / magenta→tertiary / red→error）、面色はパレット背景を
 * [dark] なら白方向、ライトなら黒方向へ段階的にずらして作る。on 色は輝度から自動選択する。
 */
private fun paletteColorScheme(p: TerminalPalette, dark: Boolean): ColorScheme {
    val bg = Color(p.background)
    val fg = Color(p.dimForeground)
    val primary = Color(p.ansi16[6])   // cyan / teal
    val secondary = Color(p.ansi16[2]) // green
    val tertiary = Color(p.ansi16[5])  // magenta
    val errorC = Color(p.ansi16[1])    // red
    return if (dark) {
        fun up(t: Float) = lerp(bg, Color.White, t)
        darkColorScheme(
            primary = primary, onPrimary = onColor(primary),
            primaryContainer = lerp(primary, bg, 0.60f), onPrimaryContainer = lerp(primary, Color.White, 0.30f),
            secondary = secondary, onSecondary = onColor(secondary),
            secondaryContainer = lerp(secondary, bg, 0.60f), onSecondaryContainer = lerp(secondary, Color.White, 0.30f),
            tertiary = tertiary, onTertiary = onColor(tertiary),
            tertiaryContainer = lerp(tertiary, bg, 0.60f), onTertiaryContainer = lerp(tertiary, Color.White, 0.30f),
            error = errorC, onError = onColor(errorC),
            errorContainer = lerp(errorC, bg, 0.60f), onErrorContainer = lerp(errorC, Color.White, 0.30f),
            background = bg, onBackground = fg,
            surface = up(0.03f), onSurface = fg,
            surfaceVariant = up(0.12f), onSurfaceVariant = lerp(fg, bg, 0.30f),
            surfaceContainerLowest = bg, surfaceContainerLow = up(0.03f),
            surfaceContainer = up(0.06f), surfaceContainerHigh = up(0.09f), surfaceContainerHighest = up(0.13f),
            inverseSurface = fg, inverseOnSurface = bg,
            outline = lerp(fg, bg, 0.45f), outlineVariant = up(0.14f),
        )
    } else {
        fun down(t: Float) = lerp(bg, Color.Black, t)
        lightColorScheme(
            primary = primary, onPrimary = onColor(primary),
            primaryContainer = lerp(primary, bg, 0.72f), onPrimaryContainer = lerp(primary, Color.Black, 0.45f),
            secondary = secondary, onSecondary = onColor(secondary),
            secondaryContainer = lerp(secondary, bg, 0.72f), onSecondaryContainer = lerp(secondary, Color.Black, 0.45f),
            tertiary = tertiary, onTertiary = onColor(tertiary),
            tertiaryContainer = lerp(tertiary, bg, 0.72f), onTertiaryContainer = lerp(tertiary, Color.Black, 0.45f),
            error = errorC, onError = onColor(errorC),
            errorContainer = lerp(errorC, bg, 0.72f), onErrorContainer = lerp(errorC, Color.Black, 0.45f),
            background = bg, onBackground = fg,
            surface = bg, onSurface = fg,
            surfaceVariant = down(0.06f), onSurfaceVariant = lerp(fg, bg, 0.30f),
            surfaceContainerLowest = bg, surfaceContainerLow = down(0.02f),
            surfaceContainer = down(0.04f), surfaceContainerHigh = down(0.06f), surfaceContainerHighest = down(0.08f),
            inverseSurface = fg, inverseOnSurface = bg,
            outline = lerp(fg, bg, 0.45f), outlineVariant = down(0.10f),
        )
    }
}

/** テーマモード（[SettingsStore.THEME_MODE_*]）とシステム設定から、暗色にするかを決める。 */
fun resolveDarkTheme(themeMode: String, systemDark: Boolean): Boolean = when (themeMode) {
    SettingsStore.THEME_MODE_LIGHT -> false
    SettingsStore.THEME_MODE_DARK -> true
    else -> systemDark
}

/**
 * アプリ全体のテーマ。[paletteId] が配色系統、[themeMode] が明暗（システム/ライト/ダーク）。
 * - [TerminalPalettes.DYNAMIC_ID]：Dynamic Color（壁紙連動、非対応端末はブランド色）を明暗に応じて。
 * - プリセット系統：明暗で解決した配色から生成したスキームをアプリ全体へ適用する。
 */
@Composable
fun TunnelTheme(
    paletteId: String = TerminalPalettes.DYNAMIC_ID,
    themeMode: String = SettingsStore.THEME_MODE_SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val colorScheme = when (paletteId) {
        TerminalPalettes.DYNAMIC_ID ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) DarkColors else LightColors
            }
        else -> paletteColorScheme(TerminalPalettes.resolve(paletteId, dark), dark)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TunnelTypography,
        content = content,
    )
}
