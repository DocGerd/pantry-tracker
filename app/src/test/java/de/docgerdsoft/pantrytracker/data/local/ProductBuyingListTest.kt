package de.docgerdsoft.pantrytracker.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ProductBuyingListTest {
    private fun p(quantity: Int, lowLimit: Int?) = Product(
        barcode = null, name = "x", quantity = quantity, lowLimit = lowLimit,
        createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test fun `untracked item (null limit) is never on the list`() =
        assertFalse(p(quantity = 0, lowLimit = null).isOnBuyingList)

    @Test fun `at the limit is on the list`() =
        assertTrue(p(quantity = 2, lowLimit = 2).isOnBuyingList)

    @Test fun `below the limit is on the list`() =
        assertTrue(p(quantity = 0, lowLimit = 2).isOnBuyingList)

    @Test fun `above the limit is off the list`() =
        assertFalse(p(quantity = 5, lowLimit = 2).isOnBuyingList)
}
