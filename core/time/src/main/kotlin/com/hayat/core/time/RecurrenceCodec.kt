package com.hayat.core.time

import com.hayat.core.model.DateRule
import com.hayat.core.model.MonthOverflow
import com.hayat.core.model.TimeRule
import com.hayat.core.model.WeekdaySet
import java.time.LocalTime

/**
 * Compact, stable, human-readable string encoding of rules for database columns and backups.
 *
 * DateRule: `ONCE` | `DAILY;n` | `WEEKLY;mask;n` | `MONTHLY;dom;n;overflow` | `YEARLY;month;dom;overflow`
 * TimeRule: `AT;08:00,20:00` | `EVERY;60;08:00;22:00`
 */
object RecurrenceCodec {
    fun encode(rule: DateRule): String = when (rule) {
        DateRule.Once -> "ONCE"
        is DateRule.Daily -> "DAILY;${rule.everyNDays}"
        is DateRule.Weekly -> "WEEKLY;${rule.days.mask};${rule.everyNWeeks}"
        is DateRule.Monthly -> "MONTHLY;${rule.dayOfMonth};${rule.everyNMonths};${rule.overflow}"
        is DateRule.Yearly -> "YEARLY;${rule.month};${rule.dayOfMonth};${rule.overflow}"
    }

    fun decodeDateRule(value: String): DateRule {
        val p = value.split(';')
        return when (p[0]) {
            "ONCE" -> DateRule.Once
            "DAILY" -> DateRule.Daily(p[1].toInt().also { require(it in 1..3650) })
            "WEEKLY" -> DateRule.Weekly(WeekdaySet(p[1].toInt()), p[2].toInt().also { require(it in 1..520) })
            "MONTHLY" -> DateRule.Monthly(p[1].toInt().also { require(it in 1..31) }, p[2].toInt().also { require(it in 1..120) }, MonthOverflow.valueOf(p[3]))
            "YEARLY" -> DateRule.Yearly(p[1].toInt().also { require(it in 1..12) }, p[2].toInt().also { require(it in 1..31) }, MonthOverflow.valueOf(p[3]))
            else -> throw IllegalArgumentException("unknown date rule")
        }
    }

    fun encode(rule: TimeRule): String = when (rule) {
        is TimeRule.At -> "AT;" + rule.times.distinct().sorted().joinToString(",") { it.toString() }
        is TimeRule.Every -> "EVERY;${rule.intervalMinutes};${rule.windowStart};${rule.windowEnd}"
    }

    fun decodeTimeRule(value: String): TimeRule {
        val p = value.split(';')
        return when (p[0]) {
            "AT" -> TimeRule.At(p[1].split(',').filter { it.isNotBlank() }.map(LocalTime::parse))
            "EVERY" -> TimeRule.Every(p[1].toInt().also { require(it in 1..1440) }, LocalTime.parse(p[2]), LocalTime.parse(p[3]))
            else -> throw IllegalArgumentException("unknown time rule")
        }
    }

    fun decodeDateRuleOrNull(value: String?): DateRule? = value?.let { runCatching { decodeDateRule(it) }.getOrNull() }
}
