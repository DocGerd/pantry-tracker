package de.docgerdsoft.pantrytracker.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.testfixtures.FakeIntentLauncher
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGateContent
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionPhase
import de.docgerdsoft.pantrytracker.ui.scan.SETTINGS_UNAVAILABLE_MESSAGE
import de.docgerdsoft.pantrytracker.ui.scan.openAppSettings
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * SR-77 — §6 HardDenied deep-link + OEM-fallback tests.
 *
 * Covers:
 *   - §6 row 1: "Camera access blocked" headline is displayed in HardDenied phase
 *   - §6 row 2: body copy "Open Settings and allow camera access..." is displayed
 *   - §6 row 3: "Open settings" button is displayed
 *   - §6 row 4: tapping "Open settings" fires [Settings.ACTION_APPLICATION_DETAILS_SETTINGS]
 *               with data URI `package:de.docgerdsoft.pantrytracker`
 *   - §6 row 7: OEM-fallback — when the Settings activity throws
 *               [android.content.ActivityNotFoundException], the app shows a Toast
 *               starting with "Couldn't open settings"
 *
 * §6 row 5 (OEM-specific permission UI) is explicitly **human-only** per spec —
 * Xiaomi/Huawei/Samsung variations cannot be covered by instrumented tests.
 * §6 row 6 (onResume auto-recovery) is covered by [CameraPermissionOnResumeTest].
 *
 * All tests here drive the [CameraPermissionPhase.HardDenied] phase
 * **deterministically** by rendering the stateless [CameraPermissionGateContent]
 * with the phase pinned — never by relying on runtime phase inference. (The
 * stateful `CameraPermissionGate` cannot be used: `createComposeRule()` ==
 * `createAndroidComposeRule<ComponentActivity>()`, which hosts a real
 * `ComponentActivity`, so `findActivity()` returns non-null and `initialPhase`
 * lands on `Unknown`/`SoftDenied` — never `HardDenied`.)
 *
 *   - Rows 1-4 pin the phase and assert the HardDenied screen renders correctly.
 *   - Row 4 wires `onOpenSettings = { openAppSettings(context, fakeLauncher) }`,
 *     taps "Open settings", and asserts the intent the [FakeIntentLauncher]
 *     recorded.
 *   - Row 7 sets [FakeIntentLauncher.throwActivityNotFound] = `true`, taps
 *     "Open settings", and asserts **at the seam**: `openAppSettings` swallowed
 *     the [android.content.ActivityNotFoundException] (the tap did not crash the
 *     test) and recorded exactly one intent. The Toast copy is pinned via the
 *     [SETTINGS_UNAVAILABLE_MESSAGE] constant, not an Espresso root matcher —
 *     Android 12+ Toasts are not inspectable via `isPlatformPopup()`.
 */
class CameraPermissionDeepLinkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun revokeCamera() {
        // Ensure camera permission is NOT granted so CameraPermissionGate starts
        // with the rationale dialog (Unknown phase). The individual tests that
        // need HardDenied pin it via CameraPermissionGateContent directly.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .revokeRuntimePermission(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                Manifest.permission.CAMERA,
            )
    }

    // --- §6 rows 1-3: HardDenied screen renders correctly ---

    @Test
    fun hardDenied_showsBlockedHeadline() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("CONTENT") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access blocked").assertIsDisplayed()
    }

    @Test
    fun hardDenied_showsSettingsBody() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("CONTENT") }
                }
            }
        }
        composeRule.onNodeWithText(
            "Open Settings and allow camera access for Pantry Tracker, then come back.",
        ).assertIsDisplayed()
    }

    @Test
    fun hardDenied_showsOpenSettingsButton() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("CONTENT") }
                }
            }
        }
        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeRule.onNodeWithText("Go back").assertIsDisplayed()
    }

    @Test
    fun hardDenied_contentNotShown() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("WRAPPED CONTENT") }
                }
            }
        }
        // Camera preview / scan content must NOT be shown in HardDenied phase.
        composeRule.onNodeWithText("WRAPPED CONTENT").assertDoesNotExist()
    }

    // --- §6 row 4: "Open settings" tap fires correct deep-link intent ---

    @Test
    fun openSettings_tapFiresDeepLinkIntent() {
        val fakeLauncher = FakeIntentLauncher()
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    // Pin HardDenied directly via the stateless content composable
                    // and wire onOpenSettings to the real openAppSettings() path
                    // with the FakeIntentLauncher. This exercises the deep-link
                    // construction deterministically — no reliance on runtime
                    // initialPhase() inference (which never yields HardDenied under
                    // createComposeRule()'s real ComponentActivity host).
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = { openAppSettings(context, fakeLauncher) },
                        onNavigateBack = {},
                    ) { Text("CONTENT") }
                }
            }
        }

        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()

        // Assert the intent recorded by the fake launcher.
        assertNotNull(
            "FakeIntentLauncher must have recorded at least one intent",
            fakeLauncher.launchedIntents.firstOrNull(),
        )
        val fired: Intent = fakeLauncher.launchedIntents[0]
        assertEquals(
            "Intent action must be ACTION_APPLICATION_DETAILS_SETTINGS",
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            fired.action,
        )
        assertEquals(
            "Intent data URI must reference the app package",
            Uri.parse("package:de.docgerdsoft.pantrytracker"),
            fired.data,
        )
    }

    // --- §6 row 7: OEM-fail fallback — ActivityNotFoundException → caught ---

    @Test
    fun openSettings_activityNotFound_isCaughtAtSeam() {
        val fakeLauncher = FakeIntentLauncher().apply {
            throwActivityNotFound = true
        }
        val context: Context =
            InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        // If openAppSettings rethrew the ActivityNotFoundException,
                        // this click handler would crash the test — the test passing
                        // through the tap is itself the "does not rethrow" assertion.
                        onOpenSettings = { openAppSettings(context, fakeLauncher) },
                        onNavigateBack = {},
                    ) { Text("CONTENT") }
                }
            }
        }

        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()

        // Assert at the seam, NOT via an Espresso Toast root matcher
        // (isPlatformPopup() is for PopupWindow roots; Android 12+ Toasts are not
        // inspectable that way, and CI runs API 35). The launcher recorded exactly
        // one launch attempt, and the ActivityNotFoundException it threw was caught
        // inside openAppSettings — proven by the click handler completing without
        // propagating the exception out of performClick().
        assertEquals(
            "openAppSettings must attempt exactly one launch before catching ANFE",
            1,
            fakeLauncher.launchedIntents.size,
        )
        // Pin the exact OEM-fallback copy at its source-of-truth constant so a
        // copy change is caught here without depending on the rendered Toast.
        assertEquals(
            "Toast copy must match the project error-tone convention",
            "Couldn't open settings on this device",
            SETTINGS_UNAVAILABLE_MESSAGE,
        )
    }
}
