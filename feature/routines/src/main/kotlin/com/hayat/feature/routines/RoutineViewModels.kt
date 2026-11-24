package com.hayat.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.designsystem.theme.UserColorChoices
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.RoutineRepository
import com.hayat.core.domain.repository.TemplateRepository
import com.hayat.core.domain.routine.RoutineActionsUseCase
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.domain.routine.SaveRoutineUseCase
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.DateRule
import com.hayat.core.model.Routine
import com.hayat.core.model.RoutineActivity
import com.hayat.core.model.RoutineKind
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.model.TemplateItem
import com.hayat.core.navigation.RoutineDetailRoute
import com.hayat.core.navigation.RoutineEditRoute
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.UiEvent
import com.hayat.core.time.RecurrenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    routines: RoutineRepository,
    private val actions: RoutineActionsUseCase,
    prefs: PreferencesRepository,
) : HayatViewModel() {
    val routines = routines.observeRoutines().stateInVm(null)
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)
    fun setPaused(r: Routine, paused: Boolean) = safeLaunch { actions.setPaused(r.id, paused).handle() }
    fun setArchived(r: Routine, archived: Boolean) = safeLaunch { actions.setArchived(r.id, archived).handle() }
    fun delete(r: Routine) = safeLaunch { actions.delete(r.id).handle { message(com.hayat.core.ui.R.string.msg_deleted) } }
    fun duplicate(r: Routine, suffix: String) = safeLaunch { actions.duplicate(r.id, suffix).handle() }
}

data class RoutineForm(
    val routine: Routine,
    val isNew: Boolean,
    val errors: List<ValidationError> = emptyList(),
)

@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val routines: RoutineRepository,
    private val save: SaveRoutineUseCase,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<RoutineEditRoute>()
    private val _form = MutableStateFlow<RoutineForm?>(null)
    val form = _form.asStateFlow()
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)

    init {
        safeLaunch {
            val existing = route.id?.let { routines.getRoutine(it) }
            val now = time.now()
            _form.value = existing?.let { RoutineForm(it, false) }
                ?: RoutineForm(Routine(ids.newId(), "", color = UserColorChoices.first(), startDate = time.today(), createdAt = now, updatedAt = now), true)
        }
    }

    fun edit(transform: (Routine) -> Routine) = _form.update { it?.copy(routine = transform(it.routine), errors = emptyList()) }

    fun addActivity() = edit { r ->
        val last = r.activities.maxByOrNull { it.startTime }
        val start = last?.let { RoutinePlannerTime.after(it.startTime, it.durationMinutes) } ?: LocalTime.of(7, 0)
        r.copy(activities = r.activities + RoutineActivity(ids.newId(), r.id, "", start, 30, position = r.activities.size))
    }
    fun updateActivity(id: String, transform: (RoutineActivity) -> RoutineActivity) = edit { r -> r.copy(activities = r.activities.map { if (it.id == id) transform(it) else it }) }
    fun removeActivity(id: String) = edit { r -> r.copy(activities = r.activities.filter { it.id != id }) }

    fun conflicts() = _form.value?.routine?.activities?.let(RoutinePlanner::conflicts).orEmpty()

    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        if (f.routine.activities.isEmpty()) { message(R.string.routine_needs_activity); return@safeLaunch }
        val errors = save(f.routine).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
}

internal object RoutinePlannerTime {
    fun after(t: LocalTime, minutes: Int): LocalTime =
        if (t.toSecondOfDay() / 60 + minutes >= 24 * 60 - 1) LocalTime.of(23, 0) else t.plusMinutes(minutes.toLong())
}

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val routines: RoutineRepository,
    private val actions: RoutineActionsUseCase,
    private val templates: TemplateRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    val id = savedState.toRoute<RoutineDetailRoute>().id
    private val _date = MutableStateFlow(time.today())
    val date = _date.asStateFlow()
    val routine = routines.observeRoutine(id).stateInVm(null)
    val logs = _date.flatMapLatest { d -> routines.observeLogs(d.minusDays(6), d) }.stateInVm(emptyList())

    fun setDate(d: LocalDate) { _date.value = d }
    fun isScheduled(r: Routine, d: LocalDate) = !r.isPaused && RecurrenceEngine.occursOn(r.dateRule, r.startDate, r.endDate, d)
    fun mark(activity: RoutineActivity, status: ActivityStatus?) = safeLaunch { actions.log(activity.id, _date.value, status).handle() }

    fun saveAsTemplate(name: String) = safeLaunch {
        val r = routine.value ?: return@safeLaunch
        templates.saveUserTemplate(RoutineTemplate(ids.newId(), null, name, r.description, r.kind, r.dateRule,
            r.activities.map { TemplateItem(it.title, it.startTime, it.durationMinutes) }))
        message(R.string.routine_template_saved)
    }
}

data class TemplateListState(val builtIn: List<RoutineTemplate> = emptyList(), val mine: List<RoutineTemplate> = emptyList(), val islamic: Boolean = false)

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val templates: TemplateRepository,
    prefs: PreferencesRepository,
    private val actions: RoutineActionsUseCase,
    private val time: TimeProvider,
) : HayatViewModel() {
    val state = combine(templates.observeUserTemplates(), prefs.preferences) { mine, p ->
        TemplateListState(
            builtIn = com.hayat.core.domain.template.BuiltInTemplates.all.filter { p.islamicModuleEnabled || (it.systemKey != "muslim" && it.systemKey != "ramadan") },
            mine = mine, islamic = p.islamicModuleEnabled,
        )
    }.stateInVm(TemplateListState())

    /** Always creates a new routine; never overwrites existing ones. */
    fun apply(template: RoutineTemplate, name: String, itemTitles: List<String>, onCreated: (String) -> Unit) = safeLaunch {
        val localized = template.copy(items = template.items.mapIndexed { i, item -> item.copy(title = itemTitles.getOrElse(i) { item.title }) })
        actions.applyTemplate(localized, name, time.today(), UserColorChoices[(name.hashCode() and 0x7fffffff) % UserColorChoices.size]).handle {
            message(R.string.tpl_created); onCreated(it.id)
        }
    }

    fun delete(t: RoutineTemplate) = safeLaunch { templates.deleteUserTemplate(t.id) }
}

fun DateRule.isSpecificDate() = this == DateRule.Once
fun RoutineKind.defaultRule(): DateRule = when (this) {
    RoutineKind.FRIDAY -> DateRule.Weekly(com.hayat.core.model.WeekdaySet.of(java.time.DayOfWeek.FRIDAY))
    RoutineKind.WORKDAY -> DateRule.Weekly(com.hayat.core.model.WeekdaySet.BD_WORKWEEK)
    else -> DateRule.Daily()
}
