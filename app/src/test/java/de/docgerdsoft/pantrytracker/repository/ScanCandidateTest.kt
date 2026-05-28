package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/**
 * Pure-JVM coverage for the [ScanCandidate] sealed hierarchy. The two variants
 * expose the same four read-only fields through different backings — Persisted
 * delegates to its wrapped [Product]; FromOff stores them directly.
 */
class ScanCandidateTest {

    private fun product(
        barcode: String? = "5449000000996",
        brand: String? = "Coca-Cola",
        imageUrl: String? = "https://images.openfoodfacts.org/coke.jpg",
    ): Product {
        val now = Instant.fromEpochMilliseconds(1_000L)
        return Product(
            id = 5L,
            barcode = barcode,
            name = "Coke",
            brand = brand,
            imageUrl = imageUrl,
            quantity = 3,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun persisted_delegatesAllFieldsToWrappedProduct() {
        val candidate: ScanCandidate = ScanCandidate.Persisted(product())
        assertEquals("5449000000996", candidate.barcode)
        assertEquals("Coke", candidate.name)
        assertEquals("Coca-Cola", candidate.brand)
        assertEquals("https://images.openfoodfacts.org/coke.jpg", candidate.imageUrl)
    }

    @Test
    fun persisted_nullableFieldsDelegateNullThrough() {
        val candidate = ScanCandidate.Persisted(product(brand = null, imageUrl = null))
        assertNull(candidate.brand)
        assertNull(candidate.imageUrl)
    }

    @Test
    fun fromOff_exposesItsOwnFields() {
        val candidate: ScanCandidate = ScanCandidate.FromOff(
            barcode = "111",
            name = "Sprite",
            brand = "Coca-Cola",
            imageUrl = null,
        )
        assertEquals("111", candidate.barcode)
        assertEquals("Sprite", candidate.name)
        assertEquals("Coca-Cola", candidate.brand)
        assertNull(candidate.imageUrl)
    }

    @Test
    fun fromOff_copyChangesOneFieldOnly() {
        val base = ScanCandidate.FromOff(barcode = "111", name = "Sprite", brand = null, imageUrl = null)
        val renamed = base.copy(name = "Fanta")
        assertEquals("Fanta", renamed.name)
        assertEquals("111", renamed.barcode)
        assertEquals(base.copy(name = "Fanta"), renamed)
    }
}
