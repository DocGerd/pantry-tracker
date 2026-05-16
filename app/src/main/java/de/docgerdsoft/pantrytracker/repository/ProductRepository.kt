package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun search(query: String): Flow<List<Product>>
    suspend fun findById(id: Long): Product?
    suspend fun findLocalByBarcode(code: String): Product?

    /**
     * Creates a new product with the given initial quantity. Returns the new row id.
     */
    suspend fun addNew(
        name: String,
        brand: String? = null,
        barcode: String? = null,
        imageUrl: String? = null,
        initialQuantity: Int,
    ): Long

    /**
     * Applies a quantity change (positive or negative). Clamps the resulting
     * quantity at 0; never returns a negative value.
     */
    suspend fun applyDelta(productId: Long, delta: Int)

    suspend fun rename(productId: Long, newName: String)
    suspend fun delete(productId: Long)
}
