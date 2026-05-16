package de.docgerdsoft.pantrytracker.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
        repo = ProductRepositoryImpl(db.productDao(), clock = clock)
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

    private class FakeClock(initial: Instant) : Clock {
        private var current: Instant = initial
        override fun now(): Instant = current
        fun advanceBy(millis: Long) {
            current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
        }
    }
}
