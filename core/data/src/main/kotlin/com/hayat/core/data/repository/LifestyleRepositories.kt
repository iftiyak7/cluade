package com.hayat.core.data.repository

import androidx.room.withTransaction
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.data.mapper.toEntity
import com.hayat.core.data.mapper.toModel
import com.hayat.core.database.HayatDatabase
import com.hayat.core.database.dao.EventDao
import com.hayat.core.database.dao.FinanceDao
import com.hayat.core.database.dao.FitnessDao
import com.hayat.core.database.dao.HabitDao
import com.hayat.core.database.dao.IbadahDao
import com.hayat.core.database.dao.MealDao
import com.hayat.core.database.dao.MedicineDao
import com.hayat.core.database.dao.NoteDao
import com.hayat.core.database.dao.SleepDao
import com.hayat.core.database.dao.StudyDao
import com.hayat.core.database.dao.WaterDao
import com.hayat.core.database.entity.FastingLogEntity
import com.hayat.core.database.entity.HabitCompletionEntity
import com.hayat.core.database.entity.MedicineDoseLogEntity
import com.hayat.core.database.entity.NoteItemEntity
import com.hayat.core.database.entity.NoteTagCrossRef
import com.hayat.core.database.entity.PrayerLogEntity
import com.hayat.core.database.entity.WaterGoalEntity
import com.hayat.core.database.entity.ZikrProgressEntity
import com.hayat.core.domain.repository.EventRepository
import com.hayat.core.domain.repository.FinanceRepository
import com.hayat.core.domain.repository.FitnessRepository
import com.hayat.core.domain.repository.HabitRepository
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.repository.MealRepository
import com.hayat.core.domain.repository.MedicineRepository
import com.hayat.core.domain.repository.NoteRepository
import com.hayat.core.domain.repository.SleepRepository
import com.hayat.core.domain.repository.StudyRepository
import com.hayat.core.domain.repository.WaterRepository
import com.hayat.core.model.*
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class RoomWaterRepository @Inject constructor(private val dao: WaterDao, private val ids: IdGenerator, private val time: TimeProvider) : WaterRepository {
    override fun observeLogs(from: LocalDate, to: LocalDate) = dao.observeLogs(from, to).map { l -> l.map { it.toModel() } }
    override fun observeGoals() = dao.observeGoals().map { l -> l.map { it.toModel() } }
    override suspend fun add(log: WaterLog) = dao.upsertLog(log.toEntity())
    override suspend fun update(log: WaterLog) = dao.upsertLog(log.toEntity())
    override suspend fun delete(id: String) = dao.deleteLog(id)
    /** Goals are versioned by effective date so past days keep the goal that applied then. */
    override suspend fun setGoal(dailyMl: Int, effectiveFrom: LocalDate) = dao.replaceGoal(WaterGoalEntity(ids.newId(), dailyMl, effectiveFrom, time.now()))
}

class RoomMedicineRepository @Inject constructor(private val db: HayatDatabase, private val dao: MedicineDao, private val ids: IdGenerator) : MedicineRepository {
    override fun observeMedicines() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeMedicine(id: String) = dao.observe(id).map { it?.toModel() }
    override suspend fun getMedicine(id: String) = dao.get(id)?.toModel()
    override suspend fun save(medicine: Medicine) = dao.save(medicine.toEntity(), medicine.schedules.map { it.copy(medicineId = medicine.id).toEntity() })
    override suspend fun setArchived(id: String, archived: Boolean, at: Instant) = dao.setArchived(id, archived, at)
    override suspend fun softDelete(id: String, at: Instant) = dao.softDelete(id, at)
    override fun observeDoseLogs(from: LocalDate, to: LocalDate) = dao.observeLogs(from, to).map { l -> l.map { it.toModel() } }

    override suspend fun setDoseStatus(medicineId: String, scheduleId: String?, date: LocalDate, time: LocalTime, status: DoseStatus?, note: String, at: Instant): Medicine? =
        db.withTransaction {
            val med = dao.get(medicineId) ?: return@withTransaction null
            val quantity = med.schedules.firstOrNull { it.id == scheduleId || it.time == time }?.quantity ?: 1.0
            val existing = dao.findLog(medicineId, date, time)
            val wasTaken = existing?.status == DoseStatus.TAKEN
            val isTaken = status == DoseStatus.TAKEN
            if (status == null) existing?.let { dao.deleteLog(it.id) }
            else dao.upsertLog(MedicineDoseLogEntity(existing?.id ?: ids.newId(), medicineId, scheduleId ?: existing?.scheduleId, date, time, status, at, note.take(200)))
            val stock = med.medicine.stock
            if (stock != null && wasTaken != isTaken) {
                val newStock = (if (isTaken) stock - quantity else stock + quantity).coerceAtLeast(0.0)
                dao.setStock(medicineId, newStock, at)
            }
            dao.get(medicineId)?.toModel()
        }
}

class RoomMealRepository @Inject constructor(private val dao: MealDao) : MealRepository {
    override fun observePlans() = dao.observePlans().map { l -> l.map { it.toModel() } }
    override suspend fun getPlan(id: String) = dao.getPlan(id)?.toModel()
    override suspend fun savePlan(plan: MealPlan) = dao.upsertPlan(plan.toEntity())
    override suspend fun deletePlan(id: String, at: Instant) = dao.softDeletePlan(id, at)
    override fun observeLogs(from: LocalDate, to: LocalDate) = dao.observeLogs(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun saveLog(log: MealLog) = dao.upsertLog(log.toEntity())
    override suspend fun deleteLog(id: String) = dao.deleteLog(id)
    override fun observeShopping() = dao.observeShopping().map { l -> l.map { it.toModel() } }
    override suspend fun saveShoppingItem(item: ShoppingItem) = dao.upsertShopping(item.toEntity())
    override suspend fun deleteShoppingItem(id: String) = dao.deleteShopping(id)
    override suspend fun clearCheckedShopping() = dao.clearChecked()
}

class RoomSleepRepository @Inject constructor(private val dao: SleepDao) : SleepRepository {
    override fun observeEntries(from: LocalDate, to: LocalDate) = dao.observe(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun save(entry: SleepEntry) = dao.upsert(entry.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}

class RoomFitnessRepository @Inject constructor(private val dao: FitnessDao) : FitnessRepository {
    override fun observePlans() = dao.observePlans().map { l -> l.map { it.toModel() } }
    override suspend fun getPlan(id: String) = dao.getPlan(id)?.toModel()
    override suspend fun savePlan(plan: WorkoutPlan) = dao.savePlan(plan.toEntity(), plan.exercises.map { it.copy(planId = plan.id).toEntity() })
    override suspend fun deletePlan(id: String, at: Instant) = dao.softDeletePlan(id, at)
    override fun observeSessions(from: LocalDate, to: LocalDate) = dao.observeSessions(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun saveSession(session: WorkoutSession) = dao.upsertSession(session.toEntity())
    override suspend fun deleteSession(id: String) = dao.deleteSession(id)
}

class RoomIbadahRepository @Inject constructor(private val db: HayatDatabase, private val dao: IbadahDao, private val ids: IdGenerator) : IbadahRepository {
    override fun observePrayerLogs(from: LocalDate, to: LocalDate) = dao.observePrayerLogs(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun setPrayerStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus?, at: Instant) {
        if (status == null) { dao.deletePrayerLog(date, prayer); return }
        val existing = dao.findPrayerLog(date, prayer)
        dao.upsertPrayerLog(PrayerLogEntity(existing?.id ?: ids.newId(), date, prayer, status, at))
    }
    override fun observeZikrs() = dao.observeZikrs().map { l -> l.map { it.toModel() } }
    override suspend fun getZikr(id: String) = dao.getZikr(id)?.toModel()
    override suspend fun saveZikr(zikr: ZikrDefinition) = dao.upsertZikr(zikr.toEntity())
    override suspend fun deleteZikr(id: String) = dao.deleteZikr(id)
    override suspend fun ensurePresets(presets: List<ZikrDefinition>) = dao.insertZikrsIgnore(presets.map { it.toEntity() })
    override fun observeZikrProgress(from: LocalDate, to: LocalDate) = dao.observeZikrProgress(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun addZikrCount(zikrId: String, date: LocalDate, delta: Int, at: Instant) = db.withTransaction {
        if (dao.addToProgress(zikrId, date, delta, at) == 0) dao.upsertProgress(ZikrProgressEntity(ids.newId(), zikrId, date, delta.coerceAtLeast(0), at))
    }
    override suspend fun setZikrCount(zikrId: String, date: LocalDate, count: Int, at: Instant) = db.withTransaction {
        val existing = dao.findProgress(zikrId, date)
        dao.upsertProgress(ZikrProgressEntity(existing?.id ?: ids.newId(), zikrId, date, count.coerceAtLeast(0), at))
    }
    override fun observeQuranLogs(from: LocalDate, to: LocalDate) = dao.observeQuran(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun saveQuranLog(log: QuranLog) = dao.upsertQuran(log.toEntity())
    override suspend fun deleteQuranLog(id: String) = dao.deleteQuran(id)
    override fun observeFasting(from: LocalDate, to: LocalDate) = dao.observeFasting(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun saveFasting(log: FastingLog) = dao.replaceFasting(FastingLogEntity(log.id, log.date, log.type, log.status, log.note, Instant.now()))
    override suspend fun deleteFasting(date: LocalDate) = dao.deleteFasting(date)
}

class RoomHabitRepository @Inject constructor(private val dao: HabitDao, private val ids: IdGenerator) : HabitRepository {
    override fun observeHabits() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun getHabit(id: String) = dao.get(id)?.toModel()
    override suspend fun save(habit: Habit) = dao.upsert(habit.toEntity())
    override suspend fun softDelete(id: String, at: Instant) = dao.softDelete(id, at)
    override fun observeCompletions(from: LocalDate, to: LocalDate) = dao.observeCompletions(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun setCount(habitId: String, date: LocalDate, count: Int, at: Instant) {
        if (count <= 0) { dao.deleteCompletion(habitId, date); return }
        val existing = dao.findCompletion(habitId, date)
        dao.upsertCompletion(HabitCompletionEntity(existing?.id ?: ids.newId(), habitId, date, count, existing?.note.orEmpty(), at))
    }
    override suspend fun getCount(habitId: String, date: LocalDate) = dao.findCompletion(habitId, date)?.count ?: 0
}

class RoomStudyRepository @Inject constructor(private val dao: StudyDao) : StudyRepository {
    override fun observeSubjects() = dao.observeSubjects().map { l -> l.map { it.toModel() } }
    override suspend fun saveSubject(subject: StudySubject) = dao.upsertSubject(subject.toEntity())
    override suspend fun deleteSubject(id: String, at: Instant) = dao.softDeleteSubject(id, at)
    override fun observeSessions(from: LocalDate, to: LocalDate) = dao.observeSessions(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun saveSession(session: FocusSession) = dao.upsertSession(session.toEntity())
    override suspend fun deleteSession(id: String) = dao.deleteSession(id)
}

class RoomFinanceRepository @Inject constructor(private val dao: FinanceDao) : FinanceRepository {
    override fun observeItems() = dao.observeItems().map { l -> l.map { it.toModel() } }
    override suspend fun getItem(id: String) = dao.getItem(id)?.toModel()
    override suspend fun saveItem(item: FinanceItem) = dao.upsertItem(item.toEntity())
    override suspend fun deleteItem(id: String, at: Instant) = dao.softDeleteItem(id, at)
    override fun observePayments(itemId: String) = dao.observePayments(itemId).map { l -> l.map { it.toModel() } }
    override fun observeAllPayments(from: LocalDate, to: LocalDate) = dao.observeAllPayments(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun recordPayment(payment: FinancePayment, updatedItem: FinanceItem) = dao.recordPayment(payment.toEntity(), updatedItem.toEntity())
}

class RoomEventRepository @Inject constructor(private val dao: EventDao) : EventRepository {
    override fun observeEvents() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun getEvent(id: String) = dao.get(id)?.toModel()
    override suspend fun getActiveEvents() = dao.getActive().map { it.toModel() }
    override suspend fun save(event: CalendarEvent) = dao.upsert(event.toEntity())
    override suspend fun softDelete(id: String, at: Instant) = dao.softDelete(id, at)
}

class RoomNoteRepository @Inject constructor(private val dao: NoteDao) : NoteRepository {
    override fun observeNotes() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeNote(id: String) = dao.observe(id).map { it?.toModel() }
    override suspend fun getNote(id: String) = dao.get(id)?.toModel()
    override suspend fun save(note: Note) = dao.save(
        note.toEntity(),
        note.items.mapIndexed { i, it -> NoteItemEntity(it.id, note.id, it.text, it.isChecked, i) },
        note.tags.map { NoteTagCrossRef(note.id, it.id) },
    )
    override suspend fun softDelete(id: String, at: Instant) = dao.softDelete(id, at)
    override suspend fun setChecklistItem(itemId: String, checked: Boolean, at: Instant) = dao.setItemChecked(itemId, checked)
    override fun observeJournal() = dao.observeJournal().map { l -> l.map { it.toModel() } }
    override suspend fun getJournal(id: String) = dao.getJournal(id)?.toModel()
    override suspend fun saveJournal(entry: JournalEntry) = dao.upsertJournal(entry.toEntity())
    override suspend fun deleteJournal(id: String, at: Instant) = dao.softDeleteJournal(id, at)
    override fun observeMoods(from: LocalDate, to: LocalDate) = dao.observeMoods(from, to).map { l -> l.map { it.toModel() } }
    override suspend fun saveMood(entry: MoodEntry) = dao.upsertMood(entry.toEntity())
    override suspend fun deleteMood(id: String) = dao.deleteMood(id)
}
