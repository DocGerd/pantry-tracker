package de.docgerdsoft.pantrytracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v1 schema JSON captured under app/schemas/ loads via
 * MigrationTestHelper and that creating a fresh DB at version 1 succeeds.
 *
 * Scope (deliberately narrow):
 *  - Confirms 1.json is wired into the androidTest APK assets.
 *  - Confirms MigrationTestHelper can instantiate the v1 schema.
 *
 * NOT verified here:
 *  - Entity-vs-schema drift (e.g. someone edits Product without bumping
 *    version + regenerating 1.json). That drift is caught at compile time
 *    by KSP/Room's identityHash check; this test does not open the live
 *    AppDatabase via Room.databaseBuilder(...), so the runtime identity
 *    check never fires here.
 *
 * Skeleton for PR C's v1->v2 migration test - adding the helper here means
 * PR C only adds a single test method, not the harness.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v1Schema_loadsViaMigrationHelper() {
        helper.createDatabase(TEST_DB, 1).apply {
            close()
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
