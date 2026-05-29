package de.docgerdsoft.pantrytracker.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * #191 coverage for the restock field-commit logic in [DetailScreen]'s
 * `RestockSettings` composable. Split in two:
 *
 *  - [parseRestockInput] unit tests pin the pure parse rules (blank/unparsable
 *    low-limit ⇒ null clears tracking; buy-amount `toIntOrNull() ?: 1`) without
 *    driving the Compose tree;
 *  - the Robolectric Compose smoke tests pin the commit wiring: a changed value
 *    fires `onSave` on focus-loss / IME-done, a blank low-limit forwards `null`,
 *    and the no-op guard suppresses `onSave` when nothing changed.
 *
 * Robolectric (JVM) so it runs under `:app:test` without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestockSettingsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- pure parse logic -------------------------------------------------

    @Test
    fun parse_numericLowLimit_andBuyAmount() {
        assertEquals(2 to 3, parseRestockInput("2", "3"))
    }

    @Test
    fun parse_blankLowLimit_yieldsNull() {
        assertEquals(null to 5, parseRestockInput("", "5"))
    }

    @Test
    fun parse_unparsableLowLimit_yieldsNull() {
        assertEquals(null to 1, parseRestockInput("abc", "1"))
    }

    @Test
    fun parse_unparsableBuyAmount_coercesToOne() {
        assertEquals(4 to 1, parseRestockInput("4", ""))
    }

    @Test
    fun parse_trimsWhitespace() {
        assertEquals(7 to 2, parseRestockInput("  7 ", " 2 "))
    }

    // --- Compose commit wiring -------------------------------------------

    private fun product(lowLimit: Int? = 2, defaultBuyAmount: Int = 3) = Product(
        id = 1L,
        barcode = null,
        name = "Milk",
        quantity = 1,
        lowLimit = lowLimit,
        defaultBuyAmount = defaultBuyAmount,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `typing a new low limit then losing focus calls onSave with the parsed value`() {
        val saved = mutableListOf<Pair<Int?, Int>>()
        composeRule.setContent {
            PantryTrackerTheme {
                RestockSettings(product = product(lowLimit = 2, defaultBuyAmount = 3)) { limit, buy ->
                    saved += limit to buy
                }
            }
        }
        // Change the low-limit field, then move focus to the buy-amount field
        // (which fires the low-limit field's commit-on-focus-loss).
        composeRule.onNodeWithText("2").performTextReplacement("5")
        composeRule.onNodeWithText("3").performImeAction() // focus the other field, then Done
        composeRule.waitForIdle()
        assertTrue("expected a commit", saved.isNotEmpty())
        assertEquals(5 to 3, saved.last())
    }

    @Test
    fun `clearing the low limit field commits a null limit`() {
        val saved = mutableListOf<Pair<Int?, Int>>()
        composeRule.setContent {
            PantryTrackerTheme {
                RestockSettings(product = product(lowLimit = 2, defaultBuyAmount = 3)) { limit, buy ->
                    saved += limit to buy
                }
            }
        }
        composeRule.onNodeWithText("2").performTextClearance()
        // Force the low-limit field to lose focus by acting on the buy-amount field.
        composeRule.onNodeWithText("3").performImeAction()
        composeRule.waitForIdle()
        assertTrue("expected a commit", saved.isNotEmpty())
        assertNull("blank low limit must clear tracking", saved.last().first)
        assertEquals(3, saved.last().second)
    }

    @Test
    fun `committing unchanged values does not call onSave`() {
        val saved = mutableListOf<Pair<Int?, Int>>()
        composeRule.setContent {
            PantryTrackerTheme {
                RestockSettings(product = product(lowLimit = 2, defaultBuyAmount = 3)) { limit, buy ->
                    saved += limit to buy
                }
            }
        }
        // Trigger a commit without changing anything (IME Done on the buy field).
        composeRule.onNodeWithText("3").performImeAction()
        composeRule.waitForIdle()
        assertTrue("unchanged values must not fire onSave", saved.isEmpty())
    }
}
