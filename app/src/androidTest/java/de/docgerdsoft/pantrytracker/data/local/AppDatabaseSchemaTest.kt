package de.docgerdsoft.pantrytracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v1 schema captured under app/schemas/ opens cleanly via
 * MigrationTestHelper. Skeleton for PR C's v1->v2 migration test - adding
 * the helper here means PR C only adds a single test method, not the harness.
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
    fun v1Schema_opensCleanly() {
        helper.createDatabase(TEST_DB, 1).apply {
            close()
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
