package com.hayat.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

    private val bengali = locale.language == "bn"
    private val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else if (bengali) "h:mm" else "h:mm a", locale)
    private val dateFmt = DateTimeFormatter.ofLocalizedDate(
        when (dateStyle) { DateFormatPref.SHORT -> FormatStyle.SHORT; DateFormatPref.MEDIUM -> FormatStyle.MEDIUM; DateFormatPref.LONG -> FormatStyle.LONG },
    ).withLocale(locale)
    private val dayMonthFmt = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    /** English "11:46 AM"; Bengali uses a day-period word: "সকাল ১১:৪৬", "রাত ৯:০৫". */
    fun time(t: LocalTime): String = if (bengali && !use24h) bengaliDayPeriod(t.hour) + " " + digits(timeFmt.format(t)) else digits(timeFmt.format(t))

    /** "250 ml" / "২৫০ মিলি". */
    fun ml(amount: Int): String = if (bengali) number(amount) + " মিলি" else "$amount ml"
    /** "45 min" / "৪৫ মিনিট". */
    fun minutes(m: Int): String = if (bengali) number(m) + " মিনিট" else "$m min"

    /** "14 Jumada II 1448" / "১৪ জমাদিউস সানি ১৪৪৮". */
    fun hijri(day: Int, month: Int, year: Int): String =
        number(day) + " " + com.hayat.core.time.HijriMonths.name(month, bengali) + " " + number(year)

    /** Countdown such as "in 4h 21m" / "৪ ঘণ্টা ২১ মিনিট পর". */
    fun countdown(totalMinutes: Long): String {
        val h = (totalMinutes / 60).toInt(); val m = (totalMinutes % 60).toInt()
        return if (bengali) buildString {
            if (h > 0) append(number(h)).append(" ঘণ্টা ")
            append(number(m)).append(" মিনিট পর")
        } else buildString {
            append("in ")
            if (h > 0) append(h).append("h ")
            append(m).append("m")
        }
    }

    /** "just now", "2 min ago", "3 h ago", "2 days ago" (and Bengali equivalents). */
    fun ago(minutes: Long): String = when {
        minutes < 1 -> if (bengali) "এইমাত্র" else "just now"
        minutes < 60 -> if (bengali) number(minutes) + " মিনিট আগে" else "$minutes min ago"
        minutes < 60 * 24 -> if (bengali) number(minutes / 60) + " ঘণ্টা আগে" else "${minutes / 60} h ago"
        else -> if (bengali) number(minutes / 1440) + " দিন আগে" else "${minutes / 1440} days ago"
    }

    private fun bengaliDayPeriod(hour: Int): String = when (hour) {
        in 4..5 -> "ভোর"
        in 6..11 -> "সকাল"
        in 12..14 -> "দুপুর"
        in 15..17 -> "বিকাল"
        in 18..19 -> "সন্ধ্যা"
        else -> "রাত"
    }
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
    val locale: Locale = config.locales.get(0) ?: Locale.ENGLISH
    val use24 = when (timeFormat) {
        TimeFormatPref.H24 -> true
        TimeFormatPref.H12 -> false
        TimeFormatPref.SYSTEM -> DateFormat.is24HourFormat(context)
    }
    return remember(locale, use24, dateFormat, digitStyle) { HayatFormatter(locale, use24, dateFormat, digitStyle) }
}
