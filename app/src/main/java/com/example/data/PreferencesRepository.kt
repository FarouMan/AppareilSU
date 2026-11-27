package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kernpulse_prefs")

data class UserPreferences(
    val dynamicColorEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoRefreshEnabled: Boolean = true,
    val pollingIntervalMs: Long = 2000L
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh_enabled")
        val POLLING_INTERVAL = longPreferencesKey("polling_interval_ms")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val mode = runCatching { ThemeMode.valueOf(modeStr) }.getOrDefault(ThemeMode.SYSTEM)
        UserPreferences(
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
            themeMode = mode,
            autoRefreshEnabled = prefs[Keys.AUTO_REFRESH] ?: true,
            pollingIntervalMs = prefs[Keys.POLLING_INTERVAL] ?: 2000L
        )
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_REFRESH] = enabled }
    }

    suspend fun setPollingIntervalMs(intervalMs: Long) {
        context.dataStore.edit { it[Keys.POLLING_INTERVAL] = intervalMs.coerceIn(1000L, 10000L) }
    }
}
