package de.docgerdsoft.pantrytracker.ui.scan

import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private lateinit var fake: FakeProductRepository
    private lateinit var vm: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeProductRepository()
        vm = ScanViewModel(fake)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialPhase_isIdle() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(ScanUiState.Phase.Idle, state.phase)
        }
    }

    @Test
    fun onBarcodeDecoded_knownBarcode_movesToPreviewWithDefaultQuantityOne() = runTest {
        val now = Clock.System.now()
        val product = Product(id = 1, barcode = "5449000000996", name = "Coca-Cola 0.5L",
            quantity = 0, createdAt = now, updatedAt = now)
        fake.seed(product)

        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onBarcodeDecoded("5449000000996")
            awaitItem() // Loading
            val state = awaitItem()
            val preview = state.phase as ScanUiState.Phase.Preview
            assertEquals("Coca-Cola 0.5L", preview.product.name)
            assertEquals(1, preview.pendingQuantity)
        }
    }

    @Test
    fun onBarcodeDecoded_localMissAndOffMiss_movesToManualEntry() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("0000000000000")
            awaitItem() // Loading
            val state = awaitItem()
            val manual = state.phase as ScanUiState.Phase.ManualEntry
            assertEquals("0000000000000", manual.barcode)
        }
    }

    @Test
    fun onBarcodeDecoded_ignoredWhenAlreadyInPreviewPhase() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "5449000000996", name = "Coca-Cola",
            quantity = 0, createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("5449000000996")
            awaitItem() // Loading
            awaitItem() // Preview
            vm.onBarcodeDecoded("5449000000996") // duplicate scan while sheet open
            expectNoEvents()
        }
    }

    @Test
    fun setQuantity_belowOne_clampsToOne() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem() // Preview pendingQuantity=1
            vm.setQuantity(0)
            expectNoEvents() // already at 1, no new emission
            vm.setQuantity(-5)
            expectNoEvents()
            assertEquals(1, (vm.uiState.value.phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun setQuantity_positiveValue_updatesPreview() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.setQuantity(6)
            val state = awaitItem()
            assertEquals(6, (state.phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun confirmAdd_appliesDeltaAndReturnsToIdle() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.setQuantity(3)
            awaitItem()
            vm.confirmAdd()
            val state = awaitItem()
            assertEquals(ScanUiState.Phase.Idle, state.phase)
        }
        assertEquals(1L to 3, fake.lastDelta)
    }

    @Test
    fun cancelPreview_returnsToIdleWithoutCallingRepository() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertNull(fake.lastDelta)
    }

    @Test
    fun dismissManualEntry_returnsToIdle() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
    }

    @Test
    fun confirmAdd_fromIdle_isNoOp() = runTest {
        vm.uiState.test {
            awaitItem() // Idle
            vm.confirmAdd()
            expectNoEvents()
        }
        assertNull(fake.lastDelta)
    }

    @Test
    fun setQuantity_fromIdle_isNoOp() = runTest {
        vm.uiState.test {
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
            vm.setQuantity(5)
            expectNoEvents()
            assertEquals(ScanUiState.Phase.Idle, vm.uiState.value.phase)
        }
    }

    @Test
    fun onBarcodeDecoded_ignoredWhenAlreadyInManualEntryPhase() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            awaitItem() // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.onBarcodeDecoded("nope") // duplicate while sheet is open
            expectNoEvents()
        }
    }

    @Test
    fun confirmAdd_passesCorrectProductId() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 42L, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Loading
            awaitItem()
            vm.setQuantity(2)
            awaitItem()
            vm.confirmAdd()
            awaitItem()
        }
        assertEquals(42L to 2, fake.lastDelta)
    }

    @Test
    fun onBarcodeDecoded_blankBarcode_isIgnored() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("")
            expectNoEvents()
            vm.onBarcodeDecoded("   ")
            expectNoEvents()
        }
    }

    // ---- New tests for T4 ----

    @Test
    fun onBarcodeDecoded_localMiss_offHit_transitionsThroughLoadingToPreview() = runTest {
        val now = Clock.System.now()
        fake.lookupResponses["222"] = Product(id = 0, barcode = "222", name = "OFF Result",
            quantity = 0, createdAt = now, updatedAt = now)
        vm.uiState.test {
            awaitItem()  // initial Idle
            vm.onBarcodeDecoded("222")
            val loading = awaitItem().phase
            assertTrue("expected Loading, was $loading", loading is ScanUiState.Phase.Loading)
            assertEquals("222", (loading as ScanUiState.Phase.Loading).barcode)
            val preview = awaitItem().phase
            assertTrue(preview is ScanUiState.Phase.Preview)
            assertEquals("OFF Result", (preview as ScanUiState.Phase.Preview).product.name)
        }
    }

    @Test
    fun onBarcodeDecoded_localHit_transitionsThroughLoadingToPreview() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "111", name = "Local Coke",
            quantity = 3, createdAt = now, updatedAt = now))
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("111")
            // Loading sheet flashes even for local hits — caller decision, keeps the path
            // identical regardless of where the data comes from.
            awaitItem()  // Loading
            val preview = awaitItem().phase
            assertTrue(preview is ScanUiState.Phase.Preview)
            assertEquals("Local Coke", (preview as ScanUiState.Phase.Preview).product.name)
        }
    }

    @Test
    fun onBarcodeDecoded_whileLoading_secondBarcode_cancelsFirst() = runTest {
        fake.suspendOnLookup = true
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("111")
            assertEquals("111", (awaitItem().phase as ScanUiState.Phase.Loading).barcode)
            vm.onBarcodeDecoded("222")
            assertEquals("222", (awaitItem().phase as ScanUiState.Phase.Loading).barcode)
            // First lookup was cancelled; only the second one is still running.
            // (completedLookups would only have entries IF suspendOnLookup were turned off;
            //  we don't drain — the test asserts the visible state transition only.)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dismissPreview_duringLoading_cancelsLookup_andReturnsToIdle() = runTest {
        fake.suspendOnLookup = true
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("111")
            assertTrue(awaitItem().phase is ScanUiState.Phase.Loading)
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
            // No completion happened — lookup was cancelled.
            assertEquals(emptyList<String>(), fake.completedLookups)
            // No Phase.Error stamped on top of the dismissed Idle — `runCatching`
            // would catch the CancellationException and race with this Idle write.
            // We use try/catch with explicit CancellationException rethrow instead.
            expectNoEvents()
        }
    }

    @Test
    fun submitManualEntry_blankName_isNoOp() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("444")
            awaitItem()  // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = "   ", initialQuantity = 1)
            expectNoEvents()
            assertTrue(vm.uiState.value.phase is ScanUiState.Phase.ManualEntry)
        }
        assertEquals(0, fake.addedProducts.size)
    }

    @Test
    fun submitManualEntry_nonPositiveQuantity_isNoOp() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("555")
            awaitItem()
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = "Cinnamon", initialQuantity = 0)
            expectNoEvents()
            vm.submitManualEntry(name = "Cinnamon", initialQuantity = -3)
            expectNoEvents()
        }
        assertEquals(0, fake.addedProducts.size)
    }

    @Test
    fun submitManualEntry_validInputs_addsProduct_andReturnsIdle() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("555")
            awaitItem()  // Loading
            assertTrue(awaitItem().phase is ScanUiState.Phase.ManualEntry)
            vm.submitManualEntry(name = " Cinnamon ", initialQuantity = 2)
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertEquals(1, fake.addedProducts.size)
        val added = fake.addedProducts.last()
        assertEquals("Cinnamon", added.name)
        assertEquals("555", added.barcode)
        assertEquals(2, added.initialQuantity)
        assertNull(added.brand)
        assertNull(added.imageUrl)
    }

    @Test
    fun setQuantity_inManualEntryPhase_updatesPendingQuantity() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("666")
            awaitItem()  // Loading
            val manual = awaitItem().phase as ScanUiState.Phase.ManualEntry
            assertEquals(1, manual.pendingQuantity)
            vm.setQuantity(4)
            assertEquals(4, (awaitItem().phase as ScanUiState.Phase.ManualEntry).pendingQuantity)
        }
    }

    private class FakeProductRepository : ProductRepository {
        private val byBarcode = mutableMapOf<String, Product>()
        var lastDelta: Pair<Long, Int>? = null

        var lookupResponses = mutableMapOf<String, Product?>()
        var lookupCallCount = 0
        var suspendOnLookup = false
        val completedLookups = mutableListOf<String>()

        fun seed(p: Product) {
            byBarcode[p.barcode!!] = p
        }

        override fun observeProducts(): Flow<List<Product>> =
            MutableStateFlow(emptyList<Product>()).asStateFlow()
        override fun search(query: String): Flow<List<Product>> =
            MutableStateFlow(emptyList<Product>()).asStateFlow()
        override suspend fun findById(id: Long): Product? =
            byBarcode.values.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = byBarcode[code]
        override suspend fun lookupForPreview(code: String): Product? {
            lookupCallCount++
            if (suspendOnLookup) {
                kotlinx.coroutines.awaitCancellation()  // never returns until job is cancelled
            }
            val result = lookupResponses[code] ?: byBarcode[code]  // fall back to seeded local row
            completedLookups += code
            return result
        }

        data class AddCall(
            val name: String,
            val brand: String?,
            val barcode: String?,
            val imageUrl: String?,
            val initialQuantity: Int,
        )
        val addedProducts = mutableListOf<AddCall>()

        override suspend fun addNew(
            name: String, brand: String?, barcode: String?, imageUrl: String?,
            initialQuantity: Int,
        ): Long {
            addedProducts += AddCall(name, brand, barcode, imageUrl, initialQuantity)
            return (addedProducts.size).toLong()
        }
        override suspend fun applyDelta(productId: Long, delta: Int) {
            lastDelta = productId to delta
        }
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
    }
}
