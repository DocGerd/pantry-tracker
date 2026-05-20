package de.docgerdsoft.pantrytracker.testfixtures

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Shared in-memory [ProductRepository] for instrumented Compose UI tests.
 *
 * Mirrors the production [de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl]
 * semantics that the screens depend on (sorted observation, unique-by-id,
 * search-by-substring, observeById flow that reflects updates) without
 * touching Room or OFF. Lifted out of `HappyPathUatTest` by SR-75 so the
 * same fake backs the 4 new scan-flow UI tests (`Scan*Test.kt`) and is the
 * canonical androidTest fixture for downstream Wave 3 tickets (#76, #77,
 * #78, #82 androidTest counterparts).
 *
 * API surface (load-bearing — Wave 3 tests are expected to use the same
 * names):
 *
 *  - [seed] / [seedAll] — pre-populate with one or more `Product`s
 *  - [observeProducts] / [observeById] / [search] — production-flavoured
 *    observation
 *  - [findById] / [findLocalByBarcode] / [lookupForPreview] — production
 *    suspend reads
 *  - [addNew] / [applyDelta] / [rename] / [delete] / [restore] — production
 *    suspend writes (all tracked for assertion)
 *
 * Test-double knobs (set before exercising the flow):
 *
 *  - [lookupResponses] — pre-canned `ScanCandidate?` keyed by barcode. The
 *    standard pattern for the new scan-flow tests: a test seeds an OFF hit
 *    via `lookupResponses["123…"] = ScanCandidate.FromOff(...)`, or an OFF
 *    miss via `lookupResponses["123…"] = null`.
 *  - [lookupShouldThrow], [addShouldThrow], [applyDeltaShouldThrow] — inject
 *    failure into the corresponding code paths (Phase.Error coverage).
 *
 * Observation knobs:
 *
 *  - [addedProducts] / [deltaCalls] / [renamedProducts] / [deletedIds] /
 *    [restoredProducts] — every write is recorded so the test can assert
 *    side-effects independently of the observed UI state.
 *
 * Threading: backing state is a single `MutableStateFlow<Map<Long, Product>>`
 * so the production sorted-by-name + unique-by-id invariants hold without
 * synchronization. All suspend writes update the map atomically.
 */
class FakeProductRepository : ProductRepository {

    private val rows = MutableStateFlow<Map<Long, Product>>(emptyMap())
    private var nextId: Long = 1

    // --- pre-population ------------------------------------------------------

    /** Pre-populate with a single product. Returns the resulting id (typically
     *  the same as `product.id` unless it was 0, in which case an auto-id is
     *  assigned). */
    fun seed(product: Product): Long {
        val id = if (product.id == 0L) nextId++ else product.id.also {
            if (it >= nextId) nextId = it + 1
        }
        rows.value = rows.value + (id to product.copy(id = id))
        return id
    }

    /** Pre-populate with multiple products. */
    fun seedAll(products: Iterable<Product>) {
        products.forEach { seed(it) }
    }

    // --- test-double knobs ---------------------------------------------------

    /**
     * Pre-canned responses for [lookupForPreview], keyed by barcode. The
     * mapped value may be `null` to model an OFF miss with no local row.
     * Calling [lookupForPreview] with a barcode that is NOT in this map
     * fails loudly with an `IllegalStateException` — tests must seed
     * every barcode they intend to resolve, which makes lookup outcomes
     * unambiguous and prevents accidental dependency on the seeded-row
     * fallback that used to be implicit.
     */
    val lookupResponses: MutableMap<String, ScanCandidate?> = mutableMapOf()

    var lookupShouldThrow: Throwable? = null
    var addShouldThrow: Throwable? = null
    var applyDeltaShouldThrow: Throwable? = null

    // --- recorded calls ------------------------------------------------------

    data class AddCall(
        val name: String,
        val brand: String?,
        val barcode: String?,
        val imageUrl: String?,
        val initialQuantity: Int,
    )

    /** Every [addNew] invocation in order. */
    val addedProducts: MutableList<AddCall> = mutableListOf()

    /** Every [applyDelta] invocation as `(productId, delta)`. */
    val deltaCalls: MutableList<Pair<Long, Int>> = mutableListOf()

    /** Every [rename] invocation as `(productId, newName)`. */
    val renamedProducts: MutableList<Pair<Long, String>> = mutableListOf()

    /** Every [delete] invocation. */
    val deletedIds: MutableList<Long> = mutableListOf()

    /** Every [restore] invocation. */
    val restoredProducts: MutableList<Product> = mutableListOf()

    /** Every [lookupForPreview] invocation in order — useful for the §12
     *  Switch-to-Add re-resolve test, which must observe a second lookup of
     *  the same barcode after the mode flip. */
    val lookupCalls: MutableList<String> = mutableListOf()

    // --- ProductRepository implementation ------------------------------------

    override fun observeProducts(): Flow<List<Product>> =
        rows.map { it.values.sortedBy { row -> row.name.lowercase() } }

    override fun search(query: String): Flow<List<Product>> =
        rows.map { snap ->
            snap.values
                .filter { it.name.contains(query, ignoreCase = true) }
                .sortedBy { row -> row.name.lowercase() }
        }

    override suspend fun findById(id: Long): Product? = rows.value[id]

    // `distinctUntilChanged` matches production Room semantics: a per-row
    // observable only fires when *this* row changes, not when other rows in
    // the table mutate. See HappyPathUatTest history for the rationale.
    override fun observeById(id: Long): Flow<Product?> =
        rows.map { it[id] }.distinctUntilChanged()

    override suspend fun findLocalByBarcode(code: String): Product? =
        rows.value.values.firstOrNull { it.barcode == code }

    override suspend fun lookupForPreview(code: String): ScanCandidate? {
        lookupCalls += code
        lookupShouldThrow?.let { throw it }
        check(lookupResponses.containsKey(code)) {
            "FakeProductRepository.lookupForPreview($code): no entry in " +
                "lookupResponses. Seed explicitly — set " +
                "lookupResponses[$code] to a ScanCandidate (hit) or null " +
                "(OFF miss). Seeded keys: ${lookupResponses.keys}."
        }
        return lookupResponses[code]
    }

    override suspend fun addNew(
        name: String,
        brand: String?,
        barcode: String?,
        imageUrl: String?,
        initialQuantity: Int,
    ): Long {
        addShouldThrow?.let { throw it }
        addedProducts += AddCall(name, brand, barcode, imageUrl, initialQuantity)
        val id = nextId++
        val now = Clock.System.now()
        val row = Product(
            id = id,
            barcode = barcode,
            name = name,
            brand = brand,
            imageUrl = imageUrl,
            quantity = initialQuantity.coerceAtLeast(0),
            createdAt = now,
            updatedAt = now,
        )
        rows.value = rows.value + (id to row)
        return id
    }

    override suspend fun applyDelta(productId: Long, delta: Int) {
        applyDeltaShouldThrow?.let { throw it }
        val existing = checkNotNull(rows.value[productId]) {
            "FakeProductRepository.applyDelta: no row id=$productId — " +
                "seeded ids: ${rows.value.keys}"
        }
        deltaCalls += productId to delta
        val newQty = (existing.quantity + delta).coerceAtLeast(0)
        if (newQty == existing.quantity) return
        rows.value = rows.value + (productId to existing.copy(
            quantity = newQty, updatedAt = Clock.System.now(),
        ))
    }

    override suspend fun rename(productId: Long, newName: String) {
        val existing = checkNotNull(rows.value[productId]) {
            "FakeProductRepository.rename: no row id=$productId — " +
                "seeded ids: ${rows.value.keys}"
        }
        renamedProducts += productId to newName
        if (existing.name == newName) return
        rows.value = rows.value + (productId to existing.copy(
            name = newName, updatedAt = Clock.System.now(),
        ))
    }

    override suspend fun delete(productId: Long) {
        deletedIds += productId
        rows.value = rows.value - productId
    }

    override suspend fun restore(product: Product) {
        restoredProducts += product
        rows.value = rows.value + (product.id to product)
    }
}
