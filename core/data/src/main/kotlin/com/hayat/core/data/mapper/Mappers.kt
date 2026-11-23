package com.hayat.core.data.mapper

import com.hayat.core.database.dao.MedicineWithSchedules
import com.hayat.core.database.dao.NoteWithDetails
import com.hayat.core.database.dao.ReminderWithSchedules
import com.hayat.core.database.dao.RoutineWithActivities
import com.hayat.core.database.dao.TaskWithDetails
import com.hayat.core.database.dao.TemplateWithItems
import com.hayat.core.database.dao.WorkoutPlanWithExercises
import com.hayat.core.database.entity.*
import com.hayat.core.model.*
import com.hayat.core.time.RecurrenceCodec
import java.time.ZoneId

// ---------------------------------------------------------------- organisation
fun CategoryEntity.toModel() = Category(id, name, systemKey, color, icon, position)
fun TagEntity.toModel() = Tag(id, name)

fun TaskWithDetails.toModel() = Task(
    id = task.id, title = task.title, description = task.description, categoryId = task.categoryId, priority = task.priority,
    dueDate = task.dueDate, dueTime = task.dueTime, estimatedMinutes = task.estimatedMinutes,
    recurrence = RecurrenceCodec.decodeDateRuleOrNull(task.recurrence), reminderOffsetMinutes = task.reminderOffsetMinutes,
    status = task.status, completedAt = task.completedAt, notes = task.notes, routineId = task.routineId, eventId = task.eventId,
    subtasks = subtasks.sortedBy { it.position }.map { Subtask(it.id, it.title, it.isDone, it.position) },
    tags = tags.map { it.toModel() }.sortedBy { it.name }, isArchived = task.isArchived, createdAt = task.createdAt, updatedAt = task.updatedAt,
)

fun Task.toEntity() = TaskEntity(
    id, title, description, categoryId, priority, dueDate, dueTime, estimatedMinutes, recurrence?.let(RecurrenceCodec::encode),
    reminderOffsetMinutes, status, completedAt, notes, routineId, eventId, isArchived, createdAt, updatedAt,
)

fun RoutineWithActivities.toModel() = Routine(
    id = routine.id, name = routine.name, description = routine.description, color = routine.color, icon = routine.icon, kind = routine.kind,
    dateRule = RecurrenceCodec.decodeDateRuleOrNull(routine.dateRule) ?: DateRule.Daily(),
    startDate = routine.startDate, endDate = routine.endDate, isPaused = routine.isPaused, isArchived = routine.isArchived,
    activities = activities.sortedWith(compareBy({ it.startTime }, { it.position })).map {
        RoutineActivity(it.id, it.routineId, it.title, it.startTime, it.durationMinutes, it.priority, it.position, it.reminderEnabled)
    },
    createdAt = routine.createdAt, updatedAt = routine.updatedAt,
)

fun Routine.toEntity() = RoutineEntity(id, name, description, color, icon, kind, RecurrenceCodec.encode(dateRule), startDate, endDate, isPaused, isArchived, createdAt, updatedAt)
fun RoutineActivity.toEntity() = RoutineActivityEntity(id, routineId, title, startTime, durationMinutes, priority, position, reminderEnabled)
fun RoutineActivityLogEntity.toModel() = RoutineActivityLog(id, activityId, date, status, loggedAt, note)

fun TemplateWithItems.toModel() = RoutineTemplate(
    template.id, null, template.name, template.description, template.kind,
    RecurrenceCodec.decodeDateRuleOrNull(template.dateRule) ?: DateRule.Daily(),
    items.sortedBy { it.position }.map { TemplateItem(it.title, it.startTime, it.durationMinutes) },
)

// ---------------------------------------------------------------- reminders
fun ReminderWithSchedules.toModel() = Reminder(
    id = reminder.id, title = reminder.title, description = reminder.description, category = reminder.category,
    targetType = reminder.targetType, targetId = reminder.targetId, priority = reminder.priority, enabled = reminder.enabled,
    style = reminder.style, vibrate = reminder.vibrate, snoozeMinutes = reminder.snoozeMinutes, requiresCompletion = reminder.requiresCompletion,
    requestCode = reminder.requestCode,
    schedules = schedules.mapNotNull { it.toModel() },
    createdAt = reminder.createdAt, updatedAt = reminder.updatedAt,
)

/** Undecodable rows (should never happen) are skipped rather than crashing the scheduler. */
fun ReminderScheduleEntity.toModel(): ReminderSchedule? = runCatching {
    ReminderSchedule(id, reminderId, Schedule(
        RecurrenceCodec.decodeDateRule(dateRule), RecurrenceCodec.decodeTimeRule(timeRule), startDate, endDate, zonePolicy,
        zoneId?.let { ZoneId.of(it) },
    ))
}.getOrNull()

fun Reminder.toEntity(requestCode: Int) = ReminderEntity(
    id, title, description, category, targetType, targetId, priority, enabled, style, vibrate, snoozeMinutes, requiresCompletion,
    requestCode, createdAt, updatedAt,
)

fun ReminderSchedule.toEntity() = ReminderScheduleEntity(
    id, reminderId, RecurrenceCodec.encode(schedule.dateRule), RecurrenceCodec.encode(schedule.timeRule),
    schedule.startDate, schedule.endDate, schedule.zonePolicy, schedule.zoneId?.id,
)

fun ReminderOccurrenceEntity.toModel() = ReminderOccurrence(
    id, reminderId, scheduleId, scheduledAt, localDate, localTime, status, precision, deliveredAt, completedAt, snoozedUntil, attemptCount, updatedAt,
)

fun ReminderOccurrence.toEntity() = ReminderOccurrenceEntity(
    id, reminderId, scheduleId, scheduledAt, localDate, localTime, status, precision, deliveredAt, completedAt, snoozedUntil, attemptCount, updatedAt,
)

fun AlarmDeliveryLogEntity.toModel() = AlarmDeliveryLog(id, occurrenceId, reminderId, event, at, detail)

// ---------------------------------------------------------------- health
fun WaterLogEntity.toModel() = WaterLog(id, amountMl, loggedAt, date, note)
fun WaterLog.toEntity() = WaterLogEntity(id, amountMl, loggedAt, date, note)
fun WaterGoalEntity.toModel() = WaterGoal(id, dailyMl, effectiveFrom)

fun MedicineWithSchedules.toModel() = Medicine(
    id = medicine.id, name = medicine.name, description = medicine.description, dose = medicine.dose, unit = medicine.unit, form = medicine.form,
    mealInstruction = medicine.mealInstruction, startDate = medicine.startDate, endDate = medicine.endDate, stock = medicine.stock,
    refillThreshold = medicine.refillThreshold, notes = medicine.notes, reminderEnabled = medicine.reminderEnabled, isArchived = medicine.isArchived,
    schedules = schedules.sortedBy { it.time }.map { MedicineSchedule(it.id, it.medicineId, it.time, WeekdaySet(it.weekdays), it.quantity) },
    createdAt = medicine.createdAt, updatedAt = medicine.updatedAt,
)
fun Medicine.toEntity() = MedicineEntity(id, name, description, dose, unit, form, mealInstruction, startDate, endDate, stock, refillThreshold, notes, reminderEnabled, isArchived, createdAt, updatedAt)
fun MedicineSchedule.toEntity() = MedicineScheduleEntity(id, medicineId, time, weekdays.mask, quantity)
fun MedicineDoseLogEntity.toModel() = MedicineDoseLog(id, medicineId, scheduleId, scheduledDate, scheduledTime, status, loggedAt, note)

fun MealPlanEntity.toModel() = MealPlan(id, name, mealType, time, WeekdaySet(weekdays), description, calories, reminderEnabled, isArchived, createdAt, updatedAt)
fun MealPlan.toEntity() = MealPlanEntity(id, name, mealType, time, weekdays.mask, description, calories, reminderEnabled, isArchived, createdAt, updatedAt)
fun MealLogEntity.toModel() = MealLog(id, mealPlanId, date, mealType, description, calories, status, loggedAt)
fun MealLog.toEntity() = MealLogEntity(id, mealPlanId, date, mealType, description, calories, status, loggedAt)
fun ShoppingItemEntity.toModel() = ShoppingItem(id, title, quantity, isChecked, position, createdAt)
fun ShoppingItem.toEntity() = ShoppingItemEntity(id, title, quantity, isChecked, position, createdAt)

fun SleepEntryEntity.toModel() = SleepEntry(id, bedAt, wakeAt, date, quality, note)
fun SleepEntry.toEntity() = SleepEntryEntity(id, bedAt, wakeAt, date, quality, note)

fun WorkoutPlanWithExercises.toModel() = WorkoutPlan(
    plan.id, plan.name, plan.description, WeekdaySet(plan.weekdays), plan.time, plan.reminderEnabled, plan.isArchived,
    exercises.sortedBy { it.position }.map { Exercise(it.id, it.planId, it.name, it.sets, it.reps, it.durationSeconds, it.restSeconds, it.position) },
    plan.createdAt, plan.updatedAt,
)
fun WorkoutPlan.toEntity() = WorkoutPlanEntity(id, name, description, weekdays.mask, time, reminderEnabled, isArchived, createdAt, updatedAt)
fun Exercise.toEntity() = WorkoutExerciseEntity(id, planId, name, sets, reps, durationSeconds, restSeconds, position)
fun WorkoutSessionEntity.toModel() = WorkoutSession(id, planId, date, startedAt, endedAt, durationMinutes, steps, note)
fun WorkoutSession.toEntity() = WorkoutSessionEntity(id, planId, date, startedAt, endedAt, durationMinutes, steps, note)

// ---------------------------------------------------------------- ibadah
fun PrayerLogEntity.toModel() = PrayerLog(id, date, prayer, status, loggedAt)
fun ZikrDefinitionEntity.toModel() = ZikrDefinition(id, title, arabic, transliteration, meaning, targetCount, isFavorite, systemKey, position, isArchived, createdAt, updatedAt)
fun ZikrDefinition.toEntity() = ZikrDefinitionEntity(id, title, arabic, transliteration, meaning, targetCount, isFavorite, systemKey, position, isArchived, createdAt, updatedAt)
fun ZikrProgressEntity.toModel() = ZikrProgress(zikrId, date, count)
fun QuranLogEntity.toModel() = QuranLog(id, date, surah, fromAyah, toAyah, pages, minutes, note, loggedAt)
fun QuranLog.toEntity() = QuranLogEntity(id, date, surah, fromAyah, toAyah, pages, minutes, note, loggedAt)
fun FastingLogEntity.toModel() = FastingLog(id, date, type, status, note)

// ---------------------------------------------------------------- productivity
fun HabitEntity.toModel() = Habit(id, name, description, kind, WeekdaySet(weekdays), targetPerDay, color, icon, reminderTime, isPaused, isArchived, createdAt, updatedAt)
fun Habit.toEntity() = HabitEntity(id, name, description, kind, weekdays.mask, targetPerDay, color, icon, reminderTime, isPaused, isArchived, createdAt, updatedAt)
fun HabitCompletionEntity.toModel() = HabitCompletion(id, habitId, date, count, note)

fun StudySubjectEntity.toModel() = StudySubject(id, name, color, weeklyGoalMinutes, examDate, isArchived, createdAt, updatedAt)
fun StudySubject.toEntity() = StudySubjectEntity(id, name, color, weeklyGoalMinutes, examDate, isArchived, createdAt, updatedAt)
fun FocusSessionEntity.toModel() = FocusSession(id, subjectId, kind, startedAt, endedAt, plannedMinutes, actualSeconds, completed, date)
fun FocusSession.toEntity() = FocusSessionEntity(id, subjectId, kind, startedAt, endedAt, plannedMinutes, actualSeconds, completed, date)

fun FinanceItemEntity.toModel() = FinanceItem(id, title, kind, amountMinor, currency, dueDate, repeat, counterparty, note, isPaid, reminderLeadDays, isArchived, createdAt, updatedAt)
fun FinanceItem.toEntity() = FinanceItemEntity(id, title, kind, amountMinor, currency, dueDate, repeat, counterparty, note, isPaid, reminderLeadDays, isArchived, createdAt, updatedAt)
fun FinancePaymentEntity.toModel() = FinancePayment(id, itemId, paidOn, amountMinor, note, loggedAt)
fun FinancePayment.toEntity() = FinancePaymentEntity(id, itemId, paidOn, amountMinor, note, loggedAt)

fun CalendarEventEntity.toModel() = CalendarEvent(
    id, title, kind, customLabel, date, time, repeatsAnnually,
    leadDays.split(',').mapNotNull { it.trim().toIntOrNull() }, notes, isCompleted, isArchived, createdAt, updatedAt,
)
fun CalendarEvent.toEntity() = CalendarEventEntity(id, title, kind, customLabel, date, time, repeatsAnnually, reminderLeadDays.joinToString(","), notes, isCompleted, isArchived, createdAt, updatedAt)

fun NoteWithDetails.toModel() = Note(
    note.id, note.title, note.body, note.isChecklist, note.isPinned, note.isLocked, note.color, note.reminderAt,
    items.sortedBy { it.position }.map { ChecklistItem(it.id, it.text, it.isChecked, it.position) },
    tags.map { it.toModel() }, note.isArchived, note.createdAt, note.updatedAt,
)
fun Note.toEntity() = NoteEntity(id, title, body, isChecklist, isPinned, isLocked, color, reminderAt, isArchived, createdAt, updatedAt)
fun JournalEntryEntity.toModel() = JournalEntry(id, date, title, body, mood, isLocked, createdAt, updatedAt)
fun JournalEntry.toEntity() = JournalEntryEntity(id, date, title, body, mood, isLocked, createdAt, updatedAt)
fun MoodEntryEntity.toModel() = MoodEntry(id, date, loggedAt, score, note)
fun MoodEntry.toEntity() = MoodEntryEntity(id, date, loggedAt, score, note)
