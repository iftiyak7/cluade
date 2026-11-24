package com.hayat.app

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.Announcement
import com.hayat.core.model.AnnouncementKind
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import javax.inject.Inject

/** Banner on Home → dismiss; bell → inbox; content follows the user's opt-out. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnouncementFlowTest {
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
    @Inject lateinit var announcements: AnnouncementRepository

    private fun item(id: Long, kind: AnnouncementKind, title: String) = Announcement(
        id, kind, title, "Body of $title", "শিরোনাম $id", "বার্তা", ctaLabelEn = "Open water", ctaUrl = "hayat://water",
        startsAt = Instant.now().minusSeconds(3600), updatedAt = Instant.now(),
    )

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            prefs.update { it.copy(onboardingCompleted = true) }
            announcements.applyFeed(
                listOf(item(1, AnnouncementKind.BANNER, "Ramadan is coming"), item(2, AnnouncementKind.NOTIFICATION, "New feature")),
                "\"e1\"", Instant.now(),
            )
        }
    }

    private fun waitText(t: String) = compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun `banner shows on home, opens its link and can be dismissed`() {
        waitText("Ramadan is coming")
        // Notification-only items are not shown as banners.
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("New feature")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Open water").performClick()
        waitText("250 ml")
        compose.onNodeWithContentDescription("Back").performClick()
        waitText("Ramadan is coming")
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitUntil(5_000) { runBlocking { announcements.observeAll().first() }.first { it.id == 1L }.dismissed }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Ramadan is coming")).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun `bell opens the inbox and marks items as seen`() {
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Announcements, 2 new")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Announcements, 2 new").performClick()
        waitText("New feature")
        compose.waitUntil(5_000) { runBlocking { announcements.observeAll().first() }.all { it.seen } }
    }

    @Test fun `turning announcements off hides banner and bell`() {
        waitText("Ramadan is coming")
        runBlocking { prefs.update { it.copy(announcementsEnabled = false) } }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Ramadan is coming")).fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Announcements")).fetchSemanticsNodes().isEmpty())
    }
}
