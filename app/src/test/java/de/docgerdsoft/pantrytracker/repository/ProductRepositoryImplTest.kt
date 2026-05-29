package de.docgerdsoft.pantrytracker.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.local.OffLookupCacheEntry
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.remote.OffHost
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.data.remote.OffLookupResult
import de.docgerdsoft.pantrytracker.data.remote.OffProduct
import de.docgerdsoft.pantrytracker.util.JulLogCapture
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
// LargeClass: this is the canonical behavioural test fixture for
// ProductRepositoryImpl — splitting it by feature area (CRUD vs preview vs
// cache) would scatter the shared Room/clock setUp across files for no
// readability gain. The 600-line threshold is calibrated for production
// classes, not exhaustive test suites.
@Suppress("LargeClass")
class ProductRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var fakeOff: FakeOffLookup
    private lateinit var repo: ProductRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000L))
        // Tests that don't exercise lookupForPreview pass an empty FakeOffLookup;
        // any unexpected call would still record into lookupCallCount and could be asserted.
        // Hoisted to a class field so the C.4 cache tests can assert
        // `fakeOff.lookupCallCount` against the repo wired into setUp.
        fakeOff = FakeOffLookup()
        repo = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addNew_returnsRowId_andRowVisibleInObserveProducts() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 3)
        assertNotNull(id)

        repo.observeProducts().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("Coke", items[0].name)
            assertEquals(3, items[0].quantity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun applyDelta_positive_increasesQuantity() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 1)
        repo.applyDelta(id, +4)
        assertEquals(5, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_negative_decreasesQuantity() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 5)
        repo.applyDelta(id, -2)
        assertEquals(3, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_quantityWouldGoNegative_clampsToZero() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 1)
        repo.applyDelta(id, -10)
        assertEquals(0, repo.findById(id)?.quantity)
    }

    @Test
    fun applyDelta_unknownId_isNoOp() = runTest {
        // No exception, no inserted row.
        repo.applyDelta(productId = 9999L, delta = 1)
        repo.observeProducts().test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun applyDelta_noOpWhenQuantityUnchanged_doesNotTouchUpdatedAt() = runTest {
        val id = repo.addNew(name = "Pasta", initialQuantity = 0)
        val before = repo.findById(id)!!.updatedAt
        clock.advanceBy(1_000) // would change updatedAt if a write happened

        // -5 on a zero-quantity row clamps back to 0 — same as current. Repo must short-circuit.
        repo.applyDelta(id, -5)

        val after = repo.findById(id)!!
        assertEquals(0, after.quantity)
        assertEquals(before, after.updatedAt) // updatedAt unchanged: no write happened
    }

    @Test
    fun rename_changesName_andTouchesUpdatedAt() = runTest {
        val id = repo.addNew(name = "Cokq", initialQuantity = 1)
        val before = repo.findById(id)!!.updatedAt
        clock.advanceBy(1_000)

        repo.rename(id, "Coke")

        val after = repo.findById(id)!!
        assertEquals("Coke", after.name)
        assertEquals(before.toEpochMilliseconds() + 1_000, after.updatedAt.toEpochMilliseconds())
    }

    @Test
    fun rename_unknownId_isNoOp() = runTest {
        // No exception, no inserted row.
        repo.rename(productId = 9999L, newName = "Anything")
        repo.observeProducts().test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rename_sameName_isNoOp_doesNotTouchUpdatedAt() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 1)
        val before = repo.findById(id)!!.updatedAt
        clock.advanceBy(1_000)

        repo.rename(id, "Coke")

        val after = repo.findById(id)!!
        assertEquals(before, after.updatedAt) // unchanged: no write happened
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = repo.addNew(name = "Coke", initialQuantity = 1)
        repo.delete(id)
        assertNull(repo.findById(id))
    }

    @Test
    fun restore_reInsertsProductWithOriginalIdAndTimestamps() = runTest {
        // Build the seed Product directly so brand + imageUrl are non-null —
        // addNew() leaves both null by default, which would make the brand /
        // imageUrl assertions below vacuous (null round-tripping to null
        // doesn't prove every field is preserved). The clock value is hand-
        // stamped to a known instant so the restored row can be compared
        // against a value the test fixture controls end-to-end. Inserted via
        // dao.upsert so Room still owns id assignment (mirrors what addNew
        // would have done internally).
        val seedInstant = clock.now()
        val seed = Product(
            id = 0L, // autoGenerate — Room assigns the real id on upsert
            barcode = "5449000000996",
            name = "Coke",
            brand = "Coca-Cola Co.",
            imageUrl = "https://images.openfoodfacts.org/coke.jpg",
            quantity = 3,
            createdAt = seedInstant,
            updatedAt = seedInstant,
        )
        val id = db.productDao().upsert(seed)
        val original = repo.findById(id)!!
        // Advance the clock so a write would visibly bump updatedAt — proves
        // restore preserves the captured Instant and doesn't re-stamp it.
        clock.advanceBy(60_000)

        repo.delete(id)
        assertNull(repo.findById(id))

        repo.restore(original)

        val restored = repo.findById(id)
        assertNotNull(restored)
        assertEquals(id, restored?.id)
        assertEquals("Coke", restored?.name)
        assertEquals("Coca-Cola Co.", restored?.brand)
        assertEquals("5449000000996", restored?.barcode)
        assertEquals("https://images.openfoodfacts.org/coke.jpg", restored?.imageUrl)
        assertEquals(3, restored?.quantity)
        // Pins the contract: restore round-trips the captured timestamps —
        // the post-undo row is identity-equal to the pre-delete one (the
        // 60 s clock advance would shift updatedAt if restore re-stamped).
        assertEquals(original.createdAt, restored?.createdAt)
        assertEquals(original.updatedAt, restored?.updatedAt)
    }

    @Test
    fun findLocalByBarcode_returnsRow_orNull() = runTest {
        repo.addNew(name = "Coke", barcode = "5449000000996", initialQuantity = 1)
        assertEquals("Coke", repo.findLocalByBarcode("5449000000996")?.name)
        assertNull(repo.findLocalByBarcode("0000000000000"))
    }

    // --- lookupForPreview tests ---

    @Test
    fun lookupForPreview_localHit_returnsPersisted_doesNotHitNetwork() = runTest {
        val fakeOff = FakeOffLookup()
        // Seed via repo so Room assigns the real id
        val id = repo.addNew(name = "Local Coke", barcode = "111", initialQuantity = 5)
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("111")

        val persisted = result as? ScanCandidate.Persisted
        assertNotNull("expected Persisted, was $result", persisted)
        assertEquals(id, persisted!!.product.id)
        assertEquals("Local Coke", persisted.name)
        assertEquals(0, fakeOff.lookupCallCount) // critical: no network on local hit
    }

    @Test
    fun lookupForPreview_localMiss_offHit_returnsFromOff() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub("222", OffProduct(productName = "Sprite", brands = "Coca-Cola", imageUrl = "https://x"))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("222")

        val fromOff = result as? ScanCandidate.FromOff
        assertNotNull("expected FromOff, was $result", fromOff)
        assertEquals("Sprite", fromOff!!.name)
        assertEquals("Coca-Cola", fromOff.brand)
        assertEquals("https://x", fromOff.imageUrl)
        assertEquals("222", fromOff.barcode)
    }

    @Test
    fun lookupForPreview_localMiss_offMiss_returnsNull() = runTest {
        val fakeOff = FakeOffLookup()
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("333")

        assertNull(result)
    }

    @Test
    fun lookupForPreview_localMiss_offHitButNameMissing_returnsNull() = runTest {
        // OFF returned an envelope with status=1 but `product_name` was absent.
        // We can't preview without a name, so treat as miss → manual entry.
        val fakeOff = FakeOffLookup()
        fakeOff.stub("444", OffProduct(productName = null, brands = "Brand only"))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("444")

        assertNull(result)
        // Pin the negative cache-write contract: a name-blank OFF response
        // must drop to manual entry AND leave the cache untouched. Without
        // this, a regression that caches the invalid response would make
        // the next scan hit the cache and re-skip OFF — locking the bad
        // shape in until the 30-day TTL expires. (PR #60 pr-test-analyzer)
        assertNull(
            "OFF responses with blank name must NOT be cached",
            db.offLookupCacheDao().findByBarcode("444"),
        )
    }

    @Test
    fun lookupForPreview_blankBarcode_returnsNull_withoutHittingNetwork() = runTest {
        val fakeOff = FakeOffLookup()
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )
        assertNull(sut.lookupForPreview(""))
        assertNull(sut.lookupForPreview("   "))
        assertEquals(0, fakeOff.lookupCallCount)
    }

    // --- #48 / C.4: OFF lookup cache (cache hit, miss + write, stale refresh,
    // TTL boundary, pantry-supersedes, addNew evicts, null-barcode no-op) ---

    @Test
    fun lookupForPreview_cacheHit_returnsFromOff_withoutCallingOff() = runTest {
        val cacheDao = db.offLookupCacheDao()
        val now = clock.now()
        cacheDao.upsert(
            OffLookupCacheEntry(
                barcode = "0123456789",
                name = "Cached Cookies",
                brand = "CacheBrand",
                imageUrl = "https://images.openfoodfacts.org/cookies.jpg",
                resolvingHost = OffHost.FOOD,
                fetchedAt = now.minus(1.seconds), // 1 second old, well within TTL
            ),
        )

        val candidate = repo.lookupForPreview("0123456789") as ScanCandidate.FromOff

        assertEquals("Cached Cookies", candidate.name)
        assertEquals("CacheBrand", candidate.brand)
        assertEquals("https://images.openfoodfacts.org/cookies.jpg", candidate.imageUrl)
        assertEquals("Cache hit must not call OFF", 0, fakeOff.lookupCallCount)
    }

    @Test
    fun lookupForPreview_cacheMiss_callsOff_andUpsertsCacheRow() = runTest {
        fakeOff.stub(
            code = "0123456789",
            product = OffProduct(
                code = "0123456789",
                productName = "Fresh Cookies",
                brands = "FreshBrand",
                imageUrl = "https://images.openfoodfacts.org/x.jpg",
            ),
            host = OffHost.PET_FOOD,
        )

        val candidate = repo.lookupForPreview("0123456789") as ScanCandidate.FromOff

        assertEquals("Fresh Cookies", candidate.name)
        val written = db.offLookupCacheDao().findByBarcode("0123456789")!!
        assertEquals("0123456789", written.barcode)
        assertEquals("Fresh Cookies", written.name)
        assertEquals("FreshBrand", written.brand)
        assertEquals("https://images.openfoodfacts.org/x.jpg", written.imageUrl)
        assertEquals(OffHost.PET_FOOD, written.resolvingHost)
        assertEquals(clock.now(), written.fetchedAt)
    }

    @Test
    fun lookupForPreview_cacheStale_refreshesFromOff() = runTest {
        val cacheDao = db.offLookupCacheDao()
        val now = clock.now()
        cacheDao.upsert(
            OffLookupCacheEntry(
                barcode = "0123456789",
                name = "Old Cookies",
                brand = null,
                imageUrl = null,
                resolvingHost = OffHost.FOOD,
                fetchedAt = now.minus(30.days).minus(1.milliseconds), // just past TTL
            ),
        )
        fakeOff.stub(
            code = "0123456789",
            product = OffProduct(
                code = "0123456789",
                productName = "New Cookies",
                brands = "NewBrand",
                imageUrl = null,
            ),
        )

        val candidate = repo.lookupForPreview("0123456789") as ScanCandidate.FromOff

        assertEquals("New Cookies", candidate.name)
        val written = cacheDao.findByBarcode("0123456789")!!
        assertEquals("New Cookies", written.name)
        assertEquals(now, written.fetchedAt)
    }

    @Test
    fun lookupForPreview_cacheExactlyAtTTL_stillFresh() = runTest {
        val cacheDao = db.offLookupCacheDao()
        val now = clock.now()
        cacheDao.upsert(
            OffLookupCacheEntry(
                barcode = "0123456789",
                name = "Boundary Cookies",
                brand = null,
                imageUrl = null,
                resolvingHost = OffHost.FOOD,
                fetchedAt = now.minus(30.days), // exactly TTL old
            ),
        )

        val candidate = repo.lookupForPreview("0123456789") as ScanCandidate.FromOff
        assertEquals("Boundary Cookies", candidate.name)
        assertEquals("Cache hit at TTL boundary must not call OFF", 0, fakeOff.lookupCallCount)
    }

    @Test
    fun lookupForPreview_pantryRow_supersedesCache() = runTest {
        repo.addNew(
            name = "PantryName",
            brand = null,
            barcode = "0123456789",
            imageUrl = null,
            initialQuantity = 5,
        )
        // addNew evicts the cache row by design (see addNew_evictsCacheRow test), so
        // upsert the cache row AFTER addNew to set up the precondition for this test.
        db.offLookupCacheDao().upsert(
            OffLookupCacheEntry(
                barcode = "0123456789",
                name = "CacheName",
                brand = null,
                imageUrl = null,
                resolvingHost = OffHost.FOOD,
                fetchedAt = clock.now(),
            ),
        )

        val candidate = repo.lookupForPreview("0123456789")
        assertTrue(candidate is ScanCandidate.Persisted)
        assertEquals("PantryName", (candidate as ScanCandidate.Persisted).product.name)
        assertEquals("Pantry hit must not consult OFF", 0, fakeOff.lookupCallCount)
    }

    @Test
    fun addNew_withCachedBarcode_evictsCacheRow() = runTest {
        val cacheDao = db.offLookupCacheDao()
        cacheDao.upsert(
            OffLookupCacheEntry(
                barcode = "0123456789",
                name = "StaleCache",
                brand = null,
                imageUrl = null,
                resolvingHost = OffHost.FOOD,
                fetchedAt = clock.now(),
            ),
        )

        repo.addNew(
            name = "User Override Name",
            brand = null,
            barcode = "0123456789",
            imageUrl = null,
            initialQuantity = 1,
        )

        assertNull(
            "Cache row must be evicted on addNew with same barcode",
            cacheDao.findByBarcode("0123456789"),
        )
    }

    @Test
    fun addNew_withNullBarcode_doesNotCrash_andUnrelatedCacheRowUntouched() = runTest {
        val cacheDao = db.offLookupCacheDao()
        cacheDao.upsert(
            OffLookupCacheEntry(
                barcode = "9999",
                name = "Unrelated",
                brand = null,
                imageUrl = null,
                resolvingHost = OffHost.FOOD,
                fetchedAt = clock.now(),
            ),
        )

        repo.addNew(
            name = "Manual Entry",
            brand = null,
            barcode = null,
            imageUrl = null,
            initialQuantity = 1,
        )

        // The pre-existing row for an unrelated barcode is untouched.
        assertNotNull(cacheDao.findByBarcode("9999"))
    }

    // --- #32 / SR-14, SR-15: response-field size caps + image_url scheme gate ---

    @Test
    fun lookupForPreview_offHitNameOver256Chars_truncatesNameAt256() = runTest {
        val fakeOff = FakeOffLookup()
        // 1_000_000 chars — a buggy/hostile OFF response would otherwise stream
        // straight into Compose Text and Room (SR-14: unbounded String fields).
        fakeOff.stub("777", OffProduct(productName = "x".repeat(1_000_000)))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("777") as ScanCandidate.FromOff
        assertEquals(256, result.name.length)
    }

    @Test
    fun lookupForPreview_offHitBrandOver256Chars_truncatesBrandAt256() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub(
            "778",
            OffProduct(productName = "Coke", brands = "y".repeat(10_000)),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("778") as ScanCandidate.FromOff
        assertEquals(256, result.brand?.length)
    }

    @Test
    fun lookupForPreview_offHitImageUrlHttps_preserved() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub(
            "779",
            OffProduct(productName = "Sprite", imageUrl = "https://images.openfoodfacts.org/sprite.jpg"),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("779") as ScanCandidate.FromOff
        assertEquals("https://images.openfoodfacts.org/sprite.jpg", result.imageUrl)
    }

    @Test
    fun lookupForPreview_offHitImageUrlHttp_returnsNullImageUrl() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub(
            "780",
            OffProduct(productName = "Sprite", imageUrl = "http://insecure.example/x.jpg"),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("780") as ScanCandidate.FromOff
        assertNull(result.imageUrl)
    }

    @Test
    fun lookupForPreview_offHitImageUrlFileScheme_returnsNullImageUrl() = runTest {
        // SR-15 headline attack: `file:///etc/passwd` would otherwise have
        // Coil's FileUriFetcher attempt a local file read.
        val fakeOff = FakeOffLookup()
        fakeOff.stub(
            "781",
            OffProduct(productName = "Sprite", imageUrl = "file:///etc/passwd"),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("781") as ScanCandidate.FromOff
        assertNull(result.imageUrl)
    }

    @Test
    fun lookupForPreview_offHitImageUrlContentScheme_returnsNullImageUrl() = runTest {
        // Companion to the file-scheme case — content:// would otherwise let
        // Coil's ContentUriFetcher silently query arbitrary content providers
        // via ContentResolver.openInputStream (no UI prompt).
        val fakeOff = FakeOffLookup()
        fakeOff.stub(
            "782",
            OffProduct(productName = "Sprite", imageUrl = "content://com.evil/data"),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("782") as ScanCandidate.FromOff
        assertNull(result.imageUrl)
    }

    @Test
    fun lookupForPreview_offHitImageUrlOver2048Chars_returnsNullImageUrl() = runTest {
        // 2 KB cap on URL length defends against a 4 GB asset URL whose query
        // string alone exhausts memory before decode (SR-15 second angle).
        val fakeOff = FakeOffLookup()
        val longUrl = "https://images.openfoodfacts.org/" + "x".repeat(3_000)
        fakeOff.stub(
            "783",
            OffProduct(productName = "Sprite", imageUrl = longUrl),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("783") as ScanCandidate.FromOff
        assertNull(result.imageUrl)
    }

    // --- PR #40 review: exact-boundary tests for the length caps ---

    @Test
    fun lookupForPreview_offHitNameExactly256Chars_passesThrough() = runTest {
        val fakeOff = FakeOffLookup()
        val name256 = "x".repeat(256)
        fakeOff.stub("784", OffProduct(productName = name256))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("784") as ScanCandidate.FromOff
        // length 256 is below take(256) — preserved unchanged.
        assertEquals(256, result.name.length)
        assertEquals(name256, result.name)
    }

    @Test
    fun lookupForPreview_offHitNameExactly257Chars_truncatesTo256() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub("785", OffProduct(productName = "x".repeat(257)))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("785") as ScanCandidate.FromOff
        // Pins the boundary so a typo to .take(255) or MAX_OFF_TEXT_LENGTH=257 fails.
        assertEquals(256, result.name.length)
    }

    @Test
    fun lookupForPreview_offHitImageUrlExactly2047Chars_preserved() = runTest {
        val fakeOff = FakeOffLookup()
        // 2047 chars — under the strict < 2048 gate.
        val url = "https://" + "x".repeat(2039)
        assertEquals(2047, url.length)
        fakeOff.stub("786", OffProduct(productName = "Sprite", imageUrl = url))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("786") as ScanCandidate.FromOff
        assertEquals(url, result.imageUrl)
    }

    @Test
    fun lookupForPreview_offHitImageUrlExactly2048Chars_returnsNull() = runTest {
        val fakeOff = FakeOffLookup()
        // Exactly at the cap — the strict < operator rejects.
        val url = "https://" + "x".repeat(2040)
        assertEquals(2048, url.length)
        fakeOff.stub("787", OffProduct(productName = "Sprite", imageUrl = url))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("787") as ScanCandidate.FromOff
        // Pins the boundary so a typo to MAX_IMAGE_URL_LENGTH_EXCLUSIVE=20480
        // or operator-flip to `<=` fails — the existing 3 K-char test would
        // pass both regressions.
        assertNull(result.imageUrl)
    }

    // --- PR #40 review: brand mapping null + blank cases ---

    @Test
    fun lookupForPreview_offHitBrandNull_returnsNullBrand() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub("788", OffProduct(productName = "Sprite", brands = null))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("788") as ScanCandidate.FromOff
        assertNull(result.brand)
    }

    @Test
    fun lookupForPreview_offHitBrandBlank_returnsNullBrand() = runTest {
        // Pins the `?.takeIf { it.isNotBlank() }?.let { capOffText(...) }`
        // chain — a regression to `brand = off.brands?.take(256)` would map
        // blank to "  " here and no test currently fails.
        val fakeOff = FakeOffLookup()
        fakeOff.stub("789", OffProduct(productName = "Sprite", brands = "   "))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("789") as ScanCandidate.FromOff
        assertNull(result.brand)
    }

    // --- PR #40 review: image-url scheme is case-insensitive (RFC 3986 §3.1) ---

    @Test
    fun lookupForPreview_offHitImageUrlUppercaseHttps_preserved() = runTest {
        // Schemes are case-insensitive per RFC 3986; OkHttp/Coil normalise.
        // A regression to case-sensitive `startsWith("https://")` would drop
        // this legitimate (if unusual) shape.
        val fakeOff = FakeOffLookup()
        fakeOff.stub("790", OffProduct(productName = "Sprite", imageUrl = "HTTPS://images.openfoodfacts.org/x.jpg"))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("790") as ScanCandidate.FromOff
        assertEquals("HTTPS://images.openfoodfacts.org/x.jpg", result.imageUrl)
    }

    @Test
    fun lookupForPreview_offHitImageUrlMixedCaseHttps_preserved() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub("791", OffProduct(productName = "Sprite", imageUrl = "Https://images.openfoodfacts.org/x.jpg"))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        val result = sut.lookupForPreview("791") as ScanCandidate.FromOff
        assertEquals("Https://images.openfoodfacts.org/x.jpg", result.imageUrl)
    }

    // --- PR #40 review: new gate/cap logs leave a forensic trail ---

    @Test
    fun lookupForPreview_offHitNameTruncated_logsInfoWithLengthsAndHint() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub("792", OffProduct(productName = "x".repeat(500)))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        JulLogCapture("ProductRepositoryImpl").use { capture ->
            sut.lookupForPreview("792")
            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected truncation log: $joined", joined.contains("name truncated"))
            assertTrue("expected from-length: $joined", joined.contains("500"))
            assertTrue("expected to-length: $joined", joined.contains("256"))
            assertTrue("expected redacted code hint: $joined", joined.contains("<short>"))
            // The raw 500-char payload must not appear in the log.
            assertFalse("raw name leaked: $joined", joined.contains("x".repeat(500)))
        }
    }

    @Test
    fun lookupForPreview_offHitImageUrlSchemeRejected_logsFineCategoricalReason() = runTest {
        val fakeOff = FakeOffLookup()
        fakeOff.stub("793", OffProduct(productName = "Sprite", imageUrl = "file:///etc/passwd"))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        JulLogCapture("ProductRepositoryImpl").use { capture ->
            sut.lookupForPreview("793")
            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected scheme-reason log: $joined", joined.contains("image_url dropped"))
            assertTrue("expected categorical reason: $joined", joined.contains("scheme"))
            // The hostile URL itself must NOT appear — it's attacker-controlled content.
            assertFalse("raw URL leaked: $joined", joined.contains("/etc/passwd"))
        }
    }

    @Test
    fun lookupForPreview_offHitImageUrlLengthRejected_logsFineCategoricalReasonWithLength() = runTest {
        val fakeOff = FakeOffLookup()
        val long = "https://images.openfoodfacts.org/" + "y".repeat(3_000)
        fakeOff.stub("794", OffProduct(productName = "Sprite", imageUrl = long))
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        JulLogCapture("ProductRepositoryImpl").use { capture ->
            sut.lookupForPreview("794")
            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected length-reason log: $joined", joined.contains("length="))
            assertFalse("raw URL leaked: $joined", joined.contains("yyyyyyyyyy"))
        }
    }

    // --- #31 / SR-12: discard log redacts barcode + drops brand ---

    @Test
    fun lookupForPreview_offHitBlankName_logsHintWithoutBarcodeOrBrand() = runTest {
        // The prior INFO log line `"OFF hit for $code discarded — name blank, brand=${off.brands}"`
        // leaked both the raw barcode and the OFF response brand string into
        // logcat. The redaction keeps the diagnostic value via barcodeHint()
        // and drops `brand` entirely (zero value over the hint).
        val fakeOff = FakeOffLookup()
        fakeOff.stub(
            "5449000000996",
            OffProduct(productName = null, brands = "Coca-Cola Company"),
        )
        val sut = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = fakeOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )

        JulLogCapture("ProductRepositoryImpl").use { capture ->
            assertNull(sut.lookupForPreview("5449000000996"))

            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected hint '5449…96' in log: $joined", joined.contains("5449…96"))
            assertFalse(
                "full barcode '5449000000996' leaked into log: $joined",
                joined.contains("5449000000996"),
            )
            assertFalse(
                "brand 'Coca-Cola Company' leaked into log: $joined",
                joined.contains("Coca-Cola Company"),
            )
        }
    }

    private class FakeClock(initial: Instant) : Clock {
        private var current: Instant = initial
        override fun now(): Instant = current
        fun advanceBy(millis: Long) {
            current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
        }
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
}
