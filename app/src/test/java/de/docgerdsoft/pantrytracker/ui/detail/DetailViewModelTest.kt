package de.docgerdsoft.pantrytracker.ui.detail

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val now = Clock.System.now()
    private fun product(id: Long = 1, name: String = "Coke", quantity: Int = 3) =
        Product(
            id = id,
            barcode = "$id",
            name = name,
            quantity = quantity,
            createdAt = now,
            updatedAt = now,
        )

    @Before
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun observe_emitsProduct_thenNull_setsShouldNavigateBack() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 1L)
        repo.emit(product())
        advanceUntilIdle()
        assertEquals("Coke", vm.uiState.value.product?.name)
        assertTrue(vm.uiState.value.everSeen)
        assertFalse(vm.uiState.value.shouldNavigateBack)

        repo.emit(null) // deleted
        advanceUntilIdle()
        assertNull(vm.uiState.value.product)
        assertTrue(vm.uiState.value.shouldNavigateBack)
    }

    @Test
    fun observe_initialNull_doesNotSetShouldNavigateBack() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 1L)
        // Flow starts with null — VM hits the `else -> state` branch (everSeen is false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.everSeen)
        assertFalse(vm.uiState.value.shouldNavigateBack)
    }

    @Test
    fun rename_trimsAndCallsRepository() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 7L)
        vm.rename("  Coke  ")
        advanceUntilIdle()
        assertEquals(listOf(7L to "Coke"), repo.renameCalls)
    }

    @Test
    fun rename_blankName_isNoOp() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 7L)
        vm.rename("   ")
        advanceUntilIdle()
        assertTrue(repo.renameCalls.isEmpty())
    }

    @Test
    fun stepperDelta_positive_callsApplyDeltaPositive() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 7L)
        vm.stepperDelta(+1)
        advanceUntilIdle()
        assertEquals(listOf(7L to +1), repo.deltaCalls)
    }

    @Test
    fun stepperDelta_negative_callsApplyDeltaNegative() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 7L)
        vm.stepperDelta(-1)
        advanceUntilIdle()
        assertEquals(listOf(7L to -1), repo.deltaCalls)
    }

    @Test
    fun stepperDelta_zero_isNoOp() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 7L)
        vm.stepperDelta(0)
        advanceUntilIdle()
        assertTrue(repo.deltaCalls.isEmpty())
    }

    @Test
    fun requestDelete_setsShowDeleteConfirm() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 1L)
        assertFalse(vm.uiState.value.showDeleteConfirm)
        vm.requestDelete()
        assertTrue(vm.uiState.value.showDeleteConfirm)
    }

    @Test
    fun cancelDelete_clearsShowDeleteConfirm() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 1L)
        vm.requestDelete()
        vm.cancelDelete()
        assertFalse(vm.uiState.value.showDeleteConfirm)
    }

    @Test
    fun confirmDelete_callsRepoDelete_andClearsDialog() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 11L)
        vm.requestDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showDeleteConfirm)
        assertEquals(listOf(11L), repo.deleteCalls)
    }

    @Test
    fun onNavigatedBack_clearsShouldNavigateBack() = runTest {
        val repo = FakeRepo()
        val vm = DetailViewModel(repo, productId = 1L)
        repo.emit(product())
        advanceUntilIdle()
        repo.emit(null) // triggers shouldNavigateBack = true
        advanceUntilIdle()
        assertTrue(vm.uiState.value.shouldNavigateBack)

        vm.onNavigatedBack()
        assertFalse(vm.uiState.value.shouldNavigateBack)
    }

    private class FakeRepo : ProductRepository {
        private val flow = MutableStateFlow<Product?>(null)
        val renameCalls = mutableListOf<Pair<Long, String>>()
        val deltaCalls = mutableListOf<Pair<Long, Int>>()
        val deleteCalls = mutableListOf<Long>()

        fun emit(product: Product?) {
            flow.value = product
        }

        override fun observeById(id: Long): Flow<Product?> = flow.asStateFlow()

        override fun observeProducts(): Flow<List<Product>> = MutableStateFlow(emptyList())
        override fun search(query: String): Flow<List<Product>> = MutableStateFlow(emptyList())
        override suspend fun findById(id: Long): Product? = flow.value
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) {
            deltaCalls += (productId to delta)
        }
        override suspend fun rename(productId: Long, newName: String) {
            renameCalls += (productId to newName)
        }
        override suspend fun delete(productId: Long) {
            deleteCalls += productId
        }
    }
}
