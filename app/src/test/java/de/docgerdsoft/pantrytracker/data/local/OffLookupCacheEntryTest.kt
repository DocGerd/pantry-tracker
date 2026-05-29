package de.docgerdsoft.pantrytracker.data.local

import de.docgerdsoft.pantrytracker.data.remote.OffHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * Pure-JVM coverage for [OffLookupCacheEntry] — field carriage, data-class
 * semantics, and the defensive non-blank-name invariant in its `init` block.
 */
class OffLookupCacheEntryTest {

    private fun entry(name: String = "Cookies") = OffLookupCacheEntry(
        barcode = "0123456789",
        name = name,
        brand = "CacheBrand",
        imageUrl = "https://images.openfoodfacts.org/cookies.jpg",
        resolvingHost = OffHost.FOOD,
        fetchedAt = Instant.fromEpochMilliseconds(1_000_000L),
    )

    @Test
    fun carriesAllFields() {
        val e = entry()
        assertEquals("0123456789", e.barcode)
        assertEquals("Cookies", e.name)
        assertEquals("CacheBrand", e.brand)
        assertEquals("https://images.openfoodfacts.org/cookies.jpg", e.imageUrl)
        assertEquals(OffHost.FOOD, e.resolvingHost)
        assertEquals(Instant.fromEpochMilliseconds(1_000_000L), e.fetchedAt)
    }

    @Test
    fun allowsNullBrandAndImageUrl() {
        val e = entry().copy(brand = null, imageUrl = null)
        assertEquals(null, e.brand)
        assertEquals(null, e.imageUrl)
    }

    @Test
    fun dataClassEqualityAndCopyHold() {
        assertEquals(entry(), entry())
        assertEquals("Crackers", entry().copy(name = "Crackers").name)
    }

    @Test
    fun blankName_isRejectedByInitInvariant() {
        val ex = assertThrows(IllegalArgumentException::class.java) { entry(name = "   ") }
        assertTrue(ex.message!!.contains("must be non-blank"))
    }

    @Test
    fun emptyName_isRejectedByInitInvariant() {
        assertThrows(IllegalArgumentException::class.java) { entry(name = "") }
    }
}
