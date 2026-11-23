package com.hayat.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class RoutineKind { GENERAL, WORKDAY, FRIDAY, HOLIDAY, RAMADAN, STUDY, FITNESS }

/** Completion states of a routine activity on a day. A dismissed alarm is none of these. */
enum class ActivityStatus { COMPLETED, SKIPPED, POSTPONED, MISSED }

data class RoutineActivity(
    val id: String,
    val routineId: String,
    val title: String,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val priority: Priority = Priority.MEDIUM,
    val position: Int = 0,
    val reminderEnabled: Boolean = false,
) {
    val endTime: LocalTime get() = startTime.plusMinutes(durationMinutes.toLong())
}

data class Routine(
    val id: String,
    val name: String,
    val description: String = "",
    val color: Long,
    val icon: String = "routine",
    val kind: RoutineKind = RoutineKind.GENERAL,
    val dateRule: DateRule = DateRule.Daily(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val activities: List<RoutineActivity> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val startTime: LocalTime? get() = activities.minOfOrNull { it.startTime }
    val endTime: LocalTime? get() = activities.maxOfOrNull { it.endTime }
}

data class RoutineActivityLog(
    val id: String,
    val activityId: String,
    val date: LocalDate,
    val status: ActivityStatus,
    val loggedAt: Instant,
    val note: String = "",
)

/** An activity placed on a specific date, joined with its log (if any). */
data class TimelineItem(
    val routine: Routine,
    val activity: RoutineActivity,
    val date: LocalDate,
    val log: RoutineActivityLog?,
)

/** Two activities overlapping in time on the same date. */
data class ActivityConflict(val first: RoutineActivity, val second: RoutineActivity)
