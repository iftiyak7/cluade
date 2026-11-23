package com.hayat.core.domain.reminder

import com.hayat.core.common.AppError
import com.hayat.core.common.AppResult
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.repository.DeliveryLogRepository
import com.hayat.core.domain.repository.OccurrenceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.DeliveryEvent
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.ZonePolicy
import com.hayat.core.time.RecurrenceEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Orchestrates the reminder engine (see docs/alarm-engine.md).
 *
 * All state-changing entry points serialize on one [Mutex]. Feature callbacks
 * ([ReminderTargetHandler], [ReminderSyncContributor]) always run **outside** the lock so they
 * may call back into the scheduler (e.g. via [ReminderLinker]).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    private val reminders: ReminderRepository,
    private val occurrences: OccurrenceRepository,
    private val alarms: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val deliveryLog: DeliveryLogRepository,
    private val preferences: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
    // Providers break the DI cycle scheduler -> handlers -> use cases -> linker -> scheduler.
    private val handlersProvider: Provider<Map<ReminderTargetType, @JvmSuppressWildcards ReminderTargetHandler>>,
    private val contributorsProvider: Provider<Set<@JvmSuppressWildcards ReminderSyncContributor>>,
) {
    private val handlers: Map<ReminderTargetType, ReminderTargetHandler> get() = handlersProvider.get()
    private val contributors: Set<ReminderSyncContributor> get() = contributorsProvider.get()

    private val mutex = Mutex()

    companion object {
        val HORIZON: Duration = Duration.ofDays(7)
        const val MAX_PER_REMINDER = 200
        const val MAX_OS_ALARMS = 400
        /** Pending occurrences later than this are MISSED instead of delivered late. */
        val MISSED_GRACE: Duration = Duration.ofMinutes(15)
        /** Alarms may fire slightly early; occurrences due within this tolerance are delivered. */
        val DUE_TOLERANCE: Duration = Duration.ofSeconds(30)
        /** Delivered occurrences requiring completion become MISSED after this long without an answer. */
        val UNANSWERED_LIMIT: Duration = Duration.ofHours(12)
    }

    // ------------------------------------------------------------------ reconciliation

    /**
     * Idempotent full pass: sync computed reminders, materialise the horizon, mark missed,
     * register the next OS alarm of every active reminder.
     */
    suspend fun reconcile(notifyMissed: Boolean = true) {
        val start = time.now()
        contributors.forEach { c -> runCatching { c.sync(start) }.onFailure { deliveryLog.log(DeliveryEvent.ERROR, null, null, "sync:${c.javaClass.simpleName}") } }
        val missedForHandlers = mutex.withLock {
            val now = time.now()
            val active = reminders.getActiveReminders()
            active.forEach { materialize(it, now) }
            val missed = markMissed(now)
            scheduleAll(active, now)
            reminders.getInactiveRequestCodes().forEach(alarms::cancel)
            deliveryLog.log(DeliveryEvent.RECONCILED, null, null, "active=${active.size} missed=${missed.size}")
            if (notifyMissed && missed.isNotEmpty()) notifier.showMissedSummary(missed.size)
            missed
        }
        notifyHandlers(missedForHandlers) { h, id, o -> h.onMissed(id, o) }
    }

    /** Regenerates future occurrences of one reminder after it was created, edited, enabled or disabled. */
    suspend fun rescheduleReminder(reminderId: String) {
        mutex.withLock {
            val now = time.now()
            val reminder = reminders.getReminder(reminderId)
            occurrences.deleteFutureScheduled(reminderId, now)
            if (reminder == null || !reminder.enabled) {
                occurrences.cancelPending(reminderId, now)
                reminder?.let { alarms.cancel(it.requestCode); notifier.cancel(it) }
                deliveryLog.log(DeliveryEvent.CANCELLED, reminderId, null)
                return
            }
            materialize(reminder, now)
            scheduleNext(reminder, now)
        }
    }

    /** Cancels everything for a reminder that is about to be deleted. */
    suspend fun cancelReminder(reminder: Reminder) {
        mutex.withLock {
            val now = time.now()
            occurrences.deleteFutureScheduled(reminder.id, now)
            occurrences.cancelPending(reminder.id, now)
            alarms.cancel(reminder.requestCode)
            notifier.cancel(reminder)
            deliveryLog.log(DeliveryEvent.CANCELLED, reminder.id, null)
        }
    }

    /** Device time zone changed: DEVICE-policy rules keep their wall-clock time, so regenerate them. */
    suspend fun onTimeZoneChanged() {
        val affected = mutex.withLock { reminders.getActiveReminders() }
            .filter { r -> r.schedules.any { it.schedule.zonePolicy == ZonePolicy.DEVICE } }
        affected.forEach { rescheduleReminder(it.id) }
        reconcile(notifyMissed = false)
    }

    // ------------------------------------------------------------------ alarm delivery

    /** Called by the alarm receiver. Safe against duplicate broadcasts. */
    suspend fun onAlarmFired(requestCode: Int) {
        val handlerWork = mutex.withLock {
            val now = time.now()
            val reminder = reminders.getByRequestCode(requestCode)
            if (reminder == null || !reminder.enabled) {
                alarms.cancel(requestCode)
                deliveryLog.log(DeliveryEvent.DUPLICATE_IGNORED, reminder?.id, null, "inactive")
                return@withLock emptyList()
            }
            deliveryLog.log(DeliveryEvent.FIRED, reminder.id, null)
            val due = occurrences.duePending(reminder.id, now.plus(DUE_TOLERANCE))
            val missed = mutableListOf<ReminderOccurrence>()
            if (due.isEmpty()) {
                deliveryLog.log(DeliveryEvent.DUPLICATE_IGNORED, reminder.id, null, "nothing due")
            } else {
                val toShow = due.maxBy { it.effectiveAt }
                // Anything older that was never shown (e.g. device was off) is not replayed as a burst.
                val older = due.filter { it.id != toShow.id }
                if (older.isNotEmpty()) {
                    val target = if (reminder.requiresCompletion) OccurrenceStatus.MISSED else OccurrenceStatus.DELIVERED
                    occurrences.transition(older.map { it.id }, setOf(OccurrenceStatus.SCHEDULED, OccurrenceStatus.SNOOZED), target, now)
                    if (target == OccurrenceStatus.MISSED) missed += older
                }
                if (occurrences.markDelivered(toShow.id, now) == 0) {
                    deliveryLog.log(DeliveryEvent.DUPLICATE_IGNORED, reminder.id, toShow.id)
                } else {
                    if (reminder.requiresCompletion) {
                        val replaced = occurrences.deliveredBefore(reminder.id, toShow.scheduledAt).filter { it.id != toShow.id }
                        if (replaced.isNotEmpty()) {
                            occurrences.transition(replaced.map { it.id }, setOf(OccurrenceStatus.DELIVERED), OccurrenceStatus.MISSED, now)
                            missed += replaced
                        }
                    }
                    val posted = notifier.show(reminder, toShow, silent = isQuiet(reminder, toShow))
                    deliveryLog.log(if (posted) DeliveryEvent.DELIVERED else DeliveryEvent.NOTIFICATION_BLOCKED, reminder.id, toShow.id)
                }
            }
            materialize(reminder, now)
            scheduleNext(reminder, now)
            missed.map { reminder to it }
        }
        handlerWork.forEach { (r, o) -> r.targetId?.let { id -> runCatching { handlers[r.targetType]?.onMissed(id, o) } } }
    }

    // ------------------------------------------------------------------ user actions

    /**
     * Applies a notification or in-app action. [rescheduleAt] is required for RESCHEDULE.
     * Delivery and completion are separate: DISMISS and OPEN never complete an occurrence.
     */
    suspend fun handleAction(occurrenceId: String, action: ReminderAction, rescheduleAt: Instant? = null): AppResult<Unit> {
        val result = mutex.withLock {
            val now = time.now()
            val occ = occurrences.get(occurrenceId) ?: return@withLock null
            val reminder = reminders.getReminder(occ.reminderId) ?: return@withLock null
            occurrences.logAction(occ.id, action, now, rescheduleAt?.toString().orEmpty())
            val changed = when (action) {
                ReminderAction.DONE -> occurrences.markCompleted(occ.id, OccurrenceStatus.COMPLETED, now)
                ReminderAction.SKIP -> occurrences.markCompleted(occ.id, OccurrenceStatus.SKIPPED, now)
                ReminderAction.SNOOZE -> {
                    val minutes = reminder.snoozeMinutes.takeIf { it > 0 } ?: preferences.current().defaultSnoozeMinutes
                    occurrences.snooze(occ.id, now.plus(Duration.ofMinutes(minutes.toLong())), now)
                }
                ReminderAction.RESCHEDULE -> {
                    val at = rescheduleAt?.takeIf { it.isAfter(now) } ?: return@withLock Triple(reminder, occ, -1)
                    occurrences.snooze(occ.id, at, now)
                }
                ReminderAction.DISMISS, ReminderAction.OPEN -> 0
            }
            if (action != ReminderAction.DISMISS && action != ReminderAction.OPEN) notifier.cancel(reminder)
            if (action != ReminderAction.DISMISS && action != ReminderAction.OPEN) scheduleNext(reminder, now)
            Triple(reminder, occ, changed)
        } ?: return AppResult.Failure(AppError.NotFound("ReminderOccurrence", occurrenceId))

        val (reminder, occ, changed) = result
        if (changed < 0) return AppResult.Failure(AppError.Validation(listOf(com.hayat.core.common.ValidationError("rescheduleAt", com.hayat.core.common.ValidationReason.OUT_OF_RANGE))))
        val targetId = reminder.targetId
        if (changed > 0 && targetId != null) {
            val handler = handlers[reminder.targetType]
            runCatching {
                when (action) {
                    ReminderAction.DONE -> handler?.onDone(targetId, occ)
                    ReminderAction.SKIP -> handler?.onSkip(targetId, occ)
                    else -> Unit
                }
            }.onFailure { deliveryLog.log(DeliveryEvent.ERROR, reminder.id, occ.id, "handler:${action}") }
        }
        return AppResult.Success(Unit)
    }

    /**
     * The user completed the target inside the app (e.g. logged a dose). Completes the matching
     * open occurrence so the notification is cleared and not delivered again. No handler callback.
     */
    suspend fun completeForTarget(type: ReminderTargetType, targetId: String, date: LocalDate, localTime: LocalTime?, status: OccurrenceStatus = OccurrenceStatus.COMPLETED) {
        mutex.withLock {
            val now = time.now()
            val reminder = reminders.findByTarget(type, targetId) ?: return
            val occ = occurrences.findOpen(reminder.id, date, localTime) ?: return
            if (occurrences.markCompleted(occ.id, status, now) > 0) {
                occurrences.logAction(occ.id, if (status == OccurrenceStatus.SKIPPED) ReminderAction.SKIP else ReminderAction.DONE, now, "in-app")
                notifier.cancel(reminder)
                scheduleNext(reminder, now)
            }
        }
    }

    // ------------------------------------------------------------------ internals (call with lock held)

    private suspend fun materialize(reminder: Reminder, now: Instant) {
        val zone = time.zone()
        val from = now.minus(MISSED_GRACE)
        val to = now.plus(HORIZON)
        val generated = reminder.schedules.flatMap { rs ->
            RecurrenceEngine.instantsBetween(rs.schedule, from, to, zone, MAX_PER_REMINDER).map { si ->
                ReminderOccurrence(
                    id = ids.newId(),
                    reminderId = reminder.id,
                    scheduleId = rs.id,
                    scheduledAt = si.instant,
                    localDate = si.localDateTime.toLocalDate(),
                    localTime = si.localDateTime.toLocalTime(),
                    status = OccurrenceStatus.SCHEDULED,
                    updatedAt = now,
                )
            }
        }.distinctBy { it.scheduledAt }.sortedBy { it.scheduledAt }.take(MAX_PER_REMINDER)
        if (generated.isNotEmpty()) occurrences.insertIfAbsent(generated)
    }

    private suspend fun markMissed(now: Instant): List<ReminderOccurrence> {
        val stale = occurrences.stalePending(now.minus(MISSED_GRACE))
        if (stale.isNotEmpty()) occurrences.transition(stale.map { it.id }, setOf(OccurrenceStatus.SCHEDULED, OccurrenceStatus.SNOOZED), OccurrenceStatus.MISSED, now)
        val unanswered = occurrences.staleDeliveredRequiringCompletion(now.minus(UNANSWERED_LIMIT))
        if (unanswered.isNotEmpty()) occurrences.transition(unanswered.map { it.id }, setOf(OccurrenceStatus.DELIVERED), OccurrenceStatus.MISSED, now)
        stale.forEach { deliveryLog.log(DeliveryEvent.MARKED_MISSED, it.reminderId, it.id) }
        return stale + unanswered
    }

    private suspend fun scheduleAll(active: List<Reminder>, now: Instant) {
        val withNext = active.mapNotNull { r -> occurrences.nextPending(r.id)?.let { r to it } }
            .sortedBy { it.second.effectiveAt }
        withNext.forEachIndexed { index, (r, next) ->
            if (index < MAX_OS_ALARMS) register(r, next, now) else alarms.cancel(r.requestCode)
        }
        val scheduledIds = withNext.map { it.first.id }.toSet()
        active.filter { it.id !in scheduledIds }.forEach { alarms.cancel(it.requestCode) }
    }

    private suspend fun scheduleNext(reminder: Reminder, now: Instant) {
        val next = occurrences.nextPending(reminder.id)
        if (next == null) alarms.cancel(reminder.requestCode) else register(reminder, next, now)
    }

    private suspend fun register(reminder: Reminder, next: ReminderOccurrence, now: Instant) {
        val at = maxOf(next.effectiveAt, now.plusSeconds(1))
        val precision = alarms.schedule(reminder.requestCode, reminder.id, at, reminder.style)
        occurrences.setPrecision(next.id, precision)
        deliveryLog.log(if (precision == AlarmPrecision.EXACT) DeliveryEvent.SCHEDULED_EXACT else DeliveryEvent.SCHEDULED_INEXACT, reminder.id, next.id)
    }

    private suspend fun isQuiet(reminder: Reminder, occurrence: ReminderOccurrence): Boolean {
        if (reminder.style == ReminderStyle.ALARM) return false
        if (reminder.category == ReminderCategory.MEDICINE) return false // never silence medication reminders
        val quiet = preferences.current().quietHours
        return quiet.contains(occurrence.localTime)
    }

    private suspend fun notifyHandlers(list: List<ReminderOccurrence>, call: suspend (ReminderTargetHandler, String, ReminderOccurrence) -> Unit) {
        for (o in list) {
            val r = reminders.getReminder(o.reminderId) ?: continue
            val id = r.targetId ?: continue
            val h = handlers[r.targetType] ?: continue
            runCatching { call(h, id, o) }
        }
    }
}
