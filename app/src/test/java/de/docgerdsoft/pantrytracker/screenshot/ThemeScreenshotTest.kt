package de.docgerdsoft.pantrytracker.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RNG screenshot tests for theme variants (§2 rows 1, 2 of the UAT checklist).
 *
 * Verifies:
 *  - The app renders a recognisable light-mode scheme (§2 row 1).
 *  - The fern-green primary colour is visible on the top app bar in both light and dark modes
 *    (§2 row 2).
 *
 * `qualifiers = "notnight-xxhdpi"` forces light mode; `qualifiers = "night-xxhdpi"` forces
 * dark mode.  Both use the same seed-fern primary (`Color(0xFF4F7942)`) because the project
 * uses the same `Fern` constant for both `lightColorScheme` and `darkColorScheme`.
 *
 * ## Config notes
 * `sdk = [34]` is required for @GraphicsMode(NATIVE).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Light-mode render of a simple home-screen skeleton with one product row.
     * Golden captures the top-app-bar primary fern-green and the surface background.
     */
    @Test
    @Config(qualifiers = "notnight-xxhdpi")
    fun theme_lightMode_matchesGolden() {
        rule.setContent { ThemedPreview(darkTheme = false) }
        ScreenshotTestBase.assertMatchesGolden(rule, "theme_light_mode")
    }

    /**
     * Dark-mode render of the same skeleton.
     * Golden captures that the primary slot still uses the fern-green value in dark
     * colour scheme (§2 row 2 — "primary colour fern green in both modes").
     */
    @Test
    @Config(qualifiers = "night-xxhdpi")
    fun theme_darkMode_matchesGolden() {
        rule.setContent { ThemedPreview(darkTheme = true) }
        ScreenshotTestBase.assertMatchesGolden(rule, "theme_dark_mode")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ThemedPreview(darkTheme: Boolean) {
        PantryTrackerTheme(darkTheme = darkTheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                TopAppBar(
                    title = { Text("Pantry Tracker") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                // Simulate one product row so the golden is meaningful.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        text = "Sample Product ×2",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
