package de.docgerdsoft.pantrytracker.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repo = ProductRepositoryImpl(db.productDao(), clock = Clock.System)
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
    fun rename_changesName_andTouchesUpdatedAt() = runTest {
        val id = repo.addNew(name = "Cokq", initialQuantity = 1)
        val before = repo.findById(id)!!.updatedAt

        // Sleep one millisecond so the new instant is strictly later.
        Thread.sleep(2)
        repo.rename(id, "Coke")

        val after = repo.findById(id)!!
        assertEquals("Coke", after.name)
        assert(after.updatedAt > before)
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
}
