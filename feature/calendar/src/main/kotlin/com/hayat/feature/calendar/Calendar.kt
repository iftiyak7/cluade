package com.hayat.feature.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.domain.productivity.EventCalculator
import com.hayat.core.domain.repository.EventRepository
import com.hayat.core.domain.repository.OccurrenceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.domain.repository.RoutineRepository
import com.hayat.core.domain.repository.TaskRepository
import com.hayat.core.domain.routine.RoutineActionsUseCase
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.CalendarEvent
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.Task
import com.hayat.core.model.TimelineItem
import com.hayat.core.model.UserPreferences
import com.hayat.core.navigation.CalendarRoute
import com.hayat.core.navigation.EventEditRoute
import com.hayat.core.navigation.EventsRoute
import com.hayat.core.navigation.ReminderRescheduleRoute
import com.hayat.core.navigation.RoutineDetailRoute
import com.hayat.core.navigation.TaskDetailRoute
import com.hayat.core.navigation.TaskEditRoute
import com.hayat.core.time.HijriCalendar
import com.hayat.core.time.instantRange
import com.hayat.core.time.monthGrid
import com.hayat.core.time.weekDays
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

enum class CalendarMode { DAY, WEEK, MONTH }

data class DayAgenda(
    val date: LocalDate,
    val timeline: List<TimelineItem>,
    val tasks: List<Task>,
    val events: List<CalendarEvent>,
    val reminders: List<Pair<Reminder, ReminderOccurrence>>,
) {
    val count: Int get() = timeline.size + tasks.size + events.size + reminders.size
}

data class CalendarState(
    val loading: Boolean = true,
    val mode: CalendarMode = CalendarMode.DAY,
    val selected: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val days: Map<LocalDate, DayAgenda> = emptyMap(),
    val prefs: UserPreferences = UserPreferences(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedState: SavedStateHandle,
    routines: RoutineRepository,
    tasks: TaskRepository,
    events: EventRepository,
    reminders: ReminderRepository,
    occurrences: OccurrenceRepository,
    prefs: PreferencesRepository,
    private val routineActions: RoutineActionsUseCase,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val initial = savedState.toRoute<CalendarRoute>().date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: time.today()
    private val selection = MutableStateFlow(CalendarMode.DAY to initial)

    val state = selection.flatMapLatest { (mode, selected) ->
        val zone = time.zone()
        val monthStart = selected.withDayOfMonth(1).minusDays(7)
        val monthEnd = selected.withDayOfMonth(selected.lengthOfMonth()).plusDays(7)
        val (from, _) = monthStart.instantRange(zone)
        val (_, to) = monthEnd.instantRange(zone)
        combine(
            combine(routines.observeRoutines(), routines.observeLogs(monthStart, monthEnd), tasks.observeTasks()) { r, l, t -> Triple(r, l, t) },
            events.observeEvents(), reminders.observeReminders(), occurrences.observeBetween(from, to), prefs.preferences,
        ) { (r, logs, t), ev, rem, occ, p ->
            val remById = rem.associateBy { it.id }
            val range = generateSequence(monthStart) { it.plusDays(1) }.takeWhile { !it.isAfter(monthEnd) }
            val days = range.associateWith { d ->
                DayAgenda(
                    date = d,
                    timeline = RoutinePlanner.timeline(r, logs, d),
                    tasks = t.filter { !it.isArchived && it.dueDate == d },
                    events = ev.filter { !it.isArchived && occursOn(it, d) },
                    // Linked routine reminders are already represented by the timeline.
                    reminders = occ.filter { it.localDate == d }.mapNotNull { o -> remById[o.reminderId]?.takeIf { it.targetType == com.hayat.core.model.ReminderTargetType.NONE }?.let { it to o } },
                )
            }
            CalendarState(false, mode, selected, time.today(), days, p)
        }
    }.stateInVm(CalendarState())

    private fun occursOn(e: CalendarEvent, d: LocalDate) =
        if (e.repeatsAnnually) EventCalculator.nextOccurrence(e, d) == d else e.date == d

    fun setMode(m: CalendarMode) = selection.update { m to it.second }
    fun select(d: LocalDate) = selection.update { it.first to d }
    fun open(d: LocalDate) = selection.update { CalendarMode.DAY to d }
    fun shift(delta: Int) = selection.update { (m, d) ->
        m to when (m) { CalendarMode.DAY -> d.plusDays(delta.toLong()); CalendarMode.WEEK -> d.plusWeeks(delta.toLong()); CalendarMode.MONTH -> d.plusMonths(delta.toLong()) }
    }
    fun today() = selection.update { it.first to time.today() }
    fun mark(item: TimelineItem, status: ActivityStatus?) = safeLaunch { routineActions.log(item.activity.id, item.date, status).handle() }
}

fun NavGraphBuilder.calendarGraph(nav: NavController) {
    composable<CalendarRoute> { CalendarScreen(nav) }
}

/** Localized Hijri date string, or null if outside the supported range. */
@Composable
fun hijriLabel(date: LocalDate, offset: Int): String? {
    val h = HijriCalendar.fromGregorian(date, offset) ?: return null
    val fmt = LocalFormatter.current
    return fmt.hijri(h.day, h.month, h.year) // "14 Jumada II 1448" / "১৪ জমাদিউস সানি ১৪৪৮"
}

@Composable
private fun CalendarScreen(nav: NavController, vm: CalendarViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    val title = when (s.mode) {
        CalendarMode.DAY -> fmt.date(s.selected)
        CalendarMode.WEEK -> s.selected.weekDays(s.prefs.firstDayOfWeek).let { fmt.dayMonth(it.first()) + " – " + fmt.dayMonth(it.last()) }
        CalendarMode.MONTH -> fmt.monthYear(YearMonth.from(s.selected))
    }
    HayatScreen(
        title = title, onBack = null,
        actions = {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.cal_prev)) }
            IconButton(onClick = vm::today) { Icon(Icons.Outlined.Today, stringResource(R.string.cal_today)) }
            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.cal_next)) }
        },
    ) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CalendarMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(s.mode == m, { vm.setMode(m) }, SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size)) {
                            Text(stringResource(when (m) { CalendarMode.DAY -> R.string.cal_day; CalendarMode.WEEK -> R.string.cal_week; CalendarMode.MONTH -> R.string.cal_month }))
                        }
                    }
                }
            }
            if (s.prefs.islamicModuleEnabled) item {
                hijriLabel(s.selected, s.prefs.hijriOffsetDays)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            when (s.mode) {
                CalendarMode.DAY -> dayItems(s.days[s.selected], s, nav, vm)
                CalendarMode.WEEK -> {
                    val week = s.selected.weekDays(s.prefs.firstDayOfWeek)
                    items(week) { d -> WeekDayRow(d, s.days[d], d == s.today) { vm.open(d) } }
                }
                CalendarMode.MONTH -> item { MonthGrid(s, vm::open) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dayItems(agenda: DayAgenda?, s: CalendarState, nav: NavController, vm: CalendarViewModel) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { nav.navigate(TaskEditRoute(date = s.selected.toString())) }) { Icon(Icons.Outlined.AddTask, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.cal_add_task)) }
            TextButton(onClick = { nav.navigate(EventEditRoute(date = s.selected.toString())) }) { Icon(Icons.Outlined.EventAvailable, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.cal_add_event)) }
        }
    }
    if (agenda == null || agenda.count == 0) { item { EmptyState(Icons.Outlined.EventNote, stringResource(com.hayat.core.ui.R.string.label_date), stringResource(R.string.cal_empty_day)) }; return }
    val conflicts = RoutinePlanner.conflictsOnDate(agenda.timeline)
    if (conflicts.isNotEmpty()) item { InfoBanner(LocalFormatter.current.digits(stringResource(R.string.cal_conflicts, conflicts.size)), tone = StatusTone.WARNING) }
    if (agenda.events.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.cal_events)) }
        items(agenda.events, key = { "e" + it.id }) { e ->
            HayatCard(onClick = { nav.navigate(EventsRoute) }) {
                Text(e.title, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(e.kind.labelRes()) + (e.time?.let { " · " + LocalFormatter.current.time(it) } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (agenda.timeline.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.cal_routine)) }
        items(agenda.timeline, key = { "a" + it.activity.id }) { t -> TimelineRow(t, onOpen = { nav.navigate(RoutineDetailRoute(t.routine.id)) }, onMark = { vm.mark(t, it) }) }
    }
    if (agenda.tasks.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.cal_tasks)) }
        items(agenda.tasks, key = { "t" + it.id }) { t ->
            HayatCard(onClick = { nav.navigate(TaskDetailRoute(t.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (t.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, Modifier.weight(1f))
                    t.dueTime?.let { Text(LocalFormatter.current.time(it), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
    if (agenda.reminders.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.cal_reminders)) }
        items(agenda.reminders, key = { "r" + it.second.id }) { (r, o) ->
            HayatCard(onClick = { nav.navigate(ReminderRescheduleRoute(o.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(r.title, Modifier.weight(1f))
                    Text(LocalFormatter.current.time(LocalDateTime.ofInstant(o.effectiveAt, ZoneId.systemDefault()).toLocalTime()), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(t: TimelineItem, onOpen: () -> Unit, onMark: (ActivityStatus?) -> Unit) {
    val fmt = LocalFormatter.current
    HayatCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).background(Color(t.routine.color), MaterialTheme.shapes.small))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.activity.title, style = MaterialTheme.typography.titleSmall)
                Text(fmt.time(t.activity.startTime) + "–" + fmt.time(t.activity.endTime) + " · " + t.routine.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            t.log?.let { StatusChip(stringResource(it.status.labelRes()), if (it.status == ActivityStatus.COMPLETED) StatusTone.SUCCESS else StatusTone.NEUTRAL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ActivityStatus.COMPLETED, ActivityStatus.SKIPPED, ActivityStatus.POSTPONED).forEach { st ->
                FilterChip(t.log?.status == st, { onMark(if (t.log?.status == st) null else st) }, label = { Text(stringResource(st.labelRes())) })
            }
        }
    }
}

@Composable
private fun WeekDayRow(d: LocalDate, agenda: DayAgenda?, isToday: Boolean, onClick: () -> Unit) {
    val fmt = LocalFormatter.current
    HayatCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt.weekdayShort(d.dayOfWeek), style = MaterialTheme.typography.labelMedium, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.dayOfMonth(d), style = MaterialTheme.typography.titleLarge, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                val first = agenda?.let { a -> (a.events.map { it.title } + a.timeline.map { it.activity.title } + a.tasks.map { it.title }).take(3) }.orEmpty()
                if (first.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                first.forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            }
            Text(fmt.digits(stringResource(R.string.cal_items, agenda?.count ?: 0)), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MonthGrid(s: CalendarState, onOpen: (LocalDate) -> Unit) {
    val fmt = LocalFormatter.current
    val grid = s.selected.monthGrid(s.prefs.firstDayOfWeek)
    val month = s.selected.month
    Column {
        Row(Modifier.fillMaxWidth()) {
            grid.take(7).forEach { Text(fmt.weekdayNarrow(it.dayOfWeek), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val count = s.days[d]?.count ?: 0
                    val inMonth = d.month == month
                    val summary = stringResource(R.string.cal_day_summary, fmt.date(d), count)
                    Column(
                        Modifier.weight(1f).aspectRatio(0.9f).clip(MaterialTheme.shapes.small)
                            .background(if (d == s.selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(role = Role.Button) { onOpen(d) }.semantics { contentDescription = summary }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(fmt.dayOfMonth(d), style = MaterialTheme.typography.bodyMedium,
                            color = when { d == s.today -> MaterialTheme.colorScheme.primary; inMonth -> MaterialTheme.colorScheme.onSurface; else -> MaterialTheme.colorScheme.outline })
                        if (count > 0) Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
    }
}
