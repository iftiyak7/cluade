package com.hayat.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.health.MedicineUseCases
import com.hayat.core.domain.health.WaterUseCases
import com.hayat.core.domain.productivity.HabitUseCases
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.routine.RoutineActionsUseCase
import com.hayat.core.domain.task.SaveTaskUseCase
import com.hayat.core.domain.template.BuiltInTemplates
import com.hayat.core.domain.worship.PrayerCities
import com.hayat.core.domain.worship.ZikrPresets
import com.hayat.core.model.AppLanguage
import com.hayat.core.model.Habit
import com.hayat.core.model.Medicine
import com.hayat.core.model.MedicineSchedule
import com.hayat.core.model.Prayer
import com.hayat.core.model.PrayerStatus
import com.hayat.core.model.Priority
import com.hayat.core.model.Task
import com.hayat.core.model.ThemeMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalTime
import javax.inject.Inject

/** Captures real screens for the website. Skipped unless -PscreenshotsDir is given. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotCapture {
    private val outDir = System.getProperty("hayat.screenshots.dir").orEmpty()
    private val hilt = HiltAndroidRule(this)
    private val workManager = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<Context>())
                base.evaluate()
            }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(hilt).around(workManager).around(compose)

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var saveTask: SaveTaskUseCase
    @Inject lateinit var routines: RoutineActionsUseCase
    @Inject lateinit var water: WaterUseCases
    @Inject lateinit var medicines: MedicineUseCases
    @Inject lateinit var habits: HabitUseCases
    @Inject lateinit var ibadah: IbadahRepository
    @Inject lateinit var time: TimeProvider
    @Inject lateinit var ids: IdGenerator
    @Inject lateinit var completeTask: com.hayat.core.domain.task.CompleteTaskUseCase
    @Inject lateinit var routineRepo: com.hayat.core.domain.repository.RoutineRepository
    @Inject lateinit var sessions: com.hayat.core.domain.sync.SessionStore

    @Before fun seed() {
        assumeTrue("screenshot capture disabled", outDir.isNotBlank())
        // Device time zone as on a phone in Bangladesh (greeting and prayer times use it).
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Dhaka"))
        hilt.inject()
        runBlocking {
            val now = time.now(); val today = time.today()
            ibadah.ensurePresets(ZikrPresets.presets(now))
            prefs.update { it.copy(onboardingCompleted = true, theme = ThemeMode.LIGHT, userName = "Ayesha", islamicModuleEnabled = true, prayer = it.prayer.copy(location = PrayerCities.all.first())) }
            val taskIds = listOf("Submit project report" to Priority.HIGH, "Call Ammu" to Priority.MEDIUM, "Pay electricity bill" to Priority.URGENT).mapIndexed { i, (t, p) ->
                val id = ids.newId()
                saveTask(Task(id, t, priority = p, dueDate = today, dueTime = LocalTime.of(8 + i * 4, 0), createdAt = now, updatedAt = now))
                id
            }
            completeTask(taskIds.first(), true)
            // Localise item titles exactly like the onboarding/templates screens do.
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val tpl = BuiltInTemplates.byId("builtin.balanced")!!
            val localized = tpl.copy(items = tpl.items.map { item -> com.hayat.core.ui.TemplateStrings.item(item.title)?.let { item.copy(title = ctx.getString(it)) } ?: item })
            routines.applyTemplate(localized, "Balanced day", today.minusDays(1), 0xFF0E7C66)
            routineRepo.observeRoutines().first().flatMap { it.activities }.sortedBy { it.startTime }.take(4).forEach { a ->
                routines.log(a.id, today, com.hayat.core.model.ActivityStatus.COMPLETED)
                routines.log(a.id, today.minusDays(1), com.hayat.core.model.ActivityStatus.COMPLETED)
            }
            listOf(250, 250, 500).forEach { water.add(it) }
            medicines.save(Medicine(ids.newId(), "Vitamin D", dose = "1000", unit = "IU", startDate = today,
                schedules = listOf(MedicineSchedule(ids.newId(), "", LocalTime.of(21, 0))), createdAt = now, updatedAt = now))
            val read = ids.newId()
            habits.save(Habit(read, "Read 20 pages", color = 0xFF3B6FD6, createdAt = now.minusSeconds(86400L * 10), updatedAt = now))
            val walk = ids.newId()
            habits.save(Habit(walk, "Morning walk", color = 0xFF0E7C66, createdAt = now.minusSeconds(86400L * 10), updatedAt = now))
            habits.save(Habit(ids.newId(), "Drink 8 glasses", targetPerDay = 8, color = 0xFF1F5FB8, createdAt = now.minusSeconds(86400L * 10), updatedAt = now))
            habits.increment(walk, today, 1)
            (1..4).forEach { d -> habits.increment(walk, today.minusDays(d.toLong()), 1); habits.increment(read, today.minusDays(d.toLong()), 1) }
            sessions.save(com.hayat.core.domain.sync.ServerSession("https://api.hayat.example", "ayesha@example.com", "a", "r", now.plusSeconds(900), 3, now.minusSeconds(120)))
            ibadah.setPrayerStatus(today, Prayer.FAJR, PrayerStatus.PRAYED, now)
            ibadah.setPrayerStatus(today, Prayer.DHUHR, PrayerStatus.PRAYED, now)
            ibadah.addZikrCount("preset.subhanallah", today, 33, now)
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir).mkdirs()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun home() = compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-screen")).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(t: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun openFeature(grid: String, tile: String, expect: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(grid))
        compose.onNodeWithText(grid).performClick()
        waitText(tile)
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(tile))
        compose.onAllNodesWithText(tile).onFirst().performClick()
        waitText(expect)
    }

    private fun scrollTo(text: String) = compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }

    @Test @Config(qualifiers = "+en") fun english_light() {
        home(); settle(); shot("en-home")
        scrollTo("More for today"); compose.onNodeWithText("More for today").performClick(); settle(); shot("en-home-2")
        openFeature("All features", "Ibadah", "Fajr"); shot("en-ibadah")
    }

    @Test @Config(qualifiers = "+en") fun english_progress() {
        home(); compose.onNodeWithContentDescription("Progress").performClick(); waitText("Consistency score"); settle(); shot("en-progress")
        scrollTo("Achievements"); settle(); shot("en-progress-2")
    }

    @Test @Config(qualifiers = "+en") fun english_add_sheet() { home(); compose.onNodeWithContentDescription("Add").performClick(); waitText("More"); settle(); shot("en-add-sheet") }

    @Test @Config(qualifiers = "+en") fun english_connect() {
        runBlocking { sessions.clear(); prefs.update { it.copy(serverSetupPending = true) } }
        waitText("Connect to your server")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction() and hasText("Server URL")).performTextInput("https://api.mydomain.com")
        compose.onNodeWithText("Test connection").performClick()
        waitText("Connected · 42 ms"); settle(); shot("en-connect")
    }

    @Test @Config(qualifiers = "+en") fun english_server_settings() {
        home(); compose.onNodeWithContentDescription("Settings").performClick(); waitText("Server"); compose.onNodeWithText("Server").performClick()
        waitText("Sync now"); settle(); shot("en-server")
    }

    @Test @Config(qualifiers = "+en") fun english_water() { home(); openFeature("All features", "Water", "Quick add"); shot("en-water") }

    @Test @Config(qualifiers = "+en") fun english_calendar() { home(); compose.onNodeWithContentDescription("Calendar").performClick(); waitText("Day"); shot("en-calendar") }

    @Test @Config(qualifiers = "+en-night") fun english_dark() {
        runBlocking { prefs.update { it.copy(theme = ThemeMode.DARK) } }
        home(); settle(); shot("en-home-dark")
        scrollTo("Today's habits"); settle(); shot("en-home-dark-2")
        compose.onNodeWithContentDescription("Progress").performClick(); waitText("Consistency score"); settle(); shot("en-progress-dark")
    }

    @Test @Config(qualifiers = "+bn") fun bengali() {
        runBlocking { prefs.update { it.copy(language = AppLanguage.BENGALI, theme = ThemeMode.DARK) } }
        home(); settle(); shot("bn-home")
        scrollTo("আজকের অভ্যাস"); settle(); shot("bn-home-2")
        compose.onNodeWithContentDescription("অগ্রগতি").performClick(); waitText("ধারাবাহিকতা স্কোর"); settle(); shot("bn-progress")
    }
}
