package com.hayat.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ReminderCategory {
    WATER, MEDICINE, MEAL, ZIKR, PRAYER, QURAN, WORK, STUDY, EXERCISE, SLEEP,
    BILL, APPOINTMENT, EVENT, TASK, ROUTINE, HABIT, CUSTOM,
}

/** The feature record a reminder belongs to (generic link, see docs/database.md). */
enum class ReminderTargetType {
    NONE, TASK, ROUTINE_ACTIVITY, MEDICINE, MEDICINE_REFILL, WATER, MEAL, HABIT, PRAYER, ZIKR, QURAN,
    SLEEP_BEDTIME, SLEEP_WAKE, WORKOUT, STUDY, FOCUS, FINANCE, EVENT, NOTE,
}

/** NOTIFICATION = ordinary notification. ALARM = alarm-clock style with full-screen UI where allowed. */
enum class ReminderStyle { NOTIFICATION, ALARM }

data class ReminderSchedule(
    val id: String,
    val reminderId: String,
    val schedule: Schedule,
)

data class Reminder(
    val id: String,
    val title: String,
    val description: String = "",
    val category: ReminderCategory = ReminderCategory.CUSTOM,
    val targetType: ReminderTargetType = ReminderTargetType.NONE,
    val targetId: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val enabled: Boolean = true,
    val style: ReminderStyle = ReminderStyle.NOTIFICATION,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** When true, an unanswered delivered occurrence later becomes MISSED. */
    val requiresCompletion: Boolean = false,
    /** Unique integer identity for PendingIntents and notification ids. Assigned by the repository. */
    val requestCode: Int = 0,
    val schedules: List<ReminderSchedule> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class OccurrenceStatus { SCHEDULED, SNOOZED, DELIVERED, COMPLETED, SKIPPED, MISSED, CANCELLED;
    val isPending: Boolean get() = this == SCHEDULED || this == SNOOZED
    val isFinal: Boolean get() = this == COMPLETED || this == SKIPPED || this == MISSED || this == CANCELLED
}

/** How the OS alarm for an occurrence was registered. */
enum class AlarmPrecision { EXACT, INEXACT, UNSCHEDULED }

data class ReminderOccurrence(
    val id: String,
    val reminderId: String,
    val scheduleId: String?,
    val scheduledAt: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val status: OccurrenceStatus,
    val precision: AlarmPrecision = AlarmPrecision.UNSCHEDULED,
    val deliveredAt: Instant? = null,
    val completedAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val attemptCount: Int = 0,
    val updatedAt: Instant,
) {
    /** When this occurrence should next fire. */
    val effectiveAt: Instant get() = snoozedUntil ?: scheduledAt
}

enum class ReminderAction { DONE, SNOOZE, SKIP, RESCHEDULE, DISMISS, OPEN }

data class ReminderActionLog(
    val id: String,
    val occurrenceId: String,
    val action: ReminderAction,
    val at: Instant,
    val detail: String = "",
)

enum class DeliveryEvent {
    SCHEDULED_EXACT, SCHEDULED_INEXACT, FIRED, DELIVERED, DUPLICATE_IGNORED, NOTIFICATION_BLOCKED,
    CANCELLED, RECONCILED, MARKED_MISSED, ERROR,
}

data class AlarmDeliveryLog(
    val id: String,
    val occurrenceId: String?,
    val reminderId: String?,
    val event: DeliveryEvent,
    val at: Instant,
    val detail: String = "",
)

/** A reminder joined with its upcoming occurrence for list screens. */
data class UpcomingReminder(val reminder: Reminder, val occurrence: ReminderOccurrence)

/** Convenience value used by features that link a reminder to their record. */
data class LinkedReminderSpec(
    val targetType: ReminderTargetType,
    val targetId: String,
    val title: String,
    val description: String = "",
    val category: ReminderCategory,
    val schedules: List<Schedule>,
    val style: ReminderStyle = ReminderStyle.NOTIFICATION,
    val requiresCompletion: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val enabled: Boolean = true,
)

/** A wall-clock moment for display (date + time) used in occurrence UIs. */
data class LocalMoment(val date: LocalDate, val time: LocalTime)
