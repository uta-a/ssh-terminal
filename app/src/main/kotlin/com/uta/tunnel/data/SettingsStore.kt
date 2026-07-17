package com.uta.tunnel.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * アプリ設定の永続化（DataStore）。生体認証ロック・ボトムタブ順・端末の見た目を保持する。
 *
 * 読み出し側は範囲外の保存値でも安全なように、[Flow] と setter の両方で clamp する。
 */
class SettingsStore(private val context: Context) {

    val biometricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BIOMETRIC] ?: true }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC] = enabled }
    }

    /** true なら下タブを「セッション / ホスト / 設定」に入れ替える（既定は「ホスト / セッション / 設定」）。 */
    val sessionsTabFirst: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SESSIONS_TAB_FIRST] ?: false }

    suspend fun setSessionsTabFirst(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SESSIONS_TAB_FIRST] = enabled }
    }

    /** 端末の等幅フォントの基準サイズ（sp）。端末画面のピンチ操作もこの値を書き換える。 */
    val terminalFontSizeSp: Flow<Float> =
        context.dataStore.data.map {
            (it[KEY_FONT_SIZE_SP] ?: DEFAULT_FONT_SIZE_SP)
                .coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        }

    suspend fun setTerminalFontSizeSp(sizeSp: Float) {
        context.dataStore.edit {
            it[KEY_FONT_SIZE_SP] = sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        }
    }

    /** 行間の倍率（1.0 = フォントメトリクスそのまま）。 */
    val terminalLineSpacing: Flow<Float> =
        context.dataStore.data.map {
            (it[KEY_LINE_SPACING] ?: DEFAULT_LINE_SPACING)
                .coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)
        }

    suspend fun setTerminalLineSpacing(spacing: Float) {
        context.dataStore.edit {
            it[KEY_LINE_SPACING] = spacing.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)
        }
    }

    /**
     * 端末の配色パレット id（`TerminalPalettes` の id、または "dynamic"）。
     * 解決は UI 側で行う（Dynamic Color は ColorScheme が要るため）。
     */
    val terminalPaletteId: Flow<String> =
        context.dataStore.data.map { it[KEY_PALETTE] ?: DEFAULT_PALETTE_ID }

    suspend fun setTerminalPaletteId(id: String) {
        context.dataStore.edit { it[KEY_PALETTE] = id }
    }

    companion object {
        const val DEFAULT_PALETTE_ID = "catppuccin_mocha"

        const val DEFAULT_FONT_SIZE_SP = 14f
        const val MIN_FONT_SIZE_SP = 8f
        const val MAX_FONT_SIZE_SP = 32f

        const val DEFAULT_LINE_SPACING = 1.0f
        const val MIN_LINE_SPACING = 0.9f
        const val MAX_LINE_SPACING = 1.6f

        private val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        private val KEY_SESSIONS_TAB_FIRST = booleanPreferencesKey("sessions_tab_first")
        private val KEY_FONT_SIZE_SP = floatPreferencesKey("terminal_font_size_sp")
        private val KEY_LINE_SPACING = floatPreferencesKey("terminal_line_spacing")
        private val KEY_PALETTE = stringPreferencesKey("terminal_palette")
    }
}
