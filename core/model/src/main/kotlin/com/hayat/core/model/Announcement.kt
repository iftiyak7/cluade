package com.hayat.core.model

import java.time.Instant

enum class AnnouncementKind { BANNER, NOTIFICATION, BOTH }

/**
 * A message published from the Hayat admin panel. Downloaded anonymously; [seen], [dismissed] and
 * [notified] are local-only state and never leave the device.
 */
data class Announcement(
    val id: Long,
    val kind: AnnouncementKind,
    val titleEn: String,
    val bodyEn: String,
    val titleBn: String,
    val bodyBn: String,
    val ctaLabelEn: String = "",
    val ctaLabelBn: String = "",
    val ctaUrl: String = "",
    /** "all", "en" or "bn". */
    val lang: String = "all",
    val minVersion: Int = 0,
    val priority: Int = 0,
    val startsAt: Instant,
    val endsAt: Instant? = null,
    val updatedAt: Instant,
    val seen: Boolean = false,
    val dismissed: Boolean = false,
    val notified: Boolean = false,
) {
    val showsBanner: Boolean get() = kind != AnnouncementKind.NOTIFICATION
    val showsNotification: Boolean get() = kind != AnnouncementKind.BANNER

    fun title(bengali: Boolean): String = if (bengali) titleBn.ifBlank { titleEn } else titleEn.ifBlank { titleBn }
    fun body(bengali: Boolean): String = if (bengali) bodyBn.ifBlank { bodyEn } else bodyEn.ifBlank { bodyBn }
    fun ctaLabel(bengali: Boolean): String = if (bengali) ctaLabelBn.ifBlank { ctaLabelEn } else ctaLabelEn.ifBlank { ctaLabelBn }
}
