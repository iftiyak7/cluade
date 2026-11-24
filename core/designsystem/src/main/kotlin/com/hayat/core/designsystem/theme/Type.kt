package com.hayat.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hayat.core.designsystem.R

/** Plus Jakarta Sans (SIL OFL 1.1) for English UI: geometric, premium; supports tabular figures. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
)

/** Noto Sans Bengali (SIL OFL 1.1), Bengali + Latin subsets merged, for Bengali UI. */
val NotoBengali = FontFamily(
    Font(R.font.notobengali_regular, FontWeight.Normal),
    Font(R.font.notobengali_medium, FontWeight.Medium),
    Font(R.font.notobengali_semibold, FontWeight.SemiBold),
    Font(R.font.notobengali_bold, FontWeight.Bold),
)

/**
 * Type scale 12 / 14 / 16 / 20 / 28 / 40. No thin weights; digits are tabular so counters and
 * times do not jitter while animating. Bengali gets taller line heights for conjuncts and vowel signs.
 */
private fun typography(family: FontFamily, bengali: Boolean): Typography {
    fun s(size: Int, weight: FontWeight, tracking: Double = 0.0): TextStyle {
        val line = if (bengali) (size * 1.45).toInt() + 2 else (size * 1.3).toInt() + 2
        return TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")
    }
    return Typography(
        displayLarge = s(40, FontWeight.SemiBold, -0.8),
        displayMedium = s(40, FontWeight.SemiBold, -0.8),
        displaySmall = s(28, FontWeight.SemiBold, -0.4),
        headlineLarge = s(28, FontWeight.SemiBold, -0.4),
        headlineMedium = s(28, FontWeight.SemiBold, -0.4),
        headlineSmall = s(20, FontWeight.SemiBold, -0.2),
        titleLarge = s(20, FontWeight.SemiBold, -0.2),
        titleMedium = s(16, FontWeight.SemiBold),
        titleSmall = s(14, FontWeight.SemiBold),
        bodyLarge = s(16, FontWeight.Normal),
        bodyMedium = s(14, FontWeight.Normal),
        bodySmall = s(12, FontWeight.Normal, 0.1),
        labelLarge = s(14, FontWeight.SemiBold),
        labelMedium = s(12, FontWeight.Medium, 0.1),
        labelSmall = s(12, FontWeight.Medium, 0.2),
    )
}

val HayatTypography: Typography = typography(Jakarta, bengali = false)
val HayatTypographyBengali: Typography = typography(NotoBengali, bengali = true)
