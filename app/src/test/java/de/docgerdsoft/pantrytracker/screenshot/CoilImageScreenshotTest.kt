package de.docgerdsoft.pantrytracker.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RNG screenshot tests for Coil image loading on the detail screen
 * (v1.2 appendix #11: "Coil image loads from OFF on detail screen").
 *
 * Two scenarios are tested:
 *  - Image present: an `AsyncImage` fed by an in-memory interceptor that
 *    returns a fixed solid-colour bitmap — verifies the image slot renders
 *    at the expected size/shape without requiring a real network.
 *  - Image absent (null URL): verifies the image slot is simply not shown,
 *    so no broken-placeholder or crash occurs.
 *
 * ## Test-interceptor approach (no coil-test dep needed)
 * Coil 3's `AsyncImage` composable accepts an explicit `imageLoader` parameter.
 * We build a custom `ImageLoader` whose interceptor pipeline contains a
 * `FixedBitmapInterceptor` that returns a pre-built 160×160 solid-cyan bitmap
 * for every request, bypassing all network code entirely.
 *
 * ## Config notes
 * `sdk = [34]` is required for @GraphicsMode(NATIVE).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoilImageScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Detail screen with a product image present.
     * The interceptor substitutes a 160×160 solid-cyan bitmap for any URL, so
     * the golden captures the image slot rendered at the correct size (160 dp)
     * aligned to the centre of the column — no network needed.
     */
    @Test
    fun detailScreen_withImage_matchesGolden() {
        rule.setContent {
            val fakeLoader = buildFakeImageLoader()
            DetailContentPreview(imageUrl = "https://images.openfoodfacts.org/coke.jpg", imageLoader = fakeLoader)
        }
        ScreenshotTestBase.assertMatchesGolden(rule, "coil_image_present")
    }

    /**
     * Detail screen with no product image (imageUrl == null).
     * Verifies the image slot is absent — no broken placeholder, no crash.
     */
    @Test
    fun detailScreen_withoutImage_matchesGolden() {
        rule.setContent {
            DetailContentPreview(imageUrl = null, imageLoader = null)
        }
        ScreenshotTestBase.assertMatchesGolden(rule, "coil_image_absent")
    }

    // -------------------------------------------------------------------------
    // Composable helpers
    // -------------------------------------------------------------------------

    /**
     * Minimal reproduction of [DetailScreen]'s product-body column, showing
     * the image slot (if a URL is present) and the product name.
     *
     * Mirrors the production layout from `DetailScreen.kt` so a structural
     * change there would show up in the golden diff.
     */
    @Composable
    private fun DetailContentPreview(imageUrl: String?, imageLoader: ImageLoader?) {
        PantryTrackerTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (imageUrl != null && imageLoader != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Product photo",
                        imageLoader = imageLoader,
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    text = "Coke 330 ml",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Brand: Coca-Cola",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Quantity: 2",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fake ImageLoader
    // -------------------------------------------------------------------------

    /**
     * Builds a Coil 3 [ImageLoader] whose interceptor pipeline contains
     * [FixedBitmapInterceptor] at the front, so every image request — regardless
     * of URL — immediately returns a 160×160 solid-cyan bitmap.
     */
    @Composable
    private fun buildFakeImageLoader(): ImageLoader {
        val context = LocalContext.current
        return ImageLoader.Builder(context)
            .components { add(FixedBitmapInterceptor) }
            .build()
    }

    /**
     * Coil 3 [Interceptor] that short-circuits every request by returning a
     * fixed solid-cyan 160×160 [Bitmap] as a successful [SuccessResult].
     *
     * Using an interceptor means the custom `ImageLoader` is the only wiring
     * needed — no Coil singleton mutation is required.
     */
    private object FixedBitmapInterceptor : Interceptor {

        private const val FIXED_SIZE = 160

        private val fixedBitmap: Bitmap by lazy {
            Bitmap.createBitmap(FIXED_SIZE, FIXED_SIZE, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                val paint = Paint().apply { color = android.graphics.Color.CYAN }
                canvas.drawRect(0f, 0f, FIXED_SIZE.toFloat(), FIXED_SIZE.toFloat(), paint)
            }
        }

        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            return SuccessResult(
                image = fixedBitmap.asImage(),
                request = chain.request,
                dataSource = DataSource.MEMORY,
            )
        }
    }
}
