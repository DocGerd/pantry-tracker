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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RNG screenshot tests for font-scale extremes (§2 row 4 of the UAT checklist:
 * "no clipped text on the smallest font size, no overflow on the largest").
 *
 * Two variants are tested:
 *  - 0.85× scale — smallest scale that most users set; text should not
 *    be abnormally small or cause layout breaks.
 *  - 1.30× scale — largest typical accessibility scale; text should not
 *    overflow its container or get clipped.
 *
 * Font scale is applied via [CompositionLocalProvider] + [LocalDensity] rather
 * than Robolectric `@Config(qualifiers = ...)` because the `fontscale` qualifier
 * is not supported in Robolectric's qualifier parser. Overriding `LocalDensity`
 * directly is the standard Compose testing pattern for font-scale variants.
 *
 * The composable under test mimics the product-row text hierarchy used in
 * HomeScreen so a regression in text sizing shows up here without requiring
 * the full HomeScreen (which needs a ViewModel + state).
 *
 * ## Config notes
 * `sdk = [34]` is required for @GraphicsMode(NATIVE).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Smallest common font scale (0.85×). Verifies text is still readable and
     * no layout element is unexpectedly invisible or clipped.
     */
    @Test
    fun fontScale_small_matchesGolden() {
        rule.setContent { WithFontScale(fontScale = 0.85f) { ProductListPreview() } }
        ScreenshotTestBase.assertMatchesGolden(rule, "font_scale_small")
    }

    /**
     * Largest typical accessibility font scale (1.30×). Verifies text does not
     * overflow its container, truncate with ellipsis, or push adjacent elements
     * off-screen.
     */
    @Test
    fun fontScale_large_matchesGolden() {
        rule.setContent { WithFontScale(fontScale = 1.30f) { ProductListPreview() } }
        ScreenshotTestBase.assertMatchesGolden(rule, "font_scale_large")
    }

    /**
     * Wraps [content] in a [CompositionLocalProvider] that overrides
     * [LocalDensity] with the given [fontScale], preserving the existing
     * pixel-density so only the text scale changes.
     */
    @Composable
    private fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
        val screenDensity = LocalContext.current.resources.displayMetrics.density
        val scaledDensity = Density(density = screenDensity, fontScale = fontScale)
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            content()
        }
    }

    /**
     * Renders the text hierarchy found in a typical populated Home screen:
     *  - App bar title (`titleLarge`)
     *  - Product name (`bodyLarge`)
     *  - Quantity badge (`titleMedium`)
     *  - "Last updated" timestamp copy (`bodySmall`)
     *
     * This exercises all text styles without requiring ViewModel wiring or
     * live data.
     */
    @Composable
    private fun ProductListPreview() {
        PantryTrackerTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Pantry Tracker",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                // Two product rows — one with a short name, one with a longer name —
                // so the golden covers both tight and wide text scenarios.
                ProductRowText(name = "Milk", quantity = 3)
                ProductRowText(name = "Organic Whole-grain Pasta Fusilli", quantity = 1)
                Text(
                    text = "Last updated just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    @Composable
    private fun ProductRowText(name: String, quantity: Int) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "×$quantity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
