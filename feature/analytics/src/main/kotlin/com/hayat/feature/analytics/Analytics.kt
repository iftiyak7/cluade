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
import javax.inject.Inject

data class AnalyticsState(val loading: Boolean = true, val days: Int = 7, val snapshot: AnalyticsSnapshot? = null, val islamic: Boolean = false)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    tasks: TaskRepository, routines: RoutineRepository, water: WaterRepository, habits: HabitRepository, ibadah: IbadahRepository,
    medicines: MedicineRepository, study: StudyRepository, fitness: FitnessRepository, prefs: PreferencesRepository,
    private val time: TimeProvider,
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
    }.stateInVm(AnalyticsState())
    fun setPeriod(d: Int) { period.value = d }
}

fun NavGraphBuilder.analyticsGraph(nav: NavController) { composable<ProgressRoute> { AnalyticsScreen() } }

@Composable
private fun RatioRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, r: Ratio, tone: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>) {
    val fmt = LocalFormatter.current
    val text = if (r.isEmpty) stringResource(R.string.an_no_data) else stringResource(R.string.an_ratio, fmt.number(r.done), fmt.number(r.total))
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 6.dp)) {
        MetricRow(Metric(icon, title, if (r.isEmpty) "–" else fmt.percent(r.value), tone))
        Row(Modifier.padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearMeter(r.value, Modifier.weight(1f), color = tone.second, description = text)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Achievements are earned only from real records in the selected period. */
private data class Achievement(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int)

private fun achievements(snap: AnalyticsSnapshot, islamic: Boolean): List<Achievement> = buildList {
    val active = snap.activityByDay.count { it.value > 0 }
    if (active >= 5) add(Achievement(Icons.Outlined.LocalFireDepartment, R.string.an_ach_active))
    if (!snap.tasks.isEmpty && snap.tasks.total >= 3 && snap.tasks.value >= 0.8f) add(Achievement(Icons.Outlined.TaskAlt, R.string.an_ach_tasks))
    if (!snap.waterGoalDays.isEmpty && snap.waterGoalDays.done >= 3) add(Achievement(Icons.Outlined.WaterDrop, R.string.an_ach_water))
    if (!snap.habits.isEmpty && snap.habits.value >= 0.8f) add(Achievement(Icons.Outlined.Repeat, R.string.an_ach_habits))
    if (snap.studyMinutes >= 60) add(Achievement(Icons.Outlined.Timer, R.string.an_ach_focus))
    if (!snap.medication.isEmpty && snap.medication.value >= 0.9f) add(Achievement(Icons.Outlined.Medication, R.string.an_ach_meds))
    if (islamic && !snap.prayers.isEmpty && snap.prayers.value >= 0.8f) add(Achievement(Icons.Outlined.Mosque, R.string.an_ach_prayer))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AnalyticsScreen(vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    HayatScreen(title = stringResource(R.string.an_title), onBack = null) { pad ->
        val snap = s.snapshot
        if (s.loading || snap == null) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        val ratios = listOfNotNull(snap.tasks, snap.routines, snap.habits, snap.waterGoalDays, snap.medication, snap.prayers.takeIf { s.islamic }).filter { !it.isEmpty }
        val score = if (ratios.isEmpty()) 0f else ratios.map { it.value }.average().toFloat()
        val activeDays = snap.activityByDay.count { it.value > 0 }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PillTabs(listOf(stringResource(R.string.an_7), stringResource(R.string.an_30)), if (s.days == 7) 0 else 1, { vm.setPeriod(if (it == 0) 7 else 30) }, Modifier.animateEnter(0)) }
            item {
                InkCard(Modifier.animateEnter(1)) {
                    Text(stringResource(R.string.an_score), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.an_score_hint), style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                    Spacer(Modifier.height(14.dp))
                    val desc = stringResource(R.string.an_score) + " " + fmt.percent(score)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCount(percentOf(score), MaterialTheme.typography.displayMedium, format = { fmt.number(it) + "%" })
                        Spacer(Modifier.width(14.dp))
                        SegmentedProgress(score, desc, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InkStat(stringResource(R.string.an_active_days), fmt.digits("$activeDays/${snap.activityByDay.size}"), Modifier.weight(1f))
                        InkStat(stringResource(R.string.an_study), stringResource(R.string.an_minutes, fmt.number(snap.studyMinutes)), Modifier.weight(1f))
                        InkStat(stringResource(R.string.an_workouts), fmt.number(snap.workouts), Modifier.weight(1f))
                    }
                }
            }
            item {
                HayatCard(Modifier.animateEnter(2)) {
                    Text(stringResource(R.string.an_activity), style = MaterialTheme.typography.titleMedium)
                    val summary = fmt.digits(stringResource(R.string.an_activity_summary, activeDays))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    val days = snap.activityByDay
                    val step = if (days.size > 7) 5 else 1
                    SmoothLineChart(
                        days.map { it.value },
                        days.mapIndexed { i, d -> if (i % step == 0 || i == days.lastIndex) (if (step == 1) fmt.weekdayNarrow(d.date.dayOfWeek) else fmt.number(d.date.dayOfMonth)) else "" },
                        summary,
                    )
                }
            }
            item {
                val earned = achievements(snap, s.islamic)
                HayatCard(Modifier.animateEnter(3)) {
                    Text(stringResource(R.string.an_achievements), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (earned.isEmpty()) Text(stringResource(R.string.an_ach_none), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        earned.forEach { AchievementChip(it.icon, stringResource(it.label)) }
                    }
                }
            }
            item {
                HayatCard(Modifier.animateEnter(4)) {
                    Text(stringResource(R.string.an_breakdown), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    RatioRow(Icons.Outlined.TaskAlt, stringResource(R.string.an_tasks), snap.tasks, ext.mint)
                    RatioRow(Icons.Outlined.ViewTimeline, stringResource(R.string.an_routines), snap.routines, ext.sky)
                    RatioRow(Icons.Outlined.Repeat, stringResource(R.string.an_habits), snap.habits, ext.lavender)
                    RatioRow(Icons.Outlined.WaterDrop, stringResource(R.string.an_water), snap.waterGoalDays, ext.sky)
                    RatioRow(Icons.Outlined.Medication, stringResource(R.string.an_medication), snap.medication, ext.pink)
                    if (s.islamic) RatioRow(Icons.Outlined.Mosque, stringResource(R.string.an_prayers), snap.prayers, ext.amber)
                }
            }
            if (s.islamic) item {
                Row(Modifier.animateEnter(5).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PastelStat({ AnimatedCount(snap.zikrTotal, MaterialTheme.typography.displaySmall, format = { fmt.number(it) }) }, stringResource(R.string.an_zikr), ext.amber, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Spa)
                    PastelStat({ Text(fmt.decimal(snap.quranPages), style = MaterialTheme.typography.displaySmall) }, stringResource(R.string.an_quran), ext.mint, Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.MenuBook)
                }
            }
            item {
                HayatCard(Modifier.animateEnter(6)) {
                    Text(stringResource(R.string.an_study_chart), style = MaterialTheme.typography.titleMedium)
                    val days = snap.studyDaily.takeLast(14)
                    BarChart(days.map { it.value }, days.map { fmt.weekdayNarrow(it.date.dayOfWeek) }, stringResource(R.string.an_minutes, fmt.number(snap.studyMinutes)))
                }
            }
            item { InfoBanner(stringResource(R.string.an_private)) }
        }
    }
}
