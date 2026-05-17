package de.docgerdsoft.pantrytracker.ui.home

import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var fake: FakeProductRepository
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeProductRepository()
        vm = HomeViewModel(fake)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_emptyList_emptyQuery() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.query)
            assertEquals(emptyList<Product>(), state.products)
            assertEquals(false, state.showAddSheet)
        }
    }

    @Test
    fun observeProducts_reflectsRepositoryEmissions() = runTest {
        val now = Clock.System.now()
        fake.emit(
            listOf(
                Product(id = 1, barcode = null, name = "Coke", quantity = 3, createdAt = now, updatedAt = now),
            ),
        )

        vm.uiState.test {
            // Drop the initial empty state if it arrives first.
            var state = awaitItem()
            if (state.products.isEmpty()) state = awaitItem()
            assertEquals(1, state.products.size)
            assertEquals("Coke", state.products[0].name)
        }
    }

    @Test
    fun setQuery_nonEmpty_switchesToSearchFlow() = runTest {
        vm.uiState.test {
            awaitItem() // initial empty state
            vm.setQuery("co")
            val state = awaitItem()
            assertEquals("co", state.query)
            assertEquals("co", fake.lastSearchQuery)
        }
    }

    @Test
    fun setQuery_blankWhitespace_routesToObserveProducts_notSearch() = runTest {
        vm.uiState.test {
            awaitItem() // initial empty state
            vm.setQuery("   ")
            val state = awaitItem()
            assertEquals("   ", state.query)
            // Blank query goes through observeProducts, not search.
            assertNull(fake.lastSearchQuery)
        }
    }

    @Test
    fun setQuery_trimsBeforePassingToSearch() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.setQuery("  co  ")
            awaitItem()
            assertEquals("co", fake.lastSearchQuery)
        }
    }

    @Test
    fun openAddSheet_andDismiss_togglesFlag() = runTest {
        vm.uiState.test {
            assertEquals(false, awaitItem().showAddSheet)
            vm.openAddSheet()
            assertEquals(true, awaitItem().showAddSheet)
            vm.dismissAddSheet()
            assertEquals(false, awaitItem().showAddSheet)
        }
    }

    @Test
    fun submitAdd_callsRepository_andDismissesSheetAfterInsert() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.openAddSheet()
            assertEquals(true, awaitItem().showAddSheet)
            vm.submitAdd(name = " Coke ", initialQuantity = 3)
            assertEquals(false, awaitItem().showAddSheet)
        }
        assertEquals("Coke", fake.lastAdded?.name)
        assertEquals(3, fake.lastAdded?.initialQuantity)
    }

    @Test
    fun submitAdd_blankName_isIgnored_andSheetStaysOpen() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.openAddSheet()
            assertEquals(true, awaitItem().showAddSheet)
            vm.submitAdd(name = "   ", initialQuantity = 1)
            expectNoEvents()
            // Sheet must stay open: invalid submit must not lie about success.
            assertEquals(true, vm.uiState.value.showAddSheet)
        }
        assertNull(fake.lastAdded)
    }

    @Test
    fun submitAdd_nonPositiveQuantity_isIgnored_andSheetStaysOpen() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.openAddSheet()
            assertEquals(true, awaitItem().showAddSheet)
            vm.submitAdd(name = "Coke", initialQuantity = 0)
            expectNoEvents()
            vm.submitAdd(name = "Coke", initialQuantity = -2)
            expectNoEvents()
            assertEquals(true, vm.uiState.value.showAddSheet)
        }
        assertNull(fake.lastAdded)
    }

    @Test
    fun confirmDelete_callsRepository_andClearsPending() = runTest {
        val now = Clock.System.now()
        val p = Product(id = 7, barcode = null, name = "Coke", quantity = 1, createdAt = now, updatedAt = now)
        vm.uiState.test {
            awaitItem()
            vm.requestDelete(p)
            assertEquals(p, awaitItem().pendingDelete)
            vm.confirmDelete()
            assertNull(awaitItem().pendingDelete)
        }
        assertEquals(7L, fake.lastDeletedId)
    }

    @Test
    fun cancelDelete_clearsPending_andDoesNotCallRepository() = runTest {
        val now = Clock.System.now()
        val p = Product(id = 7, barcode = null, name = "Coke", quantity = 1, createdAt = now, updatedAt = now)
        vm.uiState.test {
            awaitItem()
            vm.requestDelete(p)
            assertEquals(p, awaitItem().pendingDelete)
            vm.cancelDelete()
            assertNull(awaitItem().pendingDelete)
        }
        assertNull(fake.lastDeletedId)
    }

    private class FakeProductRepository : ProductRepository {
        private val all = MutableStateFlow<List<Product>>(emptyList())
        var lastSearchQuery: String? = null
        var lastAdded: AddCall? = null
        var lastDeletedId: Long? = null

        data class AddCall(
            val name: String,
            val brand: String?,
            val barcode: String?,
            val imageUrl: String?,
            val initialQuantity: Int,
        )

        fun emit(list: List<Product>) {
            all.value = list
        }

        override fun observeProducts(): Flow<List<Product>> = all.asStateFlow()
        override fun search(query: String): Flow<List<Product>> {
            lastSearchQuery = query
            return all.asStateFlow()
        }
        override fun observeById(id: Long): Flow<Product?> =
            MutableStateFlow(all.value.firstOrNull { it.id == id }).asStateFlow()

        override suspend fun findById(id: Long): Product? = all.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null

        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long {
            lastAdded = AddCall(name, brand, barcode, imageUrl, initialQuantity)
            return 1L
        }

        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) {
            lastDeletedId = productId
        }
    }
}
