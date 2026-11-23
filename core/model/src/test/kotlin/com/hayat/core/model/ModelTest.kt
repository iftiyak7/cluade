package com.hayat.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ModelTest {
    @Test
    fun `weekday set bit operations`() {
        val set = WeekdaySet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertTrue(DayOfWeek.MONDAY in set)
        assertFalse(DayOfWeek.SUNDAY in set)
        assertEquals(2, set.size)
        assertEquals(WeekdaySet.of(DayOfWeek.MONDAY), set.toggle(DayOfWeek.FRIDAY))
        assertEquals(5, WeekdaySet.BD_WORKWEEK.size)
        assertFalse(DayOfWeek.FRIDAY in WeekdaySet.BD_WORKWEEK)
        assertEquals(0x7F, WeekdaySet.ALL.mask)
    }

    @Test
    fun `habit success depends on kind`() {
        val now = java.time.Instant.EPOCH
        val build = Habit("1", "Read", targetPerDay = 2, color = 0, createdAt = now, updatedAt = now)
        val reduce = build.copy(kind = HabitKind.REDUCE, targetPerDay = 1)
        assertTrue(build.isSuccess(2)); assertFalse(build.isSuccess(1))
        assertTrue(reduce.isSuccess(0)); assertFalse(reduce.isSuccess(3))
    }

    @Test
    fun `quiet hours across midnight`() {
        val q = QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0))
        assertTrue(q.contains(LocalTime.of(23, 30)))
        assertTrue(q.contains(LocalTime.of(6, 59)))
        assertFalse(q.contains(LocalTime.of(7, 0)))
        assertFalse(q.contains(LocalTime.of(12, 0)))
        assertFalse(QuietHours(false).contains(LocalTime.of(23, 0)))
    }
}
