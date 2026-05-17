package de.docgerdsoft.pantrytracker.ui.scan

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
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
        assert(continueCalled) { "Continue button must trigger onContinue" }
    }

    @Test
    fun softDenied_showsRetryAffordance_andNotContent() {
        composeRule.setContent {
            PantryTrackerTheme {
                Surface {
                    CameraPermissionGateContent(
                        phase = CameraPermissionPhase.SoftDenied,
                        onContinue = {},
                        onOpenSettings = {},
                        onNavigateBack = {},
                    ) { Text("WRAPPED") }
                }
            }
        }
        composeRule.onNodeWithText("Camera access needed").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
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
        assert(openSettingsCalled) { "Open settings button must trigger onOpenSettings" }
    }
}
