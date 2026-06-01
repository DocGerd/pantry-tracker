package de.docgerdsoft.pantrytracker.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.docgerdsoft.pantrytracker.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Placeholder/copy drift guard for the app's user-facing format templates.
 *
 * Each template is resolved against real resources in BOTH locales and asserted
 * to format to the exact expected string. A `%1$d`->`%1$s` typo, a dropped index,
 * or an EN/DE arg-count mismatch makes one of these assertions throw or fail.
 * Intentional copy edits update the expected strings here — that is the point of
 * a drift guard. (Robolectric test: contributes 0% to JaCoCo by design — this is
 * a correctness guard, not a coverage path; coverage of the render path lives in
 * HomeSnackbarEventTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringFormatTemplateTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // ---- English (default locale) ----

    @Test
    fun en_homeDeleted() {
        assertEquals("Deleted Coke", context.getString(R.string.home_deleted, "Coke"))
    }

    @Test
    fun en_homeErrorDelete() {
        assertEquals("Couldn't delete: Coke", context.getString(R.string.home_error_delete, "Coke"))
    }

    @Test
    fun en_homeErrorRestore() {
        assertEquals("Couldn't restore: Coke", context.getString(R.string.home_error_restore, "Coke"))
    }

    @Test
    fun en_quantityCount() {
        assertEquals("×3", context.getString(R.string.quantity_count, 3))
    }

    @Test
    fun en_errorReadInventory() {
        assertEquals("Couldn't read inventory: boom", context.getString(R.string.error_read_inventory, "boom"))
    }

    @Test
    fun en_scanErrorSave() {
        assertEquals("Couldn't save: boom", context.getString(R.string.scan_error_save, "boom"))
    }

    @Test
    fun en_buylistErrorRestock() {
        assertEquals("Couldn't restock: boom", context.getString(R.string.buylist_error_restock, "boom"))
    }

    // ---- German ----

    @Test
    @Config(qualifiers = "de")
    fun de_homeDeleted() {
        assertEquals("Coke gelöscht", context.getString(R.string.home_deleted, "Coke"))
    }

    @Test
    @Config(qualifiers = "de")
    fun de_homeErrorDelete() {
        assertEquals("Coke konnte nicht gelöscht werden", context.getString(R.string.home_error_delete, "Coke"))
    }

    @Test
    @Config(qualifiers = "de")
    fun de_quantityCount() {
        assertEquals("×3", context.getString(R.string.quantity_count, 3))
    }

    @Test
    @Config(qualifiers = "de")
    fun de_scanErrorSave() {
        assertEquals("Speichern fehlgeschlagen: boom", context.getString(R.string.scan_error_save, "boom"))
    }

    @Test
    @Config(qualifiers = "de")
    fun de_buylistErrorRestock() {
        assertEquals("Nachkaufen fehlgeschlagen: boom", context.getString(R.string.buylist_error_restock, "boom"))
    }
}
