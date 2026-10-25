package com.skofqq.shieldy.data

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.os.Process
import com.skofqq.shieldy.root.RootShell

/**
 * One-time move of settings from DNSCrypt Manager (io.github.skofqq.dnscryptmanager),
 * which Shieldy replaces under a new package. Android keeps the two apart, so the files
 * are copied with root and given this app's owner and SELinux label. The old app is
 * never removed here: that is the user's choice, offered by the notice afterwards.
 */
object LegacyMigration {
    const val OLD_PACKAGE = "io.github.skofqq.dnscryptmanager"

    private const val PREFS = "migration"
    private const val KEY_DONE = "done"
    private const val KEY_NOTICE = "notice_pending"

    /** Preference files and data the old app kept; WorkManager's own state is rebuilt. */
    private val PREF_FILES = listOf("exclusions", "blocklists", "app_language")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once after a migration, until the notice about it has been shown. */
    fun noticePending(context: Context): Boolean = prefs(context).getBoolean(KEY_NOTICE, false)

    fun clearNotice(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTICE, false).apply()
    }

    /**
     * Copies the old app's settings if it is installed and this has not run yet. True when
     * files were copied: the process then has to restart, since preferences and DataStore
     * already read their (empty) files into memory.
     */
    suspend fun runIfNeeded(context: Context, shell: RootShell): Boolean {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_DONE, false)) return false
        val installed = runCatching { context.packageManager.getPackageInfo(OLD_PACKAGE, 0) }.isSuccess
        if (!installed) {
            prefs.edit().putBoolean(KEY_DONE, true).commit()
            return false
        }
        val from = RootShell.quote("/data/data/$OLD_PACKAGE")
        val to = RootShell.quote(context.applicationInfo.dataDir)
        val uid = Process.myUid()
        val copyPrefs = PREF_FILES.joinToString("; ") { name ->
            "[ -f $from/shared_prefs/$name.xml ] && cp $from/shared_prefs/$name.xml $to/shared_prefs/"
        }
        val out = shell.execLine(
            "[ -d $from ] || exit 3; " +
                "mkdir -p $to/shared_prefs $to/files/datastore; " +
                "$copyPrefs; " +
                "[ -f $from/files/datastore/app_settings.preferences_pb ] && " +
                "cp $from/files/datastore/app_settings.preferences_pb $to/files/datastore/; " +
                "[ -d $from/files/blocklists ] && cp -r $from/files/blocklists $to/files/; " +
                // restorecon gives app_data_file without the app's MLS categories, which the app
                // may not read; the data dir's own label is the right one.
                "chown -R $uid:$uid $to/shared_prefs $to/files && " +
                "chcon -R \"$(ls -Zd $to | cut -d' ' -f1)\" $to/shared_prefs $to/files; " +
                "cmd locale get-app-locales $OLD_PACKAGE 2>/dev/null"
        ).getOrNull() ?: return false
        // Root refused or the old data is gone: try again on a later start.
        if (out.code == 3 || !out.isSuccess && out.stdout.isEmpty()) return false

        // Android 13+ keeps the per-app language outside the app's files.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tags = out.stdout.lastOrNull()?.substringAfter('[', "")?.substringBefore(']')?.trim().orEmpty()
            if (tags.isNotEmpty()) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
            }
        }
        prefs.edit().putBoolean(KEY_DONE, true).putBoolean(KEY_NOTICE, true).commit()
        return true
    }

    /** Whether the old app is still there, for the notice's "App info" action. */
    fun oldAppInstalled(context: Context): Boolean = runCatching {
        // The int overload: the flags object needs API 33, the app starts at 31.
        context.packageManager.getPackageInfo(OLD_PACKAGE, 0)
    }.isSuccess
}
