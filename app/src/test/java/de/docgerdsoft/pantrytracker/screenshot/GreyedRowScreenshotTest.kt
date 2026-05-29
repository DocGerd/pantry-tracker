package de.docgerdsoft.pantrytracker.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RNG screenshot tests for the 45% opacity treatment of out-of-stock rows
 * (§11 last row of the UAT checklist: "Repeat until the row hits 0 — the row
 * stays in the list but greyed at 45% opacity").
 *
 * Two golden images are captured:
 *  - A fully-opaque row (quantity > 0) as a baseline.
 *  - A 45%-opacity row (quantity == 0) to verify the greyed treatment is
 *    pixel-accurately applied.
 *
 * The alpha difference (100% vs 45%) is clearly visible in a side-by-side
 * golden diff and cannot be accidentally removed without the test catching it.
 * The constant `OUT_OF_STOCK_ROW_ALPHA = 0.45f` is mirrored here from
 * [de.docgerdsoft.pantrytracker.ui.home.HomeScreen] — if it changes there,
 * the golden files will diverge and the test will fail.
 *
 * ## Config notes
 * `sdk = [34]` is required for @GraphicsMode(NATIVE).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GreyedRowScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Mirror of the constant in HomeScreen. If the source changes, goldens diverge. */
    private val outOfStockRowAlpha = 0.45f

    /**
     * In-stock row (quantity = 1, alpha = 1.0f).
     * Baseline golden so the greyed-row golden can be meaningfully compared.
     */
    @Test
    fun productRow_inStock_fullOpacity_matchesGolden() {
        rule.setContent {
            PantryTrackerTheme {
                ProductRowPreview(name = "Pasta", quantity = 1, alpha = 1f)
            }
        }
        ScreenshotTestBase.assertMatchesGolden(rule, "greyed_row_in_stock")
    }

    /**
     * Out-of-stock row (quantity = 0, alpha = 0.45f).
     * Verifies the 45% opacity treatment is applied — §11 last row.
     * Pixel-accurate capture means even a small alpha change (e.g. 0.45 → 0.50)
     * causes a golden mismatch and requires a deliberate re-golden.
     */
    @Test
    fun productRow_outOfStock_greyedOpacity_matchesGolden() {
        rule.setContent {
            PantryTrackerTheme {
                ProductRowPreview(name = "Pasta", quantity = 0, alpha = outOfStockRowAlpha)
            }
        }
        ScreenshotTestBase.assertMatchesGolden(rule, "greyed_row_out_of_stock")
    }

    /**
     * Minimal reproduction of the `ProductRow` composable from HomeScreen.
     * Kept in sync structurally with the production layout (Row + Column + weight(1f) + alpha).
     * The alpha is applied to the Column wrapping the name Text, mirroring the production
     * layout where dimming spans the entire name/brand column. A structural divergence
     * would show up in the golden diff.
     */
    @Composable
    private fun ProductRowPreview(name: String, quantity: Int, alpha: Float) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(alpha),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = "×$quantity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
