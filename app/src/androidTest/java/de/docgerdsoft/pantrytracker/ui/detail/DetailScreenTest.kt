package de.docgerdsoft.pantrytracker.ui.detail

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test

class DetailScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val now = Clock.System.now()

    @Test
    fun rendersProductName_brand_quantity_andLastUpdatedLabel() {
        val product = Product(
            id = 1L,
            barcode = "111",
            name = "Coke",
            brand = "Coca-Cola",
            quantity = 3,
            createdAt = now,
            updatedAt = now,
        )
        val repo = FakeRepo(initial = product)
        val vm = DetailViewModel(repo, productId = 1L)

        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    DetailScreen(viewModel = vm, onNavigateBack = {})
                }
            }
        }

        composeRule.onNodeWithText("Coke").assertIsDisplayed()
        composeRule.onNodeWithText("Brand: Coca-Cola").assertIsDisplayed()
        composeRule.onNodeWithText("Barcode: 111").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        // "Last updated just now" — substring match to avoid timing sensitivity.
        composeRule.onNodeWithText("Last updated", substring = true).assertIsDisplayed()
    }

    @Test
    fun deleteIconClick_opensConfirmDialog() {
        val product = Product(
            id = 1L,
            barcode = "111",
            name = "Coke",
            quantity = 3,
            createdAt = now,
            updatedAt = now,
        )
        val repo = FakeRepo(initial = product)
        val vm = DetailViewModel(repo, productId = 1L)

        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    DetailScreen(viewModel = vm, onNavigateBack = {})
                }
            }
        }

        composeRule.onNodeWithContentDescription("Delete product").performClick()
        composeRule.onNodeWithText("Delete this product?").assertIsDisplayed()
    }

    @Test
    fun plusStepper_callsApplyDeltaPlusOne() {
        val product = Product(
            id = 7L,
            barcode = "222",
            name = "Milk",
            quantity = 2,
            createdAt = now,
            updatedAt = now,
        )
        val repo = FakeRepo(initial = product)
        val vm = DetailViewModel(repo, productId = 7L)

        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    DetailScreen(viewModel = vm, onNavigateBack = {})
                }
            }
        }

        composeRule.onNodeWithContentDescription("Increase quantity").performClick()
        composeRule.waitForIdle()
        // FakeRepo recorded the call.
        assert(repo.deltaCalls == listOf(7L to +1)) {
            "expected [(7, +1)], got ${repo.deltaCalls}"
        }
    }

    private class FakeRepo(initial: Product? = null) : ProductRepository {
        private val flow = MutableStateFlow(initial)
        val renameCalls = mutableListOf<Pair<Long, String>>()
        val deltaCalls = mutableListOf<Pair<Long, Int>>()
        val deleteCalls = mutableListOf<Long>()

        override fun observeById(id: Long): Flow<Product?> = flow.asStateFlow()
        override fun observeProducts(): Flow<List<Product>> = MutableStateFlow(emptyList())
        override fun search(query: String): Flow<List<Product>> = MutableStateFlow(emptyList())
        override suspend fun findById(id: Long): Product? = flow.value
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) {
            deltaCalls += (productId to delta)
            // Reflect the change in the flow so the UI updates.
            flow.value?.let { p ->
                flow.value = p.copy(quantity = (p.quantity + delta).coerceAtLeast(0))
            }
        }
        override suspend fun rename(productId: Long, newName: String) {
            renameCalls += (productId to newName)
        }
        override suspend fun delete(productId: Long) {
            deleteCalls += productId
            flow.value = null
        }
    }
}
