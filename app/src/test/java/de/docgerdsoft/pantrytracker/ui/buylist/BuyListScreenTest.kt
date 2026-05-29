package de.docgerdsoft.pantrytracker.ui.buylist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuyListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun product(
        id: Long = 1,
        name: String,
        quantity: Int,
        lowLimit: Int?,
        defaultBuyAmount: Int = 1,
    ) = Product(
        id = id,
        barcode = null,
        name = name,
        quantity = quantity,
        lowLimit = lowLimit,
        defaultBuyAmount = defaultBuyAmount,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `shows empty state when nothing is below its limit`() {
        composeRule.setContent {
            PantryTrackerTheme {
                BuyListContent(items = emptyList(), onBought = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Nothing to buy").assertIsDisplayed()
    }

    @Test
    fun `Bought button invokes the callback for that row`() {
        val bought = mutableListOf<Long>()
        val milk = product(name = "Milk", quantity = 0, lowLimit = 2, defaultBuyAmount = 2)
        composeRule.setContent {
            PantryTrackerTheme {
                BuyListContent(items = listOf(milk), onBought = { bought += it.id }, onBack = {})
            }
        }
        composeRule.onNodeWithContentDescription("Bought Milk").performClick()
        assertEquals(listOf(milk.id), bought)
    }

    @Test
    fun `lists the item name and count`() {
        val milk = product(name = "Milk", quantity = 1, lowLimit = 2, defaultBuyAmount = 2)
        composeRule.setContent {
            PantryTrackerTheme {
                BuyListContent(items = listOf(milk), onBought = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Milk").assertIsDisplayed()
        composeRule.onNodeWithText("×1").assertIsDisplayed()
    }
}
