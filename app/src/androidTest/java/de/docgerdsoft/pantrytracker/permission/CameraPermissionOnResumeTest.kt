package de.docgerdsoft.pantrytracker.permission

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import de.docgerdsoft.pantrytracker.testfixtures.FakeIntentLauncher
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test

/**
 * SR-77 — §6 row 6: onResume auto-recovery regression test.
 *
 * Regression description (the "M6-caught regression"):
 *   The user has Camera permission revoked → returns to the app and grants it
 *   out-of-band (here: while backgrounded) → on the next ON_RESUME the gate MUST
 *   automatically transition to the camera preview (the
 *   [CameraPermissionPhase.Granted] branch) without requiring an extra tap.
 *
 *   Note on the starting phase: the injected `isCameraGranted` checker reports
 *   not-granted, and `shouldShowRequestPermissionRationale` is `false` in the
 *   test process, so `initialPhase` yields [CameraPermissionPhase.Unknown] — the
 *   rationale dialog, NOT HardDenied. The path under test is therefore
 *   **Unknown → (grant) → Granted**. The HardDenied → Settings deep-link path is
 *   covered by [CameraPermissionDeepLinkTest]; what matters here is solely the
 *   ON_RESUME re-check promoting *any* non-Granted phase to Granted once the
 *   checker reports the permission as held.
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
 *   1. Start with the injected `isCameraGranted` checker reporting not-granted
 *      (ensure we begin with no permission) — see the `cameraGranted` field doc
 *      (#117) for why this is a seam, not a real `revokeRuntimePermission`.
 *   2. Mount [CameraPermissionGate] in a real [ComponentActivity] so the
 *      lifecycle is controllable via [activityRule.scenario.moveToState].
 *   3. Assert a POSITIVE pre-state — the rationale dialog ("Camera access") is
 *      shown and the content lambda is not — so a wrong starting phase (e.g.
 *      nothing rendered) fails loudly instead of letting a vacuous
 *      `assertDoesNotExist` pass.
 *   4. Move the activity to [Lifecycle.State.STARTED] (simulates app going
 *      to background while the user is in Settings).
 *   5. Flip the injected `isCameraGranted` checker to granted
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

    // Drives the gate's permission read via the [CameraPermissionGate] `isCameraGranted`
    // seam (#117) instead of real OS runtime permission. The previous version revoked
    // CAMERA in @Before to force the not-granted start state; on `develop` an earlier
    // test (ErrorToneSemanticsTest) had already granted CAMERA in the shared
    // instrumentation process, so revoking a *held* permission triggered an
    // `ActivityManager` "permissions revoked" process kill and crashed the whole run.
    // Flipping this flag (instead of granting via UiAutomation) keeps the real
    // ON_RESUME re-check wiring under test while staying deterministic and independent
    // of test-execution order.
    private var cameraGranted = false

    @Test
    fun onResume_afterPermissionGranted_transitionsToGrantedWithoutExtraTap() {
        val fakeLauncher = FakeIntentLauncher()

        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGate(
                        onNavigateBack = {},
                        intentLauncher = fakeLauncher,
                        isCameraGranted = { cameraGranted },
                    ) { Text("CAMERA CONTENT") }
                }
            }
        }

        // Step 3: Verify initial state with a POSITIVE assertion first — the
        // not-granted seam yields the Unknown phase, which renders the rationale dialog.
        // Asserting the dialog title is present means a wrong starting phase (or
        // nothing rendered at all) fails loudly here, rather than the
        // assertDoesNotExist below passing vacuously.
        composeRule.onNodeWithText("Camera access").assertIsDisplayed()
        // ...and the camera content must NOT yet be shown.
        composeRule.onNodeWithText("CAMERA CONTENT").assertDoesNotExist()

        // Step 4: Move to STARTED (simulates app backgrounded while user is in Settings).
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

        // Step 5: Grant camera permission (simulates user tapping Allow in Settings).
        // Flip the injected checker instead of a real OS grant — see the field doc (#117).
        cameraGranted = true

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
