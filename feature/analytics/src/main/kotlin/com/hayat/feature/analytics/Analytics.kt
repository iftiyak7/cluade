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
private fun RatioCard(title: String, r: Ratio) {
    val fmt = LocalFormatter.current
    HayatCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (r.isEmpty) Text(stringResource(R.string.an_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val text = stringResource(R.string.an_ratio, fmt.number(r.done), fmt.number(r.total)) + " · " + fmt.percent(r.value)
            LinearMeter(r.value, description = text)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AnalyticsScreen(vm: AnalyticsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.an_title), onBack = null) { pad ->
        val snap = s.snapshot
        if (s.loading || snap == null) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(7 to R.string.an_7, 30 to R.string.an_30).forEachIndexed { i, (d, label) ->
                        SegmentedButton(s.days == d, { vm.setPeriod(d) }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(label)) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.an_study), stringResource(R.string.an_minutes, fmt.number(snap.studyMinutes)), Modifier.weight(1f), icon = Icons.Outlined.Timer)
                    StatTile(stringResource(R.string.an_workouts), fmt.number(snap.workouts), Modifier.weight(1f), supporting = stringResource(R.string.an_minutes, fmt.number(snap.workoutMinutes)), icon = Icons.Outlined.FitnessCenter)
                }
            }
            if (s.islamic) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.an_zikr), fmt.number(snap.zikrTotal), Modifier.weight(1f), icon = Icons.Outlined.Spa)
                    StatTile(stringResource(R.string.an_quran), fmt.decimal(snap.quranPages), Modifier.weight(1f), icon = Icons.Outlined.MenuBook)
                }
            }
            item { RatioCard(stringResource(R.string.an_tasks), snap.tasks) }
            item { RatioCard(stringResource(R.string.an_routines), snap.routines) }
            item { RatioCard(stringResource(R.string.an_habits), snap.habits) }
            item { RatioCard(stringResource(R.string.an_water), snap.waterGoalDays) }
            item { RatioCard(stringResource(R.string.an_medication), snap.medication) }
            if (s.islamic) item { RatioCard(stringResource(R.string.an_prayers), snap.prayers) }
            item {
                HayatCard {
                    Text(stringResource(R.string.an_study_chart), style = MaterialTheme.typography.titleSmall)
                    val days = snap.studyDaily.takeLast(14)
                    BarChart(days.map { it.value }, days.map { fmt.weekdayNarrow(it.date.dayOfWeek) }, stringResource(R.string.an_minutes, fmt.number(snap.studyMinutes)))
                }
            }
            item {
                HayatCard {
                    Text(stringResource(R.string.an_activity), style = MaterialTheme.typography.titleSmall)
                    val max = (snap.activityByDay.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1f)
                    val summary = fmt.digits(stringResource(R.string.an_activity_summary, snap.activityByDay.count { it.value > 0 }))
                    HeatmapRow(snap.activityByDay.map { it.value / max }, summary, cell = if (s.days > 7) 16.dp else 28.dp)
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { InfoBanner(stringResource(R.string.an_private)) }
        }
    }
}
