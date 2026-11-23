package com.hayat.core.domain.analytics

import com.hayat.core.domain.health.MedicinePlanner
import com.hayat.core.domain.health.WaterCalculator
import com.hayat.core.domain.productivity.HabitStatsCalculator
import com.hayat.core.domain.routine.RoutinePlanner
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.FocusKind
import com.hayat.core.model.FocusSession
import com.hayat.core.model.Habit
import com.hayat.core.model.HabitCompletion
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineDoseLog
import com.hayat.core.model.PrayerLog
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.QuranLog
import com.hayat.core.model.Routine
import com.hayat.core.model.RoutineActivityLog
import com.hayat.core.model.Task
import com.hayat.core.model.WaterGoal
import com.hayat.core.model.WaterLog
import com.hayat.core.model.WorkoutSession
import com.hayat.core.model.ZikrProgress
import java.time.LocalDate

/** A ratio with its raw counts so the UI can say "7 of 10" rather than only a percentage. */
data class Ratio(val done: Int, val total: Int) {
    val value: Float get() = if (total == 0) 0f else done.toFloat() / total
    val isEmpty: Boolean get() = total == 0
}

data class DayValue(val date: LocalDate, val value: Float)

data class AnalyticsSnapshot(
    val from: LocalDate,
    val to: LocalDate,
    val tasks: Ratio,
    val routines: Ratio,
    val waterGoalDays: Ratio,
    val waterDaily: List<DayValue>,
    val habits: Ratio,
    val prayers: Ratio,
    val medication: Ratio,
    val studyMinutes: Int,
    val studyDaily: List<DayValue>,
    val workouts: Int,
    val workoutMinutes: Int,
    val zikrTotal: Int,
    val quranPages: Double,
    /** Number of recorded actions per day (completions, logs), used for the activity heatmap. */
    val activityByDay: List<DayValue>,
)

data class AnalyticsInput(
    val tasks: List<Task> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val routineLogs: List<RoutineActivityLog> = emptyList(),
    val waterLogs: List<WaterLog> = emptyList(),
    val waterGoals: List<WaterGoal> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitCompletions: List<HabitCompletion> = emptyList(),
    val prayerLogs: List<PrayerLog> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val doseLogs: List<MedicineDoseLog> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val workouts: List<WorkoutSession> = emptyList(),
    val zikr: List<ZikrProgress> = emptyList(),
    val quran: List<QuranLog> = emptyList(),
)

/**
 * Local analytics. Everything is computed from persisted records on the device; nothing is
 * transmitted. Days after [today] are never counted as failures.
 */
object AnalyticsCalculator {
    fun compute(input: AnalyticsInput, from: LocalDate, to: LocalDate, today: LocalDate, zone: java.time.ZoneId): AnalyticsSnapshot {
        val end = minOf(to, today)
        val days = if (end.isBefore(from)) emptyList() else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

        val dueTasks = input.tasks.filter { !it.isArchived && it.dueDate?.let { d -> d in from..end } == true }
        val tasks = Ratio(dueTasks.count { it.isCompleted }, dueTasks.size)

        val timeline = days.flatMap { RoutinePlanner.timeline(input.routines, input.routineLogs, it) }
        val routines = Ratio(timeline.count { it.log?.status == ActivityStatus.COMPLETED }, timeline.size)

        val water = if (days.isEmpty()) emptyList() else WaterCalculator.days(from, end, input.waterLogs, input.waterGoals)
        val waterGoalDays = Ratio(water.count { it.totalMl >= it.goalMl }, water.size)

        val habitRatios = input.habits.filter { !it.isArchived }.map {
            val s = HabitStatsCalculator.stats(it, input.habitCompletions, maxOf(from, it.createdAt.atZone(zone).toLocalDate()), end)
            Ratio(s.successfulDays, s.scheduledDays)
        }
        val habits = Ratio(habitRatios.sumOf { it.done }, habitRatios.sumOf { it.total })

        val prayerLogsInRange = input.prayerLogs.filter { it.date in from..end }
        val prayed = prayerLogsInRange.count { it.status == PrayerStatus.PRAYED || it.status == PrayerStatus.PRAYED_LATE || it.status == PrayerStatus.QADA }
        val prayers = Ratio(prayed, prayerLogsInRange.count { it.status != PrayerStatus.EXCUSED })

        val doses = days.flatMap { MedicinePlanner.dosesFor(it, input.medicines, input.doseLogs) }
        val medication = Ratio(doses.count { it.log?.status == DoseStatus.TAKEN }, doses.size)

        val focus = input.focusSessions.filter { it.kind == FocusKind.FOCUS && it.date in from..end }
        val studyByDay = focus.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.actualSeconds } / 60f }
        val workouts = input.workouts.filter { it.date in from..end }

        val activity = mutableMapOf<LocalDate, Int>()
        fun bump(d: LocalDate?) { if (d != null && d in from..end) activity[d] = (activity[d] ?: 0) + 1 }
        input.tasks.filter { it.isCompleted }.forEach { bump(it.completedAt?.atZone(zone)?.toLocalDate()) }
        input.routineLogs.filter { it.status == ActivityStatus.COMPLETED }.forEach { bump(it.date) }
        input.habitCompletions.filter { it.count > 0 }.forEach { bump(it.date) }
        input.waterLogs.forEach { bump(it.date) }
        input.doseLogs.filter { it.status == DoseStatus.TAKEN }.forEach { bump(it.scheduledDate) }
        prayerLogsInRange.forEach { bump(it.date) }
        workouts.forEach { bump(it.date) }
        focus.forEach { bump(it.date) }

        return AnalyticsSnapshot(
            from = from, to = to,
            tasks = tasks, routines = routines, waterGoalDays = waterGoalDays,
            waterDaily = water.map { DayValue(it.date, it.totalMl.toFloat()) },
            habits = habits, prayers = prayers, medication = medication,
            studyMinutes = (focus.sumOf { it.actualSeconds } / 60).toInt(),
            studyDaily = days.map { DayValue(it, studyByDay[it] ?: 0f) },
            workouts = workouts.size, workoutMinutes = workouts.sumOf { it.durationMinutes },
            zikrTotal = input.zikr.filter { it.date in from..end }.sumOf { it.count },
            quranPages = input.quran.filter { it.date in from..end }.sumOf { it.pages ?: 0.0 },
            activityByDay = days.map { DayValue(it, (activity[it] ?: 0).toFloat()) },
        )
    }
}
