package com.hayat.feature.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.ProgressRing
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.domain.health.WaterCalculator
import com.hayat.core.domain.health.WaterUseCases
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.WaterRepository
import com.hayat.core.model.UserPreferences
import com.hayat.core.model.WaterDay
import com.hayat.core.model.WaterLog
import com.hayat.core.navigation.LifestyleSettingsRoute
import com.hayat.core.navigation.WaterRoute
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class WaterState(val loading: Boolean = true, val today: WaterDay? = null, val week: List<WaterDay> = emptyList(), val logs: List<WaterLog> = emptyList(), val prefs: UserPreferences = UserPreferences())

@HiltViewModel
class WaterViewModel @Inject constructor(
    water: WaterRepository,
    prefs: PreferencesRepository,
    private val useCases: WaterUseCases,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(water.observeLogs(today.minusDays(6), today), water.observeGoals(), prefs.preferences) { logs, goals, p ->
        val week = WaterCalculator.days(today.minusDays(6), today, logs, goals)
        WaterState(false, week.last(), week, logs.filter { it.date == today }, p)
    }.stateInVm(WaterState())

    fun add(ml: Int) = safeLaunch { useCases.add(ml).handle { message(R.string.water_added, it.amountMl) } }
    fun update(log: WaterLog, ml: Int) = safeLaunch { useCases.update(log.copy(amountMl = ml)).handle() }
    fun delete(log: WaterLog) = safeLaunch { useCases.delete(log.id).handle() }
    fun setGoal(ml: Int) = safeLaunch { useCases.setGoal(ml).handle() }
}

fun NavGraphBuilder.waterGraph(nav: NavController) {
    composable<WaterRoute> { WaterScreen(nav) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WaterScreen(nav: NavController, vm: WaterViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val fmt = LocalFormatter.current
    var custom by rememberSaveable { mutableStateOf("") }
    var goalDialog by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WaterLog?>(null) }
    HayatScreen(title = stringResource(R.string.water_title), onBack = nav::popBackStack, snackbarHostState = host,
        actions = { IconButton(onClick = { goalDialog = true }) { Icon(Icons.Outlined.Flag, stringResource(R.string.water_goal)) } }) { pad ->
        val today = s.today
        if (s.loading || today == null) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(today.progress, size = 112.dp, stroke = 12.dp,
                            description = stringResource(R.string.water_progress_cd, fmt.number(today.totalMl), fmt.number(today.goalMl))) {
                            Text(fmt.percent(today.progress), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.water_progress, fmt.number(today.totalMl), fmt.number(today.goalMl)), style = MaterialTheme.typography.titleMedium)
                            if (today.totalMl >= today.goalMl) StatusChip(stringResource(R.string.water_goal_reached), StatusTone.SUCCESS, icon = Icons.Outlined.CheckCircle)
                            val w = s.prefs.water
                            TextButton(onClick = { nav.navigate(LifestyleSettingsRoute) }) {
                                Text(if (w.reminderEnabled) stringResource(R.string.water_reminders, fmt.digits(androidx.compose.ui.platform.LocalContext.current.getString(UiR.string.label_minutes, w.reminderIntervalMinutes)),
                                    fmt.time(s.prefs.wakeTime), fmt.time(s.prefs.sleepTime)) else stringResource(R.string.water_reminders_off), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.water_quick_add)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.prefs.water.glassSizesMl.forEach { ml ->
                        FilledTonalButton(onClick = { vm.add(ml) }) { Icon(Icons.Outlined.WaterDrop, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.water_ml, fmt.number(ml))) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(custom, { custom = it }, stringResource(R.string.water_custom), Modifier.weight(1f))
                    Button(onClick = { custom.toIntOrNull()?.let { vm.add(it); custom = "" } }, enabled = custom.toIntOrNull() != null) { Text(stringResource(R.string.water_add)) }
                }
            }
            item { SectionHeader(stringResource(R.string.water_week)) }
            item {
                HayatCard {
                    val summary = stringResource(R.string.water_week_summary, s.week.count { it.totalMl >= it.goalMl }, s.week.size)
                    BarChart(s.week.map { it.totalMl.toFloat() }, s.week.map { fmt.weekdayNarrow(it.date.dayOfWeek) }, summary, goal = today.goalMl.toFloat())
                    Text(fmt.digits(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader(stringResource(R.string.water_today_log)) }
            if (s.logs.isEmpty()) item { Text(stringResource(R.string.water_empty_log), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.logs, key = { it.id }) { log ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WaterDrop, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.water_ml, fmt.number(log.amountMl)), Modifier.weight(1f))
                    Text(fmt.time(LocalDateTime.ofInstant(log.loggedAt, ZoneId.systemDefault()).toLocalTime()), style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { editing = log }) { Icon(Icons.Outlined.Edit, stringResource(R.string.water_edit_entry)) }
                    IconButton(onClick = { vm.delete(log) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                }
            }
            item { InfoBanner(stringResource(R.string.water_goal_note)) }
        }
    }
    if (goalDialog) AmountDialog(stringResource(R.string.water_goal), s.today?.goalMl ?: 2000, onDismiss = { goalDialog = false }) { vm.setGoal(it); goalDialog = false }
    editing?.let { log -> AmountDialog(stringResource(R.string.water_edit_entry), log.amountMl, onDismiss = { editing = null }) { vm.update(log, it); editing = null } }
}

@Composable
private fun AmountDialog(title: String, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { NumberField(text, { text = it }, stringResource(R.string.water_goal_ml)) },
        confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let(onSave) }) { Text(stringResource(UiR.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) } })
}
