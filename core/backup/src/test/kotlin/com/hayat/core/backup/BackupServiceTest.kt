package com.hayat.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hayat.core.common.AppError
import com.hayat.core.common.AppResult
import com.hayat.core.common.BackupFailure
import com.hayat.core.data.repository.RoomMedicineRepository
import com.hayat.core.data.repository.RoomNoteRepository
import com.hayat.core.data.repository.RoomOccurrenceRepository
import com.hayat.core.data.repository.RoomReminderRepository
import com.hayat.core.data.repository.RoomDeliveryLogRepository
import com.hayat.core.data.repository.RoomTaskRepository
import com.hayat.core.database.HayatDatabase
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.security.BackupPreview
import com.hayat.core.model.DoseStatus
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineSchedule
import com.hayat.core.model.Note
import com.hayat.core.model.Subtask
import com.hayat.core.model.Task
import com.hayat.core.testing.FakeAlarmScheduler
import com.hayat.core.testing.FakeNotifier
import com.hayat.core.testing.FakePreferencesRepository
import com.hayat.core.testing.FakeTimeProvider
import com.hayat.core.testing.SequentialIdGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val time = FakeTimeProvider()
    private val ids = SequentialIdGenerator()
    private lateinit var source: HayatDatabase
    private lateinit var target: HayatDatabase

    private fun db() = Room.inMemoryDatabaseBuilder(ctx, HayatDatabase::class.java).allowMainThreadQueries().build()
    private fun service(db: HayatDatabase, alarms: FakeAlarmScheduler = FakeAlarmScheduler()): HayatBackupService {
        val scheduler = ReminderScheduler(RoomReminderRepository(db, db.reminderDao()), RoomOccurrenceRepository(db.occurrenceDao(), ids), alarms, FakeNotifier(),
            RoomDeliveryLogRepository(db.deliveryLogDao(), ids, time), FakePreferencesRepository(), time, ids, emptyMap(), emptySet())
        return HayatBackupService(db, scheduler, alarms, time, ids, "1.0.0-test")
    }

    @Before fun setUp() { source = db(); target = db() }
    @After fun tearDown() { source.close(); target.close() }

    private suspend fun seed() {
        RoomTaskRepository(source.taskDao()).save(Task("t1", "বাজার করা", description = "চাল, ডাল", dueDate = LocalDate.of(2026, 1, 2),
            subtasks = listOf(Subtask("s1", "চাল", true, 0)), createdAt = time.now(), updatedAt = time.now()))
        val meds = RoomMedicineRepository(source, source.medicineDao(), ids)
        meds.save(Medicine("m1", "Napa", dose = "500", unit = "mg", startDate = LocalDate.of(2026, 1, 1), stock = 9.5,
            schedules = listOf(MedicineSchedule("ms1", "m1", LocalTime.of(8, 0))), createdAt = time.now(), updatedAt = time.now()))
        meds.setDoseStatus("m1", "ms1", LocalDate.of(2026, 1, 1), LocalTime.of(8, 0), DoseStatus.TAKEN, "", time.now())
        RoomNoteRepository(source.noteDao()).save(Note("n1", "Private", body = "গোপন", isLocked = true, createdAt = time.now(), updatedAt = time.now()))
    }

    private suspend fun exportBytes(pass: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        val r = service(source).export(out, pass?.toCharArray())
        assertTrue(r is AppResult.Success)
        return out.toByteArray()
    }

    private fun failureOf(r: AppResult<*>): BackupFailure? = ((r as? AppResult.Failure)?.error as? AppError.Backup)?.reason

    @Test fun `table allow-list covers every user table in the schema`() {
        val schema = File("../database/schemas/com.hayat.core.database.HayatDatabase/${HayatDatabase.VERSION}.json").readText()
        val names = Json.parseToJsonElement(schema).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { (it.jsonObject["tableName"] as JsonPrimitive).content }.toSet()
        assertEquals(names - setOf("alarm_delivery_logs", "backup_metadata"), HayatBackupService.TABLES.toSet())
    }

    @Test fun `plain backup round trips all data exactly`() = runTest {
        seed()
        val bytes = exportBytes()
        val r = service(target).restore(ByteArrayInputStream(bytes), null)
        assertTrue(r is AppResult.Success)
        val task = RoomTaskRepository(target.taskDao()).getTask("t1")!!
        assertEquals("বাজার করা", task.title)
        assertTrue(task.subtasks.single().isDone)
        val med = RoomMedicineRepository(target, target.medicineDao(), ids).getMedicine("m1")!!
        assertEquals(8.5, med.stock!!, 0.0)
        assertEquals(1, target.medicineDao().observeLogs(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)).first().size)
        assertTrue(RoomNoteRepository(target.noteDao()).getNote("n1")!!.isLocked)
        assertEquals((r as AppResult.Success<BackupPreview>).value.counts["tasks"], 1)
    }

    @Test fun `encrypted backup requires the right passphrase`() = runTest {
        seed()
        val bytes = exportBytes("correct horse battery")
        assertTrue(service(target).isEncrypted(ByteArrayInputStream(bytes)))
        assertTrue("payload is not readable", !String(bytes, Charsets.ISO_8859_1).contains("Napa"))
        assertEquals(BackupFailure.PASSPHRASE_REQUIRED, failureOf(service(target).inspect(ByteArrayInputStream(bytes), null)))
        assertEquals(BackupFailure.WRONG_PASSPHRASE_OR_CORRUPT, failureOf(service(target).inspect(ByteArrayInputStream(bytes), "wrong".toCharArray())))
        val preview = service(target).inspect(ByteArrayInputStream(bytes), "correct horse battery".toCharArray())
        assertTrue(preview is AppResult.Success && preview.value.encrypted)
        assertTrue(service(target).restore(ByteArrayInputStream(bytes), "correct horse battery".toCharArray()) is AppResult.Success)
        assertEquals("Napa", RoomMedicineRepository(target, target.medicineDao(), ids).getMedicine("m1")!!.name)
    }

    @Test fun `tampered, truncated and foreign files are rejected without touching data`() = runTest {
        seed()
        RoomTaskRepository(target.taskDao()).save(Task("keep", "Existing", createdAt = time.now(), updatedAt = time.now()))
        val bytes = exportBytes()
        val tampered = bytes.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 0xFF).toByte() }
        assertEquals(BackupFailure.CHECKSUM_MISMATCH, failureOf(service(target).restore(ByteArrayInputStream(tampered), null)))
        val encTampered = exportBytes("pw").also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals(BackupFailure.WRONG_PASSPHRASE_OR_CORRUPT, failureOf(service(target).restore(ByteArrayInputStream(encTampered), "pw".toCharArray())))
        assertEquals(BackupFailure.CHECKSUM_MISMATCH, failureOf(service(target).restore(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)), null)))
        assertEquals(BackupFailure.NOT_A_HAYAT_BACKUP, failureOf(service(target).restore(ByteArrayInputStream("PK\u0003\u0004 not a backup".toByteArray()), null)))
        assertEquals(BackupFailure.NOT_A_HAYAT_BACKUP, failureOf(service(target).restore(ByteArrayInputStream(ByteArray(0)), null)))
        assertEquals("Existing", RoomTaskRepository(target.taskDao()).getTask("keep")!!.title)
    }

    private fun craft(mutate: (JsonObject) -> JsonObject): ByteArray {
        val base = JsonObject(mapOf("app" to JsonPrimitive("Hayat"), "schemaVersion" to JsonPrimitive(HayatDatabase.VERSION),
            "createdAt" to JsonPrimitive("2026-01-01T00:00:00Z"), "tables" to JsonObject(emptyMap())))
        return BackupFormat.encode(Json.encodeToString(JsonObject.serializer(), mutate(base)).toByteArray(), null)
    }

    @Test fun `incompatible and malicious content is rejected atomically`() = runTest {
        RoomTaskRepository(target.taskDao()).save(Task("keep", "Existing", createdAt = time.now(), updatedAt = time.now()))
        val newer = craft { JsonObject(it + ("schemaVersion" to JsonPrimitive(HayatDatabase.VERSION + 1))) }
        assertEquals(BackupFailure.NEWER_SCHEMA, failureOf(service(target).restore(ByteArrayInputStream(newer), null)))
        val unknownTable = craft { JsonObject(it + ("tables" to Json.parseToJsonElement("""{"sqlite_master":[]}"""))) }
        assertEquals(BackupFailure.CORRUPT_CONTENT, failureOf(service(target).restore(ByteArrayInputStream(unknownTable), null)))
        val injectedColumn = craft { JsonObject(it + ("tables" to Json.parseToJsonElement(
            """{"tags":[{"id":"x","name":"a","created_at":1,"name) VALUES ('y');DROP TABLE tasks;--":1}]}"""))) }
        assertEquals(BackupFailure.CORRUPT_CONTENT, failureOf(service(target).restore(ByteArrayInputStream(injectedColumn), null)))
        val orphan = craft { JsonObject(it + ("tables" to Json.parseToJsonElement(
            """{"subtasks":[{"id":"s","task_id":"missing","title":"x","is_done":0,"position":0}]}"""))) }
        assertEquals(BackupFailure.CORRUPT_CONTENT, failureOf(service(target).restore(ByteArrayInputStream(orphan), null)))
        assertEquals("existing data survives every rejected restore", "Existing", RoomTaskRepository(target.taskDao()).getTask("keep")!!.title)
    }

    @Test fun `restore cancels stale os alarms and reschedules restored reminders`() = runTest {
        val alarms = FakeAlarmScheduler()
        val svc = service(target, alarms)
        alarms.alarms[42] = time.now() to com.hayat.core.model.ReminderStyle.NOTIFICATION
        target.openHelper.writableDatabase.execSQL(
            "INSERT INTO reminders (id,title,description,category,target_type,target_id,priority,enabled,style,vibrate,snooze_minutes,requires_completion,request_code,created_at,updated_at) " +
                "VALUES ('old','x','','CUSTOM','NONE',NULL,'MEDIUM',1,'NOTIFICATION',1,10,0,42,0,0)")
        seed()
        assertTrue(svc.restore(ByteArrayInputStream(exportBytes()), null) is AppResult.Success)
        assertTrue(42 !in alarms.alarms.keys)
    }
}
