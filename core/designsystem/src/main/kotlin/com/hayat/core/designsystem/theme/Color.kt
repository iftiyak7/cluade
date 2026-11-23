package com.hayat.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand palette. Screens use Material roles / [HayatExtendedColors], never these directly. */
internal object Palette {
    val Green = Color(0xFF167C5A)
    val DeepGreen = Color(0xFF0D503C)
    val Mint = Color(0xFFE9F5EF)
    val MintStrong = Color(0xFFB9EBD3)
    val WarmBackground = Color(0xFFFAFBF8)
    val DarkBackground = Color(0xFF101714)
    val DarkSurface = Color(0xFF1B2621)
    val Blue = Color(0xFF3B6FD6)
    val Error = Color(0xFFC23B3B)
}

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.Green,
    onPrimary = Color.White,
    primaryContainer = Palette.MintStrong,
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF082016),
    tertiary = Palette.Blue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDAE2FF),
    onTertiaryContainer = Color(0xFF001946),
    error = Palette.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Palette.WarmBackground,
    onBackground = Color(0xFF181D1A),
    surface = Palette.WarmBackground,
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F7F3),
    surfaceContainer = Color(0xFFEEF3EF),
    surfaceContainerHigh = Palette.Mint,
    surfaceContainerHighest = Color(0xFFE1EAE4),
    inverseSurface = Color(0xFF2D322F),
    inverseOnSurface = Color(0xFFEFF2ED),
    inversePrimary = Color(0xFF7FD4AE),
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FD4AE),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Palette.DeepGreen,
    onPrimaryContainer = Palette.MintStrong,
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFAFC6FF),
    onTertiary = Color(0xFF002C71),
    tertiaryContainer = Color(0xFF1D438F),
    onTertiaryContainer = Color(0xFFDAE2FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Palette.DarkBackground,
    onBackground = Color(0xFFE0E4DF),
    surface = Palette.DarkBackground,
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0B110E),
    surfaceContainerLow = Color(0xFF161E1A),
    surfaceContainer = Palette.DarkSurface,
    surfaceContainerHigh = Color(0xFF24302A),
    surfaceContainerHighest = Color(0xFF2E3A34),
    inverseSurface = Color(0xFFE0E4DF),
    inverseOnSurface = Color(0xFF2D322F),
    inversePrimary = Palette.Green,
)

/** High-contrast variants: strongest on-colors and outlines for low-vision users. */
internal fun ColorScheme.highContrast(dark: Boolean): ColorScheme = if (dark) copy(
    onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFE6EEE8),
    outline = Color(0xFFDCE5DD), outlineVariant = Color(0xFFB0B9B2), primary = Color(0xFF9DF0C9),
) else copy(
    onBackground = Color.Black, onSurface = Color.Black, onSurfaceVariant = Color(0xFF1C2420),
    outline = Color(0xFF3F4842), outlineVariant = Color(0xFF59625C), primary = Palette.DeepGreen,
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
)

internal val LightExtended = HayatExtendedColors(
    warning = Color(0xFF7A5000), onWarning = Color.White, warningContainer = Color(0xFFFFE3B0), onWarningContainer = Color(0xFF7A5000),
    success = Palette.Green, successContainer = Palette.MintStrong, onSuccessContainer = Color(0xFF002116),
)
internal val DarkExtended = HayatExtendedColors(
    warning = Color(0xFFFFC86B), onWarning = Color(0xFF4A3200), warningContainer = Color(0xFF4A3200), onWarningContainer = Color(0xFFFFC86B),
    success = Color(0xFF7FD4AE), successContainer = Palette.DeepGreen, onSuccessContainer = Palette.MintStrong,
)

val LocalHayatColors = staticCompositionLocalOf { LightExtended }

/** Swatches offered for routines, habits and subjects; stored as ARGB longs. */
val UserColorChoices: List<Long> = listOf(
    0xFF167C5A, 0xFF3B6FD6, 0xFF8E5BD6, 0xFFC2477A, 0xFFD06A2B, 0xFF9C7A14, 0xFF2F8F9D, 0xFF5E6B73,
)
