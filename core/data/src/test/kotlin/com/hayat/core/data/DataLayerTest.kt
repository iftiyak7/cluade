package com.hayat.core.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hayat.core.data.repository.*
import com.hayat.core.database.HayatDatabase
import com.hayat.core.database.dao.DatabaseSnapshot
import com.hayat.core.database.entity.SubtaskEntity
import com.hayat.core.domain.reminder.ReminderLinker
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.task.CompleteTaskUseCase
import com.hayat.core.domain.task.SaveTaskUseCase
import com.hayat.core.domain.task.TaskReminderHandler
import com.hayat.core.model.*
import com.hayat.core.testing.FakeAlarmScheduler
import com.hayat.core.testing.FakeNotifier
import com.hayat.core.testing.FakePreferencesRepository
import com.hayat.core.testing.FakeTimeProvider
import com.hayat.core.testing.SequentialIdGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    private lateinit var db: HayatDatabase
    private val time = FakeTimeProvider().apply { set("2026-01-01T07:00") }
    private val ids = SequentialIdGenerator()

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HayatDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun now() = time.now()

    private fun scheduler(alarms: FakeAlarmScheduler = FakeAlarmScheduler(), notifier: FakeNotifier = FakeNotifier(),
                          tasks: RoomTaskRepository = RoomTaskRepository(db.taskDao())): Triple<ReminderScheduler, RoomReminderRepository, RoomOccurrenceRepository> {
        val reminders = RoomReminderRepository(db, db.reminderDao())
        val occ = RoomOccurrenceRepository(db.occurrenceDao(), ids)
        lateinit var s: ReminderScheduler
        val handlers = mutableMapOf<ReminderTargetType, com.hayat.core.domain.reminder.ReminderTargetHandler>()
        s = ReminderScheduler(reminders, occ, alarms, notifier, RoomDeliveryLogRepository(db.deliveryLogDao(), ids, time),
            FakePreferencesRepository(), time, ids, handlers, emptySet())
        val linker = ReminderLinker(reminders, s, time, ids)
        handlers[ReminderTargetType.TASK] = TaskReminderHandler(CompleteTaskUseCase(tasks, linker, time, ids))
        return Triple(s, reminders, occ)
    }

    @Test fun `request codes are unique and stable across edits`() = runTest {
        val repo = RoomReminderRepository(db, db.reminderDao())
        fun r(id: String) = Reminder(id = id, title = id, createdAt = now(), updatedAt = now(), schedules = listOf(
            ReminderSchedule("s-$id", id, Schedule(DateRule.Daily(), TimeRule.At(LocalTime.of(8, 0)), LocalDate.of(2026, 1, 1)))))
        val a = repo.save(r("a")); val b = repo.save(r("b"))
        assertNotEquals(a.requestCode, b.requestCode)
        val a2 = repo.save(a.copy(title = "edited", requestCode = 0, schedules = listOf(ReminderSchedule("s2", "a",
            Schedule(DateRule.Weekly(WeekdaySet.BD_WORKWEEK), TimeRule.Every(30, LocalTime.of(9, 0), LocalTime.of(17, 0)), LocalDate.of(2026, 1, 1))))))
        assertEquals(a.requestCode, a2.requestCode)
        val loaded = repo.getReminder("a")!!
        assertEquals(1, loaded.schedules.size)
        assertEquals(TimeRule.Every(30, LocalTime.of(9, 0), LocalTime.of(17, 0)), loaded.schedules.single().schedule.timeRule)
    }

    @Test fun `scheduler end to end on room - dedupe, delivery, done propagates to task`() = runTest {
        val tasks = RoomTaskRepository(db.taskDao())
        val alarms = FakeAlarmScheduler(); val notifier = FakeNotifier()
        val (s, reminders, occ) = scheduler(alarms, notifier, tasks)
        val linker = ReminderLinker(reminders, s, time, ids)
        val save = SaveTaskUseCase(tasks, linker, time)
        val task = Task("t1", "Submit report", dueDate = LocalDate.of(2026, 1, 1), dueTime = LocalTime.of(9, 0), reminderOffsetMinutes = 15, createdAt = now(), updatedAt = now())
        save(task)
        val reminder = reminders.findByTarget(ReminderTargetType.TASK, "t1")!!
        val first = occ.nextPending(reminder.id)!!
        assertEquals(LocalTime.of(8, 45), first.localTime)
        assertEquals(first.scheduledAt, alarms.alarms[reminder.requestCode]!!.first)
        // Re-materialising does not duplicate.
        s.reconcile(); s.reconcile()
        assertEquals(1, db.occurrenceDao().observeForReminder(reminder.id, 100).first().size)
        time.set("2026-01-01T08:45")
        s.onAlarmFired(reminder.requestCode); s.onAlarmFired(reminder.requestCode)
        assertEquals(1, notifier.shown.size)
        s.handleAction(first.id, ReminderAction.DONE)
        assertTrue(tasks.getTask("t1")!!.isCompleted)
        // Completing removed the task's reminder.
        assertNull(reminders.findByTarget(ReminderTargetType.TASK, "t1"))
    }

    @Test fun `recurring task completion keeps history and creates next instance`() = runTest {
        val tasks = RoomTaskRepository(db.taskDao())
        val (s, reminders, _) = scheduler(tasks = tasks)
        val linker = ReminderLinker(reminders, s, time, ids)
        SaveTaskUseCase(tasks, linker, time)(Task("r1", "Water plants", dueDate = LocalDate.of(2026, 1, 1), recurrence = DateRule.Daily(2),
            subtasks = listOf(Subtask("st", "Balcony", true, 0)), createdAt = now(), updatedAt = now()))
        val next = CompleteTaskUseCase(tasks, linker, time, ids)("r1", true)
        val all = tasks.observeTasks().first()
        assertEquals(2, all.size)
        val created = all.first { it.id != "r1" }
        assertEquals(LocalDate.of(2026, 1, 3), created.dueDate)
        assertTrue(created.subtasks.none { it.isDone })
        assertTrue(next is com.hayat.core.common.AppResult.Success)
    }

    @Test fun `task save with subtasks and tags, cascade on hard delete`() = runTest {
        val tagRepo = RoomTagRepository(db.tagDao(), ids, time)
        val tag = tagRepo.getOrCreate("জরুরি")
        assertEquals(tag.id, tagRepo.getOrCreate("জরুরি").id)
        val repo = RoomTaskRepository(db.taskDao())
        repo.save(Task("t", "বাজার", subtasks = listOf(Subtask("s1", "চাল", false, 0), Subtask("s2", "ডাল", false, 1)), tags = listOf(tag), createdAt = now(), updatedAt = now()))
        val loaded = repo.getTask("t")!!
        assertEquals(listOf("চাল", "ডাল"), loaded.subtasks.map { it.title })
        assertEquals("জরুরি", loaded.tags.single().name)
        repo.setSubtaskDone("s1", true, now())
        assertTrue(repo.getTask("t")!!.subtasks.first().isDone)
        db.openHelper.writableDatabase.execSQL("DELETE FROM tasks WHERE id = 't'")
        assertEquals(0, db.backupDao().allSubtasks().size)
        assertEquals(0, db.backupDao().allTaskTags().size)
    }

    @Test fun `foreign keys are enforced`() = runTest {
        try {
            db.taskDao().insertSubtasks(listOf(SubtaskEntity("x", "missing-task", "orphan", false, 0)))
            fail("expected FK violation")
        } catch (e: SQLiteConstraintException) { /* expected */ }
    }

    @Test fun `dose logging adjusts stock exactly once and reverts`() = runTest {
        val repo = RoomMedicineRepository(db, db.medicineDao(), ids)
        val m = Medicine("m", "Napa", startDate = LocalDate.of(2026, 1, 1), stock = 10.0, refillThreshold = 3.0,
            schedules = listOf(MedicineSchedule("s", "m", LocalTime.of(8, 0), quantity = 2.0)), createdAt = now(), updatedAt = now())
        repo.save(m)
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(8.0, repo.setDoseStatus("m", "s", d, LocalTime.of(8, 0), DoseStatus.TAKEN, "", now())!!.stock!!, 0.0)
        assertEquals(8.0, repo.setDoseStatus("m", "s", d, LocalTime.of(8, 0), DoseStatus.TAKEN, "", now())!!.stock!!, 0.0)
        assertEquals(10.0, repo.setDoseStatus("m", "s", d, LocalTime.of(8, 0), DoseStatus.SKIPPED, "", now())!!.stock!!, 0.0)
        assertEquals(1, repo.observeDoseLogs(d, d).first().size)
        repo.setDoseStatus("m", "s", d, LocalTime.of(8, 0), null, "", now())
        assertTrue(repo.observeDoseLogs(d, d).first().isEmpty())
    }

    @Test fun `search matches bengali substrings, escapes wildcards and hides locked bodies`() = runTest {
        val tasks = RoomTaskRepository(db.taskDao())
        tasks.save(Task("t1", "বিদ্যুৎ বিল পরিশোধ", description = "১০০% জরুরি", createdAt = now(), updatedAt = now()))
        tasks.save(Task("t2", "Gym session", createdAt = now(), updatedAt = now()))
        val notes = RoomNoteRepository(db.noteDao())
        notes.save(Note("n1", "Secret", body = "বিল নম্বর ১২৩", isLocked = true, createdAt = now(), updatedAt = now()))
        notes.save(Note("n2", "Shopping", body = "বিল দেওয়া", createdAt = now(), updatedAt = now()))
        val search = RoomSearchRepository(db.searchDao())
        suspend fun ids(q: String) = search.search(q).map { it.id }
        assertEquals("bengali word", setOf("t1", "n2"), ids("বিল").toSet())
        assertEquals("conjuncts and vowel signs intact", listOf("t1"), ids("বিদ্যুৎ"))
        assertEquals("bengali digits with literal percent", listOf("t1"), ids("১০০%"))
        assertEquals("percent is literal, not a wildcard", listOf("t1"), ids("%"))
        assertTrue("underscore is literal", ids("_").isEmpty())
        assertEquals("ascii case-insensitive", listOf("t2"), ids("gym"))
        assertEquals("locked note matches by title", listOf("n1"), ids("Secret"))
        assertEquals("locked note exposes no snippet", "", search.search("Secret").single().snippet)
    }

    @Test fun `snapshot replace is atomic and rolls back on invalid data`() = runTest {
        RoomTaskRepository(db.taskDao()).save(Task("keep", "Existing", createdAt = now(), updatedAt = now()))
        val good = db.backupDao().snapshot()
        db.backupDao().replaceAll(good)
        assertEquals(1, db.backupDao().allTasks().size)
        val bad = DatabaseSnapshot(subtasks = listOf(SubtaskEntity("x", "no-such-task", "orphan", false, 0)))
        try { db.backupDao().replaceAll(bad); fail("expected failure") } catch (e: Exception) { /* expected */ }
        assertEquals("existing data survives a failed restore", 1, db.backupDao().allTasks().size)
    }

    @Test fun `water goals are versioned by date`() = runTest {
        val repo = RoomWaterRepository(db.waterDao(), ids, time)
        repo.setGoal(2000, LocalDate.of(2026, 1, 1))
        repo.setGoal(2400, LocalDate.of(2026, 1, 1)) // same day replaces
        repo.setGoal(3000, LocalDate.of(2026, 2, 1))
        assertEquals(listOf(2400, 3000), repo.observeGoals().first().map { it.dailyMl })
    }

    @Test fun `zikr counts accumulate per day and never go negative`() = runTest {
        val repo = RoomIbadahRepository(db, db.ibadahDao(), ids)
        repo.ensurePresets(com.hayat.core.domain.worship.ZikrPresets.presets(now()))
        repo.ensurePresets(com.hayat.core.domain.worship.ZikrPresets.presets(now())) // idempotent
        val z = repo.observeZikrs().first()
        assertEquals(7, z.size)
        val d = LocalDate.of(2026, 1, 1)
        repeat(33) { repo.addZikrCount(z[0].id, d, 1, now()) }
        repo.addZikrCount(z[0].id, d, -100, now())
        repo.addZikrCount(z[0].id, d, 5, now())
        assertEquals(5, repo.observeZikrProgress(d, d).first().single().count)
    }
}
