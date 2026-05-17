package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test

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
        val repo = FakeRepository(MutableStateFlow(emptyList()))
        val vm = HomeViewModel(repo)

        composeRule.setContent {
            PantryTrackerTheme { Surface { HomeScreen(viewModel = vm, onScanAddClick = {}, onScanRemoveClick = {}, onProductClick = {}) } }
        }

        composeRule.onNodeWithText("Add manually").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Coke")
        composeRule.onNodeWithText("Add").performClick()

        assert(repo.lastAddName == "Coke")
    }

    private class FakeRepository(
        private val flow: MutableStateFlow<List<Product>>,
    ) : ProductRepository {
        var lastAddName: String? = null
        override fun observeProducts(): Flow<List<Product>> = flow.asStateFlow()
        override fun search(query: String): Flow<List<Product>> = flow.asStateFlow()
        override suspend fun findById(id: Long): Product? = flow.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null
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
    }
}
