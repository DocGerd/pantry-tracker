package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProductDao

    private fun product(
        name: String,
        quantity: Int = 1,
        barcode: String? = null,
    ): Product {
        val now = Clock.System.now()
        return Product(
            barcode = barcode,
            name = name,
            quantity = quantity,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_assignsId() = runTest {
        val id = dao.upsert(product(name = "Coke"))
        assertEquals(1L, id)
    }

    @Test
    fun observeAll_ordersByNameCaseInsensitive() = runTest {
        dao.upsert(product(name = "tomato"))
        dao.upsert(product(name = "Apple"))
        dao.upsert(product(name = "banana"))

        dao.observeAll().test {
            val items = awaitItem()
            assertEquals(listOf("Apple", "banana", "tomato"), items.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun findByBarcode_returnsMatchingRow() = runTest {
        dao.upsert(product(name = "Coke", barcode = "5449000000996"))
        dao.upsert(product(name = "Pasta", barcode = "8001505005707"))

        val coke = dao.findByBarcode("5449000000996")
        assertEquals("Coke", coke?.name)

        val missing = dao.findByBarcode("0000000000000")
        assertNull(missing)
    }

    @Test
    fun search_matchesSubstringCaseInsensitive() = runTest {
        dao.upsert(product(name = "Tomato sauce"))
        dao.upsert(product(name = "Olive oil"))
        dao.upsert(product(name = "Cherry tomatoes"))

        dao.search("tomato").test {
            val results = awaitItem().map { it.name }
            assertEquals(listOf("Cherry tomatoes", "Tomato sauce"), results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_removesRow() = runTest {
        val id = dao.upsert(product(name = "Coke"))
        dao.deleteById(id)
        assertNull(dao.findById(id))
    }

    @Test
    fun upsert_updatesExistingRow() = runTest {
        val original = product(name = "Coke", quantity = 1)
        val id = dao.upsert(original)

        val updated = original.copy(id = id, quantity = 7)
        dao.upsert(updated)

        val loaded = dao.findById(id)
        assertEquals(7, loaded?.quantity)
    }
}
