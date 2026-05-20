package de.docgerdsoft.pantrytracker.uat

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import de.docgerdsoft.pantrytracker.PantryTrackerNavGraph
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end UAT happy path. Drives the **real** [PantryTrackerNavGraph] with
 * an in-memory [ProductRepository] injected through [AppContainer], so the
 * test exercises navigation, ViewModel construction via the nav-graph's
 * factories, and Compose recomposition — i.e. the same code paths the user
 * hits on a real device.
 *
 * Scope: the camera/scan flow is excluded because (a) it requires a real
 * back camera + real barcode hardware, and (b) ML Kit's decode pipeline
 * can't be deterministically mocked from a Compose test. The equivalent
 * user-visible state changes (a product appears in the list / the row is
 * removed / a quantity reaches 0) are exercised here via the manual-entry
 * path and the detail-screen stepper + delete. Real scanning is covered by
 * the manual UAT checklist at `docs/uat/v1-uat-checklist.md`.
 *
 * Flow:
 *   1. Launch app with empty pantry → EmptyState renders with both CTAs
 *   2. Tap "Add manually" → AddProductSheet → type name → tap Add
 *   3. Product row visible in Home with ×1
 *   4. Tap row → Detail screen
 *   5. Rename via IME action → tap back arrow → Home shows renamed product
 *   6. Tap renamed row → Detail → tap Delete → confirm
 *   7. Auto-pops back to Home → EmptyState renders again
 */
class HappyPathUatTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun fullV1UserFlow_addRenameRemove() {
        val repo = FakeProductRepository()
        val container = AppContainer(repo)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // --- 1. Empty pantry baseline ---
        // We don't assert "Scan to Add" here — it appears twice when the pantry
        // is empty (once in the always-rendered ScanButtonsRow, once in the
        // EmptyState row), which would make `assertIsDisplayed()` (single-node)
        // crash. "Your pantry is empty" is unambiguous and proves the
        // empty-state branch is active.
        rule.onNodeWithText("Your pantry is empty").assertIsDisplayed()
        // "Add manually" appears in BOTH the EmptyState OutlinedButton (Text)
        // and the FAB (contentDescription). `onNodeWithText` only matches
        // text/EditableText, not contentDescription — so this finds exactly
        // the EmptyState button. Tapping it routes to the same openAddSheet
        // method the FAB does, i.e. the canonical empty-state entry point.
        rule.onNodeWithText("Add manually").performClick()

        // --- 2. AddProductSheet → type name → Add ---
        rule.onNodeWithText("Name").performTextInput("Test Coke")
        // The sheet has both a "Cancel" and an "Add" button; "Add" is the
        // confirm. Single text match because the sheet is the only thing on
        // screen with that label.
        rule.onNodeWithText("Add").performClick()

        // --- 3. Row appears in Home ---
        rule.onNodeWithText("Test Coke").assertIsDisplayed()
        rule.onNodeWithText("×1").assertIsDisplayed()
        // The empty-state copy must no longer be on screen — branch exclusivity.
        rule.onNodeWithText("Your pantry is empty").assertDoesNotExist()

        // --- 4. Tap row → Detail screen ---
        rule.onNodeWithText("Test Coke").performClick()
        rule.onNodeWithText("Product details").assertIsDisplayed()

        // --- 5. Rename via IME Done action ---
        // The OutlinedTextField labelled "Name" holds the current name. Replace
        // it and trigger the IME Done action, which calls commitName() → rename
        // via viewModelScope.launch. We waitUntil the StateFlow propagates the
        // new product.name back into the field before navigating away — without
        // this guard, the back-arrow can unmount the screen before the rename
        // coroutine commits (the focus-loss commit at DetailScreen.kt:186 saves
        // us today by accident, which is too fragile).
        rule.onNodeWithText("Test Coke").performTextReplacement("Test Cola")
        rule.onNodeWithText("Test Cola").performImeAction()
        rule.waitUntil(timeoutMillis = 2_000) {
            // After the rename commits, the InMemoryRepo emits a new Product
            // whose name = "Test Cola", which propagates through observeById
            // into DetailUiState.product and re-renders the field with the
            // new product.name (the remember(product.name) re-keys localName).
            // We can't observe the field's value directly via Text matching
            // because the EditableText was already "Test Cola" pre-commit;
            // instead we wait for the underlying state to settle by checking
            // that "Test Coke" is no longer findable anywhere (a robust
            // "rename has been observed by the UI" signal).
            rule.onAllNodesWithText("Test Coke").fetchSemanticsNodes().isEmpty()
        }

        // --- 6. Test the stepper before leaving Detail ---
        // Closes the cheap test-coverage gap noted in PR #26 review (H2):
        // verifies +/- wiring + that quantity survives independently of rename.
        rule.onNodeWithContentDescription("Increase quantity").performClick()
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Decrease quantity").performClick()
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate back to Home via the top-bar back arrow (contentDescription="Back").
        rule.onNodeWithContentDescription("Back").performClick()

        // --- 7. Verify renamed in Home (and quantity preserved) ---
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("Test Cola").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Test Cola").assertIsDisplayed()
        // ×1 must survive the rename round-trip — closes H2 quantity-loss gap.
        rule.onNodeWithText("×1").assertIsDisplayed()
        rule.onNodeWithText("Test Coke").assertDoesNotExist()

        // --- 8. Tap renamed row → Detail → Delete → confirm ---
        rule.onNodeWithText("Test Cola").performClick()
        rule.onNodeWithText("Product details").assertIsDisplayed()
        rule.onNodeWithContentDescription("Delete product").performClick()
        rule.onNodeWithText("Delete this product?").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()

        // --- 9. DetailScreen's LaunchedEffect(shouldNavigateBack) auto-pops to Home ---
        // The chain is: confirmDelete → viewModelScope.launch { repo.delete } →
        // StateFlow emit → DetailVM.shouldNavigateBack=true → LaunchedEffect
        // calls onNavigateBack → NavHost recomposes to Home → Home's
        // WhileSubscribed re-subscribes to observeProducts → empty list emit.
        // The compose-test idle covers Main-dispatched work but the
        // WhileSubscribed cold-start chain can race on slow emulators —
        // waitUntil hardens against CI flake.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Your pantry is empty").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Your pantry is empty").assertIsDisplayed()
        rule.onNodeWithText("Test Cola").assertDoesNotExist()
    }

    @Test
    fun delete_thenUndo_restoresItem() {
        val repo = FakeProductRepository()
        val container = AppContainer(repo)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        // Add a product manually so we have a row to delete-then-undo.
        rule.onNodeWithText("Add manually").performClick()
        rule.onNodeWithText("Name").performTextInput("Soap")
        // Two "Add" nodes exist in the empty-state ScanButtonsRow + sheet — the
        // sheet's confirm is index 0 once the AddProductSheet is on top.
        rule.onAllNodesWithText("Add")[0].performClick()
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("Soap").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Soap").assertIsDisplayed()

        // Long-press the row → DeleteConfirmDialog (project pattern).
        rule.onNodeWithText("Soap").performTouchInput { longClick() }
        rule.onNodeWithText("Delete").performClick()

        // Snackbar fires after confirmDelete; assert the new copy + tap UNDO.
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("UNDO").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("UNDO").performClick()

        // Item is back in the list — restore preserved the captured Product.
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("Soap").fetchSemanticsNodes().isNotEmpty()
        }
        // Use assertCountEquals(1) rather than onNodeWithText("Soap"): a
        // lingering snackbar that still carries the product name in its
        // message ("Deleted Soap") would let a single-node assertion match
        // the wrong semantics node and false-pass even if the row had not
        // come back. Asserting exactly one node pins both "the row is back"
        // AND "no stale snackbar is also showing 'Soap'".
        rule.onAllNodesWithText("Soap").assertCountEquals(1)
        // EmptyState must not re-appear: pins the assertion that undoDelete
        // actually round-tripped through restore(), not just dismissed the
        // snackbar.
        rule.onNodeWithText("Your pantry is empty").assertDoesNotExist()
    }

    @Test
    fun delete_thenSnackbarDismiss_keepsDeleted() {
        val repo = FakeProductRepository()
        val container = AppContainer(repo)

        rule.setContent {
            PantryTrackerTheme {
                Surface { PantryTrackerNavGraph(container = container) }
            }
        }

        rule.onNodeWithText("Add manually").performClick()
        rule.onNodeWithText("Name").performTextInput("Soap")
        rule.onAllNodesWithText("Add")[0].performClick()
        rule.waitUntil(timeoutMillis = 2_000) {
            rule.onAllNodesWithText("Soap").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Soap").performTouchInput { longClick() }
        rule.onNodeWithText("Delete").performClick()

        // Wait past Material's "Short" snackbar duration (~4 s) without
        // tapping UNDO. The collector resumes after dismiss; the deletion
        // stays final because UNDO was never invoked.
        // mainClock controls Compose's virtual frame clock; the collector's
        // showSnackbar suspension is what advancing it actually dismisses.
        rule.mainClock.advanceTimeBy(5_000L)

        // Row is gone (EmptyState re-renders) and the UNDO action is no
        // longer findable on the snackbar.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Your pantry is empty").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Your pantry is empty").assertIsDisplayed()
        rule.onNodeWithText("Soap").assertDoesNotExist()
    }
}
