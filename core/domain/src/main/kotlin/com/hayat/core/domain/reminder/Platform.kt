package com.hayat.core.domain.reminder

import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import java.time.Instant

/** OS alarm registration, implemented with AlarmManager in :core:alarms. */
interface AlarmScheduler {
    fun canScheduleExact(): Boolean
    /** Registers (or replaces) the single OS alarm of a reminder. Returns the precision actually used. */
    fun schedule(requestCode: Int, reminderId: String, at: Instant, style: ReminderStyle): AlarmPrecision
    fun cancel(requestCode: Int)
}

/** Notification presentation, implemented in :core:notifications. */
interface ReminderNotifier {
    /** Posts the notification. Returns false when notifications are blocked for this reminder's channel. */
    fun show(reminder: Reminder, occurrence: ReminderOccurrence, silent: Boolean): Boolean
    fun cancel(reminder: Reminder)
    fun showMissedSummary(count: Int)
    fun canPostNotifications(): Boolean
}

/** Propagates notification actions to the feature that owns the reminder. Runs outside the scheduler lock. */
interface ReminderTargetHandler {
    val targetType: ReminderTargetType
    suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) {}
    suspend fun onSkip(targetId: String, occurrence: ReminderOccurrence) {}
    suspend fun onMissed(targetId: String, occurrence: ReminderOccurrence) {}
}

/** Features whose reminder times are computed (prayer times, annual events, preference-driven reminders). Called before each reconciliation. */
interface ReminderSyncContributor {
    suspend fun sync(now: Instant)
}
