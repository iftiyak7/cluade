package com.hayat.core.data.repository

import com.hayat.core.database.dao.AnnouncementDao
import com.hayat.core.database.entity.AnnouncementEntity
import com.hayat.core.database.entity.AnnouncementFeedStateEntity
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.model.Announcement
import com.hayat.core.model.AnnouncementKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomAnnouncementRepository @Inject constructor(private val dao: AnnouncementDao) : AnnouncementRepository {
    override fun observeAll(): Flow<List<Announcement>> = dao.observeAll().map { list -> list.mapNotNull { it.toModel() } }
    override suspend fun etag(): String? = dao.etag()
    override suspend fun applyFeed(items: List<Announcement>, etag: String?, fetchedAt: Instant) =
        dao.replace(items.map { it.toEntity() }, AnnouncementFeedStateEntity(etag = etag, fetchedAt = fetchedAt))
    override suspend fun markSeen(ids: Collection<Long>) { if (ids.isNotEmpty()) dao.markSeen(ids.toList()) }
    override suspend fun dismiss(id: Long) = dao.dismiss(id)
    override suspend fun markNotified(ids: Collection<Long>) { if (ids.isNotEmpty()) dao.markNotified(ids.toList()) }
    override suspend fun clear() = dao.clear()
}

private fun AnnouncementEntity.toModel(): Announcement? {
    val k = runCatching { AnnouncementKind.valueOf(kind) }.getOrNull() ?: return null
    return Announcement(id, k, titleEn, bodyEn, titleBn, bodyBn, ctaLabelEn, ctaLabelBn, ctaUrl, lang, minVersion, priority, startsAt, endsAt, updatedAt, seen, dismissed, notified)
}

private fun Announcement.toEntity() = AnnouncementEntity(
    id, kind.name, titleEn, bodyEn, titleBn, bodyBn, ctaLabelEn, ctaLabelBn, ctaUrl, lang, minVersion, priority, startsAt, endsAt, updatedAt, seen, dismissed, notified,
)
