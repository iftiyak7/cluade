package com.hayat.core.database

import androidx.room.migration.Migration

/**
 * All schema migrations, in order. Destructive migration is never enabled: adding a version
 * without a migration here makes the app fail loudly in tests instead of deleting user data.
 * Each migration must have a case in MigrationTest.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
