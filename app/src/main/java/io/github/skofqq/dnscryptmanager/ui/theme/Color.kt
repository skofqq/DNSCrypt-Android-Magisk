package io.github.skofqq.dnscryptmanager.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Explicit fallback palette, used when dynamic color is unavailable or switched off.
 * This file is the only place in the app allowed to spell out raw color literals.
 */
private val Teal40 = Color(0xFF00696E)
private val TealContainer40 = Color(0xFF9CF1F6)
private val OnTealContainer40 = Color(0xFF002022)

private val Indigo40 = Color(0xFF4A5B92)
private val IndigoContainer40 = Color(0xFFDCE1FF)
private val OnIndigoContainer40 = Color(0xFF00174B)

private val Amber40 = Color(0xFF7A5900)
private val AmberContainer40 = Color(0xFFFFDF9B)
private val OnAmberContainer40 = Color(0xFF261A00)

private val Red40 = Color(0xFFBA1A1A)
private val RedContainer40 = Color(0xFFFFDAD6)
private val OnRedContainer40 = Color(0xFF410002)

private val Teal80 = Color(0xFF80D4DA)
private val OnTeal80 = Color(0xFF00363A)
private val TealContainer80 = Color(0xFF004F53)

private val Indigo80 = Color(0xFFB3C5FF)
private val OnIndigo80 = Color(0xFF152C61)
private val IndigoContainer80 = Color(0xFF314379)

private val Amber80 = Color(0xFFF3C04B)
private val OnAmber80 = Color(0xFF412D00)
private val AmberContainer80 = Color(0xFF5C4200)

private val Red80 = Color(0xFFFFB4AB)
private val RedContainer80 = Color(0xFF93000A)

private val White = Color(0xFFFFFFFF)

private val LightSurface = Color(0xFFFAFDFD)
private val LightOnSurface = Color(0xFF191C1D)
private val LightSurfaceVariant = Color(0xFFDBE4E5)
private val LightOnSurfaceVariant = Color(0xFF3F4849)
private val LightOutline = Color(0xFF6F797A)

private val DarkSurface = Color(0xFF101415)
private val DarkOnSurface = Color(0xFFE0E3E3)
private val DarkSurfaceVariant = Color(0xFF3F4849)
private val DarkOnSurfaceVariant = Color(0xFFBFC8C9)
private val DarkOutline = Color(0xFF899393)

internal val FallbackLightScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = White,
    primaryContainer = TealContainer40,
    onPrimaryContainer = OnTealContainer40,
    secondary = Indigo40,
    onSecondary = White,
    secondaryContainer = IndigoContainer40,
    onSecondaryContainer = OnIndigoContainer40,
    tertiary = Amber40,
    onTertiary = White,
    tertiaryContainer = AmberContainer40,
    onTertiaryContainer = OnAmberContainer40,
    error = Red40,
    onError = White,
    errorContainer = RedContainer40,
    onErrorContainer = OnRedContainer40,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFBFC8C9),
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF3F7F7),
    surfaceContainer = Color(0xFFEDF1F1),
    surfaceContainerHigh = Color(0xFFE7EBEB),
    surfaceContainerHighest = Color(0xFFE1E5E5),
    inverseSurface = Color(0xFF2E3132),
    inverseOnSurface = Color(0xFFEFF1F1),
    inversePrimary = Teal80,
)

internal val FallbackDarkScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = OnTeal80,
    primaryContainer = TealContainer80,
    onPrimaryContainer = TealContainer40,
    secondary = Indigo80,
    onSecondary = OnIndigo80,
    secondaryContainer = IndigoContainer80,
    onSecondaryContainer = IndigoContainer40,
    tertiary = Amber80,
    onTertiary = OnAmber80,
    tertiaryContainer = AmberContainer80,
    onTertiaryContainer = AmberContainer40,
    error = Red80,
    onError = Color(0xFF690005),
    errorContainer = RedContainer80,
    onErrorContainer = RedContainer40,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF3F4849),
    surfaceContainerLowest = Color(0xFF0B0F10),
    surfaceContainerLow = Color(0xFF181C1D),
    surfaceContainer = Color(0xFF1C2021),
    surfaceContainerHigh = Color(0xFF262A2B),
    surfaceContainerHighest = Color(0xFF313536),
    inverseSurface = Color(0xFFE0E3E3),
    inverseOnSurface = Color(0xFF191C1D),
    inversePrimary = Teal40,
)
