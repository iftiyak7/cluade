package com.hayat.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { OPEN, COMPLETED }

/** Filters shown as tabs in the task list. */
enum class TaskFilter { TODAY, TOMORROW, UPCOMING, OVERDUE, NO_DATE, COMPLETED, ARCHIVED, ALL }

enum class TaskSort { DUE_DATE, PRIORITY, CREATED, TITLE }

data class Subtask(val id: String, val title: String, val isDone: Boolean, val position: Int)

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val categoryId: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val estimatedMinutes: Int? = null,
    /** Recurrence of the due date; null for one-off tasks. */
    val recurrence: DateRule? = null,
    /** Minutes before the due date-time to remind; null = no reminder. */
    val reminderOffsetMinutes: Int? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val completedAt: Instant? = null,
    val notes: String = "",
    val routineId: String? = null,
    val eventId: String? = null,
    val subtasks: List<Subtask> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    fun isOverdue(today: LocalDate): Boolean = !isCompleted && dueDate != null && dueDate.isBefore(today)
}
