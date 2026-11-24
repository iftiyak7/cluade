package com.hayat.app

import android.content.Context
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hayat.core.domain.repository.PreferencesRepository
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

/** Regression: content must start below the status bar (it once drew underneath it on phones). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsetsTest {
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

    @Before fun setUp() {
        hilt.inject()
        runBlocking { prefs.update { it.copy(onboardingCompleted = true) } }
    }

    @Test fun `home header sits below the status bar and nav above the navigation bar`() {
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search")).fetchSemanticsNodes().isNotEmpty() }
        val statusBar = 40.dp
        compose.runOnUiThread {
            val px = (statusBar.value * compose.activity.resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px))
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
        val searchTop = compose.onNodeWithContentDescription("Search").getBoundsInRoot().top
        assertTrue("header top $searchTop must be below the ${statusBar} status bar", searchTop >= statusBar)
        val homeTab = compose.onNodeWithContentDescription("Home").getBoundsInRoot()
        val rootHeight = compose.onNode(androidx.compose.ui.test.isRoot()).getBoundsInRoot().bottom
        assertTrue("bottom bar must sit above the navigation bar", homeTab.bottom <= rootHeight - statusBar)
    }
}
