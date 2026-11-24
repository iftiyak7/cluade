package com.hayat.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.*
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.domain.announcement.AnnouncementPolicy
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.domain.health.MedicinePlanner
import com.hayat.core.domain.health.MedicineUseCases
import com.hayat.core.domain.health.WaterCalculator
import com.hayat.core.domain.health.WaterUseCases
import com.hayat.core.domain.productivity.HabitUseCases
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
import com.hayat.core.domain.routine.RoutineActionsUseCase
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.domain.sync.ServerStatus
import com.hayat.core.domain.sync.SyncStatusProvider
import com.hayat.core.domain.task.CompleteTaskUseCase
import com.hayat.core.domain.task.TaskQuery
import com.hayat.core.domain.worship.PrayerTimeCalculator
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.Announcement
import com.hayat.core.model.DashboardCard
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.Habit
import com.hayat.core.model.HabitKind
import com.hayat.core.model.MealStatus
import com.hayat.core.model.MedicineDose
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.PrayerTimes
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
import com.hayat.core.time.DayPart
import com.hayat.core.time.HijriCalendar
import com.hayat.core.time.instantRange
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class HabitRow(val habit: Habit, val count: Int) {
    val done: Boolean get() = habit.kind == HabitKind.BUILD && habit.isSuccess(count)
}

data class PrayerCardState(
    /** Last prayer whose time has started today (can be marked as prayed). */
    val current: Prayer?,
    val currentStatus: PrayerStatus?,
    val next: Prayer,
    val nextAt: ZonedDateTime,
)

data class DashboardState(
    val loading: Boolean = true,
    val prefs: UserPreferences = UserPreferences(),
    val today: LocalDate = LocalDate.now(),
    val now: Instant = Instant.now(),
    val hour: Int = 9,
    val todayTasks: List<Task> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val upcoming: List<Pair<Reminder, ReminderOccurrence>> = emptyList(),
    val missedToday: Int = 0,
    val water: WaterDay? = null,
    val doses: List<MedicineDose> = emptyList(),
    val nextPrayer: Pair<Prayer, ZonedDateTime>? = null,
    val prayer: PrayerCardState? = null,
    val prayersLogged: Int = 0,
    val zikrToday: Int = 0,
    val habits: List<HabitRow> = emptyList(),
    val mealsPlanned: Int = 0,
    val mealsLogged: Int = 0,
    val banners: List<Announcement> = emptyList(),
    val unreadAnnouncements: Int = 0,
    val bengali: Boolean = false,
    val server: ServerStatus = ServerStatus.NOT_CONNECTED,
) {
    val doneCount: Int get() = todayTasks.count { it.isCompleted } + timeline.count { it.log?.status == ActivityStatus.COMPLETED }
    val totalCount: Int get() = todayTasks.size + timeline.size
    val habitsScheduled: Int get() = habits.size
    val habitsOnTrack: Int get() = habits.count { it.habit.isSuccess(it.count) }
    val dayPart: DayPart get() = DayPart.of(hour)
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
    private val ibadah: IbadahRepository,
    habits: HabitRepository,
    meals: MealRepository,
    private val waterUseCases: WaterUseCases,
    private val medicineUseCases: MedicineUseCases,
    private val habitUseCases: HabitUseCases,
    private val completeTask: CompleteTaskUseCase,
    private val routineActions: RoutineActionsUseCase,
    private val announcements: AnnouncementRepository,
    private val appConfig: AppConfig,
    private val sync: SyncStatusProvider,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val today = time.today()
    private val zone = time.zone()
    private val dayRange = today.instantRange(zone)

    /** Ticks every 30 s so the greeting (device time zone) and the prayer countdown stay current. */
    private val clock = flow { while (true) { emit(time.now()); delay(30_000) } }

    // Prayer times are calculated once per day and settings, then reused (no recomputation per tick).
    private var prayerCache: Triple<LocalDate, Any?, PrayerTimes?>? = null
    private fun prayerTimes(p: UserPreferences): PrayerTimes? {
        val c = prayerCache
        if (c != null && c.first == today && c.second == p.prayer) return c.third
        return PrayerTimeCalculator.compute(today, p.prayer).also { prayerCache = Triple(today, p.prayer, it) }
    }

    private val planning = combine(tasks.observeTasks(), routines.observeRoutines(), routines.observeLogs(today, today), prefs.preferences) { t, r, l, p ->
        val open = TaskQuery.apply(t, TaskFilter.TODAY, today, includeOverdueInToday = p.showOverdueInToday)
        val doneToday = t.filter { it.isCompleted && it.completedAt?.atZone(zone)?.toLocalDate() == today && !it.isArchived }
        Triple(p, (open + doneToday).distinctBy { it.id }, RoutinePlanner.timeline(r, l, today))
    }
    private val remindersFlow = combine(reminders.observeReminders(), occurrences.observeUpcoming(time.now(), 10), occurrences.observeBetween(dayRange.first, dayRange.second)) { rem, up, todayOcc ->
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
        val counts = hc.associate { it.habitId to it.count }
        val rows = h.filter { !it.isArchived && !it.isPaused && today.dayOfWeek in it.weekdays }.map { HabitRow(it, counts[it.id] ?: 0) }
        val planned = mp.filter { !it.isArchived && today.dayOfWeek in it.weekdays }
        Lifestyle(pl.associate { it.prayer to it.status }, zp.sumOf { it.count }, rows, planned.size, ml.count { it.mealPlanId != null && it.status == MealStatus.EATEN })
    }
    private data class Lifestyle(val prayerLogs: Map<Prayer, PrayerStatus>, val zikr: Int, val habits: List<HabitRow>, val mealsPlanned: Int, val mealsLogged: Int)

    private val base = combine(planning, remindersFlow, health, lifestyle, clock) { (p, t, tl), (up, missed), (w, doses), l, now ->
        val times = if (p.islamicModuleEnabled) prayerTimes(p) else null
        val next = if (p.islamicModuleEnabled) PrayerTimeCalculator.next(now, p.prayer) else null
        val current = times?.let { pt -> Prayer.entries.lastOrNull { !pt.timeOf(it).toInstant().isAfter(now) } }
        DashboardState(
            loading = false, prefs = p, today = today, now = now, hour = now.atZone(time.zone()).hour,
            todayTasks = t, timeline = tl, upcoming = up.take(3), missedToday = missed, water = w, doses = doses,
            nextPrayer = next,
            prayer = next?.let { (np, at) -> PrayerCardState(current, current?.let { l.prayerLogs[it] }, np, at) },
            prayersLogged = l.prayerLogs.count { it.value == PrayerStatus.PRAYED || it.value == PrayerStatus.PRAYED_LATE },
            zikrToday = l.zikr, habits = l.habits, mealsPlanned = l.mealsPlanned, mealsLogged = l.mealsLogged,
        )
    }

    val state = combine(base, announcements.observeAll(), sync.status) { s, all, server ->
        val withServer = s.copy(server = server)
        if (!s.prefs.announcementsEnabled) return@combine withServer
        val bengali = AnnouncementPolicy.isBengali(s.prefs.language, appConfig.systemLanguage)
        val visible = AnnouncementPolicy.visible(all, s.now, bengali, appConfig.versionCode)
        withServer.copy(banners = visible.filter { it.showsBanner && !it.dismissed }.take(2), unreadAnnouncements = visible.count { !it.seen }, bengali = bengali)
    }.stateInVm(DashboardState())

    // Every action below updates Room immediately; flows re-emit, so the UI changes on the same frame
    // (optimistic by construction). Server sync, when connected, follows in the background.
    fun addWater(ml: Int = state.value.prefs.water.defaultGlassMl) = safeLaunch { waterUseCases.add(ml).handle() }
    fun takeDose(d: MedicineDose) = safeLaunch { medicineUseCases.setDoseStatus(d, DoseStatus.TAKEN).handle() }
    fun toggleTask(t: Task) = safeLaunch { completeTask(t.id, !t.isCompleted).handle() }
    fun toggleActivity(i: TimelineItem) = safeLaunch {
        routineActions.log(i.activity.id, i.date, if (i.log?.status == ActivityStatus.COMPLETED) null else ActivityStatus.COMPLETED).handle()
    }
    fun markPrayed(p: Prayer) = safeLaunch { ibadah.setPrayerStatus(today, p, PrayerStatus.PRAYED, time.now()) }
    fun habitTap(r: HabitRow) = safeLaunch {
        val target = r.habit.targetPerDay.coerceAtLeast(1)
        val delta = if (r.habit.kind == HabitKind.BUILD && r.count >= target) -r.count else 1
        habitUseCases.increment(r.habit.id, today, delta).handle()
    }
    fun habitComplete(r: HabitRow) = safeLaunch {
        val target = r.habit.targetPerDay.coerceAtLeast(1)
        if (r.count < target) habitUseCases.increment(r.habit.id, today, target - r.count).handle()
    }
    fun habitReset(r: HabitRow) = safeLaunch { if (r.count > 0) habitUseCases.increment(r.habit.id, today, -r.count).handle() }
    fun dismissAnnouncement(id: Long) = safeLaunch { announcements.dismiss(id) }
    fun markAnnouncementSeen(id: Long) = safeLaunch { announcements.markSeen(listOf(id)) }
    fun refresh() = sync.requestSync()
}

/** A single row in Home's "Today's plan" timeline (routine activity or task). */
private sealed interface PlanEntry {
    val time: java.time.LocalTime?
    val title: String
    val done: Boolean
    val key: String
    data class Activity(val item: TimelineItem) : PlanEntry {
        override val time get() = item.activity.startTime
        override val title get() = item.activity.title
        override val done get() = item.log?.status == ActivityStatus.COMPLETED
        override val key get() = "a" + item.activity.id
    }
    data class TaskEntry(val task: Task) : PlanEntry {
        override val time get() = task.dueTime
        override val title get() = task.title
        override val done get() = task.isCompleted
        override val key get() = "t" + task.id
    }
}

private enum class PlanPart(val label: Int) { MORNING(R.string.db_tab_morning), AFTERNOON(R.string.db_tab_afternoon), EVENING(R.string.db_tab_evening), ANYTIME(R.string.db_tab_anytime) }

private fun partOf(t: java.time.LocalTime?): PlanPart = when {
    t == null -> PlanPart.ANYTIME
    t.hour < 12 -> PlanPart.MORNING
    t.hour < 17 -> PlanPart.AFTERNOON
    else -> PlanPart.EVENING
}

fun NavGraphBuilder.dashboardGraph(nav: NavController) {
    composable<HomeRoute> { DashboardScreen(nav) }
    composable<AnnouncementsRoute> { AnnouncementsScreen(nav) }
    composable<AllFeaturesRoute> { AllFeaturesScreen(nav) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(nav: NavController, vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var customWater by rememberSaveable { mutableStateOf(false) }
    Scaffold(snackbarHost = { SnackbarHost(host) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        if (s.loading) { SkeletonPage(Modifier.padding(pad)); return@Scaffold }
        val visible = s.prefs.dashboardCards.filter { it.visible && (s.prefs.islamicModuleEnabled || it.card !in setOf(DashboardCard.PRAYER, DashboardCard.ZIKR)) }.map { it.card }
        val heroCards = setOf(DashboardCard.TODAY_PROGRESS, DashboardCard.TIMELINE, DashboardCard.WATER, DashboardCard.PRAYER, DashboardCard.HABITS)
        var part by rememberSaveable { mutableIntStateOf(partOf(LocalTime.of(s.hour, 0)).ordinal) }
        val entries = remember(s.timeline, s.todayTasks) {
            (s.timeline.map { PlanEntry.Activity(it) } + s.todayTasks.map { PlanEntry.TaskEntry(it) }).sortedWith(compareBy(nullsLast()) { it.time })
        }
        val shown = entries.filter { partOf(it.time).ordinal == part }
        var index = 0
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; vm.refresh(); scope.launch { delay(700); refreshing = false } },
            modifier = Modifier.padding(pad),
        ) {
        LazyColumn(Modifier.fillMaxSize().testTag("home-screen"), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("header") { HomeHeader(s, nav, Modifier.animateEnter(index++)) }
            items(s.banners, key = { "ann" + it.id }) { a ->
                AnnouncementBanner(
                    a, s.bengali, Modifier.animateItem(),
                    onCta = { vm.markAnnouncementSeen(a.id); openAnnouncementLink(a.ctaUrl, nav, uriHandler) },
                    onDismiss = { vm.dismissAnnouncement(a.id) },
                )
            }
            if (DashboardCard.TODAY_PROGRESS in visible) item("hero") { HeroCard(s, nav, Modifier.animateEnter(index++)) }
            item("week") {
                HayatCard(Modifier.animateEnter(index++), contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
                    WeekStrip(
                        today = s.today, selected = null, firstDayOfWeek = s.prefs.firstDayOfWeek,
                        onSelect = { nav.navigate(CalendarRoute(it.toString())) },
                        monthLabel = { fmt.monthYear(java.time.YearMonth.from(it)) },
                        weekdayLabel = { fmt.weekdayShort(it) }, dayLabel = { fmt.number(it.dayOfMonth) },
                        previousLabel = stringResource(R.string.db_week_prev), nextLabel = stringResource(R.string.db_week_next),
                        todayLabel = stringResource(R.string.db_today),
                        marked = if (s.totalCount > 0 && s.doneCount == s.totalCount) setOf(s.today) else emptySet(),
                    )
                }
            }
            if (s.prefs.islamicModuleEnabled && DashboardCard.PRAYER in visible) item("prayer") { PrayerCard(s, nav, vm, Modifier.animateEnter(index++)) }
            if (DashboardCard.WATER in visible) s.water?.let { w -> item("water") { WaterCard(s, w, vm, onCustom = { customWater = true }, onOpen = { nav.navigate(WaterRoute) }, modifier = Modifier.animateEnter(index++)) } }
            if (DashboardCard.HABITS in visible) {
                item("habits-h") {
                    SectionHeader(stringResource(R.string.db_habits_today), Modifier.animateEnter(index++), action = { TextButton(onClick = { nav.navigate(HabitsRoute) }) { Text(stringResource(R.string.db_see_all)) } })
                }
                if (s.habits.isEmpty()) item("habits-empty") {
                    HayatCard {
                        InlineEmpty(Icons.Outlined.Repeat, stringResource(R.string.db_habits_empty_title), stringResource(R.string.db_habits_empty_text)) {
                            HayatButton(stringResource(R.string.db_start), { nav.navigate(HabitEditRoute()) }, variant = ButtonVariant.TONAL, height = 44.dp)
                        }
                    }
                }
                items(s.habits, key = { "h" + it.habit.id }) { r -> HabitItem(r, vm, Modifier.animateItem()) }
            }
            if (DashboardCard.TIMELINE in visible) {
                item("plan-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(stringResource(R.string.db_plan_today), action = { TextButton(onClick = { nav.navigate(CalendarRoute()) }) { Text(stringResource(R.string.db_see_all)) } })
                        PillTabs(PlanPart.entries.map { stringResource(it.label) }, part, { part = it })
                    }
                }
                if (shown.isEmpty()) item("plan-empty") {
                    Text(stringResource(R.string.db_tab_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                }
                itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                    TimelineEntry(
                        done = e.done, isLast = i == shown.lastIndex,
                        onToggle = { when (e) { is PlanEntry.Activity -> vm.toggleActivity(e.item); is PlanEntry.TaskEntry -> vm.toggleTask(e.task) } },
                        toggleLabel = stringResource(R.string.db_mark_done), modifier = Modifier.animateItem(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { when (e) { is PlanEntry.Activity -> nav.navigate(RoutinesRoute); is PlanEntry.TaskEntry -> nav.navigate(TaskDetailRoute(e.task.id)) } }) {
                                Text(
                                    e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (e.done) TextDecoration.LineThrough else null,
                                    color = if (e.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(stringResource(when (e) { is PlanEntry.Activity -> UiR.string.feature_routines; is PlanEntry.TaskEntry -> UiR.string.feature_tasks }),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            e.time?.let { Text(fmt.time(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            items(visible.filter { it !in heroCards }, key = { it.name }) { card -> DashboardCardView(card, s, nav, vm) }
            item("all") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HayatButton(stringResource(R.string.db_all_features), { nav.navigate(AllFeaturesRoute) }, Modifier.weight(1f), variant = ButtonVariant.TONAL, icon = Icons.Outlined.Apps)
                    HayatButton(stringResource(R.string.db_customize), { nav.navigate(DashboardSettingsRoute) }, Modifier.weight(1f), variant = ButtonVariant.TEXT, icon = Icons.Outlined.Tune)
                }
            }
            if (s.totalCount == 0 && s.upcoming.isEmpty() && s.habits.isEmpty()) item("welcome") {
                Text(stringResource(R.string.db_welcome_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        }
    }
    if (customWater) CustomWaterDialog(onDismiss = { customWater = false }, onAdd = { vm.addWater(it); customWater = false })
}

@Composable
private fun HomeHeader(s: DashboardState, nav: NavController, modifier: Modifier = Modifier) {
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val greeting = stringResource(when (s.dayPart) { DayPart.MORNING -> R.string.db_morning; DayPart.AFTERNOON -> R.string.db_afternoon; DayPart.EVENING -> R.string.db_evening; DayPart.NIGHT -> R.string.db_night })
    val name = s.prefs.userName.trim()
    val (dotColor, dotText) = when (s.server) {
        ServerStatus.SYNCED -> ext.success to stringResource(R.string.db_server_synced)
        ServerStatus.PENDING, ServerStatus.SYNCING -> ext.gold to stringResource(R.string.db_server_pending)
        ServerStatus.OFFLINE, ServerStatus.ERROR -> MaterialTheme.colorScheme.error to stringResource(R.string.db_server_offline)
        ServerStatus.NOT_CONNECTED -> MaterialTheme.colorScheme.outline to stringResource(R.string.db_server_none)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            InitialAvatar(name, size = 44.dp)
            StatusDot(dotColor, dotText, Modifier.align(Alignment.BottomEnd).clickable(role = Role.Button) { nav.navigate(ServerSettingsRoute) })
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (name.isBlank()) greeting else stringResource(R.string.db_greeting, greeting, name), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(fmt.dayMonth(s.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (s.prefs.islamicModuleEnabled) HijriCalendar.fromGregorian(s.today, s.prefs.hijriOffsetDays)?.let { h ->
                    Spacer(Modifier.width(6.dp))
                    Text(fmt.hijri(h.day, h.month, h.year), style = MaterialTheme.typography.labelSmall, color = ext.prayer.second, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ext.prayer.first).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        BadgeIconButton(Icons.Outlined.Search, stringResource(R.string.db_search), showDot = false, onClick = { nav.navigate(SearchRoute) })
        if (s.prefs.announcementsEnabled) {
            Spacer(Modifier.width(8.dp))
            BadgeIconButton(
                Icons.Outlined.Notifications,
                if (s.unreadAnnouncements > 0) stringResource(R.string.db_news_unread, s.unreadAnnouncements) else stringResource(R.string.db_news),
                showDot = s.unreadAnnouncements > 0, onClick = { nav.navigate(AnnouncementsRoute) },
            )
        }
    }
}

@Composable
private fun HeroCard(s: DashboardState, nav: NavController, modifier: Modifier = Modifier) {
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val frac = if (s.totalCount == 0) 0f else s.doneCount.toFloat() / s.totalCount
    val pct = percentOf(frac)
    val text = if (s.totalCount == 0) stringResource(R.string.db_progress_empty) else stringResource(R.string.db_progress_text, fmt.number(s.doneCount), fmt.number(s.totalCount))
    HayatCard(modifier, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(pct / 100f, size = 108.dp, stroke = 11.dp, description = stringResource(R.string.db_progress) + " " + fmt.number(pct) + "%",
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedCount(pct, MaterialTheme.typography.headlineMedium, format = { fmt.number(it) + "%" })
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.db_progress), style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                HayatButton(stringResource(R.string.db_open_plan), { nav.navigate(CalendarRoute()) }, variant = ButtonVariant.TONAL, height = 44.dp, icon = Icons.Outlined.ViewTimeline)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.water?.let { MiniStat(Icons.Outlined.WaterDrop, stringResource(R.string.db_water), fmt.ml(it.totalMl), ext.water, { nav.navigate(WaterRoute) }, Modifier.weight(1f)) }
            MiniStat(Icons.Outlined.Repeat, stringResource(R.string.db_habits), fmt.number(s.habitsOnTrack) + "/" + fmt.number(s.habitsScheduled), ext.habits, { nav.navigate(HabitsRoute) }, Modifier.weight(1f))
            if (s.prefs.islamicModuleEnabled) MiniStat(Icons.Outlined.Mosque, stringResource(R.string.db_prayer), fmt.number(s.prayersLogged) + "/" + fmt.number(5), ext.prayer, { nav.navigate(IbadahRoute) }, Modifier.weight(1f))
            else MiniStat(Icons.Outlined.Medication, stringResource(R.string.db_medicine), fmt.number(s.doses.count { it.log != null }) + "/" + fmt.number(s.doses.size), ext.pink, { nav.navigate(MedicineRoute) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PrayerCard(s: DashboardState, nav: NavController, vm: DashboardViewModel, modifier: Modifier = Modifier) {
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val p = s.prayer
    HayatCard(modifier, onClick = { nav.navigate(IbadahRoute) }) {
        if (p == null) {
            InlineEmpty(Icons.Outlined.Mosque, stringResource(R.string.db_prayer), stringResource(R.string.db_prayer_setup)) {
                HayatButton(stringResource(R.string.db_set_city), { nav.navigate(IbadahRoute) }, variant = ButtonVariant.TONAL, height = 44.dp)
            }
            return@HayatCard
        }
        CardTitle(Icons.Outlined.Mosque, stringResource(R.string.db_next_prayer_title), ext.prayer)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(p.next.labelRes()), style = MaterialTheme.typography.headlineSmall)
                Text(fmt.time(p.nextAt.toLocalTime()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val mins = Duration.between(s.now, p.nextAt.toInstant()).toMinutes().coerceAtLeast(0)
            Text(fmt.countdown(mins), style = MaterialTheme.typography.titleMedium, color = ext.prayer.second,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(ext.prayer.first).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        p.current?.let { cur ->
            Spacer(Modifier.height(12.dp))
            val prayed = p.currentStatus == PrayerStatus.PRAYED || p.currentStatus == PrayerStatus.PRAYED_LATE
            HayatButton(
                if (prayed) stringResource(R.string.db_prayed, stringResource(cur.labelRes())) else stringResource(R.string.db_mark_prayed, stringResource(cur.labelRes())),
                { vm.markPrayed(cur) }, Modifier.fillMaxWidth(), variant = if (prayed) ButtonVariant.TONAL else ButtonVariant.PRIMARY,
                icon = if (prayed) Icons.Outlined.CheckCircle else Icons.Outlined.Done, enabled = !prayed,
            )
        }
    }
}

@Composable
private fun WaterCard(s: DashboardState, w: WaterDay, vm: DashboardViewModel, onCustom: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val glass = s.prefs.water.defaultGlassMl
    val pct = percentOf(w.progress)
    HayatCard(modifier, onClick = onOpen) {
        CardTitle(Icons.Outlined.WaterDrop, stringResource(R.string.db_water), ext.water, trailing = {
            Text(fmt.number(pct) + "%", style = MaterialTheme.typography.titleMedium, color = ext.water.second)
        })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedCount(w.totalMl, MaterialTheme.typography.displaySmall, format = { fmt.number(it) })
            Text(" / " + fmt.ml(w.goalMl), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        LinearMeter(pct / 100f, description = stringResource(R.string.db_water) + " " + fmt.number(pct) + "%", height = 12.dp, color = ext.water.second, trackColor = ext.water.first)
        Spacer(Modifier.height(14.dp))
        HayatButton(
            "+" + fmt.ml(glass), { vm.addWater(glass) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.WaterDrop,
            onLongClick = onCustom, onLongClickLabel = stringResource(R.string.db_water_custom),
        )
        Text(stringResource(R.string.db_water_long_press), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun CustomWaterDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()
    val valid = value != null && value in 10..3000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.db_water_custom)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { v -> text = v.filter(Char::isDigit).take(4) }, singleLine = true,
                label = { Text(stringResource(R.string.db_water_amount)) },
                isError = text.isNotEmpty() && !valid,
                supportingText = { Text(stringResource(R.string.db_water_range)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onAdd) }, enabled = valid) { Text(stringResource(R.string.db_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitItem(r: HabitRow, vm: DashboardViewModel, modifier: Modifier = Modifier) {
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> { vm.habitComplete(r); state.snapTo(SwipeToDismissBoxValue.Settled) }
            SwipeToDismissBoxValue.EndToStart -> { vm.habitReset(r); state.snapTo(SwipeToDismissBoxValue.Settled) }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    SwipeToDismissBox(
        state = state, modifier = modifier,
        backgroundContent = {
            val toDone = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(if (toDone) ext.success.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (toDone) Icons.Outlined.Done else Icons.Outlined.Undo, null, tint = if (toDone) ext.success else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (toDone) R.string.db_swipe_done else R.string.db_swipe_reset), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        HayatCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                val target = r.habit.targetPerDay.coerceAtLeast(1)
                if (r.habit.kind == HabitKind.BUILD && target == 1) {
                    Checkbox(checked = r.done, onCheckedChange = { vm.habitTap(r) })
                } else {
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { vm.habitTap(r) }, contentAlignment = Alignment.Center) {
                        ProgressRing(if (r.habit.kind == HabitKind.BUILD) r.count.toFloat() / target else 0f, size = 32.dp, stroke = 4.dp,
                            color = Color(r.habit.color), description = fmt.number(r.count) + "/" + fmt.number(target)) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.habit.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (r.done) TextDecoration.LineThrough else null)
                    Text(
                        if (r.habit.kind == HabitKind.BUILD) fmt.number(r.count) + "/" + fmt.number(target) else stringResource(R.string.db_habit_limit, fmt.number(r.count), fmt.number(target)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(Color(r.habit.color)))
            }
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, tone: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = HayatThemeExt.colors.mint) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tone.first), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tone.second, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
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
            CardHeader(Icons.Outlined.Flag, stringResource(R.string.db_priorities), HayatThemeExt.colors.pink)
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
            CardHeader(Icons.Outlined.Alarm, stringResource(R.string.db_upcoming), HayatThemeExt.colors.lavender)
            if (s.upcoming.isEmpty()) Text(stringResource(R.string.db_no_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.upcoming.forEach { (r, o) ->
                val t = LocalDateTime.ofInstant(o.effectiveAt, ZoneId.systemDefault())
                Text((if (t.toLocalDate() == s.today) fmt.time(t.toLocalTime()) else fmt.dayMonth(t.toLocalDate()) + " " + fmt.time(t.toLocalTime())) + " · " + reminderTitle(r), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DashboardCard.MISSED_REMINDERS -> HayatCard(onClick = { nav.navigate(RemindersRoute) }) {
            CardHeader(Icons.Outlined.NotificationImportant, stringResource(R.string.db_missed), HayatThemeExt.colors.amber)
            Text(if (s.missedToday == 0) stringResource(R.string.db_missed_none) else fmt.digits(stringResource(R.string.db_missed_text, s.missedToday)))
        }
        DashboardCard.WATER -> HayatCard(onClick = { nav.navigate(WaterRoute) }) {
            CardHeader(Icons.Outlined.WaterDrop, stringResource(R.string.db_water), HayatThemeExt.colors.sky)
            s.water?.let { w ->
                val text = fmt.number(w.totalMl) + " / " + fmt.ml(w.goalMl)
                LinearMeter(w.progress, description = text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f))
                    FilledTonalButton(onClick = { vm.addWater() }) { Text("+" + fmt.ml(s.prefs.water.defaultGlassMl)) }
                }
            }
        }
        DashboardCard.MEDICINE -> HayatCard(onClick = { nav.navigate(MedicineRoute) }) {
            CardHeader(Icons.Outlined.Medication, stringResource(R.string.db_medicine), HayatThemeExt.colors.pink)
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
            CardHeader(Icons.Outlined.Spa, stringResource(R.string.db_zikr), HayatThemeExt.colors.amber)
            Text(stringResource(R.string.db_zikr_text, fmt.number(s.zikrToday)))
        }
        DashboardCard.HABITS -> HayatCard(onClick = { nav.navigate(HabitsRoute) }) {
            CardHeader(Icons.Outlined.Repeat, stringResource(R.string.db_habits), HayatThemeExt.colors.lavender)
            if (s.habitsScheduled == 0) Text(stringResource(R.string.db_habits_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else { val t = fmt.digits(stringResource(R.string.db_habits_text, s.habitsOnTrack, s.habitsScheduled)); LinearMeter(s.habitsOnTrack.toFloat() / s.habitsScheduled, description = t); Text(t) }
        }
        DashboardCard.DIET -> HayatCard(onClick = { nav.navigate(DietRoute) }) {
            CardHeader(Icons.Outlined.Restaurant, stringResource(R.string.db_diet), HayatThemeExt.colors.amber)
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
