package de.docgerdsoft.pantrytracker.ui.scan

import kotlinx.coroutines.flow.Flow

/**
 * Indirection seam for the barcode-producing camera. Production rendering
 * (`CameraPreview` composable wired through CameraX + ML Kit) does NOT
 * implement this — it pushes barcodes via the callback parameter passed
 * directly to the composable. This interface is the **test-only** seam:
 * instrumented Compose UI tests inject a [CameraSource] (a
 * `FakeCameraSource` in `testfixtures/`) so the test can drive synthetic
 * barcode events without needing a real back camera or printed barcode.
 *
 * `ScanScreen` collects from [barcodes] when a [CameraSource] is supplied
 * via `AppContainer.cameraSource`, and renders the real `CameraPreview`
 * composable otherwise. The default `AppContainer.real(context)` wires
 * `cameraSource = null`, so production behaviour is unchanged.
 *
 * The API is intentionally minimal — a single `Flow<String>` — so the
 * production source tree carries no test-only abstractions beyond the
 * barest seam needed to inject a double.
 */
interface CameraSource {
    /**
     * Flow of decoded barcode strings. Each emission represents one decode
     * event; the same string fires repeatedly for the same physical scan
     * (ML Kit decodes per frame). Implementations are expected to be hot
     * (shared across subscribers) — see [FakeCameraSource] for the test
     * double that wraps a `MutableSharedFlow`. Each emission is forwarded
     * to `ScanViewModel.onBarcodeDecoded(barcode)` exactly the way a real
     * ML Kit decode callback would be — the same sanitize / de-dup /
     * resolve pipeline runs.
     */
    val barcodes: Flow<String>
}
