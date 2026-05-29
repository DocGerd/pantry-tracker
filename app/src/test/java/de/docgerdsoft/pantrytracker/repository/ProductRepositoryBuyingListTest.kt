package de.docgerdsoft.pantrytracker.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.data.remote.OffLookupResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProductRepositoryBuyingListTest {

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
        repo = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = NoOpOffLookup,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `setRestockSettings clamps and surfaces the item on the buying list`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 1)
        repo.setRestockSettings(id, lowLimit = 2, defaultBuyAmount = 0) // 0 clamps to 1
        repo.observeBuyingList().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(1, list.first().defaultBuyAmount) // clamped up to 1
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restock via applyDelta lifts quantity above limit and clears the list`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 1)
        repo.setRestockSettings(id, lowLimit = 2, defaultBuyAmount = 3)
        repo.applyDelta(id, 3) // the "Bought" action
        repo.observeBuyingList().test {
            assertTrue(awaitItem().isEmpty()) // quantity 4 > limit 2 → off the list
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing lowLimit removes the item from the list`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 0)
        repo.setRestockSettings(id, lowLimit = 1, defaultBuyAmount = 1)
        repo.setRestockSettings(id, lowLimit = null, defaultBuyAmount = 1)
        repo.observeBuyingList().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `negative lowLimit clamps to zero`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 0)
        repo.setRestockSettings(id, lowLimit = -5, defaultBuyAmount = 1)
        assertEquals(0, repo.findById(id)!!.lowLimit)
        // quantity 0 <= limit 0 → on the list
        repo.observeBuyingList().test {
            assertEquals(listOf("Milk"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `buying list is ordered most-urgent-first then case-insensitive alphabetical`() = runTest {
        // Distinct quantities exercise the primary `quantity ASC` sort; the two
        // qty=1 rows ("apple" vs "Banana") exercise the `name COLLATE NOCASE`
        // tiebreak — lowercase must NOT sort after uppercase.
        val zero = repo.addNew(name = "Zucchini", initialQuantity = 0)
        val bananaCap = repo.addNew(name = "Banana", initialQuantity = 1)
        val appleLower = repo.addNew(name = "apple", initialQuantity = 1)
        listOf(zero, bananaCap, appleLower).forEach {
            repo.setRestockSettings(it, lowLimit = 5, defaultBuyAmount = 1) // all below limit ⇒ on the list
        }
        repo.observeBuyingList().test {
            val names = awaitItem().map { it.name }
            // quantity 0 first; then the two qty=1 rows alphabetically (case-insensitive).
            assertEquals(listOf("Zucchini", "apple", "Banana"), names)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRestockSettings on unchanged values does not bump updatedAt`() = runTest {
        val id = repo.addNew(name = "Milk", initialQuantity = 1)
        repo.setRestockSettings(id, lowLimit = 2, defaultBuyAmount = 3)
        val before = repo.findById(id)!!.updatedAt
        clock.advanceBy(1_000) // would change updatedAt if a write happened

        repo.setRestockSettings(id, lowLimit = 2, defaultBuyAmount = 3) // identical

        assertEquals(before, repo.findById(id)!!.updatedAt) // no write happened
    }

    @Test
    fun `setRestockSettings unknown id is a no-op`() = runTest {
        repo.setRestockSettings(productId = 9999L, lowLimit = 1, defaultBuyAmount = 1)
        repo.observeBuyingList().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeClock(initial: Instant) : Clock {
        private var current: Instant = initial
        override fun now(): Instant = current
        fun advanceBy(millis: Long) {
            current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
        }
    }

    private object NoOpOffLookup : OffLookup {
        override suspend fun lookup(barcode: String): OffLookupResult? = null
    }
}
