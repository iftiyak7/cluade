package com.hayat.core.domain.task

import com.hayat.core.common.AppResult
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.Limits
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationError
import com.hayat.core.common.ValidationReason
import com.hayat.core.common.appCatching
import com.hayat.core.common.toFailureOrNull
import com.hayat.core.common.validate
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.reminder.ReminderTargetHandler
import com.hayat.core.domain.repository.TaskRepository
import com.hayat.core.model.DateRule
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Schedule
import com.hayat.core.model.Task
import com.hayat.core.model.TaskFilter
import com.hayat.core.model.TaskSort
import com.hayat.core.model.TaskStatus
import com.hayat.core.model.TimeRule
import com.hayat.core.time.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

object TaskValidator {
    fun validate(task: Task): List<ValidationError> = validate {
        text(task.title, "title", maxLength = Limits.TITLE_MAX)
        text(task.description, "description", required = false, maxLength = Limits.DESCRIPTION_MAX)
        text(task.notes, "notes", required = false, maxLength = Limits.DESCRIPTION_MAX)
        range(task.estimatedMinutes, "estimatedMinutes", 1.0, 24.0 * 60)
        range(task.reminderOffsetMinutes, "reminderOffset", 0.0, 7.0 * 24 * 60)
        require(task.recurrence == null || task.dueDate != null, "dueDate", ValidationReason.REQUIRED)
        require(task.reminderOffsetMinutes == null || task.dueDate != null, "dueDate", ValidationReason.REQUIRED)
        task.subtasks.forEach { text(it.title, "subtask", maxLength = Limits.TITLE_MAX) }
    }
}

/** Pure filtering/sorting for the task list. Overdue tasks are never moved: [TaskFilter.TODAY] can optionally include them. */
object TaskQuery {
    fun apply(
        tasks: List<Task>,
        filter: TaskFilter,
        today: LocalDate,
        query: String = "",
        categoryId: String? = null,
        tagId: String? = null,
        sort: TaskSort = TaskSort.DUE_DATE,
        includeOverdueInToday: Boolean = true,
    ): List<Task> {
        val q = query.trim()
        return tasks.asSequence()
            .filter { t ->
                when (filter) {
                    TaskFilter.ARCHIVED -> t.isArchived
                    else -> !t.isArchived
                }
            }
            .filter { t ->
                when (filter) {
                    TaskFilter.TODAY -> !t.isCompleted && (t.dueDate == today || (includeOverdueInToday && t.isOverdue(today)))
                    TaskFilter.TOMORROW -> !t.isCompleted && t.dueDate == today.plusDays(1)
                    TaskFilter.UPCOMING -> !t.isCompleted && t.dueDate?.isAfter(today) == true
                    TaskFilter.OVERDUE -> t.isOverdue(today)
                    TaskFilter.NO_DATE -> !t.isCompleted && t.dueDate == null
                    TaskFilter.COMPLETED -> t.isCompleted
                    TaskFilter.ARCHIVED, TaskFilter.ALL -> true
                }
            }
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { tagId == null || it.tags.any { tag -> tag.id == tagId } }
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true) }
            .sortedWith(comparator(sort))
            .toList()
    }

    fun comparator(sort: TaskSort): Comparator<Task> = when (sort) {
        TaskSort.DUE_DATE -> compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }
            .thenBy(nullsLast()) { it.dueTime }
            .thenByDescending { it.priority.ordinal }
        TaskSort.PRIORITY -> compareByDescending<Task> { it.priority.ordinal }.thenBy(nullsLast()) { it.dueDate }
        TaskSort.CREATED -> compareByDescending { it.createdAt }
        TaskSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
}

/** Creates/updates a task, its subtasks, tags, and its linked reminder. */
class SaveTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(task: Task): AppResult<Task> {
        val trimmed = task.copy(title = task.title.trim(), description = task.description.trim(), updatedAt = time.now())
        TaskValidator.validate(trimmed).toFailureOrNull()?.let { return it }
        return appCatching {
            tasks.save(trimmed)
            syncReminder(linker, trimmed)
            trimmed
        }
    }

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

        /** Computes the reminder moment of a task, or null if it should have none. */
        fun reminderMoment(task: Task): LocalDateTime? {
            val offset = task.reminderOffsetMinutes ?: return null
            val date = task.dueDate ?: return null
            if (task.isCompleted || task.isArchived) return null
            return LocalDateTime.of(date, task.dueTime ?: DEFAULT_TIME).minusMinutes(offset.toLong())
        }

        suspend fun syncReminder(linker: ReminderLinker, task: Task) {
            val moment = reminderMoment(task)
            if (moment == null) {
                linker.remove(ReminderTargetType.TASK, task.id)
                return
            }
            linker.upsert(
                LinkedReminderSpec(
                    targetType = ReminderTargetType.TASK,
                    targetId = task.id,
                    title = task.title,
                    description = task.description,
                    category = ReminderCategory.TASK,
                    priority = task.priority,
                    schedules = listOf(Schedule(DateRule.Once, TimeRule.At(moment.toLocalTime()), moment.toLocalDate())),
                ),
            )
        }
    }
}

/**
 * Completes or re-opens a task. Completing a recurring task keeps the completed record as history
 * and creates the next open instance (subtasks reset) on the next matching date.
 */
class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(taskId: String, completed: Boolean): AppResult<Task?> = appCatching {
        val task = tasks.getTask(taskId) ?: return@appCatching null
        val now = time.now()
        if (task.isCompleted == completed) return@appCatching null
        tasks.setStatusCompleted(taskId, completed, now)
        val updated = task.copy(status = if (completed) TaskStatus.COMPLETED else TaskStatus.OPEN, completedAt = if (completed) now else null)
        SaveTaskUseCase.syncReminder(linker, updated)
        if (!completed) return@appCatching null
        val rule = task.recurrence ?: return@appCatching null
        val due = task.dueDate ?: return@appCatching null
        val nextDate = RecurrenceEngine.nextDate(rule, due, null, maxOf(due, time.today().minusDays(1))) ?: return@appCatching null
        val next = task.copy(
            id = ids.newId(),
            dueDate = nextDate,
            status = TaskStatus.OPEN,
            completedAt = null,
            subtasks = task.subtasks.map { it.copy(id = ids.newId(), isDone = false) },
            createdAt = now,
            updatedAt = now,
        )
        tasks.save(next)
        SaveTaskUseCase.syncReminder(linker, next)
        next
    }
}

class TaskReminderHandler @Inject constructor(private val complete: CompleteTaskUseCase) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.TASK
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) {
        complete(targetId, true)
    }
}
