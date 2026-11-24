package com.hayat.feature.study

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ColorSwatchPicker
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.LinearMeter
import com.hayat.core.designsystem.component.ProgressRing
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.theme.UserColorChoices
import com.hayat.core.domain.productivity.FocusTimerState
import com.hayat.core.domain.productivity.StudyUseCases
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.StudyRepository
import com.hayat.core.model.DateRule
import com.hayat.core.model.FocusKind
import com.hayat.core.model.FocusSession
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.PomodoroSettings
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Schedule
import com.hayat.core.model.StudySubject
import com.hayat.core.model.TimeRule
import com.hayat.core.model.ZonePolicy
import com.hayat.core.navigation.FocusRoute
import com.hayat.core.navigation.StudyRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class StudyState(val loading: Boolean = true, val subjects: List<StudySubject> = emptyList(), val sessions: List<FocusSession> = emptyList(), val weekMinutes: Map<String?, Int> = emptyMap(), val today: java.time.LocalDate = java.time.LocalDate.now())

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val study: StudyRepository,
    private val useCases: StudyUseCases,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(study.observeSubjects(), study.observeSessions(today.minusDays(29), today), prefs.preferences) { subj, ses, p ->
        val weekStart = today.with(TemporalAdjusters.previousOrSame(p.firstDayOfWeek))
        val week = ses.filter { it.kind == FocusKind.FOCUS && !it.date.isBefore(weekStart) }
        StudyState(false, subj.filter { !it.isArchived }, ses.filter { it.kind == FocusKind.FOCUS }, week.groupBy { it.subjectId }.mapValues { (_, l) -> (l.sumOf { it.actualSeconds } / 60).toInt() }, today)
    }.stateInVm(StudyState())

    fun saveSubject(existing: StudySubject?, name: String, goal: Int, exam: java.time.LocalDate?, color: Long) = safeLaunch {
        val now = time.now()
        val s = (existing ?: StudySubject(ids.newId(), name, color, createdAt = now, updatedAt = now)).copy(name = name, weeklyGoalMinutes = goal, examDate = exam, color = color)
        useCases.saveSubject(s).handle()
    }
    fun deleteSubject(s: StudySubject) = safeLaunch { useCases.deleteSubject(s.id).handle() }
    fun deleteSession(s: FocusSession) = safeLaunch { study.deleteSession(s.id) }
}

/**
 * Pomodoro timer. Times are measured with SystemClock.elapsedRealtime (monotonic, unaffected by
 * clock changes) and persisted in SavedStateHandle so the timer survives process recreation.
 * A one-time reminder fires at the end so the user is notified while the app is in the background.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val study: StudyRepository,
    private val prefs: PreferencesRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private fun mono(): Instant = Instant.ofEpochMilli(SystemClock.elapsedRealtime())
    private val _state = MutableStateFlow(restore())
    val state = _state.asStateFlow()
    val subjects = study.observeSubjects().map { l -> l.filter { !it.isArchived } }.stateInVm(emptyList())
    var settings = PomodoroSettings(); private set
    private var wallStart: Instant? = saved.get<Long>("wallStart")?.let(Instant::ofEpochMilli)

    init {
        safeLaunch {
            settings = prefs.current().pomodoro
            if (_state.value.startedAt == null && _state.value.pausedElapsed.isZero && saved.get<Int>("planned") == null)
                persist(_state.value.copy(plannedMinutes = settings.focusMinutes))
        }
    }

    private fun restore(): FocusTimerState = FocusTimerState(
        kind = saved.get<String>("kind")?.let { k -> FocusKind.entries.firstOrNull { it.name == k } } ?: FocusKind.FOCUS,
        subjectId = saved.get<String>("subject"),
        startedAt = saved.get<Long>("startedMono")?.let(Instant::ofEpochMilli),
        pausedElapsed = Duration.ofMillis(saved.get<Long>("paused") ?: 0),
        plannedMinutes = saved.get<Int>("planned") ?: 25,
        completedFocusSessions = saved.get<Int>("done") ?: 0,
    )

    private fun persist(s: FocusTimerState) {
        _state.value = s
        saved["kind"] = s.kind.name; saved["subject"] = s.subjectId; saved["startedMono"] = s.startedAt?.toEpochMilli()
        saved["paused"] = s.pausedElapsed.toMillis(); saved["planned"] = s.plannedMinutes; saved["done"] = s.completedFocusSessions
        saved["wallStart"] = wallStart?.toEpochMilli()
    }

    fun remaining(): Duration = _state.value.remaining(mono())
    fun selectSubject(id: String?) = persist(_state.value.copy(subjectId = id))

    fun start() = safeLaunch {
        if (wallStart == null) wallStart = time.now()
        val s = _state.value.start(mono())
        persist(s)
        scheduleEnd(time.now().plus(s.remaining(mono())))
    }

    fun pause() = safeLaunch { persist(_state.value.pause(mono())); linker.remove(ReminderTargetType.FOCUS, TARGET) }

    fun reset() = safeLaunch { wallStart = null; persist(_state.value.reset()); linker.remove(ReminderTargetType.FOCUS, TARGET) }

    /** Called by the UI tick; records the session and advances to the next phase when time is up. */
    fun tick() {
        val s = _state.value
        if (s.isRunning && s.isFinished(mono())) finish(completed = true)
    }

    fun skip() = finish(completed = false)

    private fun finish(completed: Boolean) = safeLaunch {
        val s = _state.value
        val elapsed = s.elapsed(mono()).coerceAtMost(Duration.ofMinutes(s.plannedMinutes.toLong()))
        val start = wallStart ?: time.now().minus(elapsed)
        if (elapsed.seconds >= 60) {
            study.saveSession(FocusSession(ids.newId(), s.subjectId, s.kind, start, start.plus(elapsed), s.plannedMinutes, elapsed.seconds, completed, start.atZone(time.zone()).toLocalDate()))
        }
        linker.remove(ReminderTargetType.FOCUS, TARGET)
        wallStart = null
        settings = prefs.current().pomodoro
        persist(s.next(settings))
        if (completed) message(R.string.fc_done)
    }

    private suspend fun scheduleEnd(at: Instant) {
        val local = at.plusSeconds(59).truncatedTo(ChronoUnit.MINUTES).atZone(time.zone())
        linker.upsert(LinkedReminderSpec(ReminderTargetType.FOCUS, TARGET, "focus", category = ReminderCategory.STUDY,
            schedules = listOf(Schedule(DateRule.Once, TimeRule.At(local.toLocalTime()), local.toLocalDate(), zonePolicy = ZonePolicy.FIXED, zoneId = time.zone()))))
    }

    private fun Duration.coerceAtMost(max: Duration) = if (this > max) max else this

    companion object { private const val TARGET = "focus" }
}

fun NavGraphBuilder.studyGraph(nav: NavController) {
    composable<StudyRoute> { StudyScreen(nav) }
    composable<FocusRoute> { FocusScreen(nav::popBackStack) }
}

@Composable
private fun StudyScreen(nav: NavController, vm: StudyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var editing by remember { mutableStateOf<StudySubject?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.st_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(FocusRoute) }, icon = { Icon(Icons.Outlined.Timer, null) }, text = { Text(stringResource(R.string.st_focus)) }) }) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { HayatCard { Text(fmt.digits(stringResource(R.string.st_total_week, s.weekMinutes.values.sum().toString())), style = MaterialTheme.typography.titleMedium) } }
            item { SectionHeader(stringResource(R.string.st_subjects)) { TextButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.st_add_subject)) } } }
            if (s.subjects.isEmpty()) item { InfoBanner(stringResource(R.string.st_no_subjects)) }
            items(s.subjects, key = { it.id }) { sub ->
                val minutes = s.weekMinutes[sub.id] ?: 0
                HayatCard(onClick = { editing = sub }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(sub.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(sub.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        sub.examDate?.let { d ->
                            val days = ChronoUnit.DAYS.between(s.today, d)
                            if (days >= 0) StatusChip(if (days == 0L) stringResource(R.string.st_exam_today) else fmt.digits(stringResource(R.string.st_exam_in, days.toInt())), if (days <= 3) StatusTone.WARNING else StatusTone.INFO, icon = Icons.Outlined.Event)
                        }
                    }
                    if (sub.weeklyGoalMinutes > 0) {
                        val desc = stringResource(R.string.st_week_progress, fmt.number(minutes), fmt.number(sub.weeklyGoalMinutes))
                        LinearMeter(minutes.toFloat() / sub.weeklyGoalMinutes, color = Color(sub.color), description = desc)
                        Text(desc, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.st_history)) }
            if (s.sessions.isEmpty()) item { Text(stringResource(R.string.st_no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.sessions.take(20), key = { it.id }) { ses ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ses.completed) Icons.Outlined.CheckCircle else Icons.Outlined.Timelapse, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(fmt.digits(stringResource(R.string.st_session_line, fmt.dayMonth(ses.date) + " · " + (s.subjects.firstOrNull { it.id == ses.subjectId }?.name ?: stringResource(R.string.fc_no_subject)), (ses.actualSeconds / 60).toString())), Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteSession(ses) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                }
            }
        }
    }
    if (adding || editing != null) SubjectDialog(editing, onDismiss = { adding = false; editing = null }, onDelete = { editing?.let(vm::deleteSubject); editing = null }) { name, goal, exam, color ->
        vm.saveSubject(editing, name, goal, exam, color); adding = false; editing = null
    }
}

@Composable
private fun SubjectDialog(existing: StudySubject?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (String, Int, java.time.LocalDate?, Long) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var goal by remember { mutableStateOf(existing?.weeklyGoalMinutes?.takeIf { it > 0 }?.toString().orEmpty()) }
    var exam by remember { mutableStateOf(existing?.examDate) }
    var color by remember { mutableStateOf(existing?.color ?: UserColorChoices[1]) }
    val fmt = LocalFormatter.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (existing == null) R.string.st_add_subject else R.string.st_edit_subject)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(UiR.string.label_name)) }, singleLine = true)
                NumberField(goal, { goal = it }, stringResource(R.string.st_weekly_goal))
                DateField(stringResource(R.string.st_exam_date), exam, { exam = it }, allowClear = true)
                ColorSwatchPicker(UserColorChoices, color, { color = it }, { i -> fmt.digits(ctx.getString(UiR.string.label_color_option, i + 1)) })
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), goal.toIntOrNull() ?: 0, exam, color) }) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onDelete) { Text(stringResource(UiR.string.action_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) }
            }
        })
}

@Composable
private fun FocusScreen(onBack: () -> Unit, vm: FocusViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var remainingMs by remember { mutableLongStateOf(vm.remaining().toMillis()) }
    LaunchedEffect(s) {
        remainingMs = vm.remaining().toMillis()
        while (s.isRunning) { delay(500); remainingMs = vm.remaining().toMillis(); vm.tick() }
    }
    val fmt = LocalFormatter.current
    val total = s.plannedMinutes * 60_000L
    val secs = (remainingMs + 999) / 1000
    val label = fmt.digits("%02d:%02d".format(secs / 60, secs % 60))
    HayatScreen(title = stringResource(R.string.st_focus), onBack = onBack, snackbarHostState = host) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(when (s.kind) { FocusKind.FOCUS -> R.string.fc_focus; FocusKind.SHORT_BREAK -> R.string.fc_short; FocusKind.LONG_BREAK -> R.string.fc_long }), style = MaterialTheme.typography.headlineSmall)
            ProgressRing(if (total == 0L) 0f else 1f - remainingMs.toFloat() / total, size = 240.dp, stroke = 14.dp, description = stringResource(R.string.fc_remaining, label)) {
                Text(label, style = MaterialTheme.typography.displayMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.isRunning) Button(onClick = vm::pause) { Icon(Icons.Outlined.Pause, null); Text(stringResource(R.string.fc_pause)) }
                else Button(onClick = vm::start) { Icon(Icons.Outlined.PlayArrow, null); Text(stringResource(if (s.pausedElapsed.isZero) R.string.fc_start else R.string.fc_resume)) }
                OutlinedButton(onClick = vm::reset) { Text(stringResource(R.string.fc_reset)) }
                OutlinedButton(onClick = vm::skip) { Text(stringResource(R.string.fc_skip)) }
            }
            Text(fmt.digits(stringResource(R.string.fc_sessions, s.completedFocusSessions)), style = MaterialTheme.typography.bodyMedium)
            if (subjects.isNotEmpty() && s.kind == FocusKind.FOCUS) {
                Text(stringResource(R.string.fc_subject), style = MaterialTheme.typography.titleSmall)
                val none = stringResource(R.string.fc_no_subject)
                ChoiceChips(listOf<String?>(null) + subjects.map { it.id }, s.subjectId, { id -> subjects.firstOrNull { it.id == id }?.name ?: none }, vm::selectSubject)
            }
            InfoBanner(stringResource(R.string.fc_background_note))
        }
    }
}
