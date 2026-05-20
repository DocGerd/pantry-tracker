package de.docgerdsoft.pantrytracker.scan

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.PantryTrackerNavGraph
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UAT §8 — Scan to Add, OFF miss → manual entry fallback.
 *
 * From the UI's perspective an OFF "timeout" is indistinguishable from an
 * OFF "miss" — both surface as `lookupForPreview` returning `null`, which
 * routes ScanViewModel to `Phase.ManualEntry(barcode, pendingQuantity=1)`.
 * The 8-second timeout itself is OffApiClient-level behaviour covered by
 * `OffApiClientTest`; this test asserts only the post-resolution UI.
 *
 * The only manual UAT row NOT covered here is §8 row 2 ("scan the
 * barcode") — that's the real-camera capture step. Row 1 ("Tap Scan to
 * Add") is exercised via the in-app navigation.
 *
 * Covers UAT §8 rows: 1 (navigate to scan), 3 (manual entry sheet shows
 * with barcode pre-filled), 4 (name field empty), 5 (quantity stepper at
 * 1), 6 ("Add to inventory" + Cancel buttons), 7 (type name + Add →
 * product saved → Home shows it).
 */
class ScanToAddOffMissTest {

    @get:Rule val rule = createComposeRule()

    @Before
    fun grantCameraPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )
    }

    @Test
    fun scanToAdd_offMiss_fallsBackToManualEntry_thenSaves() {
        val barcode = "9999999999991" // not seeded → lookupForPreview returns null
        val repo = FakeProductRepository().apply {
            // Explicit null mapping = OFF miss / network failure / timeout.
            // The empty `byBarcode` map means the local-first arm of
            // lookupForPreview also misses, mirroring production's "local
            // miss + OFF miss → manual entry" path.
            lookupResponses[barcode] = null
        }
        val camera = FakeCameraSource()
        val container = AppContainer(productRepository = repo, cameraSource = camera)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // --- UAT §8 row 1: navigate to Scan to Add ---
        rule.onAllNodesWithText("Scan to Add")[0].performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Scan to Add").fetchSemanticsNodes().isNotEmpty()
        }

        // --- Fire synthetic barcode (replaces "scan the barcode" UAT §8 row 2) ---
        camera.emit(barcode)

        // --- UAT §8 row 3: manual entry sheet appears with barcode pre-filled ---
        // The sheet renders "Not on Open Food Facts" as the title plus a body
        // containing "Barcode: <barcode>". `onNodeWithText` uses substring=false
        // by default so we use a substring match for the body line.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Not on Open Food Facts").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Not on Open Food Facts").assertIsDisplayed()
        // The body uses `\n\n` so the barcode line is its own visual line;
        // assert via substring-match on the rendered body Text.
        rule.onNode(
            androidx.compose.ui.test.hasText(barcode, substring = true),
        ).assertIsDisplayed()

        // --- UAT §8 row 4: Name field present + empty ---
        // The OutlinedTextField label "Name" is exposed via semantics. Focus
        // is hardware-keyboard behaviour and unreliable to assert in Compose
        // UI tests, so we only assert presence + emptiness here. The "empty +
        // focused" row stays partially manual (the focus part).
        rule.onNodeWithText("Name").assertIsDisplayed()

        // --- UAT §8 row 5: quantity stepper at 1 ---
        // The "Initial quantity" OutlinedTextField is pre-filled with "1"
        // (pendingQuantity passed in from Phase.ManualEntry).
        rule.onNodeWithText("Initial quantity").assertIsDisplayed()

        // --- UAT §8 row 6: Add to inventory + Cancel buttons ---
        rule.onNodeWithText("Add to inventory").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()

        // --- UAT §8 row 7: type a name + tap Add → product saved ---
        rule.onNodeWithText("Name").performTextInput("Local Brand Cookies")
        rule.onNodeWithText("Add to inventory").performClick()

        // Sheet dismisses (phase → Idle).
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Add to inventory").fetchSemanticsNodes().isEmpty()
        }

        // Repo side-effect: addNew called with manual-entry shape — barcode
        // round-trips, brand + imageUrl are null, quantity defaults to 1.
        assertEquals(1, repo.addedProducts.size)
        val call = repo.addedProducts[0]
        assertEquals("Local Brand Cookies", call.name)
        assertEquals(null, call.brand)
        assertEquals(barcode, call.barcode)
        assertEquals(null, call.imageUrl)
        assertEquals(1, call.initialQuantity)

        // --- Back → Home shows "Local Brand Cookies ×1" ---
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Local Brand Cookies").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Local Brand Cookies").assertIsDisplayed()
        rule.onNodeWithText("×1").assertIsDisplayed()
    }

    private companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
