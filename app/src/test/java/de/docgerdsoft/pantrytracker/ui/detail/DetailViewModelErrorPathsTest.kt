package de.docgerdsoft.pantrytracker.ui.detail

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

/**
 * Plain-JVM coverage for [DetailViewModel]'s repository-failure and
 * cancellation paths — the arms the existing `DetailViewModelTest` leaves
 * uncovered because its fake never makes `findById` / `observeById` throw.
 *
 * Two contracts are pinned per operation:
 *  - a real [Exception] from the repository surfaces as a `"Couldn't …"`
 *    user-facing message (or `shouldNavigateBack` for the init precheck);
 *  - a [CancellationException] is **re-thrown**, never swallowed into an error
 *    message (project convention; see CLAUDE.md "runCatching swallows
 *    CancellationException"). Because it propagates out of the `viewModelScope`
 *    child coroutine as ordinary cancellation, the assertion is that no error
 *    state was written.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelErrorPathsTest {

    private val now = Instant.fromEpochMilliseconds(1_000L)
    private fun product(id: Long = 1L) = Product(
        id = id,
        barcode = "$id",
        name = "Coke",
        quantity = 3,
        createdAt = now,
        updatedAt = now,
    )

    @Before
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    // --- init precheck (findById) ---------------------------------------

    @Test
    fun init_findByIdThrows_setsShouldNavigateBack() = runTest {
        val repo = ErrorRepo(findByIdError = RuntimeException("db locked"))
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        // Precheck failure is treated like a stale nav arg: pop the screen.
        assertTrue(vm.uiState.value.shouldNavigateBack)
        assertNull(vm.uiState.value.product)
    }

    @Test
    fun init_findByIdCancelled_doesNotNavigateBackOrError() = runTest {
        val repo = ErrorRepo(findByIdError = CancellationException("cancelled"))
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        // CE is re-thrown out of initialize → the launch cancels before any
        // state write; it must NOT be misread as "row gone" (navigateBack) or
        // surfaced as an error.
        assertFalse(vm.uiState.value.shouldNavigateBack)
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.product)
    }

    // --- observe stream (observeById) -----------------------------------

    @Test
    fun observe_throwsException_surfacesReadInventoryError() = runTest {
        val repo = ErrorRepo(
            findByIdResult = product(),
            observeError = RuntimeException("cursor died"),
        )
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        // findById succeeded (product seeded), then the observe stream threw.
        assertEquals("Couldn't read inventory: cursor died", vm.uiState.value.error)
        assertEquals("Coke", vm.uiState.value.product?.name)
    }

    @Test
    fun observe_cancelled_doesNotSurfaceError() = runTest {
        val repo = ErrorRepo(
            findByIdResult = product(),
            observeError = CancellationException("scope torn down"),
        )
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        // CE from the collect must re-throw, not become a "Couldn't …" message,
        // and must tear down the collect WITHOUT clobbering the already-seeded
        // product (symmetry with the Exception case above).
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.shouldNavigateBack)
        assertEquals("Coke", vm.uiState.value.product?.name)
    }

    // --- rename / stepperDelta / confirmDelete cancellation rethrow ------

    @Test
    fun rename_repoCancelled_doesNotSetError() = runTest {
        val repo = ErrorRepo(findByIdResult = product(), renameError = CancellationException("x"))
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        vm.rename("New Name")
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun stepperDelta_repoCancelled_doesNotSetError() = runTest {
        val repo = ErrorRepo(findByIdResult = product(), deltaError = CancellationException("x"))
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        vm.stepperDelta(+1)
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun confirmDelete_repoCancelled_doesNotSetError_andClearsDialog() = runTest {
        val repo = ErrorRepo(findByIdResult = product(), deleteError = CancellationException("x"))
        val vm = DetailViewModel(repo, productId = 1L)
        advanceUntilIdle()
        vm.requestDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        // Dialog still closes (synchronous update before the launch), but the CE
        // takes the rethrow arm so no error message is stamped.
        assertFalse(vm.uiState.value.showDeleteConfirm)
        assertNull(vm.uiState.value.error)
    }

    private class ErrorRepo(
        private val findByIdResult: Product? = null,
        private val findByIdError: Throwable? = null,
        private val observeError: Throwable? = null,
        private val renameError: Throwable? = null,
        private val deltaError: Throwable? = null,
        private val deleteError: Throwable? = null,
    ) : ProductRepository {

        override fun observeById(id: Long): Flow<Product?> =
            observeError?.let { err -> flow { throw err } }
                ?: MutableStateFlow(findByIdResult).asStateFlow()

        override suspend fun findById(id: Long): Product? {
            findByIdError?.let { throw it }
            return findByIdResult
        }

        override suspend fun rename(productId: Long, newName: String) {
            renameError?.let { throw it }
        }

        override suspend fun applyDelta(productId: Long, delta: Int) {
            deltaError?.let { throw it }
        }

        override suspend fun delete(productId: Long) {
            deleteError?.let { throw it }
        }

        // Unused by these tests — satisfy the interface.
        override fun observeProducts(): Flow<List<Product>> = MutableStateFlow(emptyList())
        override fun search(query: String): Flow<List<Product>> = MutableStateFlow(emptyList())
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long = 0L
        override suspend fun restore(product: Product) = Unit
    }
}
