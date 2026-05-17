package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun search(query: String): Flow<List<Product>>
    suspend fun findById(id: Long): Product?
    suspend fun findLocalByBarcode(code: String): Product?

    /**
     * Resolve a scanned barcode to a Product for preview-before-confirm.
     * Local-first: returns the persisted row if present. Otherwise hits OFF; on
     * a hit returns a Product with `id = 0` and `quantity = 0` carrying only
     * name / brand / imageUrl from OFF (caller decides initial quantity at confirm).
     * Returns `null` on OFF miss, network failure, blank barcode, or OFF hit with
     * missing/blank `product_name` (can't preview without a name) — caller drops
     * into manual entry per spec §6.2.
     */
    suspend fun lookupForPreview(code: String): Product?

    /**
     * Inserts a new product row and returns its id. `initialQuantity` is clamped at 0;
     * negative values become 0. `createdAt` and `updatedAt` are stamped from the
     * repository's `Clock`. Note: backed by an Upsert, so passing a `barcode` that
     * collides with an existing unique-indexed row overwrites it rather than failing.
     */
    suspend fun addNew(
        name: String,
        brand: String? = null,
        barcode: String? = null,
        imageUrl: String? = null,
        initialQuantity: Int,
    ): Long

    /**
     * Adjusts the persisted quantity of `productId` by `delta` (may be negative). The
     * resulting quantity is clamped at 0 — negative deltas larger than the current
     * quantity bottom out at zero rather than going negative.
     *
     * Silently no-ops when `productId` does not exist, or when the clamped result
     * equals the current quantity (no write happens, `updatedAt` is not bumped).
     * Per spec §7, callers are responsible for surfacing unknown-id as inline UI
     * state in their layer; this method must not crash on a stale id.
     */
    suspend fun applyDelta(productId: Long, delta: Int)

    /**
     * Renames the product, stamping `updatedAt` from the repository's `Clock`.
     * Silently no-ops when `productId` does not exist or when `newName` equals the
     * existing name (no write, `updatedAt` is not bumped). Per spec §7, callers
     * are responsible for surfacing unknown-id as inline UI state.
     */
    suspend fun rename(productId: Long, newName: String)

    suspend fun delete(productId: Long)
}
