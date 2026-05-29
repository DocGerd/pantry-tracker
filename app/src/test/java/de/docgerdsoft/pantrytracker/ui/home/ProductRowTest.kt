package de.docgerdsoft.pantrytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.docgerdsoft.pantrytracker.data.local.Product
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductRowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun product(name: String, brand: String?, quantity: Int = 3) = Product(
        barcode = null,
        name = name,
        brand = brand,
        imageUrl = null,
        quantity = quantity,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `shows brand as a secondary line when present`() {
        composeRule.setContent {
            ProductRow(product("Menu Paste", "Sheba"), onClick = {}, onLongPress = {})
        }
        composeRule.onNodeWithText("Menu Paste").assertIsDisplayed()
        composeRule.onNodeWithText("Sheba").assertIsDisplayed()
    }

    @Test
    fun `renders name only when brand is null - no stray separator`() {
        composeRule.setContent {
            ProductRow(product("Salt", null), onClick = {}, onLongPress = {})
        }
        composeRule.onNodeWithText("Salt").assertIsDisplayed()
        composeRule.onNodeWithText("·").assertDoesNotExist()
    }

    @Test
    fun `renders name only when brand is blank`() {
        composeRule.setContent {
            ProductRow(product("Salt", "   "), onClick = {}, onLongPress = {})
        }
        composeRule.onNodeWithText("Salt").assertIsDisplayed()
    }
}
