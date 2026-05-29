package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun search(query: String): Flow<List<Product>>

    /** Reactive buying list: items with a non-null lowLimit at or below it. */
    fun observeBuyingList(): Flow<List<Product>>

    /**
     * Saves the opt-in restock settings for [productId]. [lowLimit] null clears
     * tracking (item leaves the buying list); a non-null value is clamped to >= 0.
     * [defaultBuyAmount] is clamped to >= 1. Stamps `updatedAt`. Silently no-ops on
     * unknown id (consistent with [rename] / [applyDelta]).
     */
    suspend fun setRestockSettings(productId: Long, lowLimit: Int?, defaultBuyAmount: Int)
    suspend fun findById(id: Long): Product?
    fun observeById(id: Long): Flow<Product?>
    suspend fun findLocalByBarcode(code: String): Product?

    /**
     * Resolve a scanned barcode to a [ScanCandidate] for preview-before-confirm.
     * Local-first: returns [ScanCandidate.Persisted] if the barcode is already in
     * the local DB. Otherwise hits OFF; on a hit returns [ScanCandidate.FromOff]
     * carrying name / brand / imageUrl (not yet persisted — caller calls addNew on
     * confirm). Returns `null` on OFF miss, network failure, blank barcode, or OFF
     * hit with missing/blank `product_name` — caller drops into manual entry per
     * spec §6.2.
     */
    suspend fun lookupForPreview(code: String): ScanCandidate?

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

    /**
     * Re-inserts the full [product] entity preserving every field — `id`,
     * `name`, `brand`, `barcode`, `imageUrl`, `quantity`, `createdAt`, and
     * `updatedAt` all round-trip unchanged. Intended for delete-undo: the
     * caller captures the [Product] before [delete], then calls [restore]
     * with that captured instance on UNDO.
     *
     * Failure modes:
     *
     * - Backed by an upsert: if a row with the same `id` already exists,
     *   it is **silently overwritten** with [product]. The undo flow keeps
     *   this benign — between [delete] and [restore] the only race window
     *   is the snackbar duration (~4 s) and the id is unique-by-construction.
     * - If a different row exists with the same `barcode` (unique index),
     *   the underlying Room insert throws `SQLiteConstraintException` — the
     *   suspend call propagates that up so the caller can surface a
     *   restore-failed UI event instead of silently no-op'ing.
     */
    suspend fun restore(product: Product)
}
