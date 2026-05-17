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
            val state = awaitItem()
            val preview = state.phase as ScanUiState.Phase.Preview
            assertEquals("Coca-Cola 0.5L", preview.product.name)
            assertEquals(1, preview.pendingQuantity)
        }
    }

    @Test
    fun onBarcodeDecoded_unknownBarcode_movesToUnknownBarcode() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("0000000000000")
            val state = awaitItem()
            val unknown = state.phase as ScanUiState.Phase.UnknownBarcode
            assertEquals("0000000000000", unknown.barcode)
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
            awaitItem()
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertNull(fake.lastDelta)
    }

    @Test
    fun dismissUnknownBarcode_returnsToIdle() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            assertTrue(awaitItem().phase is ScanUiState.Phase.UnknownBarcode)
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
    fun onBarcodeDecoded_ignoredWhenAlreadyInUnknownBarcodePhase() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            assertTrue(awaitItem().phase is ScanUiState.Phase.UnknownBarcode)
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

    private class FakeProductRepository : ProductRepository {
        private val byBarcode = mutableMapOf<String, Product>()
        var lastDelta: Pair<Long, Int>? = null

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
        override suspend fun addNew(
            name: String, brand: String?, barcode: String?, imageUrl: String?,
            initialQuantity: Int,
        ): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) {
            lastDelta = productId to delta
        }
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
    }
}
