package com.hayat.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayPartTest {
    @Test fun `boundaries match the spec`() {
        val expected = mapOf(
            4 to DayPart.NIGHT, 5 to DayPart.MORNING, 7 to DayPart.MORNING, 11 to DayPart.MORNING, 12 to DayPart.AFTERNOON,
            16 to DayPart.AFTERNOON, 17 to DayPart.EVENING, 20 to DayPart.EVENING, 21 to DayPart.NIGHT, 0 to DayPart.NIGHT,
        )
        expected.forEach { (h, p) -> assertEquals("hour $h", p, DayPart.of(h)) }
    }

    @Test fun `7-25 in Dhaka is morning even when UTC is 01-25`() {
        val utc = ZonedDateTime.of(2026, 11, 24, 1, 25, 0, 0, ZoneId.of("UTC"))
        val dhakaHour = utc.withZoneSameInstant(ZoneId.of("Asia/Dhaka")).hour
        assertEquals(7, dhakaHour)
        assertEquals(DayPart.MORNING, DayPart.of(dhakaHour))
    }

    @Test fun `hijri month names`() {
        val h = HijriCalendar.fromGregorian(LocalDate.of(2026, 11, 24))!!
        assertEquals("Jumada II", HijriMonths.name(h.month, bengali = false))
        assertEquals("জমাদিউস সানি", HijriMonths.name(h.month, bengali = true))
    }
}
