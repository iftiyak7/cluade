package com.hayat.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Cached copy of the public announcements feed (server content, not user data). Excluded from
 * backups. `seen`, `dismissed` and `notified` are local-only flags.
 */
@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey val id: Long,
    val kind: String,
    @ColumnInfo(name = "title_en") val titleEn: String,
    @ColumnInfo(name = "body_en") val bodyEn: String,
    @ColumnInfo(name = "title_bn") val titleBn: String,
    @ColumnInfo(name = "body_bn") val bodyBn: String,
    @ColumnInfo(name = "cta_label_en") val ctaLabelEn: String,
    @ColumnInfo(name = "cta_label_bn") val ctaLabelBn: String,
    @ColumnInfo(name = "cta_url") val ctaUrl: String,
    val lang: String,
    @ColumnInfo(name = "min_version") val minVersion: Int,
    val priority: Int,
    @ColumnInfo(name = "starts_at") val startsAt: Instant,
    @ColumnInfo(name = "ends_at") val endsAt: Instant?,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val seen: Boolean,
    val dismissed: Boolean,
    val notified: Boolean,
)

/** Single row (id = 1) holding the feed's ETag for conditional requests. */
@Entity(tableName = "announcement_feed_state")
data class AnnouncementFeedStateEntity(
    @PrimaryKey val id: Int = 1,
    val etag: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)
