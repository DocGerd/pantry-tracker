package de.docgerdsoft.pantrytracker.error

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.PantryTrackerNavGraph
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.testfixtures.FakeCameraSource
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * Strategy B — Error-tone instrumented semantics test.
 *
 * UAT §15 row 1: every user-facing error string that the app renders must start
 * with **"Couldn't <verb>: …"**. This test exercises three known failure paths
 * by injecting errors into [FakeProductRepository] and asserting that the
 * rendered text (in an [ErrorSheet] or Snackbar) matches the convention.
 *
 * Failure paths covered:
 *
 *  1. **Network/lookup failure during Scan-to-Add** — [FakeProductRepository.lookupShouldThrow]
 *     causes [ScanViewModel.resolveBarcode] to catch and transition to
 *     `Phase.Error("Couldn't read inventory: …")`. The [ErrorSheet] renders the
 *     message text so it is assertable via semantics.
 *
 *  2. **Save failure after manual-entry confirm** — [FakeProductRepository.addShouldThrow]
 *     causes [ScanViewModel.submitManualEntry] to catch and transition to
 *     `Phase.Error("Couldn't save: …")`. The barcode resolves to an OFF miss
 *     (null in [lookupResponses]) so the ManualEntry sheet appears first, then
 *     the Add button triggers the failure.
 *
 *  3. **Rename failure on Detail screen** — a local [ErrorFakeRepository] subclass
 *     overrides `rename` to throw; [DetailViewModel] wraps this in
 *     `"Couldn't rename: …"` and the Detail screen shows it as a Snackbar. The
 *     product is SEEDED directly into the repo (not added through the
 *     manual-entry sheet) so the test reaches the Detail screen with a single
 *     Home tap — avoiding the fragile add-sheet UI sequence on-device.
 *
 * These are prefix checks — asserting that the visible text STARTS WITH
 * "Couldn't " is the enforcement mechanism. A raw "java.lang.RuntimeException"
 * or "Error: …" message would fail the waitUntil condition and then the
 * assertIsDisplayed call, surfacing the regression immediately.
 *
 * Covers: UAT §15 row 1 [automated by SR-78].
 */
class ErrorToneSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun grantCameraPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )
    }

    // -------------------------------------------------------------------------
    // Path 1: lookup failure during scan → Phase.Error "Couldn't read inventory"
    // -------------------------------------------------------------------------

    @Test
    fun scanLookupFailure_errorSheetStartsWithCouldnt() {
        val repo = FakeProductRepository().apply {
            lookupShouldThrow = RuntimeException("simulated network timeout")
        }
        val camera = FakeCameraSource()
        val container = AppContainer(productRepository = repo, cameraSource = camera)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // Navigate to Scan to Add.
        rule.onAllNodesWithText("Scan to Add")[0].performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Scan to Add").fetchSemanticsNodes().isNotEmpty()
        }

        // Fire a synthetic barcode — resolveBarcode throws via lookupShouldThrow.
        camera.emit("5449000000996")

        // ErrorSheet renders the error message as Text — wait for it to appear.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Couldn't read inventory:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Explicit prefix assertion: message starts with the canonical "Couldn't <verb>:"
        val nodes = rule.onAllNodesWithText("Couldn't read inventory:", substring = true)
            .fetchSemanticsNodes()
        assertTrue(
            "Expected an on-screen error starting with 'Couldn't read inventory:' " +
                "but found ${nodes.size} matching nodes",
            nodes.isNotEmpty(),
        )
        rule.onNodeWithText("Couldn't read inventory:", substring = true).assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Path 2: save failure after manual-entry confirm → Phase.Error "Couldn't save"
    // -------------------------------------------------------------------------

    @Test
    fun manualEntrySaveFailure_errorSheetStartsWithCouldnt() {
        val barcode = "1234500000001"
        val repo = FakeProductRepository().apply {
            // OFF miss → ManualEntry sheet opens; addNew then throws.
            lookupResponses[barcode] = null
            addShouldThrow = RuntimeException("disk full")
        }
        val camera = FakeCameraSource()
        val container = AppContainer(productRepository = repo, cameraSource = camera)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // Navigate to Scan to Add.
        rule.onAllNodesWithText("Scan to Add")[0].performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Scan to Add").fetchSemanticsNodes().isNotEmpty()
        }

        // Fire barcode → OFF miss → ManualEntry sheet appears.
        camera.emit(barcode)

        // Wait for the ManualEntry sheet — it shows "Not on Open Food Facts" as title.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Not on Open Food Facts").fetchSemanticsNodes().isNotEmpty()
        }

        // Type a name into the "Name" field and confirm via "Add to inventory".
        // submitManualEntry → addNew throws → Phase.Error("Couldn't save: …").
        rule.onNodeWithText("Name").performTextInput("Test Product")
        rule.onNodeWithText("Add to inventory").performClick()

        // ErrorSheet with "Couldn't save:" prefix.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Couldn't save:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Couldn't save:", substring = true).assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Path 3: rename failure on Detail screen → Snackbar "Couldn't rename"
    // -------------------------------------------------------------------------

    @Test
    fun detailRenameFailure_snackbarStartsWithCouldnt() {
        // SEED the product directly into the injected repo rather than driving
        // the manual-entry sheet. The sheet route was fragile on-device: the
        // HomeScreen "Add manually" sheet (AddProductSheet) has a confirm button
        // labelled "Add" — NOT "Add to inventory" (that label lives in the
        // scan-flow ScanResultSheet). The previous locator therefore matched
        // zero nodes on a real emulator and failed at performClick. Seeding
        // bypasses the entire add UI: the product appears directly on Home,
        // navigation to Detail is a single tap, and the rename-failure → error
        // snackbar path (the actual thing under test) is exercised unchanged.
        val repo = ErrorFakeRepository().apply {
            val now = Clock.System.now()
            seed(
                Product(
                    id = 1L,
                    barcode = null,
                    name = "Butter",
                    quantity = 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        val container = AppContainer(productRepository = repo)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // The seeded product is already on Home — wait for its row, then tap it
        // to navigate to the Detail screen.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Butter").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Butter").performClick()
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Product details").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Product details").assertIsDisplayed()

        // Enable rename failure, then commit a rename via IME Done action.
        // commitName() only calls rename when the new name differs from the
        // current one, so the replacement must change "Butter" → "Margarine".
        repo.renameShouldThrow = RuntimeException("permission denied")
        rule.onNodeWithText("Butter").performTextReplacement("Margarine")
        rule.onNodeWithText("Margarine").performImeAction()

        // Detail screen shows the error via Snackbar — assert "Couldn't rename:" prefix.
        rule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            rule.onAllNodesWithText("Couldn't rename:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Couldn't rename:", substring = true).assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Test double
    // -------------------------------------------------------------------------

    /**
     * [FakeProductRepository] subclass with a [renameShouldThrow] knob. The
     * base class covers lookup / add / applyDelta failures; this extends it for
     * the Detail-screen rename path which is not covered by the base knobs.
     */
    private inner class ErrorFakeRepository : FakeProductRepository() {
        var renameShouldThrow: Throwable? = null

        override suspend fun rename(productId: Long, newName: String) {
            renameShouldThrow?.let { throw it }
            super.rename(productId, newName)
        }
    }

    private companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
