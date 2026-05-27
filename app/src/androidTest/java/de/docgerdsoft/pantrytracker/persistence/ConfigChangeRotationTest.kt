package de.docgerdsoft.pantrytracker.persistence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import de.docgerdsoft.pantrytracker.MainActivity
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.testfixtures.FakeProductRepository
import de.docgerdsoft.pantrytracker.testfixtures.TestPantryTrackerApp
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

/**
 * UAT §14 row 3 — Configuration change (rotate / dark-light / font scale).
 *
 * Exercises [MainActivity] recreation via
 * [composeTestRule.activityRule.scenario.recreate()], which is the
 * programmatic equivalent of a real device rotation. Uses
 * [createAndroidComposeRule] (not bare [createComposeRule]) because Activity
 * recreation requires the real [MainActivity] lifecycle — Compose rule alone
 * cannot drive [Activity.recreate()].
 *
 * Strategy:
 *  1. Inject [FakeProductRepository] via [TestPantryTrackerApp] in [setup]
 *     BEFORE [createAndroidComposeRule] launches the Activity.
 *  2. Pre-seed one product ("Olive Oil ×2"), navigate to the Detail screen.
 *  3. Call [scenario.recreate()] — destroys + recreates the Activity,
 *     simulating rotation / dark-mode toggle / font-scale change.
 *  4. Assert: Detail screen is still shown (nav backstack preserved), the
 *     committed name and quantity survive (no data loss), and the app has
 *     not crashed (implicit: test completing without exception).
 *
 * What is NOT tested here (per spec, out of scope):
 *  - Typed-but-uncommitted edit preservation — known v1 limitation, explicitly
 *    excluded from the §14 row 3 checklist note.
 *  - Reboot persistence (§14 row 2) — stays human.
 *  - adb pm clear (§14 row 4) — covered by child #8.
 *
 * Covers: UAT §14 row 3 [automated by SR-78].
 */
class ConfigChangeRotationTest {

    /**
     * [createAndroidComposeRule] launches [MainActivity] and exposes the
     * Compose semantic tree for assertions. The [scenario.recreate()] API is
     * what enables Activity-lifecycle-level config-change simulation.
     *
     * JUnit4 evaluates [@get:Rule] fields after [@BeforeClass]; [setup] below
     * installs the fake container before this rule fires, so the Activity reads
     * the fake [AppContainer] in its own [onCreate].
     */
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun configChange_detailScreenSurvivesRecreation() {
        // --- 1. Wait for Home and navigate to Detail ---
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(PRODUCT_NAME)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(PRODUCT_NAME).performClick()

        // Assert Detail screen is visible with committed state before recreate.
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText("Product details")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Product details").assertIsDisplayed()
        composeTestRule.onNodeWithText(PRODUCT_NAME).assertIsDisplayed()
        // Quantity "2" is visible in the stepper area.
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(PRODUCT_QUANTITY_STR)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText(PRODUCT_QUANTITY_STR)[0].assertIsDisplayed()

        // --- 2. Simulate configuration change (rotation / dark-mode / font-scale) ---
        // scenario.recreate() is the instrumented-test equivalent of:
        //   adb shell settings put system user_rotation 1
        // It destroys the Activity synchronously and waits for onResume on
        // the new instance before returning. The Compose test rule automatically
        // reattaches its semantic tree to the recreated window.
        composeTestRule.activityRule.scenario.recreate()

        // --- 3. Assert: Activity rebuilt cleanly; Detail screen still shown ---
        // Navigation Compose preserves the back-stack across config changes via
        // SavedStateHandle. The ViewModel is retained by ViewModelStore (which
        // survives recreation). The FakeProductRepository in-memory state also
        // survives because it lives in the Application-scoped AppContainer.
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText("Product details")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Product details").assertIsDisplayed()
        // Committed name must survive the recreation (no data loss).
        composeTestRule.onNodeWithText(PRODUCT_NAME).assertIsDisplayed()
        // Quantity survives the recreation.
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(PRODUCT_QUANTITY_STR)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText(PRODUCT_QUANTITY_STR)[0].assertIsDisplayed()
    }

    companion object {
        private const val PRODUCT_NAME = "Olive Oil"
        private const val PRODUCT_QUANTITY = 2
        private const val PRODUCT_QUANTITY_STR = "2"
        private const val TIMEOUT_MS = 5_000L

        /**
         * Installs the fake container on [TestPantryTrackerApp] BEFORE JUnit4
         * evaluates any [@get:Rule] on the test class instance.
         *
         * JUnit4 lifecycle order:
         *   1. [@BeforeClass] (static) ← this method
         *   2. Test instance created
         *   3. [@get:Rule] evaluated → [createAndroidComposeRule] launches Activity
         *   4. [@Before] (instance)
         *   5. [@Test]
         *
         * So [setup] runs before the Activity is launched, which is exactly the
         * window in which [TestPantryTrackerApp.overrideContainer] must be called.
         */
        @BeforeClass
        @JvmStatic
        fun setup() {
            val app = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as? TestPantryTrackerApp
                ?: return // not running with TestPantryTrackerApp; skip injection

            val now = Clock.System.now()
            val repo = FakeProductRepository().also { r ->
                r.seed(
                    Product(
                        id = 1L,
                        barcode = null,
                        name = PRODUCT_NAME,
                        quantity = PRODUCT_QUANTITY,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            app.overrideContainer(AppContainer(productRepository = repo))
        }
    }
}
