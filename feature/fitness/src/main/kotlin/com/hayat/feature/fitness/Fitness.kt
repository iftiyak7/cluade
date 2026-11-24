package com.hayat.feature.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.health.FitnessUseCases
import com.hayat.core.domain.repository.FitnessRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.Exercise
import com.hayat.core.model.WorkoutPlan
import com.hayat.core.model.WorkoutSession
import com.hayat.core.navigation.FitnessRoute
import com.hayat.core.navigation.WorkoutPlanEditRoute
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.WeekdayPicker
import com.hayat.core.ui.forField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class FitnessState(val loading: Boolean = true, val plans: List<WorkoutPlan> = emptyList(), val sessions: List<WorkoutSession> = emptyList(), val weekCount: Int = 0, val weekMinutes: Int = 0)

@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val fitness: FitnessRepository,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(fitness.observePlans(), fitness.observeSessions(today.minusDays(29), today), prefs.preferences) { plans, sessions, p ->
        val weekStart = today.with(TemporalAdjusters.previousOrSame(p.firstDayOfWeek))
        val week = sessions.filter { !it.date.isBefore(weekStart) }
        FitnessState(false, plans.filter { !it.isArchived }, sessions, week.size, week.sumOf { it.durationMinutes })
    }.stateInVm(FitnessState())

    fun logSession(planId: String?, minutes: Int, steps: Int?, note: String) = safeLaunch {
        if (minutes !in 1..1440) { message(UiR.string.error_out_of_range); return@safeLaunch }
        val now = time.now()
        fitness.saveSession(WorkoutSession(ids.newId(), planId, time.today(), now.minus(Duration.ofMinutes(minutes.toLong())), now, minutes, steps?.takeIf { it in 0..200_000 }, note.take(200)))
        message(R.string.fit_logged)
    }
    fun deleteSession(s: WorkoutSession) = safeLaunch { fitness.deleteSession(s.id) }
}

data class PlanForm(val plan: WorkoutPlan, val isNew: Boolean, val errors: List<ValidationError> = emptyList())

@HiltViewModel
class WorkoutPlanEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val fitness: FitnessRepository,
    private val useCases: FitnessUseCases,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<WorkoutPlanEditRoute>()
    private val _form = MutableStateFlow<PlanForm?>(null)
    val form = _form.asStateFlow()
    val firstDay = prefs.preferences.map { it.firstDayOfWeek }.stateInVm(java.time.DayOfWeek.SUNDAY)
    init {
        safeLaunch {
            val now = time.now()
            val p = route.id?.let { fitness.getPlan(it) }
            _form.value = p?.let { PlanForm(it, false) } ?: PlanForm(WorkoutPlan(ids.newId(), "", createdAt = now, updatedAt = now), true)
        }
    }
    fun edit(t: (WorkoutPlan) -> WorkoutPlan) = _form.update { it?.copy(plan = t(it.plan), errors = emptyList()) }
    fun addExercise() = edit { p -> p.copy(exercises = p.exercises + Exercise(ids.newId(), p.id, "", sets = 3, reps = 10, restSeconds = 60, position = p.exercises.size)) }
    fun editExercise(id: String, t: (Exercise) -> Exercise) = edit { p -> p.copy(exercises = p.exercises.map { if (it.id == id) t(it) else it }) }
    fun removeExercise(id: String) = edit { p -> p.copy(exercises = p.exercises.filter { it.id != id }) }
    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val errors = useCases.savePlan(f.plan).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }
    fun delete() = safeLaunch { _form.value?.let { useCases.deletePlan(it.plan.id).handle { emit(UiEvent.NavigateBack) } } }
}

fun NavGraphBuilder.fitnessGraph(nav: NavController) {
    composable<FitnessRoute> { FitnessScreen(nav) }
    composable<WorkoutPlanEditRoute> { WorkoutPlanEditScreen(nav::popBackStack) }
}

@Composable
private fun FitnessScreen(nav: NavController, vm: FitnessViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var logDialog by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.fit_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { logDialog = true }, icon = { Icon(Icons.Outlined.FitnessCenter, null) }, text = { Text(stringResource(R.string.fit_log)) }) }) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { HayatCard { Text(fmt.digits(stringResource(R.string.fit_week, s.weekCount, s.weekMinutes)), style = MaterialTheme.typography.titleMedium) } }
            item { RestTimer() }
            item {
                SectionHeader(stringResource(R.string.fit_plans)) {
                    TextButton(onClick = { nav.navigate(WorkoutPlanEditRoute()) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.fit_new_plan)) }
                }
            }
            if (s.plans.isEmpty()) item { Text(stringResource(R.string.fit_plans_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.plans, key = { it.id }) { p ->
                HayatCard(onClick = { nav.navigate(WorkoutPlanEditRoute(p.id)) }) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(p.weekdays.days.takeIf { it.isNotEmpty() }?.joinToString(", ") { fmt.weekdayShort(it) }, p.time?.let(fmt::time)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    p.exercises.forEach { e ->
                        Text("• " + e.name + (if (e.sets != null && e.reps != null) " " + fmt.digits(stringResource(R.string.fit_exercise_line, e.sets.toString(), e.reps.toString())) else ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.fit_history)) }
            if (s.sessions.isEmpty()) item { EmptyState(Icons.Outlined.DirectionsRun, stringResource(R.string.fit_history), stringResource(R.string.fit_empty)) }
            items(s.sessions.take(20), key = { it.id }) { ses ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fmt.dayMonth(ses.date) + " · " + fmt.digits(androidx.compose.ui.platform.LocalResources.current.getString(UiR.string.label_minutes, ses.durationMinutes)))
                        Text((s.plans.firstOrNull { it.id == ses.planId }?.name ?: stringResource(R.string.fit_plan_none)) + (ses.steps?.let { " · " + fmt.number(it) + " 👣" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.deleteSession(ses) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                }
            }
            item { InfoBanner(stringResource(R.string.fit_manual_note)) }
        }
    }
    if (logDialog) LogDialog(s.plans, onDismiss = { logDialog = false }) { plan, min, steps, note -> vm.logSession(plan, min, steps, note); logDialog = false }
}

/** Short foreground rest timer; it does not need to survive process death. */
@Composable
private fun RestTimer() {
    val fmt = LocalFormatter.current
    var remaining by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) { delay(1000); remaining-- }
        running = false
    }
    HayatCard {
        Text(stringResource(R.string.fit_rest_timer), style = MaterialTheme.typography.titleSmall)
        if (running) Text(fmt.digits(stringResource(R.string.fit_rest_left, remaining.toString())), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90).forEach { sec -> OutlinedButton(onClick = { remaining = sec; running = true }) { Text(fmt.digits(stringResource(R.string.fit_rest_start, sec.toString()))) } }
            if (running) TextButton(onClick = { running = false; remaining = 0 }) { Text(stringResource(UiR.string.action_stop)) }
        }
    }
}

@Composable
private fun LogDialog(plans: List<WorkoutPlan>, onDismiss: () -> Unit, onSave: (String?, Int, Int?, String) -> Unit) {
    var plan by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableStateOf("30") }
    var steps by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val none = stringResource(R.string.fit_plan_none)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.fit_log)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plans.isNotEmpty()) ChoiceChips(listOf<String?>(null) + plans.map { it.id }, plan, { id -> plans.firstOrNull { it.id == id }?.name ?: none }, { plan = it })
                NumberField(minutes, { minutes = it }, stringResource(R.string.fit_minutes))
                NumberField(steps, { steps = it }, stringResource(R.string.fit_steps))
                OutlinedTextField(note, { note = it.take(200) }, label = { Text(stringResource(UiR.string.label_notes)) })
            }
        },
        confirmButton = { TextButton(onClick = { minutes.toIntOrNull()?.let { onSave(plan, it, steps.toIntOrNull(), note) } }) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } })
}

@Composable
private fun WorkoutPlanEditScreen(onBack: () -> Unit, vm: WorkoutPlanEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val f = form
    HayatScreen(title = stringResource(if (f?.isNew != false) R.string.fit_new_plan else R.string.fit_edit_plan), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        }) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val p = f.plan
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(p.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(UiR.string.label_name), error = f.errors.forField("name"), maxLength = 120)
            FormTextField(p.description, { v -> vm.edit { it.copy(description = v) } }, stringResource(UiR.string.label_description), singleLine = false)
            Text(stringResource(R.string.fit_schedule), style = MaterialTheme.typography.titleSmall)
            WeekdayPicker(p.weekdays, { w -> vm.edit { it.copy(weekdays = w) } }, firstDay)
            TimeField(stringResource(R.string.fit_time), p.time, { t -> vm.edit { it.copy(time = t) } }, allowClear = true)
            SwitchRow(stringResource(R.string.fit_reminder), p.reminderEnabled, { v -> vm.edit { it.copy(reminderEnabled = v) } })
            if (f.errors.forField("schedule") != null) Text(stringResource(UiR.string.error_required), color = MaterialTheme.colorScheme.error)
            SectionHeader(stringResource(R.string.fit_exercises))
            p.exercises.forEach { e ->
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(e.name, { v -> vm.editExercise(e.id) { it.copy(name = v.take(120)) } }, Modifier.weight(1f), label = { Text(stringResource(R.string.fit_exercise)) }, singleLine = true)
                        IconButton(onClick = { vm.removeExercise(e.id) }) { Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(e.sets?.toString().orEmpty(), { v -> vm.editExercise(e.id) { it.copy(sets = v.toIntOrNull()) } }, stringResource(R.string.fit_sets), Modifier.weight(1f))
                        NumberField(e.reps?.toString().orEmpty(), { v -> vm.editExercise(e.id) { it.copy(reps = v.toIntOrNull()) } }, stringResource(R.string.fit_reps), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(e.durationSeconds?.toString().orEmpty(), { v -> vm.editExercise(e.id) { it.copy(durationSeconds = v.toIntOrNull()) } }, stringResource(R.string.fit_duration_s), Modifier.weight(1f))
                        NumberField(e.restSeconds?.toString().orEmpty(), { v -> vm.editExercise(e.id) { it.copy(restSeconds = v.toIntOrNull()) } }, stringResource(R.string.fit_rest_s), Modifier.weight(1f))
                    }
                }
            }
            TextButton(onClick = vm::addExercise) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.fit_add_exercise)) }
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; vm.delete() }, { confirm = false }, destructive = true)
}
