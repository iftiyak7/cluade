package com.hayat.feature.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ColorSwatchPicker
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.theme.UserColorChoices
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.Routine
import com.hayat.core.model.RoutineKind
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.navigation.RoutineDetailRoute
import com.hayat.core.navigation.RoutineEditRoute
import com.hayat.core.navigation.RoutinesRoute
import com.hayat.core.navigation.TemplatesRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.DateRuleEditor
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.TemplateStrings
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.forField
import com.hayat.core.ui.labelRes
import com.hayat.core.ui.summary
import com.hayat.core.ui.R as UiR

fun NavGraphBuilder.routinesGraph(nav: NavController) {
    composable<RoutinesRoute> { RoutinesScreen(nav) }
    composable<RoutineEditRoute> { RoutineEditScreen(nav::popBackStack) }
    composable<RoutineDetailRoute> { RoutineDetailScreen(nav) }
    composable<TemplatesRoute> { TemplatesScreen(nav) }
}

@Composable
fun ActivityStatus.tone() = when (this) {
    ActivityStatus.COMPLETED -> StatusTone.SUCCESS; ActivityStatus.SKIPPED -> StatusTone.NEUTRAL
    ActivityStatus.POSTPONED -> StatusTone.WARNING; ActivityStatus.MISSED -> StatusTone.ERROR
}

fun ActivityStatus.icon() = when (this) {
    ActivityStatus.COMPLETED -> Icons.Outlined.CheckCircle; ActivityStatus.SKIPPED -> Icons.Outlined.SkipNext
    ActivityStatus.POSTPONED -> Icons.Outlined.Schedule; ActivityStatus.MISSED -> Icons.Outlined.Warning
}

@Composable
private fun RoutinesScreen(nav: NavController, vm: RoutinesViewModel = hiltViewModel()) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val copySuffix = stringResource(UiR.string.copy_suffix)
    HayatScreen(
        title = stringResource(R.string.routines_title), onBack = nav::popBackStack, snackbarHostState = host,
        actions = { TextButton(onClick = { nav.navigate(TemplatesRoute) }) { Text(stringResource(R.string.routine_templates)) } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { nav.navigate(RoutineEditRoute()) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.routine_new)) }) },
    ) { pad ->
        val list = routines
        when {
            list == null -> LoadingBox(Modifier.padding(pad))
            list.isEmpty() -> EmptyState(Icons.Outlined.ViewTimeline, stringResource(R.string.routine_empty_title), stringResource(R.string.routine_empty_text), Modifier.padding(pad)) {
                TextButton(onClick = { nav.navigate(TemplatesRoute) }) { Text(stringResource(R.string.routine_templates)) }
            }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val groups = listOf(
                    R.string.routine_section_active to list.filter { !it.isArchived && !it.isPaused },
                    R.string.routine_section_paused to list.filter { !it.isArchived && it.isPaused },
                    R.string.routine_section_archived to list.filter { it.isArchived },
                )
                groups.filter { it.second.isNotEmpty() }.forEach { (title, items) ->
                    item { SectionHeader(stringResource(title)) }
                    items(items, key = { it.id }) { r ->
                        RoutineCard(r, onClick = { nav.navigate(RoutineDetailRoute(r.id)) }, onEdit = { nav.navigate(RoutineEditRoute(r.id)) },
                            onPause = { vm.setPaused(r, !r.isPaused) }, onArchive = { vm.setArchived(r, !r.isArchived) },
                            onDuplicate = { vm.duplicate(r, copySuffix) }, onDelete = { vm.delete(r) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(r: Routine, onClick: () -> Unit, onEdit: () -> Unit, onPause: () -> Unit, onArchive: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val fmt = LocalFormatter.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    HayatCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(r.color)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(stringResource(r.kind.labelRes()), r.dateRule.summary(),
                    r.startTime?.let { s -> r.endTime?.let { e -> fmt.time(s) + "–" + fmt.time(e) } }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.digits(stringResource(R.string.routine_activity_count, r.activities.size)), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(UiR.string.action_more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(UiR.string.action_edit)) }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                    DropdownMenuItem({ Text(stringResource(if (r.isPaused) UiR.string.action_resume else UiR.string.action_pause)) }, { menu = false; onPause() }, leadingIcon = { Icon(Icons.Outlined.PauseCircle, null) })
                    DropdownMenuItem({ Text(stringResource(UiR.string.action_duplicate)) }, { menu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                    DropdownMenuItem({ Text(stringResource(if (r.isArchived) UiR.string.action_unarchive else UiR.string.action_archive)) }, { menu = false; onArchive() }, leadingIcon = { Icon(Icons.Outlined.Archive, null) })
                    DropdownMenuItem({ Text(stringResource(UiR.string.action_delete)) }, { menu = false; confirm = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                }
            }
        }
    }
    if (confirm) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirm = false; onDelete() }, { confirm = false }, destructive = true)
}

@Composable
private fun RoutineEditScreen(onBack: () -> Unit, vm: RoutineEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val firstDay by vm.firstDay.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    val fmt = LocalFormatter.current
    val f = form
    HayatScreen(
        title = stringResource(if (f?.isNew != false) R.string.routine_new else R.string.routine_edit), onBack = onBack, snackbarHostState = host,
        actions = { TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) } },
    ) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        val r = f.routine
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(r.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(UiR.string.label_name), error = f.errors.forField("name"), maxLength = 120)
            FormTextField(r.description, { v -> vm.edit { it.copy(description = v) } }, stringResource(UiR.string.label_description), singleLine = false)
            SectionHeader(stringResource(R.string.routine_kind))
            val kindLabels = RoutineKind.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(RoutineKind.entries, r.kind, { kindLabels.getValue(it) }, { k -> vm.edit { it.copy(kind = k, dateRule = if (f.isNew) k.defaultRule() else it.dateRule) } })
            SectionHeader(stringResource(UiR.string.label_color))
            val ctx = androidx.compose.ui.platform.LocalResources.current
            ColorSwatchPicker(UserColorChoices, r.color, { c -> vm.edit { it.copy(color = c) } }, { i -> fmt.digits(ctx.getString(UiR.string.label_color_option, i + 1)) })
            SectionHeader(stringResource(UiR.string.label_repeat))
            DateRuleEditor(r.dateRule, r.startDate, { rule -> vm.edit { it.copy(dateRule = rule) } }, firstDay)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(stringResource(UiR.string.label_start_date), r.startDate, { d -> d?.let { vm.edit { s -> s.copy(startDate = it) } } }, Modifier.weight(1f))
                DateField(stringResource(UiR.string.label_end_date), r.endDate, { d -> vm.edit { it.copy(endDate = d) } }, Modifier.weight(1f), allowClear = true, isError = f.errors.forField("endDate") != null)
            }
            SectionHeader(stringResource(R.string.routine_activities))
            vm.conflicts().forEach { c -> InfoBanner(stringResource(R.string.routine_conflict, c.first.title.ifBlank { "…" }, c.second.title.ifBlank { "…" }), tone = StatusTone.WARNING) }
            r.activities.sortedBy { it.startTime }.forEach { a ->
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(a.title, { v -> vm.updateActivity(a.id) { it.copy(title = v.take(120)) } }, Modifier.weight(1f), label = { Text(stringResource(R.string.routine_activity_title)) }, singleLine = true)
                        IconButton(onClick = { vm.removeActivity(a.id) }) { Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(stringResource(R.string.routine_start), a.startTime, { t -> t?.let { vm.updateActivity(a.id) { x -> x.copy(startTime = it) } } }, Modifier.weight(1f))
                        NumberField(a.durationMinutes.toString(), { v -> v.toIntOrNull()?.takeIf { it in 1..1440 }?.let { m -> vm.updateActivity(a.id) { it.copy(durationMinutes = m) } } }, stringResource(R.string.routine_duration), Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routine_activity_reminder), Modifier.weight(1f))
                        Switch(a.reminderEnabled, { on -> vm.updateActivity(a.id) { it.copy(reminderEnabled = on) } })
                    }
                }
            }
            TextButton(onClick = vm::addActivity) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.routine_add_activity)) }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RoutineDetailScreen(nav: NavController, vm: RoutineDetailViewModel = hiltViewModel()) {
    val routine by vm.routine.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var templateDialog by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    val r = routine
    HayatScreen(
        title = r?.name.orEmpty(), onBack = nav::popBackStack, snackbarHostState = host,
        actions = {
            if (r != null) {
                IconButton(onClick = { templateDialog = true }) { Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.routine_save_template)) }
                IconButton(onClick = { nav.navigate(RoutineEditRoute(r.id)) }) { Icon(Icons.Outlined.Edit, stringResource(UiR.string.action_edit)) }
            }
        },
    ) { pad ->
        if (r == null) { LoadingBox(); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(stringResource(UiR.string.label_date), date, { d -> d?.let(vm::setDate) })
            Text(stringResource(R.string.routine_for_date, fmt.date(date)), style = MaterialTheme.typography.titleMedium)
            if (!vm.isScheduled(r, date)) InfoBanner(stringResource(R.string.routine_not_scheduled))
            r.activities.sortedBy { it.startTime }.forEach { a ->
                val log = logs.firstOrNull { it.activityId == a.id && it.date == date }
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall)
                            Text(fmt.time(a.startTime) + "–" + fmt.time(a.endTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        log?.let { StatusChip(stringResource(it.status.labelRes()), it.status.tone(), icon = it.status.icon()) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActivityStatus.entries.forEach { st ->
                            FilterChip(log?.status == st, { vm.mark(a, if (log?.status == st) null else st) }, label = { Text(stringResource(st.labelRes())) },
                                leadingIcon = { Icon(st.icon(), null, Modifier.size(16.dp)) })
                        }
                    }
                }
            }
            SectionHeader(stringResource(R.string.routine_history))
            val days = (6 downTo 0).map { date.minusDays(it.toLong()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { d ->
                    val items = RoutinePlanner.timeline(listOf(r), logs, d)
                    val rate = RoutinePlanner.completionRate(items)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmt.weekdayNarrow(d.dayOfWeek), style = MaterialTheme.typography.labelSmall)
                        Text(if (items.isEmpty()) "–" else fmt.percent(rate), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (templateDialog && r != null) {
        var name by remember { mutableStateOf(r.name) }
        AlertDialog(onDismissRequest = { templateDialog = false }, title = { Text(stringResource(R.string.routine_save_template)) },
            text = { OutlinedTextField(name, { name = it.take(120) }, singleLine = true, label = { Text(stringResource(UiR.string.label_name)) }) },
            confirmButton = { TextButton(onClick = { templateDialog = false; if (name.isNotBlank()) vm.saveAsTemplate(name.trim()) }) { Text(stringResource(UiR.string.action_save)) } },
            dismissButton = { TextButton(onClick = { templateDialog = false }) { Text(stringResource(UiR.string.action_cancel)) } })
    }
}

@Composable
private fun TemplatesScreen(nav: NavController, vm: TemplatesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var preview by remember { mutableStateOf<RoutineTemplate?>(null) }
    val res = androidx.compose.ui.platform.LocalResources.current
    HayatScreen(title = stringResource(R.string.tpl_title), onBack = nav::popBackStack, snackbarHostState = host) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionHeader(stringResource(R.string.tpl_builtin)) }
            items(s.builtIn, key = { it.id }) { t -> TemplateCard(templateName(t), templateDescription(t), t.items.size) { preview = t } }
            if (s.mine.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.tpl_mine)) }
                items(s.mine, key = { it.id }) { t -> TemplateCard(t.name, t.description, t.items.size) { preview = t } }
            }
        }
    }
    preview?.let { t ->
        val name = templateName(t)
        val titles = t.items.map { item -> TemplateStrings.item(item.title)?.let(res::getString) ?: item.title }
        val fmt = LocalFormatter.current
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.tpl_confirm_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.tpl_confirm_text, name))
                    Spacer(Modifier.height(8.dp))
                    t.items.forEachIndexed { i, item -> Text(fmt.time(item.startTime) + "  " + titles[i], style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null; vm.apply(t, name, titles) { id -> nav.navigate(RoutineDetailRoute(id)) } }) { Text(stringResource(R.string.tpl_use)) } },
            dismissButton = {
                Row {
                    if (t.systemKey == null) TextButton(onClick = { preview = null; vm.delete(t) }) { Text(stringResource(R.string.tpl_delete)) }
                    TextButton(onClick = { preview = null }) { Text(stringResource(UiR.string.action_cancel)) }
                }
            },
        )
    }
}

@Composable private fun templateName(t: RoutineTemplate) = TemplateStrings.name(t.systemKey)?.let { stringResource(it) } ?: t.name
@Composable private fun templateDescription(t: RoutineTemplate) = TemplateStrings.description(t.systemKey)?.let { stringResource(it) } ?: t.description

@Composable
private fun TemplateCard(name: String, description: String, count: Int, onClick: () -> Unit) {
    val fmt = LocalFormatter.current
    HayatCard(onClick = onClick) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt.digits(stringResource(R.string.routine_activity_count, count)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
