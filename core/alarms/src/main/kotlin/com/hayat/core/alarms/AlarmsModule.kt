package com.hayat.core.alarms

import com.hayat.core.domain.reminder.AlarmScheduler
import com.hayat.core.domain.reminder.ReminderCapabilityChecker
import com.hayat.core.domain.reminder.ReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmsModule {
    @Binds abstract fun alarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler
    @Binds abstract fun notifier(impl: AndroidReminderNotifier): ReminderNotifier
    @Binds abstract fun capabilities(impl: AndroidCapabilityChecker): ReminderCapabilityChecker
}
