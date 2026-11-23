package com.hayat.core.domain.worship

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule as AdhanHighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerAdjustments
import com.batoulapps.adhan.data.DateComponents
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.reminder.ReminderSyncContributor
import com.hayat.core.domain.reminder.ReminderTargetHandler
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.AsrMadhab
import com.hayat.core.model.DateRule
import com.hayat.core.model.HighLatitudeRule
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerCalcMethod
import com.hayat.core.model.PrayerLocation
import com.hayat.core.model.PrayerSettings
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.PrayerTimes
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Schedule
import com.hayat.core.model.Surah
import com.hayat.core.model.TimeRule
import com.hayat.core.model.ZikrDefinition
import com.hayat.core.model.ZonePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import javax.inject.Inject

/**
 * Offline prayer times using the Adhan library (MIT). Times are astronomical calculations and may
 * differ from local mosque timetables; users can apply per-prayer minute adjustments.
 */
object PrayerTimeCalculator {
    fun compute(date: LocalDate, settings: PrayerSettings): PrayerTimes? {
        val loc = settings.location ?: return null
        return compute(date, loc, settings)
    }

    fun compute(date: LocalDate, location: PrayerLocation, settings: PrayerSettings): PrayerTimes? {
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null
        val zone = runCatching { ZoneId.of(location.zoneId) }.getOrNull() ?: return null
        val params = settings.method.toAdhan().parameters.apply {
            madhab = if (settings.madhab == AsrMadhab.HANAFI) Madhab.HANAFI else Madhab.SHAFI
            highLatitudeRule = when (settings.highLatitudeRule) {
                HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> AdhanHighLatitudeRule.MIDDLE_OF_THE_NIGHT
                HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> AdhanHighLatitudeRule.SEVENTH_OF_THE_NIGHT
                HighLatitudeRule.TWILIGHT_ANGLE -> AdhanHighLatitudeRule.TWILIGHT_ANGLE
            }
            adjustments = PrayerAdjustments(
                settings.adjustments[Prayer.FAJR] ?: 0, 0,
                settings.adjustments[Prayer.DHUHR] ?: 0,
                settings.adjustments[Prayer.ASR] ?: 0,
                settings.adjustments[Prayer.MAGHRIB] ?: 0,
                settings.adjustments[Prayer.ISHA] ?: 0,
            )
        }
        val pt = runCatching {
            com.batoulapps.adhan.PrayerTimes(Coordinates(location.latitude, location.longitude), DateComponents(date.year, date.monthValue, date.dayOfMonth), params)
        }.getOrNull() ?: return null
        fun Date.z(): ZonedDateTime = toInstant().atZone(zone)
        return PrayerTimes(date, pt.fajr.z(), pt.sunrise.z(), pt.dhuhr.z(), pt.asr.z(), pt.maghrib.z(), pt.isha.z())
    }

    /** The next prayer after [now], looking into tomorrow if needed. */
    fun next(now: Instant, settings: PrayerSettings): Pair<Prayer, ZonedDateTime>? {
        val loc = settings.location ?: return null
        val zone = runCatching { ZoneId.of(loc.zoneId) }.getOrNull() ?: return null
        val today = now.atZone(zone).toLocalDate()
        for (d in listOf(today, today.plusDays(1))) {
            val times = compute(d, loc, settings) ?: return null
            Prayer.entries.firstOrNull { times.timeOf(it).toInstant().isAfter(now) }?.let { return it to times.timeOf(it) }
        }
        return null
    }

    private fun PrayerCalcMethod.toAdhan(): CalculationMethod = when (this) {
        PrayerCalcMethod.KARACHI -> CalculationMethod.KARACHI
        PrayerCalcMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        PrayerCalcMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
        PrayerCalcMethod.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        PrayerCalcMethod.DUBAI -> CalculationMethod.DUBAI
        PrayerCalcMethod.QATAR -> CalculationMethod.QATAR
        PrayerCalcMethod.KUWAIT -> CalculationMethod.KUWAIT
        PrayerCalcMethod.MOON_SIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE
        PrayerCalcMethod.SINGAPORE -> CalculationMethod.SINGAPORE
        PrayerCalcMethod.NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA
    }
}

/** Approximate city-centre coordinates for manual selection (no location permission needed). */
object PrayerCities {
    val all: List<PrayerLocation> = listOf(
        PrayerLocation("Dhaka", 23.8103, 90.4125, "Asia/Dhaka"),
        PrayerLocation("Chattogram", 22.3569, 91.7832, "Asia/Dhaka"),
        PrayerLocation("Khulna", 22.8456, 89.5403, "Asia/Dhaka"),
        PrayerLocation("Rajshahi", 24.3745, 88.6042, "Asia/Dhaka"),
        PrayerLocation("Sylhet", 24.8949, 91.8687, "Asia/Dhaka"),
        PrayerLocation("Barishal", 22.7010, 90.3535, "Asia/Dhaka"),
        PrayerLocation("Rangpur", 25.7439, 89.2752, "Asia/Dhaka"),
        PrayerLocation("Mymensingh", 24.7471, 90.4203, "Asia/Dhaka"),
        PrayerLocation("Cumilla", 23.4607, 91.1809, "Asia/Dhaka"),
        PrayerLocation("Gazipur", 23.9999, 90.4203, "Asia/Dhaka"),
        PrayerLocation("Narayanganj", 23.6238, 90.5000, "Asia/Dhaka"),
        PrayerLocation("Cox's Bazar", 21.4272, 92.0058, "Asia/Dhaka"),
        PrayerLocation("Bogura", 24.8465, 89.3773, "Asia/Dhaka"),
        PrayerLocation("Jashore", 23.1664, 89.2081, "Asia/Dhaka"),
        PrayerLocation("Dinajpur", 25.6217, 88.6354, "Asia/Dhaka"),
        PrayerLocation("Noakhali", 22.8696, 91.0995, "Asia/Dhaka"),
        PrayerLocation("Makkah", 21.4225, 39.8262, "Asia/Riyadh"),
        PrayerLocation("Madinah", 24.4672, 39.6111, "Asia/Riyadh"),
        PrayerLocation("Dubai", 25.2048, 55.2708, "Asia/Dubai"),
        PrayerLocation("Kuala Lumpur", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        PrayerLocation("Jakarta", -6.2088, 106.8456, "Asia/Jakarta"),
        PrayerLocation("Karachi", 24.8607, 67.0011, "Asia/Karachi"),
        PrayerLocation("Kolkata", 22.5726, 88.3639, "Asia/Kolkata"),
        PrayerLocation("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"),
        PrayerLocation("London", 51.5074, -0.1278, "Europe/London"),
        PrayerLocation("New York", 40.7128, -74.0060, "America/New_York"),
        PrayerLocation("Toronto", 43.6532, -79.3832, "America/Toronto"),
        PrayerLocation("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
    )
}

/** The 114 surahs with verse counts (Hafs). Names are transliterations. Verified total: 6236 verses. */
object QuranData {
    private val raw = listOf(
        "Al-Fatihah" to 7, "Al-Baqarah" to 286, "Ali 'Imran" to 200, "An-Nisa" to 176, "Al-Ma'idah" to 120,
        "Al-An'am" to 165, "Al-A'raf" to 206, "Al-Anfal" to 75, "At-Tawbah" to 129, "Yunus" to 109,
        "Hud" to 123, "Yusuf" to 111, "Ar-Ra'd" to 43, "Ibrahim" to 52, "Al-Hijr" to 99,
        "An-Nahl" to 128, "Al-Isra" to 111, "Al-Kahf" to 110, "Maryam" to 98, "Taha" to 135,
        "Al-Anbya" to 112, "Al-Hajj" to 78, "Al-Mu'minun" to 118, "An-Nur" to 64, "Al-Furqan" to 77,
        "Ash-Shu'ara" to 227, "An-Naml" to 93, "Al-Qasas" to 88, "Al-'Ankabut" to 69, "Ar-Rum" to 60,
        "Luqman" to 34, "As-Sajdah" to 30, "Al-Ahzab" to 73, "Saba" to 54, "Fatir" to 45,
        "Ya-Sin" to 83, "As-Saffat" to 182, "Sad" to 88, "Az-Zumar" to 75, "Ghafir" to 85,
        "Fussilat" to 54, "Ash-Shura" to 53, "Az-Zukhruf" to 89, "Ad-Dukhan" to 59, "Al-Jathiyah" to 37,
        "Al-Ahqaf" to 35, "Muhammad" to 38, "Al-Fath" to 29, "Al-Hujurat" to 18, "Qaf" to 45,
        "Adh-Dhariyat" to 60, "At-Tur" to 49, "An-Najm" to 62, "Al-Qamar" to 55, "Ar-Rahman" to 78,
        "Al-Waqi'ah" to 96, "Al-Hadid" to 29, "Al-Mujadilah" to 22, "Al-Hashr" to 24, "Al-Mumtahanah" to 13,
        "As-Saff" to 14, "Al-Jumu'ah" to 11, "Al-Munafiqun" to 11, "At-Taghabun" to 18, "At-Talaq" to 12,
        "At-Tahrim" to 12, "Al-Mulk" to 30, "Al-Qalam" to 52, "Al-Haqqah" to 52, "Al-Ma'arij" to 44,
        "Nuh" to 28, "Al-Jinn" to 28, "Al-Muzzammil" to 20, "Al-Muddaththir" to 56, "Al-Qiyamah" to 40,
        "Al-Insan" to 31, "Al-Mursalat" to 50, "An-Naba" to 40, "An-Nazi'at" to 46, "'Abasa" to 42,
        "At-Takwir" to 29, "Al-Infitar" to 19, "Al-Mutaffifin" to 36, "Al-Inshiqaq" to 25, "Al-Buruj" to 22,
        "At-Tariq" to 17, "Al-A'la" to 19, "Al-Ghashiyah" to 26, "Al-Fajr" to 30, "Al-Balad" to 20,
        "Ash-Shams" to 15, "Al-Layl" to 21, "Ad-Duha" to 11, "Ash-Sharh" to 8, "At-Tin" to 8,
        "Al-'Alaq" to 19, "Al-Qadr" to 5, "Al-Bayyinah" to 8, "Az-Zalzalah" to 8, "Al-'Adiyat" to 11,
        "Al-Qari'ah" to 11, "At-Takathur" to 8, "Al-'Asr" to 3, "Al-Humazah" to 9, "Al-Fil" to 5,
        "Quraysh" to 4, "Al-Ma'un" to 7, "Al-Kawthar" to 3, "Al-Kafirun" to 6, "An-Nasr" to 3,
        "Al-Masad" to 5, "Al-Ikhlas" to 4, "Al-Falaq" to 5, "An-Nas" to 6,
    )
    val surahs: List<Surah> = raw.mapIndexed { i, (name, ayahs) -> Surah(i + 1, name, ayahs) }
    const val TOTAL_AYAHS = 6236
    const val STANDARD_MUSHAF_PAGES = 604
    fun surah(number: Int): Surah? = surahs.getOrNull(number - 1)
}

/**
 * Short, universally known adhkar. Arabic text is limited to these well-attested phrases;
 * translations are provided by localized UI strings keyed by [ZikrDefinition.systemKey].
 */
object ZikrPresets {
    fun presets(now: Instant): List<ZikrDefinition> = listOf(
        p("subhanallah", "SubhanAllah", "سُبْحَانَ اللّٰهِ", "Subhan Allah", "Glory be to Allah", 33, 0, now),
        p("alhamdulillah", "Alhamdulillah", "اَلْحَمْدُ لِلّٰهِ", "Alhamdu lillah", "All praise is for Allah", 33, 1, now),
        p("allahuakbar", "Allahu Akbar", "اَللّٰهُ أَكْبَرُ", "Allahu Akbar", "Allah is the Greatest", 34, 2, now),
        p("tahlil", "La ilaha illallah", "لَا إِلٰهَ إِلَّا اللّٰهُ", "La ilaha illallah", "There is no god but Allah", 100, 3, now),
        p("istighfar", "Astaghfirullah", "أَسْتَغْفِرُ اللّٰهَ", "Astaghfirullah", "I seek forgiveness from Allah", 100, 4, now),
        p("durood", "Durood", "اَللّٰهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ", "Allahumma salli 'ala Muhammad", "O Allah, send blessings upon Muhammad", 100, 5, now),
        p("subhanallahi_wa_bihamdihi", "SubhanAllahi wa bihamdihi", "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ", "Subhan Allahi wa bihamdihi", "Glory and praise be to Allah", 100, 6, now),
    )

    private fun p(key: String, title: String, arabic: String, translit: String, meaning: String, target: Int, pos: Int, now: Instant) =
        ZikrDefinition("preset.$key", title, arabic, translit, meaning, target, systemKey = key, position = pos, createdAt = now, updatedAt = now)
}

/** Prayer reminders are recomputed daily because the times change every day. */
class PrayerReminderContributor @Inject constructor(
    private val prefs: PreferencesRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) : ReminderSyncContributor {
    override suspend fun sync(now: Instant) {
        val p = prefs.current()
        val settings = p.prayer
        val location = settings.location
        val active = p.islamicModuleEnabled && location != null && ReminderCategory.PRAYER in p.enabledReminderCategories
        for (prayer in Prayer.entries) {
            if (!active || prayer !in settings.remindersEnabled) { linker.remove(ReminderTargetType.PRAYER, prayer.name); continue }
            val zone = ZoneId.of(location!!.zoneId)
            val today = now.atZone(zone).toLocalDate()
            val schedules = (0L until DAYS_AHEAD).mapNotNull { offset ->
                val date = today.plusDays(offset)
                val t = PrayerTimeCalculator.compute(date, location, settings)?.timeOf(prayer) ?: return@mapNotNull null
                val at = t.minusMinutes(settings.reminderOffsetMinutes.toLong())
                Schedule(DateRule.Once, TimeRule.At(at.toLocalTime().withSecond(0).withNano(0)), at.toLocalDate(), zonePolicy = ZonePolicy.FIXED, zoneId = zone)
            }
            linker.upsert(LinkedReminderSpec(ReminderTargetType.PRAYER, prayer.name, prayer.name, category = ReminderCategory.PRAYER, schedules = schedules))
        }
    }
    companion object { const val DAYS_AHEAD = 7L }
}

class PrayerReminderHandler @Inject constructor(private val ibadah: IbadahRepository, private val time: TimeProvider) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.PRAYER
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) {
        val prayer = runCatching { Prayer.valueOf(targetId) }.getOrNull() ?: return
        ibadah.setPrayerStatus(occurrence.localDate, prayer, PrayerStatus.PRAYED, time.now())
    }
}
