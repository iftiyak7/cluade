package com.hayat.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.hayat.core.database.entity.AlarmDeliveryLogEntity
import com.hayat.core.database.entity.ReminderActionLogEntity
import com.hayat.core.database.entity.ReminderEntity
import com.hayat.core.database.entity.ReminderOccurrenceEntity
import com.hayat.core.database.entity.ReminderScheduleEntity
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.ReminderTargetType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ReminderWithSchedules(
    @Embedded val reminder: ReminderEntity,
    @Relation(parentColumn = "id", entityColumn = "reminder_id") val schedules: List<ReminderScheduleEntity>,
)

@Dao
interface ReminderDao {
    @Transaction @Query("SELECT * FROM reminders WHERE deleted_at IS NULL ORDER BY title") fun observeAll(): Flow<List<ReminderWithSchedules>>
    @Transaction @Query("SELECT * FROM reminders WHERE id = :id AND deleted_at IS NULL") fun observe(id: String): Flow<ReminderWithSchedules?>
    @Transaction @Query("SELECT * FROM reminders WHERE id = :id AND deleted_at IS NULL") suspend fun get(id: String): ReminderWithSchedules?
    @Transaction @Query("SELECT * FROM reminders WHERE request_code = :code AND deleted_at IS NULL") suspend fun getByRequestCode(code: Int): ReminderWithSchedules?
    @Transaction @Query("SELECT * FROM reminders WHERE target_type = :type AND target_id = :targetId AND deleted_at IS NULL LIMIT 1")
    suspend fun findByTarget(type: ReminderTargetType, targetId: String): ReminderWithSchedules?
    @Transaction @Query("SELECT * FROM reminders WHERE enabled = 1 AND deleted_at IS NULL") suspend fun getActive(): List<ReminderWithSchedules>
    @Query("SELECT request_code FROM reminders WHERE enabled = 0 OR deleted_at IS NOT NULL") suspend fun inactiveRequestCodes(): List<Int>
    @Query("SELECT request_code FROM reminders WHERE id = :id") suspend fun requestCodeOf(id: String): Int?
    @Query("SELECT COALESCE(MAX(request_code), 0) + 1 FROM reminders") suspend fun nextRequestCode(): Int

    @Upsert suspend fun upsert(entity: ReminderEntity)
    @Query("DELETE FROM reminder_schedules WHERE reminder_id = :reminderId") suspend fun clearSchedules(reminderId: String)
    @Insert suspend fun insertSchedules(list: List<ReminderScheduleEntity>)

    @Transaction
    suspend fun saveWithSchedules(entity: ReminderEntity, schedules: List<ReminderScheduleEntity>) {
        upsert(entity)
        clearSchedules(entity.id)
        if (schedules.isNotEmpty()) insertSchedules(schedules)
    }

    @Query("UPDATE reminders SET enabled = :enabled, updated_at = :at WHERE id = :id") suspend fun setEnabled(id: String, enabled: Boolean, at: Instant)
    @Query("UPDATE reminders SET deleted_at = :at, enabled = 0, updated_at = :at WHERE id = :id") suspend fun softDelete(id: String, at: Instant)
}

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(list: List<ReminderOccurrenceEntity>): List<Long>
    @Query("SELECT * FROM reminder_occurrences WHERE id = :id") suspend fun get(id: String): ReminderOccurrenceEntity?

    @Query("""SELECT * FROM reminder_occurrences WHERE reminder_id = :reminderId AND status IN ('SCHEDULED','SNOOZED')
        ORDER BY COALESCE(snoozed_until, scheduled_at) LIMIT 1""")
    suspend fun nextPending(reminderId: String): ReminderOccurrenceEntity?

    @Query("""SELECT * FROM reminder_occurrences WHERE reminder_id = :reminderId AND status IN ('SCHEDULED','SNOOZED')
        AND COALESCE(snoozed_until, scheduled_at) <= :until ORDER BY COALESCE(snoozed_until, scheduled_at)""")
    suspend fun duePending(reminderId: String, until: Instant): List<ReminderOccurrenceEntity>

    @Query("UPDATE reminder_occurrences SET status = :to, updated_at = :at WHERE id IN (:ids) AND status IN (:from)")
    suspend fun transition(ids: List<String>, from: List<OccurrenceStatus>, to: OccurrenceStatus, at: Instant): Int

    @Query("""UPDATE reminder_occurrences SET status = 'DELIVERED', delivered_at = :at, attempt_count = attempt_count + 1, updated_at = :at
        WHERE id = :id AND status IN ('SCHEDULED','SNOOZED')""")
    suspend fun markDelivered(id: String, at: Instant): Int

    @Query("""UPDATE reminder_occurrences SET status = :status, completed_at = :at, updated_at = :at
        WHERE id = :id AND status IN ('SCHEDULED','SNOOZED','DELIVERED')""")
    suspend fun markCompleted(id: String, status: OccurrenceStatus, at: Instant): Int

    @Query("""UPDATE reminder_occurrences SET status = 'SNOOZED', snoozed_until = :until, updated_at = :at
        WHERE id = :id AND status IN ('SCHEDULED','SNOOZED','DELIVERED')""")
    suspend fun snooze(id: String, until: Instant, at: Instant): Int

    @Query("UPDATE reminder_occurrences SET precision = :precision WHERE id = :id") suspend fun setPrecision(id: String, precision: AlarmPrecision)

    @Query("DELETE FROM reminder_occurrences WHERE reminder_id = :reminderId AND status = 'SCHEDULED' AND scheduled_at > :after")
    suspend fun deleteFutureScheduled(reminderId: String, after: Instant): Int

    @Query("UPDATE reminder_occurrences SET status = 'CANCELLED', updated_at = :at WHERE reminder_id = :reminderId AND status IN ('SCHEDULED','SNOOZED')")
    suspend fun cancelPending(reminderId: String, at: Instant): Int

    @Query("SELECT * FROM reminder_occurrences WHERE status IN ('SCHEDULED','SNOOZED') AND COALESCE(snoozed_until, scheduled_at) < :cutoff")
    suspend fun stalePending(cutoff: Instant): List<ReminderOccurrenceEntity>

    @Query("""SELECT o.* FROM reminder_occurrences o JOIN reminders r ON r.id = o.reminder_id
        WHERE o.status = 'DELIVERED' AND r.requires_completion = 1 AND COALESCE(o.delivered_at, o.scheduled_at) < :cutoff""")
    suspend fun staleDeliveredRequiringCompletion(cutoff: Instant): List<ReminderOccurrenceEntity>

    @Query("SELECT * FROM reminder_occurrences WHERE reminder_id = :reminderId AND status = 'DELIVERED' AND scheduled_at < :before")
    suspend fun deliveredBefore(reminderId: String, before: Instant): List<ReminderOccurrenceEntity>

    @Query("""SELECT * FROM reminder_occurrences WHERE reminder_id = :reminderId AND local_date = :date
        AND (:time IS NULL OR local_time = :time) AND status IN ('SCHEDULED','SNOOZED','DELIVERED') ORDER BY scheduled_at LIMIT 1""")
    suspend fun findOpen(reminderId: String, date: LocalDate, time: LocalTime?): ReminderOccurrenceEntity?

    @Query("""SELECT * FROM reminder_occurrences WHERE status IN ('SCHEDULED','SNOOZED') AND COALESCE(snoozed_until, scheduled_at) >= :from
        ORDER BY COALESCE(snoozed_until, scheduled_at) LIMIT :limit""")
    fun observeUpcoming(from: Instant, limit: Int): Flow<List<ReminderOccurrenceEntity>>

    @Query("SELECT * FROM reminder_occurrences WHERE scheduled_at >= :from AND scheduled_at < :to ORDER BY scheduled_at")
    fun observeBetween(from: Instant, to: Instant): Flow<List<ReminderOccurrenceEntity>>

    @Query("SELECT * FROM reminder_occurrences WHERE reminder_id = :reminderId ORDER BY scheduled_at DESC LIMIT :limit")
    fun observeForReminder(reminderId: String, limit: Int): Flow<List<ReminderOccurrenceEntity>>

    @Insert suspend fun insertAction(log: ReminderActionLogEntity)
}

@Dao
interface DeliveryLogDao {
    @Insert suspend fun insert(log: AlarmDeliveryLogEntity)
    @Query("SELECT * FROM alarm_delivery_logs ORDER BY at DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<AlarmDeliveryLogEntity>>
    @Query("DELETE FROM alarm_delivery_logs WHERE at < :cutoff") suspend fun pruneBefore(cutoff: Instant): Int
}
