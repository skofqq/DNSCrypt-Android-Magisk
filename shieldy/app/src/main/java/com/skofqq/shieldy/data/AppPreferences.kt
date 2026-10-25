package com.skofqq.shieldy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skofqq.shieldy.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App-level preferences only. Module settings live in module.conf and are never mirrored here.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pollIntervalSeconds: Int = DEFAULT_POLL_SECONDS,
    /** Scale of the whole app, in percent; the system's display settings are left alone. */
    val uiScalePercent: Int = DEFAULT_UI_SCALE,
) {
    companion object {
        const val DEFAULT_POLL_SECONDS = 5
        const val DEFAULT_UI_SCALE = 100
        const val MIN_UI_SCALE = 80
        const val MAX_UI_SCALE = 120
        const val UI_SCALE_STEP = 5
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
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            pollIntervalSeconds = prefs[KEY_POLL] ?: AppSettings.DEFAULT_POLL_SECONDS,
            uiScalePercent = prefs[KEY_UI_SCALE] ?: AppSettings.DEFAULT_UI_SCALE,
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

    suspend fun setUiScale(percent: Int) {
        store.edit { it[KEY_UI_SCALE] = percent.coerceIn(AppSettings.MIN_UI_SCALE, AppSettings.MAX_UI_SCALE) }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_POLL = intPreferencesKey("poll_interval_seconds")
        val KEY_UI_SCALE = intPreferencesKey("ui_scale_percent")
    }
}
