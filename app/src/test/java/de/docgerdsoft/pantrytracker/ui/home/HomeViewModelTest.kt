package de.docgerdsoft.pantrytracker.ui.home

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
    fun setQuery_emptyQuery_streamsAll() = runTest {
        vm.setQuery("")
        assertEquals("", vm.uiState.value.query)
    }

    @Test
    fun setQuery_nonEmpty_switchesToSearchFlow() = runTest {
        vm.setQuery("co")
        assertEquals("co", vm.uiState.value.query)
        assertEquals("co", fake.lastSearchQuery)
    }

    @Test
    fun openAddSheet_andDismiss_togglesFlag() = runTest {
        vm.openAddSheet()
        assertEquals(true, vm.uiState.value.showAddSheet)
        vm.dismissAddSheet()
        assertEquals(false, vm.uiState.value.showAddSheet)
    }

    @Test
    fun submitAdd_callsRepository_andDismissesSheet() = runTest {
        vm.openAddSheet()
        vm.submitAdd(name = " Coke ", initialQuantity = 3)
        assertEquals(false, vm.uiState.value.showAddSheet)
        assertEquals("Coke", fake.lastAdded?.name)
        assertEquals(3, fake.lastAdded?.initialQuantity)
    }

    @Test
    fun submitAdd_blankName_isIgnored() = runTest {
        vm.submitAdd(name = "   ", initialQuantity = 1)
        assertNull(fake.lastAdded)
    }

    @Test
    fun submitAdd_nonPositiveQuantity_isIgnored() = runTest {
        vm.submitAdd(name = "Coke", initialQuantity = 0)
        assertNull(fake.lastAdded)
        vm.submitAdd(name = "Coke", initialQuantity = -2)
        assertNull(fake.lastAdded)
    }

    @Test
    fun confirmDelete_callsRepository_andClearsPending() = runTest {
        val now = Clock.System.now()
        val p = Product(id = 7, barcode = null, name = "Coke", quantity = 1, createdAt = now, updatedAt = now)
        vm.requestDelete(p)
        assertEquals(p, vm.uiState.value.pendingDelete)
        vm.confirmDelete()
        assertEquals(7L, fake.lastDeletedId)
        assertNull(vm.uiState.value.pendingDelete)
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

        override suspend fun findById(id: Long): Product? = all.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null

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
