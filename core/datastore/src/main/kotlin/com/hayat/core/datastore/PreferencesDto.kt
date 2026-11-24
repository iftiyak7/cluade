package com.hayat.core.datastore

import com.hayat.core.model.AppLanguage
import com.hayat.core.model.AppLockSettings
import com.hayat.core.model.AsrMadhab
import com.hayat.core.model.DashboardCard
import com.hayat.core.model.DashboardCardConfig
import com.hayat.core.model.DateFormatPref
import com.hayat.core.model.DigitStyle
import com.hayat.core.model.HighLatitudeRule
import com.hayat.core.model.PomodoroSettings
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerCalcMethod
import com.hayat.core.model.PrayerLocation
import com.hayat.core.model.PrayerSettings
import com.hayat.core.model.QuietHours
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ThemeMode
import com.hayat.core.model.TimeFormatPref
import com.hayat.core.model.UserPreferences
import com.hayat.core.model.WaterSettings
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * On-disk shape of [UserPreferences]. Enums are stored by name and decoded leniently, so a
 * value removed in a future version falls back to the default instead of corrupting the file.
 */
@Serializable
internal data class PreferencesDto(
    val version: Int = 1,
    val onboardingCompleted: Boolean = false,
    val language: String = AppLanguage.SYSTEM.name,
    val theme: String = ThemeMode.DARK.name,
    val dynamicColor: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val firstDayOfWeek: String = DayOfWeek.SUNDAY.name,
    val timeFormat: String = TimeFormatPref.SYSTEM.name,
    val dateFormat: String = DateFormatPref.MEDIUM.name,
    val digitStyle: String = DigitStyle.AUTO.name,
    val wakeTime: String = "06:30",
    val sleepTime: String = "22:30",
    val sleepGoalMinutes: Int = 480,
    val bedtimeReminderEnabled: Boolean = false,
    val bedtimeReminderLeadMinutes: Int = 30,
    val wakeAlarmEnabled: Boolean = false,
    val islamicModuleEnabled: Boolean = false,
    val enabledReminderCategories: List<String> = ReminderCategory.entries.map { it.name },
    val defaultSnoozeMinutes: Int = 10,
    val quietEnabled: Boolean = false,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val showOverdueInToday: Boolean = true,
    val dashboardCards: List<CardDto> = DashboardCard.entries.map { CardDto(it.name, true) },
    val waterGlassSizes: List<Int> = listOf(100, 150, 200, 250, 500),
    val waterDefaultGlass: Int = 250,
    val waterReminderEnabled: Boolean = false,
    val waterReminderInterval: Int = 120,
    val prayer: PrayerDto = PrayerDto(),
    val quranDailyPagesGoal: Int = 0,
    val hijriOffsetDays: Int = 0,
    val pomodoroFocus: Int = 25,
    val pomodoroShort: Int = 5,
    val pomodoroLong: Int = 15,
    val pomodoroSessions: Int = 4,
    val lockEnabled: Boolean = false,
    val lockBiometric: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val lockSecureScreen: Boolean = false,
    val currency: String = "BDT",
    val userName: String = "",
    val announcementsEnabled: Boolean = true,
    val serverSetupPending: Boolean = false,
)

@Serializable internal data class CardDto(val card: String, val visible: Boolean)

@Serializable
internal data class PrayerDto(
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val zoneId: String? = null,
    val method: String = PrayerCalcMethod.KARACHI.name,
    val madhab: String = AsrMadhab.HANAFI.name,
    val highLatitudeRule: String = HighLatitudeRule.MIDDLE_OF_THE_NIGHT.name,
    val adjustments: Map<String, Int> = emptyMap(),
    val remindersEnabled: List<String> = emptyList(),
    val reminderOffsetMinutes: Int = 0,
)

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

private fun timeOr(value: String, default: LocalTime): LocalTime = runCatching { LocalTime.parse(value) }.getOrDefault(default)

internal fun PreferencesDto.toModel(): UserPreferences {
    val d = UserPreferences()
    val cards = dashboardCards.mapNotNull { c -> enumValues<DashboardCard>().firstOrNull { it.name == c.card }?.let { DashboardCardConfig(it, c.visible) } }
        .distinctBy { it.card }
    // New card types added in later versions are appended, visible.
    val allCards = cards + DashboardCard.entries.filter { e -> cards.none { it.card == e } }.map { DashboardCardConfig(it, true) }
    return UserPreferences(
        onboardingCompleted = onboardingCompleted,
        language = enumOr(language, d.language),
        theme = enumOr(theme, d.theme),
        dynamicColor = dynamicColor,
        highContrast = highContrast,
        reduceMotion = reduceMotion,
        firstDayOfWeek = enumOr(firstDayOfWeek, d.firstDayOfWeek),
        timeFormat = enumOr(timeFormat, d.timeFormat),
        dateFormat = enumOr(dateFormat, d.dateFormat),
        digitStyle = enumOr(digitStyle, d.digitStyle),
        wakeTime = timeOr(wakeTime, d.wakeTime),
        sleepTime = timeOr(sleepTime, d.sleepTime),
        sleepGoalMinutes = sleepGoalMinutes.coerceIn(60, 16 * 60),
        bedtimeReminderEnabled = bedtimeReminderEnabled,
        bedtimeReminderLeadMinutes = bedtimeReminderLeadMinutes.coerceIn(0, 180),
        wakeAlarmEnabled = wakeAlarmEnabled,
        islamicModuleEnabled = islamicModuleEnabled,
        enabledReminderCategories = enabledReminderCategories.mapNotNull { n -> enumValues<ReminderCategory>().firstOrNull { it.name == n } }.toSet(),
        defaultSnoozeMinutes = defaultSnoozeMinutes.coerceIn(1, 120),
        quietHours = QuietHours(quietEnabled, timeOr(quietStart, d.quietHours.start), timeOr(quietEnd, d.quietHours.end)),
        showOverdueInToday = showOverdueInToday,
        dashboardCards = allCards,
        water = WaterSettings(
            glassSizesMl = waterGlassSizes.filter { it in 10..3000 }.distinct().ifEmpty { d.water.glassSizesMl },
            defaultGlassMl = waterDefaultGlass.coerceIn(10, 3000),
            reminderEnabled = waterReminderEnabled,
            reminderIntervalMinutes = waterReminderInterval.coerceIn(15, 480),
        ),
        prayer = prayer.toModel(),
        quranDailyPagesGoal = quranDailyPagesGoal.coerceIn(0, 604),
        hijriOffsetDays = hijriOffsetDays.coerceIn(-2, 2),
        pomodoro = PomodoroSettings(pomodoroFocus.coerceIn(1, 180), pomodoroShort.coerceIn(1, 60), pomodoroLong.coerceIn(1, 120), pomodoroSessions.coerceIn(1, 12)),
        appLock = AppLockSettings(lockEnabled, lockBiometric, lockTimeoutSeconds.coerceIn(0, 3600), lockSecureScreen),
        currency = currency.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: d.currency,
        userName = userName.trim().take(40),
        announcementsEnabled = announcementsEnabled,
        serverSetupPending = serverSetupPending,
    )
}

private fun PrayerDto.toModel(): PrayerSettings {
    val d = PrayerSettings()
    val loc = if (locationName != null && latitude != null && longitude != null && zoneId != null) PrayerLocation(locationName, latitude, longitude, zoneId) else null
    return PrayerSettings(
        location = loc,
        method = enumOr(method, d.method),
        madhab = enumOr(madhab, d.madhab),
        highLatitudeRule = enumOr(highLatitudeRule, d.highLatitudeRule),
        adjustments = adjustments.mapNotNull { (k, v) -> enumValues<Prayer>().firstOrNull { it.name == k }?.let { it to v.coerceIn(-60, 60) } }.toMap(),
        remindersEnabled = remindersEnabled.mapNotNull { n -> enumValues<Prayer>().firstOrNull { it.name == n } }.toSet(),
        reminderOffsetMinutes = reminderOffsetMinutes.coerceIn(0, 60),
    )
}

internal fun UserPreferences.toDto(): PreferencesDto = PreferencesDto(
    onboardingCompleted = onboardingCompleted,
    language = language.name, theme = theme.name, dynamicColor = dynamicColor, highContrast = highContrast, reduceMotion = reduceMotion,
    firstDayOfWeek = firstDayOfWeek.name, timeFormat = timeFormat.name, dateFormat = dateFormat.name, digitStyle = digitStyle.name,
    wakeTime = wakeTime.toString(), sleepTime = sleepTime.toString(), sleepGoalMinutes = sleepGoalMinutes,
    bedtimeReminderEnabled = bedtimeReminderEnabled, bedtimeReminderLeadMinutes = bedtimeReminderLeadMinutes, wakeAlarmEnabled = wakeAlarmEnabled,
    islamicModuleEnabled = islamicModuleEnabled,
    enabledReminderCategories = enabledReminderCategories.map { it.name }.sorted(),
    defaultSnoozeMinutes = defaultSnoozeMinutes,
    quietEnabled = quietHours.enabled, quietStart = quietHours.start.toString(), quietEnd = quietHours.end.toString(),
    showOverdueInToday = showOverdueInToday,
    dashboardCards = dashboardCards.map { CardDto(it.card.name, it.visible) },
    waterGlassSizes = water.glassSizesMl, waterDefaultGlass = water.defaultGlassMl,
    waterReminderEnabled = water.reminderEnabled, waterReminderInterval = water.reminderIntervalMinutes,
    prayer = PrayerDto(
        prayer.location?.name, prayer.location?.latitude, prayer.location?.longitude, prayer.location?.zoneId,
        prayer.method.name, prayer.madhab.name, prayer.highLatitudeRule.name,
        prayer.adjustments.mapKeys { it.key.name }, prayer.remindersEnabled.map { it.name }.sorted(), prayer.reminderOffsetMinutes,
    ),
    quranDailyPagesGoal = quranDailyPagesGoal, hijriOffsetDays = hijriOffsetDays,
    pomodoroFocus = pomodoro.focusMinutes, pomodoroShort = pomodoro.shortBreakMinutes, pomodoroLong = pomodoro.longBreakMinutes,
    pomodoroSessions = pomodoro.sessionsBeforeLongBreak,
    lockEnabled = appLock.enabled, lockBiometric = appLock.biometricEnabled, lockTimeoutSeconds = appLock.timeoutSeconds, lockSecureScreen = appLock.secureScreen,
    currency = currency,
    userName = userName,
    announcementsEnabled = announcementsEnabled,
    serverSetupPending = serverSetupPending,
)
