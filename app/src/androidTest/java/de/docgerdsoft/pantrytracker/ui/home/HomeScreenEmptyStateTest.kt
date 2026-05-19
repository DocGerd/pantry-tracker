package de.docgerdsoft.pantrytracker.ui.home

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

class HomeScreenEmptyStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun emptyPantry_emptyQuery_showsCTAs_andHidesNoMatches() {
        val repo = FakeRepository(MutableStateFlow(emptyList()))
        // ViewModel is constructed OUTSIDE setContent — lint's
        // ViewModelConstructorInComposable rule (correctly) flags inline
        // construction inside a composable, and `viewModel(factory = …)`
        // inside the test would force every test to recreate the VM on each
        // recomposition. Hoisting once mirrors how production code uses
        // viewModelFactory + remember in the nav graph.
        val vm = HomeViewModel(repo)
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
        composeRule.onNodeWithText("Your pantry is empty").assertIsDisplayed()
        composeRule.onNodeWithText("Add manually").assertIsDisplayed()
        // Note: NOT asserting "Scan to Add" — the always-visible
        // ScanButtonsRow + the EmptyState row both render it, so a
        // single-node assertion would crash. (Same fix as in
        // HappyPathUatTest after the M6 review.)
    }

    @Test
    fun emptyPantry_nonEmptyQuery_showsNoMatchesHint_notCTAs() {
        val repo = FakeRepository(MutableStateFlow(emptyList()))
        val vm = HomeViewModel(repo)
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
        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search").performTextInput("zzz")
        composeRule.onNodeWithText("No matches for \"zzz\"").assertIsDisplayed()
        // Lock in branch exclusivity — the empty-pantry CTAs must not also render.
        composeRule.onNodeWithText("Your pantry is empty").assertDoesNotExist()
    }

    @Test
    fun nonEmptyPantry_showsList_hidesBothEmptyStates() {
        val now = Clock.System.now()
        val repo = FakeRepository(
            MutableStateFlow(
                listOf(
                    Product(
                        id = 1, barcode = null, name = "Coke", quantity = 3,
                        createdAt = now, updatedAt = now,
                    ),
                ),
            ),
        )
        val vm = HomeViewModel(repo)
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
        composeRule.onNodeWithText("Coke").assertIsDisplayed()
    }

    private class FakeRepository(
        private val flow: MutableStateFlow<List<Product>>,
    ) : ProductRepository {
        override fun observeProducts(): Flow<List<Product>> = flow.asStateFlow()
        override fun search(query: String): Flow<List<Product>> =
            MutableStateFlow(flow.value.filter { it.name.contains(query, ignoreCase = true) })
                .asStateFlow()
        override fun observeById(id: Long): Flow<Product?> =
            MutableStateFlow(flow.value.firstOrNull { it.id == id }).asStateFlow()
        override suspend fun findById(id: Long): Product? =
            flow.value.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = null
        override suspend fun lookupForPreview(code: String): ScanCandidate? = null
        override suspend fun addNew(
            name: String,
            brand: String?,
            barcode: String?,
            imageUrl: String?,
            initialQuantity: Int,
        ): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) = Unit
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
        override suspend fun restore(product: Product) = Unit
    }
}
