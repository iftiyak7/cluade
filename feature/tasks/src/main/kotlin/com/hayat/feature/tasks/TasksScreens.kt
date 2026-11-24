package com.hayat.feature.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.ConfirmDialog
import com.hayat.core.designsystem.component.EmptyState
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.KeyValueRow
import com.hayat.core.designsystem.component.SectionHeader
import com.hayat.core.designsystem.component.StatusChip
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.model.Category
import com.hayat.core.model.DateRule
import com.hayat.core.model.Priority
import com.hayat.core.model.Task
import com.hayat.core.model.TaskFilter
import com.hayat.core.model.TaskSort
import com.hayat.core.navigation.TaskDetailRoute
import com.hayat.core.navigation.TaskEditRoute
import com.hayat.core.navigation.TasksRoute
import com.hayat.core.ui.DateField
import com.hayat.core.ui.DateRuleEditor
import com.hayat.core.ui.FormTextField
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.LoadingBox
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import com.hayat.core.ui.NumberField
import com.hayat.core.ui.TimeField
import com.hayat.core.ui.UiEvent
import com.hayat.core.ui.forField
import com.hayat.core.ui.labelRes
import com.hayat.core.ui.summary
import com.hayat.core.ui.taskCategoryRes
import java.time.DayOfWeek
import java.time.LocalDate
import com.hayat.core.ui.R as UiR

fun NavGraphBuilder.tasksGraph(nav: NavController) {
    composable<TasksRoute> { TasksScreen(nav) }
    composable<TaskEditRoute> { TaskEditScreen(nav::popBackStack) }
    composable<TaskDetailRoute> { TaskDetailScreen(nav) }
}

@Composable
fun categoryName(c: Category): String = taskCategoryRes(c.systemKey)?.let { stringResource(it) } ?: c.name

@Composable
fun TaskFilter.label(): String = stringResource(
    when (this) {
        TaskFilter.TODAY -> R.string.filter_today; TaskFilter.TOMORROW -> R.string.filter_tomorrow; TaskFilter.UPCOMING -> R.string.filter_upcoming
        TaskFilter.OVERDUE -> R.string.filter_overdue; TaskFilter.NO_DATE -> R.string.filter_no_date; TaskFilter.COMPLETED -> R.string.filter_completed
        TaskFilter.ARCHIVED -> R.string.filter_archived; TaskFilter.ALL -> R.string.filter_all
    },
)

@Composable
fun priorityTone(p: Priority) = when (p) { Priority.URGENT -> StatusTone.ERROR; Priority.HIGH -> StatusTone.WARNING; Priority.MEDIUM -> StatusTone.INFO; Priority.LOW -> StatusTone.NEUTRAL }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasksScreen(nav: NavController, vm: TasksViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    var sortMenu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selecting = s.selected.isNotEmpty()
    HayatScreen(
        title = if (selecting) stringResource(R.string.task_selected, s.selected.size) else stringResource(R.string.tasks_title),
        onBack = { if (selecting) vm.clearSelection() else nav.popBackStack() },
        snackbarHostState = host,
        actions = {
            if (selecting) {
                IconButton(onClick = vm::completeSelected) { Icon(Icons.Outlined.DoneAll, stringResource(R.string.task_mark_done)) }
                IconButton(onClick = { vm.archiveSelected(s.filter != TaskFilter.ARCHIVED) }) { Icon(Icons.Outlined.Archive, stringResource(UiR.string.action_archive)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            } else {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, stringResource(R.string.sort_by)) }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    TaskSort.entries.forEach { srt ->
                        DropdownMenuItem(text = { Text(stringResource(when (srt) { TaskSort.DUE_DATE -> R.string.sort_due; TaskSort.PRIORITY -> R.string.sort_priority; TaskSort.CREATED -> R.string.sort_created; TaskSort.TITLE -> R.string.sort_title })) },
                            onClick = { vm.setSort(srt); sortMenu = false }, leadingIcon = { if (srt == s.sort) Icon(Icons.Outlined.Check, null) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(onClick = { nav.navigate(TaskEditRoute(date = if (s.filter == TaskFilter.TODAY) s.today.toString() else null)) },
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.task_new)) })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(s.query, vm::setQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text(stringResource(R.string.search_tasks)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskFilter.entries.filter { it != TaskFilter.ALL }) { f -> FilterChip(f == s.filter, { vm.setFilter(f) }, label = { Text(f.label()) }) }
            }
            if (s.categories.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.categories) { c -> FilterChip(c.id == s.categoryId, { vm.setCategory(if (c.id == s.categoryId) null else c.id) }, label = { Text(categoryName(c)) }) }
            }
            when {
                s.loading -> LoadingBox()
                s.tasks.isEmpty() -> EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.empty_tasks_title), stringResource(R.string.empty_tasks_text))
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.tasks, key = { it.id }) { t ->
                        TaskRow(t, s.today, selected = t.id in s.selected,
                            onToggle = { vm.toggleComplete(t) },
                            onClick = { if (selecting) vm.toggleSelect(t.id) else nav.navigate(TaskDetailRoute(t.id)) },
                            onLongClick = { vm.toggleSelect(t.id) },
                            category = s.categories.firstOrNull { it.id == t.categoryId })
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirmDelete = false; vm.deleteSelected() }, { confirmDelete = false }, destructive = true)
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TaskRow(task: Task, today: LocalDate, selected: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, category: Category?) {
    val fmt = LocalFormatter.current
    val doneLabel = stringResource(UiR.string.status_completed)
    val notDoneLabel = stringResource(UiR.string.status_pending)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(task.isCompleted, { onToggle() }, Modifier.semantics { stateDescription = if (task.isCompleted) doneLabel else notDoneLabel })
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                task.dueDate?.let { d ->
                    val overdue = task.isOverdue(today)
                    val text = when (d) { today -> stringResource(UiR.string.label_today); today.plusDays(1) -> stringResource(UiR.string.label_tomorrow); else -> fmt.dayMonth(d) } +
                        (task.dueTime?.let { " · " + fmt.time(it) } ?: "")
                    StatusChip(if (overdue) stringResource(UiR.string.status_overdue) + " · " + text else text, if (overdue) StatusTone.ERROR else StatusTone.NEUTRAL,
                        icon = if (overdue) Icons.Outlined.Warning else Icons.Outlined.Event)
                }
                if (task.priority >= Priority.HIGH) StatusChip(stringResource(task.priority.labelRes()), priorityTone(task.priority), icon = Icons.Outlined.Flag)
                if (task.subtasks.isNotEmpty()) StatusChip(stringResource(R.string.task_subtasks_progress, fmt.number(task.subtasks.count { it.isDone }), fmt.number(task.subtasks.size)), StatusTone.NEUTRAL, icon = Icons.Outlined.Checklist)
                if (task.recurrence != null) StatusChip(task.recurrence!!.summary(), StatusTone.NEUTRAL, icon = Icons.Outlined.Repeat)
                category?.let { StatusChip(categoryName(it), StatusTone.NEUTRAL) }
                task.tags.forEach { StatusChip("#" + it.name, StatusTone.NEUTRAL) }
            }
        }
    }
}

@Composable
private fun reminderOptions(): List<Pair<Int?, String>> = listOf(
    null to stringResource(R.string.task_reminder_none),
    0 to stringResource(R.string.task_reminder_at_time),
    5 to LocalFormatter.current.digits(stringResource(R.string.task_reminder_before, 5)),
    15 to LocalFormatter.current.digits(stringResource(R.string.task_reminder_before, 15)),
    30 to LocalFormatter.current.digits(stringResource(R.string.task_reminder_before, 30)),
    60 to LocalFormatter.current.digits(stringResource(R.string.task_reminder_before, 60)),
    1440 to stringResource(R.string.task_reminder_day_before),
)

@Composable
private fun TaskEditScreen(onBack: () -> Unit, vm: TaskEditViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) onBack() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val f = form
    HayatScreen(
        title = stringResource(if (f?.isNew != false) R.string.task_new else R.string.task_edit),
        onBack = onBack, snackbarHostState = host,
        actions = {
            if (f != null && !f.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            TextButton(onClick = vm::save) { Text(stringResource(UiR.string.action_save)) }
        },
    ) { pad ->
        if (f == null) { LoadingBox(); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(f.title, { v -> vm.edit { it.copy(title = v) } }, stringResource(UiR.string.label_title), error = f.errors.forField("title"), maxLength = 120)
            FormTextField(f.description, { v -> vm.edit { it.copy(description = v) } }, stringResource(UiR.string.label_description), singleLine = false, minLines = 2, error = f.errors.forField("description"))
            SectionHeader(stringResource(UiR.string.label_priority))
            val prLabels = Priority.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceChips(Priority.entries, f.priority, { prLabels.getValue(it) }, { p -> vm.edit { it.copy(priority = p) } })
            if (categories.isNotEmpty()) {
                SectionHeader(stringResource(R.string.task_category))
                val names = categories.associate { it.id to categoryName(it) }
                val none = stringResource(UiR.string.label_none)
                ChoiceChips(listOf<String?>(null) + categories.map { it.id }, f.categoryId, { it?.let(names::getValue) ?: none }, { id -> vm.edit { it.copy(categoryId = id) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(stringResource(R.string.task_due_date), f.dueDate, { d -> vm.edit { it.copy(dueDate = d) } }, Modifier.weight(1f), allowClear = true, isError = f.errors.forField("dueDate") != null)
                TimeField(stringResource(R.string.task_due_time), f.dueTime, { t -> vm.edit { it.copy(dueTime = t) } }, Modifier.weight(1f), allowClear = true)
            }
            if (f.dueDate == null) InfoBanner(stringResource(R.string.task_reminder_needs_date))
            else {
                SectionHeader(stringResource(UiR.string.label_reminder))
                val opts = reminderOptions()
                ChoiceChips(opts.map { it.first }, f.reminderOffset, { o -> opts.first { it.first == o }.second }, { o -> vm.edit { it.copy(reminderOffset = o) } })
                SectionHeader(stringResource(UiR.string.label_repeat))
                DateRuleEditor(f.recurrence ?: DateRule.Once, f.dueDate, { r -> vm.edit { it.copy(recurrence = r) } }, DayOfWeek.SUNDAY)
            }
            NumberField(f.estimate, { v -> vm.edit { it.copy(estimate = v) } }, stringResource(R.string.task_estimate), error = f.errors.forField("estimatedMinutes"))
            SectionHeader(stringResource(R.string.task_subtasks))
            f.subtasks.forEach { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(st.title, { vm.updateSubtask(st.id, it.take(120)) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.task_subtask_hint)) }, singleLine = true)
                    IconButton(onClick = { vm.removeSubtask(st.id) }) { Icon(Icons.Outlined.Close, stringResource(UiR.string.action_remove)) }
                }
            }
            TextButton(onClick = vm::addSubtask) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.task_add_subtask)) }
            FormTextField(f.tagsText, { v -> vm.edit { it.copy(tagsText = v) } }, stringResource(R.string.task_tags), supporting = stringResource(R.string.task_tags_hint))
            FormTextField(f.notes, { v -> vm.edit { it.copy(notes = v) } }, stringResource(UiR.string.label_notes), singleLine = false, minLines = 3)
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirmDelete = false; vm.delete() }, { confirmDelete = false }, destructive = true)
}

@Composable
private fun TaskDetailScreen(nav: NavController, vm: TaskDetailViewModel = hiltViewModel()) {
    val task by vm.task.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.events.collect { if (it is UiEvent.NavigateBack) nav.popBackStack() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    val t = task
    HayatScreen(
        title = t?.title.orEmpty(), onBack = nav::popBackStack,
        actions = {
            if (t != null) {
                IconButton(onClick = { nav.navigate(TaskEditRoute(t.id)) }) { Icon(Icons.Outlined.Edit, stringResource(UiR.string.action_edit)) }
                IconButton(onClick = vm::archive) { Icon(Icons.Outlined.Archive, stringResource(if (t.isArchived) UiR.string.action_unarchive else UiR.string.action_archive)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(UiR.string.action_delete)) }
            }
        },
        floatingActionButton = {
            if (t != null) ExtendedFloatingActionButton(onClick = vm::toggleComplete,
                icon = { Icon(if (t.isCompleted) Icons.Outlined.Undo else Icons.Outlined.Check, null) },
                text = { Text(stringResource(if (t.isCompleted) R.string.task_mark_open else R.string.task_mark_done)) })
        },
    ) { pad ->
        if (t == null) { LoadingBox(); return@HayatScreen }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (t.isCompleted) StatusChip(t.completedAt?.let { stringResource(R.string.task_completed_on, fmt.dateTime(java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()))) } ?: stringResource(UiR.string.status_completed), StatusTone.SUCCESS, icon = Icons.Outlined.CheckCircle)
            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodyLarge)
            HayatCard {
                KeyValueRow(stringResource(UiR.string.label_priority), stringResource(t.priority.labelRes()))
                t.dueDate?.let { KeyValueRow(stringResource(R.string.task_due_date), fmt.date(it) + (t.dueTime?.let { tm -> " · " + fmt.time(tm) } ?: "")) }
                t.recurrence?.let { KeyValueRow(stringResource(UiR.string.label_repeat), it.summary()) }
                t.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.let { KeyValueRow(stringResource(R.string.task_category), categoryName(it)) } }
                t.estimatedMinutes?.let { KeyValueRow(stringResource(R.string.task_estimate), fmt.number(it)) }
                t.reminderOffsetMinutes?.let { KeyValueRow(stringResource(UiR.string.label_reminder), if (it == 0) stringResource(R.string.task_reminder_at_time) else fmt.digits(stringResource(R.string.task_reminder_before, it))) }
            }
            if (t.subtasks.isNotEmpty()) {
                SectionHeader(stringResource(R.string.task_subtasks))
                t.subtasks.forEach { st ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(st.isDone, { vm.toggleSubtask(st) })
                        Text(st.title, textDecoration = if (st.isDone) TextDecoration.LineThrough else null)
                    }
                }
            }
            if (t.tags.isNotEmpty()) Text(t.tags.joinToString("  ") { "#" + it.name }, color = MaterialTheme.colorScheme.primary)
            if (t.notes.isNotBlank()) { HorizontalDivider(); Text(t.notes, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(80.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(UiR.string.confirm_delete_title), stringResource(UiR.string.confirm_delete_message),
        stringResource(UiR.string.action_delete), stringResource(UiR.string.action_cancel), { confirmDelete = false; vm.delete() }, { confirmDelete = false }, destructive = true)
}
