package com.hayat.core.network

import com.hayat.core.model.Announcement
import com.hayat.core.model.AnnouncementKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

@Serializable
private data class FeedDto(val version: Int = 0, val items: List<ItemDto> = emptyList())

@Serializable
private data class ItemDto(
    val id: Long,
    val kind: String,
    @SerialName("title_en") val titleEn: String = "",
    @SerialName("body_en") val bodyEn: String = "",
    @SerialName("title_bn") val titleBn: String = "",
    @SerialName("body_bn") val bodyBn: String = "",
    @SerialName("cta_label_en") val ctaLabelEn: String = "",
    @SerialName("cta_label_bn") val ctaLabelBn: String = "",
    @SerialName("cta_url") val ctaUrl: String = "",
    val lang: String = "all",
    @SerialName("min_version") val minVersion: Int = 0,
    val priority: Int = 0,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Parses feed version 1. Unknown fields are ignored; malformed items are skipped, not fatal. */
object FeedParser {
    const val MAX_ITEMS = 50
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Returns null when the document itself is unusable (wrong version or not JSON). */
    fun parse(body: String): List<Announcement>? {
        val feed = runCatching { json.decodeFromString(FeedDto.serializer(), body) }.getOrNull() ?: return null
        if (feed.version != 1) return null
        return feed.items.take(MAX_ITEMS).mapNotNull { it.toModel() }
    }

    private fun time(s: String?): Instant? = s?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    private fun ItemDto.toModel(): Announcement? {
        val k = when (kind) { "banner" -> AnnouncementKind.BANNER; "notification" -> AnnouncementKind.NOTIFICATION; "both" -> AnnouncementKind.BOTH; else -> return null }
        val start = time(startsAt) ?: return null
        return Announcement(
            id = id, kind = k, titleEn = titleEn, bodyEn = bodyEn, titleBn = titleBn, bodyBn = bodyBn,
            ctaLabelEn = ctaLabelEn, ctaLabelBn = ctaLabelBn, ctaUrl = ctaUrl, lang = lang, minVersion = minVersion, priority = priority,
            startsAt = start, endsAt = time(endsAt), updatedAt = time(updatedAt) ?: start,
        )
    }
}
