package com.hayat.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

enum class Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

/**
 * Personal tracking state. Marking a prayer only records what the user chose to log;
 * Hayat makes no judgement about validity.
 */
enum class PrayerStatus { PRAYED, PRAYED_LATE, QADA, MISSED, EXCUSED }

data class PrayerLog(val id: String, val date: LocalDate, val prayer: Prayer, val status: PrayerStatus, val loggedAt: Instant)

enum class PrayerCalcMethod {
    KARACHI, MUSLIM_WORLD_LEAGUE, EGYPTIAN, UMM_AL_QURA, DUBAI, QATAR, KUWAIT,
    MOON_SIGHTING_COMMITTEE, SINGAPORE, NORTH_AMERICA,
}

enum class AsrMadhab { SHAFI, HANAFI }

enum class HighLatitudeRule { MIDDLE_OF_THE_NIGHT, SEVENTH_OF_THE_NIGHT, TWILIGHT_ANGLE }

data class PrayerLocation(val name: String, val latitude: Double, val longitude: Double, val zoneId: String)

data class PrayerSettings(
    val location: PrayerLocation? = null,
    val method: PrayerCalcMethod = PrayerCalcMethod.KARACHI,
    val madhab: AsrMadhab = AsrMadhab.HANAFI,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
    /** Manual corrections in minutes per prayer to match a local mosque timetable. */
    val adjustments: Map<Prayer, Int> = emptyMap(),
    val remindersEnabled: Set<Prayer> = emptySet(),
    val reminderOffsetMinutes: Int = 0,
)

data class PrayerTimes(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    fun timeOf(prayer: Prayer): ZonedDateTime = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

data class ZikrDefinition(
    val id: String,
    val title: String,
    val arabic: String = "",
    val transliteration: String = "",
    val meaning: String = "",
    val targetCount: Int = 33,
    val isFavorite: Boolean = false,
    /** Non-null for bundled presets. */
    val systemKey: String? = null,
    val position: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ZikrProgress(val zikrId: String, val date: LocalDate, val count: Int)

data class QuranLog(
    val id: String,
    val date: LocalDate,
    val surah: Int? = null,
    val fromAyah: Int? = null,
    val toAyah: Int? = null,
    val pages: Double? = null,
    val minutes: Int? = null,
    val note: String = "",
    val loggedAt: Instant,
)

enum class FastingType { RAMADAN, SUNNAH, NAFL, QADA, OTHER }
enum class FastingStatus { FASTED, NOT_FASTED, PLANNED }
data class FastingLog(val id: String, val date: LocalDate, val type: FastingType, val status: FastingStatus, val note: String = "")

data class Surah(val number: Int, val nameTransliterated: String, val ayahCount: Int)
