package com.hayat.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// ---- Water ----
data class WaterLog(val id: String, val amountMl: Int, val loggedAt: Instant, val date: LocalDate, val note: String = "")
data class WaterGoal(val id: String, val dailyMl: Int, val effectiveFrom: LocalDate)
data class WaterDay(val date: LocalDate, val totalMl: Int, val goalMl: Int) {
    val progress: Float get() = if (goalMl <= 0) 0f else (totalMl.toFloat() / goalMl).coerceIn(0f, 1f)
}

// ---- Medicine ----
enum class MedicineForm { TABLET, CAPSULE, SYRUP, INJECTION, DROPS, INHALER, CREAM, POWDER, OTHER }
enum class MealInstruction { NONE, BEFORE_MEAL, WITH_MEAL, AFTER_MEAL, EMPTY_STOMACH }
enum class DoseStatus { TAKEN, SKIPPED, MISSED, POSTPONED }

data class MedicineSchedule(
    val id: String,
    val medicineId: String,
    val time: LocalTime,
    val weekdays: WeekdaySet = WeekdaySet.ALL,
    /** Units consumed per dose, used to decrement stock (e.g. 1 tablet). */
    val quantity: Double = 1.0,
)

data class Medicine(
    val id: String,
    val name: String,
    val description: String = "",
    /** Exactly as entered by the user, e.g. "500". Hayat never changes it. */
    val dose: String = "",
    val unit: String = "",
    val form: MedicineForm = MedicineForm.TABLET,
    val mealInstruction: MealInstruction = MealInstruction.NONE,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val stock: Double? = null,
    val refillThreshold: Double? = null,
    val notes: String = "",
    val reminderEnabled: Boolean = true,
    val isArchived: Boolean = false,
    val schedules: List<MedicineSchedule> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val needsRefill: Boolean get() = stock != null && refillThreshold != null && stock <= refillThreshold
    fun isActiveOn(date: LocalDate): Boolean = !isArchived && !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))
}

data class MedicineDoseLog(
    val id: String,
    val medicineId: String,
    val scheduleId: String?,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val status: DoseStatus,
    val loggedAt: Instant,
    val note: String = "",
)

/** A computed dose slot for a day, with its log if the user recorded one. */
data class MedicineDose(
    val medicine: Medicine,
    val schedule: MedicineSchedule,
    val date: LocalDate,
    val log: MedicineDoseLog?,
)

// ---- Diet ----
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, CUSTOM }
enum class MealStatus { EATEN, SKIPPED }

data class MealPlan(
    val id: String,
    val name: String,
    val mealType: MealType,
    val time: LocalTime,
    val weekdays: WeekdaySet = WeekdaySet.ALL,
    val description: String = "",
    /** Optional, user-entered only. Hayat never invents nutritional values. */
    val calories: Int? = null,
    val reminderEnabled: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MealLog(
    val id: String,
    val mealPlanId: String?,
    val date: LocalDate,
    val mealType: MealType,
    val description: String,
    val calories: Int?,
    val status: MealStatus,
    val loggedAt: Instant,
)

data class ShoppingItem(val id: String, val title: String, val quantity: String = "", val isChecked: Boolean = false, val position: Int = 0, val createdAt: Instant)

// ---- Sleep ----
data class SleepEntry(
    val id: String,
    val bedAt: Instant,
    val wakeAt: Instant,
    /** Local date of waking up; used for grouping. */
    val date: LocalDate,
    val quality: Int? = null,
    val note: String = "",
) {
    val duration: Duration get() = Duration.between(bedAt, wakeAt)
}

// ---- Fitness ----
data class Exercise(
    val id: String,
    val planId: String,
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val restSeconds: Int? = null,
    val position: Int = 0,
)

data class WorkoutPlan(
    val id: String,
    val name: String,
    val description: String = "",
    val weekdays: WeekdaySet = WeekdaySet.NONE,
    val time: LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val isArchived: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkoutSession(
    val id: String,
    val planId: String?,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationMinutes: Int,
    val steps: Int? = null,
    val note: String = "",
)
