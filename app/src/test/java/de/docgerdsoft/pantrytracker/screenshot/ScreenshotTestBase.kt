package de.docgerdsoft.pantrytracker.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.fail
import java.io.File
import java.io.FileOutputStream

/**
 * Shared golden-comparison helpers for RNG screenshot tests.
 *
 * ## Diff strategy: byte-for-byte
 * On first run the golden does not exist → the captured PNG is written to
 * [SNAPSHOTS_DIR] and the test **fails** with a clear message so the developer
 * knows they must review and commit the new golden before CI can go green.
 * On subsequent runs the PNG bytes are compared exactly.
 *
 * ## Why byte-for-byte?
 * RNG renders deterministically on the same host (same Robolectric version,
 * same SDK level, same device config).  A pixel-level tolerance would silently
 * accept font-hinting regressions that are clearly visible to the human eye.
 *
 * ## Regenerating goldens
 * Delete the stale `.png` under [SNAPSHOTS_DIR] and re-run the test.  The
 * test will write a fresh golden and fail once; commit the file and CI passes.
 * See [app/src/test/snapshots/README.md] for the full procedure.
 *
 * ## Robolectric + RNG capture note
 * `SemanticsNodeInteraction.captureToImage()` internally calls `forceRedraw`
 * which waits for a `ViewTreeObserver.OnDrawListener` to fire. In Robolectric,
 * this callback does not fire via normal test execution because there is no
 * real hardware display. Instead we render the root decor view directly to a
 * [Bitmap] via [View.draw] — this works correctly with `@GraphicsMode(NATIVE)`
 * because RNG provides a real Skia pipeline via a software rasterizer.
 *
 * The [rule] parameter is an `AndroidComposeTestRule<ComponentActivity>` so we
 * can reach into the Activity's window decor to grab the rendered view.
 */
internal object ScreenshotTestBase {

    /**
     * Absolute path to the snapshots directory, resolved relative to the
     * Gradle module root regardless of where Gradle sets the working directory
     * during test execution.
     */
    private val SNAPSHOTS_DIR: File by lazy {
        // `user.dir` is set to <module-root> by Robolectric / Gradle test runner.
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        File(moduleRoot, "src/test/snapshots").also { it.mkdirs() }
    }

    /**
     * Renders the Compose content via the Activity's decor view and asserts
     * it matches the golden at `src/test/snapshots/<name>.png`.
     *
     * On first run (no golden): writes the PNG and fails with a descriptive
     * message so the developer knows to review and commit it.
     *
     * On subsequent runs: compares byte-for-byte and fails if they differ.
     */
    fun assertMatchesGolden(
        rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
        name: String,
    ) {
        val bitmap = renderToBitmap(rule)
        compareOrWrite(bitmap, name)
    }

    /**
     * Renders the Activity's decor view to a [Bitmap] using [View.draw].
     *
     * We use the decor view (not a specific semantic node) so the golden image
     * captures exactly what the user would see — including background colour,
     * padding, and theme — without depending on node hit-testing.
     *
     * The view is measured and laid out at a fixed 1080×1920 size (portrait
     * xxhdpi baseline) before drawing so golden images are stable regardless
     * of the host machine's display size.
     */
    private fun renderToBitmap(
        rule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    ): Bitmap {
        rule.waitForIdle()
        var bitmap: Bitmap? = null
        rule.activityRule.scenario.onActivity { activity ->
            val decorView = activity.window.decorView
            // Measure and lay out at a fixed size so the golden doesn't depend
            // on host-machine display dimensions.
            val width = 1080
            val height = 1920
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            decorView.measure(widthSpec, heightSpec)
            decorView.layout(0, 0, width, height)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                decorView.draw(canvas)
            }
        }
        return bitmap ?: error("Failed to render bitmap from activity — activity was not available")
    }

    private fun compareOrWrite(bitmap: Bitmap, name: String) {
        val goldenFile = File(SNAPSHOTS_DIR, "$name.png")

        if (!goldenFile.exists()) {
            writePng(bitmap, goldenFile)
            fail(
                "Golden '$name.png' did not exist — wrote it to ${goldenFile.absolutePath}. " +
                    "Review the image visually, then commit it and re-run the test.",
            )
        }

        val goldenBytes = goldenFile.readBytes()
        val actualBytes = encodePng(bitmap)

        if (!goldenBytes.contentEquals(actualBytes)) {
            // Overwrite so the developer can inspect the new render side-by-side.
            writePng(bitmap, File(SNAPSHOTS_DIR, "${name}_actual.png"))
            fail(
                "Screenshot '$name.png' differs from golden. " +
                    "Actual PNG written to ${SNAPSHOTS_DIR.absolutePath}/${name}_actual.png. " +
                    "If the change is intentional, delete the golden and re-run to regenerate.",
            )
        }
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
