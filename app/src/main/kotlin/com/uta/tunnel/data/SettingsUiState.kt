package com.uta.tunnel.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * 設定値のスナップショット。項目が増えるたびに呼び出し側の引数が増えるのを防ぎ、
 * 同じ設定を複数箇所で個別に collect しないための集約。
 */
@Immutable
data class SettingsUiState(
    val biometricEnabled: Boolean,
    val sessionsTabFirst: Boolean,
    val paletteId: String,
    val fontSizeSp: Float,
    val lineSpacing: Float,
)

/** [SettingsStore] の各 Flow をまとめて購読する。 */
@Composable
fun rememberSettingsUiState(store: SettingsStore): SettingsUiState {
    val biometricEnabled by store.biometricEnabled.collectAsState(initial = true)
    val sessionsTabFirst by store.sessionsTabFirst.collectAsState(initial = false)
    val paletteId by store.terminalPaletteId
        .collectAsState(initial = SettingsStore.DEFAULT_PALETTE_ID)
    val fontSizeSp by store.terminalFontSizeSp
        .collectAsState(initial = SettingsStore.DEFAULT_FONT_SIZE_SP)
    val lineSpacing by store.terminalLineSpacing
        .collectAsState(initial = SettingsStore.DEFAULT_LINE_SPACING)
    return SettingsUiState(
        biometricEnabled = biometricEnabled,
        sessionsTabFirst = sessionsTabFirst,
        paletteId = paletteId,
        fontSizeSp = fontSizeSp,
        lineSpacing = lineSpacing,
    )
}
