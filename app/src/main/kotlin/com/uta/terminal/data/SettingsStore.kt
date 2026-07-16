package com.uta.terminal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * アプリ設定の永続化（DataStore）。生体認証ロックとボトムタブ順の設定を保持する。
 */
class SettingsStore(private val context: Context) {

    val biometricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BIOMETRIC] ?: true }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC] = enabled }
    }

    /** true なら下タブを「ホスト / セッション / 設定」に入れ替える（既定は「セッション / ホスト / 設定」）。 */
    val hostTabFirst: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOST_TAB_FIRST] ?: false }

    suspend fun setHostTabFirst(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HOST_TAB_FIRST] = enabled }
    }

    private companion object {
        val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val KEY_HOST_TAB_FIRST = booleanPreferencesKey("host_tab_first")
    }
}
