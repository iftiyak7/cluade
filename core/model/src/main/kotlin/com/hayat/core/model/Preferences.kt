package com.hayat.core.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class AppLanguage(val tag: String) { SYSTEM(""), ENGLISH("en"), BENGALI("bn") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TimeFormatPref { SYSTEM, H12, H24 }
enum class DateFormatPref { SHORT, MEDIUM, LONG }
enum class DigitStyle { AUTO, LATIN, BENGALI }

enum class DashboardCard {
    TODAY_PROGRESS, PRIORITIES, NEXT_TASK, UPCOMING_REMINDERS, MISSED_REMINDERS, WATER, MEDICINE,
    PRAYER, ZIKR, HABITS, DIET, TIMELINE,
}

data class DashboardCardConfig(val card: DashboardCard, val visible: Boolean)

data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
)

data class WaterSettings(
    val glassSizesMl: List<Int> = listOf(100, 150, 200, 250, 500),
    val defaultGlassMl: Int = 250,
    val reminderEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 120,
)

data class AppLockSettings(
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** Lock after the app has been in the background this long. 0 = immediately. */
    val timeoutSeconds: Int = 60,
    /** Sets FLAG_SECURE: hides content in Recents and blocks screenshots. */
    val secureScreen: Boolean = false,
)

data class QuietHours(val enabled: Boolean = false, val start: LocalTime = LocalTime.of(22, 0), val end: LocalTime = LocalTime.of(7, 0)) {
    fun contains(time: LocalTime): Boolean = enabled && if (start <= end) time >= start && time < end else time >= start || time < end
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val timeFormat: TimeFormatPref = TimeFormatPref.SYSTEM,
    val dateFormat: DateFormatPref = DateFormatPref.MEDIUM,
    val digitStyle: DigitStyle = DigitStyle.AUTO,
    val wakeTime: LocalTime = LocalTime.of(6, 30),
    val sleepTime: LocalTime = LocalTime.of(22, 30),
    val sleepGoalMinutes: Int = 8 * 60,
    val bedtimeReminderEnabled: Boolean = false,
    /** Minutes before [sleepTime] to send the bedtime reminder. */
    val bedtimeReminderLeadMinutes: Int = 30,
    val wakeAlarmEnabled: Boolean = false,
    val islamicModuleEnabled: Boolean = false,
    val enabledReminderCategories: Set<ReminderCategory> = ReminderCategory.entries.toSet(),
    val defaultSnoozeMinutes: Int = 10,
    val quietHours: QuietHours = QuietHours(),
    /** When true, overdue open tasks are shown in Today; due dates are never modified silently. */
    val showOverdueInToday: Boolean = true,
    val dashboardCards: List<DashboardCardConfig> = DashboardCard.entries.map { DashboardCardConfig(it, true) },
    val water: WaterSettings = WaterSettings(),
    val prayer: PrayerSettings = PrayerSettings(),
    val quranDailyPagesGoal: Int = 0,
    val hijriOffsetDays: Int = 0,
    val pomodoro: PomodoroSettings = PomodoroSettings(),
    val appLock: AppLockSettings = AppLockSettings(),
    val currency: String = "BDT",
    /** Optional first name for the greeting; stays on the device. */
    val userName: String = "",
    /** Show in-app announcements fetched from the Hayat server (no personal data is sent). */
    val announcementsEnabled: Boolean = true,
    /** New installs must connect to a server once before using the app (set when onboarding ends). */
    val serverSetupPending: Boolean = false,
)
