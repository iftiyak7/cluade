package com.hayat.core.domain.reminder

import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderSchedule
import com.hayat.core.model.ReminderTargetType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates, updates and removes the reminder owned by a feature record. Idempotent: an unchanged
 * spec does not touch the database or regenerate occurrences.
 */
@Singleton
class ReminderLinker @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    /** Returns the reminder id, or null when the spec has no schedules (the link is removed). */
    suspend fun upsert(spec: LinkedReminderSpec): String? {
        if (spec.schedules.isEmpty()) {
            remove(spec.targetType, spec.targetId)
            return null
        }
        val now = time.now()
        val existing = reminders.findByTarget(spec.targetType, spec.targetId)
        val reminderId = existing?.id ?: ids.newId()
        val schedules = spec.schedules.map { ReminderSchedule(ids.newId(), reminderId, it) }
        if (existing != null && isSame(existing, spec)) return existing.id
        val reminder = (existing ?: Reminder(id = reminderId, title = spec.title, createdAt = now, updatedAt = now)).copy(
            title = spec.title,
            description = spec.description,
            category = spec.category,
            targetType = spec.targetType,
            targetId = spec.targetId,
            priority = spec.priority,
            enabled = spec.enabled,
            style = spec.style,
            requiresCompletion = spec.requiresCompletion,
            schedules = schedules,
            updatedAt = now,
        )
        reminders.save(reminder)
        scheduler.rescheduleReminder(reminderId)
        return reminderId
    }

    suspend fun remove(type: ReminderTargetType, targetId: String) {
        val existing = reminders.findByTarget(type, targetId) ?: return
        scheduler.cancelReminder(existing)
        reminders.softDelete(existing.id, time.now())
    }

    private fun isSame(r: Reminder, spec: LinkedReminderSpec): Boolean =
        r.title == spec.title && r.description == spec.description && r.category == spec.category &&
            r.priority == spec.priority && r.enabled == spec.enabled && r.style == spec.style &&
            r.requiresCompletion == spec.requiresCompletion &&
            r.schedules.map { it.schedule } == spec.schedules
}
