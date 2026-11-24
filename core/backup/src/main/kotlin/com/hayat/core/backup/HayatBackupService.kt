package com.hayat.core.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hayat.core.common.AppError
import com.hayat.core.common.AppException
import com.hayat.core.common.AppResult
import com.hayat.core.common.BackupFailure
import com.hayat.core.common.IdGenerator
import com.hayat.core.common.TimeProvider
import com.hayat.core.common.appCatching
import com.hayat.core.database.HayatDatabase
import com.hayat.core.domain.reminder.AlarmScheduler
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.security.BackupPreview
import com.hayat.core.domain.security.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Schema-driven backup: every user table is exported as rows of (column → typed value) and
 * restored in foreign-key order inside one transaction. Table names come from a fixed
 * allow-list and column names are checked against the live schema, so a crafted file can
 * neither inject SQL nor write unexpected columns. Any constraint violation rolls back the
 * whole restore and leaves existing data untouched.
 */
@Singleton
class HayatBackupService @Inject constructor(
    private val db: HayatDatabase,
    private val scheduler: ReminderScheduler,
    private val alarms: AlarmScheduler,
    private val time: TimeProvider,
    private val ids: IdGenerator,
    @Named("appVersion") private val appVersion: String,
) : BackupService {

    private val json = Json { prettyPrint = false }

    override suspend fun export(output: OutputStream, passphrase: CharArray?): AppResult<BackupPreview> = io {
        val sql = db.openHelper.writableDatabase
        val tables = LinkedHashMap<String, JsonElement>()
        val counts = LinkedHashMap<String, Int>()
        sql.beginTransaction() // consistent snapshot across tables
        try {
            for (t in TABLES) {
                val rows = mutableListOf<JsonElement>()
                sql.query("SELECT * FROM `$t`").use { c -> while (c.moveToNext()) rows += rowToJson(c) }
                tables[t] = JsonArray(rows); counts[t] = rows.size
            }
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
        val createdAt = time.now()
        val doc = JsonObject(mapOf(
            "app" to JsonPrimitive("Hayat"),
            "formatVersion" to JsonPrimitive(BackupFormat.FORMAT_VERSION.toInt()),
            "schemaVersion" to JsonPrimitive(HayatDatabase.VERSION),
            "appVersion" to JsonPrimitive(appVersion),
            "createdAt" to JsonPrimitive(createdAt.toString()),
            "tables" to JsonObject(tables),
        ))
        output.write(BackupFormat.encode(json.encodeToString(JsonObject.serializer(), doc).toByteArray(), passphrase))
        output.flush()
        recordMetadata("EXPORT", passphrase != null, counts.values.sum())
        BackupPreview(createdAt, appVersion, HayatDatabase.VERSION, passphrase != null, counts)
    }

    override suspend fun inspect(input: InputStream, passphrase: CharArray?): AppResult<BackupPreview> = io {
        val bytes = BackupFormat.readLimited(input)
        val (preview, _) = parse(bytes, passphrase)
        preview
    }

    override suspend fun isEncrypted(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        runCatching { BackupFormat.isEncrypted(BackupFormat.readLimited(input)) }.getOrDefault(false)
    }

    override suspend fun restore(input: InputStream, passphrase: CharArray?): AppResult<BackupPreview> = io {
        val bytes = BackupFormat.readLimited(input)
        val (preview, tables) = parse(bytes, passphrase)
        val sql = db.openHelper.writableDatabase
        val oldCodes = sql.query("SELECT request_code FROM reminders").use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }
        val columns = TABLES.associateWith { columnsOf(sql, it) }
        sql.beginTransaction()
        try {
            ALL_TABLES_DELETE_ORDER.forEach { sql.execSQL("DELETE FROM `$it`") }
            for (t in TABLES) {
                val allowed = columns.getValue(t)
                for (row in tables[t].orEmpty()) {
                    val cv = ContentValues()
                    for ((k, v) in row) {
                        if (k !in allowed) throw AppException(AppError.Backup(BackupFailure.CORRUPT_CONTENT))
                        putValue(cv, k, v)
                    }
                    sql.insert(t, SQLiteDatabase.CONFLICT_ABORT, cv)
                }
            }
            sql.query("PRAGMA foreign_key_check").use { c -> if (c.moveToFirst()) throw AppException(AppError.Backup(BackupFailure.CORRUPT_CONTENT)) }
            sql.setTransactionSuccessful()
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw AppException(AppError.Backup(BackupFailure.CORRUPT_CONTENT))
        } finally {
            sql.endTransaction()
        }
        db.invalidationTracker.refreshAsync()
        // Old OS alarms belong to reminders that no longer exist; then rebuild from the restored data.
        oldCodes.forEach(alarms::cancel)
        recordMetadata("IMPORT", preview.encrypted, preview.totalRecords)
        scheduler.reconcile(notifyMissed = false)
        preview
    }

    // ------------------------------------------------------------------ parsing & validation

    private fun parse(bytes: ByteArray, passphrase: CharArray?): Pair<BackupPreview, Map<String, List<JsonObject>>> {
        val raw = BackupFormat.decode(bytes, passphrase)
        val root = try { json.parseToJsonElement(raw.decodeToString()).jsonObject } catch (e: Exception) { fail(BackupFailure.CORRUPT_CONTENT) }
        if (root["app"]?.jsonPrimitive?.contentOrNull != "Hayat") fail(BackupFailure.NOT_A_HAYAT_BACKUP)
        val schema = runCatching { root.getValue("schemaVersion").jsonPrimitive.int }.getOrElse { fail(BackupFailure.CORRUPT_CONTENT) }
        if (schema > HayatDatabase.VERSION) fail(BackupFailure.NEWER_SCHEMA)
        if (schema < 1) fail(BackupFailure.CORRUPT_CONTENT)
        // Older schemas would be upgraded here via BackupMigrations (none exist yet at version 1).
        val createdAt = runCatching { Instant.parse(root.getValue("createdAt").jsonPrimitive.content) }.getOrElse { fail(BackupFailure.CORRUPT_CONTENT) }
        val tablesObj = runCatching { root.getValue("tables").jsonObject }.getOrElse { fail(BackupFailure.CORRUPT_CONTENT) }
        if (tablesObj.keys.any { it !in TABLES }) fail(BackupFailure.CORRUPT_CONTENT)
        val tables = tablesObj.mapValues { (_, v) -> runCatching { v.jsonArray.map { it.jsonObject } }.getOrElse { fail(BackupFailure.CORRUPT_CONTENT) } }
        val preview = BackupPreview(
            createdAt = createdAt,
            appVersion = root["appVersion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            schemaVersion = schema,
            encrypted = BackupFormat.isEncrypted(bytes),
            counts = TABLES.associateWith { tables[it]?.size ?: 0 },
        )
        return preview to tables
    }

    private fun rowToJson(c: Cursor): JsonObject = JsonObject((0 until c.columnCount).associate { i ->
        c.getColumnName(i) to when (c.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JsonNull
            Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
            Cursor.FIELD_TYPE_BLOB -> JsonObject(mapOf("b64" to JsonPrimitive(Base64.encodeToString(c.getBlob(i), Base64.NO_WRAP))))
            else -> JsonPrimitive(c.getString(i))
        }
    })

    private fun putValue(cv: ContentValues, key: String, v: JsonElement) {
        when {
            v is JsonNull -> cv.putNull(key)
            v is JsonObject && v["b64"] != null -> cv.put(key, Base64.decode(v.getValue("b64").jsonPrimitive.content, Base64.NO_WRAP))
            v is JsonPrimitive && v.isString -> cv.put(key, v.content)
            v is JsonPrimitive && v.longOrNull != null -> cv.put(key, v.longOrNull)
            v is JsonPrimitive && v.doubleOrNull != null -> cv.put(key, v.doubleOrNull)
            v is JsonPrimitive && v.booleanOrNull != null -> cv.put(key, if (v.booleanOrNull == true) 1 else 0)
            else -> throw AppException(AppError.Backup(BackupFailure.CORRUPT_CONTENT))
        }
    }

    private fun columnsOf(sql: SupportSQLiteDatabase, table: String): Set<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c -> buildSet { val idx = c.getColumnIndex("name"); while (c.moveToNext()) add(c.getString(idx)) } }

    private fun recordMetadata(kind: String, encrypted: Boolean, count: Int) {
        runCatching {
            db.openHelper.writableDatabase.insert("backup_metadata", SQLiteDatabase.CONFLICT_REPLACE, ContentValues().apply {
                put("id", ids.newId()); put("created_at", time.now().toEpochMilli()); put("kind", kind)
                put("encrypted", if (encrypted) 1 else 0); put("schema_version", HayatDatabase.VERSION)
                put("record_count", count); put("display_name", "")
            })
        }
    }

    private suspend fun <T> io(block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) { appCatching { block() } }

    private fun fail(reason: BackupFailure): Nothing = throw AppException(AppError.Backup(reason))

    companion object {
        /** User-data tables in parent-before-child (insert) order. Device diagnostics are not exported. */
        /**
         * Tables deliberately not in backups: diagnostics, backup bookkeeping and the cached
         * announcements feed (server content that is re-downloaded, not user data).
         */
        val EXCLUDED: Set<String> = setOf("alarm_delivery_logs", "backup_metadata", "announcements", "announcement_feed_state")

        val TABLES: List<String> = listOf(
            "categories", "tags", "routines", "routine_activities", "routine_activity_logs", "calendar_events",
            "tasks", "subtasks", "task_tags", "templates", "template_items",
            "reminders", "reminder_schedules", "reminder_occurrences", "reminder_action_logs",
            "habits", "habit_completions", "water_goals", "water_logs",
            "medicines", "medicine_schedules", "medicine_dose_logs",
            "meal_plans", "meal_logs", "shopping_items", "sleep_entries",
            "workout_plans", "workout_exercises", "workout_sessions",
            "prayer_logs", "zikr_definitions", "zikr_progress", "quran_logs", "fasting_logs",
            "study_subjects", "focus_sessions", "finance_items", "finance_payments",
            "notes", "note_items", "note_tags", "journal_entries", "mood_entries",
        )
        private val ALL_TABLES_DELETE_ORDER = listOf("alarm_delivery_logs") + TABLES.reversed()
    }
}
