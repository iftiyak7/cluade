package com.hayat.core.alarms

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hayat.core.domain.reminder.AlarmScheduler
import com.hayat.core.domain.reminder.ReminderCapabilities
import com.hayat.core.domain.reminder.ReminderCapabilityChecker
import com.hayat.core.domain.reminder.SystemSettingsTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** AlarmPermissionCoordinator: detects capabilities and opens the right system screen. */
@Singleton
class AndroidCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarms: AlarmScheduler,
    private val reconciliation: ReconciliationScheduler,
) : ReminderCapabilityChecker {

    override fun capabilities(): ReminderCapabilities {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val pm = context.getSystemService(PowerManager::class.java)
        return ReminderCapabilities(
            notificationsEnabled = !needsPermission && NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarmsAllowed = alarms.canScheduleExact(),
            fullScreenAllowed = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true,
            batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) == true,
            needsNotificationPermission = needsPermission,
        )
    }

    override fun open(target: SystemSettingsTarget) {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val intent = when (target) {
            SystemSettingsTarget.APP_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            SystemSettingsTarget.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            SystemSettingsTarget.FULL_SCREEN_INTENT ->
                if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            // Opens the system list; Hayat does not request REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
            SystemSettingsTarget.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            SystemSettingsTarget.APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun requestReconcile() = reconciliation.enqueueNow(ScheduleReconciliationWorker.REASON_APP)
}
