package com.hayat.core.data.repository

import androidx.room.withTransaction
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.data.mapper.toEntity
import com.hayat.core.data.mapper.toModel
import com.hayat.core.database.HayatDatabase
import com.hayat.core.database.dao.DeliveryLogDao
import com.hayat.core.database.dao.OccurrenceDao
import com.hayat.core.database.dao.ReminderDao
import com.hayat.core.database.entity.AlarmDeliveryLogEntity
import com.hayat.core.database.entity.ReminderActionLogEntity
import com.hayat.core.domain.repository.DeliveryLogRepository
import com.hayat.core.domain.repository.OccurrenceRepository
import com.hayat.core.domain.repository.ReminderRepository
import com.hayat.core.model.AlarmPrecision
import com.hayat.core.model.DeliveryEvent
import com.hayat.core.model.OccurrenceStatus
import com.hayat.core.model.Reminder
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderOccurrence
import com.hayat.core.model.ReminderTargetType
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class RoomReminderRepository @Inject constructor(private val db: HayatDatabase, private val dao: ReminderDao) : ReminderRepository {
    override fun observeReminders() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeReminder(id: String) = dao.observe(id).map { it?.toModel() }
    override suspend fun getReminder(id: String) = dao.get(id)?.toModel()
    override suspend fun getByRequestCode(requestCode: Int) = dao.getByRequestCode(requestCode)?.toModel()
    override suspend fun findByTarget(type: ReminderTargetType, targetId: String) = dao.findByTarget(type, targetId)?.toModel()
    override suspend fun getActiveReminders() = dao.getActive().map { it.toModel() }
    override suspend fun getInactiveRequestCodes() = dao.inactiveRequestCodes()

    /** Request codes are stable for the life of a reminder and unique (DB constraint); assigned inside the transaction. */
    override suspend fun save(reminder: Reminder): Reminder = db.withTransaction {
        val code = dao.requestCodeOf(reminder.id) ?: reminder.requestCode.takeIf { it > 0 } ?: dao.nextRequestCode()
        val saved = reminder.copy(requestCode = code, schedules = reminder.schedules.map { it.copy(reminderId = reminder.id) })
        dao.saveWithSchedules(saved.toEntity(code), saved.schedules.map { it.toEntity() })
        saved
    }
    override suspend fun setEnabled(id: String, enabled: Boolean, at: Instant) = dao.setEnabled(id, enabled, at)
    override suspend fun softDelete(id: String, at: Instant) = dao.softDelete(id, at)
}

class RoomOccurrenceRepository @Inject constructor(private val dao: OccurrenceDao, private val ids: IdGenerator) : OccurrenceRepository {
    override suspend fun insertIfAbsent(occurrences: List<ReminderOccurrence>): Int =
        dao.insertIgnore(occurrences.map { it.toEntity() }).count { it != -1L }
    override suspend fun get(id: String) = dao.get(id)?.toModel()
    override suspend fun nextPending(reminderId: String) = dao.nextPending(reminderId)?.toModel()
    override suspend fun duePending(reminderId: String, until: Instant) = dao.duePending(reminderId, until).map { it.toModel() }
    override suspend fun transition(ids: List<String>, from: Set<OccurrenceStatus>, to: OccurrenceStatus, at: Instant): Int =
        if (ids.isEmpty()) 0 else ids.chunked(500).sumOf { dao.transition(it, from.toList(), to, at) }
    override suspend fun markDelivered(id: String, at: Instant) = dao.markDelivered(id, at)
    override suspend fun markCompleted(id: String, status: OccurrenceStatus, at: Instant) = dao.markCompleted(id, status, at)
    override suspend fun snooze(id: String, until: Instant, at: Instant) = dao.snooze(id, until, at)
    override suspend fun setPrecision(id: String, precision: AlarmPrecision) = dao.setPrecision(id, precision)
    override suspend fun deleteFutureScheduled(reminderId: String, after: Instant) = dao.deleteFutureScheduled(reminderId, after)
    override suspend fun cancelPending(reminderId: String, at: Instant) = dao.cancelPending(reminderId, at)
    override suspend fun stalePending(cutoff: Instant) = dao.stalePending(cutoff).map { it.toModel() }
    override suspend fun staleDeliveredRequiringCompletion(cutoff: Instant) = dao.staleDeliveredRequiringCompletion(cutoff).map { it.toModel() }
    override suspend fun deliveredBefore(reminderId: String, before: Instant) = dao.deliveredBefore(reminderId, before).map { it.toModel() }
    override suspend fun findOpen(reminderId: String, date: LocalDate, time: LocalTime?) = dao.findOpen(reminderId, date, time)?.toModel()
    override fun observeUpcoming(from: Instant, limit: Int) = dao.observeUpcoming(from, limit).map { l -> l.map { it.toModel() } }
    override fun observeBetween(from: Instant, to: Instant) = dao.observeBetween(from, to).map { l -> l.map { it.toModel() } }
    override fun observeForReminder(reminderId: String, limit: Int) = dao.observeForReminder(reminderId, limit).map { l -> l.map { it.toModel() } }
    override suspend fun logAction(occurrenceId: String, action: ReminderAction, at: Instant, detail: String) =
        dao.insertAction(ReminderActionLogEntity(ids.newId(), occurrenceId, action, at, detail.take(100)))
}

/** Logs only identifiers and event types: never titles or other user content. */
class RoomDeliveryLogRepository @Inject constructor(private val dao: DeliveryLogDao, private val ids: IdGenerator, private val time: TimeProvider) : DeliveryLogRepository {
    override suspend fun log(event: DeliveryEvent, reminderId: String?, occurrenceId: String?, detail: String) =
        dao.insert(AlarmDeliveryLogEntity(ids.newId(), occurrenceId, reminderId, event, time.now(), detail.take(120)))
    override fun observeRecent(limit: Int) = dao.observeRecent(limit).map { l -> l.map { it.toModel() } }
    override suspend fun pruneBefore(cutoff: Instant) = dao.pruneBefore(cutoff)
}
