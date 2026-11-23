package com.hayat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.hayat.core.database.entity.*

/** Complete snapshot of user data, in FK-safe insert order. Used by backup/restore. */
data class DatabaseSnapshot(
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineActivities: List<RoutineActivityEntity> = emptyList(),
    val routineActivityLogs: List<RoutineActivityLogEntity> = emptyList(),
    val calendarEvents: List<CalendarEventEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val subtasks: List<SubtaskEntity> = emptyList(),
    val taskTags: List<TaskTagCrossRef> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val templateItems: List<TemplateItemEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val reminderSchedules: List<ReminderScheduleEntity> = emptyList(),
    val reminderOccurrences: List<ReminderOccurrenceEntity> = emptyList(),
    val reminderActionLogs: List<ReminderActionLogEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitCompletions: List<HabitCompletionEntity> = emptyList(),
    val waterGoals: List<WaterGoalEntity> = emptyList(),
    val waterLogs: List<WaterLogEntity> = emptyList(),
    val medicines: List<MedicineEntity> = emptyList(),
    val medicineSchedules: List<MedicineScheduleEntity> = emptyList(),
    val medicineDoseLogs: List<MedicineDoseLogEntity> = emptyList(),
    val mealPlans: List<MealPlanEntity> = emptyList(),
    val mealLogs: List<MealLogEntity> = emptyList(),
    val shoppingItems: List<ShoppingItemEntity> = emptyList(),
    val sleepEntries: List<SleepEntryEntity> = emptyList(),
    val workoutPlans: List<WorkoutPlanEntity> = emptyList(),
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val workoutSessions: List<WorkoutSessionEntity> = emptyList(),
    val prayerLogs: List<PrayerLogEntity> = emptyList(),
    val zikrDefinitions: List<ZikrDefinitionEntity> = emptyList(),
    val zikrProgress: List<ZikrProgressEntity> = emptyList(),
    val quranLogs: List<QuranLogEntity> = emptyList(),
    val fastingLogs: List<FastingLogEntity> = emptyList(),
    val studySubjects: List<StudySubjectEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val financeItems: List<FinanceItemEntity> = emptyList(),
    val financePayments: List<FinancePaymentEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val noteItems: List<NoteItemEntity> = emptyList(),
    val noteTags: List<NoteTagCrossRef> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val moodEntries: List<MoodEntryEntity> = emptyList(),
) {
    fun counts(): Map<String, Int> = linkedMapOf(
        "categories" to categories.size,
        "tags" to tags.size,
        "routines" to routines.size,
        "routine_activities" to routineActivities.size,
        "routine_activity_logs" to routineActivityLogs.size,
        "calendar_events" to calendarEvents.size,
        "tasks" to tasks.size,
        "subtasks" to subtasks.size,
        "task_tags" to taskTags.size,
        "templates" to templates.size,
        "template_items" to templateItems.size,
        "reminders" to reminders.size,
        "reminder_schedules" to reminderSchedules.size,
        "reminder_occurrences" to reminderOccurrences.size,
        "reminder_action_logs" to reminderActionLogs.size,
        "habits" to habits.size,
        "habit_completions" to habitCompletions.size,
        "water_goals" to waterGoals.size,
        "water_logs" to waterLogs.size,
        "medicines" to medicines.size,
        "medicine_schedules" to medicineSchedules.size,
        "medicine_dose_logs" to medicineDoseLogs.size,
        "meal_plans" to mealPlans.size,
        "meal_logs" to mealLogs.size,
        "shopping_items" to shoppingItems.size,
        "sleep_entries" to sleepEntries.size,
        "workout_plans" to workoutPlans.size,
        "workout_exercises" to workoutExercises.size,
        "workout_sessions" to workoutSessions.size,
        "prayer_logs" to prayerLogs.size,
        "zikr_definitions" to zikrDefinitions.size,
        "zikr_progress" to zikrProgress.size,
        "quran_logs" to quranLogs.size,
        "fasting_logs" to fastingLogs.size,
        "study_subjects" to studySubjects.size,
        "focus_sessions" to focusSessions.size,
        "finance_items" to financeItems.size,
        "finance_payments" to financePayments.size,
        "notes" to notes.size,
        "note_items" to noteItems.size,
        "note_tags" to noteTags.size,
        "journal_entries" to journalEntries.size,
        "mood_entries" to moodEntries.size,
    )
}

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM categories") abstract suspend fun allCategories(): List<CategoryEntity>
    @Insert abstract suspend fun insertCategories(list: List<CategoryEntity>)
    @Query("SELECT * FROM tags") abstract suspend fun allTags(): List<TagEntity>
    @Insert abstract suspend fun insertTags(list: List<TagEntity>)
    @Query("SELECT * FROM routines") abstract suspend fun allRoutines(): List<RoutineEntity>
    @Insert abstract suspend fun insertRoutines(list: List<RoutineEntity>)
    @Query("SELECT * FROM routine_activities") abstract suspend fun allRoutineActivities(): List<RoutineActivityEntity>
    @Insert abstract suspend fun insertRoutineActivities(list: List<RoutineActivityEntity>)
    @Query("SELECT * FROM routine_activity_logs") abstract suspend fun allRoutineActivityLogs(): List<RoutineActivityLogEntity>
    @Insert abstract suspend fun insertRoutineActivityLogs(list: List<RoutineActivityLogEntity>)
    @Query("SELECT * FROM calendar_events") abstract suspend fun allCalendarEvents(): List<CalendarEventEntity>
    @Insert abstract suspend fun insertCalendarEvents(list: List<CalendarEventEntity>)
    @Query("SELECT * FROM tasks") abstract suspend fun allTasks(): List<TaskEntity>
    @Insert abstract suspend fun insertTasks(list: List<TaskEntity>)
    @Query("SELECT * FROM subtasks") abstract suspend fun allSubtasks(): List<SubtaskEntity>
    @Insert abstract suspend fun insertSubtasks(list: List<SubtaskEntity>)
    @Query("SELECT * FROM task_tags") abstract suspend fun allTaskTags(): List<TaskTagCrossRef>
    @Insert abstract suspend fun insertTaskTags(list: List<TaskTagCrossRef>)
    @Query("SELECT * FROM templates") abstract suspend fun allTemplates(): List<TemplateEntity>
    @Insert abstract suspend fun insertTemplates(list: List<TemplateEntity>)
    @Query("SELECT * FROM template_items") abstract suspend fun allTemplateItems(): List<TemplateItemEntity>
    @Insert abstract suspend fun insertTemplateItems(list: List<TemplateItemEntity>)
    @Query("SELECT * FROM reminders") abstract suspend fun allReminders(): List<ReminderEntity>
    @Insert abstract suspend fun insertReminders(list: List<ReminderEntity>)
    @Query("SELECT * FROM reminder_schedules") abstract suspend fun allReminderSchedules(): List<ReminderScheduleEntity>
    @Insert abstract suspend fun insertReminderSchedules(list: List<ReminderScheduleEntity>)
    @Query("SELECT * FROM reminder_occurrences") abstract suspend fun allReminderOccurrences(): List<ReminderOccurrenceEntity>
    @Insert abstract suspend fun insertReminderOccurrences(list: List<ReminderOccurrenceEntity>)
    @Query("SELECT * FROM reminder_action_logs") abstract suspend fun allReminderActionLogs(): List<ReminderActionLogEntity>
    @Insert abstract suspend fun insertReminderActionLogs(list: List<ReminderActionLogEntity>)
    @Query("SELECT * FROM habits") abstract suspend fun allHabits(): List<HabitEntity>
    @Insert abstract suspend fun insertHabits(list: List<HabitEntity>)
    @Query("SELECT * FROM habit_completions") abstract suspend fun allHabitCompletions(): List<HabitCompletionEntity>
    @Insert abstract suspend fun insertHabitCompletions(list: List<HabitCompletionEntity>)
    @Query("SELECT * FROM water_goals") abstract suspend fun allWaterGoals(): List<WaterGoalEntity>
    @Insert abstract suspend fun insertWaterGoals(list: List<WaterGoalEntity>)
    @Query("SELECT * FROM water_logs") abstract suspend fun allWaterLogs(): List<WaterLogEntity>
    @Insert abstract suspend fun insertWaterLogs(list: List<WaterLogEntity>)
    @Query("SELECT * FROM medicines") abstract suspend fun allMedicines(): List<MedicineEntity>
    @Insert abstract suspend fun insertMedicines(list: List<MedicineEntity>)
    @Query("SELECT * FROM medicine_schedules") abstract suspend fun allMedicineSchedules(): List<MedicineScheduleEntity>
    @Insert abstract suspend fun insertMedicineSchedules(list: List<MedicineScheduleEntity>)
    @Query("SELECT * FROM medicine_dose_logs") abstract suspend fun allMedicineDoseLogs(): List<MedicineDoseLogEntity>
    @Insert abstract suspend fun insertMedicineDoseLogs(list: List<MedicineDoseLogEntity>)
    @Query("SELECT * FROM meal_plans") abstract suspend fun allMealPlans(): List<MealPlanEntity>
    @Insert abstract suspend fun insertMealPlans(list: List<MealPlanEntity>)
    @Query("SELECT * FROM meal_logs") abstract suspend fun allMealLogs(): List<MealLogEntity>
    @Insert abstract suspend fun insertMealLogs(list: List<MealLogEntity>)
    @Query("SELECT * FROM shopping_items") abstract suspend fun allShoppingItems(): List<ShoppingItemEntity>
    @Insert abstract suspend fun insertShoppingItems(list: List<ShoppingItemEntity>)
    @Query("SELECT * FROM sleep_entries") abstract suspend fun allSleepEntries(): List<SleepEntryEntity>
    @Insert abstract suspend fun insertSleepEntries(list: List<SleepEntryEntity>)
    @Query("SELECT * FROM workout_plans") abstract suspend fun allWorkoutPlans(): List<WorkoutPlanEntity>
    @Insert abstract suspend fun insertWorkoutPlans(list: List<WorkoutPlanEntity>)
    @Query("SELECT * FROM workout_exercises") abstract suspend fun allWorkoutExercises(): List<WorkoutExerciseEntity>
    @Insert abstract suspend fun insertWorkoutExercises(list: List<WorkoutExerciseEntity>)
    @Query("SELECT * FROM workout_sessions") abstract suspend fun allWorkoutSessions(): List<WorkoutSessionEntity>
    @Insert abstract suspend fun insertWorkoutSessions(list: List<WorkoutSessionEntity>)
    @Query("SELECT * FROM prayer_logs") abstract suspend fun allPrayerLogs(): List<PrayerLogEntity>
    @Insert abstract suspend fun insertPrayerLogs(list: List<PrayerLogEntity>)
    @Query("SELECT * FROM zikr_definitions") abstract suspend fun allZikrDefinitions(): List<ZikrDefinitionEntity>
    @Insert abstract suspend fun insertZikrDefinitions(list: List<ZikrDefinitionEntity>)
    @Query("SELECT * FROM zikr_progress") abstract suspend fun allZikrProgress(): List<ZikrProgressEntity>
    @Insert abstract suspend fun insertZikrProgress(list: List<ZikrProgressEntity>)
    @Query("SELECT * FROM quran_logs") abstract suspend fun allQuranLogs(): List<QuranLogEntity>
    @Insert abstract suspend fun insertQuranLogs(list: List<QuranLogEntity>)
    @Query("SELECT * FROM fasting_logs") abstract suspend fun allFastingLogs(): List<FastingLogEntity>
    @Insert abstract suspend fun insertFastingLogs(list: List<FastingLogEntity>)
    @Query("SELECT * FROM study_subjects") abstract suspend fun allStudySubjects(): List<StudySubjectEntity>
    @Insert abstract suspend fun insertStudySubjects(list: List<StudySubjectEntity>)
    @Query("SELECT * FROM focus_sessions") abstract suspend fun allFocusSessions(): List<FocusSessionEntity>
    @Insert abstract suspend fun insertFocusSessions(list: List<FocusSessionEntity>)
    @Query("SELECT * FROM finance_items") abstract suspend fun allFinanceItems(): List<FinanceItemEntity>
    @Insert abstract suspend fun insertFinanceItems(list: List<FinanceItemEntity>)
    @Query("SELECT * FROM finance_payments") abstract suspend fun allFinancePayments(): List<FinancePaymentEntity>
    @Insert abstract suspend fun insertFinancePayments(list: List<FinancePaymentEntity>)
    @Query("SELECT * FROM notes") abstract suspend fun allNotes(): List<NoteEntity>
    @Insert abstract suspend fun insertNotes(list: List<NoteEntity>)
    @Query("SELECT * FROM note_items") abstract suspend fun allNoteItems(): List<NoteItemEntity>
    @Insert abstract suspend fun insertNoteItems(list: List<NoteItemEntity>)
    @Query("SELECT * FROM note_tags") abstract suspend fun allNoteTags(): List<NoteTagCrossRef>
    @Insert abstract suspend fun insertNoteTags(list: List<NoteTagCrossRef>)
    @Query("SELECT * FROM journal_entries") abstract suspend fun allJournalEntries(): List<JournalEntryEntity>
    @Insert abstract suspend fun insertJournalEntries(list: List<JournalEntryEntity>)
    @Query("SELECT * FROM mood_entries") abstract suspend fun allMoodEntries(): List<MoodEntryEntity>
    @Insert abstract suspend fun insertMoodEntries(list: List<MoodEntryEntity>)
    @Query("DELETE FROM mood_entries") abstract suspend fun clearMoodEntries()
    @Query("DELETE FROM journal_entries") abstract suspend fun clearJournalEntries()
    @Query("DELETE FROM note_tags") abstract suspend fun clearNoteTags()
    @Query("DELETE FROM note_items") abstract suspend fun clearNoteItems()
    @Query("DELETE FROM notes") abstract suspend fun clearNotes()
    @Query("DELETE FROM finance_payments") abstract suspend fun clearFinancePayments()
    @Query("DELETE FROM finance_items") abstract suspend fun clearFinanceItems()
    @Query("DELETE FROM focus_sessions") abstract suspend fun clearFocusSessions()
    @Query("DELETE FROM study_subjects") abstract suspend fun clearStudySubjects()
    @Query("DELETE FROM fasting_logs") abstract suspend fun clearFastingLogs()
    @Query("DELETE FROM quran_logs") abstract suspend fun clearQuranLogs()
    @Query("DELETE FROM zikr_progress") abstract suspend fun clearZikrProgress()
    @Query("DELETE FROM zikr_definitions") abstract suspend fun clearZikrDefinitions()
    @Query("DELETE FROM prayer_logs") abstract suspend fun clearPrayerLogs()
    @Query("DELETE FROM workout_sessions") abstract suspend fun clearWorkoutSessions()
    @Query("DELETE FROM workout_exercises") abstract suspend fun clearWorkoutExercises()
    @Query("DELETE FROM workout_plans") abstract suspend fun clearWorkoutPlans()
    @Query("DELETE FROM sleep_entries") abstract suspend fun clearSleepEntries()
    @Query("DELETE FROM shopping_items") abstract suspend fun clearShoppingItems()
    @Query("DELETE FROM meal_logs") abstract suspend fun clearMealLogs()
    @Query("DELETE FROM meal_plans") abstract suspend fun clearMealPlans()
    @Query("DELETE FROM medicine_dose_logs") abstract suspend fun clearMedicineDoseLogs()
    @Query("DELETE FROM medicine_schedules") abstract suspend fun clearMedicineSchedules()
    @Query("DELETE FROM medicines") abstract suspend fun clearMedicines()
    @Query("DELETE FROM water_logs") abstract suspend fun clearWaterLogs()
    @Query("DELETE FROM water_goals") abstract suspend fun clearWaterGoals()
    @Query("DELETE FROM habit_completions") abstract suspend fun clearHabitCompletions()
    @Query("DELETE FROM habits") abstract suspend fun clearHabits()
    @Query("DELETE FROM reminder_action_logs") abstract suspend fun clearReminderActionLogs()
    @Query("DELETE FROM reminder_occurrences") abstract suspend fun clearReminderOccurrences()
    @Query("DELETE FROM reminder_schedules") abstract suspend fun clearReminderSchedules()
    @Query("DELETE FROM reminders") abstract suspend fun clearReminders()
    @Query("DELETE FROM template_items") abstract suspend fun clearTemplateItems()
    @Query("DELETE FROM templates") abstract suspend fun clearTemplates()
    @Query("DELETE FROM task_tags") abstract suspend fun clearTaskTags()
    @Query("DELETE FROM subtasks") abstract suspend fun clearSubtasks()
    @Query("DELETE FROM tasks") abstract suspend fun clearTasks()
    @Query("DELETE FROM calendar_events") abstract suspend fun clearCalendarEvents()
    @Query("DELETE FROM routine_activity_logs") abstract suspend fun clearRoutineActivityLogs()
    @Query("DELETE FROM routine_activities") abstract suspend fun clearRoutineActivities()
    @Query("DELETE FROM routines") abstract suspend fun clearRoutines()
    @Query("DELETE FROM tags") abstract suspend fun clearTags()
    @Query("DELETE FROM categories") abstract suspend fun clearCategories()
    @Query("DELETE FROM alarm_delivery_logs") abstract suspend fun clearDeliveryLogs()

    @Transaction
    open suspend fun snapshot(): DatabaseSnapshot = DatabaseSnapshot(
        categories = allCategories(),
        tags = allTags(),
        routines = allRoutines(),
        routineActivities = allRoutineActivities(),
        routineActivityLogs = allRoutineActivityLogs(),
        calendarEvents = allCalendarEvents(),
        tasks = allTasks(),
        subtasks = allSubtasks(),
        taskTags = allTaskTags(),
        templates = allTemplates(),
        templateItems = allTemplateItems(),
        reminders = allReminders(),
        reminderSchedules = allReminderSchedules(),
        reminderOccurrences = allReminderOccurrences(),
        reminderActionLogs = allReminderActionLogs(),
        habits = allHabits(),
        habitCompletions = allHabitCompletions(),
        waterGoals = allWaterGoals(),
        waterLogs = allWaterLogs(),
        medicines = allMedicines(),
        medicineSchedules = allMedicineSchedules(),
        medicineDoseLogs = allMedicineDoseLogs(),
        mealPlans = allMealPlans(),
        mealLogs = allMealLogs(),
        shoppingItems = allShoppingItems(),
        sleepEntries = allSleepEntries(),
        workoutPlans = allWorkoutPlans(),
        workoutExercises = allWorkoutExercises(),
        workoutSessions = allWorkoutSessions(),
        prayerLogs = allPrayerLogs(),
        zikrDefinitions = allZikrDefinitions(),
        zikrProgress = allZikrProgress(),
        quranLogs = allQuranLogs(),
        fastingLogs = allFastingLogs(),
        studySubjects = allStudySubjects(),
        focusSessions = allFocusSessions(),
        financeItems = allFinanceItems(),
        financePayments = allFinancePayments(),
        notes = allNotes(),
        noteItems = allNoteItems(),
        noteTags = allNoteTags(),
        journalEntries = allJournalEntries(),
        moodEntries = allMoodEntries(),
    )

    /** Deletes all user data (children first). */
    @Transaction
    open suspend fun clearAll() {
        clearMoodEntries()
        clearJournalEntries()
        clearNoteTags()
        clearNoteItems()
        clearNotes()
        clearFinancePayments()
        clearFinanceItems()
        clearFocusSessions()
        clearStudySubjects()
        clearFastingLogs()
        clearQuranLogs()
        clearZikrProgress()
        clearZikrDefinitions()
        clearPrayerLogs()
        clearWorkoutSessions()
        clearWorkoutExercises()
        clearWorkoutPlans()
        clearSleepEntries()
        clearShoppingItems()
        clearMealLogs()
        clearMealPlans()
        clearMedicineDoseLogs()
        clearMedicineSchedules()
        clearMedicines()
        clearWaterLogs()
        clearWaterGoals()
        clearHabitCompletions()
        clearHabits()
        clearReminderActionLogs()
        clearReminderOccurrences()
        clearReminderSchedules()
        clearReminders()
        clearTemplateItems()
        clearTemplates()
        clearTaskTags()
        clearSubtasks()
        clearTasks()
        clearCalendarEvents()
        clearRoutineActivityLogs()
        clearRoutineActivities()
        clearRoutines()
        clearTags()
        clearCategories()
        clearDeliveryLogs()
    }

    /** Replaces all user data with [s] atomically. Any constraint violation rolls the whole restore back. */
    @Transaction
    open suspend fun replaceAll(s: DatabaseSnapshot) {
        clearAll()
        if (s.categories.isNotEmpty()) insertCategories(s.categories)
        if (s.tags.isNotEmpty()) insertTags(s.tags)
        if (s.routines.isNotEmpty()) insertRoutines(s.routines)
        if (s.routineActivities.isNotEmpty()) insertRoutineActivities(s.routineActivities)
        if (s.routineActivityLogs.isNotEmpty()) insertRoutineActivityLogs(s.routineActivityLogs)
        if (s.calendarEvents.isNotEmpty()) insertCalendarEvents(s.calendarEvents)
        if (s.tasks.isNotEmpty()) insertTasks(s.tasks)
        if (s.subtasks.isNotEmpty()) insertSubtasks(s.subtasks)
        if (s.taskTags.isNotEmpty()) insertTaskTags(s.taskTags)
        if (s.templates.isNotEmpty()) insertTemplates(s.templates)
        if (s.templateItems.isNotEmpty()) insertTemplateItems(s.templateItems)
        if (s.reminders.isNotEmpty()) insertReminders(s.reminders)
        if (s.reminderSchedules.isNotEmpty()) insertReminderSchedules(s.reminderSchedules)
        if (s.reminderOccurrences.isNotEmpty()) insertReminderOccurrences(s.reminderOccurrences)
        if (s.reminderActionLogs.isNotEmpty()) insertReminderActionLogs(s.reminderActionLogs)
        if (s.habits.isNotEmpty()) insertHabits(s.habits)
        if (s.habitCompletions.isNotEmpty()) insertHabitCompletions(s.habitCompletions)
        if (s.waterGoals.isNotEmpty()) insertWaterGoals(s.waterGoals)
        if (s.waterLogs.isNotEmpty()) insertWaterLogs(s.waterLogs)
        if (s.medicines.isNotEmpty()) insertMedicines(s.medicines)
        if (s.medicineSchedules.isNotEmpty()) insertMedicineSchedules(s.medicineSchedules)
        if (s.medicineDoseLogs.isNotEmpty()) insertMedicineDoseLogs(s.medicineDoseLogs)
        if (s.mealPlans.isNotEmpty()) insertMealPlans(s.mealPlans)
        if (s.mealLogs.isNotEmpty()) insertMealLogs(s.mealLogs)
        if (s.shoppingItems.isNotEmpty()) insertShoppingItems(s.shoppingItems)
        if (s.sleepEntries.isNotEmpty()) insertSleepEntries(s.sleepEntries)
        if (s.workoutPlans.isNotEmpty()) insertWorkoutPlans(s.workoutPlans)
        if (s.workoutExercises.isNotEmpty()) insertWorkoutExercises(s.workoutExercises)
        if (s.workoutSessions.isNotEmpty()) insertWorkoutSessions(s.workoutSessions)
        if (s.prayerLogs.isNotEmpty()) insertPrayerLogs(s.prayerLogs)
        if (s.zikrDefinitions.isNotEmpty()) insertZikrDefinitions(s.zikrDefinitions)
        if (s.zikrProgress.isNotEmpty()) insertZikrProgress(s.zikrProgress)
        if (s.quranLogs.isNotEmpty()) insertQuranLogs(s.quranLogs)
        if (s.fastingLogs.isNotEmpty()) insertFastingLogs(s.fastingLogs)
        if (s.studySubjects.isNotEmpty()) insertStudySubjects(s.studySubjects)
        if (s.focusSessions.isNotEmpty()) insertFocusSessions(s.focusSessions)
        if (s.financeItems.isNotEmpty()) insertFinanceItems(s.financeItems)
        if (s.financePayments.isNotEmpty()) insertFinancePayments(s.financePayments)
        if (s.notes.isNotEmpty()) insertNotes(s.notes)
        if (s.noteItems.isNotEmpty()) insertNoteItems(s.noteItems)
        if (s.noteTags.isNotEmpty()) insertNoteTags(s.noteTags)
        if (s.journalEntries.isNotEmpty()) insertJournalEntries(s.journalEntries)
        if (s.moodEntries.isNotEmpty()) insertMoodEntries(s.moodEntries)
    }
}
