package com.hayat.core.testing

import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.reminder.AlarmScheduler
import com.hayat.core.domain.reminder.ReminderNotifier
import com.hayat.core.domain.repository.DeliveryLogRepository
import com.hayat.core.domain.repository.OccurrenceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.domain.repository.TaskRepository
import com.hayat.core.model.AlarmDeliveryLog
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.DeliveryEvent
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderActionLog
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Task
import com.hayat.core.model.TaskStatus
import com.hayat.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FakeTimeProvider(var instant: Instant = Instant.parse("2026-01-01T00:00:00Z"), var zoneId: ZoneId = ZoneId.of("Asia/Dhaka")) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    fun advance(d: Duration) { instant = instant.plus(d) }
    fun set(local: String) { instant = java.time.LocalDateTime.parse(local).atZone(zoneId).toInstant() }
}

class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun newId(): String = "$prefix-${++n}"
}

class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state
    override suspend fun current(): UserPreferences = state.value
    override suspend fun update(transform: (UserPreferences) -> UserPreferences) { state.value = transform(state.value) }
}

class FakeAlarmScheduler(var exact: Boolean = true) : AlarmScheduler {
    /** requestCode → (at, style) */
    val alarms = linkedMapOf<Int, Pair<Instant, ReminderStyle>>()
    override fun canScheduleExact() = exact
    override fun schedule(requestCode: Int, reminderId: String, at: Instant, style: ReminderStyle): AlarmPrecision {
        alarms[requestCode] = at to style
        return if (exact) AlarmPrecision.EXACT else AlarmPrecision.INEXACT
    }
    override fun cancel(requestCode: Int) { alarms.remove(requestCode) }
}

class FakeNotifier(var enabled: Boolean = true) : ReminderNotifier {
    val shown = mutableListOf<Pair<String, String>>() // reminderId, occurrenceId
    val silentFlags = mutableListOf<Boolean>()
    val cancelled = mutableListOf<String>()
    var missedSummaries = mutableListOf<Int>()
    override fun show(reminder: Reminder, occurrence: ReminderOccurrence, silent: Boolean): Boolean {
        if (!enabled) return false
        shown += reminder.id to occurrence.id
        silentFlags += silent
        return true
    }
    override fun cancel(reminder: Reminder) { cancelled += reminder.id }
    override fun showMissedSummary(count: Int) { missedSummaries.add(count) }
    override fun canPostNotifications() = enabled
}

class FakeDeliveryLog : DeliveryLogRepository {
    val events = mutableListOf<AlarmDeliveryLog>()
    override suspend fun log(event: DeliveryEvent, reminderId: String?, occurrenceId: String?, detail: String) {
        events += AlarmDeliveryLog("log-${events.size}", occurrenceId, reminderId, event, Instant.EPOCH, detail)
    }
    override fun observeRecent(limit: Int): Flow<List<AlarmDeliveryLog>> = MutableStateFlow(events.takeLast(limit))
    override suspend fun pruneBefore(cutoff: Instant) = 0
    fun count(e: DeliveryEvent) = events.count { it.event == e }
}

class InMemoryReminderRepository : ReminderRepository {
    private val state = MutableStateFlow<Map<String, Reminder>>(emptyMap())
    private val deleted = mutableSetOf<String>()
    private var nextCode = 1
    val all: Collection<Reminder> get() = state.value.values

    override fun observeReminders() = state.map { m -> m.values.filter { it.id !in deleted } }
    override fun observeReminder(id: String) = state.map { it[id]?.takeIf { r -> r.id !in deleted } }
    override suspend fun getReminder(id: String) = state.value[id]?.takeIf { it.id !in deleted }
    override suspend fun getByRequestCode(requestCode: Int) = state.value.values.firstOrNull { it.requestCode == requestCode && it.id !in deleted }
    override suspend fun findByTarget(type: ReminderTargetType, targetId: String) = state.value.values.firstOrNull { it.targetType == type && it.targetId == targetId && it.id !in deleted }
    override suspend fun getActiveReminders() = state.value.values.filter { it.enabled && it.id !in deleted }
    override suspend fun getInactiveRequestCodes() = state.value.values.filter { !it.enabled || it.id in deleted }.map { it.requestCode }
    override suspend fun save(reminder: Reminder): Reminder {
        val existing = state.value[reminder.id]
        val code = existing?.requestCode ?: reminder.requestCode.takeIf { it != 0 } ?: nextCode++
        val saved = reminder.copy(requestCode = code)
        state.value = state.value + (saved.id to saved)
        deleted.remove(saved.id)
        return saved
    }
    override suspend fun setEnabled(id: String, enabled: Boolean, at: Instant) { state.value[id]?.let { state.value = state.value + (id to it.copy(enabled = enabled)) } }
    override suspend fun softDelete(id: String, at: Instant) { deleted += id }
}

class InMemoryOccurrenceRepository : OccurrenceRepository {
    val items = linkedMapOf<String, ReminderOccurrence>()
    val actions = mutableListOf<ReminderActionLog>()
    private val flow = MutableStateFlow(0)
    private fun touch() { flow.value++ }
    private val pending = setOf(OccurrenceStatus.SCHEDULED, OccurrenceStatus.SNOOZED)
    private val open = pending + OccurrenceStatus.DELIVERED

    fun forReminder(id: String) = items.values.filter { it.reminderId == id }.sortedBy { it.scheduledAt }

    override suspend fun insertIfAbsent(occurrences: List<ReminderOccurrence>): Int {
        var n = 0
        occurrences.forEach { o ->
            if (items.values.none { it.reminderId == o.reminderId && it.scheduledAt == o.scheduledAt }) { items[o.id] = o; n++ }
        }
        touch(); return n
    }
    override suspend fun get(id: String) = items[id]
    override suspend fun nextPending(reminderId: String) = items.values.filter { it.reminderId == reminderId && it.status in pending }.minByOrNull { it.effectiveAt }
    override suspend fun duePending(reminderId: String, until: Instant) = items.values.filter { it.reminderId == reminderId && it.status in pending && !it.effectiveAt.isAfter(until) }.sortedBy { it.effectiveAt }
    override suspend fun transition(ids: List<String>, from: Set<OccurrenceStatus>, to: OccurrenceStatus, at: Instant): Int {
        var n = 0
        ids.forEach { id -> items[id]?.takeIf { it.status in from }?.let { items[id] = it.copy(status = to, updatedAt = at); n++ } }
        touch(); return n
    }
    override suspend fun markDelivered(id: String, at: Instant): Int {
        val o = items[id]?.takeIf { it.status in pending } ?: return 0
        items[id] = o.copy(status = OccurrenceStatus.DELIVERED, deliveredAt = at, attemptCount = o.attemptCount + 1, updatedAt = at); touch(); return 1
    }
    override suspend fun markCompleted(id: String, status: OccurrenceStatus, at: Instant): Int {
        val o = items[id]?.takeIf { it.status in open } ?: return 0
        items[id] = o.copy(status = status, completedAt = at, updatedAt = at); touch(); return 1
    }
    override suspend fun snooze(id: String, until: Instant, at: Instant): Int {
        val o = items[id]?.takeIf { it.status in open } ?: return 0
        items[id] = o.copy(status = OccurrenceStatus.SNOOZED, snoozedUntil = until, updatedAt = at); touch(); return 1
    }
    override suspend fun setPrecision(id: String, precision: AlarmPrecision) { items[id]?.let { items[id] = it.copy(precision = precision) } }
    override suspend fun deleteFutureScheduled(reminderId: String, after: Instant): Int {
        val ids = items.values.filter { it.reminderId == reminderId && it.status == OccurrenceStatus.SCHEDULED && it.scheduledAt.isAfter(after) }.map { it.id }
        ids.forEach { items.remove(it) }; touch(); return ids.size
    }
    override suspend fun cancelPending(reminderId: String, at: Instant): Int =
        transition(items.values.filter { it.reminderId == reminderId && it.status in pending }.map { it.id }, pending, OccurrenceStatus.CANCELLED, at)
    override suspend fun stalePending(cutoff: Instant) = items.values.filter { it.status in pending && it.effectiveAt.isBefore(cutoff) }
    var requiresCompletion: (String) -> Boolean = { false }
    override suspend fun staleDeliveredRequiringCompletion(cutoff: Instant) =
        items.values.filter { it.status == OccurrenceStatus.DELIVERED && requiresCompletion(it.reminderId) && (it.deliveredAt ?: it.scheduledAt).isBefore(cutoff) }
    override suspend fun deliveredBefore(reminderId: String, before: Instant) = items.values.filter { it.reminderId == reminderId && it.status == OccurrenceStatus.DELIVERED && it.scheduledAt.isBefore(before) }
    override suspend fun findOpen(reminderId: String, date: LocalDate, time: LocalTime?) =
        items.values.filter { it.reminderId == reminderId && it.status in open && it.localDate == date && (time == null || it.localTime == time) }.minByOrNull { it.scheduledAt }
    override fun observeUpcoming(from: Instant, limit: Int) = flow.map { items.values.filter { it.status in pending && !it.effectiveAt.isBefore(from) }.sortedBy { it.effectiveAt }.take(limit) }
    override fun observeBetween(from: Instant, to: Instant) = flow.map { items.values.filter { !it.scheduledAt.isBefore(from) && it.scheduledAt.isBefore(to) } }
    override fun observeForReminder(reminderId: String, limit: Int) = flow.map { forReminder(reminderId).takeLast(limit) }
    override suspend fun logAction(occurrenceId: String, action: ReminderAction, at: Instant, detail: String) { actions += ReminderActionLog("a${actions.size}", occurrenceId, action, at, detail) }
}

class InMemoryTaskRepository : TaskRepository {
    val state = MutableStateFlow<Map<String, Task>>(emptyMap())
    override fun observeTasks() = state.map { it.values.toList() }
    override fun observeTask(id: String) = state.map { it[id] }
    override suspend fun getTask(id: String) = state.value[id]
    override suspend fun save(task: Task) { state.value = state.value + (task.id to task) }
    override suspend fun setStatusCompleted(id: String, completed: Boolean, at: Instant) {
        state.value[id]?.let { save(it.copy(status = if (completed) TaskStatus.COMPLETED else TaskStatus.OPEN, completedAt = if (completed) at else null)) }
    }
    override suspend fun setArchived(ids: Collection<String>, archived: Boolean, at: Instant) { ids.forEach { id -> state.value[id]?.let { save(it.copy(isArchived = archived)) } } }
    override suspend fun softDelete(ids: Collection<String>, at: Instant) { state.value = state.value - ids.toSet() }
    override suspend fun restore(ids: Collection<String>, at: Instant) {}
    override suspend fun setSubtaskDone(subtaskId: String, done: Boolean, at: Instant) {}
}
