package de.docgerdsoft.pantrytracker.ui.scan

import android.content.Intent

/**
 * Minimal test seam for launching Intents from [CameraPermissionGate].
 *
 * Production code uses the default `context::startActivity` binding wired
 * inside [CameraPermissionGate]'s composable default argument. Instrumented
 * tests (SR-77) inject a [FakeIntentLauncher] from `testfixtures/` so the
 * deep-link intent can be captured and asserted without a real Settings app,
 * and the [android.content.ActivityNotFoundException] path can be forced to
 * test the OEM-fallback Toast copy.
 *
 * The API is intentionally minimal — a single [launch] method — so the
 * production source tree carries no test-only abstractions beyond the
 * barest seam needed to inject a double. Mirrors the [CameraSource] seam
 * pattern introduced in SR-75.
 */
fun interface IntentLauncher {
    /** Launch [intent]. May throw [android.content.ActivityNotFoundException]
     *  on stripped AOSP / MDM-locked devices; callers are responsible for
     *  catching it (see [CameraPermissionGate]). */
    fun launch(intent: Intent)
}
