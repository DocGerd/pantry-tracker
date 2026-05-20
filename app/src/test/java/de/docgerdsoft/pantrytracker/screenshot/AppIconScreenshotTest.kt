package de.docgerdsoft.pantrytracker.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import de.docgerdsoft.pantrytracker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RNG screenshot tests for the adaptive launcher icon.
 *
 * Verifies §0 rows 2-4 of the UAT checklist:
 *  - The three-jars-on-shelf foreground renders without clipping on a 108×108 dp canvas.
 *  - The icon is visually centred and intact when masked to a circle (circular-icon launchers).
 *  - The icon is visually centred and intact when masked to a rounded-square (legacy/square
 *    launcher masks).
 *
 * These tests do NOT cover OEM launcher rendering fidelity (§0 row 3) — that remains
 * human-only because it depends on the actual system launcher, which Robolectric cannot
 * reproduce.
 *
 * ## Config notes
 * `sdk = [34]` is required for @GraphicsMode(NATIVE) — Robolectric's native graphics pipeline
 * was stabilised at API 34 level. `qualifiers = "xxhdpi"` pins a single density so golden
 * files are stable across hosts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Full adaptive-icon foreground at 108×108 dp on the fern-green background.
     * Catches regressions where the foreground vector is cropped, mis-aligned, or
     * missing entirely (§0 row 2).
     */
    @Test
    fun icon_fullCanvas_matchesGolden() {
        rule.setContent { IconOnBackground(Modifier.size(108.dp)) }
        ScreenshotTestBase.assertMatchesGolden(rule, "icon_full_canvas")
    }

    /**
     * Icon with a circular mask — simulates circular-icon launchers (§0 row 4).
     * Catches regressions where the three-jar graphic bleeds outside the safe zone
     * and gets clipped by the mask.
     */
    @Test
    fun icon_circularMask_matchesGolden() {
        rule.setContent { IconOnBackground(Modifier.size(108.dp).clip(CircleShape)) }
        ScreenshotTestBase.assertMatchesGolden(rule, "icon_circular_mask")
    }

    /**
     * Icon with a rounded-square (squircle) mask — simulates legacy/square-style
     * icon masks used by many launchers (§0 row 4 — centred + not clipped check).
     */
    @Test
    fun icon_squareMask_matchesGolden() {
        rule.setContent {
            IconOnBackground(Modifier.size(108.dp).clip(RoundedCornerShape(percent = 20)))
        }
        ScreenshotTestBase.assertMatchesGolden(rule, "icon_square_mask")
    }

    @Composable
    private fun IconOnBackground(modifier: Modifier) {
        // Background colour matches ic_launcher_background (#4F7942 = Fern).
        Box(
            modifier = modifier.background(Color(0xFF4F7942)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Launcher icon foreground",
                modifier = Modifier.size(108.dp),
            )
        }
    }
}
