package com.hayat.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import java.time.Instant

@Dao
abstract class MaintenanceDao {
    @Query("DELETE FROM tasks WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeTasks(before: Instant): Int
    @Query("DELETE FROM routines WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeRoutines(before: Instant): Int
    @Query("DELETE FROM reminders WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeReminders(before: Instant): Int
    @Query("DELETE FROM habits WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeHabits(before: Instant): Int
    @Query("DELETE FROM medicines WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeMedicines(before: Instant): Int
    @Query("DELETE FROM meal_plans WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeMealPlans(before: Instant): Int
    @Query("DELETE FROM workout_plans WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeWorkoutPlans(before: Instant): Int
    @Query("DELETE FROM study_subjects WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeSubjects(before: Instant): Int
    @Query("DELETE FROM finance_items WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeFinance(before: Instant): Int
    @Query("DELETE FROM calendar_events WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeEvents(before: Instant): Int
    @Query("DELETE FROM notes WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeNotes(before: Instant): Int
    @Query("DELETE FROM journal_entries WHERE deleted_at IS NOT NULL AND deleted_at < :before") abstract suspend fun purgeJournal(before: Instant): Int
    /** Old occurrence history beyond the retention window (final states only). */
    @Query("DELETE FROM reminder_occurrences WHERE scheduled_at < :before AND status IN ('COMPLETED','SKIPPED','MISSED','CANCELLED','DELIVERED')")
    abstract suspend fun purgeOccurrences(before: Instant): Int

    @Transaction
    open suspend fun purgeSoftDeleted(before: Instant): Int =
        purgeTasks(before) + purgeRoutines(before) + purgeReminders(before) + purgeHabits(before) + purgeMedicines(before) +
            purgeMealPlans(before) + purgeWorkoutPlans(before) + purgeSubjects(before) + purgeFinance(before) +
            purgeEvents(before) + purgeNotes(before) + purgeJournal(before)
}
