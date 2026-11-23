package com.hayat.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hayat.core.model.BackupKind
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.FastingStatus
import com.hayat.core.model.FastingType
import com.hayat.core.model.FinanceKind
import com.hayat.core.model.FinanceRepeat
import com.hayat.core.model.FocusKind
import com.hayat.core.model.HabitKind
import com.hayat.core.model.MealInstruction
import com.hayat.core.model.MealStatus
import com.hayat.core.model.MealType
import com.hayat.core.model.MedicineForm
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ---------------------------------------------------------------- habits
@Entity(tableName = "habits", indices = [Index("deleted_at")])
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val kind: HabitKind,
    val weekdays: Int,
    @ColumnInfo(name = "target_per_day") val targetPerDay: Int,
    val color: Long,
    val icon: String,
    @ColumnInfo(name = "reminder_time") val reminderTime: LocalTime?,
    @ColumnInfo(name = "is_paused") val isPaused: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "habit_completions",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = CASCADE)],
    indices = [Index(value = ["habit_id", "date"], unique = true), Index("date")],
)
data class HabitCompletionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val date: LocalDate,
    val count: Int,
    val note: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

// ---------------------------------------------------------------- water
@Entity(tableName = "water_goals", indices = [Index(value = ["effective_from"], unique = true)])
data class WaterGoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "daily_ml") val dailyMl: Int,
    @ColumnInfo(name = "effective_from") val effectiveFrom: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "water_logs", indices = [Index("date")])
data class WaterLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "amount_ml") val amountMl: Int,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val date: LocalDate,
    val note: String,
)

// ---------------------------------------------------------------- medicine
@Entity(tableName = "medicines", indices = [Index("deleted_at")])
data class MedicineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val dose: String,
    val unit: String,
    val form: MedicineForm,
    @ColumnInfo(name = "meal_instruction") val mealInstruction: MealInstruction,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    val stock: Double?,
    @ColumnInfo(name = "refill_threshold") val refillThreshold: Double?,
    val notes: String,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "medicine_schedules",
    foreignKeys = [ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicine_id"], onDelete = CASCADE)],
    indices = [Index("medicine_id")],
)
data class MedicineScheduleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "medicine_id") val medicineId: String,
    @ColumnInfo(name = "local_time") val time: LocalTime,
    val weekdays: Int,
    val quantity: Double,
)

@Entity(
    tableName = "medicine_dose_logs",
    foreignKeys = [
        ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicine_id"], onDelete = CASCADE),
        ForeignKey(entity = MedicineScheduleEntity::class, parentColumns = ["id"], childColumns = ["schedule_id"], onDelete = SET_NULL),
    ],
    indices = [Index(value = ["medicine_id", "scheduled_date", "scheduled_time"], unique = true), Index("schedule_id"), Index("scheduled_date")],
)
data class MedicineDoseLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "medicine_id") val medicineId: String,
    @ColumnInfo(name = "schedule_id") val scheduleId: String?,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: LocalDate,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: LocalTime,
    val status: DoseStatus,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val note: String,
)

// ---------------------------------------------------------------- diet
@Entity(tableName = "meal_plans", indices = [Index("deleted_at")])
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "meal_type") val mealType: MealType,
    @ColumnInfo(name = "local_time") val time: LocalTime,
    val weekdays: Int,
    val description: String,
    val calories: Int?,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "meal_logs",
    foreignKeys = [ForeignKey(entity = MealPlanEntity::class, parentColumns = ["id"], childColumns = ["meal_plan_id"], onDelete = SET_NULL)],
    indices = [Index("meal_plan_id"), Index("date")],
)
data class MealLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "meal_plan_id") val mealPlanId: String?,
    val date: LocalDate,
    @ColumnInfo(name = "meal_type") val mealType: MealType,
    val description: String,
    val calories: Int?,
    val status: MealStatus,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val quantity: String,
    @ColumnInfo(name = "is_checked") val isChecked: Boolean,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

// ---------------------------------------------------------------- sleep & fitness
@Entity(tableName = "sleep_entries", indices = [Index("date")])
data class SleepEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bed_at") val bedAt: Instant,
    @ColumnInfo(name = "wake_at") val wakeAt: Instant,
    val date: LocalDate,
    val quality: Int?,
    val note: String,
)

@Entity(tableName = "workout_plans", indices = [Index("deleted_at")])
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val weekdays: Int,
    @ColumnInfo(name = "local_time") val time: LocalTime?,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(entity = WorkoutPlanEntity::class, parentColumns = ["id"], childColumns = ["plan_id"], onDelete = CASCADE)],
    indices = [Index("plan_id")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val name: String,
    val sets: Int?,
    val reps: Int?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int?,
    val position: Int,
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [ForeignKey(entity = WorkoutPlanEntity::class, parentColumns = ["id"], childColumns = ["plan_id"], onDelete = SET_NULL)],
    indices = [Index("plan_id"), Index("date")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String?,
    val date: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val steps: Int?,
    val note: String,
)

// ---------------------------------------------------------------- ibadah
@Entity(tableName = "prayer_logs", indices = [Index(value = ["date", "prayer"], unique = true)])
data class PrayerLogEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val prayer: Prayer,
    val status: PrayerStatus,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

@Entity(tableName = "zikr_definitions", indices = [Index(value = ["system_key"], unique = true)])
data class ZikrDefinitionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    @ColumnInfo(name = "target_count") val targetCount: Int,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "system_key") val systemKey: String?,
    val position: Int,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "zikr_progress",
    foreignKeys = [ForeignKey(entity = ZikrDefinitionEntity::class, parentColumns = ["id"], childColumns = ["zikr_id"], onDelete = CASCADE)],
    indices = [Index(value = ["zikr_id", "date"], unique = true), Index("date")],
)
data class ZikrProgressEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "zikr_id") val zikrId: String,
    val date: LocalDate,
    val count: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "quran_logs", indices = [Index("date")])
data class QuranLogEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val surah: Int?,
    @ColumnInfo(name = "from_ayah") val fromAyah: Int?,
    @ColumnInfo(name = "to_ayah") val toAyah: Int?,
    val pages: Double?,
    val minutes: Int?,
    val note: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

@Entity(tableName = "fasting_logs", indices = [Index(value = ["date"], unique = true)])
data class FastingLogEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val type: FastingType,
    val status: FastingStatus,
    val note: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

// ---------------------------------------------------------------- study
@Entity(tableName = "study_subjects", indices = [Index("deleted_at")])
data class StudySubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    @ColumnInfo(name = "weekly_goal_minutes") val weeklyGoalMinutes: Int,
    @ColumnInfo(name = "exam_date") val examDate: LocalDate?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [ForeignKey(entity = StudySubjectEntity::class, parentColumns = ["id"], childColumns = ["subject_id"], onDelete = SET_NULL)],
    indices = [Index("subject_id"), Index("date")],
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "subject_id") val subjectId: String?,
    val kind: FocusKind,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "actual_seconds") val actualSeconds: Long,
    val completed: Boolean,
    val date: LocalDate,
)

// ---------------------------------------------------------------- finance
@Entity(tableName = "finance_items", indices = [Index("due_date"), Index("deleted_at")])
data class FinanceItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: FinanceKind,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    val currency: String,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    val repeat: FinanceRepeat,
    val counterparty: String,
    val note: String,
    @ColumnInfo(name = "is_paid") val isPaid: Boolean,
    @ColumnInfo(name = "reminder_lead_days") val reminderLeadDays: Int?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "finance_payments",
    foreignKeys = [ForeignKey(entity = FinanceItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = CASCADE)],
    indices = [Index("item_id"), Index("paid_on")],
)
data class FinancePaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "paid_on") val paidOn: LocalDate,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long?,
    val note: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
)

// ---------------------------------------------------------------- notes & journal
@Entity(tableName = "notes", indices = [Index("deleted_at"), Index("updated_at")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_checklist") val isChecklist: Boolean,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean,
    val color: Long?,
    @ColumnInfo(name = "reminder_at") val reminderAt: LocalDateTime?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "note_items",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = CASCADE)],
    indices = [Index("note_id")],
)
data class NoteItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "note_id") val noteId: String,
    val text: String,
    @ColumnInfo(name = "is_checked") val isChecked: Boolean,
    val position: Int,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(tableName = "journal_entries", indices = [Index("date"), Index("deleted_at")])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val title: String,
    val body: String,
    val mood: Int?,
    @ColumnInfo(name = "is_locked") val isLocked: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(tableName = "mood_entries", indices = [Index("date")])
data class MoodEntryEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val score: Int,
    val note: String,
)

// ---------------------------------------------------------------- system
@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val kind: BackupKind,
    val encrypted: Boolean,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "record_count") val recordCount: Int,
    @ColumnInfo(name = "display_name") val displayName: String,
)
