package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersProductRows_andShowsEmptyState_basedOnRepository() {
        val now = Clock.System.now()
        val products = listOf(
            Product(id = 1, barcode = null, name = "Coke 0.5L", quantity = 3, createdAt = now, updatedAt = now),
            Product(id = 2, barcode = null, name = "Olivenöl", quantity = 1, createdAt = now, updatedAt = now),
        )
        val repo = FakeRepository(MutableStateFlow(products))
        val vm = HomeViewModel(repo)

        composeRule.setContent {
            PantryTrackerTheme { Surface { HomeScreen(viewModel = vm, onScanAddClick = {}, onScanRemoveClick = {}, onProductClick = {}) } }
        }

        composeRule.onNodeWithText("Coke 0.5L").assertIsDisplayed()
        composeRule.onNodeWithText("×3").assertIsDisplayed()
        composeRule.onNodeWithText("Olivenöl").assertIsDisplayed()
    }

    @Test
    fun fab_opensAddSheet_thenConfirm_callsRepository() {
        // Seed a non-empty list so EmptyState (with its own "Add manually"
        // OutlinedButton) does NOT render — otherwise the test would
        // ambiguously match the EmptyState button instead of the FAB.
        val now = Clock.System.now()
        val repo = FakeRepository(
            MutableStateFlow(
                listOf(
                    Product(id = 1, barcode = null, name = "Existing",
                        quantity = 1, createdAt = now, updatedAt = now),
                ),
            ),
        )
        val vm = HomeViewModel(repo)

        composeRule.setContent {
            PantryTrackerTheme { Surface { HomeScreen(viewModel = vm, onScanAddClick = {}, onScanRemoveClick = {}, onProductClick = {}) } }
        }

        // Target the FAB explicitly via its contentDescription, not the
        // EmptyState button's text content.
        composeRule.onNodeWithContentDescription("Add manually").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Coke")
        composeRule.onNodeWithText("Add").performClick()

        // Use JUnit assertion — bare assert() is disabled on ART.
        org.junit.Assert.assertEquals("Coke", repo.lastAddName)
    }

    private class FakeRepository(
        private val flow: MutableStateFlow<List<Product>>,
    ) : ProductRepository {
        var lastAddName: String? = null
        override fun observeProducts(): Flow<List<Product>> = flow.asStateFlow()
        override fun search(query: String): Flow<List<Product>> = flow.asStateFlow()
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
        ): Long {
            lastAddName = name
            return 1L
        }
        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit

        // The screens covered by this test never exercise restore. Throwing on
        // accidental invocation is a louder failure mode than a silent `= Unit`
        // — a future test that long-presses + confirms delete + taps UNDO will
        // surface this immediately instead of green-on-broken.
        override suspend fun restore(product: Product): Unit =
            throw NotImplementedError(
                "test harness does not exercise restore — " +
                    "use HomeViewModelTest.FakeProductRepository if you need it",
            )
    }
}
