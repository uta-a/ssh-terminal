package com.uta.tunnel.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun TunnelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TunnelTypography,
        content = content,
    )
}
