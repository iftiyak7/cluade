package com.hayat.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hayat.core.model.ActivityStatus
import com.hayat.core.model.Priority
import com.hayat.core.model.RoutineKind
import com.hayat.core.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "categories", indices = [Index(value = ["system_key"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "system_key") val systemKey: String?,
    val color: Long,
    val icon: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "routines",
    indices = [Index("deleted_at")],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val color: Long,
    val icon: String,
    val kind: RoutineKind,
    /** RecurrenceCodec-encoded DateRule. */
    @ColumnInfo(name = "date_rule") val dateRule: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    @ColumnInfo(name = "is_paused") val isPaused: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "routine_activities",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = CASCADE)],
    indices = [Index("routine_id")],
)
data class RoutineActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    val title: String,
    @ColumnInfo(name = "start_time") val startTime: LocalTime,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val priority: Priority,
    val position: Int,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
)

@Entity(
    tableName = "routine_activity_logs",
    foreignKeys = [ForeignKey(entity = RoutineActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = CASCADE)],
    indices = [Index(value = ["activity_id", "date"], unique = true), Index("date")],
)
data class RoutineActivityLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    val date: LocalDate,
    val status: ActivityStatus,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val note: String,
)

@Entity(tableName = "calendar_events", indices = [Index("date"), Index("deleted_at")])
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: com.hayat.core.model.EventKind,
    @ColumnInfo(name = "custom_label") val customLabel: String,
    val date: LocalDate,
    @ColumnInfo(name = "local_time") val time: LocalTime?,
    @ColumnInfo(name = "repeats_annually") val repeatsAnnually: Boolean,
    /** Comma separated lead days, e.g. "7,1,0". */
    @ColumnInfo(name = "lead_days") val leadDays: String,
    val notes: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = SET_NULL),
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = SET_NULL),
        ForeignKey(entity = CalendarEventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = SET_NULL),
    ],
    indices = [Index("category_id"), Index("routine_id"), Index("event_id"), Index("due_date"), Index(value = ["status", "deleted_at"])],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val priority: Priority,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate?,
    @ColumnInfo(name = "due_time") val dueTime: LocalTime?,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int?,
    /** RecurrenceCodec-encoded DateRule, null = one-off. */
    val recurrence: String?,
    @ColumnInfo(name = "reminder_offset_minutes") val reminderOffsetMinutes: Int?,
    val status: TaskStatus,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    val notes: String,
    @ColumnInfo(name = "routine_id") val routineId: String?,
    @ColumnInfo(name = "event_id") val eventId: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"], onDelete = CASCADE)],
    indices = [Index("task_id")],
)
data class SubtaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    val title: String,
    @ColumnInfo(name = "is_done") val isDone: Boolean,
    val position: Int,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["task_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"], onDelete = CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class TaskTagCrossRef(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val kind: RoutineKind,
    @ColumnInfo(name = "date_rule") val dateRule: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "template_items",
    foreignKeys = [ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["template_id"], onDelete = CASCADE)],
    indices = [Index("template_id")],
)
data class TemplateItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "template_id") val templateId: String,
    val title: String,
    @ColumnInfo(name = "start_time") val startTime: LocalTime,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val position: Int,
)
