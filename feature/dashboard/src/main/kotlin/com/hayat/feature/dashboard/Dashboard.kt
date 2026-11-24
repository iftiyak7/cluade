package com.hayat.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.LinearMeter
import com.hayat.core.designsystem.component.ProgressRing
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.domain.health.MedicinePlanner
import com.hayat.core.domain.health.MedicineUseCases
import com.hayat.core.domain.health.WaterCalculator
import com.hayat.core.domain.health.WaterUseCases
import com.hayat.core.domain.repository.HabitRepository
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.repository.MealRepository
import com.hayat.core.domain.repository.MedicineRepository
import com.hayat.core.domain.repository.OccurrenceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.domain.repository.RoutineRepository
import com.hayat.core.domain.repository.TaskRepository
import com.hayat.core.domain.repository.WaterRepository
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.domain.task.TaskQuery
import com.hayat.core.domain.worship.PrayerTimeCalculator
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.DashboardCard
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.MealStatus
import com.hayat.core.model.MedicineDose
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Prayer
import com.hayat.core.model.Priority
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Task
import com.hayat.core.model.TaskFilter
import com.hayat.core.model.TimelineItem
import com.hayat.core.model.UserPreferences
import com.hayat.core.model.WaterDay
import com.hayat.core.navigation.*
import com.hayat.core.time.HijriCalendar
import com.hayat.core.time.instantRange
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import com.hayat.core.ui.R as UiR
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost

data class DashboardState(
    val loading: Boolean = true,
    val prefs: UserPreferences = UserPreferences(),
    val today: LocalDate = LocalDate.now(),
    val hour: Int = 9,
    val todayTasks: List<Task> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val upcoming: List<Pair<Reminder, ReminderOccurrence>> = emptyList(),
    val missedToday: Int = 0,
    val water: WaterDay? = null,
    val doses: List<MedicineDose> = emptyList(),
    val nextPrayer: Pair<Prayer, ZonedDateTime>? = null,
    val prayersLogged: Int = 0,
    val zikrToday: Int = 0,
    val habitsScheduled: Int = 0,
    val habitsOnTrack: Int = 0,
    val mealsPlanned: Int = 0,
    val mealsLogged: Int = 0,
) {
    val doneCount: Int get() = todayTasks.count { it.isCompleted } + timeline.count { it.log?.status == ActivityStatus.COMPLETED }
    val totalCount: Int get() = todayTasks.size + timeline.size
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    prefs: PreferencesRepository,
    tasks: TaskRepository,
    routines: RoutineRepository,
    reminders: ReminderRepository,
    occurrences: OccurrenceRepository,
    water: WaterRepository,
    medicines: MedicineRepository,
    ibadah: IbadahRepository,
    habits: HabitRepository,
    meals: MealRepository,
    private val waterUseCases: WaterUseCases,
    private val medicineUseCases: MedicineUseCases,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val today = time.today()
    private val zone = time.zone()
    private val dayRange = today.instantRange(zone)
    private val dayStart = dayRange.first
    private val dayEnd = dayRange.second

    private val planning = combine(tasks.observeTasks(), routines.observeRoutines(), routines.observeLogs(today, today), prefs.preferences) { t, r, l, p ->
        // Today's tasks: due today (and overdue if the user wants them shown), plus tasks completed today.
        val open = TaskQuery.apply(t, TaskFilter.TODAY, today, includeOverdueInToday = p.showOverdueInToday)
        val doneToday = t.filter { it.isCompleted && it.completedAt?.atZone(zone)?.toLocalDate() == today && !it.isArchived }
        Triple(p, (open + doneToday).distinctBy { it.id }, RoutinePlanner.timeline(r, l, today))
    }
    private val remindersFlow = combine(reminders.observeReminders(), occurrences.observeUpcoming(time.now(), 10), occurrences.observeBetween(dayStart, dayEnd)) { rem, up, todayOcc ->
        val byId = rem.associateBy { it.id }
        up.mapNotNull { o -> byId[o.reminderId]?.let { it to o } } to todayOcc.count { it.status == OccurrenceStatus.MISSED }
    }
    private val health = combine(water.observeLogs(today, today), water.observeGoals(), medicines.observeMedicines(), medicines.observeDoseLogs(today, today)) { wl, wg, meds, dl ->
        WaterCalculator.days(today, today, wl, wg).first() to MedicinePlanner.dosesFor(today, meds, dl)
    }
    private val lifestyle = combine(
        ibadah.observePrayerLogs(today, today), ibadah.observeZikrProgress(today, today),
        combine(habits.observeHabits(), habits.observeCompletions(today, today)) { h, c -> h to c },
        combine(meals.observePlans(), meals.observeLogs(today, today)) { p, l -> p to l },
    ) { pl, zp, (h, hc), (mp, ml) ->
        val scheduled = h.filter { !it.isArchived && !it.isPaused && today.dayOfWeek in it.weekdays }
        val counts = hc.associate { it.habitId to it.count }
        val planned = mp.filter { !it.isArchived && today.dayOfWeek in it.weekdays }
        listOf(pl.size, zp.sumOf { it.count }, scheduled.size, scheduled.count { it.isSuccess(counts[it.id] ?: 0) }, planned.size, ml.count { it.mealPlanId != null && it.status == MealStatus.EATEN })
    }

    val state = combine(planning, remindersFlow, health, lifestyle) { (p, t, tl), (up, missed), (w, doses), l ->
        DashboardState(
            loading = false, prefs = p, today = today, hour = time.zonedNow().hour,
            todayTasks = t, timeline = tl, upcoming = up.take(3), missedToday = missed, water = w, doses = doses,
            nextPrayer = if (p.islamicModuleEnabled) PrayerTimeCalculator.next(time.now(), p.prayer) else null,
            prayersLogged = l[0], zikrToday = l[1], habitsScheduled = l[2], habitsOnTrack = l[3], mealsPlanned = l[4], mealsLogged = l[5],
        )
    }.stateInVm(DashboardState())

    fun addWater() = safeLaunch { waterUseCases.add(state.value.prefs.water.defaultGlassMl).handle() }
    fun takeDose(d: MedicineDose) = safeLaunch { medicineUseCases.setDoseStatus(d, DoseStatus.TAKEN).handle() }
}

fun NavGraphBuilder.dashboardGraph(nav: NavController) {
    composable<HomeRoute> { DashboardScreen(nav) }
    composable<AllFeaturesRoute> { AllFeaturesScreen(nav) }
}

@Composable
private fun DashboardScreen(nav: NavController, vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val fmt = LocalFormatter.current
    Scaffold(snackbarHost = { SnackbarHost(host) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(when (s.hour) { in 5..11 -> R.string.db_morning; in 12..16 -> R.string.db_afternoon; in 17..20 -> R.string.db_evening; else -> R.string.db_night }), style = MaterialTheme.typography.headlineSmall)
                        val hijri = if (s.prefs.islamicModuleEnabled) HijriCalendar.fromGregorian(s.today, s.prefs.hijriOffsetDays) else null
                        Text(fmt.date(s.today) + (hijri?.let { " · " + fmt.digits("${it.day}/${it.month}/${it.year}") } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { nav.navigate(SearchRoute) }) { Icon(Icons.Outlined.Search, stringResource(R.string.db_search)) }
                    IconButton(onClick = { nav.navigate(DashboardSettingsRoute) }) { Icon(Icons.Outlined.Tune, stringResource(R.string.db_customize)) }
                }
            }
            val islamicCards = setOf(DashboardCard.PRAYER, DashboardCard.ZIKR)
            items(s.prefs.dashboardCards.filter { it.visible && (s.prefs.islamicModuleEnabled || it.card !in islamicCards) }, key = { it.card.name }) { cfg ->
                DashboardCardView(cfg.card, s, nav, vm)
            }
            item {
                TextButton(onClick = { nav.navigate(AllFeaturesRoute) }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Apps, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.db_all_features)) }
            }
            if (s.totalCount == 0 && s.upcoming.isEmpty()) item { Text(stringResource(R.string.db_welcome_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun DashboardCardView(card: DashboardCard, s: DashboardState, nav: NavController, vm: DashboardViewModel) {
    val fmt = LocalFormatter.current
    when (card) {
        DashboardCard.TODAY_PROGRESS -> HayatCard(onClick = { nav.navigate(CalendarRoute()) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val frac = if (s.totalCount == 0) 0f else s.doneCount.toFloat() / s.totalCount
                val text = if (s.totalCount == 0) stringResource(R.string.db_progress_empty) else stringResource(R.string.db_progress_text, fmt.number(s.doneCount), fmt.number(s.totalCount))
                ProgressRing(frac, description = text) { Text(fmt.percent(frac), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(16.dp))
                Column { Text(stringResource(R.string.db_progress), style = MaterialTheme.typography.titleMedium); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        DashboardCard.PRIORITIES -> HayatCard(onClick = { nav.navigate(TasksRoute) }) {
            CardHeader(Icons.Outlined.Flag, stringResource(R.string.db_priorities))
            val pri = s.todayTasks.filter { !it.isCompleted && it.priority >= Priority.HIGH }.take(3)
            if (pri.isEmpty()) Text(stringResource(R.string.db_no_priorities), color = MaterialTheme.colorScheme.onSurfaceVariant)
            pri.forEach { t -> Text("• " + t.title + " · " + stringResource(t.priority.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        DashboardCard.NEXT_TASK -> HayatCard(onClick = { s.todayTasks.firstOrNull { !it.isCompleted }?.let { nav.navigate(TaskDetailRoute(it.id)) } ?: nav.navigate(TasksRoute) }) {
            CardHeader(Icons.Outlined.TaskAlt, stringResource(R.string.db_next_task))
            val next = s.todayTasks.filter { !it.isCompleted }.sortedWith(compareBy(nullsLast()) { it.dueTime }).firstOrNull()
            if (next == null) Text(stringResource(R.string.db_no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(next.title + (next.dueTime?.let { " · " + fmt.time(it) } ?: ""), style = MaterialTheme.typography.bodyLarge)
        }
        DashboardCard.UPCOMING_REMINDERS -> HayatCard(onClick = { nav.navigate(RemindersRoute) }) {
            CardHeader(Icons.Outlined.Alarm, stringResource(R.string.db_upcoming))
            if (s.upcoming.isEmpty()) Text(stringResource(R.string.db_no_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.upcoming.forEach { (r, o) ->
                val t = LocalDateTime.ofInstant(o.effectiveAt, ZoneId.systemDefault())
                Text((if (t.toLocalDate() == s.today) fmt.time(t.toLocalTime()) else fmt.dayMonth(t.toLocalDate()) + " " + fmt.time(t.toLocalTime())) + " · " + reminderTitle(r), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DashboardCard.MISSED_REMINDERS -> HayatCard(onClick = { nav.navigate(RemindersRoute) }) {
            CardHeader(Icons.Outlined.NotificationImportant, stringResource(R.string.db_missed))
            Text(if (s.missedToday == 0) stringResource(R.string.db_missed_none) else fmt.digits(stringResource(R.string.db_missed_text, s.missedToday)))
        }
        DashboardCard.WATER -> HayatCard(onClick = { nav.navigate(WaterRoute) }) {
            CardHeader(Icons.Outlined.WaterDrop, stringResource(R.string.db_water))
            s.water?.let { w ->
                val text = fmt.digits("${w.totalMl} / ${w.goalMl} ml")
                LinearMeter(w.progress, description = text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f))
                    FilledTonalButton(onClick = vm::addWater) { Text(stringResource(R.string.db_water_add, fmt.number(s.prefs.water.defaultGlassMl))) }
                }
            }
        }
        DashboardCard.MEDICINE -> HayatCard(onClick = { nav.navigate(MedicineRoute) }) {
            CardHeader(Icons.Outlined.Medication, stringResource(R.string.db_medicine))
            val pending = s.doses.firstOrNull { it.log == null }
            when {
                s.doses.isEmpty() -> Text(stringResource(R.string.db_med_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                pending == null -> Text(stringResource(R.string.db_med_done))
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.db_med_next, pending.medicine.name, fmt.time(pending.schedule.time)), Modifier.weight(1f))
                    FilledTonalButton(onClick = { vm.takeDose(pending) }) { Text(stringResource(R.string.db_take)) }
                }
            }
        }
        DashboardCard.PRAYER -> HayatCard(onClick = { nav.navigate(IbadahRoute) }) {
            CardHeader(Icons.Outlined.Mosque, stringResource(R.string.db_prayer))
            val np = s.nextPrayer
            if (np == null) Text(stringResource(R.string.db_prayer_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(stringResource(np.first.labelRes()) + " · " + fmt.time(np.second.toLocalTime()), style = MaterialTheme.typography.bodyLarge)
            Text(fmt.digits(stringResource(R.string.db_prayer_logged, s.prayersLogged)), style = MaterialTheme.typography.bodySmall)
        }
        DashboardCard.ZIKR -> HayatCard(onClick = { nav.navigate(IbadahRoute) }) {
            CardHeader(Icons.Outlined.Spa, stringResource(R.string.db_zikr))
            Text(stringResource(R.string.db_zikr_text, fmt.number(s.zikrToday)))
        }
        DashboardCard.HABITS -> HayatCard(onClick = { nav.navigate(HabitsRoute) }) {
            CardHeader(Icons.Outlined.Repeat, stringResource(R.string.db_habits))
            if (s.habitsScheduled == 0) Text(stringResource(R.string.db_habits_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else { val t = fmt.digits(stringResource(R.string.db_habits_text, s.habitsOnTrack, s.habitsScheduled)); LinearMeter(s.habitsOnTrack.toFloat() / s.habitsScheduled, description = t); Text(t) }
        }
        DashboardCard.DIET -> HayatCard(onClick = { nav.navigate(DietRoute) }) {
            CardHeader(Icons.Outlined.Restaurant, stringResource(R.string.db_diet))
            if (s.mealsPlanned == 0) Text(stringResource(R.string.db_diet_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(fmt.digits(stringResource(R.string.db_diet_text, s.mealsLogged, s.mealsPlanned)))
        }
        DashboardCard.TIMELINE -> HayatCard(onClick = { nav.navigate(CalendarRoute()) }) {
            CardHeader(Icons.Outlined.ViewTimeline, stringResource(R.string.db_timeline))
            val now = java.time.LocalTime.now()
            val upcoming = s.timeline.filter { it.log == null && it.activity.endTime >= now }.take(3)
            if (upcoming.isEmpty()) Text(stringResource(R.string.db_timeline_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            upcoming.forEach { Text(fmt.time(it.activity.startTime) + " · " + it.activity.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun reminderTitle(r: Reminder): String = when (r.targetType) {
    ReminderTargetType.WATER -> stringResource(UiR.string.feature_water)
    ReminderTargetType.SLEEP_BEDTIME, ReminderTargetType.SLEEP_WAKE -> stringResource(UiR.string.feature_sleep)
    ReminderTargetType.PRAYER -> runCatching { Prayer.valueOf(r.title) }.getOrNull()?.let { stringResource(it.labelRes()) } ?: r.title
    ReminderTargetType.FOCUS -> stringResource(UiR.string.feature_study)
    else -> r.title
}

private data class FeatureTile(val icon: ImageVector, val label: Int, val route: Any)

@Composable
private fun AllFeaturesScreen(nav: NavController) {
    val tiles = listOf(
        FeatureTile(Icons.Outlined.TaskAlt, UiR.string.feature_tasks, TasksRoute),
        FeatureTile(Icons.Outlined.ViewTimeline, UiR.string.feature_routines, RoutinesRoute),
        FeatureTile(Icons.Outlined.Alarm, UiR.string.feature_reminders, RemindersRoute),
        FeatureTile(Icons.Outlined.WaterDrop, UiR.string.feature_water, WaterRoute),
        FeatureTile(Icons.Outlined.Medication, UiR.string.feature_medicine, MedicineRoute),
        FeatureTile(Icons.Outlined.Restaurant, UiR.string.feature_diet, DietRoute),
        FeatureTile(Icons.Outlined.Bedtime, UiR.string.feature_sleep, SleepRoute),
        FeatureTile(Icons.Outlined.FitnessCenter, UiR.string.feature_fitness, FitnessRoute),
        FeatureTile(Icons.Outlined.Mosque, UiR.string.feature_ibadah, IbadahRoute),
        FeatureTile(Icons.Outlined.Repeat, UiR.string.feature_habits, HabitsRoute),
        FeatureTile(Icons.Outlined.School, UiR.string.feature_study, StudyRoute),
        FeatureTile(Icons.Outlined.Payments, UiR.string.feature_finance, FinanceRoute),
        FeatureTile(Icons.Outlined.Cake, UiR.string.feature_events, EventsRoute),
        FeatureTile(Icons.Outlined.StickyNote2, UiR.string.feature_notes, NotesRoute),
        FeatureTile(Icons.Outlined.ViewAgenda, UiR.string.feature_templates, TemplatesRoute),
        FeatureTile(Icons.Outlined.Search, UiR.string.feature_search, SearchRoute),
        FeatureTile(Icons.Outlined.Backup, UiR.string.feature_backup, BackupRoute),
    )
    com.hayat.core.ui.HayatScreen(title = stringResource(UiR.string.feature_all), onBack = nav::popBackStack) { pad ->
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.padding(pad), contentPadding = PaddingValues(12.dp)) {
            items(tiles) { t ->
                Column(Modifier.heightIn(min = 96.dp).clickable(role = Role.Button) { nav.navigate(t.route) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(t.icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
