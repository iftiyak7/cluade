package com.hayat.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Validates the exported schema and the migration chain. When VERSION is bumped, add a
 * Migration to [Migrations.ALL]; [allVersionsMigrateToLatest] then verifies it against the
 * exported JSON schemas. Destructive migration is never allowed.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HayatDatabase::class.java)

    @Test fun `every schema version is exported`() {
        val dir = File("schemas/com.hayat.core.database.HayatDatabase")
        (1..HayatDatabase.VERSION).forEach { assertTrue("missing schema $it.json", File(dir, "$it.json").exists()) }
    }

    @Test fun `every version has a migration path to latest`() {
        val covered = Migrations.ALL.map { it.startVersion to it.endVersion }.toSet()
        (1 until HayatDatabase.VERSION).forEach { v -> assertTrue("missing migration $v -> ${v + 1}", (v to v + 1) in covered) }
    }

    @Test fun `allVersionsMigrateToLatest`() {
        helper.createDatabase(dbName, 1).apply {
            // Representative data written with the oldest schema must survive every migration.
            execSQL("INSERT INTO tags (id, name, created_at) VALUES ('t1', 'জরুরি', 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, HayatDatabase.VERSION, true, *Migrations.ALL)
        db.query("SELECT name FROM tags WHERE id = 't1'").use { c -> c.moveToFirst(); assertEquals("জরুরি", c.getString(0)) }
        db.close()
        // Room itself opens the migrated file without falling back to destructive migration.
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HayatDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL).build().apply { openHelper.writableDatabase; close() }
    }
}
