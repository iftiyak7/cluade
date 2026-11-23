package com.hayat.core.time

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class HijriDate(val year: Int, val month: Int, val day: Int) {
    val isRamadan: Boolean get() = month == 9
}

/**
 * Offline Hijri conversion using java.time's Umm al-Qura based [HijrahChronology], plus a user
 * offset (−2…+2 days) because the locally observed date depends on moon sighting.
 * Results are approximate and may differ from local announcements.
 */
object HijriCalendar {
    const val MIN_OFFSET = -2
    const val MAX_OFFSET = 2

    fun fromGregorian(date: LocalDate, offsetDays: Int = 0): HijriDate? = runCatching {
        val adjusted = date.plusDays(offsetDays.coerceIn(MIN_OFFSET, MAX_OFFSET).toLong())
        val h: HijrahDate = HijrahChronology.INSTANCE.date(adjusted)
        HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }.getOrNull() // outside the supported range of the chronology data
}
