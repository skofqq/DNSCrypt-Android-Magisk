package io.github.skofqq.dnscryptmanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Surface palette shared with Boxy (the Box for Magisk manager), so both apps look
 * like one family: page, card, secondary surface, hairline, text, secondary text, accent.
 * This file is the only place in the app allowed to spell out raw color literals.
 */
@Immutable
data class BoxyColors(
    val page: Color,
    val card: Color,
    val surface2: Color,
    val outline: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val isDark: Boolean,
)

internal val LightPalette = BoxyColors(
    page = Color(0xFFF1F0F7),
    card = Color(0xFFFEFBFF),
    surface2 = Color(0xFFF0F0FA),
    outline = Color.Black.copy(alpha = 0.07f),
    text = Color(0xFF0D1117),
    text2 = Color(0xFF57606A),
    accent = Color(0xFF0969DA),
    isDark = false,
)

internal val DarkPalette = BoxyColors(
    page = Color(0xFF0A0C0F),
    card = Color(0xFF1C1F26),
    surface2 = Color(0xFF252930),
    outline = Color.White.copy(alpha = 0.09f),
    text = Color(0xFFF5F7FA),
    text2 = Color(0xFFB4BCC8),
    accent = Color(0xFF58A6FF),
    isDark = true,
)

val LocalBoxyColors = staticCompositionLocalOf { LightPalette }

object Boxy {
    val colors: BoxyColors
        @Composable get() = LocalBoxyColors.current
}

/** A foreground colour with its soft background, as used by badges and action pills. */
@Immutable
data class Tint(val fg: Color, val bg: Color)

object Tints {
    private fun pair(light: Color, dark: Color, isDark: Boolean): Tint {
        val fg = if (isDark) dark else light
        return Tint(fg, fg.copy(alpha = if (isDark) 0.18f else 0.13f))
    }

    val green @Composable get() = pair(Color(0xFF1F883D), Color(0xFF3FB950), Boxy.colors.isDark)
    val red @Composable get() = pair(Color(0xFFCF222E), Color(0xFFF85149), Boxy.colors.isDark)
    val amber @Composable get() = pair(Color(0xFFBF8700), Color(0xFFD29922), Boxy.colors.isDark)
    val blue @Composable get() = pair(Color(0xFF0969DA), Color(0xFF58A6FF), Boxy.colors.isDark)
    val teal @Composable get() = pair(Color(0xFF0E9F8E), Color(0xFF2FD1B9), Boxy.colors.isDark)
    val purple @Composable get() = pair(Color(0xFF8250DF), Color(0xFFBC8CFF), Boxy.colors.isDark)
    val gray @Composable get() = pair(Color(0xFF57606A), Color(0xFFB4BCC8), Boxy.colors.isDark)
}

/**
 * Material scheme built on the Boxy palette, so stock components (switches, segmented
 * buttons, sliders, dialogs) pick it up. Every container tier is the card colour:
 * cards sit on the page colour, never on a slightly different grey.
 */
internal val BoxyLightScheme: ColorScheme = LightPalette.let { p ->
    lightColorScheme(
        primary = p.accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDEBFB),
        onPrimaryContainer = Color(0xFF0A3069),
        secondary = p.accent,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEBFB),
        onSecondaryContainer = Color(0xFF0A3069),
        tertiary = Color(0xFFBF8700),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFF1C7),
        onTertiaryContainer = Color(0xFF4D3800),
        error = Color(0xFFCF222E),
        onError = Color.White,
        errorContainer = Color(0xFFFFE2E0),
        onErrorContainer = Color(0xFF82071E),
        background = p.page,
        onBackground = p.text,
        surface = p.page,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.text2,
        surfaceTint = Color.Transparent,
        outline = Color(0xFFC9CED6),
        outlineVariant = Color(0xFFE4E3EC),
        surfaceContainerLowest = p.card,
        surfaceContainerLow = p.card,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        surfaceBright = p.card,
        surfaceDim = p.page,
        inverseSurface = Color(0xFF24292F),
        inverseOnSurface = Color(0xFFF5F7FA),
        inversePrimary = Color(0xFF58A6FF),
    )
}

internal val BoxyDarkScheme: ColorScheme = DarkPalette.let { p ->
    darkColorScheme(
        primary = p.accent,
        onPrimary = Color(0xFF04213F),
        primaryContainer = Color(0xFF1E3A5C),
        onPrimaryContainer = Color(0xFFCFE4FF),
        secondary = p.accent,
        onSecondary = Color(0xFF04213F),
        secondaryContainer = Color(0xFF1E3A5C),
        onSecondaryContainer = Color(0xFFCFE4FF),
        tertiary = Color(0xFFD29922),
        onTertiary = Color(0xFF2B1E00),
        tertiaryContainer = Color(0xFF3D3220),
        onTertiaryContainer = Color(0xFFF5DDA8),
        error = Color(0xFFF85149),
        onError = Color(0xFF3D0007),
        errorContainer = Color(0xFF4A1F22),
        onErrorContainer = Color(0xFFFFD7D5),
        background = p.page,
        onBackground = p.text,
        surface = p.page,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.text2,
        surfaceTint = Color.Transparent,
        outline = Color(0xFF3D434D),
        outlineVariant = Color(0xFF2A2E36),
        surfaceContainerLowest = p.card,
        surfaceContainerLow = p.card,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        surfaceBright = p.card,
        surfaceDim = p.page,
        inverseSurface = Color(0xFFE6EDF3),
        inverseOnSurface = Color(0xFF0D1117),
        inversePrimary = Color(0xFF0969DA),
    )
}
