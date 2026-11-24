package com.hayat.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.repository.CategoryRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.TagRepository
import com.hayat.core.domain.repository.TaskRepository
import com.hayat.core.domain.task.CompleteTaskUseCase
import com.hayat.core.domain.task.SaveTaskUseCase
import com.hayat.core.domain.task.TaskQuery
import com.hayat.core.model.Category
import com.hayat.core.model.DateRule
import com.hayat.core.model.Priority
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Subtask
import com.hayat.core.model.Task
import com.hayat.core.model.TaskFilter
import com.hayat.core.model.TaskSort
import com.hayat.core.navigation.TaskDetailRoute
import com.hayat.core.navigation.TaskEditRoute
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TaskListState(
    val loading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: TaskFilter = TaskFilter.TODAY,
    val sort: TaskSort = TaskSort.DUE_DATE,
    val query: String = "",
    val categoryId: String? = null,
    val selected: Set<String> = emptySet(),
    val today: LocalDate = LocalDate.now(),
)

private data class ListControls(val filter: TaskFilter = TaskFilter.TODAY, val sort: TaskSort = TaskSort.DUE_DATE, val query: String = "", val categoryId: String? = null, val selected: Set<String> = emptySet())

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    categories: CategoryRepository,
    prefs: PreferencesRepository,
    private val complete: CompleteTaskUseCase,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) : HayatViewModel() {
    private val controls = MutableStateFlow(ListControls())

    val state: StateFlow<TaskListState> = combine(tasks.observeTasks(), categories.observeCategories(), prefs.preferences, controls) { all, cats, p, c ->
        val today = time.today()
        TaskListState(
            loading = false,
            tasks = TaskQuery.apply(all, c.filter, today, c.query, c.categoryId, sort = c.sort, includeOverdueInToday = p.showOverdueInToday),
            categories = cats, filter = c.filter, sort = c.sort, query = c.query, categoryId = c.categoryId, selected = c.selected, today = today,
        )
    }.stateInVm(TaskListState())

    fun setFilter(f: TaskFilter) = controls.update { it.copy(filter = f, selected = emptySet()) }
    fun setSort(s: TaskSort) = controls.update { it.copy(sort = s) }
    fun setQuery(q: String) = controls.update { it.copy(query = q) }
    fun setCategory(id: String?) = controls.update { it.copy(categoryId = id) }
    fun toggleSelect(id: String) = controls.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
    fun clearSelection() = controls.update { it.copy(selected = emptySet()) }

    fun toggleComplete(task: Task) = safeLaunch { complete(task.id, !task.isCompleted).handle() }

    fun completeSelected() = safeLaunch {
        controls.value.selected.forEach { complete(it, true) }
        clearSelection()
    }

    fun archiveSelected(archived: Boolean) = safeLaunch {
        val ids = controls.value.selected
        ids.forEach { linker.remove(ReminderTargetType.TASK, it) }
        tasks.setArchived(ids, archived, time.now())
        clearSelection()
    }

    fun deleteSelected() = safeLaunch {
        val ids = controls.value.selected
        ids.forEach { linker.remove(ReminderTargetType.TASK, it) }
        tasks.softDelete(ids, time.now())
        clearSelection()
        message(com.hayat.core.ui.R.string.msg_deleted)
    }
}

/** Editable form state; text fields are kept as strings while typing. */
data class TaskForm(
    val id: String,
    val isNew: Boolean,
    val title: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val estimate: String = "",
    val reminderOffset: Int? = null,
    val recurrence: DateRule? = null,
    val subtasks: List<Subtask> = emptyList(),
    val tagsText: String = "",
    val notes: String = "",
    val original: Task? = null,
    val errors: List<ValidationError> = emptyList(),
)

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    categories: CategoryRepository,
    private val save: SaveTaskUseCase,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : HayatViewModel() {
    private val route = savedState.toRoute<TaskEditRoute>()
    private val _form = MutableStateFlow<TaskForm?>(null)
    val form: StateFlow<TaskForm?> = _form.asStateFlow()
    val categories = categories.observeCategories().stateInVm(emptyList())

    init {
        safeLaunch {
            val existing = route.id?.let { tasks.getTask(it) }
            _form.value = existing?.let {
                TaskForm(it.id, false, it.title, it.description, it.categoryId, it.priority, it.dueDate, it.dueTime,
                    it.estimatedMinutes?.toString().orEmpty(), it.reminderOffsetMinutes, it.recurrence, it.subtasks,
                    it.tags.joinToString(", ") { t -> t.name }, it.notes, it)
            } ?: TaskForm(ids.newId(), true, dueDate = route.date?.let(LocalDate::parse))
        }
    }

    fun edit(transform: (TaskForm) -> TaskForm) = _form.update { it?.let(transform)?.copy(errors = emptyList()) }

    fun addSubtask() = edit { it.copy(subtasks = it.subtasks + Subtask(ids.newId(), "", false, it.subtasks.size)) }
    fun updateSubtask(id: String, title: String) = edit { f -> f.copy(subtasks = f.subtasks.map { if (it.id == id) it.copy(title = title) else it }) }
    fun removeSubtask(id: String) = edit { f -> f.copy(subtasks = f.subtasks.filter { it.id != id }) }

    fun save() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        val now = time.now()
        val tagList = f.tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(10).map { tags.getOrCreate(it) }
        val task = (f.original ?: Task(f.id, f.title, createdAt = now, updatedAt = now)).copy(
            title = f.title, description = f.description, categoryId = f.categoryId, priority = f.priority,
            dueDate = f.dueDate, dueTime = f.dueTime?.takeIf { f.dueDate != null }, estimatedMinutes = f.estimate.toIntOrNull(),
            reminderOffsetMinutes = f.reminderOffset.takeIf { f.dueDate != null }, recurrence = f.recurrence.takeIf { f.dueDate != null && it != DateRule.Once },
            subtasks = f.subtasks.filter { it.title.isNotBlank() }, tags = tagList, notes = f.notes,
        )
        val errors = save(task).handle { emit(UiEvent.NavigateBack) }
        if (errors.isNotEmpty()) _form.update { it?.copy(errors = errors) }
    }

    fun delete() = safeLaunch {
        val f = _form.value ?: return@safeLaunch
        linker.remove(ReminderTargetType.TASK, f.id)
        tasks.softDelete(listOf(f.id), time.now())
        emit(UiEvent.NavigateBack)
    }
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    categories: CategoryRepository,
    private val complete: CompleteTaskUseCase,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) : HayatViewModel() {
    val id = savedState.toRoute<TaskDetailRoute>().id
    val task = tasks.observeTask(id).stateInVm(null)
    val categories = categories.observeCategories().stateInVm(emptyList())

    fun toggleComplete() = safeLaunch { task.value?.let { complete(it.id, !it.isCompleted).handle() } }
    fun toggleSubtask(s: Subtask) = safeLaunch { tasks.setSubtaskDone(s.id, !s.isDone, time.now()) }
    fun archive() = safeLaunch {
        val t = task.value ?: return@safeLaunch
        linker.remove(ReminderTargetType.TASK, t.id)
        tasks.setArchived(listOf(t.id), !t.isArchived, time.now())
    }
    fun delete() = safeLaunch {
        linker.remove(ReminderTargetType.TASK, id)
        tasks.softDelete(listOf(id), time.now())
        emit(UiEvent.NavigateBack)
    }
}
