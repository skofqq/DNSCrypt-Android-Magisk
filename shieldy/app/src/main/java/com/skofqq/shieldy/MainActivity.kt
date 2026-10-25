package com.skofqq.shieldy

import com.skofqq.shieldy.data.LegacyMigration
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import com.skofqq.shieldy.ui.components.LocalBaseDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.skofqq.shieldy.data.AppLanguage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skofqq.shieldy.data.AppSettings
import com.skofqq.shieldy.ui.AppRoot
import com.skofqq.shieldy.ui.theme.DnsCryptTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Hold the splash until the first status read (so the app opens on real data),
        // but never longer than SPLASH_MAX_MS: a slow root prompt must not look like a hang.
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !AppStartGate.ready && SystemClock.uptimeMillis() - shownAt < SPLASH_MAX_MS
        }
        splash.setOnExitAnimationListener { provider ->
            // A splash for a start that did not come from the launcher (the restart after
            // moving settings over) has no icon view, and asking for it throws.
            val icon = runCatching { provider.iconView }.getOrNull()
            if (icon == null) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            icon.animate()
                .scaleX(1.25f).scaleY(1.25f).alpha(0f)
                .setDuration(EXIT_MS)
                .setInterpolator(AccelerateInterpolator())
                .start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(EXIT_MS / 3)
                .setDuration(EXIT_MS)
                .withEndAction { provider.remove() }
                .start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        // Settings from DNSCrypt Manager, once: copied with root, then a fresh process
        // so preferences and DataStore read the copied files instead of the empty ones.
        lifecycleScope.launch {
            if (LegacyMigration.runIfNeeded(applicationContext, container.rootShell)) restartProcess()
        }
        setContent {
            val settings by container.preferences.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            DnsCryptTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                // The app's own scale: a denser or looser LocalDensity for the whole tree.
                // Text follows, since sp is built on density; fontScale stays the user's.
                val base = LocalDensity.current
                val factor = settings.uiScalePercent / 100f
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * factor, base.fontScale),
                    LocalBaseDensity provides base,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }

    /**
     * A new process for a fresh start: the activity is launched and this process exits at
     * once, before it can host it, so Android starts the activity in a new one.
     */
    private fun restartProcess() {
        finishAffinity()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
        const val EXIT_MS = 260L
    }
}
