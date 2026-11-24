package com.hayat.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.hayat.core.database.entity.AnnouncementEntity
import com.hayat.core.database.entity.AnnouncementFeedStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY priority DESC, starts_at DESC")
    abstract fun observeAll(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements") abstract suspend fun all(): List<AnnouncementEntity>
    @Upsert abstract suspend fun upsert(items: List<AnnouncementEntity>)
    @Query("DELETE FROM announcements WHERE id NOT IN (:keep)") abstract suspend fun deleteExcept(keep: List<Long>)
    @Query("DELETE FROM announcements") abstract suspend fun deleteAll()
    @Query("UPDATE announcements SET seen = 1 WHERE id IN (:ids)") abstract suspend fun markSeen(ids: List<Long>)
    @Query("UPDATE announcements SET dismissed = 1, seen = 1 WHERE id = :id") abstract suspend fun dismiss(id: Long)
    @Query("UPDATE announcements SET notified = 1 WHERE id IN (:ids)") abstract suspend fun markNotified(ids: List<Long>)

    @Query("SELECT etag FROM announcement_feed_state WHERE id = 1") abstract suspend fun etag(): String?
    @Upsert abstract suspend fun setState(state: AnnouncementFeedStateEntity)
    @Query("DELETE FROM announcement_feed_state") abstract suspend fun clearState()

    /** Replaces the cached feed atomically, carrying over local flags for items that remain. */
    @Transaction
    open suspend fun replace(items: List<AnnouncementEntity>, state: AnnouncementFeedStateEntity) {
        val old = all().associateBy { it.id }
        val merged = items.map { n -> old[n.id]?.let { o -> n.copy(seen = o.seen, dismissed = o.dismissed, notified = o.notified) } ?: n }
        if (merged.isEmpty()) deleteAll() else deleteExcept(merged.map { it.id })
        upsert(merged)
        setState(state)
    }

    @Transaction
    open suspend fun clear() { deleteAll(); clearState() }
}
