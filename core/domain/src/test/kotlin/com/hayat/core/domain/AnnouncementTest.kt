package com.hayat.core.domain

import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.announcement.AnnouncementNotifier
import com.hayat.core.domain.announcement.AnnouncementPolicy
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.domain.announcement.FeedClient
import com.hayat.core.domain.announcement.FeedResult
import com.hayat.core.domain.announcement.SyncAnnouncementsUseCase
import com.hayat.core.domain.announcement.SyncOutcome
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.AppLanguage
import com.hayat.core.model.Announcement
import com.hayat.core.model.AnnouncementKind
import com.hayat.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AnnouncementTest {
    private val now = Instant.parse("2026-11-24T06:00:00Z")

    private fun item(id: Long, kind: AnnouncementKind = AnnouncementKind.BANNER, lang: String = "all", minVersion: Int = 0, priority: Int = 0,
                     starts: Instant = now.minusSeconds(60), ends: Instant? = null, cta: String = "") =
        Announcement(id, kind, "Title $id", "Body", "শিরোনাম $id", "বার্তা", ctaLabelEn = if (cta.isEmpty()) "" else "Open", ctaLabelBn = if (cta.isEmpty()) "" else "খুলুন",
            ctaUrl = cta, lang = lang, minVersion = minVersion, priority = priority, startsAt = starts, endsAt = ends, updatedAt = now)

    @Test fun `links are restricted to https and allow-listed deep links`() {
        listOf("hayat://water", "https://hayat.app/news", "https://example.com:8443/x?y=1").forEach { assertTrue(it, AnnouncementPolicy.isSafeLink(it)) }
        listOf("javascript:alert(1)", "http://example.com", "hayat://evil", "https://user@evil.com", "https://", "intent://x#Intent;end", "https://a.com/\nx", "file:///sdcard")
            .forEach { assertFalse(it, AnnouncementPolicy.isSafeLink(it)) }
    }

    @Test fun `sanitize drops unsafe links, clamps text and rejects untitled items`() {
        val s = AnnouncementPolicy.sanitize(item(1, cta = "javascript:alert(1)").copy(titleEn = "x".repeat(500), lang = "fr"))!!
        assertEquals("", s.ctaUrl)
        assertEquals("", s.ctaLabelEn)
        assertEquals(AnnouncementPolicy.MAX_TITLE, s.titleEn.length)
        assertEquals("all", s.lang)
        assertNull(AnnouncementPolicy.sanitize(item(2).copy(titleEn = " ", titleBn = "")))
    }

    @Test fun `visibility honours schedule, language, version and priority`() {
        val items = listOf(
            item(1, priority = 1), item(2, priority = 9), item(3, lang = "bn"), item(4, lang = "en"),
            item(5, minVersion = 5), item(6, starts = now.plusSeconds(60)), item(7, ends = now),
        )
        assertEquals(listOf(2L, 1L, 4L), AnnouncementPolicy.visible(items, now, bengali = false, versionCode = 1).map { it.id })
        assertEquals(listOf(2L, 1L, 3L, 5L), AnnouncementPolicy.visible(items, now, bengali = true, versionCode = 5).map { it.id })
    }

    @Test fun `language follows the app setting, then the device`() {
        assertTrue(AnnouncementPolicy.isBengali(AppLanguage.BENGALI, "en"))
        assertFalse(AnnouncementPolicy.isBengali(AppLanguage.ENGLISH, "bn"))
        assertTrue(AnnouncementPolicy.isBengali(AppLanguage.SYSTEM, "bn"))
    }

    private class Harness(enabled: Boolean = true, url: String = "https://hayat.example/api/v1/announcements") {
        val stored = MutableStateFlow<List<Announcement>>(emptyList())
        var etag: String? = null
        var result: FeedResult = FeedResult.NotModified
        val requests = mutableListOf<Pair<String, String?>>()
        val notified = mutableListOf<List<Long>>()
        val prefs = object : PreferencesRepository {
            val state = MutableStateFlow(UserPreferences(announcementsEnabled = enabled))
            override val preferences: Flow<UserPreferences> = state
            override suspend fun current() = state.value
            override suspend fun update(transform: (UserPreferences) -> UserPreferences) { state.value = transform(state.value) }
        }
        val repo = object : AnnouncementRepository {
            override fun observeAll() = stored
            override suspend fun etag() = etag
            override suspend fun applyFeed(items: List<Announcement>, etag: String?, fetchedAt: Instant) {
                val old = stored.value.associateBy { it.id }
                stored.value = items.map { n -> old[n.id]?.let { o -> n.copy(seen = o.seen, dismissed = o.dismissed, notified = o.notified) } ?: n }
                this@Harness.etag = etag
            }
            override suspend fun markSeen(ids: Collection<Long>) { stored.value = stored.value.map { if (it.id in ids) it.copy(seen = true) else it } }
            override suspend fun dismiss(id: Long) { stored.value = stored.value.map { if (it.id == id) it.copy(dismissed = true) else it } }
            override suspend fun markNotified(ids: Collection<Long>) { stored.value = stored.value.map { if (it.id in ids) it.copy(notified = true) else it } }
            override suspend fun clear() { stored.value = emptyList() }
        }
        val useCase = SyncAnnouncementsUseCase(
            repo,
            object : FeedClient { override suspend fun fetch(url: String, etag: String?): FeedResult { requests += url to etag; return result } },
            prefs,
            object : AnnouncementNotifier { override fun notify(items: List<Announcement>, bengali: Boolean) { notified += items.map { it.id } } },
            object : AppConfig { override val feedUrl = url; override val versionCode = 1; override val systemLanguage = "en" },
            object : TimeProvider {
                override fun now() = Instant.parse("2026-11-24T06:00:00Z")
                override fun zone(): ZoneId = ZoneOffset.UTC
                override fun today(): LocalDate = LocalDate.of(2026, 11, 24)
            },
        )
    }

    @Test fun `nothing is fetched when disabled or not configured`() = runBlocking {
        val off = Harness(enabled = false)
        assertEquals(SyncOutcome.DISABLED, off.useCase())
        val noUrl = Harness(url = "")
        assertEquals(SyncOutcome.NOT_CONFIGURED, noUrl.useCase())
        val http = Harness(url = "http://insecure.example/feed")
        assertEquals(SyncOutcome.NOT_CONFIGURED, http.useCase())
        assertTrue(off.requests.isEmpty() && noUrl.requests.isEmpty() && http.requests.isEmpty())
    }

    @Test fun `notifications are posted once and local flags survive refreshes`() = runBlocking {
        val h = Harness()
        h.result = FeedResult.Updated(listOf(item(1, AnnouncementKind.NOTIFICATION), item(2, AnnouncementKind.BANNER), item(3, AnnouncementKind.BOTH)), "\"v1\"")
        assertEquals(SyncOutcome.UPDATED, h.useCase())
        assertEquals(listOf(listOf(1L, 3L)), h.notified)
        h.repo.dismiss(2)
        assertEquals(SyncOutcome.UPDATED, h.useCase()) // same items again
        assertEquals(1, h.notified.size)
        assertTrue(h.stored.value.first { it.id == 2L }.dismissed)
        h.result = FeedResult.NotModified
        assertEquals(SyncOutcome.NOT_MODIFIED, h.useCase())
        assertEquals("\"v1\"", h.requests.last().second)
    }

    @Test fun `failures map to retry or stop`() = runBlocking {
        val h = Harness()
        h.result = FeedResult.Failed(retryable = true)
        assertEquals(SyncOutcome.RETRY, h.useCase())
        h.result = FeedResult.Failed(retryable = false)
        assertEquals(SyncOutcome.FAILED, h.useCase())
    }
}
