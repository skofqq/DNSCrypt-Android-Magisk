package com.skofqq.shieldy.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The in-app language switch. Android 13+ keeps the choice itself (per-app language,
 * also reachable from system settings); on 12 the app stores it and applies it to
 * every activity context.
 */
object AppLanguage {

    /**
     * Language tag and its own name; an empty tag means "follow the system". [country]
     * picks the flag shown before the name in the picker.
     */
    data class Option(val tag: String, val nativeName: String, val country: String) {
        val label: String get() = ServerCountries.flag(country) + " " + nativeName
    }

    /** Globe before "System default", matching the flags of the other entries. */
    const val SYSTEM_FLAG = "🌐"

    val OPTIONS = listOf(
        Option("en", "English", "GB"),
        Option("ru", "Русский", "RU"),
        Option("uk", "Українська", "UA"),
        Option("be", "Беларуская", "BY"),
        Option("kk", "Қазақша", "KZ"),
        Option("es", "Español", "ES"),
        Option("fr", "Français", "FR"),
        Option("it", "Italiano", "IT"),
        Option("pt", "Português", "PT"),
        Option("de", "Deutsch", "DE"),
        Option("pl", "Polski", "PL"),
        Option("tr", "Türkçe", "TR"),
    )

    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    /** The chosen tag, or "" for the system language. */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) "" else locales[0].language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "").orEmpty()
        }

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activity with the new configuration itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, tag).apply()
            activity.recreate()
        }
    }

    /** Android 12 only: the base context for an activity, in the stored language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }
}
