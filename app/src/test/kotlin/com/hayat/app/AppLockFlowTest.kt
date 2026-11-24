package com.hayat.app

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.security.AppLockManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
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
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppLockFlowTest {
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
    @Inject lateinit var lock: AppLockManager

    /** The test keystore is unavailable under Robolectric, so the PIN record uses an in-memory wrapper. */
    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            lock.setPin("2580".toCharArray())
            prefs.update { it.copy(onboardingCompleted = true, appLock = it.appLock.copy(enabled = true)) }
        }
    }

    private fun enter(pin: String) {
        pin.forEach { compose.onNodeWithContentDescription("Digit $it").performClick() }
        compose.onNodeWithText("Unlock").performClick()
    }

    @Test fun `locked app hides content and unlocks only with the right pin`() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Hayat is locked").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("home content must not be exposed while locked",
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-screen")).fetchSemanticsNodes().isEmpty())
        enter("1111")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Wrong PIN", substring = true).fetchSemanticsNodes().isNotEmpty() }
        enter("2580")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("home-screen")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Hayat is locked").fetchSemanticsNodes().isEmpty())
    }
}
