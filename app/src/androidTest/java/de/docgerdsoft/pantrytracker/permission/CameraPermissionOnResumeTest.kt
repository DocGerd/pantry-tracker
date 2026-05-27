package de.docgerdsoft.pantrytracker.permission

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.testfixtures.FakeIntentLauncher
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * SR-77 — §6 row 6: onResume auto-recovery regression test.
 *
 * Regression description (the "M6-caught regression"):
 *   User is on the HardDenied screen → taps "Open settings" → grants Camera
 *   permission in the OS Settings app → presses device Back to return to the
 *   app → the app MUST automatically transition to the camera preview (i.e.
 *   the [CameraPermissionPhase.Granted] branch) without requiring an extra tap.
 *
 * Mechanism under test:
 *   [CameraPermissionGate] installs a [androidx.lifecycle.LifecycleEventObserver]
 *   via [androidx.compose.runtime.DisposableEffect] that re-checks
 *   [android.content.pm.PackageManager.PERMISSION_GRANTED] on every
 *   [Lifecycle.Event.ON_RESUME]. When the user returns from Settings after
 *   granting the permission, the app's activity resumes → the observer fires →
 *   the phase is promoted to [CameraPermissionPhase.Granted] → the composable
 *   recomposes to show the content lambda (camera preview).
 *
 * Test strategy:
 *   1. Revoke camera permission (ensure we start with no permission).
 *   2. Mount [CameraPermissionGate] in a real [ComponentActivity] so the
 *      lifecycle is controllable via [activityRule.scenario.moveToState].
 *   3. Capture the initial render (gate shows rationale/deny UI, not content).
 *   4. Move the activity to [Lifecycle.State.STARTED] (simulates app going
 *      to background while the user is in Settings).
 *   5. Grant camera permission via [UiAutomation.grantRuntimePermission]
 *      (simulates the user tapping "Allow" in OS Settings).
 *   6. Move the activity back to [Lifecycle.State.RESUMED] (simulates the
 *      user pressing Back to return from Settings).
 *   7. Assert: the composable now shows the content lambda ("CAMERA CONTENT")
 *      WITHOUT any additional user interaction.
 *
 * [createAndroidComposeRule] is used (not [createComposeRule]) because we need
 * [activityRule.scenario.moveToState] to exercise the real lifecycle path that
 * triggers [Lifecycle.Event.ON_RESUME]. [createComposeRule] does not expose the
 * underlying activity scenario.
 */
class CameraPermissionOnResumeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun revokeCamera() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .revokeRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )
    }

    @Test
    fun onResume_afterPermissionGranted_transitionsToGrantedWithoutExtraTap() {
        val fakeLauncher = FakeIntentLauncher()

        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGate(
                        onNavigateBack = {},
                        intentLauncher = fakeLauncher,
                    ) { Text("CAMERA CONTENT") }
                }
            }
        }

        // Step 3: Verify initial state — camera permission is not granted,
        // so the rationale dialog or HardDenied screen is shown; content is NOT.
        // "CAMERA CONTENT" must not be visible.
        composeRule.onNodeWithText("CAMERA CONTENT").assertDoesNotExist()

        // Step 4: Move to STARTED (simulates app backgrounded while user is in Settings).
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

        // Step 5: Grant camera permission (simulates user tapping Allow in Settings).
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )

        // Step 6: Move back to RESUMED (simulates Back press returning from Settings).
        // The DisposableEffect observer fires ON_RESUME → re-checks permission →
        // finds PERMISSION_GRANTED → sets phase = CameraPermissionPhase.Granted.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        // Step 7: Without any additional user interaction, the composable must
        // recompose to show the content lambda.
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeRule.onAllNodesWithText("CAMERA CONTENT")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("CAMERA CONTENT").assertIsDisplayed()
    }

    private companion object {
        // 5 s covers Compose recomposition + lifecycle dispatch latency on slow
        // CI emulators (same budget as HappyPathUatTest cold-start chains).
        private const val TIMEOUT_MS = 5_000L
    }
}
