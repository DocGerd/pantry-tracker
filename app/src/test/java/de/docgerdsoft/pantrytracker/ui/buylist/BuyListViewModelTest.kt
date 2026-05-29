package de.docgerdsoft.pantrytracker.ui.buylist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.data.remote.OffLookupResult
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuyListViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repo = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = NoOpOffLookup,
            offLookupCacheDao = db.offLookupCacheDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes the repository buying list`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 1)
        repo.setRestockSettings(id, lowLimit = 2, defaultBuyAmount = 2)
        val vm = BuyListViewModel(repo)
        vm.uiState.test {
            // The VM's init collector and Room's flow dispatch on separate
            // schedulers, so the populated state may arrive a tick after the
            // initial empty one — await until Milk shows up.
            var names = awaitItem().items.map { it.name }
            while (names.isEmpty()) {
                names = awaitItem().items.map { it.name }
            }
            assertEquals(listOf("Milk"), names)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onBought restocks by defaultBuyAmount and item leaves the list`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 1)
        repo.setRestockSettings(id, lowLimit = 2, defaultBuyAmount = 3)
        val vm = BuyListViewModel(repo)
        // Await the populated list first so the restock has a steady-state to
        // mutate, then trigger the restock and await the item falling off.
        vm.uiState.test {
            var items = awaitItem().items
            while (items.isEmpty()) items = awaitItem().items
            vm.onBought(items.first()) // 1 + 3 = 4 > limit 2 → off the list
            while (items.isNotEmpty()) items = awaitItem().items
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(4, repo.findById(id)!!.quantity) // 1 + 3
    }

    @Test
    fun `onBought repo failure surfaces a Couldn't error`() = runTest {
        val failing = object : NoOpRepository() {
            override suspend fun applyDelta(productId: Long, delta: Int): Unit = error("disk full")
        }
        val vm = BuyListViewModel(failing)
        vm.onBought(product(id = 1, defaultBuyAmount = 2))
        advanceUntilIdle()
        assertEquals("Couldn't restock: disk full", vm.uiState.value.error)
    }

    @Test
    fun `onBought repo cancellation does not surface an error`() = runTest {
        val cancelling = object : NoOpRepository() {
            override suspend fun applyDelta(productId: Long, delta: Int): Unit =
                throw CancellationException("scope torn down")
        }
        val vm = BuyListViewModel(cancelling)
        vm.onBought(product(id = 1, defaultBuyAmount = 2))
        advanceUntilIdle()
        // CE takes the rethrow arm, so no "Couldn't …" message is stamped.
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `load failure surfaces a Couldn't error`() = runTest {
        val failing = object : NoOpRepository() {
            override fun observeBuyingList(): Flow<List<Product>> = flow { error("locked") }
        }
        val vm = BuyListViewModel(failing)
        advanceUntilIdle()
        assertEquals("Couldn't load the buying list: locked", vm.uiState.value.error)
    }

    @Test
    fun `dismissError clears the error`() = runTest {
        val failing = object : NoOpRepository() {
            override fun observeBuyingList(): Flow<List<Product>> = flow { error("locked") }
        }
        val vm = BuyListViewModel(failing)
        advanceUntilIdle()
        vm.dismissError()
        assertEquals(null, vm.uiState.value.error)
    }

    private fun product(id: Long, defaultBuyAmount: Int) = Product(
        id = id, barcode = null, name = "x", quantity = 0, lowLimit = 0,
        defaultBuyAmount = defaultBuyAmount,
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        updatedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
    )

    private object NoOpOffLookup : OffLookup {
        override suspend fun lookup(barcode: String): OffLookupResult? = null
    }

    /** Minimal interface stub for the error-injection tests. */
    private open class NoOpRepository : ProductRepository {
        override fun observeProducts(): Flow<List<Product>> = MutableStateFlow(emptyList())
        override fun search(query: String): Flow<List<Product>> = MutableStateFlow(emptyList())
        override fun observeBuyingList(): Flow<List<Product>> = MutableStateFlow(emptyList())
        override suspend fun setRestockSettings(productId: Long, lowLimit: Int?, defaultBuyAmount: Int) = Unit
        override suspend fun findById(id: Long): Product? = null
        override fun observeById(id: Long): Flow<Product?> = MutableStateFlow(null)
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
        override suspend fun restore(product: Product) = Unit
    }
}
