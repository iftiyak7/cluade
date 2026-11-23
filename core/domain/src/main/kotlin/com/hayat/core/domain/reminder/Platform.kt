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

/** What the OS currently allows for reminders. Shown honestly in the UI. */
data class ReminderCapabilities(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenAllowed: Boolean,
    /** False when the app is subject to battery optimization (reminders may be delayed). */
    val batteryUnrestricted: Boolean,
    /** True on Android 13+ when POST_NOTIFICATIONS has not been granted yet. */
    val needsNotificationPermission: Boolean,
)

enum class SystemSettingsTarget { APP_NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN_INTENT, BATTERY_OPTIMIZATION, APP_DETAILS }

interface ReminderCapabilityChecker {
    fun capabilities(): ReminderCapabilities
    fun open(target: SystemSettingsTarget)
    /** Asks the system to run reconciliation soon (e.g. after settings changed). */
    fun requestReconcile()
}
