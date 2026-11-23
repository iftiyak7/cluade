package com.hayat.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hayat.core.designsystem.R

/** Hind Siliguri (SIL OFL 1.1): one family designed for both Bengali and Latin. */
val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_light, FontWeight.Light),
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = HindSiliguri, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

/** Generous line heights (~1.45x body) leave room for Bengali conjuncts and vowel signs. */
val HayatTypography = Typography(
    displayLarge = style(48, 60, FontWeight.SemiBold),
    displayMedium = style(40, 52, FontWeight.SemiBold),
    displaySmall = style(34, 44, FontWeight.SemiBold),
    headlineLarge = style(30, 40, FontWeight.SemiBold),
    headlineMedium = style(26, 36, FontWeight.SemiBold),
    headlineSmall = style(22, 32, FontWeight.SemiBold),
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.Medium, 0.1),
    titleSmall = style(14, 21, FontWeight.Medium, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.2),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.2),
    bodySmall = style(12, 18, FontWeight.Normal, 0.3),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 18, FontWeight.Medium, 0.4),
    labelSmall = style(11, 16, FontWeight.Medium, 0.4),
)
