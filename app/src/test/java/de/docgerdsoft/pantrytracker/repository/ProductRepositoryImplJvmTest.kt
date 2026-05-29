package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.OffLookupCacheDao
import de.docgerdsoft.pantrytracker.data.local.OffLookupCacheEntry
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import de.docgerdsoft.pantrytracker.data.remote.OffHost
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.data.remote.OffLookupResult
import de.docgerdsoft.pantrytracker.data.remote.OffProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * **Plain-JVM** behavioural coverage for [ProductRepositoryImpl] and its
 * file-scope helpers (`capOffText`, `gateImageUrl`).
 *
 * The sibling `ProductRepositoryImplTest` wires the real Room database via
 * `@RunWith(RobolectricTestRunner)` — which is the right shape for proving the
 * SQL/Converters integration, but Robolectric's sandbox classloader bypasses
 * the on-the-fly JaCoCo agent, so that suite records **0%** coverage for this
 * class. This suite substitutes hand-rolled in-memory fakes for the two DAO
 * interfaces + the OFF seam, so it runs on the plain JVM and the JaCoCo agent
 * actually attributes the lines. It deliberately exercises every branch of the
 * repository (including the best-effort cache-failure catch arms and the
 * length-cap / scheme-gate helpers), not just the happy paths.
 *
 * @Suppress LargeClass: a single repository under exhaustive branch test —
 * splitting by feature area would scatter the shared fake/clock setup.
 */
@Suppress("LargeClass")
class ProductRepositoryImplJvmTest {

    private val dao = FakeProductDao()
    private val cacheDao = FakeOffLookupCacheDao()
    private val off = FakeOffLookup()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000L))
    private val repo: ProductRepository = ProductRepositoryImpl(dao, off, cacheDao, clock)

    // --- delegating reads ------------------------------------------------

    @Test
    fun observeProducts_emitsDaoRows() = runTest {
        repo.addNew(name = "Coke", initialQuantity = 1)
        assertEquals(listOf("Coke"), repo.observeProducts().first().map { it.name })
    }

    @Test
    fun search_delegatesToDao() = runTest {
        repo.addNew(name = "Coke Zero", initialQuantity = 1)
        repo.addNew(name = "Pasta", initialQuantity = 1)
        assertEquals(listOf("Coke Zero"), repo.search("coke").first().map { it.name })
    }

    @Test
    fun observeById_emitsTheRow() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 1)
        assertEquals("Coke", repo.observeById(id).first()?.name)
    }

    @Test
    fun findLocalByBarcode_returnsRowOrNull() = runTest {
        repo.addNew(name = "Coke", barcode = "5449000000996", initialQuantity = 1)
        assertEquals("Coke", repo.findLocalByBarcode("5449000000996")?.name)
        assertNull(repo.findLocalByBarcode("0000000000000"))
    }

    // --- addNew ----------------------------------------------------------

    @Test
    fun addNew_persistsRow_coercesNegativeQuantityToZero() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = -5)
        assertEquals(0, repo.findById(id)?.quantity)
    }

    @Test
    fun addNew_withBarcode_evictsCacheRow() = runTest {
        cacheDao.put(cacheEntry(barcode = "111"))
        repo.addNew(name = "Coke", barcode = "111", initialQuantity = 1)
        assertEquals(1, cacheDao.deleteCount)
        assertNull(cacheDao.rows["111"])
    }

    @Test
    fun addNew_withBarcode_cacheEvictThrows_stillReturnsId() = runTest {
        cacheDao.failDelete = true
        // The pantry write must commit even when the best-effort cache evict throws.
        val id = repo.addNew(name = "Coke", barcode = "111", initialQuantity = 2)
        assertEquals("Coke", repo.findById(id)?.name)
    }

    @Test
    fun addNew_nullBarcode_skipsCacheEvict() = runTest {
        repo.addNew(name = "Manual", barcode = null, initialQuantity = 1)
        assertEquals(0, cacheDao.deleteCount)
    }

    // --- applyDelta ------------------------------------------------------

    @Test
    fun applyDelta_unknownId_isNoOp() = runTest {
        repo.applyDelta(productId = 9999L, delta = 3)
        assertTrue(repo.observeProducts().first().isEmpty())
    }

    @Test
    fun applyDelta_positiveAndNegative() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 2)
        repo.applyDelta(id, +4)
        assertEquals(6, repo.findById(id)?.quantity)
        repo.applyDelta(id, -1)
        assertEquals(5, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_clampsAtZero() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 1)
        repo.applyDelta(id, -10)
        assertEquals(0, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_noChange_doesNotTouchUpdatedAt() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 0)
        val before = repo.findById(id)!!.updatedAt
        clock.current = clock.current.plus(1.seconds)
        repo.applyDelta(id, -3) // 0 - 3 clamps to 0 == current; must short-circuit
        assertEquals(before, repo.findById(id)!!.updatedAt)
    }

    // --- rename ----------------------------------------------------------

    @Test
    fun rename_unknownId_isNoOp() = runTest {
        repo.rename(productId = 9999L, newName = "Anything")
        assertTrue(repo.observeProducts().first().isEmpty())
    }

    @Test
    fun rename_sameName_doesNotTouchUpdatedAt() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 1)
        val before = repo.findById(id)!!.updatedAt
        clock.current = clock.current.plus(1.seconds)
        repo.rename(id, "Coke")
        assertEquals(before, repo.findById(id)!!.updatedAt)
    }

    @Test
    fun rename_changesNameAndBumpsUpdatedAt() = runTest {
        val id = repo.addNew(name = "Cokq", initialQuantity = 1)
        val before = repo.findById(id)!!.updatedAt
        clock.current = clock.current.plus(1.seconds)
        repo.rename(id, "Coke")
        val after = repo.findById(id)!!
        assertEquals("Coke", after.name)
        assertEquals(before.toEpochMilliseconds() + 1_000, after.updatedAt.toEpochMilliseconds())
    }

    // --- delete / restore ------------------------------------------------

    @Test
    fun delete_thenRestore_roundTripsRow() = runTest {
        val id = repo.addNew(name = "Coke", barcode = "111", initialQuantity = 3)
        val original = repo.findById(id)!!
        repo.delete(id)
        assertNull(repo.findById(id))
        repo.restore(original)
        val restored = repo.findById(id)
        assertEquals(original, restored)
    }

    // --- lookupForPreview: blank + local hit -----------------------------

    @Test
    fun lookupForPreview_blank_returnsNull_withoutNetwork() = runTest {
        assertNull(repo.lookupForPreview(""))
        assertNull(repo.lookupForPreview("   "))
        assertEquals(0, off.lookupCallCount)
    }

    @Test
    fun lookupForPreview_localHit_returnsPersisted_withoutNetwork() = runTest {
        repo.addNew(name = "Local Coke", barcode = "111", initialQuantity = 5)
        val candidate = repo.lookupForPreview("111")
        assertTrue(candidate is ScanCandidate.Persisted)
        assertEquals("Local Coke", candidate!!.name)
        assertEquals(0, off.lookupCallCount)
    }

    // --- lookupForPreview: cache paths -----------------------------------

    @Test
    fun lookupForPreview_freshCacheHit_returnsFromOff_withoutNetwork() = runTest {
        cacheDao.put(cacheEntry(barcode = "222", name = "Cached", fetchedAt = clock.current.minus(1.seconds)))
        val candidate = repo.lookupForPreview("222") as ScanCandidate.FromOff
        assertEquals("Cached", candidate.name)
        assertEquals(0, off.lookupCallCount)
    }

    @Test
    fun lookupForPreview_cacheAtTtlBoundary_stillFresh() = runTest {
        cacheDao.put(cacheEntry(barcode = "222", name = "Boundary", fetchedAt = clock.current.minus(30.days)))
        // The TTL-boundary row must be SERVED (not just "no network call"): pin
        // the returned candidate too, so a regression that early-returns null at
        // the boundary while also not calling OFF would still fail.
        val candidate = repo.lookupForPreview("222") as ScanCandidate.FromOff
        assertEquals("Boundary", candidate.name)
        assertEquals(0, off.lookupCallCount)
    }

    @Test
    fun lookupForPreview_staleCache_refreshesFromOff() = runTest {
        cacheDao.put(cacheEntry(barcode = "222", name = "Old", fetchedAt = clock.current.minus(30.days).minus(1.milliseconds)))
        off.stub("222", OffProduct(productName = "New"))
        val candidate = repo.lookupForPreview("222") as ScanCandidate.FromOff
        assertEquals("New", candidate.name)
        assertEquals(1, off.lookupCallCount)
        assertEquals("New", cacheDao.rows["222"]!!.name) // refreshed
    }

    @Test
    fun lookupForPreview_futureFetchedAt_isClockSkew_fallsThroughToOff() = runTest {
        // fetchedAt in the future would make (now - fetchedAt) negative — must NOT be treated fresh.
        cacheDao.put(cacheEntry(barcode = "222", name = "Skewed", fetchedAt = clock.current.plus(1.days)))
        off.stub("222", OffProduct(productName = "Fresh"))
        val candidate = repo.lookupForPreview("222") as ScanCandidate.FromOff
        assertEquals("Fresh", candidate.name)
        assertEquals(1, off.lookupCallCount)
    }

    @Test
    fun lookupForPreview_cacheReadThrows_treatedAsMiss_continuesToOff() = runTest {
        cacheDao.failFind = true
        off.stub("222", OffProduct(productName = "Net"))
        val candidate = repo.lookupForPreview("222") as ScanCandidate.FromOff
        assertEquals("Net", candidate.name)
        assertEquals(1, off.lookupCallCount)
    }

    // --- lookupForPreview: OFF outcomes ----------------------------------

    @Test
    fun lookupForPreview_offMiss_returnsNull() = runTest {
        assertNull(repo.lookupForPreview("333"))
        assertEquals(1, off.lookupCallCount)
    }

    @Test
    fun lookupForPreview_offHitBlankName_dropsToManualEntry_doesNotCache() = runTest {
        off.stub("444", OffProduct(productName = "   ", brands = "BrandOnly"))
        assertNull(repo.lookupForPreview("444"))
        assertNull(cacheDao.rows["444"])
    }

    @Test
    fun lookupForPreview_offHitHappy_cachesPostGatingShape() = runTest {
        off.stub(
            "555",
            OffProduct(productName = "Sprite", brands = "Coca-Cola", imageUrl = "https://img/x.jpg"),
            host = OffHost.PET_FOOD,
        )
        val candidate = repo.lookupForPreview("555") as ScanCandidate.FromOff
        assertEquals("Sprite", candidate.name)
        assertEquals("Coca-Cola", candidate.brand)
        assertEquals("https://img/x.jpg", candidate.imageUrl)
        val written = cacheDao.rows["555"]!!
        assertEquals("Sprite", written.name)
        assertEquals(OffHost.PET_FOOD, written.resolvingHost)
        assertEquals(clock.current, written.fetchedAt)
    }

    @Test
    fun lookupForPreview_offHit_cacheUpsertThrows_stillReturnsCandidate() = runTest {
        cacheDao.failUpsert = true
        off.stub("555", OffProduct(productName = "Sprite"))
        val candidate = repo.lookupForPreview("555") as ScanCandidate.FromOff
        assertEquals("Sprite", candidate.name)
        assertNull(cacheDao.rows["555"]) // upsert failed, nothing cached
    }

    // --- capOffText branches ---------------------------------------------

    @Test
    fun lookupForPreview_longNameAndBrand_areTruncatedAt256() = runTest {
        off.stub("666", OffProduct(productName = "x".repeat(300), brands = "y".repeat(300)))
        val candidate = repo.lookupForPreview("666") as ScanCandidate.FromOff
        assertEquals(256, candidate.name.length)
        assertEquals(256, candidate.brand?.length)
    }

    @Test
    fun lookupForPreview_brandBlankOrNull_mapsToNull() = runTest {
        off.stub("667", OffProduct(productName = "Coke", brands = "   "))
        assertNull((repo.lookupForPreview("667") as ScanCandidate.FromOff).brand)
        off.stub("668", OffProduct(productName = "Coke", brands = null))
        assertNull((repo.lookupForPreview("668") as ScanCandidate.FromOff).brand)
    }

    // --- gateImageUrl branches -------------------------------------------

    @Test
    fun lookupForPreview_httpsImageUrl_isPreserved_caseInsensitive() = runTest {
        off.stub("777", OffProduct(productName = "Coke", imageUrl = "HTTPS://img/x.jpg"))
        assertEquals("HTTPS://img/x.jpg", (repo.lookupForPreview("777") as ScanCandidate.FromOff).imageUrl)
    }

    @Test
    fun lookupForPreview_nonHttpsImageUrl_isDropped() = runTest {
        off.stub("778", OffProduct(productName = "Coke", imageUrl = "http://insecure/x.jpg"))
        assertNull((repo.lookupForPreview("778") as ScanCandidate.FromOff).imageUrl)
    }

    @Test
    fun lookupForPreview_oversizeImageUrl_isDropped() = runTest {
        val tooLong = "https://" + "x".repeat(2_040) // length 2048, fails strict < 2048
        off.stub("779", OffProduct(productName = "Coke", imageUrl = tooLong))
        assertNull((repo.lookupForPreview("779") as ScanCandidate.FromOff).imageUrl)
    }

    @Test
    fun lookupForPreview_nullImageUrl_staysNull() = runTest {
        off.stub("780", OffProduct(productName = "Coke", imageUrl = null))
        assertNull((repo.lookupForPreview("780") as ScanCandidate.FromOff).imageUrl)
    }

    // --- CancellationException must propagate (not be swallowed) ---------
    // The best-effort cache catch arms explicitly rethrow CancellationException
    // before the generic Exception arm; a regression that dropped the rethrow
    // would let a cancelled job race a state write (see CLAUDE.md "runCatching
    // swallows CancellationException").

    @Test
    fun addNew_cacheEvictCancelled_propagatesCancellation() = runTest {
        cacheDao.cancelDelete = true
        assertCancellationPropagates { repo.addNew(name = "Coke", barcode = "111", initialQuantity = 1) }
        // Pin that the CE originated INSIDE the guarded evict (barcode-gate passed,
        // delete invoked) rather than somewhere before the try.
        assertEquals(1, cacheDao.deleteCount)
    }

    @Test
    fun lookupForPreview_cacheReadCancelled_propagatesCancellation() = runTest {
        cacheDao.cancelFind = true
        assertCancellationPropagates { repo.lookupForPreview("222") }
        // CE thrown from the cache read must NOT be reinterpreted as a miss that
        // then calls OFF — the rethrow short-circuits before the network call.
        assertEquals(0, off.lookupCallCount)
    }

    @Test
    fun lookupForPreview_cacheUpsertCancelled_propagatesCancellation() = runTest {
        cacheDao.cancelUpsert = true
        off.stub("555", OffProduct(productName = "Sprite"))
        assertCancellationPropagates { repo.lookupForPreview("555") }
        // The OFF call ran (so the upsert arm was reached) before CE propagated.
        assertEquals(1, off.lookupCallCount)
    }

    // --- helpers / fakes -------------------------------------------------

    // Asserts the suspend [block] rethrows CancellationException rather than
    // swallowing it into the best-effort generic-Exception arm. We catch it
    // ourselves so the surrounding TestScope job is never actually cancelled.
    private suspend fun assertCancellationPropagates(block: suspend () -> Unit) {
        var caught: CancellationException? = null
        try {
            block()
        } catch (e: CancellationException) {
            caught = e
        }
        assertTrue("expected CancellationException to propagate", caught != null)
    }

    private fun cacheEntry(
        barcode: String,
        name: String = "Cached",
        fetchedAt: Instant = clock.current,
    ) = OffLookupCacheEntry(
        barcode = barcode,
        name = name,
        brand = null,
        imageUrl = null,
        resolvingHost = OffHost.FOOD,
        fetchedAt = fetchedAt,
    )

    private class FakeClock(var current: Instant) : Clock {
        override fun now(): Instant = current
    }

    private class FakeOffLookup : OffLookup {
        private val responses = mutableMapOf<String, OffLookupResult>()
        var lookupCallCount = 0
        fun stub(code: String, product: OffProduct, host: OffHost = OffHost.FOOD) {
            responses[code] = OffLookupResult(product, host)
        }
        override suspend fun lookup(barcode: String): OffLookupResult? {
            lookupCallCount++
            return responses[barcode]
        }
    }

    private class FakeOffLookupCacheDao : OffLookupCacheDao {
        val rows = mutableMapOf<String, OffLookupCacheEntry>()
        var deleteCount = 0
        var failFind = false
        var failUpsert = false
        var failDelete = false
        var cancelFind = false
        var cancelUpsert = false
        var cancelDelete = false

        fun put(entry: OffLookupCacheEntry) {
            rows[entry.barcode] = entry
        }

        override suspend fun findByBarcode(barcode: String): OffLookupCacheEntry? {
            if (cancelFind) throw CancellationException("cache read cancelled")
            if (failFind) error("cache read boom")
            return rows[barcode]
        }

        override suspend fun upsert(entry: OffLookupCacheEntry) {
            if (cancelUpsert) throw CancellationException("cache upsert cancelled")
            if (failUpsert) error("cache upsert boom")
            rows[entry.barcode] = entry
        }

        override suspend fun delete(barcode: String) {
            deleteCount++
            if (cancelDelete) throw CancellationException("cache delete cancelled")
            if (failDelete) error("cache delete boom")
            rows.remove(barcode)
        }
    }

    private class FakeProductDao : ProductDao {
        private val rows = mutableMapOf<Long, Product>()
        private var nextId = 1L
        private val all = MutableStateFlow<List<Product>>(emptyList())

        private fun publish() {
            all.value = rows.values.sortedBy { it.name.lowercase() }
        }

        override fun observeAll(): Flow<List<Product>> = all

        override suspend fun findById(id: Long): Product? = rows[id]

        override fun observeById(id: Long): Flow<Product?> = all.map { rows[id] }

        override suspend fun findByBarcode(code: String): Product? =
            rows.values.firstOrNull { it.barcode == code }

        override fun search(query: String): Flow<List<Product>> =
            all.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }

        override suspend fun upsert(product: Product): Long {
            val id = if (product.id == 0L) nextId++ else product.id
            rows[id] = product.copy(id = id)
            publish()
            return id
        }

        override suspend fun delete(product: Product) {
            rows.remove(product.id)
            publish()
        }

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
            publish()
        }
    }
}
