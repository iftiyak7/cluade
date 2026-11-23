package com.hayat.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// ---- Habits ----
/** BUILD: do more of it (success = count >= target). REDUCE: do less (success = count <= target). */
enum class HabitKind { BUILD, REDUCE }

data class Habit(
    val id: String,
    val name: String,
    val description: String = "",
    val kind: HabitKind = HabitKind.BUILD,
    val weekdays: WeekdaySet = WeekdaySet.ALL,
    /** BUILD: required count per day. REDUCE: maximum allowed per day. */
    val targetPerDay: Int = 1,
    val color: Long,
    val icon: String = "habit",
    val reminderTime: LocalTime? = null,
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun isSuccess(count: Int): Boolean = when (kind) {
        HabitKind.BUILD -> count >= targetPerDay
        HabitKind.REDUCE -> count <= targetPerDay
    }
}

data class HabitCompletion(val id: String, val habitId: String, val date: LocalDate, val count: Int, val note: String = "")

data class HabitStats(val currentStreak: Int, val bestStreak: Int, val completionRate: Float, val scheduledDays: Int, val successfulDays: Int)

// ---- Study & focus ----
data class StudySubject(
    val id: String,
    val name: String,
    val color: Long,
    val weeklyGoalMinutes: Int = 0,
    val examDate: LocalDate? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class FocusKind { FOCUS, SHORT_BREAK, LONG_BREAK }

data class FocusSession(
    val id: String,
    val subjectId: String?,
    val kind: FocusKind,
    val startedAt: Instant,
    val endedAt: Instant,
    val plannedMinutes: Int,
    val actualSeconds: Long,
    val completed: Boolean,
    val date: LocalDate,
)

// ---- Finance ----
enum class FinanceKind { BILL, RENT, INSTALLMENT, SUBSCRIPTION, RECEIVABLE, PAYABLE, OTHER }
enum class FinanceRepeat { NONE, WEEKLY, MONTHLY, YEARLY }

data class FinanceItem(
    val id: String,
    val title: String,
    val kind: FinanceKind,
    /** Amount in minor units (e.g. poisha/cents). Optional. */
    val amountMinor: Long? = null,
    val currency: String = "BDT",
    val dueDate: LocalDate,
    val repeat: FinanceRepeat = FinanceRepeat.NONE,
    val counterparty: String = "",
    val note: String = "",
    /** Paid state of the current cycle. Repeating items advance [dueDate] when paid. */
    val isPaid: Boolean = false,
    /** Days before the due date to remind (0 = on the day). Null = no reminder. */
    val reminderLeadDays: Int? = 1,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FinancePayment(val id: String, val itemId: String, val paidOn: LocalDate, val amountMinor: Long?, val note: String = "", val loggedAt: Instant)

// ---- Events ----
enum class EventKind { BIRTHDAY, ANNIVERSARY, APPOINTMENT, TRAVEL, DOCUMENT_EXPIRY, LICENSE_RENEWAL, CUSTOM }

data class CalendarEvent(
    val id: String,
    val title: String,
    val kind: EventKind,
    val customLabel: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val repeatsAnnually: Boolean = false,
    /** Days before the event to remind, e.g. [7, 1, 0]. */
    val reminderLeadDays: List<Int> = emptyList(),
    val notes: String = "",
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// ---- Notes & journal ----
data class ChecklistItem(val id: String, val text: String, val isChecked: Boolean, val position: Int)

data class Note(
    val id: String,
    val title: String,
    val body: String = "",
    val isChecklist: Boolean = false,
    val isPinned: Boolean = false,
    /** Requires app-lock authentication to open. */
    val isLocked: Boolean = false,
    val color: Long? = null,
    /** Optional one-time reminder for this note. */
    val reminderAt: java.time.LocalDateTime? = null,
    val items: List<ChecklistItem> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val title: String,
    val body: String,
    val mood: Int? = null,
    val isLocked: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MoodEntry(val id: String, val date: LocalDate, val loggedAt: Instant, val score: Int, val note: String = "")

// ---- Templates ----
data class TemplateItem(val title: String, val startTime: LocalTime, val durationMinutes: Int)

data class RoutineTemplate(
    val id: String,
    /** Non-null for bundled templates; UI resolves a localized name. */
    val systemKey: String?,
    val name: String,
    val description: String,
    val kind: RoutineKind,
    val dateRule: DateRule,
    val items: List<TemplateItem>,
)

// ---- Search ----
enum class SearchResultType { TASK, ROUTINE, NOTE, JOURNAL, EVENT, HABIT, REMINDER, MEDICINE, FINANCE }
data class SearchResult(val type: SearchResultType, val id: String, val title: String, val snippet: String)

// ---- Backup ----
enum class BackupKind { EXPORT, IMPORT }
data class BackupRecord(val id: String, val createdAt: Instant, val kind: BackupKind, val encrypted: Boolean, val schemaVersion: Int, val recordCount: Int, val displayName: String)
