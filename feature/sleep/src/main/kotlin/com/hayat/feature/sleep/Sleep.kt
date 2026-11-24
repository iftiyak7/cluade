package com.hayat.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
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
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.toFailureOrNull
import com.hayat.core.designsystem.component.BarChart
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.domain.health.SleepCalculator
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.SleepRepository
import com.hayat.core.model.SleepEntry
import com.hayat.core.model.UserPreferences
import com.hayat.core.navigation.LifestyleSettingsRoute
import com.hayat.core.navigation.SleepRoute
import com.hayat.core.navigation.TemplatesRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

data class SleepState(val loading: Boolean = true, val entries: List<SleepEntry> = emptyList(), val prefs: UserPreferences = UserPreferences(), val today: LocalDate = LocalDate.now())

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val sleep: SleepRepository,
    prefs: PreferencesRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val today = time.today()
    val state = combine(sleep.observeEntries(today.minusDays(29), today), prefs.preferences) { e, p -> SleepState(false, e, p, today) }.stateInVm(SleepState())

    fun log(bedDate: LocalDate, bed: LocalTime, wakeDate: LocalDate, wake: LocalTime, quality: Int?) = safeLaunch {
        val zone = time.zone()
        val bedAt = LocalDateTime.of(bedDate, bed).atZone(zone).toInstant()
        val wakeAt = LocalDateTime.of(wakeDate, wake).atZone(zone).toInstant()
        val entry = SleepEntry(ids.newId(), bedAt, wakeAt, wakeDate, quality)
        SleepCalculator.validate(entry).toFailureOrNull()?.let { it.handle(); return@safeLaunch }
        sleep.save(entry)
        message(R.string.sleep_saved)
    }

    fun delete(e: SleepEntry) = safeLaunch { sleep.delete(e.id) }
}

fun NavGraphBuilder.sleepGraph(nav: NavController) { composable<SleepRoute> { SleepScreen(nav) } }

@Composable
fun durationLabel(minutes: Long): String = LocalFormatter.current.digits(stringResource(R.string.sleep_duration, (minutes / 60).toInt(), (minutes % 60).toInt()))

@Composable
private fun SleepScreen(nav: NavController, vm: SleepViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val fmt = LocalFormatter.current
    HayatScreen(title = stringResource(R.string.sleep_title), onBack = nav::popBackStack, snackbarHostState = host) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@HayatScreen }
        var bedDate by rememberSaveable { mutableStateOf(s.today.minusDays(1)) }
        var wakeDate by rememberSaveable { mutableStateOf(s.today) }
        var bed by rememberSaveable { mutableStateOf(s.prefs.sleepTime) }
        var wake by rememberSaveable { mutableStateOf(s.prefs.wakeTime) }
        var quality by rememberSaveable { mutableStateOf<Int?>(null) }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HayatCard {
                    Text(stringResource(R.string.sleep_log), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(stringResource(R.string.sleep_bed), bedDate, { d -> d?.let { bedDate = it } }, Modifier.weight(1f))
                        TimeField(stringResource(UiR.string.label_time), bed, { t -> t?.let { bed = it } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(stringResource(R.string.sleep_wake), wakeDate, { d -> d?.let { wakeDate = it } }, Modifier.weight(1f))
                        TimeField(stringResource(UiR.string.label_time), wake, { t -> t?.let { wake = it } }, Modifier.weight(1f))
                    }
                    Text(stringResource(R.string.sleep_quality), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips((1..5).toList(), quality, { "★".repeat(it) }, { quality = if (quality == it) null else it })
                    Button(onClick = { vm.log(bedDate, bed, wakeDate, wake, quality) }) { Text(stringResource(UiR.string.action_save)) }
                }
            }
            val week = s.entries.filter { !it.date.isBefore(s.today.minusDays(6)) }
            item {
                HayatCard {
                    Text(stringResource(R.string.sleep_week), style = MaterialTheme.typography.titleMedium)
                    val days = (6 downTo 0).map { s.today.minusDays(it.toLong()) }
                    val values = days.map { d -> s.entries.filter { it.date == d }.sumOf { it.duration.toMinutes() } / 60f }
                    val avg = SleepCalculator.averageMinutes(week).toLong()
                    val summary = stringResource(R.string.sleep_average, durationLabel(avg), durationLabel(s.prefs.sleepGoalMinutes.toLong()))
                    BarChart(values, days.map { fmt.weekdayNarrow(it.dayOfWeek) }, summary, goal = s.prefs.sleepGoalMinutes / 60f)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row {
                    TextButton(onClick = { nav.navigate(LifestyleSettingsRoute) }) { Icon(Icons.Outlined.Alarm, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sleep_reminders)) }
                }
            }
            item {
                HayatCard(onClick = { nav.navigate(TemplatesRoute) }) {
                    Text(stringResource(R.string.sleep_routine), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.sleep_routine_text), style = MaterialTheme.typography.bodySmall)
                }
            }
            item { SectionHeader(stringResource(R.string.sleep_history)) }
            if (s.entries.isEmpty()) item { Text(stringResource(R.string.sleep_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.entries.take(14), key = { it.id }) { e ->
                val zone = java.time.ZoneId.systemDefault()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fmt.dayMonth(e.date) + " · " + durationLabel(e.duration.toMinutes()), style = MaterialTheme.typography.bodyLarge)
                        Text(fmt.time(e.bedAt.atZone(zone).toLocalTime()) + " → " + fmt.time(e.wakeAt.atZone(zone).toLocalTime()) +
                            (e.quality?.let { " · " + fmt.digits(androidx.compose.ui.platform.LocalResources.current.getString(R.string.sleep_quality_n, it)) } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.delete(e) }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
                }
            }
            item { InfoBanner(stringResource(R.string.sleep_manual_note)) }
        }
    }
}
