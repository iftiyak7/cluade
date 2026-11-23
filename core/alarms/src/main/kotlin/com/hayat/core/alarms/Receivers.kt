package com.hayat.core.alarms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hayat.core.common.ApplicationScope
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.model.ReminderAction
import com.hayat.core.notifications.NotificationChannelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Runs [block] off the main thread within the broadcast time budget. */
internal fun BroadcastReceiver.runAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try { withTimeoutOrNull(9_000) { block() } } finally { pending.finish() }
    }
}

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmScheduler.ACTION_FIRE) return
        val code = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_REQUEST_CODE, -1).takeIf { it > 0 } ?: return
        runAsync(scope) { scheduler.onAlarmFired(code) }
    }
}

@AndroidEntryPoint
class AlarmActionReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION)?.let { a -> ReminderAction.entries.firstOrNull { it.name == a } } ?: return
        if (action !in setOf(ReminderAction.DONE, ReminderAction.SNOOZE, ReminderAction.SKIP, ReminderAction.DISMISS)) return
        runAsync(scope) { scheduler.handleAction(occurrenceId, action) }
    }

    companion object {
        const val ACTION = "com.hayat.alarm.ACTION"
        const val EXTRA_OCCURRENCE = "occurrence_id"
        const val EXTRA_ACTION = "action"
    }
}

/**
 * Exported (required for system broadcasts). Accepts only the listed system actions and only
 * enqueues reconciliation work; it never trusts extras.
 */
@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {
    @Inject lateinit var reconcile: ReconciliationScheduler
    @Inject lateinit var channels: NotificationChannelManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                reconcile.ensurePeriodic()
                reconcile.enqueueNow(ScheduleReconciliationWorker.REASON_BOOT)
            }
            Intent.ACTION_TIME_CHANGED -> reconcile.enqueueNow(ScheduleReconciliationWorker.REASON_TIME)
            Intent.ACTION_TIMEZONE_CHANGED -> reconcile.enqueueNow(ScheduleReconciliationWorker.REASON_ZONE)
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> reconcile.enqueueNow(ScheduleReconciliationWorker.REASON_PERMISSION)
            Intent.ACTION_LOCALE_CHANGED -> channels.ensureChannels()
        }
    }
}
