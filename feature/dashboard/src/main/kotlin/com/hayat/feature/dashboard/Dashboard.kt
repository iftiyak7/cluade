package com.hayat.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.hayat.core.designsystem.component.AnimatedCount
import com.hayat.core.designsystem.component.BadgeIconButton
import com.hayat.core.designsystem.component.DayChip
import com.hayat.core.designsystem.component.DayStrip
import com.hayat.core.designsystem.component.GradientCta
import com.hayat.core.designsystem.component.InitialAvatar
import com.hayat.core.designsystem.component.InkCard
import com.hayat.core.designsystem.component.InkStat
import com.hayat.core.designsystem.component.InkTag
import com.hayat.core.designsystem.component.SegmentedProgress
import com.hayat.core.designsystem.component.percentOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import com.hayat.core.designsystem.component.PastelStat
import com.hayat.core.designsystem.component.PillTabs
import com.hayat.core.designsystem.component.TimelineEntry
import com.hayat.core.designsystem.component.animateEnter
import com.hayat.core.designsystem.component.pressScale
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.domain.routine.RoutineActionsUseCase
import com.hayat.core.domain.task.CompleteTaskUseCase
import com.hayat.core.domain.announcement.AnnouncementPolicy
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.model.Announcement
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
    val banners: List<Announcement> = emptyList(),
    val unreadAnnouncements: Int = 0,
    val bengali: Boolean = false,
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
    private val completeTask: CompleteTaskUseCase,
    private val routineActions: RoutineActionsUseCase,
    private val announcements: AnnouncementRepository,
    private val appConfig: AppConfig,
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

    private val base = combine(planning, remindersFlow, health, lifestyle) { (p, t, tl), (up, missed), (w, doses), l ->
        DashboardState(
            loading = false, prefs = p, today = today, hour = time.zonedNow().hour,
            todayTasks = t, timeline = tl, upcoming = up.take(3), missedToday = missed, water = w, doses = doses,
            nextPrayer = if (p.islamicModuleEnabled) PrayerTimeCalculator.next(time.now(), p.prayer) else null,
            prayersLogged = l[0], zikrToday = l[1], habitsScheduled = l[2], habitsOnTrack = l[3], mealsPlanned = l[4], mealsLogged = l[5],
        )
    }

    val state = combine(base, announcements.observeAll()) { s, all ->
        if (!s.prefs.announcementsEnabled) return@combine s
        val bengali = AnnouncementPolicy.isBengali(s.prefs.language, appConfig.systemLanguage)
        val visible = AnnouncementPolicy.visible(all, time.now(), bengali, appConfig.versionCode)
        s.copy(
            banners = visible.filter { it.showsBanner && !it.dismissed }.take(2),
            unreadAnnouncements = visible.count { !it.seen },
            bengali = bengali,
        )
    }.stateInVm(DashboardState())

    fun dismissAnnouncement(id: Long) = safeLaunch { announcements.dismiss(id) }
    fun markAnnouncementSeen(id: Long) = safeLaunch { announcements.markSeen(listOf(id)) }

    fun addWater() = safeLaunch { waterUseCases.add(state.value.prefs.water.defaultGlassMl).handle() }
    fun takeDose(d: MedicineDose) = safeLaunch { medicineUseCases.setDoseStatus(d, DoseStatus.TAKEN).handle() }
    fun toggleTask(t: Task) = safeLaunch { completeTask(t.id, !t.isCompleted).handle() }
    fun toggleActivity(i: TimelineItem) = safeLaunch {
        routineActions.log(i.activity.id, i.date, if (i.log?.status == ActivityStatus.COMPLETED) null else ActivityStatus.COMPLETED).handle()
    }
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

private enum class DayPart(val label: Int) { MORNING(R.string.db_tab_morning), AFTERNOON(R.string.db_tab_afternoon), EVENING(R.string.db_tab_evening), ANYTIME(R.string.db_tab_anytime) }

private fun partOf(t: java.time.LocalTime?): DayPart = when {
    t == null -> DayPart.ANYTIME
    t.hour < 12 -> DayPart.MORNING
    t.hour < 17 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

fun NavGraphBuilder.dashboardGraph(nav: NavController, onQuickAdd: () -> Unit = {}) {
    composable<HomeRoute> { DashboardScreen(nav, onQuickAdd) }
    composable<AnnouncementsRoute> { AnnouncementsScreen(nav) }
    composable<AllFeaturesRoute> { AllFeaturesScreen(nav) }
}

@Composable
private fun DashboardScreen(nav: NavController, onQuickAdd: () -> Unit, vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Scaffold(snackbarHost = { SnackbarHost(host) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val visible = s.prefs.dashboardCards.filter { it.visible && (s.prefs.islamicModuleEnabled || it.card !in setOf(DashboardCard.PRAYER, DashboardCard.ZIKR)) }.map { it.card }
        val heroCards = setOf(DashboardCard.TODAY_PROGRESS, DashboardCard.TIMELINE, DashboardCard.WATER)
        var part by rememberSaveable { mutableIntStateOf(partOf(java.time.LocalTime.of(s.hour, 0)).ordinal) }
        val entries = remember(s.timeline, s.todayTasks) {
            (s.timeline.map { PlanEntry.Activity(it) } + s.todayTasks.map { PlanEntry.TaskEntry(it) }).sortedWith(compareBy(nullsLast()) { it.time })
        }
        val shown = entries.filter { partOf(it.time).ordinal == part }
        var index = 0
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("header") {
                Row(Modifier.animateEnter(index++), verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(s.prefs.userName, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.db_brand), style = MaterialTheme.typography.titleLarge)
                        Text(fmt.date(s.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
                    Spacer(Modifier.width(8.dp))
                    BadgeIconButton(Icons.Outlined.Tune, stringResource(R.string.db_customize), showDot = false, onClick = { nav.navigate(DashboardSettingsRoute) })
                }
            }
            item("hero") {
                val hijri = if (s.prefs.islamicModuleEnabled) HijriCalendar.fromGregorian(s.today, s.prefs.hijriOffsetDays) else null
                val frac = if (s.totalCount == 0) 0f else s.doneCount.toFloat() / s.totalCount
                val progressText = if (s.totalCount == 0) stringResource(R.string.db_progress_empty) else stringResource(R.string.db_progress_text, fmt.number(s.doneCount), fmt.number(s.totalCount))
                Column(Modifier.animateEnter(index++), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InkCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                val greet = stringResource(when (s.hour) { in 5..11 -> R.string.db_morning; in 12..16 -> R.string.db_afternoon; in 17..20 -> R.string.db_evening; else -> R.string.db_night })
                                Text(greet + ",", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light))
                                Text(s.prefs.userName.ifBlank { stringResource(R.string.db_friend) }, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            hijri?.let { InkTag(fmt.digits("${it.day}/${it.month}/${it.year}")) } ?: InkTag(fmt.weekdayShort(s.today.dayOfWeek).uppercase())
                        }
                    }
                    if (DashboardCard.TODAY_PROGRESS in visible) InkCard(onClick = { nav.navigate(ProgressRoute) }) {
                        Text(stringResource(R.string.db_progress), style = MaterialTheme.typography.titleMedium)
                        Text(progressText, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedCount(percentOf(frac), MaterialTheme.typography.displayMedium, format = { fmt.number(it) + "%" })
                            Spacer(Modifier.width(14.dp))
                            SegmentedProgress(frac, description = progressText, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            s.water?.let { InkStat(stringResource(R.string.db_water), fmt.percent(it.progress), Modifier.weight(1f)) }
                            if (s.habitsScheduled > 0) InkStat(stringResource(R.string.db_habits), fmt.digits("${s.habitsOnTrack}/${s.habitsScheduled}"), Modifier.weight(1f))
                            if (s.prefs.islamicModuleEnabled) InkStat(stringResource(R.string.db_prayer), fmt.digits("${s.prayersLogged}/5"), Modifier.weight(1f))
                            else if (s.doses.isNotEmpty()) InkStat(stringResource(R.string.db_medicine), fmt.digits("${s.doses.count { it.log != null }}/${s.doses.size}"), Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    GradientCta(stringResource(R.string.db_quick_add), Icons.Outlined.AutoAwesome, onQuickAdd, trailing = Icons.Outlined.ChevronRight)
                }
            }
            items(s.banners, key = { "ann" + it.id }) { a ->
                AnnouncementBanner(
                    a, s.bengali, Modifier.animateItem(),
                    onCta = { vm.markAnnouncementSeen(a.id); openAnnouncementLink(a.ctaUrl, nav, uriHandler) },
                    onDismiss = { vm.dismissAnnouncement(a.id) },
                )
            }
            item("week") {
                val days = (-3..3).map { s.today.plusDays(it.toLong()) }
                DayStrip(
                    days.map { d -> DayChip(d.toString(), fmt.weekdayShort(d.dayOfWeek), fmt.number(d.dayOfMonth), d == s.today, d == s.today && s.totalCount > 0 && s.doneCount == s.totalCount, false) },
                    onSelect = { nav.navigate(CalendarRoute(it)) }, modifier = Modifier.animateEnter(index++),
                    doneLabel = stringResource(R.string.db_all_done), todayLabel = stringResource(R.string.db_today),
                )
            }
            if (DashboardCard.WATER in visible || DashboardCard.PRAYER in visible || DashboardCard.MEDICINE in visible) item("tiles") {
                Row(Modifier.animateEnter(index++).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val w = s.water
                    if (DashboardCard.WATER in visible && w != null) PastelStat(
                        value = { AnimatedCount(w.totalMl, MaterialTheme.typography.displaySmall, format = { fmt.number(it) + " ml" }) },
                        label = stringResource(R.string.db_water_add, fmt.number(s.prefs.water.defaultGlassMl)),
                        tone = ext.sky, icon = Icons.Outlined.WaterDrop, onClick = vm::addWater, modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    val np = s.nextPrayer
                    if (DashboardCard.PRAYER in visible && np != null) PastelStat(
                        value = { Text(fmt.time(np.second.toLocalTime()), style = MaterialTheme.typography.displaySmall, maxLines = 1) },
                        label = stringResource(R.string.db_next_prayer, stringResource(np.first.labelRes())),
                        tone = ext.amber, icon = Icons.Outlined.Mosque, onClick = { nav.navigate(IbadahRoute) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) else if (DashboardCard.MEDICINE in visible) {
                        val pending = s.doses.firstOrNull { it.log == null }
                        PastelStat(
                            value = { Text(pending?.let { fmt.time(it.schedule.time) } ?: fmt.digits("${s.doses.count { it.log != null }}/${s.doses.size}"), style = MaterialTheme.typography.headlineMedium) },
                            label = pending?.medicine?.name ?: stringResource(if (s.doses.isEmpty()) R.string.db_med_none else R.string.db_med_done),
                            tone = ext.pink, icon = Icons.Outlined.Medication, onClick = { nav.navigate(MedicineRoute) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            if (DashboardCard.TIMELINE in visible) {
                item("plan-header") {
                    Column(Modifier.animateEnter(index++), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(stringResource(R.string.db_plan_today), action = { TextButton(onClick = { nav.navigate(CalendarRoute()) }) { Text(stringResource(R.string.db_see_all)) } })
                        PillTabs(DayPart.entries.map { stringResource(it.label) }, part, { part = it })
                    }
                }
                if (shown.isEmpty()) item("plan-empty") {
                    Text(stringResource(R.string.db_tab_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                }
                itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                    TimelineEntry(
                        done = e.done, isLast = i == shown.lastIndex,
                        onToggle = { when (e) { is PlanEntry.Activity -> vm.toggleActivity(e.item); is PlanEntry.TaskEntry -> vm.toggleTask(e.task) } },
                        toggleLabel = stringResource(R.string.db_mark_done), modifier = Modifier.animateItem().animateEnter(i),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (e.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                    color = if (e.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clickable { when (e) { is PlanEntry.Activity -> nav.navigate(RoutinesRoute); is PlanEntry.TaskEntry -> nav.navigate(TaskDetailRoute(e.task.id)) } },
                                )
                                Text(
                                    stringResource(when (e) { is PlanEntry.Activity -> UiR.string.feature_routines; is PlanEntry.TaskEntry -> UiR.string.feature_tasks }),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            e.time?.let { Text(fmt.time(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            items(visible.filter { it !in heroCards }, key = { it.name }) { card ->
                Box(Modifier.animateEnter(index)) { DashboardCardView(card, s, nav, vm) }
            }
            item("all") {
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier.fillMaxWidth().pressScale(interaction).clip(RoundedCornerShape(50)).background(ext.lavender.first)
                        .clickable(interaction, ripple(), role = Role.Button) { nav.navigate(AllFeaturesRoute) }.padding(16.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { Icon(Icons.Outlined.Apps, null, tint = ext.lavender.second); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.db_all_features), color = ext.lavender.second, style = MaterialTheme.typography.labelLarge) }
            }
            if (s.totalCount == 0 && s.upcoming.isEmpty()) item("welcome") { Text(stringResource(R.string.db_welcome_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
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
                val text = fmt.digits("${w.totalMl} / ${w.goalMl} ml")
                LinearMeter(w.progress, description = text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f))
                    FilledTonalButton(onClick = vm::addWater) { Text(stringResource(R.string.db_water_add, fmt.number(s.prefs.water.defaultGlassMl))) }
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
