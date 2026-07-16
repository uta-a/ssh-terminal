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
 * アプリ設定の永続化（DataStore）。現状は生体認証ロックの有効/無効のみ。
 */
class SettingsStore(private val context: Context) {

    val biometricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BIOMETRIC] ?: true }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC] = enabled }
    }

    private companion object {
        val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")
    }
}
