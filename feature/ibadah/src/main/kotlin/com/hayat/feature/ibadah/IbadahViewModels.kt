package com.hayat.feature.ibadah

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.reminder.ReminderCapabilityChecker
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.worship.PrayerTimeCalculator
import com.hayat.core.model.FastingLog
import com.hayat.core.model.FastingStatus
import com.hayat.core.model.FastingType
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerLocation
import com.hayat.core.model.PrayerLog
import com.hayat.core.model.PrayerSettings
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.PrayerTimes
import com.hayat.core.model.QuranLog
import com.hayat.core.model.UserPreferences
import com.hayat.core.model.ZikrDefinition
import com.hayat.core.model.ZikrProgress
import com.hayat.core.navigation.ZikrCounterRoute
import com.hayat.core.navigation.ZikrEditRoute
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class IbadahState(
    val loading: Boolean = true,
    val prefs: UserPreferences = UserPreferences(),
    val today: LocalDate = LocalDate.now(),
    val times: PrayerTimes? = null,
    val next: Pair<Prayer, ZonedDateTime>? = null,
    val prayerLogs: List<PrayerLog> = emptyList(),
    val zikrs: List<ZikrDefinition> = emptyList(),
    val zikrToday: Map<String, Int> = emptyMap(),
    val quran: List<QuranLog> = emptyList(),
    val fasting: List<FastingLog> = emptyList(),
)

@HiltViewModel
class IbadahViewModel @Inject constructor(
    private val ibadah: IbadahRepository,
    private val prefs: PreferencesRepository,
    private val capabilities: ReminderCapabilityChecker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val today = time.today()
    private val monthStart = today.withDayOfMonth(1)

    val state = combine(
        prefs.preferences,
        ibadah.observePrayerLogs(today, today),
        combine(ibadah.observeZikrs(), ibadah.observeZikrProgress(today, today)) { z, p -> z to p },
        ibadah.observeQuranLogs(today.minusDays(29), today),
        ibadah.observeFasting(monthStart, monthStart.plusMonths(1).minusDays(1)),
    ) { p, logs, (zikrs, progress), quran, fasting ->
        IbadahState(
            loading = false, prefs = p, today = today,
            times = PrayerTimeCalculator.compute(today, p.prayer),
            next = PrayerTimeCalculator.next(time.now(), p.prayer),
            prayerLogs = logs, zikrs = zikrs, zikrToday = progress.associate { it.zikrId to it.count },
            quran = quran, fasting = fasting,
        )
    }.stateInVm(IbadahState())

    fun enableModule() = safeLaunch { prefs.update { it.copy(islamicModuleEnabled = true) }; capabilities.requestReconcile() }
    fun setPrayer(prayer: Prayer, status: PrayerStatus?) = safeLaunch { ibadah.setPrayerStatus(today, prayer, status, time.now()) }
    fun logQuran(surah: Int?, from: Int?, to: Int?, pages: Double?, minutes: Int?) = safeLaunch {
        if (surah == null && pages == null && minutes == null) return@safeLaunch
        ibadah.saveQuranLog(QuranLog(ids.newId(), today, surah, from, to, pages?.coerceIn(0.0, 604.0), minutes?.coerceIn(0, 1440), "", time.now()))
    }
    fun deleteQuran(log: QuranLog) = safeLaunch { ibadah.deleteQuranLog(log.id) }
    fun setFasting(date: LocalDate, type: FastingType, status: FastingStatus?) = safeLaunch {
        if (status == null) ibadah.deleteFasting(date) else ibadah.saveFasting(FastingLog(ids.newId(), date, type, status))
    }
}

@HiltViewModel
class ZikrCounterViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ibadah: IbadahRepository,
    private val time: TimeProvider,
) : HayatViewModel() {
    val id = savedState.toRoute<ZikrCounterRoute>().id
    private val today = time.today()
    val zikr = ibadah.observeZikrs().map { l -> l.firstOrNull { it.id == id } }.stateInVm(null)
    val week = ibadah.observeZikrProgress(today.minusDays(6), today).map { l -> l.filter { it.zikrId == id } }.stateInVm(emptyList<ZikrProgress>())
    val todayCount = week.map { l -> l.firstOrNull { it.date == today }?.count ?: 0 }.stateInVm(0)
    val vibrate = MutableStateFlow(true)

    fun add(delta: Int) = safeLaunch { ibadah.addZikrCount(id, today, delta, time.now()) }
    fun reset() = safeLaunch { ibadah.setZikrCount(id, today, 0, time.now()) }
    fun toggleFavorite() = safeLaunch { zikr.value?.let { ibadah.saveZikr(it.copy(isFavorite = !it.isFavorite, updatedAt = time.now())) } }
    fun days(): List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }
}

@HiltViewModel
class ZikrEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ibadah: IbadahRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<ZikrEditRoute>()
    private val _form = MutableStateFlow<ZikrDefinition?>(null)
    val form = _form.asStateFlow()
    val isNew get() = route.id == null
    init {
        safeLaunch {
            val now = time.now()
            _form.value = route.id?.let { ibadah.getZikr(it) } ?: ZikrDefinition(ids.newId(), "", targetCount = 33, position = 100, createdAt = now, updatedAt = now)
        }
    }
    fun edit(t: (ZikrDefinition) -> ZikrDefinition) = _form.update { it?.let(t) }
    fun save() = safeLaunch {
        val z = _form.value ?: return@safeLaunch
        if (z.title.isBlank() || z.title.length > 120 || z.targetCount !in 1..100_000) { message(com.hayat.core.ui.R.string.error_required); return@safeLaunch }
        ibadah.saveZikr(z.copy(title = z.title.trim(), updatedAt = time.now()))
        emit(UiEvent.NavigateBack)
    }
    fun delete() = safeLaunch { _form.value?.let { if (it.systemKey == null) ibadah.deleteZikr(it.id) else ibadah.saveZikr(it.copy(isArchived = true)) }; emit(UiEvent.NavigateBack) }
}

@HiltViewModel
class PrayerSettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val capabilities: ReminderCapabilityChecker,
    private val time: TimeProvider,
) : HayatViewModel() {
    val settings = prefs.preferences.map { it.prayer }.stateInVm(null)
    val preview = prefs.preferences.map { PrayerTimeCalculator.compute(time.today(), it.prayer) }.stateInVm(null)

    fun update(t: (PrayerSettings) -> PrayerSettings) = safeLaunch {
        prefs.update { it.copy(prayer = t(it.prayer)) }
        capabilities.requestReconcile()
    }

    /** Returns false when the manual coordinates are invalid. */
    fun setManual(lat: String, lon: String, zone: String, name: String): Boolean {
        val la = lat.replace(',', '.').toDoubleOrNull()
        val lo = lon.replace(',', '.').toDoubleOrNull()
        val z = runCatching { ZoneId.of(zone.trim()) }.getOrNull()
        if (la == null || lo == null || z == null || la !in -90.0..90.0 || lo !in -180.0..180.0) { message(R.string.ps_invalid); return false }
        update { it.copy(location = PrayerLocation(name.ifBlank { "%.3f, %.3f".format(java.util.Locale.ROOT, la, lo) }, la, lo, z.id)) }
        return true
    }
}
