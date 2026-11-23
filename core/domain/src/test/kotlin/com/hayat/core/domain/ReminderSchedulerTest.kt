package com.hayat.core.domain

import com.hayat.core.common.AppResult
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.reminder.ReminderTargetHandler
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.DateRule
import com.hayat.core.model.DeliveryEvent
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.QuietHours
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderSchedule
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Schedule
import com.hayat.core.model.TimeRule
import com.hayat.core.model.UserPreferences
import com.hayat.core.testing.FakeAlarmScheduler
import com.hayat.core.testing.FakeDeliveryLog
import com.hayat.core.testing.FakeNotifier
import com.hayat.core.testing.FakePreferencesRepository
import com.hayat.core.testing.FakeTimeProvider
import com.hayat.core.testing.InMemoryOccurrenceRepository
import com.hayat.core.testing.InMemoryReminderRepository
import com.hayat.core.testing.SequentialIdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderSchedulerTest {
    private val time = FakeTimeProvider().apply { set("2026-01-01T07:00") } // Dhaka
    private val ids = SequentialIdGenerator()
    private val reminders = InMemoryReminderRepository()
    private val occurrences = InMemoryOccurrenceRepository()
    private val alarms = FakeAlarmScheduler()
    private val notifier = FakeNotifier()
    private val log = FakeDeliveryLog()
    private val prefs = FakePreferencesRepository()
    private val handled = mutableListOf<String>()
    private val handler = object : ReminderTargetHandler {
        override val targetType = ReminderTargetType.TASK
        override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) { handled += "done:$targetId" }
        override suspend fun onSkip(targetId: String, occurrence: ReminderOccurrence) { handled += "skip:$targetId" }
        override suspend fun onMissed(targetId: String, occurrence: ReminderOccurrence) { handled += "missed:$targetId" }
    }
    private val scheduler = ReminderScheduler(reminders, occurrences, alarms, notifier, log, prefs, time, ids,
        { mapOf(ReminderTargetType.TASK to handler) }, { emptySet() })

    init { occurrences.requiresCompletion = { id -> reminders.all.first { it.id == id }.requiresCompletion } }

    private suspend fun daily(at: String = "08:00", requiresCompletion: Boolean = false, target: Boolean = false): Reminder {
        val id = ids.newId()
        val r = reminders.save(Reminder(
            id = id, title = "Take a walk", category = ReminderCategory.CUSTOM,
            targetType = if (target) ReminderTargetType.TASK else ReminderTargetType.NONE, targetId = if (target) "task-1" else null,
            requiresCompletion = requiresCompletion,
            schedules = listOf(ReminderSchedule("s-$id", id, Schedule(DateRule.Daily(), TimeRule.At(LocalTime.parse(at)), LocalDate.of(2026, 1, 1)))),
            createdAt = time.now(), updatedAt = time.now(),
        ))
        scheduler.rescheduleReminder(r.id)
        return r
    }

    private fun local(s: String): Instant = java.time.LocalDateTime.parse(s).atZone(time.zone()).toInstant()

    @Test fun `materialises a bounded horizon and schedules the first occurrence`() = runTest {
        val r = daily()
        val occ = occurrences.forReminder(r.id)
        assertEquals(7, occ.size) // 08:00 on Jan 1..7 inside [now-15m, now+7d)
        assertEquals(local("2026-01-01T08:00"), alarms.alarms[r.requestCode]!!.first)
        assertEquals(AlarmPrecision.EXACT, occ.first().precision)
    }

    @Test fun `firing delivers once, schedules next, and ignores duplicate broadcasts`() = runTest {
        val r = daily()
        time.set("2026-01-01T08:00")
        scheduler.onAlarmFired(r.requestCode)
        scheduler.onAlarmFired(r.requestCode) // duplicate delivery of the same broadcast
        assertEquals(1, notifier.shown.size)
        assertEquals(OccurrenceStatus.DELIVERED, occurrences.forReminder(r.id).first().status)
        assertEquals(local("2026-01-02T08:00"), alarms.alarms[r.requestCode]!!.first)
        assertTrue(log.count(DeliveryEvent.DUPLICATE_IGNORED) >= 1)
    }

    @Test fun `dismiss is not completion, done is`() = runTest {
        val r = daily(target = true)
        time.set("2026-01-01T08:00")
        scheduler.onAlarmFired(r.requestCode)
        val occ = occurrences.forReminder(r.id).first()
        scheduler.handleAction(occ.id, ReminderAction.DISMISS)
        assertEquals(OccurrenceStatus.DELIVERED, occurrences.get(occ.id)!!.status)
        assertTrue(handled.isEmpty())
        scheduler.handleAction(occ.id, ReminderAction.DONE)
        assertEquals(OccurrenceStatus.COMPLETED, occurrences.get(occ.id)!!.status)
        assertEquals(listOf("done:task-1"), handled)
        // Done twice does not call the handler twice.
        scheduler.handleAction(occ.id, ReminderAction.DONE)
        assertEquals(1, handled.size)
    }

    @Test fun `snooze reschedules the alarm and redelivers`() = runTest {
        val r = daily()
        time.set("2026-01-01T08:00")
        scheduler.onAlarmFired(r.requestCode)
        val occ = occurrences.forReminder(r.id).first()
        scheduler.handleAction(occ.id, ReminderAction.SNOOZE)
        val snoozed = occurrences.get(occ.id)!!
        assertEquals(OccurrenceStatus.SNOOZED, snoozed.status)
        assertEquals(local("2026-01-01T08:10"), snoozed.snoozedUntil)
        assertEquals(local("2026-01-01T08:10"), alarms.alarms[r.requestCode]!!.first)
        time.set("2026-01-01T08:10")
        scheduler.onAlarmFired(r.requestCode)
        assertEquals(2, notifier.shown.size)
        assertEquals(2, occurrences.get(occ.id)!!.attemptCount)
    }

    @Test fun `reschedule requires a future instant`() = runTest {
        val r = daily()
        time.set("2026-01-01T08:00")
        scheduler.onAlarmFired(r.requestCode)
        val occ = occurrences.forReminder(r.id).first()
        assertTrue(scheduler.handleAction(occ.id, ReminderAction.RESCHEDULE, local("2026-01-01T07:00")) is AppResult.Failure)
        assertTrue(scheduler.handleAction(occ.id, ReminderAction.RESCHEDULE, local("2026-01-01T12:00")) is AppResult.Success)
        assertEquals(local("2026-01-01T12:00"), alarms.alarms[r.requestCode]!!.first)
    }

    @Test fun `alarms that never fired become missed on reconcile without a burst`() = runTest {
        val r = daily()
        // Device was off / app force-stopped from 07:00 to 11:00.
        time.set("2026-01-01T11:00")
        scheduler.reconcile()
        val first = occurrences.forReminder(r.id).first()
        assertEquals(OccurrenceStatus.MISSED, first.status)
        assertTrue(notifier.shown.isEmpty())
        assertEquals(listOf(1), notifier.missedSummaries)
        assertEquals(local("2026-01-02T08:00"), alarms.alarms[r.requestCode]!!.first)
    }

    @Test fun `late delivery within grace still delivers`() = runTest {
        val r = daily()
        time.set("2026-01-01T08:05") // inexact alarm, 5 minutes late
        scheduler.onAlarmFired(r.requestCode)
        assertEquals(1, notifier.shown.size)
    }

    @Test fun `unanswered occurrence requiring completion is missed when the next one is delivered`() = runTest {
        val r = daily(requiresCompletion = true, target = true)
        time.set("2026-01-01T08:00"); scheduler.onAlarmFired(r.requestCode)
        time.set("2026-01-02T08:00"); scheduler.onAlarmFired(r.requestCode)
        val list = occurrences.forReminder(r.id)
        assertEquals(OccurrenceStatus.MISSED, list[0].status)
        assertEquals(OccurrenceStatus.DELIVERED, list[1].status)
        assertEquals(listOf("missed:task-1"), handled)
    }

    @Test fun `disabling cancels pending occurrences and the os alarm`() = runTest {
        val r = daily()
        reminders.setEnabled(r.id, false, time.now())
        scheduler.rescheduleReminder(r.id)
        assertFalse(alarms.alarms.containsKey(r.requestCode))
        assertTrue(occurrences.forReminder(r.id).all { it.status == OccurrenceStatus.CANCELLED })
        // A stale broadcast for the disabled reminder posts nothing.
        scheduler.onAlarmFired(r.requestCode)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test fun `exact alarm denial falls back to inexact and records it`() = runTest {
        alarms.exact = false
        val r = daily()
        assertEquals(AlarmPrecision.INEXACT, occurrences.forReminder(r.id).first().precision)
        assertTrue(log.count(DeliveryEvent.SCHEDULED_INEXACT) >= 1)
    }

    @Test fun `notification permission denied is logged, occurrence still recorded`() = runTest {
        notifier.enabled = false
        val r = daily()
        time.set("2026-01-01T08:00")
        scheduler.onAlarmFired(r.requestCode)
        assertEquals(OccurrenceStatus.DELIVERED, occurrences.forReminder(r.id).first().status)
        assertEquals(1, log.count(DeliveryEvent.NOTIFICATION_BLOCKED))
    }

    @Test fun `time zone change keeps wall clock time for device policy`() = runTest {
        val r = daily()
        time.zoneId = ZoneId.of("Europe/London") // traveller lands in London; instant unchanged (01:00Z)
        scheduler.onTimeZoneChanged()
        val next = occurrences.nextPending(r.id)!!
        assertEquals(LocalTime.of(8, 0), next.localTime)
        assertEquals(Instant.parse("2026-01-01T08:00:00Z"), next.scheduledAt)
        assertEquals(next.scheduledAt, alarms.alarms[r.requestCode]!!.first)
    }

    @Test fun `quiet hours deliver silently`() = runTest {
        prefs.update { it.copy(quietHours = QuietHours(true, LocalTime.of(22, 0), LocalTime.of(9, 0))) }
        val r = daily()
        time.set("2026-01-01T08:00")
        scheduler.onAlarmFired(r.requestCode)
        assertEquals(listOf(true), notifier.silentFlags)
    }

    @Test fun `interval reminders are capped per reminder`() = runTest {
        val id = "interval"
        val r = reminders.save(Reminder(id = id, title = "Water", schedules = listOf(ReminderSchedule("s", id,
            Schedule(DateRule.Daily(), TimeRule.Every(15, LocalTime.MIDNIGHT, LocalTime.of(23, 59)), LocalDate.of(2026, 1, 1)))),
            createdAt = time.now(), updatedAt = time.now()))
        scheduler.rescheduleReminder(r.id)
        assertEquals(ReminderScheduler.MAX_PER_REMINDER, occurrences.forReminder(id).size)
    }

    @Test fun `editing a schedule regenerates future occurrences and keeps history`() = runTest {
        val r = daily()
        time.set("2026-01-01T08:00"); scheduler.onAlarmFired(r.requestCode)
        val edited = reminders.save(r.copy(schedules = listOf(r.schedules.first().copy(schedule = r.schedules.first().schedule.copy(timeRule = TimeRule.At(LocalTime.of(9, 30)))))))
        scheduler.rescheduleReminder(edited.id)
        val list = occurrences.forReminder(r.id)
        assertEquals(OccurrenceStatus.DELIVERED, list.first().status) // history kept
        assertTrue(list.filter { it.status == OccurrenceStatus.SCHEDULED }.all { it.localTime == LocalTime.of(9, 30) })
    }

    @Test fun `in-app completion clears the matching occurrence`() = runTest {
        val r = daily(target = true)
        scheduler.completeForTarget(ReminderTargetType.TASK, "task-1", LocalDate.of(2026, 1, 1), LocalTime.of(8, 0))
        assertEquals(OccurrenceStatus.COMPLETED, occurrences.forReminder(r.id).first().status)
        assertEquals(local("2026-01-02T08:00"), alarms.alarms[r.requestCode]!!.first)
        assertTrue(handled.isEmpty()) // no feedback loop into the feature
    }

    @Test fun `linker is idempotent and removes reminders`() = runTest {
        val linker = ReminderLinker(reminders, scheduler, time, ids)
        val spec = LinkedReminderSpec(ReminderTargetType.TASK, "t9", "Pay rent", category = ReminderCategory.BILL,
            schedules = listOf(Schedule(DateRule.Once, TimeRule.At(LocalTime.of(9, 0)), LocalDate.of(2026, 1, 2))))
        val id1 = linker.upsert(spec)!!
        val countAfterFirst = occurrences.items.size
        val id2 = linker.upsert(spec)!!
        assertEquals(id1, id2)
        assertEquals(countAfterFirst, occurrences.items.size)
        linker.remove(ReminderTargetType.TASK, "t9")
        assertEquals(null, reminders.findByTarget(ReminderTargetType.TASK, "t9"))
        assertFalse(alarms.alarms.containsKey(reminders.all.first { it.id == id1 }.requestCode))
    }

    @Test fun `os alarm count never exceeds the cap`() = runTest {
        repeat(5) { daily(at = "0${it + 1}:00".takeLast(5).padStart(5, '0')) }
        assertTrue(alarms.alarms.size <= ReminderScheduler.MAX_OS_ALARMS)
        assertEquals(5, alarms.alarms.size)
        assertEquals(UserPreferences().defaultSnoozeMinutes, 10)
        assertTrue(Duration.ofDays(7) == ReminderScheduler.HORIZON)
    }
}
