package io.github.skofqq.dnscryptmanager

import io.github.skofqq.dnscryptmanager.ui.components.LocalBaseDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.animation.AccelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.skofqq.dnscryptmanager.data.AppLanguage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skofqq.dnscryptmanager.data.AppSettings
import io.github.skofqq.dnscryptmanager.ui.AppRoot
import io.github.skofqq.dnscryptmanager.ui.theme.DnsCryptTheme

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
            val icon = provider.iconView
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

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
        const val EXIT_MS = 260L
    }
}
