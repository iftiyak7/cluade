package com.hayat.core.time

import com.hayat.core.model.DateRule
import com.hayat.core.model.MonthOverflow
import com.hayat.core.model.Schedule
import com.hayat.core.model.TimeRule
import com.hayat.core.model.WeekdaySet
import com.hayat.core.model.ZonePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceEngineTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val newYork = ZoneId.of("America/New_York")
    private val d = LocalDate.of(2026, 1, 1)

    private fun at(vararg t: String) = TimeRule.At(t.map(LocalTime::parse))

    // ---------- date rules

    @Test fun `once matches only start date`() {
        assertTrue(RecurrenceEngine.occursOn(DateRule.Once, d, null, d))
        assertFalse(RecurrenceEngine.occursOn(DateRule.Once, d, null, d.plusDays(1)))
        assertNull(RecurrenceEngine.nextDate(DateRule.Once, d, null, d))
    }

    @Test fun `daily every n days`() {
        val dates = RecurrenceEngine.datesBetween(DateRule.Daily(3), d, null, d, d.plusDays(9))
        assertEquals(listOf(0L, 3L, 6L, 9L).map { d.plusDays(it) }, dates)
    }

    @Test fun `start and end date bound the rule`() {
        val end = d.plusDays(2)
        assertEquals(3, RecurrenceEngine.datesBetween(DateRule.Daily(), d, end, d.minusDays(5), d.plusDays(30)).size)
        assertFalse(RecurrenceEngine.occursOn(DateRule.Daily(), d, end, end.plusDays(1)))
        assertNull(RecurrenceEngine.nextDate(DateRule.Daily(), d, end, end))
    }

    @Test fun `weekly selected weekdays`() {
        // 2026-01-01 is a Thursday
        val rule = DateRule.Weekly(WeekdaySet.of(DayOfWeek.FRIDAY))
        val dates = RecurrenceEngine.datesBetween(rule, d, null, d, d.plusDays(15))
        assertEquals(listOf(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 16)), dates)
    }

    @Test fun `weekly every second week`() {
        val rule = DateRule.Weekly(WeekdaySet.of(DayOfWeek.MONDAY), everyNWeeks = 2)
        val start = LocalDate.of(2026, 1, 5) // Monday
        val dates = RecurrenceEngine.datesBetween(rule, start, null, start, start.plusWeeks(5))
        assertEquals(listOf(0L, 2L, 4L).map { start.plusWeeks(it) }, dates)
    }

    @Test fun `monthly on 31st clamps to month end`() {
        val rule = DateRule.Monthly(31, overflow = MonthOverflow.CLAMP)
        val dates = RecurrenceEngine.datesBetween(rule, d, null, d, LocalDate.of(2026, 5, 31))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31").map(LocalDate::parse),
            dates,
        )
    }

    @Test fun `monthly on 30th skip policy skips february`() {
        val rule = DateRule.Monthly(30, overflow = MonthOverflow.SKIP)
        val dates = RecurrenceEngine.datesBetween(rule, d, null, d, LocalDate.of(2026, 3, 31))
        assertEquals(listOf("2026-01-30", "2026-03-30").map(LocalDate::parse), dates)
    }

    @Test fun `monthly 29th in leap february`() {
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceEngine.monthlyDate(YearMonth.of(2028, 2), 29, MonthOverflow.SKIP))
        assertNull(RecurrenceEngine.monthlyDate(YearMonth.of(2027, 2), 29, MonthOverflow.SKIP))
        assertEquals(LocalDate.of(2027, 2, 28), RecurrenceEngine.monthlyDate(YearMonth.of(2027, 2), 29, MonthOverflow.CLAMP))
    }

    @Test fun `monthly every three months`() {
        val rule = DateRule.Monthly(15, everyNMonths = 3)
        val dates = RecurrenceEngine.datesBetween(rule, LocalDate.of(2026, 1, 15), null, d, LocalDate.of(2026, 12, 31))
        assertEquals(listOf("2026-01-15", "2026-04-15", "2026-07-15", "2026-10-15").map(LocalDate::parse), dates)
    }

    @Test fun `yearly feb 29 birthday`() {
        val start = LocalDate.of(2024, 2, 29)
        val clamp = DateRule.Yearly(2, 29, MonthOverflow.CLAMP)
        val skip = DateRule.Yearly(2, 29, MonthOverflow.SKIP)
        assertEquals(LocalDate.of(2025, 2, 28), RecurrenceEngine.nextDate(clamp, start, null, start))
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceEngine.nextDate(skip, start, null, start))
    }

    // ---------- instants

    @Test fun `daily wall clock in dhaka`() {
        val s = Schedule(DateRule.Daily(), at("08:00"), d)
        val from = d.atStartOfDay(dhaka).toInstant()
        val res = RecurrenceEngine.instantsBetween(s, from, from.plus(Duration.ofDays(3)), dhaka)
        assertEquals(3, res.size)
        // Dhaka is UTC+6, no DST.
        assertEquals(Instant.parse("2026-01-01T02:00:00Z"), res[0].instant)
        assertEquals(Instant.parse("2026-01-02T02:00:00Z"), res[1].instant)
    }

    @Test fun `multiple times per day are sorted and deduplicated`() {
        val s = Schedule(DateRule.Daily(), at("20:00", "08:00", "08:00", "14:00"), d)
        val from = d.atStartOfDay(dhaka).toInstant()
        val res = RecurrenceEngine.instantsBetween(s, from, from.plus(Duration.ofDays(1)), dhaka)
        assertEquals(listOf("08:00", "14:00", "20:00"), res.map { it.localDateTime.toLocalTime().toString() })
    }

    @Test fun `range is half open and limited`() {
        val s = Schedule(DateRule.Daily(), at("08:00"), d)
        val first = Instant.parse("2026-01-01T02:00:00Z")
        val res = RecurrenceEngine.instantsBetween(s, first, first.plus(Duration.ofDays(10)), dhaka, limit = 4)
        assertEquals(4, res.size)
        assertEquals(first, res.first().instant)
        val excl = RecurrenceEngine.instantsBetween(s, first.minusSeconds(60), first, dhaka)
        assertTrue(excl.isEmpty())
    }

    @Test fun `dst gap time is shifted later by gap length`() {
        // New York 2026-03-08 02:00 -> 03:00; 02:30 does not exist.
        val date = LocalDate.of(2026, 3, 8)
        val z = RecurrenceEngine.resolve(date, LocalTime.of(2, 30), newYork)
        assertEquals(LocalTime.of(3, 30), z.toLocalTime())
        val s = Schedule(DateRule.Daily(), at("02:30"), date)
        val res = RecurrenceEngine.instantsBetween(s, date.atStartOfDay(newYork).toInstant(), date.plusDays(2).atStartOfDay(newYork).toInstant(), newYork)
        assertEquals(2, res.size) // still exactly one per day
        assertEquals(ZonedDateTime.of(2026, 3, 9, 2, 30, 0, 0, newYork).toInstant(), res[1].instant)
    }

    @Test fun `dst overlap fires once at earlier offset`() {
        // New York 2026-11-01 01:30 happens twice.
        val date = LocalDate.of(2026, 11, 1)
        val s = Schedule(DateRule.Daily(), at("01:30"), date)
        val res = RecurrenceEngine.instantsBetween(s, date.atStartOfDay(newYork).toInstant(), date.plusDays(1).atStartOfDay(newYork).toInstant(), newYork)
        assertEquals(1, res.size)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), res[0].instant) // EDT (-4)
    }

    @Test fun `interval uses elapsed time across dst`() {
        // Every 60 min from 00:00 to 04:00 on the spring-forward day: 00:00, 01:00, then 03:00 (elapsed 1h after 01:00 is 03:00 EDT), 04:00.
        val date = LocalDate.of(2026, 3, 8)
        val s = Schedule(DateRule.Once, TimeRule.Every(60, LocalTime.MIDNIGHT, LocalTime.of(4, 0)), date)
        val res = RecurrenceEngine.instantsBetween(s, date.atStartOfDay(newYork).toInstant(), date.plusDays(1).atStartOfDay(newYork).toInstant(), newYork)
        val spacing = res.zipWithNext { a, b -> Duration.between(a.instant, b.instant).toMinutes() }.distinct()
        assertEquals(listOf(60L), spacing)
        assertEquals(listOf("00:00", "01:00", "03:00", "04:00"), res.map { it.localDateTime.toLocalTime().toString() })
    }

    @Test fun `interval every 15 minutes in waking window`() {
        val s = Schedule(DateRule.Daily(), TimeRule.Every(15, LocalTime.of(8, 0), LocalTime.of(9, 0)), d)
        val res = RecurrenceEngine.instantsBetween(s, d.atStartOfDay(dhaka).toInstant(), d.plusDays(1).atStartOfDay(dhaka).toInstant(), dhaka)
        assertEquals(listOf("08:00", "08:15", "08:30", "08:45", "09:00"), res.map { it.localDateTime.toLocalTime().toString() })
    }

    @Test fun `interval window crossing midnight`() {
        val s = Schedule(DateRule.Once, TimeRule.Every(120, LocalTime.of(22, 0), LocalTime.of(2, 0)), d)
        val res = RecurrenceEngine.instantsBetween(s, d.atStartOfDay(dhaka).toInstant(), d.plusDays(2).atStartOfDay(dhaka).toInstant(), dhaka)
        assertEquals(listOf("2026-01-01T22:00", "2026-01-02T00:00", "2026-01-02T02:00"), res.map { it.localDateTime.toString() })
    }

    @Test fun `device zone policy follows device, fixed stays pinned`() {
        val device = Schedule(DateRule.Daily(), at("08:00"), d, zonePolicy = ZonePolicy.DEVICE)
        val fixed = device.copy(zonePolicy = ZonePolicy.FIXED, zoneId = dhaka)
        val london = ZoneId.of("Europe/London")
        val from = Instant.parse("2026-01-01T00:00:00Z")
        val to = from.plus(Duration.ofDays(1))
        assertEquals(Instant.parse("2026-01-01T08:00:00Z"), RecurrenceEngine.instantsBetween(device, from, to, london).first().instant)
        assertEquals(Instant.parse("2026-01-01T02:00:00Z"), RecurrenceEngine.instantsBetween(fixed, from, to, london).first().instant)
    }

    @Test fun `next instant searches forward`() {
        val s = Schedule(DateRule.Weekly(WeekdaySet.of(DayOfWeek.FRIDAY)), at("13:00"), d)
        val next = RecurrenceEngine.nextInstant(s, Instant.parse("2026-01-02T08:00:00Z"), dhaka)!!
        assertEquals(LocalDate.of(2026, 1, 9), next.localDateTime.toLocalDate())
    }

    // ---------- codec

    @Test fun `codec round trips all rules`() {
        val dateRules = listOf(
            DateRule.Once, DateRule.Daily(2), DateRule.Weekly(WeekdaySet.BD_WORKWEEK, 3),
            DateRule.Monthly(31, 2, MonthOverflow.SKIP), DateRule.Yearly(2, 29, MonthOverflow.CLAMP),
        )
        dateRules.forEach { assertEquals(it, RecurrenceCodec.decodeDateRule(RecurrenceCodec.encode(it))) }
        val timeRules = listOf(at("08:00", "20:30"), TimeRule.Every(45, LocalTime.of(7, 0), LocalTime.of(23, 0)))
        timeRules.forEach { assertEquals(it, RecurrenceCodec.decodeTimeRule(RecurrenceCodec.encode(it))) }
    }

    @Test fun `codec rejects malformed input`() {
        assertNull(RecurrenceCodec.decodeDateRuleOrNull("DAILY;0"))
        assertNull(RecurrenceCodec.decodeDateRuleOrNull("MONTHLY;40;1;CLAMP"))
        assertNull(RecurrenceCodec.decodeDateRuleOrNull("garbage"))
    }

    // ---------- hijri & ranges

    @Test fun `hijri conversion with offset`() {
        // 2026-02-18 is expected to be 1 Ramadan 1447 per Umm al-Qura tables.
        val h = HijriCalendar.fromGregorian(LocalDate.of(2026, 2, 18))!!
        assertEquals(1447, h.year)
        assertEquals(9, h.month)
        assertEquals(1, h.day)
        assertTrue(h.isRamadan)
        val shifted = HijriCalendar.fromGregorian(LocalDate.of(2026, 2, 18), offsetDays = -1)!!
        assertEquals(8, shifted.month)
    }

    @Test fun `week and month grids`() {
        val week = LocalDate.of(2026, 1, 1).weekDays(DayOfWeek.SATURDAY)
        assertEquals(LocalDate.of(2025, 12, 27), week.first())
        assertEquals(7, week.size)
        val grid = LocalDate.of(2026, 2, 10).monthGrid(DayOfWeek.SUNDAY)
        assertEquals(42, grid.size)
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
    }
}
