package com.skofqq.shieldy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun shouldUseDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/** Boxy's rounded geometry: 20-28 dp cards, pill buttons. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DnsCryptTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = shouldUseDarkTheme(themeMode)
    val context = LocalContext.current
    // minSdk is 31, so dynamic color is always available when it is asked for.
    val dynamicScheme = when {
        !dynamicColor -> null
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    val colorScheme = dynamicScheme?.let { scheme ->
        // Same layering as the Boxy palette: page behind, flat cards on top.
        val card = if (darkTheme) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
        scheme.copy(
            background = scheme.surfaceContainer,
            surface = scheme.surfaceContainer,
            surfaceContainerLowest = card,
            surfaceContainerLow = card,
            surfaceContainerHigh = card,
            surfaceContainerHighest = card,
        )
    } ?: if (darkTheme) BoxyDarkScheme else BoxyLightScheme

    val palette = when {
        dynamicScheme != null -> BoxyColors(
            page = colorScheme.background,
            card = colorScheme.surfaceContainerHigh,
            surface2 = dynamicScheme.surfaceContainerHighest.takeIf { darkTheme }
                ?: dynamicScheme.surfaceContainer,
            outline = dynamicScheme.outlineVariant.copy(alpha = 0.5f),
            text = colorScheme.onSurface,
            text2 = colorScheme.onSurfaceVariant,
            accent = colorScheme.primary,
            isDark = darkTheme,
        )
        darkTheme -> DarkPalette
        else -> LightPalette
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalBoxyColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
