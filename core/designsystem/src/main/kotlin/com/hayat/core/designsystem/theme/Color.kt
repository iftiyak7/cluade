package com.hayat.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand palette (design system v3). Screens use Material roles / [HayatExtendedColors], never these directly. */
internal object Palette {
    val Emerald = Color(0xFF0B6B57)
    val EmeraldPressed = Color(0xFF08523F)
    val Gold = Color(0xFFB08A2E)
    val LightBg = Color(0xFFF5F3EE)
    val LightText = Color(0xFF121614)
    val LightText2 = Color(0xFF5E6763)
    val LightBorder = Color(0xFFE6E2D8)
    val DarkBg = Color(0xFF0B1220)
    val DarkSurface = Color(0xFF131C2E)
    val DarkRaised = Color(0xFF1A2438)
    val DarkEmerald = Color(0xFF2CC59B)
    val DarkGold = Color(0xFFD8B866)
    val DarkText = Color(0xFFF2F5FA)
    val DarkText2 = Color(0xFF94A0B8)
    val DarkBorder = Color(0xFF24314A)
    val Error = Color(0xFFB3261E)
}

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EFEB),
    onPrimaryContainer = Palette.EmeraldPressed,
    secondary = Palette.LightText2,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEEE9),
    onSecondaryContainer = Palette.LightText,
    tertiary = Palette.Gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8F4E5),
    onTertiaryContainer = Color(0xFF6E560B),
    error = Palette.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFBE9E7),
    onErrorContainer = Color(0xFF7A1A14),
    background = Palette.LightBg,
    onBackground = Palette.LightText,
    surface = Color.White,
    onSurface = Palette.LightText,
    surfaceVariant = Color(0xFFF0EFEA),
    onSurfaceVariant = Palette.LightText2,
    outline = Color(0xFF8E959E),
    outlineVariant = Palette.LightBorder,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF2F1EC),
    surfaceContainerHigh = Color(0xFFEDECE6),
    surfaceContainerHighest = Palette.LightBorder,
    inverseSurface = Color(0xFF262B30),
    inverseOnSurface = Color(0xFFF2F1EC),
    inversePrimary = Palette.DarkEmerald,
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Palette.DarkEmerald,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF123A36),
    onPrimaryContainer = Color(0xFF7FE3C4),
    secondary = Palette.DarkText2,
    onSecondary = Palette.DarkBg,
    secondaryContainer = Palette.DarkRaised,
    onSecondaryContainer = Palette.DarkText,
    tertiary = Palette.DarkGold,
    onTertiary = Color(0xFF3A2C00),
    tertiaryContainer = Color(0xFF2D281C),
    onTertiaryContainer = Palette.DarkGold,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1F1C),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Palette.DarkBg,
    onBackground = Palette.DarkText,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkText,
    surfaceVariant = Palette.DarkRaised,
    onSurfaceVariant = Palette.DarkText2,
    outline = Color(0xFF5E6A67),
    outlineVariant = Palette.DarkBorder,
    surfaceContainerLowest = Palette.DarkBg,
    surfaceContainerLow = Palette.DarkSurface,
    surfaceContainer = Palette.DarkRaised,
    surfaceContainerHigh = Color(0xFF202B40),
    surfaceContainerHighest = Color(0xFF2A3650),
    inverseSurface = Palette.DarkText,
    inverseOnSurface = Color(0xFF1B2321),
    inversePrimary = Palette.Emerald,
)

internal fun ColorScheme.highContrast(dark: Boolean): ColorScheme = if (dark) copy(
    onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFE6EEE8),
    outline = Color(0xFFDCE5DD), outlineVariant = Color(0xFFB0B9B2), primary = Color(0xFF9DF0C9),
) else copy(
    onBackground = Color.Black, onSurface = Color.Black, onSurfaceVariant = Color(0xFF1C2420),
    outline = Color(0xFF3F4842), outlineVariant = Color(0xFF59625C), primary = Palette.EmeraldPressed,
)

/** Semantic colors Material 3 does not define. */
@Immutable
data class HayatExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Pastel accent pairs (container, content) used for feature tiles and charts. */
    val lavender: Pair<Color, Color>,
    val amber: Pair<Color, Color>,
    val mint: Pair<Color, Color>,
    val pink: Pair<Color, Color>,
    val sky: Pair<Color, Color>,
    /** Brand gradient for primary call-to-action surfaces. */
    val gradientStart: Color,
    val gradientEnd: Color,
    /** Deep contrast card (like the dark alarm cards) */
    val ink: Color,
    val onInk: Color,
    /** Accent used on ink surfaces (tags, filled segments). */
    val inkAccent: Color,
    /** Hairline border for cards (1 px in dark mode; soft border in light). */
    val hairline: Color,
    /** Accent gold for prayer and achievements. */
    val gold: Color,
    /** Pressed state of the primary colour. */
    val primaryPressed: Color,
    /** Card shadow tint (light theme only; dark theme uses borders). */
    val shadow: Color,
    val isDark: Boolean,
) {
    /** Category tints: water = blue, prayer = gold, habits = violet. */
    val water: Pair<Color, Color> get() = sky
    val prayer: Pair<Color, Color> get() = amber
    val habits: Pair<Color, Color> get() = lavender
}

internal val LightExtended = HayatExtendedColors(
    warning = Color(0xFF8A6D0F), onWarning = Color.White, warningContainer = Color(0xFFF8F4E5), onWarningContainer = Color(0xFF6E560B),
    success = Palette.Emerald, successContainer = Color(0xFFE7F2F0), onSuccessContainer = Palette.EmeraldPressed,
    lavender = Color(0xFFF1EDFA) to Color(0xFF5B3FB8), amber = Color(0xFFF8F4E5) to Color(0xFF8A6D0F),
    mint = Color(0xFFE7F2F0) to Palette.EmeraldPressed, pink = Color(0xFFFAEDF1) to Color(0xFF9A2D4E),
    sky = Color(0xFFE8F1FC) to Color(0xFF1F5FB8),
    gradientStart = Palette.Emerald, gradientEnd = Color(0xFF0F8A70),
    ink = Palette.EmeraldPressed, onInk = Color.White, inkAccent = Color(0xFFE9D48A), hairline = Palette.LightBorder,
    gold = Palette.Gold, primaryPressed = Palette.EmeraldPressed, shadow = Color(0x0F14181F), isDark = false,
)
internal val DarkExtended = HayatExtendedColors(
    warning = Palette.DarkGold, onWarning = Color(0xFF3A2C00), warningContainer = Color(0xFF313122), onWarningContainer = Palette.DarkGold,
    success = Palette.DarkEmerald, successContainer = Color(0xFF123A36), onSuccessContainer = Color(0xFF6FDDBF),
    lavender = Color(0xFF252848) to Color(0xFFC7B6FF), amber = Color(0xFF2D281C) to Palette.DarkGold,
    mint = Color(0xFF123A36) to Color(0xFF6FDDBF), pink = Color(0xFF3A2232) to Color(0xFFFFB3C8),
    sky = Color(0xFF1A2B45) to Color(0xFF9CC6FF),
    gradientStart = Color(0xFF2CC59B), gradientEnd = Color(0xFF1E9E7C),
    ink = Palette.DarkRaised, onInk = Palette.DarkText, inkAccent = Palette.DarkGold, hairline = Color(0x1AFFFFFF),
    gold = Palette.DarkGold, primaryPressed = Color(0xFF2AA889), shadow = Color.Transparent, isDark = true,
)

val LocalHayatColors = staticCompositionLocalOf { LightExtended }

/** Swatches offered for routines, habits and subjects; stored as ARGB longs. */
val UserColorChoices: List<Long> = listOf(
    0xFF0E7C66, 0xFF3B6FD6, 0xFF8E5BD6, 0xFFC2477A, 0xFFD06A2B, 0xFF9C7A14, 0xFF2F8F9D, 0xFF5E6B73,
)
