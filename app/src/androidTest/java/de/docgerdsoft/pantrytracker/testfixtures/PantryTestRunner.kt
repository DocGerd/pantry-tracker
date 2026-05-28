package de.docgerdsoft.pantrytracker.testfixtures

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Custom instrumentation runner that swaps the production [PantryTrackerApp]
 * for [TestPantryTrackerApp] in the androidTest process.
 *
 * Why a runner instead of `android:name` in the androidTest manifest: the
 * manifest-merge `android:name` override is NOT a reliable way to substitute
 * the Application class on-device. AGP merges the androidTest manifest into
 * the *test* APK, but the Application that actually instantiates at runtime is
 * the one declared in the **target** (app-under-test) manifest, which still
 * names [PantryTrackerApp]. The canonical Android mechanism to force a
 * different Application for instrumented tests is to override
 * [newApplication] in a custom [AndroidJUnitRunner] — that hook is consulted
 * by the instrumentation framework when it creates the Application instance,
 * so it reliably wins regardless of manifest-merge behaviour.
 *
 * Effect: EVERY instrumented test now runs against [TestPantryTrackerApp].
 * That is safe because:
 *  - [TestPantryTrackerApp] extends [PantryTrackerApp] and its [onCreate]
 *    still calls `super.onCreate()`, so the real [AppContainer] is wired up
 *    exactly as in production unless a test explicitly calls
 *    `overrideContainer(...)`.
 *  - The Compose UI tests (`HappyPathUatTest`, `Scan*Test`, the SR-77
 *    permission tests, SR-82's `OffCacheOfflineReplayTest`, `ErrorToneSemanticsTest`)
 *    inject their fakes via `PantryTrackerNavGraph(container = …)` and never
 *    read `(application as PantryTrackerApp).container`, so the Application
 *    swap is transparent to them.
 *  - Only [ConfigChangeRotationTest] depends on the swap: it launches the real
 *    [MainActivity], which DOES read the Application's container, and it
 *    pre-installs a fake via `TestPantryTrackerApp.overrideContainer(...)` in
 *    `@BeforeClass`. Its hard cast `applicationContext as TestPantryTrackerApp`
 *    now succeeds because this runner guarantees the Application IS a
 *    [TestPantryTrackerApp].
 */
class PantryTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application =
        super.newApplication(cl, TestPantryTrackerApp::class.java.name, context)
}
