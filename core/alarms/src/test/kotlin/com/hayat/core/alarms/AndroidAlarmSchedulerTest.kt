package com.hayat.core.alarms

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.ReminderStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidAlarmSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val am = context.getSystemService(AlarmManager::class.java)
    private val scheduler = AndroidAlarmScheduler(context)

    @Test fun `exact alarm when allowed, one pending intent per reminder`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val at = Instant.now().plusSeconds(3600)
        assertEquals(AlarmPrecision.EXACT, scheduler.schedule(7, "r", at, ReminderStyle.NOTIFICATION))
        assertEquals(AlarmPrecision.EXACT, scheduler.schedule(7, "r", at.plusSeconds(60), ReminderStyle.NOTIFICATION))
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals("rescheduling replaces the alarm", 1, scheduled.size)
        assertEquals(at.plusSeconds(60).toEpochMilli(), scheduled.single().triggerAtTime)
    }

    @Test fun `falls back to inexact when exact alarms are denied`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertEquals(AlarmPrecision.INEXACT, scheduler.schedule(8, "r", Instant.now().plusSeconds(60), ReminderStyle.NOTIFICATION))
        assertEquals(1, shadowOf(am).scheduledAlarms.size)
    }

    @Test fun `alarm style uses alarm clock`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(9, "r", Instant.now().plusSeconds(60), ReminderStyle.ALARM)
        assertNotNull(am.nextAlarmClock)
    }

    @Test fun `cancel removes the alarm and is safe when absent`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(10, "r", Instant.now().plusSeconds(60), ReminderStyle.NOTIFICATION)
        scheduler.cancel(10)
        scheduler.cancel(999)
        assertEquals(0, shadowOf(am).scheduledAlarms.size)
        assertNull(AndroidAlarmScheduler.firePendingIntent(context, 10, android.app.PendingIntent.FLAG_NO_CREATE))
    }
}
