package de.docgerdsoft.pantrytracker.testfixtures

import android.content.ActivityNotFoundException
import android.content.Intent
import de.docgerdsoft.pantrytracker.ui.scan.IntentLauncher

/**
 * Test double for the [IntentLauncher] seam (SR-77).
 *
 * Records every [Intent] passed to [launch] into [launchedIntents] so
 * instrumented Compose tests can assert which intent was constructed and
 * dispatched by [de.docgerdsoft.pantrytracker.ui.scan.CameraPermissionGate].
 *
 * Set [throwActivityNotFound] to `true` to simulate the OEM-fallback path
 * where the Settings activity is disabled or filtered — the gate must show
 * a "Couldn't open settings on this device" Toast in this case.
 *
 * Usage in a Compose UI test:
 *
 * ```
 * val fakeLauncher = FakeIntentLauncher()
 * composeRule.setContent {
 *     PantryTrackerTheme {
 *         Surface {
 *             CameraPermissionGate(
 *                 onNavigateBack = {},
 *                 intentLauncher = fakeLauncher,
 *             ) { Text("Content") }
 *         }
 *     }
 * }
 * // ... click "Open settings" ...
 * assertNotNull(fakeLauncher.launchedIntents.firstOrNull())
 * assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fakeLauncher.launchedIntents[0].action)
 * ```
 */
class FakeIntentLauncher : IntentLauncher {

    /** All intents passed to [launch], in order. */
    val launchedIntents = mutableListOf<Intent>()

    /**
     * When `true`, [launch] throws [ActivityNotFoundException] to simulate
     * a stripped AOSP / MDM-locked device with no Settings activity.
     */
    var throwActivityNotFound: Boolean = false

    override fun launch(intent: Intent) {
        launchedIntents.add(intent)
        if (throwActivityNotFound) {
            throw ActivityNotFoundException(
                "FakeIntentLauncher: simulating ActivityNotFoundException for OEM-fallback test",
            )
        }
    }
}
