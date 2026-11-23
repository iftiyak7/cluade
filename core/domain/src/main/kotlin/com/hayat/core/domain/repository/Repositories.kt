package com.hayat.core.domain.repository

import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.AlarmDeliveryLog
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.CalendarEvent
import com.hayat.core.model.Category
import com.hayat.core.model.DeliveryEvent
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.FastingLog
import com.hayat.core.model.FinanceItem
import com.hayat.core.model.FinancePayment
import com.hayat.core.model.FocusSession
import com.hayat.core.model.Habit
import com.hayat.core.model.HabitCompletion
import com.hayat.core.model.JournalEntry
import com.hayat.core.model.MealLog
import com.hayat.core.model.MealPlan
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineDoseLog
import com.hayat.core.model.MoodEntry
import com.hayat.core.model.Note
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerLog
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.QuranLog
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.Routine
import com.hayat.core.model.RoutineActivityLog
import com.hayat.core.model.RoutineTemplate
import com.hayat.core.model.SearchResult
import com.hayat.core.model.ShoppingItem
import com.hayat.core.model.SleepEntry
import com.hayat.core.model.StudySubject
import com.hayat.core.model.Tag
import com.hayat.core.model.Task
import com.hayat.core.model.UserPreferences
import com.hayat.core.model.WaterGoal
import com.hayat.core.model.WaterLog
import com.hayat.core.model.WorkoutPlan
import com.hayat.core.model.WorkoutSession
import com.hayat.core.model.ZikrDefinition
import com.hayat.core.model.ZikrProgress
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun current(): UserPreferences
    /** Atomic read-modify-write. */
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun upsert(category: Category)
    suspend fun delete(id: String)
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    suspend fun getOrCreate(name: String): Tag
}

interface TaskRepository {
    /** All non-deleted tasks (open, completed and archived). */
    fun observeTasks(): Flow<List<Task>>
    fun observeTask(id: String): Flow<Task?>
    suspend fun getTask(id: String): Task?
    /** Upserts the task with its subtasks and tags in one transaction. */
    suspend fun save(task: Task)
    suspend fun setStatusCompleted(id: String, completed: Boolean, at: Instant)
    suspend fun setArchived(ids: Collection<String>, archived: Boolean, at: Instant)
    suspend fun softDelete(ids: Collection<String>, at: Instant)
    suspend fun restore(ids: Collection<String>, at: Instant)
    suspend fun setSubtaskDone(subtaskId: String, done: Boolean, at: Instant)
}

interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    fun observeRoutine(id: String): Flow<Routine?>
    suspend fun getRoutine(id: String): Routine?
    suspend fun findActivityRoutine(activityId: String): Routine?
    suspend fun save(routine: Routine)
    suspend fun setPaused(id: String, paused: Boolean, at: Instant)
    suspend fun setArchived(id: String, archived: Boolean, at: Instant)
    suspend fun softDelete(id: String, at: Instant)
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<RoutineActivityLog>>
    suspend fun setLog(activityId: String, date: LocalDate, status: ActivityStatus?, note: String, at: Instant)
}

interface ReminderRepository {
    fun observeReminders(): Flow<List<Reminder>>
    fun observeReminder(id: String): Flow<Reminder?>
    suspend fun getReminder(id: String): Reminder?
    suspend fun getByRequestCode(requestCode: Int): Reminder?
    suspend fun findByTarget(type: ReminderTargetType, targetId: String): Reminder?
    suspend fun getActiveReminders(): List<Reminder>
    suspend fun getInactiveRequestCodes(): List<Int>
    /** Upserts definition + replaces schedules. Assigns a unique request code when [Reminder.requestCode] is 0. Returns the saved reminder. */
    suspend fun save(reminder: Reminder): Reminder
    suspend fun setEnabled(id: String, enabled: Boolean, at: Instant)
    suspend fun softDelete(id: String, at: Instant)
}

interface OccurrenceRepository {
    /** Inserts occurrences, ignoring ones that already exist for (reminderId, scheduledAt). Returns inserted count. */
    suspend fun insertIfAbsent(occurrences: List<ReminderOccurrence>): Int
    suspend fun get(id: String): ReminderOccurrence?
    /** Earliest SCHEDULED/SNOOZED occurrence by effective time. */
    suspend fun nextPending(reminderId: String): ReminderOccurrence?
    /** SCHEDULED/SNOOZED occurrences whose effective time is <= [until]. */
    suspend fun duePending(reminderId: String, until: Instant): List<ReminderOccurrence>
    /** Atomically moves [ids] from any of [from] to [to]. Returns rows changed (0 for duplicates). */
    suspend fun transition(ids: List<String>, from: Set<OccurrenceStatus>, to: OccurrenceStatus, at: Instant): Int
    suspend fun markDelivered(id: String, at: Instant): Int
    suspend fun markCompleted(id: String, status: OccurrenceStatus, at: Instant): Int
    suspend fun snooze(id: String, until: Instant, at: Instant): Int
    suspend fun setPrecision(id: String, precision: AlarmPrecision)
    /** Deletes SCHEDULED occurrences after [after] (used before regenerating a changed schedule). */
    suspend fun deleteFutureScheduled(reminderId: String, after: Instant): Int
    /** Marks all pending occurrences of the reminder CANCELLED. */
    suspend fun cancelPending(reminderId: String, at: Instant): Int
    /** Pending occurrences whose effective time is before [cutoff]. */
    suspend fun stalePending(cutoff: Instant): List<ReminderOccurrence>
    /** DELIVERED occurrences of reminders that require completion, delivered before [cutoff]. */
    suspend fun staleDeliveredRequiringCompletion(cutoff: Instant): List<ReminderOccurrence>
    suspend fun deliveredBefore(reminderId: String, before: Instant): List<ReminderOccurrence>
    /** Pending or delivered occurrence of [reminderId] on [date] (optionally at [time]). */
    suspend fun findOpen(reminderId: String, date: LocalDate, time: LocalTime?): ReminderOccurrence?
    fun observeUpcoming(from: Instant, limit: Int): Flow<List<ReminderOccurrence>>
    fun observeBetween(from: Instant, to: Instant): Flow<List<ReminderOccurrence>>
    fun observeForReminder(reminderId: String, limit: Int): Flow<List<ReminderOccurrence>>
    suspend fun logAction(occurrenceId: String, action: ReminderAction, at: Instant, detail: String = "")
}

interface DeliveryLogRepository {
    suspend fun log(event: DeliveryEvent, reminderId: String?, occurrenceId: String?, detail: String = "")
    fun observeRecent(limit: Int): Flow<List<AlarmDeliveryLog>>
    suspend fun pruneBefore(cutoff: Instant): Int
}

interface WaterRepository {
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<WaterLog>>
    fun observeGoals(): Flow<List<WaterGoal>>
    suspend fun add(log: WaterLog)
    suspend fun update(log: WaterLog)
    suspend fun delete(id: String)
    suspend fun setGoal(dailyMl: Int, effectiveFrom: LocalDate)
}

interface MedicineRepository {
    fun observeMedicines(): Flow<List<Medicine>>
    fun observeMedicine(id: String): Flow<Medicine?>
    suspend fun getMedicine(id: String): Medicine?
    suspend fun save(medicine: Medicine)
    suspend fun setArchived(id: String, archived: Boolean, at: Instant)
    suspend fun softDelete(id: String, at: Instant)
    fun observeDoseLogs(from: LocalDate, to: LocalDate): Flow<List<MedicineDoseLog>>
    /**
     * Records the status of one scheduled dose (upsert on medicine+date+time) and adjusts stock:
     * entering TAKEN subtracts the schedule quantity, leaving TAKEN adds it back. [status] null deletes the log.
     * Returns the medicine after the stock update.
     */
    suspend fun setDoseStatus(medicineId: String, scheduleId: String?, date: LocalDate, time: LocalTime, status: DoseStatus?, note: String, at: Instant): Medicine?
}

interface MealRepository {
    fun observePlans(): Flow<List<MealPlan>>
    suspend fun getPlan(id: String): MealPlan?
    suspend fun savePlan(plan: MealPlan)
    suspend fun deletePlan(id: String, at: Instant)
    fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<MealLog>>
    suspend fun saveLog(log: MealLog)
    suspend fun deleteLog(id: String)
    fun observeShopping(): Flow<List<ShoppingItem>>
    suspend fun saveShoppingItem(item: ShoppingItem)
    suspend fun deleteShoppingItem(id: String)
    suspend fun clearCheckedShopping()
}

interface SleepRepository {
    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>>
    suspend fun save(entry: SleepEntry)
    suspend fun delete(id: String)
}

interface FitnessRepository {
    fun observePlans(): Flow<List<WorkoutPlan>>
    suspend fun getPlan(id: String): WorkoutPlan?
    suspend fun savePlan(plan: WorkoutPlan)
    suspend fun deletePlan(id: String, at: Instant)
    fun observeSessions(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>>
    suspend fun saveSession(session: WorkoutSession)
    suspend fun deleteSession(id: String)
}

interface IbadahRepository {
    fun observePrayerLogs(from: LocalDate, to: LocalDate): Flow<List<PrayerLog>>
    /** null clears the log for that prayer. */
    suspend fun setPrayerStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus?, at: Instant)
    fun observeZikrs(): Flow<List<ZikrDefinition>>
    suspend fun getZikr(id: String): ZikrDefinition?
    suspend fun saveZikr(zikr: ZikrDefinition)
    suspend fun deleteZikr(id: String)
    /** Ensures bundled presets exist (idempotent, keyed by systemKey). */
    suspend fun ensurePresets(presets: List<ZikrDefinition>)
    fun observeZikrProgress(from: LocalDate, to: LocalDate): Flow<List<ZikrProgress>>
    suspend fun addZikrCount(zikrId: String, date: LocalDate, delta: Int, at: Instant)
    suspend fun setZikrCount(zikrId: String, date: LocalDate, count: Int, at: Instant)
    fun observeQuranLogs(from: LocalDate, to: LocalDate): Flow<List<QuranLog>>
    suspend fun saveQuranLog(log: QuranLog)
    suspend fun deleteQuranLog(id: String)
    fun observeFasting(from: LocalDate, to: LocalDate): Flow<List<FastingLog>>
    suspend fun saveFasting(log: FastingLog)
    suspend fun deleteFasting(date: LocalDate)
}

interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    suspend fun getHabit(id: String): Habit?
    suspend fun save(habit: Habit)
    suspend fun softDelete(id: String, at: Instant)
    fun observeCompletions(from: LocalDate, to: LocalDate): Flow<List<HabitCompletion>>
    suspend fun setCount(habitId: String, date: LocalDate, count: Int, at: Instant)
    suspend fun getCount(habitId: String, date: LocalDate): Int
}

interface StudyRepository {
    fun observeSubjects(): Flow<List<StudySubject>>
    suspend fun saveSubject(subject: StudySubject)
    suspend fun deleteSubject(id: String, at: Instant)
    fun observeSessions(from: LocalDate, to: LocalDate): Flow<List<FocusSession>>
    suspend fun saveSession(session: FocusSession)
    suspend fun deleteSession(id: String)
}

interface FinanceRepository {
    fun observeItems(): Flow<List<FinanceItem>>
    suspend fun getItem(id: String): FinanceItem?
    suspend fun saveItem(item: FinanceItem)
    suspend fun deleteItem(id: String, at: Instant)
    fun observePayments(itemId: String): Flow<List<FinancePayment>>
    fun observeAllPayments(from: LocalDate, to: LocalDate): Flow<List<FinancePayment>>
    /** Records a payment and saves the advanced item in one transaction. */
    suspend fun recordPayment(payment: FinancePayment, updatedItem: FinanceItem)
}

interface EventRepository {
    fun observeEvents(): Flow<List<CalendarEvent>>
    suspend fun getEvent(id: String): CalendarEvent?
    suspend fun getActiveEvents(): List<CalendarEvent>
    suspend fun save(event: CalendarEvent)
    suspend fun softDelete(id: String, at: Instant)
}

interface NoteRepository {
    fun observeNotes(): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>
    suspend fun getNote(id: String): Note?
    suspend fun save(note: Note)
    suspend fun softDelete(id: String, at: Instant)
    suspend fun setChecklistItem(itemId: String, checked: Boolean, at: Instant)
    fun observeJournal(): Flow<List<JournalEntry>>
    suspend fun getJournal(id: String): JournalEntry?
    suspend fun saveJournal(entry: JournalEntry)
    suspend fun deleteJournal(id: String, at: Instant)
    fun observeMoods(from: LocalDate, to: LocalDate): Flow<List<MoodEntry>>
    suspend fun saveMood(entry: MoodEntry)
    suspend fun deleteMood(id: String)
}

interface TemplateRepository {
    fun observeUserTemplates(): Flow<List<RoutineTemplate>>
    suspend fun saveUserTemplate(template: RoutineTemplate)
    suspend fun deleteUserTemplate(id: String)
}

interface SearchRepository {
    suspend fun search(query: String, limitPerType: Int = 20): List<SearchResult>
}

/** Bulk data operations used by "Delete all data" and maintenance. */
interface DataMaintenanceRepository {
    suspend fun purgeSoftDeleted(before: Instant): Int
    suspend fun deleteAllUserData()
}
