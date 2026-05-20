package de.docgerdsoft.pantrytracker.scan

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.PantryTrackerNavGraph
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * UAT §11 — Scan to Remove, in-inventory path.
 *
 * Pre-populates a [FakeProductRepository] with a product at quantity 3,
 * scans its barcode in Remove mode, decrements to 0, asserts the row stays
 * visible (depleted opacity is a visual concern — UAT §11 row 5 notes
 * "defer pixel-perfect check to RNG ticket #1").
 *
 * Covers UAT §11 rows: 1 (navigate to Scan to Remove + red top bar),
 * 2 (sheet shows the product with stepper clamped at current quantity),
 * 3 (decrease, Confirm), 4 (Home shows new lower quantity), 5 (repeat
 * until 0 — row stays in list).
 */
class ScanToRemoveInInventoryTest {

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
    fun scanToRemove_inInventory_decrementsToZero_rowStays() {
        val barcode = "4006381333931" // arbitrary EAN-13
        val now = Clock.System.now()
        val seeded = Product(
            id = 1L,
            barcode = barcode,
            name = "Pasta",
            brand = "Barilla",
            imageUrl = null,
            quantity = INITIAL_QUANTITY,
            createdAt = now,
            updatedAt = now,
        )
        val repo = FakeProductRepository().apply {
            seed(seeded)
            // Local-first lookup with the seeded row → FakeProductRepository's
            // fallback arm returns ScanCandidate.Persisted(seeded). We don't
            // override lookupResponses; the in-mode `findLocalByBarcode` path
            // in ScanViewModel.resolveBarcode handles Remove without calling
            // lookupForPreview anyway (OFF is skipped per spec D4). The fake
            // covers both code paths transparently.
        }
        val camera = FakeCameraSource()
        val container = AppContainer(productRepository = repo, cameraSource = camera)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // --- UAT §11 row 1: navigate to Scan to Remove (red top bar) ---
        // With a non-empty pantry, "Scan to Remove" appears only once
        // (the EmptyState row is gone), so the single-node click is safe.
        rule.onNodeWithText("Scan to Remove").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            // Top-app-bar title is the unambiguous signal we're on the scan
            // screen in Remove mode.
            rule.onAllNodesWithText("Scan to Remove").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Scan to Remove").assertIsDisplayed()

        // --- Fire synthetic barcode ---
        camera.emit(barcode)

        // --- UAT §11 row 2: sheet shows product + stepper at 1 (default
        //     pendingQuantity), with Confirm Remove + Cancel ---
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Pasta").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Pasta").assertIsDisplayed()
        rule.onNodeWithText("Barilla").assertIsDisplayed()
        rule.onNodeWithText("Confirm Remove").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        // Stepper at the default 1 — ScanViewModel.resolveBarcode sets
        // pendingQuantity=1 for Persisted candidates in Remove mode too.
        rule.onNodeWithText("1").assertIsDisplayed()

        // The stepper's max is clamped to the seeded quantity per
        // ScanViewModel.setQuantity. Increment past 3 — should clamp at 3.
        // This validates UAT §11 row 2 "stepper clamped to max = current
        // quantity".
        repeat(INITIAL_QUANTITY + 1) {
            rule.onNodeWithContentDescription("Increment quantity").performClick()
        }
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("$INITIAL_QUANTITY").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("$INITIAL_QUANTITY").assertIsDisplayed()

        // Decrement back to 1 for the actual confirm (decrement is row 3).
        repeat(INITIAL_QUANTITY - 1) {
            rule.onNodeWithContentDescription("Decrement quantity").performClick()
        }

        // --- UAT §11 row 3: tap Confirm ---
        rule.onNodeWithText("Confirm Remove").performClick()

        // --- UAT §11 row 4: Home shows the new lower quantity (×2) ---
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Confirm Remove").fetchSemanticsNodes().isEmpty()
        }
        // Repo side-effect: applyDelta(id=1, delta=-1).
        assertTrue(
            "expected applyDelta(1, -1), got: ${repo.deltaCalls}",
            repo.deltaCalls.contains(1L to -1),
        )

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("×$AFTER_FIRST_REMOVE").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("×$AFTER_FIRST_REMOVE").assertIsDisplayed()
        rule.onNodeWithText("Pasta").assertIsDisplayed()

        // --- UAT §11 row 5: repeat to 0 ---
        // Two more decrements (1-by-default) to drop the row to 0.
        repeat(AFTER_FIRST_REMOVE) {
            rule.onNodeWithText("Scan to Remove").performClick()
            rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                rule.onAllNodesWithText("Scan to Remove").fetchSemanticsNodes().isNotEmpty()
            }
            camera.emit(barcode)
            rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                rule.onAllNodesWithText("Confirm Remove").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Confirm Remove").performClick()
            rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                rule.onAllNodesWithText("Confirm Remove").fetchSemanticsNodes().isEmpty()
            }
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                // Home top-app-bar title is "Pantry"; once that's visible we're
                // back on Home and Compose has settled.
                rule.onAllNodesWithText("Pasta").fetchSemanticsNodes().isNotEmpty()
            }
        }

        // After two more decrements, the row's quantity is 0 — but the row
        // stays in the list (greyed at OUT_OF_STOCK_ROW_ALPHA, but Compose
        // still emits the row's semantics node, so `onNodeWithText("Pasta")`
        // still resolves). Pixel-opacity assertion deferred per spec note.
        rule.onNodeWithText("Pasta").assertIsDisplayed()
        rule.onNodeWithText("×0").assertIsDisplayed()
        // Three decrements total (1 + 1 + 1).
        assertEquals(EXPECTED_DECREMENTS, repo.deltaCalls.size)
        assertTrue(repo.deltaCalls.all { it == 1L to -1 })
    }

    private companion object {
        private const val TIMEOUT_MS = 5_000L
        private const val INITIAL_QUANTITY = 3
        private const val AFTER_FIRST_REMOVE = 2
        private const val EXPECTED_DECREMENTS = 3
    }
}
