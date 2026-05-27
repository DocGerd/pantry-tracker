package de.docgerdsoft.pantrytracker.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.testfixtures.FakeIntentLauncher
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionPhase
import de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGateContent
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
 * Two strategies are used here:
 *   - Rows 1-4 use [CameraPermissionGateContent] (pure-presentation composable):
 *     pin the phase to [CameraPermissionPhase.HardDenied] and inject a
 *     [FakeIntentLauncher] via the stateful [CameraPermissionGate] wrapper.
 *   - Row 7 uses [CameraPermissionGate] with [FakeIntentLauncher.throwActivityNotFound]
 *     set to `true` before the tap, then asserts an Espresso Toast matcher.
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
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    // CameraPermissionGate (stateful) is used here so the
                    // FakeIntentLauncher is wired through the real
                    // openAppSettings() call path.
                    // We pass intentLauncher to override context::startActivity.
                    // initialPhase() without camera permission and with no
                    // Activity in LocalContext falls back to HardDenied, which
                    // is exactly the phase we want to drive.
                    CameraPermissionGate(
                        onNavigateBack = {},
                        intentLauncher = fakeLauncher,
                    ) { Text("CONTENT") }
                }
            }
        }

        // With no camera permission and no Activity in the test composition context,
        // CameraPermissionGate starts in HardDenied (null-activity fallback path).
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

    // --- §6 row 7: OEM-fail fallback — ActivityNotFoundException → Toast ---

    @Test
    fun openSettings_activityNotFound_showsToast() {
        val fakeLauncher = FakeIntentLauncher().apply {
            throwActivityNotFound = true
        }
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGate(
                        onNavigateBack = {},
                        intentLauncher = fakeLauncher,
                    ) { Text("CONTENT") }
                }
            }
        }

        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()

        // Espresso matcher — verifies the "Couldn't open settings on this device"
        // Toast is shown when ActivityNotFoundException is thrown. Toasts on
        // Android 11+ (API 30+) render via a platform popup window outside the
        // activity; `isPlatformPopup()` is the correct root matcher for this.
        // Toast copy must start with "Couldn't" per the project error-tone
        // convention (enforced by ErrorToneSemanticsTest for snackbar/sheet;
        // this test pins the same convention for the fallback Toast).
        onView(withText("Couldn't open settings on this device"))
            .inRoot(isPlatformPopup())
            .check(matches(isDisplayed()))
    }
}
