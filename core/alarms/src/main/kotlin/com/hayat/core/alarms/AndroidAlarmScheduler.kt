package com.hayat.core.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.hayat.core.domain.reminder.AlarmScheduler
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.ReminderStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager implementation. One PendingIntent per reminder (identity = request code + data
 * URI), so rescheduling replaces the previous alarm instead of adding another.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) : AlarmScheduler {
    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    override fun schedule(requestCode: Int, reminderId: String, at: Instant, style: ReminderStyle): AlarmPrecision {
        val am = alarmManager ?: return AlarmPrecision.UNSCHEDULED
        val pi = firePendingIntent(context, requestCode, PendingIntent.FLAG_UPDATE_CURRENT) ?: return AlarmPrecision.UNSCHEDULED
        val millis = at.toEpochMilli()
        return try {
            if (canScheduleExact()) {
                if (style == ReminderStyle.ALARM) {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent()), pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                }
                AlarmPrecision.EXACT
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                AlarmPrecision.INEXACT
            }
        } catch (e: SecurityException) {
            // Exact-alarm access was revoked between the check and the call.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            AlarmPrecision.INEXACT
        }
    }

    override fun cancel(requestCode: Int) {
        val pi = firePendingIntent(context, requestCode, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    private fun showIntent(): PendingIntent? = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, SHOW_REQUEST_CODE, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ACTION_FIRE = "com.hayat.alarm.FIRE"
        const val EXTRA_REQUEST_CODE = "request_code"
        private const val SHOW_REQUEST_CODE = Int.MAX_VALUE - 1

        fun fireIntent(context: Context, requestCode: Int) = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("hayat://reminder/$requestCode"))
            .putExtra(EXTRA_REQUEST_CODE, requestCode)

        fun firePendingIntent(context: Context, requestCode: Int, extraFlags: Int): PendingIntent? =
            PendingIntent.getBroadcast(context, requestCode, fireIntent(context, requestCode), PendingIntent.FLAG_IMMUTABLE or extraFlags)
    }
}
