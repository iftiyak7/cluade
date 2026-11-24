package com.hayat.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All schema migrations, in order. Destructive migration is never enabled: adding a version
 * without a migration here makes the app fail loudly in tests instead of deleting user data.
 * Each migration must have a case in MigrationTest.
 */
object Migrations {
    /** v2: cached announcements feed (server content; no user data touched). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `announcements` (`id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title_en` TEXT NOT NULL, " +
                    "`body_en` TEXT NOT NULL, `title_bn` TEXT NOT NULL, `body_bn` TEXT NOT NULL, `cta_label_en` TEXT NOT NULL, " +
                    "`cta_label_bn` TEXT NOT NULL, `cta_url` TEXT NOT NULL, `lang` TEXT NOT NULL, `min_version` INTEGER NOT NULL, " +
                    "`priority` INTEGER NOT NULL, `starts_at` INTEGER NOT NULL, `ends_at` INTEGER, `updated_at` INTEGER NOT NULL, " +
                    "`seen` INTEGER NOT NULL, `dismissed` INTEGER NOT NULL, `notified` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS `announcement_feed_state` (`id` INTEGER NOT NULL, `etag` TEXT, `fetched_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
