package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * Instrumented Compose UI test for SS9 Search in the UAT checklist.
 *
 * Covers keystroke-driven filter behaviour on a pre-populated
 * [HomeScreen]. The four test methods map 1-to-1 to the four UAT rows
 * in SS9 that have no camera or network dependency:
 *
 *  1. [query_matchesSubset_filtersListToMatches] -- type "Test" -- only
 *     the matching row is visible
 *  2. [query_matchesNothing_showsNoMatchesHint] -- type "zzz" -- the
 *     no-matches hint appears
 *  3. [query_matchesNothing_doesNotShowEmptyPantryCtas] -- type "zzz"
 *     -- empty-pantry CTAs are absent (state-exclusion contract)
 *  4. [query_cleared_restoresFullList] -- type "zzz", then clear --
 *     all three seeded rows are visible again
 *
 * Uses [FakeProductRepository] (shared fixture from SR-75 / #88) so no
 * Room, network, or DI wiring is needed. The VM is constructed outside
 * [setContent] to avoid the ViewModelConstructorInComposable lint finding
 * (mirrors [HomeScreenEmptyStateTest] rationale).
 *
 * [automated by SR-76]
 */
class SearchUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Builds and returns a rule-scoped [HomeViewModel] backed by a
     * [FakeProductRepository] pre-populated with:
     *   - "Test Product" (quantity 2)
     *   - "Cookies" (quantity 5)
     *   - "Mineral Water" (quantity 3)
     *
     * These three entries deliberately include one that starts with "Test"
     * (to exercise the matching branch) and two that do not.
     */
    private fun buildVm(): Pair<HomeViewModel, FakeProductRepository> {
        val now = Clock.System.now()
        val repo = FakeProductRepository()
        repo.seedAll(
            listOf(
                Product(
                    id = 1, barcode = null, name = "Test Product", quantity = 2,
                    createdAt = now, updatedAt = now,
                ),
                Product(
                    id = 2, barcode = null, name = "Cookies", quantity = 5,
                    createdAt = now, updatedAt = now,
                ),
                Product(
                    id = 3, barcode = null, name = "Mineral Water", quantity = 3,
                    createdAt = now, updatedAt = now,
                ),
            ),
        )
        return HomeViewModel(repo) to repo
    }

    private fun mountScreen(vm: HomeViewModel) {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    HomeScreen(
                        viewModel = vm,
                        onScanAddClick = {},
                        onScanRemoveClick = {},
                        onProductClick = {},
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // SS9 UAT row 1: Type "Test" -- list filters to only matching rows
    // -----------------------------------------------------------------------

    @Test
    fun query_matchesSubset_filtersListToMatches() {
        val (vm, _) = buildVm()
        mountScreen(vm)

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search").performTextInput("Test")

        // "Test Product" row must be visible.
        composeRule.onNodeWithText("Test Product").assertIsDisplayed()

        // The non-matching rows must not exist in the composition.
        composeRule.onNodeWithText("Cookies").assertDoesNotExist()
        composeRule.onNodeWithText("Mineral Water").assertDoesNotExist()
    }

    // -----------------------------------------------------------------------
    // SS9 UAT row 2: Type "zzz" -- "No matches for \"zzz\"" hint appears
    // -----------------------------------------------------------------------

    @Test
    fun query_matchesNothing_showsNoMatchesHint() {
        val (vm, _) = buildVm()
        mountScreen(vm)

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search").performTextInput("zzz")

        // NoMatchesHint renders: No matches for "zzz"
        // The straight double-quotes (U+0022) are Kotlin-escaped as \".
        composeRule.onNodeWithText("No matches for \"zzz\"").assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // SS9 UAT row 3: Empty-pantry CTAs do NOT appear during a non-matching search
    // -----------------------------------------------------------------------

    @Test
    fun query_matchesNothing_doesNotShowEmptyPantryCtas() {
        val (vm, _) = buildVm()
        mountScreen(vm)

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search").performTextInput("zzz")

        // State-exclusion contract: the no-matches branch and the
        // genuine-empty-pantry branch are mutually exclusive in HomeScreen.
        // assertDoesNotExist() is a member on SemanticsNodeInteraction --
        // no import needed (project CLAUDE.md pitfall note).
        composeRule.onNodeWithText("Your pantry is empty").assertDoesNotExist()
        composeRule.onNodeWithText("Add manually").assertDoesNotExist()

        // "Scan to Add" text is also present in the always-visible
        // ScanButtonsRow so a single-node assertion would be ambiguous.
        // The "Your pantry is empty" headline gate above is the
        // deterministic proxy for the entire empty-pantry CTA section
        // (same rationale as HomeScreenEmptyStateTest).
    }

    // -----------------------------------------------------------------------
    // SS9 UAT row 4: Clear the search -- list returns to full
    // -----------------------------------------------------------------------

    @Test
    fun query_cleared_restoresFullList() {
        val (vm, _) = buildVm()
        mountScreen(vm)

        // Type a non-matching query to enter the no-matches branch.
        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search").performTextInput("zzz")

        // Verify we are actually in the no-matches state before clearing.
        composeRule.onNodeWithText("No matches for \"zzz\"").assertIsDisplayed()

        // Clear the search field -- performTextReplacement("") empties the
        // field, which drives HomeViewModel.setQuery("") and switches back to
        // repository.observeProducts() (the blank-query path in the VM).
        composeRule.onNodeWithText("zzz").performTextReplacement("")

        // All three seeded rows must be visible again.
        composeRule.onNodeWithText("Test Product").assertIsDisplayed()
        composeRule.onNodeWithText("Cookies").assertIsDisplayed()
        composeRule.onNodeWithText("Mineral Water").assertIsDisplayed()
    }
}
