package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * Instrumented coverage for HomeScreen.SnackbarEventCollector — the delete/undo
 * snackbar render path (#219). Drives the real long-press → confirm → snackbar →
 * UNDO flow on the emulator so the three SnackbarEvent variants (Deleted,
 * DeleteFailed, RestoreFailed) are exercised. Runs on the emulator (NOT
 * Robolectric) so it credits the on-the-fly JaCoCo report (see CLAUDE.md
 * "Robolectric tests add 0% to the on-the-fly JaCoCo report").
 */
class HomeSnackbarEventTest {

    @get:Rule val composeRule = createComposeRule()

    private val now = Clock.System.now()
    private fun butter() =
        Product(id = 1, barcode = null, name = "Butter", quantity = 1, createdAt = now, updatedAt = now)

    private fun setContent(repo: ProductRepository) {
        val vm = HomeViewModel(repo)
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    HomeScreen(
                        viewModel = vm,
                        onScanAddClick = {}, onScanRemoveClick = {},
                        onProductClick = {}, onBuyListClick = {},
                    )
                }
            }
        }
    }

    private fun deleteButter() {
        composeRule.onNodeWithText("Butter").performTouchInput { longClick() }
        // Confirm dialog: title "Delete Butter?", confirm button "Delete".
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Delete").performClick()
    }

    @Test
    fun delete_showsDeletedSnackbar_andUndoRestoresRow() {
        val repo = MutableFakeRepository(MutableStateFlow(listOf(butter())))
        setContent(repo)

        deleteButter()

        // Deleted snackbar with UNDO.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Deleted Butter").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Deleted Butter").assertIsDisplayed()

        // Tap UNDO → undoDelete → restore re-adds the row.
        composeRule.onNodeWithText("UNDO").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Butter").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Butter").assertIsDisplayed()
    }

    @Test
    fun deleteFailure_showsCouldntDeleteSnackbar() {
        val repo = MutableFakeRepository(MutableStateFlow(listOf(butter())))
            .apply { deleteShouldThrow = RuntimeException("disk full") }
        setContent(repo)

        deleteButter()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Couldn't delete: Butter").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Couldn't delete: Butter").assertIsDisplayed()
    }

    @Test
    fun restoreFailure_showsCouldntRestoreSnackbar() {
        val repo = MutableFakeRepository(MutableStateFlow(listOf(butter())))
            .apply { restoreShouldThrow = RuntimeException("locked") }
        setContent(repo)

        deleteButter()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Deleted Butter").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("UNDO").performClick()

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Couldn't restore: Butter").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Couldn't restore: Butter").assertIsDisplayed()
    }

    /** Stateful fake: delete removes from the flow, restore re-adds; optional
     *  throw knobs drive the DeleteFailed / RestoreFailed paths. */
    private class MutableFakeRepository(
        private val flow: MutableStateFlow<List<Product>>,
    ) : ProductRepository {
        var deleteShouldThrow: Throwable? = null
        var restoreShouldThrow: Throwable? = null

        override fun observeProducts(): Flow<List<Product>> = flow.asStateFlow()
        override fun search(query: String): Flow<List<Product>> = flow.asStateFlow()
        override fun observeBuyingList(): Flow<List<Product>> = MutableStateFlow(emptyList())
        override suspend fun setRestockSettings(productId: Long, lowLimit: Int?, defaultBuyAmount: Int) = Unit
        override suspend fun findById(id: Long): Product? = flow.value.firstOrNull { it.id == id }
        override fun observeById(id: Long): Flow<Product?> =
            MutableStateFlow(flow.value.firstOrNull { it.id == id }).asStateFlow()
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long = 1L
        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit

        override suspend fun delete(productId: Long) {
            deleteShouldThrow?.let { throw it }
            flow.value = flow.value.filterNot { it.id == productId }
        }

        override suspend fun restore(product: Product) {
            restoreShouldThrow?.let { throw it }
            flow.value = (flow.value + product).sortedBy { it.id }
        }
    }

    private companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
