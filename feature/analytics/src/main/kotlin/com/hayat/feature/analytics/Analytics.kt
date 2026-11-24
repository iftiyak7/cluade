package com.hayat.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.BarChart
import com.hayat.core.designsystem.component.AchievementChip
import com.hayat.core.designsystem.component.AnimatedCount
import com.hayat.core.designsystem.component.InkCard
import com.hayat.core.designsystem.component.InkStat
import com.hayat.core.designsystem.component.Metric
import com.hayat.core.designsystem.component.MetricRow
import com.hayat.core.designsystem.component.PastelStat
import com.hayat.core.designsystem.component.PillTabs
import com.hayat.core.designsystem.component.SegmentedProgress
import com.hayat.core.designsystem.component.SmoothLineChart
import com.hayat.core.designsystem.component.animateEnter
import com.hayat.core.designsystem.component.percentOf
import com.hayat.core.designsystem.theme.HayatThemeExt
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.navigation.FitnessRoute
import com.hayat.core.navigation.FocusRoute
import com.hayat.core.designsystem.component.ButtonVariant
import com.hayat.core.designsystem.component.HayatButton
import com.hayat.core.designsystem.component.InlineEmpty
import com.hayat.core.designsystem.component.CardTitle
import com.hayat.core.designsystem.component.ProgressRing
import com.hayat.core.designsystem.component.ValueBarChart
import com.hayat.core.designsystem.component.AchievementBadge
import com.hayat.core.designsystem.component.HeatmapRow
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.LinearMeter
import com.hayat.core.designsystem.component.StatTile
import com.hayat.core.domain.analytics.AnalyticsCalculator
import com.hayat.core.domain.analytics.AnalyticsInput
import com.hayat.core.domain.analytics.AnalyticsSnapshot
import com.hayat.core.domain.analytics.Ratio
import com.hayat.core.domain.repository.*
import com.hayat.core.navigation.ProgressRoute
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import javax.inject.Inject

data class AnalyticsState(val loading: Boolean = true, val days: Int = 7, val snapshot: AnalyticsSnapshot? = null, val islamic: Boolean = false)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    tasks: TaskRepository, routines: RoutineRepository, water: WaterRepository, habits: HabitRepository, ibadah: IbadahRepository,
    medicines: MedicineRepository, study: StudyRepository, fitness: FitnessRepository, prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val sync: com.hayat.core.domain.sync.SyncStatusProvider,
) : HayatViewModel() {
    private val period = MutableStateFlow(7)
    val state = period.flatMapLatest { days ->
        val today = time.today()
        val from = today.minusDays(days - 1L)
        val a = combine(tasks.observeTasks(), routines.observeRoutines(), routines.observeLogs(from, today), water.observeLogs(from, today), water.observeGoals()) { t, r, rl, wl, wg ->
            AnalyticsInput(tasks = t, routines = r, routineLogs = rl, waterLogs = wl, waterGoals = wg)
        }
        val b = combine(habits.observeHabits(), habits.observeCompletions(from, today), ibadah.observePrayerLogs(from, today), medicines.observeMedicines(), medicines.observeDoseLogs(from, today)) { h, hc, pl, m, dl ->
            AnalyticsInput(habits = h, habitCompletions = hc, prayerLogs = pl, medicines = m, doseLogs = dl)
        }
        val c = combine(study.observeSessions(from, today), fitness.observeSessions(from, today), ibadah.observeZikrProgress(from, today), ibadah.observeQuranLogs(from, today), prefs.preferences) { fs, ws, z, q, p ->
            AnalyticsInput(focusSessions = fs, workouts = ws, zikr = z, quran = q) to p.islamicModuleEnabled
        }
        combine(a, b, c) { x, y, (z, islamic) ->
            val input = x.copy(habits = y.habits, habitCompletions = y.habitCompletions, prayerLogs = y.prayerLogs, medicines = y.medicines, doseLogs = y.doseLogs,
                focusSessions = z.focusSessions, workouts = z.workouts, zikr = z.zikr, quran = z.quran)
            AnalyticsState(false, days, AnalyticsCalculator.compute(input, from, today, today, time.zone()), islamic)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default) // heavy aggregation off the main thread
        .stateInVm(AnalyticsState())
    fun setPeriod(d: Int) { period.value = d }
    fun refresh() = sync.requestSync()
}

fun NavGraphBuilder.analyticsGraph(nav: NavController) { composable<ProgressRoute> { AnalyticsScreen(nav) } }

@Composable
private fun RatioRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, r: Ratio, tone: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>) {
    val fmt = LocalFormatter.current
    val pct = percentOf(r.value)
    val text = if (r.isEmpty) stringResource(R.string.an_no_data) else stringResource(R.string.an_ratio, fmt.number(r.done), fmt.number(r.total))
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 6.dp)) {
        MetricRow(Metric(icon, title, if (r.isEmpty) "–" else fmt.number(pct) + "%", tone))
        Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearMeter(pct / 100f, Modifier.weight(1f), color = tone.second, description = text, trackColor = tone.first)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Achievements are earned only from real records in the selected period; locked ones show what to aim for. */
private data class Achievement(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int, val earned: Boolean)

private fun achievements(snap: AnalyticsSnapshot, islamic: Boolean): List<Achievement> = buildList {
    val active = snap.activityByDay.count { it.value > 0 }
    add(Achievement(Icons.Outlined.LocalFireDepartment, R.string.an_ach_active, active >= 5))
    add(Achievement(Icons.Outlined.TaskAlt, R.string.an_ach_tasks, !snap.tasks.isEmpty && snap.tasks.total >= 3 && snap.tasks.value >= 0.8f))
    add(Achievement(Icons.Outlined.WaterDrop, R.string.an_ach_water, !snap.waterGoalDays.isEmpty && snap.waterGoalDays.done >= 3))
    add(Achievement(Icons.Outlined.Repeat, R.string.an_ach_habits, !snap.habits.isEmpty && snap.habits.value >= 0.8f))
    add(Achievement(Icons.Outlined.Timer, R.string.an_ach_focus, snap.studyMinutes >= 60))
    add(Achievement(Icons.Outlined.Medication, R.string.an_ach_meds, !snap.medication.isEmpty && snap.medication.value >= 0.9f))
    if (islamic) add(Achievement(Icons.Outlined.Mosque, R.string.an_ach_prayer, !snap.prayers.isEmpty && snap.prayers.value >= 0.8f))
}.sortedByDescending { it.earned }

/** Current streak (consecutive active days ending today, or yesterday if today is still empty) and best streak. */
private fun streaks(days: List<Boolean>): Pair<Int, Int> {
    var best = 0; var run = 0
    days.forEach { if (it) { run++; best = maxOf(best, run) } else run = 0 }
    var current = 0
    val trimmed = if (days.isNotEmpty() && !days.last()) days.dropLast(1) else days
    for (d in trimmed.asReversed()) if (d) current++ else break
    return current to best
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsScreen(nav: NavController, vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    HayatScreen(title = stringResource(R.string.an_title), onBack = null) { pad ->
        val snap = s.snapshot
        if (s.loading || snap == null) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        val ratios = listOfNotNull(snap.tasks, snap.routines, snap.habits, snap.waterGoalDays, snap.medication, snap.prayers.takeIf { s.islamic }).filter { !it.isEmpty }
        val score = percentOf(if (ratios.isEmpty()) 0f else ratios.map { it.value }.average().toFloat())
        val activeDays = snap.activityByDay.count { it.value > 0 }
        val (current, best) = streaks(snap.activityByDay.map { it.value > 0 })
        var refreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing, modifier = Modifier.padding(pad),
            onRefresh = { refreshing = true; vm.refresh(); scope.launch { kotlinx.coroutines.delay(700); refreshing = false } },
        ) {
        LazyColumn(Modifier, contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PillTabs(listOf(stringResource(R.string.an_7), stringResource(R.string.an_30)), if (s.days == 7) 0 else 1, { vm.setPeriod(if (it == 0) 7 else 30) }, Modifier.animateEnter(0)) }
            item {
                HayatCard(Modifier.animateEnter(1), contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(score / 100f, size = 116.dp, stroke = 12.dp, description = stringResource(R.string.an_score) + " " + fmt.number(score) + "%",
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            AnimatedCount(score, MaterialTheme.typography.headlineMedium, format = { fmt.number(it) + "%" })
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.an_score), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.an_score_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.an_active_of, fmt.number(activeDays), fmt.number(snap.activityByDay.size)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                Row(Modifier.animateEnter(2).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PastelStat({ AnimatedCount(current, MaterialTheme.typography.displaySmall, format = { fmt.number(it) }) }, stringResource(R.string.an_streak_current), ext.prayer, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.LocalFireDepartment)
                    PastelStat({ AnimatedCount(best, MaterialTheme.typography.displaySmall, format = { fmt.number(it) }) }, stringResource(R.string.an_streak_best), ext.habits, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.EmojiEvents)
                }
            }
            item {
                HayatCard(Modifier.animateEnter(3)) {
                    Text(stringResource(R.string.an_activity), style = MaterialTheme.typography.titleMedium)
                    val summary = fmt.digits(stringResource(R.string.an_activity_summary, activeDays))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    val days = snap.activityByDay.takeLast(if (s.days == 7) 7 else 30)
                    val weekly = s.days == 7
                    // 30 days: one bar per day, labels every 5th day (1, 6, 11 …) so they never overlap.
                    ValueBarChart(
                        values = days.map { it.value },
                        labels = days.mapIndexed { i, d -> if (weekly) fmt.weekdayShort(d.date.dayOfWeek) else if (i % 5 == 0) fmt.number(d.date.dayOfMonth) else "" },
                        summary = summary, highlightIndex = days.lastIndex,
                        axisLabel = { fmt.number(kotlin.math.round(it).toInt()) },
                    )
                }
            }
            item {
                val list = achievements(snap, s.islamic)
                HayatCard(Modifier.animateEnter(4)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.an_achievements), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(fmt.number(list.count { it.earned }) + "/" + fmt.number(list.size), style = MaterialTheme.typography.labelLarge, color = ext.gold)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        list.forEach { AchievementBadge(it.icon, stringResource(it.label), it.earned, lockedLabel = stringResource(R.string.an_locked)) }
                    }
                }
            }
            item {
                HayatCard(Modifier.animateEnter(5)) {
                    Text(stringResource(R.string.an_breakdown), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    RatioRow(Icons.Outlined.TaskAlt, stringResource(R.string.an_tasks), snap.tasks, ext.mint)
                    RatioRow(Icons.Outlined.ViewTimeline, stringResource(R.string.an_routines), snap.routines, ext.sky)
                    RatioRow(Icons.Outlined.Repeat, stringResource(R.string.an_habits), snap.habits, ext.habits)
                    RatioRow(Icons.Outlined.WaterDrop, stringResource(R.string.an_water), snap.waterGoalDays, ext.water)
                    RatioRow(Icons.Outlined.Medication, stringResource(R.string.an_medication), snap.medication, ext.pink)
                    if (s.islamic) RatioRow(Icons.Outlined.Mosque, stringResource(R.string.an_prayers), snap.prayers, ext.prayer)
                }
            }
            // Focus & workouts: real numbers when used, otherwise a friendly start prompt (never "0 min").
            item {
                HayatCard(Modifier.animateEnter(6)) {
                    if (snap.studyMinutes > 0) {
                        CardTitle(Icons.Outlined.Timer, stringResource(R.string.an_study), ext.mint, trailing = { Text(fmt.minutes(snap.studyMinutes), style = MaterialTheme.typography.titleMedium) })
                        Spacer(Modifier.height(12.dp))
                        val days = snap.studyDaily.takeLast(7)
                        ValueBarChart(days.map { it.value }, days.map { fmt.weekdayShort(it.date.dayOfWeek) }, fmt.minutes(snap.studyMinutes), height = 120.dp,
                            axisLabel = { fmt.number(kotlin.math.round(it).toInt()) }, color = ext.mint.second, highlightIndex = days.lastIndex)
                    } else InlineEmpty(Icons.Outlined.Timer, stringResource(R.string.an_focus_empty_title), stringResource(R.string.an_focus_empty_text)) {
                        HayatButton(stringResource(R.string.an_start), { nav.navigate(FocusRoute) }, variant = ButtonVariant.TONAL, height = 44.dp)
                    }
                }
            }
            item {
                HayatCard(Modifier.animateEnter(7)) {
                    if (snap.workouts > 0) CardTitle(Icons.Outlined.FitnessCenter, stringResource(R.string.an_workouts), ext.pink, trailing = {
                        Text(fmt.number(snap.workouts) + " · " + fmt.minutes(snap.workoutMinutes), style = MaterialTheme.typography.titleMedium)
                    }) else InlineEmpty(Icons.Outlined.FitnessCenter, stringResource(R.string.an_workout_empty_title), stringResource(R.string.an_workout_empty_text)) {
                        HayatButton(stringResource(R.string.an_start), { nav.navigate(FitnessRoute) }, variant = ButtonVariant.TONAL, height = 44.dp)
                    }
                }
            }
            if (s.islamic && (snap.zikrTotal > 0 || snap.quranPages > 0)) item {
                Row(Modifier.animateEnter(8).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PastelStat({ AnimatedCount(snap.zikrTotal, MaterialTheme.typography.displaySmall, format = { fmt.number(it) }) }, stringResource(R.string.an_zikr), ext.prayer, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Spa)
                    PastelStat({ Text(fmt.decimal(snap.quranPages), style = MaterialTheme.typography.displaySmall) }, stringResource(R.string.an_quran), ext.mint, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.MenuBook)
                }
            }
            item { InfoBanner(stringResource(R.string.an_private)) }
        }
        }
    }
}
