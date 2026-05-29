package de.docgerdsoft.pantrytracker.scan

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.PantryTrackerNavGraph
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.remote.OffHost
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.data.remote.OffLookupResult
import de.docgerdsoft.pantrytracker.data.remote.OffProduct
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl
import de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * UAT §12 — **OFF lookup cache offline replay**, driven end-to-end through the
 * real Compose UI (`PantryTrackerNavGraph` → `ScanScreen` → `ScanPreviewSheet`),
 * not at the repository layer.
 *
 * This is the genuine user story the v1.2 minified-APK appendix scenario #12
 * asks for: *scan a non-pantry barcode, dismiss the preview, go offline,
 * re-scan the same barcode → the preview sheet still appears with the product,
 * served from cache, with no second network call.*
 *
 * ### What this drives (genuinely through the UI)
 *  - The **real** [PantryTrackerNavGraph] is rendered, so navigation
 *    ("Scan to Add"), the `ScanViewModel`, `ScanScreen`, and the
 *    `ScanPreviewSheet` recomposition all run — the same code paths a real
 *    device hits.
 *  - A real [FakeCameraSource] (SR-75 / #88 seam) emits the synthetic barcode
 *    that ML Kit would emit on a real decode; it is wired through
 *    `AppContainer.cameraSource` exactly as the other `Scan*Test` classes do.
 *    There is **no** stubbing of `lookupForPreview` — the barcode travels
 *    `FakeCameraSource.emit` → `ScanScreen.BindTestCameraSource` →
 *    `ScanViewModel.onBarcodeDecoded` → `ProductRepositoryImpl.lookupForPreview`.
 *
 * ### Why the real repository (not [de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository])
 * The cache round-trip we are proving lives in [ProductRepositoryImpl]: the
 * online scan writes an `off_lookup_cache` Room row, and the offline re-scan
 * reads it back *instead of* calling OFF. `FakeProductRepository.lookupForPreview`
 * returns a pre-canned `ScanCandidate` and has no cache table, so it could only
 * fake this behaviour. To exercise the genuine path we inject the production
 * [ProductRepositoryImpl] (backed by an in-memory Room DB) and make only the
 * **network seam** switchable via [SwitchableOffLookup] — flipping it offline
 * is the test's analogue of airplane mode.
 *
 * The in-memory DB persists the cache row across the online→offline transition
 * within the test, matching production (the Room table survives the network
 * going away).
 */
class OffCacheOfflineReplayTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var switchableOff: SwitchableOffLookup
    private lateinit var repo: ProductRepositoryImpl
    private lateinit var camera: FakeCameraSource

    @Before
    fun setUp() {
        // Pre-grant CAMERA so the permission gate resolves to Granted and the
        // scan screen mounts — same pattern as ScanToAddOffHitTest.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )

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
        camera = FakeCameraSource()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * The core UAT §12 E2E.
     *
     * **Phase A (online):** navigate to Scan to Add, emit the barcode. OFF is
     * online and returns Nutella, so the preview sheet renders the product and
     * [ProductRepositoryImpl] writes a cache row. The user taps **Cancel**
     * (dismiss without saving) — this is the appendix's "dismiss preview" step,
     * and crucially leaves NO pantry row, so the cache (not a pantry hit) is
     * what serves Phase B.
     *
     * **Phase B (offline):** flip [SwitchableOffLookup] offline (airplane-mode
     * analogue) and re-emit the same barcode. The preview sheet appears again
     * with the same product — served from the Room cache row written in Phase A.
     *
     * Assertions:
     *  1. Phase A: preview sheet renders the OFF product (name + brand visible).
     *  2. Phase B: preview sheet renders the SAME product while offline.
     *  3. Zero OFF calls happened in Phase B — the OFF call count is unchanged
     *     across the offline re-scan (the Room cache row absorbed it).
     *  4. No pantry row was ever created (the barcode was only ever cached,
     *     never confirmed), proving the Phase-B hit came from the cache table
     *     and not from a `findLocalByBarcode` pantry hit.
     */
    @Test
    fun offlineReplay_phaseAScanCaches_phaseBReScanServesFromCache_noNetwork() {
        val barcode = "3017624010701" // Nutella EAN-13
        switchableOff.goOnline(
            barcode = barcode,
            product = OffProduct(
                code = barcode,
                productName = "Nutella",
                brands = "Ferrero",
                imageUrl = "https://images.openfoodfacts.org/nutella.jpg",
            ),
        )

        val container = AppContainer(productRepository = repo, cameraSource = camera)
        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // --- Navigate to Scan to Add (empty pantry → "Scan to Add" appears twice). ---
        rule.onAllNodesWithText("Scan to Add")[0].performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Scan to Add").fetchSemanticsNodes().isNotEmpty()
        }

        // --- Phase A (online): emit barcode → preview sheet renders from OFF. ---
        camera.emit(barcode)
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Nutella").fetchSemanticsNodes().isNotEmpty()
        }
        // Assertion 1: preview sheet shows the OFF-resolved product.
        rule.onNodeWithText("Nutella").assertIsDisplayed()
        rule.onNodeWithText("Ferrero").assertIsDisplayed()
        // Confirm-Add button proves this is the FromOff add-mode preview sheet.
        rule.onNodeWithText("Confirm Add").assertIsDisplayed()

        // OFF was hit exactly once for the online scan.
        assertEquals("OFF must be hit once during the online scan", 1, switchableOff.callCount)

        // --- Dismiss the preview WITHOUT saving (appendix "dismiss preview"). ---
        // Tap Cancel → ScanViewModel.dismissPreview → Phase.Idle. No addNew, so
        // no pantry row and the Phase-A cache row stays intact (addNew is the
        // only path that evicts the cache row).
        rule.onNodeWithText("Cancel").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Confirm Add").fetchSemanticsNodes().isEmpty()
        }

        // --- Phase B (offline): cut the network, re-scan the SAME barcode. ---
        switchableOff.goOffline()
        val offlineCallBaseline = switchableOff.callCount

        camera.emit(barcode)
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Nutella").fetchSemanticsNodes().isNotEmpty()
        }
        // Assertion 2: the preview sheet renders the SAME product while offline.
        rule.onNodeWithText("Nutella").assertIsDisplayed()
        rule.onNodeWithText("Ferrero").assertIsDisplayed()
        rule.onNodeWithText("Confirm Add").assertIsDisplayed()

        // Assertion 3: ZERO OFF calls happened during Phase B — the Room cache
        // row absorbed the re-scan. If the cache read had missed, the offline
        // SwitchableOffLookup would have been called (and returned null, routing
        // to ManualEntry / "Not on Open Food Facts" instead of the preview).
        assertEquals(
            "offline re-scan must be served from cache — no OFF call in Phase B",
            offlineCallBaseline,
            switchableOff.callCount,
        )

        // Assertion 4: no pantry row was ever created for this barcode (Cancel
        // never confirmed). This pins that the Phase-B hit came from the
        // off_lookup_cache table, NOT from a findLocalByBarcode pantry hit —
        // otherwise the cache assertion above could pass for the wrong reason.
        assertEquals(
            "barcode must never have been persisted to the pantry",
            null,
            runBlocking { db.productDao().findByBarcode(barcode) },
        )
    }

    /**
     * Switchable [OffLookup] test double — the network seam, the only thing
     * faked in this E2E. Records [callCount] and toggles between an online
     * state (returns a stubbed product) and an offline state (returns null for
     * every barcode, the airplane-mode analogue).
     *
     * No `runCatching`: [lookup] is a plain suspend that does not catch, so a
     * `CancellationException` from a cancelled `viewModelScope` job propagates
     * naturally (the project's suspend-code convention).
     */
    private class SwitchableOffLookup : OffLookup {

        @Volatile
        var callCount: Int = 0
            private set

        @Volatile
        private var stubs: Map<String, OffLookupResult> = emptyMap()

        /** Wire a barcode → product result and switch to "online" for it. */
        fun goOnline(
            barcode: String,
            product: OffProduct,
            host: OffHost = OffHost.FOOD,
        ) {
            stubs = stubs + (barcode to OffLookupResult(product, host))
        }

        /** Clear all stubs — every [lookup] returns null (offline / no network). */
        fun goOffline() {
            stubs = emptyMap()
        }

        override suspend fun lookup(barcode: String): OffLookupResult? {
            callCount++
            return stubs[barcode]
        }
    }

    private companion object {
        // Matches the 5s timeout the other Compose UI scan tests use for cold
        // recomposition on slow CI emulators.
        private const val TIMEOUT_MS = 5_000L
    }
}
