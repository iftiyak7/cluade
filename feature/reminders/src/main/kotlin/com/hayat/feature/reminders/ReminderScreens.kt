package com.hayat.feature.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.reminder.SystemSettingsTarget
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.DateRule
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Priority
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.TimeRule
import com.hayat.core.model.ZonePolicy
import com.hayat.core.navigation.ReminderEditRoute
import com.hayat.core.navigation.ReminderRescheduleRoute
import com.hayat.core.navigation.RemindersRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.DateRuleEditor
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.forField
import com.hayat.core.ui.labelRes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import com.hayat.core.ui.R as UiR

fun NavGraphBuilder.remindersGraph(nav: NavController) {
    composable<RemindersRoute> { RemindersScreen(nav) }
    composable<ReminderEditRoute> { ReminderEditScreen(nav::popBackStack) }
    composable<ReminderRescheduleRoute> { RescheduleScreen(nav::popBackStack) }
}

fun OccurrenceStatus.tone() = when (this) {
    OccurrenceStatus.COMPLETED -> StatusTone.SUCCESS; OccurrenceStatus.MISSED -> StatusTone.ERROR; OccurrenceStatus.SNOOZED -> StatusTone.WARNING
    OccurrenceStatus.DELIVERED -> StatusTone.INFO; else -> StatusTone.NEUTRAL
}

@Composable
fun ownerLabel(t: ReminderTargetType): String = stringResource(
    when (t) {
        ReminderTargetType.TASK -> R.string.owner_task; ReminderTargetType.ROUTINE_ACTIVITY -> R.string.owner_routine
        ReminderTargetType.MEDICINE, ReminderTargetType.MEDICINE_REFILL -> R.string.owner_medicine; ReminderTargetType.WATER -> R.string.owner_water
        ReminderTargetType.MEAL -> R.string.owner_meal; ReminderTargetType.HABIT -> R.string.owner_habit; ReminderTargetType.PRAYER -> R.string.owner_prayer
        ReminderTargetType.SLEEP_BEDTIME, ReminderTargetType.SLEEP_WAKE -> R.string.owner_sleep; ReminderTargetType.WORKOUT -> R.string.owner_workout
        ReminderTargetType.STUDY, ReminderTargetType.FOCUS -> R.string.owner_study; ReminderTargetType.FINANCE -> R.string.owner_finance
        ReminderTargetType.EVENT -> R.string.owner_event; ReminderTargetType.NOTE -> R.string.owner_note
        else -> R.string.owner_other
    },
)

/** Display title for linked reminders whose stored title is an internal key. */
@Composable
fun displayTitle(r: Reminder): String = when (r.targetType) {
    ReminderTargetType.WATER -> stringResource(UiR.string.feature_water)
    ReminderTargetType.SLEEP_BEDTIME, ReminderTargetType.SLEEP_WAKE -> stringResource(UiR.string.feature_sleep)
    ReminderTargetType.PRAYER -> runCatching { com.hayat.core.model.Prayer.valueOf(r.title) }.getOrNull()?.let { stringResource(it.labelRes()) } ?: r.title
    else -> r.title
}

@Composable
fun CapabilityBanners(vm: RemindersViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCapabilities() }
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCapabilities() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!caps.notificationsEnabled) InfoBanner(stringResource(R.string.rem_notifications_off), tone = StatusTone.WARNING) {
            TextButton(onClick = {
                if (caps.needsNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.openSettings(SystemSettingsTarget.APP_NOTIFICATIONS)
            }) { Text(stringResource(R.string.rem_allow)) }
        }
        if (!caps.exactAlarmsAllowed) InfoBanner(stringResource(R.string.rem_exact_off)) {
            TextButton(onClick = { vm.openSettings(SystemSettingsTarget.EXACT_ALARMS) }) { Text(stringResource(R.string.rem_allow)) }
        }
    }
}

@Composable
private fun RemindersScreen(nav: NavController, vm: RemindersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val fmt = LocalFormatter.current
    HayatScreen(
        title = stringResource(R.string.rem_title), onBack = nav::popBackStack, snackbarHostState = host,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(ReminderEditRoute()) }, icon = { Icon(Icons.Outlined.AddAlarm, null) }, text = { Text(stringResource(R.string.rem_new)) }) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.rem_tab_upcoming, R.string.rem_tab_mine, R.string.rem_tab_missed, R.string.rem_tab_all).forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t), maxLines = 1) })
                }
            }
            if (s.loading) { LoadingBox(); return@Column }
            LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CapabilityBanners(vm) }
                when (tab) {
                    0 -> if (s.upcoming.isEmpty()) item { EmptyState(Icons.Outlined.Alarm, stringResource(R.string.rem_tab_upcoming), stringResource(R.string.rem_empty_upcoming)) }
                    else items(s.upcoming, key = { it.occurrence.id }) { item ->
                        val o = item.occurrence
                        HayatCard(onClick = { if (item.reminder.targetType == ReminderTargetType.NONE) nav.navigate(ReminderEditRoute(item.reminder.id)) else nav.navigate(ReminderRescheduleRoute(o.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(displayTitle(item.reminder), style = MaterialTheme.typography.titleSmall)
                                    Text(fmt.dateTime(LocalDateTime.ofInstant(o.effectiveAt, ZoneId.systemDefault())) + " · " + stringResource(item.reminder.category.labelRes()),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (o.precision == AlarmPrecision.INEXACT) StatusChip(stringResource(R.string.rem_precision_inexact), StatusTone.WARNING, icon = Icons.Outlined.Schedule)
                                if (o.status == OccurrenceStatus.SNOOZED) StatusChip(stringResource(o.status.labelRes()), o.status.tone())
                            }
                        }
                    }
                    1, 3 -> {
                        val list = if (tab == 1) s.reminders.filter { it.targetType == ReminderTargetType.NONE } else s.reminders
                        if (list.isEmpty()) item { EmptyState(Icons.Outlined.NotificationsNone, stringResource(R.string.rem_tab_mine), stringResource(R.string.rem_empty_mine)) }
                        else items(list, key = { it.id }) { r ->
                            val editable = r.targetType == ReminderTargetType.NONE
                            HayatCard(onClick = if (editable) ({ nav.navigate(ReminderEditRoute(r.id)) }) else null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(displayTitle(r), style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(r.category.labelRes()) + if (!editable) " · " + stringResource(R.string.rem_managed_by, ownerLabel(r.targetType)) else "",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (editable) Switch(r.enabled, { vm.setEnabled(r, it) })
                                }
                            }
                        }
                    }
                    2 -> if (s.missed.isEmpty()) item { EmptyState(Icons.Outlined.CheckCircle, stringResource(R.string.rem_tab_missed), stringResource(R.string.rem_empty_missed)) }
                    else items(s.missed, key = { it.occurrence.id }) { item ->
                        HayatCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(displayTitle(item.reminder), style = MaterialTheme.typography.titleSmall)
                                    Text(fmt.dateTime(LocalDateTime.ofInstant(item.occurrence.scheduledAt, ZoneId.systemDefault())), style = MaterialTheme.typography.bodySmall)
                                }
                                StatusChip(stringResource(UiR.string.status_missed), StatusTone.ERROR, icon = Icons.Outlined.Warning)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditScreen(onBack: () -> Unit, vm: ReminderEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    val ctx = androidx.compose.ui.platform.LocalResources.current
    val f = form
    HayatScreen(
        title = stringResource(if (f?.isNew != false) R.string.rem_new else R.string.rem_edit), onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        },
    ) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val r = f.reminder
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(r.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), error = f.errors.forField("title"), maxLength = 120)
            FormTextField(r.description, { v -> vm.edit { it.copy(description = v) } }, stringResource(UiR.string.label_description), singleLine = false)
            SectionHeader(stringResource(R.string.rem_category))
            val cats = listOf(ReminderCategory.CUSTOM, ReminderCategory.WORK, ReminderCategory.STUDY, ReminderCategory.EXERCISE, ReminderCategory.APPOINTMENT,
                ReminderCategory.BILL, ReminderCategory.MEAL, ReminderCategory.WATER, ReminderCategory.MEDICINE, ReminderCategory.SLEEP, ReminderCategory.ZIKR, ReminderCategory.QURAN)
            val catLabels = cats.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(cats, r.category, { catLabels.getValue(it) }, { c -> vm.edit { it.copy(category = c) } })
            SectionHeader(stringResource(UiR.string.label_priority))
            val prLabels = Priority.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(Priority.entries, r.priority, { prLabels.getValue(it) }, { p -> vm.edit { it.copy(priority = p) } })
            SectionHeader(stringResource(R.string.rem_style))
            val styleLabels = mapOf(ReminderStyle.NOTIFICATION to stringResource(R.string.rem_style_notification), ReminderStyle.ALARM to stringResource(R.string.rem_style_alarm))
            ChoiceChips(ReminderStyle.entries, r.style, { styleLabels.getValue(it) }, { st -> vm.edit { it.copy(style = st) } })
            if (r.style == ReminderStyle.ALARM) InfoBanner(stringResource(R.string.rem_style_alarm_note))

            SectionHeader(stringResource(R.string.rem_schedules))
            if (f.errors.forField("schedule") != null || f.errors.forField("time") != null) Text(stringResource(UiR.string.error_required), color = MaterialTheme.colorScheme.error)
            r.schedules.forEach { rs ->
                val sch = rs.schedule
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DateField(stringResource(UiR.string.label_start_date), sch.startDate, { d -> d?.let { vm.editSchedule(rs.id) { s -> s.copy(startDate = it) } } }, Modifier.weight(1f))
                        if (r.schedules.size > 1) IconButton(onClick = { vm.removeSchedule(rs.id) }) { Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    DateRuleEditor(sch.dateRule, sch.startDate, { rule -> vm.editSchedule(rs.id) { it.copy(dateRule = rule) } }, firstDay)
                    if (sch.dateRule != DateRule.Once) {
                        Spacer(Modifier.height(8.dp))
                        DateField(stringResource(UiR.string.label_end_date), sch.endDate, { d -> vm.editSchedule(rs.id) { it.copy(endDate = d) } }, allowClear = true, isError = f.errors.forField("endDate") != null)
                    }
                    Spacer(Modifier.height(8.dp))
                    val isInterval = sch.timeRule is TimeRule.Every
                    val modeLabels = mapOf(false to stringResource(R.string.rem_mode_times), true to stringResource(R.string.rem_mode_interval))
                    ChoiceChips(listOf(false, true), isInterval, { modeLabels.getValue(it) }, { interval ->
                        vm.editSchedule(rs.id) { s -> s.copy(timeRule = if (interval) TimeRule.Every(60, LocalTime.of(8, 0), LocalTime.of(22, 0)) else TimeRule.At(LocalTime.of(9, 0))) }
                    })
                    when (val tr = sch.timeRule) {
                        is TimeRule.At -> {
                            Text(stringResource(R.string.rem_times), style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tr.times.sorted().forEach { t ->
                                    InputChip(selected = false, onClick = { if (tr.times.size > 1) vm.editSchedule(rs.id) { s -> s.copy(timeRule = TimeRule.At(tr.times - t)) } },
                                        label = { Text(fmt.time(t)) }, trailingIcon = { if (tr.times.size > 1) Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) })
                                }
                            }
                            TimeField(stringResource(R.string.rem_add_time), null, { t -> t?.let { vm.editSchedule(rs.id) { s -> s.copy(timeRule = TimeRule.At((tr.times + it).distinct())) } } })
                        }
                        is TimeRule.Every -> {
                            Text(stringResource(R.string.rem_interval_presets), style = MaterialTheme.typography.labelLarge)
                            ChoiceChips(listOf(15, 30, 60, 120, 180), tr.intervalMinutes, { fmt.digits(ctx.getString(UiR.string.label_every_n_minutes, it)) },
                                { m -> vm.editSchedule(rs.id) { s -> s.copy(timeRule = tr.copy(intervalMinutes = m)) } })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimeField(stringResource(UiR.string.label_window_start), tr.windowStart, { t -> t?.let { vm.editSchedule(rs.id) { s -> s.copy(timeRule = tr.copy(windowStart = it)) } } }, Modifier.weight(1f))
                                TimeField(stringResource(UiR.string.label_window_end), tr.windowEnd, { t -> t?.let { vm.editSchedule(rs.id) { s -> s.copy(timeRule = tr.copy(windowEnd = it)) } } }, Modifier.weight(1f))
                            }
                        }
                    }
                    SwitchRow(stringResource(R.string.rem_zone_fixed, vm.zoneName), sch.zonePolicy == ZonePolicy.FIXED, { vm.setFixedZone(rs.id, it) },
                        subtitle = if (sch.zonePolicy == ZonePolicy.DEVICE) stringResource(R.string.rem_zone_device) else sch.zoneId?.id)
                }
            }
            TextButton(onClick = vm::addSchedule) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.rem_add_schedule)) }
            SectionHeader(stringResource(R.string.rem_snooze))
            ChoiceChips(listOf(5, 10, 15, 30, 60), r.snoozeMinutes, { fmt.digits(ctx.getString(UiR.string.label_minutes, it)) }, { m -> vm.edit { it.copy(snoozeMinutes = m) } })
            SwitchRow(stringResource(R.string.rem_requires_completion), r.requiresCompletion, { v -> vm.edit { it.copy(requiresCompletion = v) } }, subtitle = stringResource(R.string.rem_requires_completion_sub))
            if (history.isNotEmpty()) {
                SectionHeader(stringResource(R.string.rem_history))
                history.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.dateTime(LocalDateTime.ofInstant(o.scheduledAt, ZoneId.systemDefault())), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        StatusChip(stringResource(o.status.labelRes()), o.status.tone())
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirmDelete = false; vm.delete() }, { confirmDelete = false }, destructive = true)
}

@Composable
private fun RescheduleScreen(onBack: () -> Unit, vm: RescheduleViewModel = hiltViewModel()) {
    val item by vm.item.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    val fmt = LocalFormatter.current
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    HayatScreen(title = stringResource(R.string.rem_reschedule_title), onBack = onBack, snackbarHostState = host) { pad ->
        val it = item
        if (it == null) { LoadingBox(); return@HayatScreen }
        val zone = vm.zone()
        val base = it.occurrence.effectiveAt
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(displayTitle(it.reminder), style = MaterialTheme.typography.headlineSmall)
            Text(fmt.dateTime(LocalDateTime.ofInstant(base, zone)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Duration.ofMinutes(15) to R.string.rem_dur_15m, Duration.ofHours(1) to R.string.rem_dur_1h, Duration.ofHours(3) to R.string.rem_dur_3h).forEach { (d, label) ->
                    AssistChip(onClick = { vm.rescheduleTo(vm.now().plus(d)) }, label = { Text(stringResource(R.string.rem_reschedule_in, stringResource(label))) })
                }
                AssistChip(onClick = { vm.rescheduleTo(base.atZone(zone).plusDays(1).toInstant()) }, label = { Text(stringResource(R.string.rem_reschedule_tomorrow)) })
            }
            SectionHeader(stringResource(R.string.rem_reschedule_pick))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(UiR.string.label_date), date, { d -> date = d }, Modifier.weight(1f))
                TimeField(stringResource(UiR.string.label_time), time, { t -> time = t }, Modifier.weight(1f))
            }
            TextButton(enabled = date != null && time != null, onClick = { vm.rescheduleTo(LocalDateTime.of(date, time).atZone(zone).toInstant()) }) { Text(stringResource(UiR.string.action_save)) }
            TextButton(onClick = vm::markDone) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.rem_mark_done)) }
        }
    }
}
