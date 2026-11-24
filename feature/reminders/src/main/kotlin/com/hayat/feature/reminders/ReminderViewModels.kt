package com.hayat.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.hayat.core.common.AppResult
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.common.toFailureOrNull
import com.hayat.core.domain.reminder.ReminderCapabilities
import com.hayat.core.domain.reminder.ReminderCapabilityChecker
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.reminder.ReminderValidator
import com.hayat.core.domain.reminder.SystemSettingsTarget
import com.hayat.core.domain.repository.OccurrenceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.model.DateRule
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderSchedule
import com.hayat.core.model.Schedule
import com.hayat.core.model.TimeRule
import com.hayat.core.model.ZonePolicy
import com.hayat.core.navigation.ReminderEditRoute
import com.hayat.core.navigation.ReminderRescheduleRoute
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

data class OccurrenceItem(val reminder: Reminder, val occurrence: ReminderOccurrence)

data class RemindersState(
    val loading: Boolean = true,
    val upcoming: List<OccurrenceItem> = emptyList(),
    val missed: List<OccurrenceItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminders: ReminderRepository,
    occurrences: OccurrenceRepository,
    private val scheduler: ReminderScheduler,
    private val capabilityChecker: ReminderCapabilityChecker,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val now = time.now()
    val state = combine(
        reminders.observeReminders(),
        occurrences.observeUpcoming(now.minus(Duration.ofMinutes(15)), 100),
        occurrences.observeBetween(now.minus(Duration.ofDays(7)), now.plusSeconds(60)),
    ) { list, upcoming, recent ->
        val byId = list.associateBy { it.id }
        RemindersState(
            loading = false,
            upcoming = upcoming.mapNotNull { o -> byId[o.reminderId]?.let { OccurrenceItem(it, o) } },
            missed = recent.filter { it.status == OccurrenceStatus.MISSED }.sortedByDescending { it.scheduledAt }.mapNotNull { o -> byId[o.reminderId]?.let { OccurrenceItem(it, o) } },
            reminders = list,
        )
    }.stateInVm(RemindersState())

    private val _caps = MutableStateFlow(capabilityChecker.capabilities())
    val capabilities = _caps.asStateFlow()
    fun refreshCapabilities() { _caps.value = capabilityChecker.capabilities() }
    fun openSettings(t: SystemSettingsTarget) = capabilityChecker.open(t)

    fun setEnabled(r: Reminder, enabled: Boolean) = safeLaunch {
        reminders.setEnabled(r.id, enabled, time.now())
        scheduler.rescheduleReminder(r.id)
    }

    fun markDone(o: ReminderOccurrence) = safeLaunch { scheduler.handleAction(o.id, ReminderAction.DONE).handle() }
}

data class ReminderForm(val reminder: Reminder, val isNew: Boolean, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class ReminderEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val reminders: ReminderRepository,
    occurrences: OccurrenceRepository,
    private val scheduler: ReminderScheduler,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<ReminderEditRoute>()
    private val _form = MutableStateFlow<ReminderForm?>(null)
    val form = _form.asStateFlow()
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)
    val history = (route.id?.let { occurrences.observeForReminder(it, 30) } ?: flowOf(emptyList())).stateInVm(emptyList())
    val zoneName: String get() = time.zone().id

    init {
        safeLaunch {
            val existing = route.id?.let { reminders.getReminder(it) }
            val now = time.now()
            _form.value = existing?.let { ReminderForm(it, false) } ?: run {
                val id = ids.newId()
                val start = time.zonedNow().plusHours(1).withMinute(0)
                ReminderForm(Reminder(id = id, title = "", createdAt = now, updatedAt = now, snoozeMinutes = 10,
                    schedules = listOf(ReminderSchedule(ids.newId(), id, Schedule(DateRule.Once, TimeRule.At(start.toLocalTime().withSecond(0).withNano(0)), start.toLocalDate())))), true)
            }
        }
    }

    fun edit(transform: (Reminder) -> Reminder) = _form.update { it?.copy(reminder = transform(it.reminder), errors = emptyList()) }

    fun editSchedule(id: String, transform: (Schedule) -> Schedule) = edit { r ->
        r.copy(schedules = r.schedules.map { if (it.id == id) it.copy(schedule = transform(it.schedule)) else it })
    }

    fun addSchedule() = edit { r ->
        r.copy(schedules = r.schedules + ReminderSchedule(ids.newId(), r.id, Schedule(DateRule.Daily(), TimeRule.At(LocalTime.of(9, 0)), time.today())))
    }

    fun removeSchedule(id: String) = edit { r -> if (r.schedules.size <= 1) r else r.copy(schedules = r.schedules.filter { it.id != id }) }

    fun setFixedZone(id: String, fixed: Boolean) = editSchedule(id) {
        if (fixed) it.copy(zonePolicy = ZonePolicy.FIXED, zoneId = time.zone()) else it.copy(zonePolicy = ZonePolicy.DEVICE, zoneId = null)
    }

    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val r = f.reminder.copy(title = f.reminder.title.trim(), updatedAt = time.now())
        val failure = ReminderValidator.validate(r).toFailureOrNull()
        if (failure != null) {
            val errs = failure.handle()
            _form.update { it?.copy(errors = errs) }
            return@safeLaunch
        }
        val saved = reminders.save(r)
        scheduler.rescheduleReminder(saved.id)
        emit(UiEvent.NavigateBack)
    }

    fun delete() = safeLaunch {
        val r = _form.value?.reminder ?: return@safeLaunch
        reminders.getReminder(r.id)?.let { scheduler.cancelReminder(it) }
        reminders.softDelete(r.id, time.now())
        emit(UiEvent.NavigateBack)
    }
}

@HiltViewModel
class RescheduleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    occurrences: OccurrenceRepository,
    reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val time: TimeProvider,
) : HayatViewModel() {
    val occurrenceId = savedState.toRoute<ReminderRescheduleRoute>().occurrenceId
    private val trigger = MutableStateFlow(0)
    val item = trigger.flatMapLatest {
        kotlinx.coroutines.flow.flow {
            val o = occurrences.get(occurrenceId)
            emit(o?.let { occ -> reminders.getReminder(occ.reminderId)?.let { OccurrenceItem(it, occ) } })
        }
    }.stateInVm(null)

    fun now(): Instant = time.now()
    fun zone() = time.zone()

    fun rescheduleTo(at: Instant) = safeLaunch {
        if (!at.isAfter(time.now())) { message(R.string.rem_reschedule_past); return@safeLaunch }
        val r = scheduler.handleAction(occurrenceId, ReminderAction.RESCHEDULE, at)
        if (r is AppResult.Success) emit(UiEvent.NavigateBack) else r.handle()
    }

    fun markDone() = safeLaunch { scheduler.handleAction(occurrenceId, ReminderAction.DONE).handle { emit(UiEvent.NavigateBack) } }
}
