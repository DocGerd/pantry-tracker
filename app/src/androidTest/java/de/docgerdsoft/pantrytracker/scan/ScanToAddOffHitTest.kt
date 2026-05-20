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
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UAT §7 — Scan to Add, OFF hit. Drives the real [PantryTrackerNavGraph]
 * (so the permission gate, ScanScreen, navigation, and Home re-observation
 * all run) via the SR-75 [FakeCameraSource] + [FakeProductRepository]
 * fixtures. The only manual UAT rows NOT covered here are §7 row 3 ("point
 * at the barcode — within ~1 second it auto-detects") — that's the real
 * ML Kit decode latency, which has no test analogue.
 *
 * Covers UAT §7 rows: 1 (camera preview = scan screen mounted), 2 (green
 * top bar), 4 (bottom sheet with name+brand+image-slot+stepper=1
 * +Confirm+Cancel), 5 (increase to 3 + Confirm), 6 (sheet dismisses, camera
 * resumes = Phase.Idle), 7 (Back → Home shows ×3).
 */
class ScanToAddOffHitTest {

    @get:Rule val rule = createComposeRule()

    @Before
    fun grantCameraPermission() {
        // Pre-grant CAMERA so the permission gate immediately resolves to
        // Granted. Using UiAutomation rather than GrantPermissionRule avoids
        // pulling in androidx.test:rules as a new dependency.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )
    }

    @Test
    fun scanToAdd_offHit_previewsThenAddsAtChosenQuantity() {
        val barcode = "5449000000996" // Coca-Cola EAN-13 (matches OffApiClientTest fixture)
        val repo = FakeProductRepository().apply {
            // Pre-seed the OFF "hit" — ProductRepository.lookupForPreview
            // production behaviour: local miss + OFF hit → ScanCandidate.FromOff
            // carrying name / brand / imageUrl. Image URL is intentionally
            // a placeholder Coil can't fetch — we assert on the name and
            // brand text, not the image bytes (per spec note "defer
            // pixel-perfect image check to RNG ticket #1").
            lookupResponses[barcode] = ScanCandidate.FromOff(
                barcode = barcode,
                name = "Coca-Cola",
                brand = "Coca-Cola Company",
                imageUrl = "https://example.invalid/coke.png",
            )
        }
        val camera = FakeCameraSource()
        val container = AppContainer(productRepository = repo, cameraSource = camera)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // --- UAT §7 row 1: navigate to Scan to Add ---
        // Empty pantry → "Scan to Add" appears twice (ScanButtonsRow + EmptyState),
        // both wired to the same nav target. Tap the first match.
        rule.onAllNodesWithText("Scan to Add")[0].performClick()

        // --- UAT §7 row 2: green top bar with "Scan to Add" title ---
        // Top app bar title is the only on-screen text "Scan to Add" once on the
        // scan screen, so `onNodeWithText(...).assertIsDisplayed()` is unambiguous.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Scan to Add").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Scan to Add").assertIsDisplayed()

        // --- Fire synthetic barcode (replaces "point at barcode" UAT §7 row 3) ---
        camera.emit(barcode)

        // --- UAT §7 row 4: sheet shows name + brand + stepper=1 + Confirm + Cancel ---
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Coca-Cola").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Coca-Cola").assertIsDisplayed()
        rule.onNodeWithText("Coca-Cola Company").assertIsDisplayed()
        // Stepper at 1 is the initial pendingQuantity per ScanViewModel.resolveBarcode.
        rule.onNodeWithText("1").assertIsDisplayed()
        rule.onNodeWithText("Confirm Add").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        // The image content description is rendered by AsyncImage when imageUrl
        // is non-null — pins the image SLOT exists (the bytes are #1's job).
        rule.onNodeWithContentDescription("Product photo").assertIsDisplayed()

        // --- UAT §7 row 5: increase to 3, Confirm ---
        rule.onNodeWithContentDescription("Increment quantity").performClick()
        rule.onNodeWithContentDescription("Increment quantity").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("3").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Confirm Add").performClick()

        // --- UAT §7 row 6: sheet dismisses (phase → Idle), Coca-Cola text
        //     no longer on the scan screen. The camera "resumes" in the
        //     sense that Phase.Idle is the camera-listening state. ---
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Confirm Add").fetchSemanticsNodes().isEmpty()
        }

        // --- Repo side-effect: addNew called with the OFF-resolved name/brand
        //     and the chosen quantity. Hard assertion that the production
        //     applyDelta vs addNew branching used the FromOff arm. ---
        assertEquals(1, repo.addedProducts.size)
        val call = repo.addedProducts[0]
        assertEquals("Coca-Cola", call.name)
        assertEquals("Coca-Cola Company", call.brand)
        assertEquals(barcode, call.barcode)
        assertEquals("https://example.invalid/coke.png", call.imageUrl)
        assertEquals(QUANTITY_THREE, call.initialQuantity)

        // --- UAT §7 row 7: Back → Home shows the product with ×3 ---
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Coca-Cola").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Coca-Cola").assertIsDisplayed()
        rule.onNodeWithText("×$QUANTITY_THREE").assertIsDisplayed()
        // Sanity: the lookup was invoked exactly once for this barcode
        // (de-dup didn't fail and we didn't accidentally re-resolve).
        assertNotNull(repo.lookupCalls.firstOrNull { it == barcode })
        assertEquals(1, repo.lookupCalls.count { it == barcode })
    }

    private companion object {
        // Compose UI tests on emulators occasionally see >1s waits during cold
        // recomposition; 5s is the timeout used by HappyPathUatTest for the
        // same reason. Below 5s the test flakes on slow CI emulators.
        private const val TIMEOUT_MS = 5_000L
        private const val QUANTITY_THREE = 3
    }
}
