package de.docgerdsoft.pantrytracker.ui.common

import de.docgerdsoft.pantrytracker.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.time.Instant

/**
 * Pure-JVM coverage for the [SnackbarEvent] one-shot event variants.
 */
class SnackbarEventTest {

    private fun product(name: String = "Coke"): Product {
        val now = Instant.fromEpochMilliseconds(1_000L)
        return Product(
            id = 1L,
            barcode = null,
            name = name,
            brand = null,
            imageUrl = null,
            quantity = 2,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun deleted_carriesTheRestorableProduct() {
        val p = product()
        val event = SnackbarEvent.Deleted(p)
        assertEquals(p, event.product)
    }

    @Test
    fun deleteFailed_carriesTheName() {
        assertEquals("Coke", SnackbarEvent.DeleteFailed("Coke").name)
    }

    @Test
    fun restoreFailed_carriesTheName() {
        assertEquals("Coke", SnackbarEvent.RestoreFailed("Coke").name)
    }

    @Test
    fun variantsWithSameNameAreNotEqualAcrossTypes() {
        // DeleteFailed and RestoreFailed are distinct types even with equal payload —
        // the collector dispatches on type, so this distinction matters.
        val deleteFailed: SnackbarEvent = SnackbarEvent.DeleteFailed("Coke")
        val restoreFailed: SnackbarEvent = SnackbarEvent.RestoreFailed("Coke")
        assertNotEquals(deleteFailed, restoreFailed)
    }

    @Test
    fun dataClassEqualityAndCopyHold() {
        assertEquals(SnackbarEvent.DeleteFailed("Coke"), SnackbarEvent.DeleteFailed("Coke"))
        assertEquals(
            SnackbarEvent.RestoreFailed("Pasta"),
            SnackbarEvent.RestoreFailed("Coke").copy(name = "Pasta"),
        )
    }
}
