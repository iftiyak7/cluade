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

    @Before fun seed() {
        assumeTrue("screenshot capture disabled", outDir.isNotBlank())
        hilt.inject()
        runBlocking {
            val now = time.now(); val today = time.today()
            ibadah.ensurePresets(ZikrPresets.presets(now))
            prefs.update { it.copy(onboardingCompleted = true, userName = "Ayesha", islamicModuleEnabled = true, prayer = it.prayer.copy(location = PrayerCities.all.first())) }
            val taskIds = listOf("Submit project report" to Priority.HIGH, "Call Ammu" to Priority.MEDIUM, "Pay electricity bill" to Priority.URGENT).mapIndexed { i, (t, p) ->
                val id = ids.newId()
                saveTask(Task(id, t, priority = p, dueDate = today, dueTime = LocalTime.of(8 + i * 4, 0), createdAt = now, updatedAt = now))
                id
            }
            completeTask(taskIds.first(), true)
            routines.applyTemplate(BuiltInTemplates.byId("builtin.balanced")!!, "Balanced day", today.minusDays(1), 0xFF167C5A)
            routineRepo.observeRoutines().first().flatMap { it.activities }.sortedBy { it.startTime }.take(4).forEach { a ->
                routines.log(a.id, today, com.hayat.core.model.ActivityStatus.COMPLETED)
                routines.log(a.id, today.minusDays(1), com.hayat.core.model.ActivityStatus.COMPLETED)
            }
            listOf(250, 250, 500).forEach { water.add(it) }
            medicines.save(Medicine(ids.newId(), "Vitamin D", dose = "1000", unit = "IU", startDate = today,
                schedules = listOf(MedicineSchedule(ids.newId(), "", LocalTime.of(21, 0))), createdAt = now, updatedAt = now))
            habits.save(Habit(ids.newId(), "Read 20 pages", color = 0xFF3B6FD6, createdAt = now.minusSeconds(86400L * 10), updatedAt = now))
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

    private fun home() = compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Customise home") or hasContentDescription("হোম সাজান")).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(t: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun openFeature(grid: String, tile: String, expect: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(grid))
        compose.onNodeWithText(grid).performClick()
        waitText(tile)
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(tile))
        compose.onAllNodesWithText(tile).onFirst().performClick()
        waitText(expect)
    }

    @Test @Config(qualifiers = "+en") fun english_light() {
        home(); shot("en-home")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Anytime"))
        shot("en-home-plan")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("All features"))
        shot("en-home-cards")
        openFeature("All features", "Ibadah", "Fajr"); shot("en-ibadah")
    }

    @Test @Config(qualifiers = "+en") fun english_progress() { home(); compose.onNodeWithContentDescription("Progress").performClick(); waitText("Consistency score"); compose.mainClock.advanceTimeBy(1500); shot("en-progress") }

    @Test @Config(qualifiers = "+en") fun english_water() { home(); openFeature("All features", "Water", "Quick add"); shot("en-water") }

    @Test @Config(qualifiers = "+en") fun english_calendar() { home(); compose.onNodeWithContentDescription("Calendar").performClick(); waitText("Day"); shot("en-calendar") }

    @Test @Config(qualifiers = "+en-night") fun english_dark() {
        runBlocking { prefs.update { it.copy(theme = ThemeMode.DARK) } }
        home(); shot("en-home-dark")
    }

    @Test @Config(qualifiers = "+bn") fun bengali() {
        runBlocking { prefs.update { it.copy(language = AppLanguage.BENGALI) } }
        home(); shot("bn-home")
        openFeature("সব ফিচার", "ইবাদত", "ফজর"); shot("bn-ibadah")
    }
}
