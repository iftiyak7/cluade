package com.hayat.core.domain.routine

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
import com.hayat.core.domain.repository.RoutineRepository
import com.hayat.core.model.ActivityConflict
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.DateRule
import com.hayat.core.model.LinkedReminderSpec
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Routine
import com.hayat.core.model.RoutineActivity
import com.hayat.core.model.RoutineActivityLog
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.model.Schedule
import com.hayat.core.model.TimeRule
import com.hayat.core.model.TimelineItem
import com.hayat.core.time.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

object RoutineValidator {
    fun validate(routine: Routine): List<ValidationError> = validate {
        text(routine.name, "name", maxLength = Limits.TITLE_MAX)
        text(routine.description, "description", required = false, maxLength = Limits.DESCRIPTION_MAX)
        routine.endDate?.let { e -> require(!e.isBefore(routine.startDate), "endDate", ValidationReason.END_BEFORE_START) }
        val rule = routine.dateRule
        if (rule is DateRule.Weekly) require(!rule.days.isEmpty, "weekdays", ValidationReason.EMPTY_SELECTION)
        routine.activities.forEach {
            text(it.title, "activity", maxLength = Limits.TITLE_MAX)
            range(it.durationMinutes, "duration", 1.0, 24.0 * 60)
        }
    }
}

object RoutinePlanner {
    /** Activities of active routines scheduled on [date], joined with logs, in time order. */
    fun timeline(routines: List<Routine>, logs: List<RoutineActivityLog>, date: LocalDate): List<TimelineItem> {
        val byKey = logs.associateBy { it.activityId to it.date }
        return routines.asSequence()
            .filter { !it.isPaused && !it.isArchived }
            .filter { RecurrenceEngine.occursOn(it.dateRule, it.startDate, it.endDate, date) }
            .flatMap { r -> r.activities.map { a -> TimelineItem(r, a, date, byKey[a.id to date]) } }
            .sortedWith(compareBy<TimelineItem> { it.activity.startTime }.thenBy { it.activity.position })
            .toList()
    }

    /** Pairs of activities that overlap in time. Activities crossing midnight are treated as ending at 24:00. */
    fun conflicts(activities: List<RoutineActivity>): List<ActivityConflict> {
        fun endMinute(a: RoutineActivity) = (a.startTime.toSecondOfDay() / 60 + a.durationMinutes).coerceAtMost(24 * 60)
        val sorted = activities.sortedBy { it.startTime }
        val result = mutableListOf<ActivityConflict>()
        for (i in sorted.indices) for (j in i + 1 until sorted.size) {
            val a = sorted[i]; val b = sorted[j]
            if (b.startTime.toSecondOfDay() / 60 < endMinute(a)) result += ActivityConflict(a, b)
        }
        return result
    }

    fun conflictsOnDate(items: List<TimelineItem>): List<ActivityConflict> = conflicts(items.map { it.activity })

    /** Completion ratio of a day's timeline (completed / scheduled). */
    fun completionRate(items: List<TimelineItem>): Float =
        if (items.isEmpty()) 0f else items.count { it.log?.status == ActivityStatus.COMPLETED }.toFloat() / items.size
}

class SaveRoutineUseCase @Inject constructor(
    private val routines: RoutineRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(routine: Routine): AppResult<Routine> {
        val clean = routine.copy(
            name = routine.name.trim(),
            activities = routine.activities.mapIndexed { i, a -> a.copy(title = a.title.trim(), position = i, routineId = routine.id) },
            updatedAt = time.now(),
        )
        RoutineValidator.validate(clean).toFailureOrNull()?.let { return it }
        return appCatching {
            val previous = routines.getRoutine(clean.id)
            routines.save(clean)
            previous?.activities?.filter { old -> clean.activities.none { it.id == old.id } }
                ?.forEach { linker.remove(ReminderTargetType.ROUTINE_ACTIVITY, it.id) }
            syncReminders(linker, clean)
            clean
        }
    }

    companion object {
        suspend fun syncReminders(linker: ReminderLinker, routine: Routine) {
            val active = !routine.isPaused && !routine.isArchived
            routine.activities.forEach { a ->
                if (active && a.reminderEnabled) {
                    linker.upsert(
                        LinkedReminderSpec(
                            targetType = ReminderTargetType.ROUTINE_ACTIVITY,
                            targetId = a.id,
                            title = a.title,
                            description = routine.name,
                            category = ReminderCategory.ROUTINE,
                            priority = a.priority,
                            schedules = listOf(Schedule(routine.dateRule, TimeRule.At(a.startTime), routine.startDate, routine.endDate)),
                        ),
                    )
                } else {
                    linker.remove(ReminderTargetType.ROUTINE_ACTIVITY, a.id)
                }
            }
        }
    }
}

class RoutineActionsUseCase @Inject constructor(
    private val routines: RoutineRepository,
    private val linker: ReminderLinker,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend fun setPaused(id: String, paused: Boolean) = appCatching {
        routines.setPaused(id, paused, time.now())
        routines.getRoutine(id)?.let { SaveRoutineUseCase.syncReminders(linker, it) }
    }

    suspend fun setArchived(id: String, archived: Boolean) = appCatching {
        routines.setArchived(id, archived, time.now())
        routines.getRoutine(id)?.let { SaveRoutineUseCase.syncReminders(linker, it) }
    }

    suspend fun delete(id: String) = appCatching {
        routines.getRoutine(id)?.activities?.forEach { linker.remove(ReminderTargetType.ROUTINE_ACTIVITY, it.id) }
        routines.softDelete(id, time.now())
    }

    /** Copies a routine with new ids. The copy starts paused-free but without reminders to avoid surprise alarms. */
    suspend fun duplicate(id: String, copySuffix: String): AppResult<Routine?> = appCatching {
        val source = routines.getRoutine(id) ?: return@appCatching null
        val now = time.now()
        val newId = ids.newId()
        val copy = source.copy(
            id = newId,
            name = (source.name + " " + copySuffix).take(Limits.TITLE_MAX),
            activities = source.activities.map { it.copy(id = ids.newId(), routineId = newId, reminderEnabled = false) },
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        routines.save(copy)
        copy
    }

    /** Builds an editable routine from a template. Never overwrites existing routines. */
    suspend fun applyTemplate(template: RoutineTemplate, name: String, startDate: LocalDate, color: Long): AppResult<Routine> = appCatching {
        val now = time.now()
        val routineId = ids.newId()
        val routine = Routine(
            id = routineId,
            name = name,
            description = template.description,
            color = color,
            kind = template.kind,
            dateRule = template.dateRule,
            startDate = startDate,
            activities = template.items.mapIndexed { i, item ->
                RoutineActivity(ids.newId(), routineId, item.title, item.startTime, item.durationMinutes, position = i)
            },
            createdAt = now,
            updatedAt = now,
        )
        routines.save(routine)
        routine
    }

    suspend fun log(activityId: String, date: LocalDate, status: ActivityStatus?, note: String = "") = appCatching {
        routines.setLog(activityId, date, status, note, time.now())
    }
}

/** Reminder "Done" on a routine activity logs COMPLETED for the occurrence's date. */
class RoutineReminderHandler @Inject constructor(private val routines: RoutineRepository, private val time: TimeProvider) : ReminderTargetHandler {
    override val targetType = ReminderTargetType.ROUTINE_ACTIVITY
    override suspend fun onDone(targetId: String, occurrence: ReminderOccurrence) =
        routines.setLog(targetId, occurrence.localDate, ActivityStatus.COMPLETED, "", time.now())
    override suspend fun onSkip(targetId: String, occurrence: ReminderOccurrence) =
        routines.setLog(targetId, occurrence.localDate, ActivityStatus.SKIPPED, "", time.now())
}

fun LocalTime.plusMinutesClamped(minutes: Int): LocalTime =
    if (toSecondOfDay() / 60 + minutes >= 24 * 60) LocalTime.MAX else plusMinutes(minutes.toLong())
