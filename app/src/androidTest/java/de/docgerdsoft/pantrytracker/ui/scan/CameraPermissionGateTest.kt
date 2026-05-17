package de.docgerdsoft.pantrytracker.ui.scan

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CameraPermissionGateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun granted_rendersContent() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.Granted,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("WRAPPED CONTENT") }
                }
            }
        }
        composeRule.onNodeWithText("WRAPPED CONTENT").assertIsDisplayed()
    }

    @Test
    fun unknown_showsRationaleDialog_andContinueInvokesCallback() {
        var continueCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.Unknown,
                        onContinue = { continueCalled = true },
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()
        // Use JUnit's assertTrue — Kotlin's bare assert() compiles to a JVM
        // `assert` statement which is disabled by default on ART, making
        // the assertion a silent no-op in instrumentation tests.
        assertTrue("Continue button must trigger onContinue", continueCalled)
    }

    @Test
    fun unknown_cancelInvokesOnNavigateBack() {
        var backCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.Unknown,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = { backCalled = true },
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue("Cancel button must trigger onNavigateBack", backCalled)
    }

    @Test
    fun softDenied_showsRetryAffordance_hidesContent_andTryAgainInvokesContinue() {
        var continueCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.SoftDenied,
                        onContinue = { continueCalled = true },
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access needed").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onNodeWithText("WRAPPED").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").performClick()
        assertTrue(
            "Try again button must trigger onContinue (not onOpenSettings)",
            continueCalled,
        )
    }

    @Test
    fun hardDenied_showsOpenSettings_andInvokesIntentCallback() {
        var openSettingsCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = { openSettingsCalled = true },
                        onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access blocked").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()
        assertTrue("Open settings button must trigger onOpenSettings", openSettingsCalled)
    }

    @Test
    fun hardDenied_goBackInvokesOnNavigateBack() {
        var backCalled = false
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.HardDenied,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = { backCalled = true },
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Go back").performClick()
        assertTrue("Go back button must trigger onNavigateBack", backCalled)
    }
}
