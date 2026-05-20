package de.docgerdsoft.pantrytracker.testfixtures

import de.docgerdsoft.pantrytracker.ui.scan.CameraSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Test double for the SR-75 [CameraSource] seam. Backed by a
 * `MutableSharedFlow<String>` with `replay = 1` so a barcode emitted before
 * the screen is mounted is still delivered when `ScanScreen` starts
 * collecting in its `LaunchedEffect(cameraSource)`. This matches the
 * "scan-already-happened" semantics ML Kit can produce in production
 * (a frame can decode before the screen is fully composed).
 *
 * Usage in a Compose UI test:
 *
 * ```
 * val camera = FakeCameraSource()
 * val container = AppContainer(productRepository = repo, cameraSource = camera)
 * rule.setContent { PantryTrackerNavGraph(container = container) }
 *
 * // ...navigate to Scan to Add...
 * camera.emit("5449000000996") // fire one synthetic barcode
 * ```
 *
 * Tests can call [emit] multiple times to simulate ML Kit's repeat-decode
 * behaviour; `ScanViewModel`'s `isAlreadyShowing` de-dup will drop the
 * repeats just like in production.
 */
class FakeCameraSource : CameraSource {

    // replay = 1 so emissions before the LaunchedEffect attaches are still
    // delivered. extraBufferCapacity = 64 is intentionally large so a test
    // burst-emitting (e.g. simulating ML Kit's rapid repeat decodes) never
    // suspends — tryEmit() can't return false for collectors that haven't
    // started yet.
    private val _barcodes = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    override val barcodes: Flow<String> = _barcodes

    /** Fire one barcode through the source. Non-suspending — see the
     *  buffer comment above for why this is safe to call from a test
     *  thread without awaiting subscription. */
    fun emit(barcode: String) {
        _barcodes.tryEmit(barcode)
    }

    private companion object {
        private const val BUFFER_CAPACITY = 64
    }
}
