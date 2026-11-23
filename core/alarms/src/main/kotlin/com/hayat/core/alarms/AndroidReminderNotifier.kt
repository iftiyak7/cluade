package com.hayat.core.alarms

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hayat.core.domain.reminder.ReminderNotifier
import com.hayat.core.model.Prayer
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.navigation.DeepLinkKeys
import com.hayat.core.notifications.NotificationChannelManager
import com.hayat.core.notifications.R as NR
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) : ReminderNotifier {
    private val nm = NotificationManagerCompat.from(context)

    override fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return nm.areNotificationsEnabled()
    }

    private fun channelBlocked(channelId: String): Boolean =
        context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE

    @SuppressLint("MissingPermission") // checked in canPostNotifications()
    override fun show(reminder: Reminder, occurrence: ReminderOccurrence, silent: Boolean): Boolean {
        val channel = NotificationChannelManager.channelFor(reminder.category, reminder.style)
        if (!canPostNotifications() || channelBlocked(channel)) return false
        val (title, text) = content(reminder)
        val isAlarm = reminder.style == ReminderStyle.ALARM
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(NR.drawable.ic_stat_hayat)
            .setContentTitle(context.getString(NR.string.notif_public_text))
            .build()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(NR.drawable.ic_stat_hayat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (isAlarm || reminder.priority.ordinal >= 2) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(occurrence.scheduledAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setSilent(silent)
            // Content stays private on the lock screen; the public version reveals nothing personal.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openIntent(reminder, occurrence, DeepLinkKeys.ACTION_OPEN))
            .setDeleteIntent(actionIntent(occurrence, ReminderAction.DISMISS))
            .addAction(0, context.getString(doneLabel(reminder.targetType)), actionIntent(occurrence, ReminderAction.DONE))
            .addAction(0, context.getString(NR.string.notif_action_snooze), actionIntent(occurrence, ReminderAction.SNOOZE))
        if (reminder.requiresCompletion) builder.addAction(0, context.getString(NR.string.notif_action_skip), actionIntent(occurrence, ReminderAction.SKIP))
        else builder.addAction(0, context.getString(NR.string.notif_action_reschedule), openIntent(reminder, occurrence, DeepLinkKeys.ACTION_RESCHEDULE))
        if (isAlarm) {
            builder.setOngoing(true)
            if (canUseFullScreen()) builder.setFullScreenIntent(alarmScreenIntent(reminder, occurrence, title, text), true)
        }
        return try {
            nm.notify(reminder.requestCode, builder.build()); true
        } catch (e: SecurityException) { false }
    }

    override fun cancel(reminder: Reminder) = nm.cancel(reminder.requestCode)

    @SuppressLint("MissingPermission")
    override fun showMissedSummary(count: Int) {
        if (!canPostNotifications()) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val pi = PendingIntent.getActivity(context, MISSED_ID, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, NotificationChannelManager.SYSTEM)
            .setSmallIcon(NR.drawable.ic_stat_hayat)
            .setContentTitle(context.getString(NR.string.notif_missed_title))
            .setContentText(context.resources.getQuantityString(NR.plurals.notif_missed_text, count, count))
            .setAutoCancel(true).setContentIntent(pi).build()
        try { nm.notify(MISSED_ID, n) } catch (_: SecurityException) { }
    }

    private fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    /** Localized content. Linked reminders whose titles are internal keys get proper text. */
    private fun content(r: Reminder): Pair<String, String> = when (r.targetType) {
        ReminderTargetType.WATER -> context.getString(NR.string.notif_water_title) to context.getString(NR.string.notif_water_text)
        ReminderTargetType.SLEEP_BEDTIME -> context.getString(NR.string.notif_bedtime_title) to context.getString(NR.string.notif_bedtime_text)
        ReminderTargetType.SLEEP_WAKE -> context.getString(NR.string.notif_wake_title) to context.getString(NR.string.notif_wake_text)
        ReminderTargetType.MEDICINE -> context.getString(NR.string.notif_medicine_title, r.title) to r.description
        ReminderTargetType.MEDICINE_REFILL -> context.getString(NR.string.notif_refill_title, r.title) to context.getString(NR.string.notif_refill_text)
        ReminderTargetType.PRAYER -> {
            val name = when (runCatching { Prayer.valueOf(r.title) }.getOrNull()) {
                Prayer.FAJR -> NR.string.prayer_fajr_n; Prayer.DHUHR -> NR.string.prayer_dhuhr_n; Prayer.ASR -> NR.string.prayer_asr_n
                Prayer.MAGHRIB -> NR.string.prayer_maghrib_n; Prayer.ISHA -> NR.string.prayer_isha_n; null -> null
            }
            (name?.let { context.getString(NR.string.notif_prayer_title, context.getString(it)) } ?: r.title) to ""
        }
        ReminderTargetType.FOCUS -> context.getString(NR.string.notif_focus_title) to ""
        else -> r.title to r.description
    }

    private fun doneLabel(type: ReminderTargetType) = when (type) {
        ReminderTargetType.MEDICINE -> NR.string.notif_action_taken
        ReminderTargetType.WATER -> NR.string.notif_action_log_glass
        else -> NR.string.notif_action_done
    }

    private fun actionIntent(o: ReminderOccurrence, action: ReminderAction): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java)
            .setAction(AlarmActionReceiver.ACTION)
            .setData(Uri.parse("hayat://occurrence/${o.id}/${action.name}")) // unique identity per occurrence + action
            .putExtra(AlarmActionReceiver.EXTRA_OCCURRENCE, o.id)
            .putExtra(AlarmActionReceiver.EXTRA_ACTION, action.name)
        return PendingIntent.getBroadcast(context, (o.id.hashCode() * 31) + action.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openIntent(r: Reminder, o: ReminderOccurrence, openAction: String): PendingIntent {
        val launch = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setData(Uri.parse("hayat://open/${o.id}/$openAction"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(DeepLinkKeys.EXTRA_TARGET_TYPE, r.targetType.name)
            .putExtra(DeepLinkKeys.EXTRA_TARGET_ID, r.targetId)
            .putExtra(DeepLinkKeys.EXTRA_OCCURRENCE_ID, o.id)
            .putExtra(DeepLinkKeys.EXTRA_ACTION, openAction)
        return PendingIntent.getActivity(context, o.id.hashCode() + openAction.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun alarmScreenIntent(r: Reminder, o: ReminderOccurrence, title: String, text: String): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .setData(Uri.parse("hayat://alarm/${o.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(AlarmActivity.EXTRA_OCCURRENCE, o.id)
            .putExtra(AlarmActivity.EXTRA_REQUEST_CODE, r.requestCode)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .putExtra(AlarmActivity.EXTRA_TEXT, text)
        return PendingIntent.getActivity(context, o.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object { private const val MISSED_ID = Int.MAX_VALUE - 2 }
}
