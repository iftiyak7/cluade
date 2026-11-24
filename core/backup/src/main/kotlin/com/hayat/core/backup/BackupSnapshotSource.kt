package com.hayat.core.backup

import com.hayat.core.common.AppResult
import com.hayat.core.database.HayatDatabase
import com.hayat.core.domain.sync.SnapshotSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync snapshots use the backup format (schema-driven, allow-listed tables, validated on restore)
 * compressed with gzip. Restores run in one transaction, so a bad download never leaves partial data.
 */
@Singleton
class BackupSnapshotSource @Inject constructor(
    private val db: HayatDatabase,
    private val backup: HayatBackupService,
) : SnapshotSource {
    /** Changes observed until this time (ms) were caused by our own import and are ignored. */
    private val ignoreUntil = AtomicLong(0)

    override suspend fun export(): ByteArray = withContext(Dispatchers.IO) {
        val raw = ByteArrayOutputStream()
        val r = backup.export(raw, null)
        check(r is AppResult.Success) { "export failed" }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw.toByteArray()) }
        out.toByteArray()
    }

    override suspend fun import(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val plain = runCatching { GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() } }.getOrNull() ?: return@withContext false
        ignoreUntil.set(Long.MAX_VALUE)
        try {
            backup.restore(ByteArrayInputStream(plain), null) is AppResult.Success
        } finally {
            ignoreUntil.set(System.currentTimeMillis() + 1500) // invalidation callbacks arrive slightly later
        }
    }

    override suspend fun hasUserData(): Boolean = withContext(Dispatchers.IO) {
        // Seeded rows (default categories, zikr presets) do not count as user data.
        val tables = HayatBackupService.TABLES - setOf("categories", "zikr_definitions")
        val sql = tables.joinToString(" + ") { "(SELECT COUNT(*) FROM `$it`)" }
        db.openHelper.readableDatabase.query("SELECT $sql").use { c -> c.moveToFirst() && c.getLong(0) > 0 }
    }

    override fun changes(): Flow<Unit> =
        db.invalidationTracker.createFlow(*HayatBackupService.TABLES.toTypedArray(), emitInitialState = false)
            .filter { System.currentTimeMillis() > ignoreUntil.get() }
            .map { }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SnapshotModule {
    @Binds abstract fun source(impl: BackupSnapshotSource): SnapshotSource
}
