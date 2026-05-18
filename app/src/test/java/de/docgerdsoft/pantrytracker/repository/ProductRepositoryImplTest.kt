package de.docgerdsoft.pantrytracker.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
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
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock
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
        repo = ProductRepositoryImpl(db.productDao(), FakeOffLookup(), clock = clock)
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
        val sut = ProductRepositoryImpl(db.productDao(), fakeOff, clock)

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
        val sut = ProductRepositoryImpl(db.productDao(), fakeOff, clock)

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
        val sut = ProductRepositoryImpl(db.productDao(), fakeOff, clock)

        val result = sut.lookupForPreview("333")

        assertNull(result)
    }

    @Test
    fun lookupForPreview_localMiss_offHitButNameMissing_returnsNull() = runTest {
        // OFF returned an envelope with status=1 but `product_name` was absent.
        // We can't preview without a name, so treat as miss → manual entry.
        val fakeOff = FakeOffLookup()
        fakeOff.stub("444", OffProduct(productName = null, brands = "Brand only"))
        val sut = ProductRepositoryImpl(db.productDao(), fakeOff, clock)

        val result = sut.lookupForPreview("444")

        assertNull(result)
    }

    @Test
    fun lookupForPreview_blankBarcode_returnsNull_withoutHittingNetwork() = runTest {
        val fakeOff = FakeOffLookup()
        val sut = ProductRepositoryImpl(db.productDao(), fakeOff, clock)
        assertNull(sut.lookupForPreview(""))
        assertNull(sut.lookupForPreview("   "))
        assertEquals(0, fakeOff.lookupCallCount)
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
        val sut = ProductRepositoryImpl(db.productDao(), fakeOff, clock)

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
        val responses = mutableMapOf<String, OffProduct>()
        var lookupCallCount = 0
        fun stub(code: String, value: OffProduct) { responses[code] = value }
        override suspend fun lookup(barcode: String): OffProduct? {
            lookupCallCount++
            return responses[barcode]
        }
    }
}
