package com.hayat.app

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.repository.WaterRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import java.time.LocalDate
import javax.inject.Inject

/** Opens every feature screen and every "Create" editor through real navigation to catch runtime crashes. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureSmokeTest {
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
    @Inject lateinit var water: WaterRepository
    @Inject lateinit var ibadah: IbadahRepository

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            prefs.update { it.copy(onboardingCompleted = true, islamicModuleEnabled = true) }
            // Done by AppInitializer in the real Application (not used by HiltTestApplication).
            ibadah.ensurePresets(com.hayat.core.domain.worship.ZikrPresets.presets(java.time.Instant.now()))
        }
    }

    private fun waitFor(text: String) = try {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("'$text' not shown. Tree:\n" + compose.onRoot(useUnmergedTree = true).printToString().lines().filter { "Text =" in it || "ContentDescription" in it }.joinToString("\n").take(3000), e)
    }

    private fun waitForHome() = compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-screen")).fetchSemanticsNodes().isNotEmpty() }

    private fun back() {
        compose.onAllNodesWithContentDescription("Back").onFirst().performClick()
        compose.waitForIdle()
    }

    @Test fun `every feature screen opens from the feature grid`() {
        waitForHome()
        // tile label -> a text that must appear on the opened screen
        val screens = listOf(
            "Tasks" to "New task", "Routines" to "New routine", "Reminders" to "New reminder", "Water" to "Quick add",
            "Medicine" to "Add medicine", "Diet & meals" to "Meal plan", "Sleep" to "Log sleep", "Fitness" to "Log workout",
            "Ibadah" to "Zikr", "Habits" to "New habit", "Study & focus" to "Focus timer", "Finance reminders" to "New item",
            "Important dates" to "New date", "Notes & journal" to "Journal", "Routine templates" to "Bundled templates",
            "Search" to "Type to search everything stored on this phone.", "Backup & restore" to "Export backup",
        )
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("All features"))
        compose.onNodeWithText("All features").performClick()
        for ((tile, expected) in screens) {
            waitFor(tile)
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(tile))
            compose.onAllNodesWithText(tile).onFirst().performClick()
            waitFor(expected)
            back()
        }
    }

    @Test fun `every create action opens its editor`() {
        val actions = listOf(
            "Task" to "New task", "Reminder" to "New reminder", "Routine" to "New routine", "Medicine" to "Add medicine",
            "Habit" to "New habit", "Note" to "New note", "Journal entry" to "New journal entry", "Important date" to "New date",
            "Bill or payment" to "New item", "Meal plan" to "Add meal", "Log sleep" to "Log sleep", "Log water" to "250 ml",
        )
        for ((label, expected) in actions) {
            waitForHome()
            compose.onNodeWithContentDescription("Add").performClick()
            waitFor("Create")
            compose.onAllNodesWithText(label).onFirst().performClick()
            waitFor(expected)
            back()
        }
    }

    @Test fun `logging water from the screen persists an entry`() {
        waitForHome()
        compose.onNodeWithContentDescription("Add").performClick()
        waitFor("Create")
        compose.onNodeWithText("Log water").performClick()
        waitFor("250 ml")
        compose.onAllNodesWithText("250 ml").onFirst().performClick()
        compose.waitUntil(5_000) { runBlocking { water.observeLogs(LocalDate.now(), LocalDate.now()).first() }.isNotEmpty() }
        assertEquals(250, runBlocking { water.observeLogs(LocalDate.now(), LocalDate.now()).first() }.single().amountMl)
    }

    @Test fun `zikr counter increments today's count`() {
        waitForHome()
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("All features"))
        compose.onNodeWithText("All features").performClick()
        waitFor("Ibadah")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Ibadah"))
        compose.onNodeWithText("Ibadah").performClick()
        waitFor("Zikr")
        compose.onNodeWithText("Zikr").performClick()
        waitFor("SubhanAllah")
        compose.onAllNodesWithText("SubhanAllah").onFirst().performClick()
        waitFor("Tap to count")
        repeat(3) { compose.onNodeWithText("Tap to count", useUnmergedTree = true).performClick() }
        compose.waitUntil(5_000) { runBlocking { ibadah.observeZikrProgress(LocalDate.now(), LocalDate.now()).first() }.sumOf { it.count } == 3 }
        assertTrue(runBlocking { ibadah.observeZikrProgress(LocalDate.now(), LocalDate.now()).first() }.any { it.count == 3 })
    }
}
