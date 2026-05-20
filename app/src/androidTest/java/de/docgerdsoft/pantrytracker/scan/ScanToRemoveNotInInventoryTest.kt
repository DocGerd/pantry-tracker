package de.docgerdsoft.pantrytracker.scan

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.PantryTrackerNavGraph
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UAT §12 — Scan to Remove, not-in-inventory + Switch-to-Add.
 *
 * Empty repo. Scans a barcode that has no local row. ScanViewModel routes
 * to `Phase.NotInInventory(barcode)` per spec D4 (Remove mode skips OFF
 * and goes straight to NotInInventory on local miss / qty==0).
 *
 * Tapping "Switch to Add" calls `onSwitchToAdd()` which flips mode to Add
 * and re-resolves the captured barcode via `lookupForPreview`. We pre-seed
 * the OFF response so the second resolution lands at Preview(FromOff) and
 * the top bar flips green ("Scan to Add").
 *
 * Covers UAT §12 both rows: 1 (sheet shows "Not in inventory" + barcode +
 * Switch-to-Add), 2 (tap Switch-to-Add → top bar flips green + OFF lookup
 * proceeds).
 */
class ScanToRemoveNotInInventoryTest {

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
    fun scanToRemove_notInInventory_switchesToAdd_andReResolvesViaOff() {
        val barcode = "4006381333932"
        val repo = FakeProductRepository().apply {
            // Empty seeded rows → first resolution in Remove mode produces
            // NotInInventory. We also pre-seed an OFF hit so that the
            // post-switch re-resolution in Add mode produces Preview(FromOff)
            // and the user sees the product name on the green Add bar.
            lookupResponses[barcode] = ScanCandidate.FromOff(
                barcode = barcode,
                name = "Mystery Snack",
                brand = null,
                imageUrl = null,
            )
        }
        val camera = FakeCameraSource()
        val container = AppContainer(productRepository = repo, cameraSource = camera)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // --- Navigate to Scan to Remove ---
        // Empty pantry: "Scan to Remove" appears once in ScanButtonsRow only
        // (EmptyState shows "Scan to Add" + "Add manually" but not Remove).
        // So `onNodeWithText` is unambiguous.
        rule.onNodeWithText("Scan to Remove").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Scan to Remove").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Scan to Remove").assertIsDisplayed()

        // --- Fire synthetic barcode ---
        camera.emit(barcode)

        // --- UAT §12 row 1: NotInInventory sheet ---
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Not in your inventory yet")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("Not in your inventory yet").assertIsDisplayed()
        // The body line embeds the barcode — substring match.
        rule.onNode(hasText(barcode, substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Switch to Add").assertIsDisplayed()

        // --- UAT §12 row 2: tap Switch to Add ---
        rule.onNodeWithText("Switch to Add").performClick()

        // After the switch, ScanViewModel flips mode to Add (top bar title
        // "Scan to Add") and re-resolves the same barcode. The second
        // resolution lands at Preview(FromOff) carrying our seeded name.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Mystery Snack").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Mystery Snack").assertIsDisplayed()

        // Top bar title flipped from "Scan to Remove" to "Scan to Add" —
        // this proves the mode flip propagated through ScanUiState (the
        // top-bar color also flipped, but a color assertion needs pixel
        // diff — title is the load-bearing user-visible signal).
        rule.onNodeWithText("Scan to Add").assertIsDisplayed()
        // The "Confirm" button in the now-Preview sheet reads "Confirm Add"
        // — confirms mode-driven button copy flipped.
        rule.onNodeWithText("Confirm Add").assertIsDisplayed()

        // Lookup was called twice for this barcode: once on the initial
        // Remove-mode scan (which short-circuits inside resolveBarcode to
        // findLocalByBarcode; our fake's lookupForPreview is NOT invoked in
        // Remove mode — see ScanViewModel.resolveBarcode), and once again
        // after onSwitchToAdd in Add mode. Assert only the post-switch
        // invocation to make the test resilient to a future refactor that
        // optimises away the Remove-mode lookup.
        assertTrue(
            "expected at least one lookupForPreview call after switch, got: ${repo.lookupCalls}",
            repo.lookupCalls.any { it == barcode },
        )
        // No products added yet — Switch-to-Add only re-resolves; Confirm
        // hasn't been pressed.
        assertEquals(0, repo.addedProducts.size)
    }

    private companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
