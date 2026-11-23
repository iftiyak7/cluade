package com.hayat.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.DeliveryEvent
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Priority
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderCategory
import com.hayat.core.model.ReminderStyle
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.ZonePolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["request_code"], unique = true), Index(value = ["target_type", "target_id"]), Index(value = ["enabled", "deleted_at"])],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: ReminderCategory,
    @ColumnInfo(name = "target_type") val targetType: ReminderTargetType,
    @ColumnInfo(name = "target_id") val targetId: String?,
    val priority: Priority,
    val enabled: Boolean,
    val style: ReminderStyle,
    val vibrate: Boolean,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int,
    @ColumnInfo(name = "requires_completion") val requiresCompletion: Boolean,
    @ColumnInfo(name = "request_code") val requestCode: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "reminder_schedules",
    foreignKeys = [ForeignKey(entity = ReminderEntity::class, parentColumns = ["id"], childColumns = ["reminder_id"], onDelete = CASCADE)],
    indices = [Index("reminder_id")],
)
data class ReminderScheduleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_id") val reminderId: String,
    /** RecurrenceCodec-encoded DateRule (ONCE, DAILY, WEEKLY, MONTHLY, YEARLY). */
    @ColumnInfo(name = "date_rule") val dateRule: String,
    /** RecurrenceCodec-encoded TimeRule (AT wall-clock times or EVERY elapsed interval). */
    @ColumnInfo(name = "time_rule") val timeRule: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    @ColumnInfo(name = "zone_policy") val zonePolicy: ZonePolicy,
    @ColumnInfo(name = "zone_id") val zoneId: String?,
)

@Entity(
    tableName = "reminder_occurrences",
    foreignKeys = [
        ForeignKey(entity = ReminderEntity::class, parentColumns = ["id"], childColumns = ["reminder_id"], onDelete = CASCADE),
        ForeignKey(entity = ReminderScheduleEntity::class, parentColumns = ["id"], childColumns = ["schedule_id"], onDelete = SET_NULL),
    ],
    indices = [
        Index(value = ["reminder_id", "scheduled_at"], unique = true),
        Index(value = ["status", "scheduled_at"]),
        Index("schedule_id"),
    ],
)
data class ReminderOccurrenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_id") val reminderId: String,
    @ColumnInfo(name = "schedule_id") val scheduleId: String?,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Instant,
    @ColumnInfo(name = "local_date") val localDate: LocalDate,
    @ColumnInfo(name = "local_time") val localTime: LocalTime,
    val status: OccurrenceStatus,
    val precision: AlarmPrecision,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Instant?,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Instant?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "reminder_action_logs",
    foreignKeys = [ForeignKey(entity = ReminderOccurrenceEntity::class, parentColumns = ["id"], childColumns = ["occurrence_id"], onDelete = CASCADE)],
    indices = [Index("occurrence_id")],
)
data class ReminderActionLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    val action: ReminderAction,
    val at: Instant,
    val detail: String,
)

/** Diagnostic log. Holds ids and event types only, never user content. No FK so it survives deletions. */
@Entity(tableName = "alarm_delivery_logs", indices = [Index("at")])
data class AlarmDeliveryLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String?,
    @ColumnInfo(name = "reminder_id") val reminderId: String?,
    val event: DeliveryEvent,
    val at: Instant,
    val detail: String,
)
