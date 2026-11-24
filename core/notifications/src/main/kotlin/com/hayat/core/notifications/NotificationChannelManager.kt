package com.hayat.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the notification channels (docs/alarm-engine.md). Re-running only refreshes names and
 * descriptions; Android keeps the user's importance/sound/vibration choices for existing channels.
 */
@Singleton
class NotificationChannelManager @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAttrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val channels = listOf(
            channel(ALARMS, R.string.channel_alarms, R.string.channel_alarms_desc, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(alarmSound, alarmAttrs); enableVibration(true)
            },
            channel(MEDICINE, R.string.channel_medicine, R.string.channel_medicine_desc, NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
            channel(ROUTINE, R.string.channel_routine, R.string.channel_routine_desc, NotificationManager.IMPORTANCE_DEFAULT),
            channel(WATER, R.string.channel_water, R.string.channel_water_desc, NotificationManager.IMPORTANCE_LOW),
            channel(IBADAH, R.string.channel_ibadah, R.string.channel_ibadah_desc, NotificationManager.IMPORTANCE_DEFAULT),
            channel(TASKS, R.string.channel_tasks, R.string.channel_tasks_desc, NotificationManager.IMPORTANCE_DEFAULT),
            channel(SYSTEM, R.string.channel_system, R.string.channel_system_desc, NotificationManager.IMPORTANCE_LOW),
            channel(ANNOUNCEMENTS, R.string.channel_announcements, R.string.channel_announcements_desc, NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannels(channels)
    }

    private fun channel(id: String, name: Int, desc: Int, importance: Int) =
        NotificationChannel(id, context.getString(name), importance).apply {
            description = context.getString(desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

    companion object {
        const val ALARMS = "alarms_critical"
        const val MEDICINE = "medicine"
        const val ROUTINE = "routine"
        const val WATER = "water"
        const val IBADAH = "ibadah"
        const val TASKS = "tasks"
        const val SYSTEM = "system"
        const val ANNOUNCEMENTS = "announcements"

        fun channelFor(category: ReminderCategory, style: ReminderStyle): String = when {
            style == ReminderStyle.ALARM -> ALARMS
            else -> when (category) {
                ReminderCategory.MEDICINE -> MEDICINE
                ReminderCategory.ROUTINE, ReminderCategory.WORK, ReminderCategory.EXERCISE, ReminderCategory.MEAL, ReminderCategory.SLEEP -> ROUTINE
                ReminderCategory.WATER -> WATER
                ReminderCategory.PRAYER, ReminderCategory.ZIKR, ReminderCategory.QURAN -> IBADAH
                else -> TASKS
            }
        }
    }
}
