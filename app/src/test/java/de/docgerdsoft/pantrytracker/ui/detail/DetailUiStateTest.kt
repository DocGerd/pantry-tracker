package de.docgerdsoft.pantrytracker.ui.detail

import de.docgerdsoft.pantrytracker.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * Pure-JVM coverage for [DetailUiState] — defaults plus the construction-time
 * invariant that `shouldNavigateBack` implies a cleared product.
 */
class DetailUiStateTest {

    private fun product(): Product {
        val now = Instant.fromEpochMilliseconds(1_000L)
        return Product(
            id = 7L,
            barcode = "5449000000996",
            name = "Coke",
            brand = "Coca-Cola",
            imageUrl = null,
            quantity = 3,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun default_isEmptyAndNotNavigatingBack() {
        val state = DetailUiState()
        assertNull(state.product)
        assertFalse(state.shouldNavigateBack)
        assertFalse(state.showDeleteConfirm)
        assertNull(state.error)
    }

    @Test
    fun loadedProductWithoutNavBack_isAllowed() {
        val state = DetailUiState(product = product(), showDeleteConfirm = true, error = null)
        assertEquals("Coke", state.product?.name)
        assertTrue(state.showDeleteConfirm)
    }

    @Test
    fun navigateBackWithClearedProduct_isAllowed() {
        val state = DetailUiState(product = null, shouldNavigateBack = true)
        assertTrue(state.shouldNavigateBack)
        assertNull(state.product)
    }

    @Test
    fun navigateBackWithLingeringProduct_isRejected() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DetailUiState(product = product(), shouldNavigateBack = true)
        }
        assertTrue(ex.message!!.contains("shouldNavigateBack implies product == null"))
    }

    @Test
    fun errorMessage_isCarriedThrough() {
        val state = DetailUiState(product = product(), error = "Couldn't rename: boom")
        assertEquals("Couldn't rename: boom", state.error)
    }
}
