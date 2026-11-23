package com.hayat.core.domain.productivity

import com.hayat.core.common.AppResult
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.Limits
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.ValidationReason
import com.hayat.core.common.appCatching
import com.hayat.core.common.toFailureOrNull
import com.hayat.core.common.validate
import com.hayat.core.domain.health.toDateRule
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.reminder.ReminderSyncContributor
import com.hayat.core.domain.reminder.ReminderTargetHandler
import com.hayat.core.domain.repository.EventRepository
import com.hayat.core.domain.repository.FinanceRepository
import com.hayat.core.domain.repository.HabitRepository
import com.hayat.core.domain.repository.NoteRepository
import com.hayat.core.domain.repository.StudyRepository
import com.hayat.core.model.CalendarEvent
import com.hayat.core.model.DateRule
import com.hayat.core.model.EventKind
import com.hayat.core.model.FinanceItem
import com.hayat.core.model.FinancePayment
import com.hayat.core.model.FinanceRepeat
import com.hayat.core.model.FocusKind
import com.hayat.core.model.Habit
import com.hayat.core.model.HabitCompletion
import com.hayat.core.model.HabitStats
import com.hayat.core.model.JournalEntry
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.MonthOverflow
import com.hayat.core.model.Note
import com.hayat.core.model.PomodoroSettings
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Schedule
import com.hayat.core.model.StudySubject
import com.hayat.core.model.TimeRule
import com.hayat.core.time.RecurrenceEngine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

private val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

// ===================================================================== habits

object HabitStatsCalculator {
    /**
     * Streaks count consecutive *scheduled* days that succeeded; unscheduled days are neutral.
     * Today does not break the current streak until it is over (it counts only if already successful).
     * Paused habits keep their history; days while paused are not evaluated (caller passes [pausedDays]).
     * Callers pass [from] no earlier than the date the habit was created.
     */
    fun stats(habit: Habit, completions: List<HabitCompletion>, from: LocalDate, today: LocalDate, pausedDays: Set<LocalDate> = emptySet()): HabitStats {
        val counts = completions.filter { it.habitId == habit.id }.associate { it.date to it.count }
        val start = from
        val scheduled = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
            .filter { it.dayOfWeek in habit.weekdays && it !in pausedDays }.toList()
        val success = scheduled.map { d -> d to habit.isSuccess(counts[d] ?: 0) }
        // Today counts toward history only once it is successful (for BUILD) — REDUCE habits succeed by default until exceeded.
        val evaluated = success.filter { (d, ok) -> d != today || ok }
        var best = 0; var run = 0
        for ((_, ok) in evaluated) { run = if (ok) run + 1 else 0; best = maxOf(best, run) }
        var current = 0
        for ((_, ok) in evaluated.asReversed()) { if (ok) current++ else break }
        val successful = evaluated.count { it.second }
        return HabitStats(current, best, if (evaluated.isEmpty()) 0f else successful.toFloat() / evaluated.size, evaluated.size, successful)
    }
}

class HabitUseCases @Inject constructor(
    private val habits: HabitRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend fun save(habit: Habit): AppResult<Habit> {
        val clean = habit.copy(name = habit.name.trim(), updatedAt = time.now())
        validate {
            text(clean.name, "name", maxLength = Limits.TITLE_MAX)
            text(clean.description, "description", required = false, maxLength = Limits.DESCRIPTION_MAX)
            range(clean.targetPerDay, "target", 0.0, 1000.0)
            require(!clean.weekdays.isEmpty, "weekdays", ValidationReason.EMPTY_SELECTION)
        }.toFailureOrNull()?.let { return it }
        return appCatching { habits.save(clean); syncReminder(clean); clean }
    }

    suspend fun setPaused(habit: Habit, paused: Boolean) = save(habit.copy(isPaused = paused))
    suspend fun setArchived(habit: Habit, archived: Boolean) = save(habit.copy(isArchived = archived))

    suspend fun delete(id: String) = appCatching { linker.remove(ReminderTargetType.HABIT, id); habits.softDelete(id, time.now()) }

    suspend fun increment(habitId: String, date: LocalDate, delta: Int) = appCatching {
        val current = habits.getCount(habitId, date)
        habits.setCount(habitId, date, (current + delta).coerceIn(0, 10_000), time.now())
    }

    private suspend fun syncReminder(h: Habit) {
        val t = h.reminderTime
        if (t == null || h.isPaused || h.isArchived) { linker.remove(ReminderTargetType.HABIT, h.id); return }
        linker.upsert(LinkedReminderSpec(ReminderTargetType.HABIT, h.id, h.name, h.description, ReminderCategory.HABIT,
            listOf(Schedule(h.weekdays.toDateRule(), TimeRule.At(t), LocalDate.of(2020, 1, 1)))))
    }
}

class HabitReminderHandler @Inject constructor(private val useCases: HabitUseCases) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.HABIT
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) { useCases.increment(targetId, occurrence.localDate, 1) }
}

// ===================================================================== finance

object FinanceCalculator {
    fun nextDueDate(item: FinanceItem): LocalDate? = when (item.repeat) {
        FinanceRepeat.NONE -> null
        FinanceRepeat.WEEKLY -> item.dueDate.plusWeeks(1)
        FinanceRepeat.MONTHLY -> RecurrenceEngine.monthlyDate(YearMonth.from(item.dueDate).plusMonths(1), item.dueDate.dayOfMonth, MonthOverflow.CLAMP)
        FinanceRepeat.YEARLY -> item.dueDate.plusYears(1)
    }

    /** Parses a user-entered decimal amount ("1,250.50") into minor units. */
    fun parseAmount(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val bd = cleaned.toBigDecimalOrNull() ?: return null
        if (bd.signum() < 0 || bd.scale() > 2) return null
        return runCatching { bd.movePointRight(2).longValueExact() }.getOrNull()
    }

    fun formatMinor(minor: Long): String = java.math.BigDecimal.valueOf(minor, 2).toPlainString()
}

class FinanceUseCases @Inject constructor(
    private val finance: FinanceRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend fun save(item: FinanceItem): AppResult<FinanceItem> {
        val clean = item.copy(title = item.title.trim(), currency = item.currency.trim().uppercase(), updatedAt = time.now())
        validate {
            text(clean.title, "title", maxLength = Limits.TITLE_MAX)
            text(clean.note, "note", required = false, maxLength = Limits.DESCRIPTION_MAX)
            range(clean.amountMinor, "amount", 0.0, 1e15)
            require(clean.currency.matches(Regex("[A-Z]{3}")), "currency", ValidationReason.INVALID_FORMAT)
            range(clean.reminderLeadDays, "leadDays", 0.0, 60.0)
        }.toFailureOrNull()?.let { return it }
        return appCatching { finance.saveItem(clean); syncReminder(clean); clean }
    }

    /** Marks the current cycle paid: records a payment; repeating items advance to the next due date (unpaid). */
    suspend fun markPaid(item: FinanceItem, paidOn: LocalDate, amountMinor: Long?, note: String = ""): AppResult<FinanceItem> = appCatching {
        val now = time.now()
        val next = FinanceCalculator.nextDueDate(item)
        val updated = if (next != null) item.copy(dueDate = next, isPaid = false, updatedAt = now) else item.copy(isPaid = true, updatedAt = now)
        finance.recordPayment(FinancePayment(ids.newId(), item.id, paidOn, amountMinor ?: item.amountMinor, note, now), updated)
        syncReminder(updated)
        updated
    }

    suspend fun markUnpaid(item: FinanceItem) = save(item.copy(isPaid = false))

    suspend fun delete(id: String) = appCatching { linker.remove(ReminderTargetType.FINANCE, id); finance.deleteItem(id, time.now()) }

    private suspend fun syncReminder(item: FinanceItem) {
        val lead = item.reminderLeadDays
        if (lead == null || item.isPaid || item.isArchived) { linker.remove(ReminderTargetType.FINANCE, item.id); return }
        linker.upsert(LinkedReminderSpec(ReminderTargetType.FINANCE, item.id, item.title, item.counterparty, ReminderCategory.BILL,
            listOf(Schedule(DateRule.Once, TimeRule.At(DEFAULT_REMINDER_TIME), item.dueDate.minusDays(lead.toLong())))))
    }
}

// ===================================================================== events

object EventCalculator {
    /** Next date (>= today) on which the event happens, or null if a one-off event has passed. */
    fun nextOccurrence(event: CalendarEvent, today: LocalDate): LocalDate? {
        if (!event.repeatsAnnually) return event.date.takeIf { !it.isBefore(today) }
        val rule = DateRule.Yearly(event.date.monthValue, event.date.dayOfMonth, MonthOverflow.CLAMP)
        return if (RecurrenceEngine.occursOn(rule, event.date, null, today)) today
        else RecurrenceEngine.nextDate(rule, event.date, null, today)
    }

    /** For birthdays/anniversaries: years since the original date at the next occurrence. */
    fun yearsAt(event: CalendarEvent, occurrence: LocalDate): Int? =
        if (event.repeatsAnnually && (event.kind == EventKind.BIRTHDAY || event.kind == EventKind.ANNIVERSARY)) occurrence.year - event.date.year else null

    fun daysUntil(event: CalendarEvent, today: LocalDate): Long? = nextOccurrence(event, today)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
}

class EventUseCases @Inject constructor(
    private val events: EventRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend fun save(event: CalendarEvent): AppResult<CalendarEvent> {
        val clean = event.copy(title = event.title.trim(), reminderLeadDays = event.reminderLeadDays.distinct().sortedDescending(), updatedAt = time.now())
        validate {
            text(clean.title, "title", maxLength = Limits.TITLE_MAX)
            text(clean.notes, "notes", required = false, maxLength = Limits.DESCRIPTION_MAX)
            text(clean.customLabel, "label", required = false, maxLength = 40)
            clean.reminderLeadDays.forEach { range(it, "leadDays", 0.0, 365.0) }
        }.toFailureOrNull()?.let { return it }
        return appCatching { events.save(clean); sync(linker, clean, time.today()); clean }
    }

    suspend fun delete(id: String) = appCatching { linker.remove(ReminderTargetType.EVENT, id); events.softDelete(id, time.now()) }

    companion object {
        suspend fun sync(linker: ReminderLinker, event: CalendarEvent, today: LocalDate) {
            val next = EventCalculator.nextOccurrence(event, today)
            if (next == null || event.isArchived || event.isCompleted || event.reminderLeadDays.isEmpty()) {
                linker.remove(ReminderTargetType.EVENT, event.id); return
            }
            val time = event.time ?: DEFAULT_REMINDER_TIME
            val schedules = event.reminderLeadDays.map { Schedule(DateRule.Once, TimeRule.At(time), next.minusDays(it.toLong())) }
            linker.upsert(LinkedReminderSpec(ReminderTargetType.EVENT, event.id, event.title, event.notes, ReminderCategory.EVENT, schedules))
        }
    }
}

/** Annual events roll to next year's occurrence after they pass. */
class EventReminderContributor @Inject constructor(
    private val events: EventRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) : ReminderSyncContributor {
    override suspend fun sync(now: Instant) {
        val today = time.today()
        events.getActiveEvents().forEach { EventUseCases.sync(linker, it, today) }
    }
}

// ===================================================================== study & focus

class StudyUseCases @Inject constructor(
    private val study: StudyRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend fun saveSubject(subject: StudySubject): AppResult<StudySubject> {
        val clean = subject.copy(name = subject.name.trim(), updatedAt = time.now())
        validate {
            text(clean.name, "name", maxLength = Limits.TITLE_MAX)
            range(clean.weeklyGoalMinutes, "goal", 0.0, 7.0 * 24 * 60)
        }.toFailureOrNull()?.let { return it }
        return appCatching {
            study.saveSubject(clean)
            val exam = clean.examDate
            if (exam != null && !clean.isArchived && !exam.isBefore(time.today())) {
                linker.upsert(LinkedReminderSpec(ReminderTargetType.STUDY, clean.id, clean.name, category = ReminderCategory.STUDY,
                    schedules = listOf(
                        Schedule(DateRule.Once, TimeRule.At(LocalTime.of(20, 0)), exam.minusDays(1)),
                        Schedule(DateRule.Once, TimeRule.At(LocalTime.of(7, 0)), exam),
                    )))
            } else linker.remove(ReminderTargetType.STUDY, clean.id)
            clean
        }
    }

    suspend fun deleteSubject(id: String) = appCatching { linker.remove(ReminderTargetType.STUDY, id); study.deleteSubject(id, time.now()) }
}

/**
 * Pomodoro state as a pure value. The running timer is defined by its start [Instant] and planned
 * length, so it survives process death (persisted by the caller) and is immune to UI recomposition.
 * Elapsed time uses wall-clock instants persisted at start; the UI ticks with a monotonic clock.
 */
data class FocusTimerState(
    val kind: FocusKind = FocusKind.FOCUS,
    val subjectId: String? = null,
    val startedAt: Instant? = null,
    val pausedElapsed: Duration = Duration.ZERO,
    val plannedMinutes: Int = 25,
    val completedFocusSessions: Int = 0,
) {
    val isRunning: Boolean get() = startedAt != null
    fun elapsed(now: Instant): Duration = pausedElapsed + (startedAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO)
    fun remaining(now: Instant): Duration = Duration.ofMinutes(plannedMinutes.toLong()).minus(elapsed(now)).coerceAtLeast(Duration.ZERO)
    fun isFinished(now: Instant): Boolean = remaining(now).isZero
    fun endsAt(now: Instant): Instant = now.plus(remaining(now))

    fun start(now: Instant) = if (isRunning) this else copy(startedAt = now)
    fun pause(now: Instant) = if (!isRunning) this else copy(pausedElapsed = elapsed(now), startedAt = null)
    fun reset() = copy(startedAt = null, pausedElapsed = Duration.ZERO)

    /** The phase that follows the current one. */
    fun next(settings: PomodoroSettings): FocusTimerState {
        val done = if (kind == FocusKind.FOCUS) completedFocusSessions + 1 else completedFocusSessions
        val nextKind = when {
            kind != FocusKind.FOCUS -> FocusKind.FOCUS
            done % settings.sessionsBeforeLongBreak.coerceAtLeast(1) == 0 -> FocusKind.LONG_BREAK
            else -> FocusKind.SHORT_BREAK
        }
        val minutes = when (nextKind) {
            FocusKind.FOCUS -> settings.focusMinutes
            FocusKind.SHORT_BREAK -> settings.shortBreakMinutes
            FocusKind.LONG_BREAK -> settings.longBreakMinutes
        }
        return FocusTimerState(nextKind, subjectId, null, Duration.ZERO, minutes, done)
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this
}

// ===================================================================== notes & journal

class NoteUseCases @Inject constructor(
    private val notes: NoteRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend fun save(note: Note): AppResult<Note> {
        val clean = note.copy(title = note.title.trim(), updatedAt = time.now())
        validate {
            text(clean.title, "title", required = clean.body.isBlank() && clean.items.isEmpty(), maxLength = Limits.TITLE_MAX)
            text(clean.body, "body", required = false, maxLength = Limits.NOTE_BODY_MAX)
            clean.items.forEach { text(it.text, "item", maxLength = Limits.SHORT_TEXT_MAX) }
        }.toFailureOrNull()?.let { return it }
        return appCatching {
            notes.save(clean)
            val at = clean.reminderAt
            if (at != null && !clean.isArchived && at.isAfter(time.zonedNow().toLocalDateTime())) {
                linker.upsert(LinkedReminderSpec(ReminderTargetType.NOTE, clean.id, clean.title.ifBlank { clean.body.take(60) },
                    // Locked notes never expose their content in notifications.
                    description = if (clean.isLocked) "" else clean.body.take(120),
                    category = ReminderCategory.CUSTOM,
                    schedules = listOf(Schedule(DateRule.Once, TimeRule.At(at.toLocalTime()), at.toLocalDate()))))
            } else linker.remove(ReminderTargetType.NOTE, clean.id)
            clean
        }
    }

    suspend fun delete(id: String) = appCatching { linker.remove(ReminderTargetType.NOTE, id); notes.softDelete(id, time.now()) }

    suspend fun saveJournal(entry: JournalEntry): AppResult<JournalEntry> {
        val clean = entry.copy(title = entry.title.trim(), updatedAt = time.now())
        validate {
            text(clean.body, "body", maxLength = Limits.NOTE_BODY_MAX)
            text(clean.title, "title", required = false, maxLength = Limits.TITLE_MAX)
            range(clean.mood, "mood", 1.0, 5.0)
        }.toFailureOrNull()?.let { return it }
        return appCatching { notes.saveJournal(clean); clean }
    }
}
