package io.github.skofqq.dnscryptmanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.skofqq.dnscryptmanager.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-level preferences only. Module settings live in module.conf and are never mirrored here.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pollIntervalSeconds: Int = DEFAULT_POLL_SECONDS,
) {
    companion object {
        const val DEFAULT_POLL_SECONDS = 5
        val POLL_CHOICES = listOf(3, 5, 10, 30)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppPreferences(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { stored ->
                ThemeMode.entries.firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC] ?: true,
            pollIntervalSeconds = prefs[KEY_POLL] ?: AppSettings.DEFAULT_POLL_SECONDS,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[KEY_DYNAMIC] = enabled }
    }

    suspend fun setPollInterval(seconds: Int) {
        store.edit { it[KEY_POLL] = seconds.coerceIn(1, 600) }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_POLL = intPreferencesKey("poll_interval_seconds")
    }
}
