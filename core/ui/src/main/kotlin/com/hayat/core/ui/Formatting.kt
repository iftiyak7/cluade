package com.hayat.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import com.hayat.core.model.DateFormatPref
import com.hayat.core.model.DigitStyle
import com.hayat.core.model.TimeFormatPref
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Locale-aware formatting with explicit control over Bengali digits (০১২…). Created once per
 * locale/preference change and provided through [LocalFormatter].
 */
@Immutable
class HayatFormatter(
    val locale: Locale,
    private val use24h: Boolean,
    private val dateStyle: DateFormatPref,
    private val digitStyle: DigitStyle,
) {
    val bengaliDigits: Boolean = when (digitStyle) {
        DigitStyle.BENGALI -> true
        DigitStyle.LATIN -> false
        DigitStyle.AUTO -> locale.language == "bn"
    }

    fun digits(text: String): String = if (!bengaliDigits) text else buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') BENGALI_DIGITS[c - '0'] else c)
    }

    fun number(n: Int): String = digits(n.toString())
    fun number(n: Long): String = digits(n.toString())
    fun decimal(d: Double, maxFraction: Int = 1): String {
        val s = if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.ROOT, "%.${maxFraction}f", d).trimEnd('0').trimEnd('.')
        return digits(s)
    }
    fun percent(fraction: Float): String = digits((fraction * 100).toInt().coerceIn(0, 100).toString()) + "%"

    private val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)
    private val dateFmt = DateTimeFormatter.ofLocalizedDate(
        when (dateStyle) { DateFormatPref.SHORT -> FormatStyle.SHORT; DateFormatPref.MEDIUM -> FormatStyle.MEDIUM; DateFormatPref.LONG -> FormatStyle.LONG },
    ).withLocale(locale)
    private val dayMonthFmt = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun time(t: LocalTime): String = digits(timeFmt.format(t))
    fun date(d: LocalDate): String = digits(dateFmt.format(d))
    fun dayMonth(d: LocalDate): String = digits(dayMonthFmt.format(d))
    fun dateTime(dt: LocalDateTime): String = date(dt.toLocalDate()) + ", " + time(dt.toLocalTime())
    fun monthYear(ym: YearMonth): String = digits(monthYearFmt.format(ym.atDay(1)))
    fun weekdayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayNarrow(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, locale)
    fun weekdayFull(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, locale)
    fun monthName(m: Month): String = m.getDisplayName(TextStyle.FULL, locale)
    fun dayOfMonth(d: LocalDate): String = number(d.dayOfMonth)

    companion object {
        private const val BENGALI_DIGITS = "০১২৩৪৫৬৭৮৯"
        val Default = HayatFormatter(Locale.ENGLISH, use24h = false, dateStyle = DateFormatPref.MEDIUM, digitStyle = DigitStyle.LATIN)
    }
}

val LocalFormatter = staticCompositionLocalOf { HayatFormatter.Default }

@Composable
fun rememberHayatFormatter(timeFormat: TimeFormatPref, dateFormat: DateFormatPref, digitStyle: DigitStyle): HayatFormatter {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(config)[0] ?: Locale.getDefault()
    val use24 = when (timeFormat) {
        TimeFormatPref.H24 -> true
        TimeFormatPref.H12 -> false
        TimeFormatPref.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    return remember(locale, use24, dateFormat, digitStyle) { HayatFormatter(locale, use24, dateFormat, digitStyle) }
}
