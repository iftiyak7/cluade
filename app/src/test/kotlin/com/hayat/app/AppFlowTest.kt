package com.hayat.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hayat.core.domain.repository.TaskRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
import javax.inject.Inject

/**
 * End-to-end on the JVM: real Hilt graph, Room database, DataStore, navigation and Compose UI.
 * Covers onboarding, bottom navigation, the feature grid and task creation with persistence.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
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

    @Inject lateinit var tasks: TaskRepository

    @Before fun inject() = hilt.inject()

    /** First launch: onboarding → "Connect to your server" (fake server) → Home. */
    private fun connectToServer(url: String = "https://hayat.test") {
        waitForText("Connect to your server")
        compose.onNode(hasSetTextAction() and hasText("Server URL")).performTextInput(url)
        compose.onNodeWithText("Test connection").performClick()
        waitForText("Connected · 42 ms")
        compose.onNode(hasSetTextAction() and hasText("Email")).performTextInput("ayesha@example.com")
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("a long password 1")
        compose.onNodeWithText("Sign in").performClick()
    }

    private fun waitForHome() = compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-screen")).fetchSemanticsNodes().isNotEmpty() }

    private fun openAllFeatures() {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("All features"))
        compose.onNodeWithText("All features").performClick()
    }

    private fun waitForText(text: String, timeout: Long = 10_000) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            println("UI TREE while waiting for '$text':\n" + runCatching { compose.onRoot(useUnmergedTree = true).printToString() }.getOrElse { it.toString() })
            throw e
        }
    }

    @Test fun `skip onboarding, navigate tabs, create a task and see it persisted`() {
        waitForText("Skip")
        compose.onNodeWithText("Skip").performClick()
        connectToServer()

        // Home dashboard with bottom navigation.
        waitForHome()
        compose.onNodeWithContentDescription("Calendar").performClick()
        waitForText("Day")
        compose.onNodeWithContentDescription("Progress").performClick()
        waitForText("Consistency score")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Privacy & security")
        compose.onNodeWithContentDescription("Home").performClick()
        waitForHome()

        // Open Tasks from the feature grid and create a task due today.
        openAllFeatures()
        waitForText("Tasks")
        compose.onAllNodesWithText("Tasks").onFirst().performClick()
        waitForText("New task")
        compose.onAllNodesWithText("New task", useUnmergedTree = true).onFirst().performClick()
        waitForText("Title")
        compose.onNode(hasSetTextAction() and hasText("Title")).performTextInput("Pay electricity bill")
        compose.onNodeWithText("Save").performClick()

        waitForText("Pay electricity bill")
        compose.onNodeWithText("Pay electricity bill").assertIsDisplayed()
        val stored = runBlocking { tasks.observeTasks().first() }
        assertEquals(listOf("Pay electricity bill"), stored.map { it.title })
    }

    @Test fun `empty title is rejected with a validation message`() {
        waitForText("Skip")
        compose.onNodeWithText("Skip").performClick()
        connectToServer()
        waitForHome()
        compose.onNodeWithContentDescription("Add").performClick()
        waitForText("Create")
        compose.onNodeWithText("Task").performClick()
        waitForText("Save")
        compose.onNodeWithText("Save").performClick()
        waitForText("This field is required")
        assertEquals(0, runBlocking { tasks.observeTasks().first() }.size)
    }

    @Test fun `connect screen explains SSL errors and blocks sign-in until the test succeeds`() {
        waitForText("Skip")
        compose.onNodeWithText("Skip").performClick()
        waitForText("Connect to your server")
        compose.onNode(hasSetTextAction() and hasText("Server URL")).performTextInput("https://badcert.example")
        compose.onNodeWithText("Test connection").performClick()
        waitForText("Secure connection failed (SSL certificate problem). Make sure HTTPS is set up correctly on the server.")
        // Sign-in stays locked.
        waitForText("Test the connection first.")
        assertEquals(0, compose.onAllNodes(hasSetTextAction() and hasText("Password")).fetchSemanticsNodes().size)
    }

    @Test fun `http addresses are rejected before any request`() {
        waitForText("Skip")
        compose.onNodeWithText("Skip").performClick()
        waitForText("Connect to your server")
        compose.onNode(hasSetTextAction() and hasText("Server URL")).performTextInput("http://plain.example")
        compose.onNodeWithText("Test connection").performClick()
        waitForText("Only secure https:// addresses are allowed.")
    }
}
