package com.hayat.core.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What to do when a monthly/yearly rule asks for a day that the month does not have. */
enum class MonthOverflow { CLAMP, SKIP }

/** DEVICE: the rule follows the device's current zone (default). FIXED: pinned to [Schedule.zoneId]. */
enum class ZonePolicy { DEVICE, FIXED }

/** Which calendar dates a rule matches. Always evaluated relative to a start date. */
sealed interface DateRule {
    /** Exactly the start date. */
    data object Once : DateRule
    data class Daily(val everyNDays: Int = 1) : DateRule
    data class Weekly(val days: WeekdaySet, val everyNWeeks: Int = 1) : DateRule
    data class Monthly(val dayOfMonth: Int, val everyNMonths: Int = 1, val overflow: MonthOverflow = MonthOverflow.CLAMP) : DateRule
    data class Yearly(val month: Int, val dayOfMonth: Int, val overflow: MonthOverflow = MonthOverflow.CLAMP) : DateRule
}

/** At which times on a matching date. */
sealed interface TimeRule {
    /** Wall-clock times, e.g. 08:00 and 20:00. */
    data class At(val times: List<LocalTime>) : TimeRule {
        constructor(vararg time: LocalTime) : this(time.toList())
    }

    /**
     * Elapsed interval inside a daily window, e.g. every 60 minutes from 08:00 to 22:00.
     * If [windowEnd] <= [windowStart] the window crosses midnight.
     */
    data class Every(val intervalMinutes: Int, val windowStart: LocalTime, val windowEnd: LocalTime) : TimeRule
}

/** A complete schedule: dates × times, bounded by start/end dates, interpreted in a zone. */
data class Schedule(
    val dateRule: DateRule,
    val timeRule: TimeRule,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val zonePolicy: ZonePolicy = ZonePolicy.DEVICE,
    val zoneId: ZoneId? = null,
)
