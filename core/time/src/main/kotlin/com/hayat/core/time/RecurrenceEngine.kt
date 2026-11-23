package com.hayat.core.time

import com.hayat.core.model.DateRule
import com.hayat.core.model.MonthOverflow
import com.hayat.core.model.Schedule
import com.hayat.core.model.TimeRule
import com.hayat.core.model.ZonePolicy
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One concrete firing of a schedule: the absolute instant and the wall-clock label it represents. */
data class ScheduledInstant(val instant: Instant, val localDateTime: LocalDateTime)

/**
 * Pure recurrence calculations. No Android dependencies; every policy is documented in
 * docs/alarm-engine.md and covered by RecurrenceEngineTest.
 */
object RecurrenceEngine {

    /** Safety bound for any single expansion. */
    const val MAX_RESULTS = 2_000

    // ---------------------------------------------------------------- dates

    /** True if [rule] (anchored at [startDate], ending at [endDate]) matches [date]. */
    fun occursOn(rule: DateRule, startDate: LocalDate, endDate: LocalDate?, date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        if (endDate != null && date.isAfter(endDate)) return false
        return when (rule) {
            DateRule.Once -> date == startDate
            is DateRule.Daily -> ChronoUnit.DAYS.between(startDate, date) % rule.everyNDays.coerceAtLeast(1) == 0L
            is DateRule.Weekly -> {
                if (date.dayOfWeek !in rule.days) return false
                val n = rule.everyNWeeks.coerceAtLeast(1)
                if (n == 1) return true
                val anchor = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ChronoUnit.WEEKS.between(anchor, week) % n == 0L
            }
            is DateRule.Monthly -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(date))
                if (months % rule.everyNMonths.coerceAtLeast(1) != 0L) return false
                monthlyDate(YearMonth.from(date), rule.dayOfMonth, rule.overflow) == date
            }
            is DateRule.Yearly -> yearlyDate(date.year, rule.month, rule.dayOfMonth, rule.overflow) == date
        }
    }

    /** All matching dates in [from]..[to] (inclusive), ascending. */
    fun datesBetween(rule: DateRule, startDate: LocalDate, endDate: LocalDate?, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val first = maxOf(from, startDate)
        val last = if (endDate != null) minOf(to, endDate) else to
        if (last.isBefore(first)) return emptyList()
        if (rule == DateRule.Once) return if (startDate in first..last) listOf(startDate) else emptyList()
        val result = ArrayList<LocalDate>()
        var d = first
        while (!d.isAfter(last) && result.size < MAX_RESULTS) {
            if (occursOn(rule, startDate, endDate, d)) result += d
            d = d.plusDays(1)
        }
        return result
    }

    /** First matching date strictly after [after], or null if the rule has ended. Searches up to ~10 years. */
    fun nextDate(rule: DateRule, startDate: LocalDate, endDate: LocalDate?, after: LocalDate): LocalDate? {
        if (rule == DateRule.Once) return startDate.takeIf { it.isAfter(after) }
        var d = maxOf(after.plusDays(1), startDate)
        val limit = after.plusYears(10)
        while (!d.isAfter(limit)) {
            if (endDate != null && d.isAfter(endDate)) return null
            if (occursOn(rule, startDate, endDate, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** The date for [dayOfMonth] in [month], applying [overflow] for short months. Null when skipped. */
    fun monthlyDate(month: YearMonth, dayOfMonth: Int, overflow: MonthOverflow): LocalDate? {
        val dom = dayOfMonth.coerceIn(1, 31)
        return if (dom <= month.lengthOfMonth()) month.atDay(dom)
        else when (overflow) {
            MonthOverflow.CLAMP -> month.atEndOfMonth()
            MonthOverflow.SKIP -> null
        }
    }

    fun yearlyDate(year: Int, month: Int, dayOfMonth: Int, overflow: MonthOverflow): LocalDate? =
        monthlyDate(YearMonth.of(year, month.coerceIn(1, 12)), dayOfMonth, overflow)

    // ---------------------------------------------------------------- instants

    /**
     * Resolves a wall-clock date-time in [zone]:
     *  - DST gap (time does not exist): shifted later by the gap length.
     *  - DST overlap (time occurs twice): the earlier offset.
     */
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.ofLocal(LocalDateTime.of(date, time), zone, null)

    fun zoneFor(schedule: Schedule, deviceZone: ZoneId): ZoneId =
        if (schedule.zonePolicy == ZonePolicy.FIXED && schedule.zoneId != null) schedule.zoneId!! else deviceZone

    /**
     * All firings of [schedule] with instant in [from, to), ascending, de-duplicated, capped at [limit].
     */
    fun instantsBetween(
        schedule: Schedule,
        from: Instant,
        to: Instant,
        deviceZone: ZoneId,
        limit: Int = MAX_RESULTS,
    ): List<ScheduledInstant> {
        if (!to.isAfter(from)) return emptyList()
        val zone = zoneFor(schedule, deviceZone)
        // Include the previous day so windows crossing midnight are not lost.
        val fromDate = from.atZone(zone).toLocalDate().minusDays(1)
        val toDate = to.atZone(zone).toLocalDate()
        val dates = datesBetween(schedule.dateRule, schedule.startDate, schedule.endDate, fromDate, toDate)
        val out = sortedMapOf<Instant, LocalDateTime>()
        for (date in dates) {
            for (si in firingsOnDate(schedule.timeRule, date, zone)) {
                if (!si.instant.isBefore(from) && si.instant.isBefore(to)) out.putIfAbsent(si.instant, si.localDateTime)
            }
            if (out.size >= limit) break
        }
        return out.entries.take(limit).map { ScheduledInstant(it.key, it.value) }
    }

    /** First firing at or after [from], searching up to [horizonDays]. */
    fun nextInstant(schedule: Schedule, from: Instant, deviceZone: ZoneId, horizonDays: Long = 400): ScheduledInstant? =
        instantsBetween(schedule, from, from.plus(Duration.ofDays(horizonDays)), deviceZone, limit = 1).firstOrNull()

    /** Firings generated by [timeRule] for a single matching [date]. */
    fun firingsOnDate(timeRule: TimeRule, date: LocalDate, zone: ZoneId): List<ScheduledInstant> = when (timeRule) {
        is TimeRule.At -> timeRule.times.distinct().sorted().map { t ->
            val z = resolve(date, t, zone)
            ScheduledInstant(z.toInstant(), z.toLocalDateTime())
        }
        is TimeRule.Every -> {
            val interval = Duration.ofMinutes(timeRule.intervalMinutes.coerceAtLeast(1).toLong())
            val start = resolve(date, timeRule.windowStart, zone).toInstant()
            val endDate = if (timeRule.windowEnd <= timeRule.windowStart) date.plusDays(1) else date
            val end = resolve(endDate, timeRule.windowEnd, zone).toInstant()
            val list = ArrayList<ScheduledInstant>()
            var t = start
            while (!t.isAfter(end) && list.size < MAX_RESULTS) {
                list += ScheduledInstant(t, t.atZone(zone).toLocalDateTime())
                t = t.plus(interval) // elapsed time, not wall clock
            }
            list
        }
    }
}
