package com.hayat.core.domain.announcement

import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.AppLanguage
import com.hayat.core.model.Announcement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/** Build/runtime facts the announcement feature needs (provided by :app). */
interface AppConfig {
    /** HTTPS URL of the feed, or blank when this build has no server configured. */
    val feedUrl: String
    val versionCode: Int
    /** Language of the device locale (used when the in-app language is "system"). */
    val systemLanguage: String
}

interface AnnouncementRepository {
    fun observeAll(): Flow<List<Announcement>>
    suspend fun etag(): String?
    /** Replaces the stored feed with [items], keeping local seen/dismissed/notified flags. */
    suspend fun applyFeed(items: List<Announcement>, etag: String?, fetchedAt: Instant)
    suspend fun markSeen(ids: Collection<Long>)
    suspend fun dismiss(id: Long)
    suspend fun markNotified(ids: Collection<Long>)
    suspend fun clear()
}

sealed interface FeedResult {
    data object NotModified : FeedResult
    data class Updated(val items: List<Announcement>, val etag: String?) : FeedResult
    data class Failed(val retryable: Boolean) : FeedResult
}

/** Downloads the feed. Implementations send no identifiers: no cookies, no device info. */
interface FeedClient {
    suspend fun fetch(url: String, etag: String?): FeedResult
}

/** Posts system notifications for new notification-type announcements. */
interface AnnouncementNotifier {
    fun notify(items: List<Announcement>, bengali: Boolean)
}

object AnnouncementPolicy {
    const val MAX_TITLE = 80
    const val MAX_BODY = 400
    const val MAX_CTA = 30

    /** In-app destinations an announcement may open; anything else is dropped. */
    val deepLinks: Set<String> = setOf(
        "hayat://home", "hayat://calendar", "hayat://tasks", "hayat://reminders", "hayat://water", "hayat://medicine",
        "hayat://habits", "hayat://ibadah", "hayat://notes", "hayat://progress", "hayat://settings",
    )

    fun isSafeLink(url: String): Boolean {
        if (url in deepLinks) return true
        if (url.length > 500 || !url.startsWith("https://")) return false
        val rest = url.removePrefix("https://")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotEmpty() && '@' !in host && host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' } &&
            url.none { it.isWhitespace() || it.code < 0x20 }
    }

    private fun clean(s: String, max: Int): String =
        s.filter { it == '\n' || it.code >= 0x20 }.trim().let { if (it.length > max) it.take(max) else it }

    /** Defensive client-side check of server data (the server validates too). Returns null if unusable. */
    fun sanitize(a: Announcement): Announcement? {
        val c = a.copy(
            titleEn = clean(a.titleEn, MAX_TITLE), bodyEn = clean(a.bodyEn, MAX_BODY),
            titleBn = clean(a.titleBn, MAX_TITLE), bodyBn = clean(a.bodyBn, MAX_BODY),
            ctaLabelEn = clean(a.ctaLabelEn, MAX_CTA), ctaLabelBn = clean(a.ctaLabelBn, MAX_CTA),
            ctaUrl = a.ctaUrl.trim().takeIf { isSafeLink(it) } ?: "",
            lang = a.lang.takeIf { it in setOf("all", "en", "bn") } ?: "all",
        )
        if (c.titleEn.isBlank() && c.titleBn.isBlank()) return null
        return if (c.ctaUrl.isBlank()) c.copy(ctaLabelEn = "", ctaLabelBn = "") else c
    }

    fun isBengali(language: AppLanguage, systemLanguage: String): Boolean = when (language) {
        AppLanguage.BENGALI -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM -> systemLanguage == "bn"
    }

    /** Items the user should see now, highest priority first. */
    fun visible(items: List<Announcement>, now: Instant, bengali: Boolean, versionCode: Int): List<Announcement> =
        items.filter { a ->
            !now.isBefore(a.startsAt) && (a.endsAt == null || now.isBefore(a.endsAt)) &&
                versionCode >= a.minVersion &&
                when (a.lang) { "en" -> !bengali; "bn" -> bengali; else -> true }
        }.sortedWith(compareByDescending<Announcement> { it.priority }.thenByDescending { it.startsAt })
}

enum class SyncOutcome { DISABLED, NOT_CONFIGURED, NOT_MODIFIED, UPDATED, RETRY, FAILED }

/**
 * Fetches the feed (only when the user has announcements turned on and the build has a server),
 * stores it, and notifies about new notification-type items exactly once each.
 */
class SyncAnnouncementsUseCase @Inject constructor(
    private val repo: AnnouncementRepository,
    private val client: FeedClient,
    private val prefs: PreferencesRepository,
    private val notifier: AnnouncementNotifier,
    private val config: AppConfig,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): SyncOutcome {
        val p = prefs.current()
        if (!p.announcementsEnabled) return SyncOutcome.DISABLED
        if (!config.feedUrl.startsWith("https://")) return SyncOutcome.NOT_CONFIGURED
        val outcome = when (val r = client.fetch(config.feedUrl, repo.etag())) {
            FeedResult.NotModified -> SyncOutcome.NOT_MODIFIED
            is FeedResult.Updated -> {
                repo.applyFeed(r.items.mapNotNull(AnnouncementPolicy::sanitize), r.etag, time.now())
                SyncOutcome.UPDATED
            }
            is FeedResult.Failed -> return if (r.retryable) SyncOutcome.RETRY else SyncOutcome.FAILED
        }
        notifyNew(AnnouncementPolicy.isBengali(p.language, config.systemLanguage))
        return outcome
    }

    private suspend fun notifyNew(bengali: Boolean) {
        val all = repo.observeAll().first()
        val due = AnnouncementPolicy.visible(all, time.now(), bengali, config.versionCode)
            .filter { it.showsNotification && !it.notified && !it.dismissed }
            .take(3) // never a burst
        if (due.isEmpty()) return
        notifier.notify(due, bengali)
        repo.markNotified(due.map { it.id })
    }
}
