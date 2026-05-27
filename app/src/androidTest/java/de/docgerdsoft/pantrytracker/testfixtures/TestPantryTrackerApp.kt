package de.docgerdsoft.pantrytracker.testfixtures

import coil3.ImageLoader
import coil3.SingletonImageLoader
import de.docgerdsoft.pantrytracker.PantryTrackerApp
import de.docgerdsoft.pantrytracker.di.AppContainer

/**
 * Test application that replaces [PantryTrackerApp] in the androidTest APK.
 *
 * Registered via `app/src/androidTest/AndroidManifest.xml`
 * (`android:name=".testfixtures.TestPantryTrackerApp"`). By overriding the
 * application class, Activity-level tests (e.g. [ConfigChangeRotationTest])
 * can inject a [FakeProductRepository] into [AppContainer] **before** the
 * Activity under test reads `(application as PantryTrackerApp).container`.
 *
 * Usage in a test:
 *
 * ```
 * val app = InstrumentationRegistry.getInstrumentation()
 *     .targetContext.applicationContext as TestPantryTrackerApp
 * app.overrideContainer(AppContainer(FakeProductRepository()))
 * // createAndroidComposeRule / ActivityScenario.launch will now use the fake
 * ```
 *
 * [overrideContainer] immediately replaces the [PantryTrackerApp.container]
 * field via reflection so that the next [Activity.onCreate] reads the fake
 * container. It must be called before launching the Activity under test —
 * typically from a companion-object `init` block (which runs at class-load
 * time, before JUnit4 rules fire) or from a [org.junit.ClassRule].
 *
 * Thread-safety: [overrideContainer] is expected to be called from the test
 * thread; [AppContainer] reads happen on the main thread. No concurrent access
 * expected in the normal test sequence.
 */
class TestPantryTrackerApp : PantryTrackerApp() {

    override fun onCreate() {
        super.onCreate() // initialises `container` via AppContainer.real(...)
        // Coil singleton — ensures AsyncImage composables work in tests even
        // when the production singleton was already configured by super.onCreate.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).build()
        }
    }

    /**
     * Immediately replaces the [PantryTrackerApp.container] field with
     * [container] via reflection.
     *
     * Call this before launching the Activity under test. The Activity reads
     * `(application as PantryTrackerApp).container` in its own [onCreate],
     * so the override must land before that call.
     */
    fun overrideContainer(container: AppContainer) {
        // PantryTrackerApp.container has a private setter — bypass it.
        val field = PantryTrackerApp::class.java.getDeclaredField("container")
        field.isAccessible = true
        field.set(this, container)
    }
}
