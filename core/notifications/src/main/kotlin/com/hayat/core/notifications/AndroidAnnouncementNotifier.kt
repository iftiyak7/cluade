package com.hayat.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hayat.core.domain.announcement.AnnouncementNotifier
import com.hayat.core.model.Announcement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAnnouncementNotifier @Inject constructor(@ApplicationContext private val context: Context) : AnnouncementNotifier {
    @SuppressLint("MissingPermission") // checked below
    override fun notify(items: List<Announcement>, bengali: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!nm.areNotificationsEnabled()) return
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(NotificationChannelManager.ANNOUNCEMENTS)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            action = ACTION_OPEN_ANNOUNCEMENTS
        } ?: return
        val open = PendingIntent.getActivity(context, REQUEST_CODE, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        items.forEach { a ->
            val body = a.body(bengali)
            val n = NotificationCompat.Builder(context, NotificationChannelManager.ANNOUNCEMENTS)
                .setSmallIcon(R.drawable.ic_stat_hayat)
                .setContentTitle(a.title(bengali))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try { nm.notify(TAG, a.id.toInt(), n) } catch (_: SecurityException) { return }
        }
    }

    companion object {
        /** Launch action that opens the in-app announcements inbox. */
        const val ACTION_OPEN_ANNOUNCEMENTS = "hayat.action.OPEN_ANNOUNCEMENTS"
        private const val TAG = "announcement"
        private const val REQUEST_CODE = 7301
    }
}
