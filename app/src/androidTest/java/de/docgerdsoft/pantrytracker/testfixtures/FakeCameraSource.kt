package de.docgerdsoft.pantrytracker.testfixtures

import de.docgerdsoft.pantrytracker.ui.scan.CameraSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Test double for the [CameraSource] seam. Backed by a
 * `MutableSharedFlow<String>` with `replay = 0` (no replay-on-resubscribe)
 * — emissions before a collector attaches are dropped, matching ML Kit's
 * production behaviour (a freshly-started camera session does not re-deliver
 * the last decode from a prior session). Tests must `waitUntil` for the
 * scan screen UI before calling [emit].
 *
 * Usage in a Compose UI test:
 *
 * ```
 * val camera = FakeCameraSource()
 * val container = AppContainer(productRepository = repo, cameraSource = camera)
 * rule.setContent { PantryTrackerNavGraph(container = container) }
 *
 * // ...navigate to Scan to Add, then waitUntil for the scan top bar...
 * camera.emit("5449000000996") // fire one synthetic barcode
 * ```
 *
 * Tests can call [emit] multiple times to simulate ML Kit's repeat-decode
 * behaviour; `ScanViewModel`'s `isAlreadyShowing` de-dup will drop the
 * repeats just like in production.
 */
class FakeCameraSource : CameraSource {

    // No replay (replay = 0) — tests must `waitUntil` for the scan screen
    // to mount before emitting. extraBufferCapacity = 64 absorbs realistic
    // ML-Kit repeat-decode bursts without suspending tryEmit.
    private val _barcodes = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    override val barcodes: Flow<String> = _barcodes

    /**
     * Fire one barcode through the source. Non-suspending. Fails the test
     * loudly if the buffer is full (which never happens in single-emit tests,
     * but a stress test exceeding [BUFFER_CAPACITY] would otherwise silently
     * drop the barcode and produce a 5s-timeout test hang with no clear
     * cause).
     */
    fun emit(barcode: String) {
        check(_barcodes.tryEmit(barcode)) {
            "FakeCameraSource.emit($barcode) returned false — buffer full " +
                "($BUFFER_CAPACITY slots). Either no collector is attached " +
                "(call rule.waitUntil for the scan screen first) or the test " +
                "is emitting faster than the ViewModel can drain."
        }
    }

    private companion object {
        private const val BUFFER_CAPACITY = 64
    }
}
