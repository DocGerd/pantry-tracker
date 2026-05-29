package de.docgerdsoft.pantrytracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v2 -> v3 migration (#191) adds the opt-in restock columns
 * (`lowLimit`, `defaultBuyAmount`) to `products` with a schema that matches
 * `3.json` — Room's `validateMigration` checks each column's name, affinity,
 * and nullability against the expected schema (NOT the literal bytes and NOT
 * the `DEFAULT` clause; see the `MIGRATION_2_3` KDoc). It also confirms an
 * existing v2 row back-fills to the safe defaults (`lowLimit = NULL`,
 * `defaultBuyAmount = 1`).
 *
 * Runs on a real device / emulator only. CI compiles this class via
 * `assembleDebugAndroidTest` but does not execute it.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3_addsRestockColumnsWithDefaults() {
        // Seed a v2 `products` row (no restock columns yet) and close cleanly.
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO products (barcode,name,brand,imageUrl,quantity,createdAt,updatedAt) " +
                    "VALUES (NULL,'Salt',NULL,NULL,3,0,0)",
            )
            close()
        }
        // Reopen at v3 running MIGRATION_2_3. Room always validates the resulting
        // schema's column name/affinity/nullability against 3.json; the trailing
        // `true` is validateDroppedTables, which additionally asserts no stray
        // tables remain (it does not gate the column-schema validation).
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        db.query("SELECT lowLimit, defaultBuyAmount FROM products WHERE name='Salt'").use { c ->
            assertTrue("seeded row missing after migration", c.moveToFirst())
            assertTrue("lowLimit must default to NULL", c.isNull(0))
            assertEquals(1, c.getInt(1)) // defaultBuyAmount defaults to 1
        }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
