package com.hayat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hayat.core.database.dao.BackupDao
import com.hayat.core.database.dao.CategoryDao
import com.hayat.core.database.dao.DeliveryLogDao
import com.hayat.core.database.dao.EventDao
import com.hayat.core.database.dao.FinanceDao
import com.hayat.core.database.dao.FitnessDao
import com.hayat.core.database.dao.HabitDao
import com.hayat.core.database.dao.IbadahDao
import com.hayat.core.database.dao.MaintenanceDao
import com.hayat.core.database.dao.MealDao
import com.hayat.core.database.dao.MedicineDao
import com.hayat.core.database.dao.NoteDao
import com.hayat.core.database.dao.OccurrenceDao
import com.hayat.core.database.dao.ReminderDao
import com.hayat.core.database.dao.RoutineDao
import com.hayat.core.database.dao.SearchDao
import com.hayat.core.database.dao.SleepDao
import com.hayat.core.database.dao.StudyDao
import com.hayat.core.database.dao.TagDao
import com.hayat.core.database.dao.TaskDao
import com.hayat.core.database.dao.TemplateDao
import com.hayat.core.database.dao.WaterDao
import com.hayat.core.database.entity.*

@Database(
    version = HayatDatabase.VERSION,
    exportSchema = true,
    entities = [
        CategoryEntity::class, TagEntity::class, RoutineEntity::class, RoutineActivityEntity::class, RoutineActivityLogEntity::class,
        CalendarEventEntity::class, TaskEntity::class, SubtaskEntity::class, TaskTagCrossRef::class, TemplateEntity::class, TemplateItemEntity::class,
        ReminderEntity::class, ReminderScheduleEntity::class, ReminderOccurrenceEntity::class, ReminderActionLogEntity::class, AlarmDeliveryLogEntity::class,
        HabitEntity::class, HabitCompletionEntity::class, WaterGoalEntity::class, WaterLogEntity::class,
        MedicineEntity::class, MedicineScheduleEntity::class, MedicineDoseLogEntity::class,
        MealPlanEntity::class, MealLogEntity::class, ShoppingItemEntity::class, SleepEntryEntity::class,
        WorkoutPlanEntity::class, WorkoutExerciseEntity::class, WorkoutSessionEntity::class,
        PrayerLogEntity::class, ZikrDefinitionEntity::class, ZikrProgressEntity::class, QuranLogEntity::class, FastingLogEntity::class,
        StudySubjectEntity::class, FocusSessionEntity::class, FinanceItemEntity::class, FinancePaymentEntity::class,
        NoteEntity::class, NoteItemEntity::class, NoteTagCrossRef::class, JournalEntryEntity::class, MoodEntryEntity::class,
        BackupMetadataEntity::class,
        AnnouncementEntity::class, AnnouncementFeedStateEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class HayatDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun taskDao(): TaskDao
    abstract fun routineDao(): RoutineDao
    abstract fun templateDao(): TemplateDao
    abstract fun reminderDao(): ReminderDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun deliveryLogDao(): DeliveryLogDao
    abstract fun waterDao(): WaterDao
    abstract fun medicineDao(): MedicineDao
    abstract fun mealDao(): MealDao
    abstract fun sleepDao(): SleepDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun ibadahDao(): IbadahDao
    abstract fun habitDao(): HabitDao
    abstract fun studyDao(): StudyDao
    abstract fun financeDao(): FinanceDao
    abstract fun eventDao(): EventDao
    abstract fun noteDao(): NoteDao
    abstract fun searchDao(): SearchDao
    abstract fun backupDao(): BackupDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun announcementDao(): com.hayat.core.database.dao.AnnouncementDao

    companion object {
        const val VERSION = 2
        const val NAME = "hayat.db"
    }
}
