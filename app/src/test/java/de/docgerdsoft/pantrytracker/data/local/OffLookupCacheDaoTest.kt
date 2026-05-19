package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OffLookupCacheDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OffLookupCacheDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.offLookupCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_then_findByBarcode_roundtrips() = runTest {
        val entry = OffLookupCacheEntry(
            barcode = "0123",
            name = "Name",
            brand = "Brand",
            imageUrl = null,
            resolvingHost = "https://world.openfoodfacts.org/",
            fetchedAt = Instant.fromEpochMilliseconds(1L),
        )
        dao.upsert(entry)
        assertEquals(entry, dao.findByBarcode("0123"))
    }

    @Test
    fun upsert_replacesExistingRow_perOnConflictStrategy() = runTest {
        dao.upsert(
            OffLookupCacheEntry(
                barcode = "0123",
                name = "Old",
                brand = null,
                imageUrl = null,
                resolvingHost = "https://world.openfoodfacts.org/",
                fetchedAt = Instant.fromEpochMilliseconds(1L),
            ),
        )
        dao.upsert(
            OffLookupCacheEntry(
                barcode = "0123",
                name = "New",
                brand = "BrandNew",
                imageUrl = null,
                resolvingHost = "https://world.openbeautyfacts.org/",
                fetchedAt = Instant.fromEpochMilliseconds(2L),
            ),
        )
        val got = dao.findByBarcode("0123")!!
        assertEquals("New", got.name)
        assertEquals("BrandNew", got.brand)
        assertEquals("https://world.openbeautyfacts.org/", got.resolvingHost)
        assertEquals(Instant.fromEpochMilliseconds(2L), got.fetchedAt)
    }

    @Test
    fun delete_removesRow() = runTest {
        dao.upsert(
            OffLookupCacheEntry(
                barcode = "0123",
                name = "Name",
                brand = null,
                imageUrl = null,
                resolvingHost = "https://world.openfoodfacts.org/",
                fetchedAt = Instant.fromEpochMilliseconds(1L),
            ),
        )
        dao.delete("0123")
        assertNull(dao.findByBarcode("0123"))
    }

    @Test
    fun findByBarcode_missingBarcode_returnsNull() = runTest {
        assertNull(dao.findByBarcode("9999"))
    }

    @Test
    fun delete_missingBarcode_isNoOp() = runTest {
        // Should not throw or affect any other rows.
        dao.upsert(
            OffLookupCacheEntry(
                barcode = "0123",
                name = "Name",
                brand = null,
                imageUrl = null,
                resolvingHost = "https://world.openfoodfacts.org/",
                fetchedAt = Instant.fromEpochMilliseconds(1L),
            ),
        )
        dao.delete("9999")
        assertEquals("Name", dao.findByBarcode("0123")?.name)
    }
}
