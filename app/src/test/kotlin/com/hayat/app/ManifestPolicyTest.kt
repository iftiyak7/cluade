package com.hayat.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guards the privacy policy (docs/permissions.md, docs/privacy.md) against regressions from
 * dependencies. INTERNET is allowed only for the anonymous announcements feed.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class ManifestPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val forbidden = setOf(
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS", "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.CALL_PHONE",
        "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.USE_EXACT_ALARM", "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "android.permission.FOREGROUND_SERVICE",
        "com.google.android.gms.permission.AD_ID",
    )

    @Test fun `no location, contacts, sms, storage or ad permissions are requested`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertEquals(emptySet<String>(), requested intersect forbidden)
        assertTrue("SCHEDULE_EXACT_ALARM is the only exact-alarm permission", "android.permission.SCHEDULE_EXACT_ALARM" in requested)
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
    }

    @Test fun `cleartext traffic is not permitted`() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
    }

    @Test fun `android backup and device transfer are disabled`() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun `alarm receivers are not exported`() {
        val pm = context.packageManager
        listOf("com.hayat.core.alarms.AlarmReceiver", "com.hayat.core.alarms.AlarmActionReceiver").forEach { name ->
            val info = pm.getReceiverInfo(android.content.ComponentName(context, name), 0)
            assertFalse("$name must not be exported", info.exported)
        }
    }
}
