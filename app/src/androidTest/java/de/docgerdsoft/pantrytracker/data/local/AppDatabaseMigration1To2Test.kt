package de.docgerdsoft.pantrytracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v1 -> v2 migration creates the `off_lookup_cache` table with a
 * schema that matches `2.json` byte-for-byte (Room's `validateMigration` check
 * runs when `validateDroppedTables = true` below).
 *
 * Runs on a real device / emulator only. CI compiles this class via
 * `assembleDebugAndroidTest` but does not execute it.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration1To2Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_createsOffLookupCacheTable() {
        // Boot a v1 database and close it cleanly.
        helper.createDatabase(TEST_DB, 1).apply {
            close()
        }
        // Reopen at v2, running MIGRATION_1_2; validateDroppedTables=true
        // forces Room to validate the resulting schema matches 2.json byte-for-byte.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        // Probe: the off_lookup_cache table exists. Use org.junit.Assert.fail
        // because bare assert() is a no-op on ART (see CLAUDE.md).
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='off_lookup_cache'",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                fail("off_lookup_cache table missing after migration")
            }
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
