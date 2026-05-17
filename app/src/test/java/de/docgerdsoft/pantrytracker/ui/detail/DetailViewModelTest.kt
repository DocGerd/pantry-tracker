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
    fun init_findByIdReturnsNull_setsShouldNavigateBack_immediately() = runTest {
        // Stale nav arg — row no longer exists. Spec D2: must auto-pop.
        val repo = FakeRepo() // no seed → findById returns null
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        assertNull(vm.uiState.value.product)
        assertTrue("stale nav arg should trigger pop", vm.uiState.value.shouldNavigateBack)
    }

    @Test
    fun init_findByIdReturnsProduct_seedsState_withoutNavigateBack() = runTest {
        val repo = FakeRepo()
        repo.emit(product())
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        assertEquals("Coke", vm.uiState.value.product?.name)
        assertFalse(vm.uiState.value.shouldNavigateBack)
    }

    @Test
    fun observe_emitsNull_afterSuccessfulInit_setsShouldNavigateBack() = runTest {
        // Row existed at open time, then deleted while screen is up.
        val repo = FakeRepo()
        repo.emit(product())
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.shouldNavigateBack)

        repo.emit(null) // deleted while open
        advanceUntilIdle()
        assertNull(vm.uiState.value.product)
        assertTrue(vm.uiState.value.shouldNavigateBack)
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
    fun rename_repoThrows_setsErrorMessage() = runTest {
        val repo = FakeRepo()
        repo.emit(product())
        repo.renameError = RuntimeException("disk full")
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()

        vm.rename("New Name")
        advanceUntilIdle()
        assertEquals("Couldn't rename: disk full", vm.uiState.value.error)
    }

    @Test
    fun stepperDelta_repoThrows_setsErrorMessage() = runTest {
        val repo = FakeRepo()
        repo.emit(product())
        repo.deltaError = RuntimeException("constraint")
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()

        vm.stepperDelta(+1)
        advanceUntilIdle()
        assertEquals("Couldn't update quantity: constraint", vm.uiState.value.error)
    }

    @Test
    fun confirmDelete_repoThrows_setsErrorMessage_andClearsDialog() = runTest {
        val repo = FakeRepo()
        repo.emit(product())
        repo.deleteError = RuntimeException("locked")
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()

        vm.requestDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showDeleteConfirm) // dialog closes regardless
        assertEquals("Couldn't delete: locked", vm.uiState.value.error)
        assertFalse(vm.uiState.value.shouldNavigateBack) // delete failed → stay
    }

    @Test
    fun dismissError_clearsErrorField() = runTest {
        val repo = FakeRepo()
        repo.emit(product())
        repo.renameError = RuntimeException("x")
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        vm.rename("Y")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)

        vm.dismissError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun onNavigatedBack_clearsShouldNavigateBack() = runTest {
        val repo = FakeRepo() // no seed → init detects stale id → flag is true
        val vm = DetailViewModel(repo, productId = 1L)
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

        // Error-injection knobs for testing repository-failure paths.
        var renameError: Throwable? = null
        var deltaError: Throwable? = null
        var deleteError: Throwable? = null

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
            deltaError?.let { throw it }
            deltaCalls += (productId to delta)
        }
        override suspend fun rename(productId: Long, newName: String) {
            renameError?.let { throw it }
            renameCalls += (productId to newName)
        }
        override suspend fun delete(productId: Long) {
            deleteError?.let { throw it }
            deleteCalls += productId
        }
    }
}
