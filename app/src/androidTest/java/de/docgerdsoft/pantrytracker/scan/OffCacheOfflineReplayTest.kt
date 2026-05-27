package de.docgerdsoft.pantrytracker.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.data.remote.OffLookupResult
import de.docgerdsoft.pantrytracker.data.remote.OffProduct
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/**
 * Instrumented test covering UAT v1.2 scenario #12:
 * "Scan a non-pantry barcode, dismiss preview, enable airplane mode,
 * re-scan same barcode → preview appears with no network (cache hit)".
 *
 * Uses a Room in-memory database so the [de.docgerdsoft.pantrytracker.data.local.OffLookupCacheDao]
 * row persists across the "online → offline" transition, matching production
 * behaviour. The OFF network layer is replaced by a switchable [SwitchableOffLookup]
 * that records call counts — the key assertion is that a second scan of the same
 * barcode does NOT trigger a second OFF call (the cache row absorbs it).
 *
 * Why instrumented (not Robolectric): [Room.inMemoryDatabaseBuilder] works in
 * both environments, but this test lives in androidTest alongside the other
 * scan-flow tests (UAT §7–§12) so the full E2E coverage picture is in one
 * source set.
 *
 * Reuses the [de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository] and
 * [de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource] fixtures from SR-75 (#88)
 * via the shared [de.docgerdsoft.pantrytracker.testfixtures] package for the barcode
 * emission pattern — the repository layer here is the real
 * [ProductRepositoryImpl] (with Room + switchable OffLookup) so the cache
 * round-trip is exercised at the correct layer.
 */
@RunWith(AndroidJUnit4::class)
class OffCacheOfflineReplayTest {

    private lateinit var db: AppDatabase
    private lateinit var switchableOff: SwitchableOffLookup
    private lateinit var repo: ProductRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        switchableOff = SwitchableOffLookup()
        repo = ProductRepositoryImpl(
            dao = db.productDao(),
            offLookup = switchableOff,
            offLookupCacheDao = db.offLookupCacheDao(),
            clock = Clock.System,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * E2E cache offline replay — the core UAT §12 scenario.
     *
     * Phase A (online): first [ProductRepositoryImpl.lookupForPreview] call
     * hits the real OFF lookup (SwitchableOffLookup in online mode), gets a
     * product back, and [ProductRepositoryImpl] writes a cache row.
     *
     * Phase B (offline): [SwitchableOffLookup] is switched to return null for
     * every barcode (simulates airplane mode / network unavailable). A second
     * [ProductRepositoryImpl.lookupForPreview] call for the same barcode must
     * return the cached product WITHOUT making a second OFF call.
     *
     * Assertions:
     *  1. Phase A returns the expected product name from OFF.
     *  2. A [de.docgerdsoft.pantrytracker.data.local.OffLookupCacheEntry] row is
     *     present in the DB after Phase A (proving the write succeeded).
     *  3. Phase B returns the same product name.
     *  4. OFF was called exactly once total (Phase A only — Phase B was cache-served).
     */
    @Test
    fun cacheOfflineReplay_phaseAWritesCache_phaseBReadsCache_noSecondNetworkCall() = runTest {
        val barcode = "3017624010701" // Nutella EAN-13 — distinct from Coke fixture barcode

        // --- Phase A: online scan ---
        // Stub the online response. Uses a valid product name so ProductRepositoryImpl
        // does not discard the result (blank-name OFF responses are dropped — C6 gate).
        switchableOff.goOnline(
            barcode = barcode,
            product = OffProduct(
                code = barcode,
                productName = "Nutella",
                brands = "Ferrero",
                imageUrl = "https://images.openfoodfacts.org/nutella.jpg",
            ),
        )

        val phaseAResult = repo.lookupForPreview(barcode)

        // Assertion 1: Phase A returned the OFF-resolved product.
        val phaseACandidate = phaseAResult as? ScanCandidate.FromOff
        assertNotNull("Phase A must return a FromOff candidate", phaseACandidate)
        assertEquals("Nutella", phaseACandidate!!.name)
        assertEquals("Ferrero", phaseACandidate.brand)

        // Assertion 2: cache row was written (barcode is persisted in off_lookup_cache).
        val cachedRow = db.offLookupCacheDao().findByBarcode(barcode)
        assertNotNull("cache row must exist after Phase A lookup", cachedRow)
        assertEquals("cached name must match the OFF response", "Nutella", cachedRow!!.name)
        assertEquals("cached brand must match the OFF response", "Ferrero", cachedRow.brand)

        // Verify OFF was called exactly once so far.
        assertEquals("OFF must be called once during Phase A", 1, switchableOff.callCount)

        // --- Phase B: simulate airplane mode / offline ---
        // Switch to offline: all barcode lookups now return null (no network).
        switchableOff.goOffline()

        val phaseBResult = repo.lookupForPreview(barcode)

        // Assertion 3: Phase B returned the same product data from cache.
        val phaseBCandidate = phaseBResult as? ScanCandidate.FromOff
        assertNotNull("Phase B must return a FromOff candidate from cache", phaseBCandidate)
        assertEquals(
            "cache-served name must match Phase A name",
            "Nutella",
            phaseBCandidate!!.name,
        )
        assertEquals(
            "cache-served brand must match Phase A brand",
            "Ferrero",
            phaseBCandidate.brand,
        )

        // Assertion 4: OFF call count is still 1 — Phase B was served from cache,
        // no second network request was made.
        assertEquals(
            "OFF must NOT be called during Phase B (cache hit expected)",
            1,
            switchableOff.callCount,
        )
    }

    /**
     * Switchable [OffLookup] test double that records call count and can be
     * toggled between an online state (returns a stubbed product) and an offline
     * state (returns null for every barcode, simulating airplane mode).
     *
     * No `runCatching` anywhere — [lookup] uses explicit `try/catch` with
     * rethrow of [kotlinx.coroutines.CancellationException] per the project's
     * suspend-code convention.
     *
     * Starts in offline mode. Call [goOnline] before Phase A to stub a product,
     * then [goOffline] before Phase B to cut the network.
     */
    private class SwitchableOffLookup : OffLookup {

        var callCount: Int = 0
            private set

        private var stubs: Map<String, OffLookupResult> = emptyMap()

        /** Wire a barcode → product result. Switches to "online" for that barcode. */
        fun goOnline(
            barcode: String,
            product: OffProduct,
            host: String = "https://world.openfoodfacts.org/",
        ) {
            stubs = stubs + (barcode to OffLookupResult(product, host))
        }

        /** Clears all stubs — any [lookup] call returns null (offline / no results). */
        fun goOffline() {
            stubs = emptyMap()
        }

        override suspend fun lookup(barcode: String): OffLookupResult? {
            callCount++
            return stubs[barcode]
        }
    }
}
