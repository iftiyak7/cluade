package com.hayat.core.domain

import com.hayat.core.domain.analytics.AnalyticsCalculator
import com.hayat.core.domain.analytics.AnalyticsInput
import com.hayat.core.domain.health.MedicinePlanner
import com.hayat.core.domain.health.MedicineValidator
import com.hayat.core.domain.health.SleepCalculator
import com.hayat.core.domain.health.WaterCalculator
import com.hayat.core.domain.productivity.EventCalculator
import com.hayat.core.domain.productivity.FinanceCalculator
import com.hayat.core.domain.productivity.FocusTimerState
import com.hayat.core.domain.productivity.HabitStatsCalculator
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.domain.security.PinPolicy
import com.hayat.core.domain.task.TaskQuery
import com.hayat.core.domain.task.TaskValidator
import com.hayat.core.domain.template.BuiltInTemplates
import com.hayat.core.domain.worship.PrayerTimeCalculator
import com.hayat.core.domain.worship.QuranData
import com.hayat.core.domain.worship.ZikrPresets
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.AsrMadhab
import com.hayat.core.model.CalendarEvent
import com.hayat.core.model.DateRule
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.EventKind
import com.hayat.core.model.FinanceItem
import com.hayat.core.model.FinanceKind
import com.hayat.core.model.FinanceRepeat
import com.hayat.core.model.FocusKind
import com.hayat.core.model.Habit
import com.hayat.core.model.HabitCompletion
import com.hayat.core.model.HabitKind
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineDoseLog
import com.hayat.core.model.MedicineSchedule
import com.hayat.core.model.PomodoroSettings
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerCalcMethod
import com.hayat.core.model.PrayerLocation
import com.hayat.core.model.PrayerSettings
import com.hayat.core.model.Priority
import com.hayat.core.model.Routine
import com.hayat.core.model.RoutineActivity
import com.hayat.core.model.RoutineActivityLog
import com.hayat.core.model.SleepEntry
import com.hayat.core.model.Task
import com.hayat.core.model.TaskFilter
import com.hayat.core.model.TaskSort
import com.hayat.core.model.TaskStatus
import com.hayat.core.model.WaterGoal
import com.hayat.core.model.WaterLog
import com.hayat.core.model.WeekdaySet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class DomainCalculationsTest {
    private val now = Instant.parse("2026-01-10T06:00:00Z")
    private val today = LocalDate.of(2026, 1, 10)
    private val zone = ZoneId.of("Asia/Dhaka")

    private fun task(id: String, due: LocalDate?, status: TaskStatus = TaskStatus.OPEN, p: Priority = Priority.MEDIUM, title: String = id) =
        Task(id, title, dueDate = due, status = status, priority = p, createdAt = now, updatedAt = now)

    // ---------------- tasks

    @Test fun `task filters`() {
        val tasks = listOf(
            task("today", today), task("overdue", today.minusDays(2)), task("tomorrow", today.plusDays(1)),
            task("later", today.plusDays(5)), task("done", today, TaskStatus.COMPLETED), task("nodate", null),
            task("archived", today).copy(isArchived = true),
        )
        fun ids(f: TaskFilter, overdueInToday: Boolean = true) = TaskQuery.apply(tasks, f, today, includeOverdueInToday = overdueInToday).map { it.id }.toSet()
        assertEquals(setOf("today", "overdue"), ids(TaskFilter.TODAY))
        assertEquals(setOf("today"), ids(TaskFilter.TODAY, overdueInToday = false))
        assertEquals(setOf("tomorrow"), ids(TaskFilter.TOMORROW))
        assertEquals(setOf("tomorrow", "later"), ids(TaskFilter.UPCOMING))
        assertEquals(setOf("overdue"), ids(TaskFilter.OVERDUE))
        assertEquals(setOf("done"), ids(TaskFilter.COMPLETED))
        assertEquals(setOf("archived"), ids(TaskFilter.ARCHIVED))
        assertEquals(setOf("nodate"), ids(TaskFilter.NO_DATE))
    }

    @Test fun `task sort and bengali search`() {
        val tasks = listOf(task("a", today, p = Priority.LOW, title = "বাজার করা"), task("b", today, p = Priority.URGENT, title = "Call mother"))
        assertEquals(listOf("b", "a"), TaskQuery.apply(tasks, TaskFilter.ALL, today, sort = TaskSort.PRIORITY).map { it.id })
        assertEquals(listOf("a"), TaskQuery.apply(tasks, TaskFilter.ALL, today, query = "বাজার").map { it.id })
    }

    @Test fun `task validation`() {
        assertTrue(TaskValidator.validate(task("x", null).copy(title = "")).isNotEmpty())
        assertTrue(TaskValidator.validate(task("x", null).copy(recurrence = DateRule.Daily())).isNotEmpty())
        assertTrue(TaskValidator.validate(task("x", today)).isEmpty())
    }

    // ---------------- routines

    @Test fun `routine timeline and conflicts`() {
        val r = Routine("r", "Day", color = 0, startDate = today.minusDays(10), dateRule = DateRule.Weekly(WeekdaySet.of(today.dayOfWeek)),
            activities = listOf(
                RoutineActivity("a1", "r", "Gym", LocalTime.of(6, 0), 60),
                RoutineActivity("a2", "r", "Breakfast", LocalTime.of(6, 30), 30),
                RoutineActivity("a3", "r", "Work", LocalTime.of(9, 0), 240),
            ), createdAt = now, updatedAt = now)
        val logs = listOf(RoutineActivityLog("l", "a1", today, ActivityStatus.COMPLETED, now))
        val items = RoutinePlanner.timeline(listOf(r), logs, today)
        assertEquals(listOf("a1", "a2", "a3"), items.map { it.activity.id })
        assertEquals(1f / 3, RoutinePlanner.completionRate(items), 0.001f)
        assertEquals(1, RoutinePlanner.conflicts(r.activities).size)
        assertTrue(RoutinePlanner.timeline(listOf(r), logs, today.plusDays(1)).isEmpty())
        assertTrue(RoutinePlanner.timeline(listOf(r.copy(isPaused = true)), logs, today).isEmpty())
    }

    @Test fun `bundled templates are well formed`() {
        assertEquals(10, BuiltInTemplates.all.size)
        BuiltInTemplates.all.forEach { t -> assertTrue(t.items.isNotEmpty()); assertTrue(t.items.all { it.durationMinutes > 0 }) }
    }

    // ---------------- water

    @Test fun `water goal history and totals`() {
        val goals = listOf(WaterGoal("g1", 2000, LocalDate.of(2026, 1, 1)), WaterGoal("g2", 2500, LocalDate.of(2026, 1, 8)))
        assertEquals(2000, WaterCalculator.goalFor(LocalDate.of(2026, 1, 5), goals))
        assertEquals(2500, WaterCalculator.goalFor(today, goals))
        val logs = listOf(WaterLog("1", 250, now, today), WaterLog("2", 500, now, today), WaterLog("3", 200, now, today.minusDays(1)))
        val days = WaterCalculator.days(today.minusDays(1), today, logs, goals)
        assertEquals(listOf(200, 750), days.map { it.totalMl })
        assertEquals(0.3f, days[1].progress, 0.001f)
        assertTrue(WaterCalculator.validateAmount(0).isNotEmpty())
        assertTrue(WaterCalculator.validateAmount(250).isEmpty())
    }

    // ---------------- medicine

    @Test fun `medicine doses for a day respect weekdays, course dates and logs`() {
        val m = Medicine("m", "Metformin", dose = "500", unit = "mg", startDate = today.minusDays(3), endDate = today.plusDays(3),
            schedules = listOf(
                MedicineSchedule("s1", "m", LocalTime.of(8, 0)),
                MedicineSchedule("s2", "m", LocalTime.of(20, 0), WeekdaySet.of(DayOfWeek.MONDAY)),
            ), createdAt = now, updatedAt = now)
        val logs = listOf(MedicineDoseLog("l", "m", "s1", today, LocalTime.of(8, 0), DoseStatus.TAKEN, now))
        val doses = MedicinePlanner.dosesFor(today, listOf(m), logs) // 2026-01-10 is a Saturday
        assertEquals(1, doses.size)
        assertEquals(DoseStatus.TAKEN, doses[0].log?.status)
        assertEquals(2, MedicinePlanner.dosesFor(LocalDate.of(2026, 1, 12), listOf(m), logs).size)
        assertTrue(MedicinePlanner.dosesFor(today.plusDays(4), listOf(m), logs).isEmpty())
        assertTrue(MedicineValidator.validate(m.copy(schedules = m.schedules + m.schedules.first().copy(id = "dup"))).isNotEmpty())
        assertTrue(m.copy(stock = 3.0, refillThreshold = 5.0).needsRefill)
    }

    // ---------------- habits

    @Test fun `habit streaks ignore unscheduled days and today until done`() {
        val h = Habit("h", "Read", weekdays = WeekdaySet.ALL, color = 0, createdAt = now, updatedAt = now)
        val c = (1..4).map { HabitCompletion("c$it", "h", today.minusDays(it.toLong()), 1) }
        val s = HabitStatsCalculator.stats(h, c, today.minusDays(6), today)
        assertEquals(4, s.currentStreak) // today not done yet: does not break the streak
        assertEquals(4, s.bestStreak)
        val withToday = HabitStatsCalculator.stats(h, c + HabitCompletion("t", "h", today, 1), today.minusDays(6), today)
        assertEquals(5, withToday.currentStreak)
        val weekdaysOnly = h.copy(weekdays = WeekdaySet.of(DayOfWeek.MONDAY))
        val ws = HabitStatsCalculator.stats(weekdaysOnly, listOf(HabitCompletion("m", "h", LocalDate.of(2026, 1, 5), 1)), today.minusDays(9), today)
        assertEquals(1, ws.currentStreak)
        val reduce = h.copy(kind = HabitKind.REDUCE, targetPerDay = 0)
        val rs = HabitStatsCalculator.stats(reduce, listOf(HabitCompletion("x", "h", today.minusDays(1), 2)), today.minusDays(3), today)
        assertEquals(1, rs.currentStreak) // today (0 so far) counts as success for REDUCE, yesterday failed
    }

    // ---------------- finance & events

    @Test fun `finance amounts and next due dates`() {
        assertEquals(125050L, FinanceCalculator.parseAmount("1,250.50"))
        assertNull(FinanceCalculator.parseAmount("-5"))
        assertNull(FinanceCalculator.parseAmount("1.234"))
        assertEquals("1250.50", FinanceCalculator.formatMinor(125050))
        val item = FinanceItem("f", "Rent", FinanceKind.RENT, dueDate = LocalDate.of(2026, 1, 31), repeat = FinanceRepeat.MONTHLY, createdAt = now, updatedAt = now)
        assertEquals(LocalDate.of(2026, 2, 28), FinanceCalculator.nextDueDate(item))
        assertNull(FinanceCalculator.nextDueDate(item.copy(repeat = FinanceRepeat.NONE)))
    }

    @Test fun `annual events roll over and compute age`() {
        val e = CalendarEvent("e", "Amma", EventKind.BIRTHDAY, date = LocalDate.of(1970, 1, 5), repeatsAnnually = true, createdAt = now, updatedAt = now)
        assertEquals(LocalDate.of(2027, 1, 5), EventCalculator.nextOccurrence(e, today))
        assertEquals(57, EventCalculator.yearsAt(e, LocalDate.of(2027, 1, 5)))
        val oneOff = e.copy(repeatsAnnually = false, date = today.minusDays(1))
        assertNull(EventCalculator.nextOccurrence(oneOff, today))
    }

    // ---------------- focus timer

    @Test fun `focus timer pause resume and phases`() {
        var s = FocusTimerState(plannedMinutes = 25).start(now)
        s = s.pause(now.plus(Duration.ofMinutes(10)))
        assertEquals(Duration.ofMinutes(15), s.remaining(now.plus(Duration.ofHours(1)))) // paused: no time passes
        s = s.start(now.plus(Duration.ofMinutes(20)))
        assertTrue(s.isFinished(now.plus(Duration.ofMinutes(35))))
        val settings = PomodoroSettings(sessionsBeforeLongBreak = 2)
        val b1 = s.next(settings)
        assertEquals(FocusKind.SHORT_BREAK, b1.kind)
        val f2 = b1.next(settings); assertEquals(FocusKind.FOCUS, f2.kind)
        assertEquals(FocusKind.LONG_BREAK, f2.next(settings).kind)
    }

    // ---------------- security

    @Test fun `pin policy`() {
        assertTrue(PinPolicy.isValid("1234".toCharArray()))
        assertTrue(!PinPolicy.isValid("12a4".toCharArray()))
        assertTrue(!PinPolicy.isValid("123".toCharArray()))
        assertEquals(Duration.ZERO, PinPolicy.lockoutFor(4))
        assertEquals(Duration.ofSeconds(30), PinPolicy.lockoutFor(5))
        assertEquals(Duration.ofSeconds(60), PinPolicy.lockoutFor(6))
        assertEquals(Duration.ofHours(1), PinPolicy.lockoutFor(50))
    }

    @Test fun `sleep validation and averages`() {
        val e = SleepEntry("s", Instant.parse("2026-01-09T17:00:00Z"), Instant.parse("2026-01-10T00:30:00Z"), today)
        assertEquals(450L, e.duration.toMinutes())
        assertTrue(SleepCalculator.validate(e).isEmpty())
        assertTrue(SleepCalculator.validate(e.copy(wakeAt = e.bedAt.minusSeconds(1))).isNotEmpty())
        assertEquals(450, SleepCalculator.averageMinutes(listOf(e)))
    }

    // ---------------- worship

    @Test fun `quran metadata totals`() {
        assertEquals(114, QuranData.surahs.size)
        assertEquals(QuranData.TOTAL_AYAHS, QuranData.surahs.sumOf { it.ayahCount })
        assertEquals("Al-Ikhlas", QuranData.surah(112)!!.nameTransliterated)
    }

    @Test fun `zikr presets have targets and unique keys`() {
        val p = ZikrPresets.presets(now)
        assertEquals(p.size, p.map { it.systemKey }.toSet().size)
        assertTrue(p.all { it.targetCount > 0 && it.arabic.isNotBlank() })
    }

    @Test fun `prayer times match the published Adhan reference example`() {
        // Reference from the Adhan library documentation: Raleigh NC, 2015-07-12, North America, Hanafi.
        val settings = PrayerSettings(
            location = PrayerLocation("Raleigh", 35.78056, -78.6389, "America/New_York"),
            method = PrayerCalcMethod.NORTH_AMERICA, madhab = AsrMadhab.HANAFI,
        )
        val t = PrayerTimeCalculator.compute(LocalDate.of(2015, 7, 12), settings)!!
        fun near(expected: String, actual: java.time.ZonedDateTime) {
            val diff = abs(Duration.between(LocalTime.parse(expected), actual.toLocalTime()).toMinutes())
            assertTrue("expected $expected got ${actual.toLocalTime()}", diff <= 1)
        }
        near("04:42", t.fajr); near("06:08", t.sunrise); near("13:21", t.dhuhr)
        near("18:22", t.asr); near("20:32", t.maghrib); near("21:57", t.isha)
    }

    @Test fun `prayer times for dhaka are ordered and adjustable`() {
        val base = PrayerSettings(location = PrayerLocation("Dhaka", 23.8103, 90.4125, "Asia/Dhaka"))
        val t = PrayerTimeCalculator.compute(today, base)!!
        val order = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
        assertEquals(order, order.sortedBy { it.toInstant() })
        assertTrue(t.dhuhr.hour == 12) // solar noon in Dhaka is ~12:00 local
        val adjusted = PrayerTimeCalculator.compute(today, base.copy(adjustments = mapOf(Prayer.MAGHRIB to 3)))!!
        assertEquals(3L, Duration.between(t.maghrib, adjusted.maghrib).toMinutes())
        assertNull(PrayerTimeCalculator.compute(today, PrayerSettings()))
        val shafi = PrayerTimeCalculator.compute(today, base.copy(madhab = AsrMadhab.SHAFI))!!
        assertTrue(shafi.asr.isBefore(t.asr)) // Hanafi Asr is later
    }

    // ---------------- analytics

    @Test fun `analytics derive from records and ignore future days`() {
        val tasks = listOf(task("a", today, TaskStatus.COMPLETED).copy(completedAt = now), task("b", today), task("c", today.plusDays(3)))
        val water = listOf(WaterLog("w", 2500, now, today))
        val snap = AnalyticsCalculator.compute(AnalyticsInput(tasks = tasks, waterLogs = water), today.minusDays(6), today.plusDays(7), today, zone)
        assertEquals(1, snap.tasks.done); assertEquals(2, snap.tasks.total)
        assertEquals(1, snap.waterGoalDays.done); assertEquals(7, snap.waterGoalDays.total)
        assertEquals(7, snap.activityByDay.size)
        // Deleting the completed task changes the metric.
        val after = AnalyticsCalculator.compute(AnalyticsInput(tasks = tasks.drop(1)), today.minusDays(6), today, today, zone)
        assertEquals(0, after.tasks.done)
    }
}
