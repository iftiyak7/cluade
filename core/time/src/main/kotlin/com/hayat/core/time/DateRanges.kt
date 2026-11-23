package com.hayat.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Start (inclusive) and end (exclusive) instants of a local date in [zone]. */
fun LocalDate.instantRange(zone: ZoneId): Pair<Instant, Instant> =
    atStartOfDay(zone).toInstant() to plusDays(1).atStartOfDay(zone).toInstant()

/** The week containing this date, starting on [firstDay]. */
fun LocalDate.weekDays(firstDay: DayOfWeek): List<LocalDate> {
    val start = with(TemporalAdjusters.previousOrSame(firstDay))
    return (0L until 7L).map { start.plusDays(it) }
}

/** Calendar grid (6 rows x 7) for the month of this date, starting on [firstDay]. */
fun LocalDate.monthGrid(firstDay: DayOfWeek): List<LocalDate> {
    val first = withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(firstDay))
    return (0L until 42L).map { first.plusDays(it) }
}
