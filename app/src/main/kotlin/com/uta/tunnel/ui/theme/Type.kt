package com.uta.tunnel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tunnel の Typography。
 *
 * クロム（接続一覧・設定・見出し）はシステムの sans-serif を基調にモダンに、
 * 端末描画は別途 [TerminalTextStyle] の等幅スタイルで扱う。
 */
private val Base = Typography()

val TunnelTypography = Base.copy(
    headlineSmall = Base.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = Base.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = Base.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = Base.titleSmall.copy(
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 21.sp),
    bodySmall = Base.bodySmall.copy(lineHeight = 17.sp),
    labelLarge = Base.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * 端末描画・補助キーで使う等幅ベーススタイル。
 * TODO: バンドルフォント（JetBrains Mono 等）差し替え時に fontFamily を置換する。
 */
val TerminalTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    letterSpacing = 0.sp,
)
