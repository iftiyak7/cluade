package com.hayat.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.hayat.core.database.entity.CalendarEventEntity
import com.hayat.core.database.entity.FastingLogEntity
import com.hayat.core.database.entity.FinanceItemEntity
import com.hayat.core.database.entity.FinancePaymentEntity
import com.hayat.core.database.entity.FocusSessionEntity
import com.hayat.core.database.entity.HabitCompletionEntity
import com.hayat.core.database.entity.HabitEntity
import com.hayat.core.database.entity.JournalEntryEntity
import com.hayat.core.database.entity.MealLogEntity
import com.hayat.core.database.entity.MealPlanEntity
import com.hayat.core.database.entity.MedicineDoseLogEntity
import com.hayat.core.database.entity.MedicineEntity
import com.hayat.core.database.entity.MedicineScheduleEntity
import com.hayat.core.database.entity.MoodEntryEntity
import com.hayat.core.database.entity.NoteEntity
import com.hayat.core.database.entity.NoteItemEntity
import com.hayat.core.database.entity.NoteTagCrossRef
import com.hayat.core.database.entity.PrayerLogEntity
import com.hayat.core.database.entity.QuranLogEntity
import com.hayat.core.database.entity.ShoppingItemEntity
import com.hayat.core.database.entity.SleepEntryEntity
import com.hayat.core.database.entity.StudySubjectEntity
import com.hayat.core.database.entity.TagEntity
import com.hayat.core.database.entity.WaterGoalEntity
import com.hayat.core.database.entity.WaterLogEntity
import com.hayat.core.database.entity.WorkoutExerciseEntity
import com.hayat.core.database.entity.WorkoutPlanEntity
import com.hayat.core.database.entity.WorkoutSessionEntity
import com.hayat.core.database.entity.ZikrDefinitionEntity
import com.hayat.core.database.entity.ZikrProgressEntity
import com.hayat.core.model.Prayer
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs WHERE date BETWEEN :from AND :to ORDER BY logged_at DESC") fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<WaterLogEntity>>
    @Query("SELECT * FROM water_goals ORDER BY effective_from") fun observeGoals(): Flow<List<WaterGoalEntity>>
    @Upsert suspend fun upsertLog(e: WaterLogEntity)
    @Query("DELETE FROM water_logs WHERE id = :id") suspend fun deleteLog(id: String)
    @Query("DELETE FROM water_goals WHERE effective_from = :date") suspend fun deleteGoalOn(date: LocalDate)
    @Insert suspend fun insertGoal(e: WaterGoalEntity)
    @Transaction suspend fun replaceGoal(e: WaterGoalEntity) { deleteGoalOn(e.effectiveFrom); insertGoal(e) }
}

data class MedicineWithSchedules(
    @Embedded val medicine: MedicineEntity,
    @Relation(parentColumn = "id", entityColumn = "medicine_id") val schedules: List<MedicineScheduleEntity>,
)

@Dao
interface MedicineDao {
    @Transaction @Query("SELECT * FROM medicines WHERE deleted_at IS NULL ORDER BY is_archived, name") fun observeAll(): Flow<List<MedicineWithSchedules>>
    @Transaction @Query("SELECT * FROM medicines WHERE id = :id AND deleted_at IS NULL") fun observe(id: String): Flow<MedicineWithSchedules?>
    @Transaction @Query("SELECT * FROM medicines WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): MedicineWithSchedules?
    @Upsert suspend fun upsert(e: MedicineEntity)
    @Upsert suspend fun upsertSchedules(list: List<MedicineScheduleEntity>)
    @Query("DELETE FROM medicine_schedules WHERE medicine_id = :id AND id NOT IN (:keep)") suspend fun deleteSchedulesExcept(id: String, keep: List<String>)
    @Query("DELETE FROM medicine_schedules WHERE medicine_id = :id") suspend fun deleteAllSchedules(id: String)
    @Transaction suspend fun save(e: MedicineEntity, schedules: List<MedicineScheduleEntity>) {
        upsert(e)
        if (schedules.isEmpty()) deleteAllSchedules(e.id) else { deleteSchedulesExcept(e.id, schedules.map { it.id }); upsertSchedules(schedules) }
    }
    @Query("UPDATE medicines SET is_archived = :archived, updated_at = :at WHERE id = :id") suspend fun setArchived(id: String, archived: Boolean, at: Instant)
    @Query("UPDATE medicines SET deleted_at = :at, updated_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Instant)
    @Query("UPDATE medicines SET stock = :stock, updated_at = :at WHERE id = :id") suspend fun setStock(id: String, stock: Double?, at: Instant)

    @Query("SELECT * FROM medicine_dose_logs WHERE scheduled_date BETWEEN :from AND :to") fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<MedicineDoseLogEntity>>
    @Query("SELECT * FROM medicine_dose_logs WHERE medicine_id = :id AND scheduled_date = :date AND scheduled_time = :time") suspend fun findLog(id: String, date: LocalDate, time: LocalTime): MedicineDoseLogEntity?
    @Upsert suspend fun upsertLog(e: MedicineDoseLogEntity)
    @Query("DELETE FROM medicine_dose_logs WHERE id = :id") suspend fun deleteLog(id: String)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_plans WHERE deleted_at IS NULL ORDER BY local_time") fun observePlans(): Flow<List<MealPlanEntity>>
    @Query("SELECT * FROM meal_plans WHERE id = :id AND deleted_at IS NULL") suspend fun getPlan(id: String): MealPlanEntity?
    @Upsert suspend fun upsertPlan(e: MealPlanEntity)
    @Query("UPDATE meal_plans SET deleted_at = :at WHERE id = :id") suspend fun softDeletePlan(id: String, at: Instant)
    @Query("SELECT * FROM meal_logs WHERE date BETWEEN :from AND :to ORDER BY logged_at DESC") fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<MealLogEntity>>
    @Upsert suspend fun upsertLog(e: MealLogEntity)
    @Query("DELETE FROM meal_logs WHERE id = :id") suspend fun deleteLog(id: String)
    @Query("SELECT * FROM shopping_items ORDER BY is_checked, position, created_at") fun observeShopping(): Flow<List<ShoppingItemEntity>>
    @Upsert suspend fun upsertShopping(e: ShoppingItemEntity)
    @Query("DELETE FROM shopping_items WHERE id = :id") suspend fun deleteShopping(id: String)
    @Query("DELETE FROM shopping_items WHERE is_checked = 1") suspend fun clearChecked()
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_entries WHERE date BETWEEN :from AND :to ORDER BY bed_at DESC") fun observe(from: LocalDate, to: LocalDate): Flow<List<SleepEntryEntity>>
    @Upsert suspend fun upsert(e: SleepEntryEntity)
    @Query("DELETE FROM sleep_entries WHERE id = :id") suspend fun delete(id: String)
}

data class WorkoutPlanWithExercises(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "plan_id") val exercises: List<WorkoutExerciseEntity>,
)

@Dao
interface FitnessDao {
    @Transaction @Query("SELECT * FROM workout_plans WHERE deleted_at IS NULL ORDER BY name") fun observePlans(): Flow<List<WorkoutPlanWithExercises>>
    @Transaction @Query("SELECT * FROM workout_plans WHERE id = :id AND deleted_at IS NULL") suspend fun getPlan(id: String): WorkoutPlanWithExercises?
    @Upsert suspend fun upsertPlan(e: WorkoutPlanEntity)
    @Query("DELETE FROM workout_exercises WHERE plan_id = :id") suspend fun clearExercises(id: String)
    @Insert suspend fun insertExercises(list: List<WorkoutExerciseEntity>)
    @Transaction suspend fun savePlan(e: WorkoutPlanEntity, ex: List<WorkoutExerciseEntity>) { upsertPlan(e); clearExercises(e.id); if (ex.isNotEmpty()) insertExercises(ex) }
    @Query("UPDATE workout_plans SET deleted_at = :at WHERE id = :id") suspend fun softDeletePlan(id: String, at: Instant)
    @Query("SELECT * FROM workout_sessions WHERE date BETWEEN :from AND :to ORDER BY started_at DESC") fun observeSessions(from: LocalDate, to: LocalDate): Flow<List<WorkoutSessionEntity>>
    @Upsert suspend fun upsertSession(e: WorkoutSessionEntity)
    @Query("DELETE FROM workout_sessions WHERE id = :id") suspend fun deleteSession(id: String)
}

@Dao
interface IbadahDao {
    @Query("SELECT * FROM prayer_logs WHERE date BETWEEN :from AND :to") fun observePrayerLogs(from: LocalDate, to: LocalDate): Flow<List<PrayerLogEntity>>
    @Query("SELECT * FROM prayer_logs WHERE date = :date AND prayer = :prayer") suspend fun findPrayerLog(date: LocalDate, prayer: Prayer): PrayerLogEntity?
    @Upsert suspend fun upsertPrayerLog(e: PrayerLogEntity)
    @Query("DELETE FROM prayer_logs WHERE date = :date AND prayer = :prayer") suspend fun deletePrayerLog(date: LocalDate, prayer: Prayer)

    @Query("SELECT * FROM zikr_definitions WHERE is_archived = 0 ORDER BY is_favorite DESC, position, title") fun observeZikrs(): Flow<List<ZikrDefinitionEntity>>
    @Query("SELECT * FROM zikr_definitions WHERE id = :id") suspend fun getZikr(id: String): ZikrDefinitionEntity?
    @Upsert suspend fun upsertZikr(e: ZikrDefinitionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertZikrsIgnore(list: List<ZikrDefinitionEntity>)
    @Query("DELETE FROM zikr_definitions WHERE id = :id") suspend fun deleteZikr(id: String)

    @Query("SELECT * FROM zikr_progress WHERE date BETWEEN :from AND :to") fun observeZikrProgress(from: LocalDate, to: LocalDate): Flow<List<ZikrProgressEntity>>
    @Query("SELECT * FROM zikr_progress WHERE zikr_id = :zikrId AND date = :date") suspend fun findProgress(zikrId: String, date: LocalDate): ZikrProgressEntity?
    @Upsert suspend fun upsertProgress(e: ZikrProgressEntity)
    @Query("UPDATE zikr_progress SET count = MAX(0, count + :delta), updated_at = :at WHERE zikr_id = :zikrId AND date = :date")
    suspend fun addToProgress(zikrId: String, date: LocalDate, delta: Int, at: Instant): Int

    @Query("SELECT * FROM quran_logs WHERE date BETWEEN :from AND :to ORDER BY logged_at DESC") fun observeQuran(from: LocalDate, to: LocalDate): Flow<List<QuranLogEntity>>
    @Upsert suspend fun upsertQuran(e: QuranLogEntity)
    @Query("DELETE FROM quran_logs WHERE id = :id") suspend fun deleteQuran(id: String)

    @Query("SELECT * FROM fasting_logs WHERE date BETWEEN :from AND :to ORDER BY date") fun observeFasting(from: LocalDate, to: LocalDate): Flow<List<FastingLogEntity>>
    @Query("DELETE FROM fasting_logs WHERE date = :date") suspend fun deleteFasting(date: LocalDate)
    @Insert suspend fun insertFasting(e: FastingLogEntity)
    @Transaction suspend fun replaceFasting(e: FastingLogEntity) { deleteFasting(e.date); insertFasting(e) }
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deleted_at IS NULL ORDER BY is_archived, name") fun observeAll(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habits WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): HabitEntity?
    @Upsert suspend fun upsert(e: HabitEntity)
    @Query("UPDATE habits SET deleted_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Instant)
    @Query("SELECT * FROM habit_completions WHERE date BETWEEN :from AND :to") fun observeCompletions(from: LocalDate, to: LocalDate): Flow<List<HabitCompletionEntity>>
    @Query("SELECT * FROM habit_completions WHERE habit_id = :habitId AND date = :date") suspend fun findCompletion(habitId: String, date: LocalDate): HabitCompletionEntity?
    @Upsert suspend fun upsertCompletion(e: HabitCompletionEntity)
    @Query("DELETE FROM habit_completions WHERE habit_id = :habitId AND date = :date") suspend fun deleteCompletion(habitId: String, date: LocalDate)
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_subjects WHERE deleted_at IS NULL ORDER BY is_archived, name") fun observeSubjects(): Flow<List<StudySubjectEntity>>
    @Upsert suspend fun upsertSubject(e: StudySubjectEntity)
    @Query("UPDATE study_subjects SET deleted_at = :at WHERE id = :id") suspend fun softDeleteSubject(id: String, at: Instant)
    @Query("SELECT * FROM focus_sessions WHERE date BETWEEN :from AND :to ORDER BY started_at DESC") fun observeSessions(from: LocalDate, to: LocalDate): Flow<List<FocusSessionEntity>>
    @Upsert suspend fun upsertSession(e: FocusSessionEntity)
    @Query("DELETE FROM focus_sessions WHERE id = :id") suspend fun deleteSession(id: String)
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_items WHERE deleted_at IS NULL ORDER BY is_paid, due_date") fun observeItems(): Flow<List<FinanceItemEntity>>
    @Query("SELECT * FROM finance_items WHERE id = :id AND deleted_at IS NULL") suspend fun getItem(id: String): FinanceItemEntity?
    @Upsert suspend fun upsertItem(e: FinanceItemEntity)
    @Query("UPDATE finance_items SET deleted_at = :at WHERE id = :id") suspend fun softDeleteItem(id: String, at: Instant)
    @Query("SELECT * FROM finance_payments WHERE item_id = :itemId ORDER BY paid_on DESC") fun observePayments(itemId: String): Flow<List<FinancePaymentEntity>>
    @Query("SELECT * FROM finance_payments WHERE paid_on BETWEEN :from AND :to ORDER BY paid_on DESC") fun observeAllPayments(from: LocalDate, to: LocalDate): Flow<List<FinancePaymentEntity>>
    @Insert suspend fun insertPayment(e: FinancePaymentEntity)
    @Transaction suspend fun recordPayment(p: FinancePaymentEntity, item: FinanceItemEntity) { insertPayment(p); upsertItem(item) }
}

@Dao
interface EventDao {
    @Query("SELECT * FROM calendar_events WHERE deleted_at IS NULL ORDER BY date") fun observeAll(): Flow<List<CalendarEventEntity>>
    @Query("SELECT * FROM calendar_events WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): CalendarEventEntity?
    @Query("SELECT * FROM calendar_events WHERE deleted_at IS NULL AND is_archived = 0 AND is_completed = 0") suspend fun getActive(): List<CalendarEventEntity>
    @Upsert suspend fun upsert(e: CalendarEventEntity)
    @Query("UPDATE calendar_events SET deleted_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Instant)
}

data class NoteWithDetails(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "note_id") val items: List<NoteItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "id", associateBy = Junction(NoteTagCrossRef::class, parentColumn = "note_id", entityColumn = "tag_id"))
    val tags: List<TagEntity>,
)

@Dao
interface NoteDao {
    @Transaction @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY is_pinned DESC, updated_at DESC") fun observeAll(): Flow<List<NoteWithDetails>>
    @Transaction @Query("SELECT * FROM notes WHERE id = :id AND deleted_at IS NULL") fun observe(id: String): Flow<NoteWithDetails?>
    @Transaction @Query("SELECT * FROM notes WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): NoteWithDetails?
    @Upsert suspend fun upsert(e: NoteEntity)
    @Query("DELETE FROM note_items WHERE note_id = :id") suspend fun clearItems(id: String)
    @Insert suspend fun insertItems(list: List<NoteItemEntity>)
    @Query("DELETE FROM note_tags WHERE note_id = :id") suspend fun clearTags(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTags(list: List<NoteTagCrossRef>)
    @Transaction suspend fun save(e: NoteEntity, items: List<NoteItemEntity>, tags: List<NoteTagCrossRef>) {
        upsert(e); clearItems(e.id); if (items.isNotEmpty()) insertItems(items); clearTags(e.id); if (tags.isNotEmpty()) insertTags(tags)
    }
    @Query("UPDATE notes SET deleted_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Instant)
    @Query("UPDATE note_items SET is_checked = :checked WHERE id = :id") suspend fun setItemChecked(id: String, checked: Boolean)

    @Query("SELECT * FROM journal_entries WHERE deleted_at IS NULL ORDER BY date DESC, created_at DESC") fun observeJournal(): Flow<List<JournalEntryEntity>>
    @Query("SELECT * FROM journal_entries WHERE id = :id AND deleted_at IS NULL") suspend fun getJournal(id: String): JournalEntryEntity?
    @Upsert suspend fun upsertJournal(e: JournalEntryEntity)
    @Query("UPDATE journal_entries SET deleted_at = :at WHERE id = :id") suspend fun softDeleteJournal(id: String, at: Instant)

    @Query("SELECT * FROM mood_entries WHERE date BETWEEN :from AND :to ORDER BY logged_at DESC") fun observeMoods(from: LocalDate, to: LocalDate): Flow<List<MoodEntryEntity>>
    @Upsert suspend fun upsertMood(e: MoodEntryEntity)
    @Query("DELETE FROM mood_entries WHERE id = :id") suspend fun deleteMood(id: String)
}

/** Row returned by the cross-table search. */
data class SearchRow(val type: String, val id: String, val title: String, val snippet: String)

@Dao
interface SearchDao {
    /**
     * Substring search across record types (see ADR-9: LIKE instead of FTS for correct Bengali matching).
     * [pattern] must already be escaped and wrapped in %…%. Locked notes/journal entries match on title only and expose no snippet.
     */
    @Query("""
        SELECT 'TASK' AS type, id, title, description AS snippet FROM tasks
          WHERE deleted_at IS NULL AND (title LIKE :pattern ESCAPE '\' OR description LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\')
        UNION ALL SELECT 'ROUTINE', id, name, description FROM routines
          WHERE deleted_at IS NULL AND (name LIKE :pattern ESCAPE '\' OR description LIKE :pattern ESCAPE '\')
        UNION ALL SELECT 'ROUTINE', r.id, r.name, a.title FROM routine_activities a JOIN routines r ON r.id = a.routine_id
          WHERE r.deleted_at IS NULL AND a.title LIKE :pattern ESCAPE '\'
        UNION ALL SELECT 'NOTE', id, title, CASE WHEN is_locked = 1 THEN '' ELSE substr(body, 1, 120) END FROM notes
          WHERE deleted_at IS NULL AND (title LIKE :pattern ESCAPE '\' OR (is_locked = 0 AND body LIKE :pattern ESCAPE '\'))
        UNION ALL SELECT 'JOURNAL', id, title, CASE WHEN is_locked = 1 THEN '' ELSE substr(body, 1, 120) END FROM journal_entries
          WHERE deleted_at IS NULL AND (title LIKE :pattern ESCAPE '\' OR (is_locked = 0 AND body LIKE :pattern ESCAPE '\'))
        UNION ALL SELECT 'EVENT', id, title, notes FROM calendar_events
          WHERE deleted_at IS NULL AND (title LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\' OR custom_label LIKE :pattern ESCAPE '\')
        UNION ALL SELECT 'HABIT', id, name, description FROM habits
          WHERE deleted_at IS NULL AND (name LIKE :pattern ESCAPE '\' OR description LIKE :pattern ESCAPE '\')
        UNION ALL SELECT 'REMINDER', id, title, description FROM reminders
          WHERE deleted_at IS NULL AND target_type = 'NONE' AND (title LIKE :pattern ESCAPE '\' OR description LIKE :pattern ESCAPE '\')
        UNION ALL SELECT 'MEDICINE', id, name, notes FROM medicines
          WHERE deleted_at IS NULL AND (name LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\')
        UNION ALL SELECT 'FINANCE', id, title, counterparty FROM finance_items
          WHERE deleted_at IS NULL AND (title LIKE :pattern ESCAPE '\' OR counterparty LIKE :pattern ESCAPE '\' OR note LIKE :pattern ESCAPE '\')
        LIMIT :limit
    """)
    suspend fun search(pattern: String, limit: Int): List<SearchRow>
}
