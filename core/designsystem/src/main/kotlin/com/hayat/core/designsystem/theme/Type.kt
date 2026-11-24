package com.hayat.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hayat.core.designsystem.R

/** Inter (SIL OFL 1.1) for English UI. Latin subset; supports tabular figures (`tnum`). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Hind Siliguri (SIL OFL 1.1) for Bengali UI. */
val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold),
)

/**
 * Type scale 12 / 14 / 16 / 20 / 28 / 40. No thin weights; digits are tabular so counters and
 * times do not jitter while animating. Bengali gets taller line heights for conjuncts and vowel signs.
 */
private fun typography(family: FontFamily, bengali: Boolean): Typography {
    fun s(size: Int, weight: FontWeight, tracking: Double = 0.0): TextStyle {
        val line = if (bengali) (size * 1.5).toInt() else (size * 1.3).toInt() + 2
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

val HayatTypography: Typography = typography(Inter, bengali = false)
val HayatTypographyBengali: Typography = typography(HindSiliguri, bengali = true)
