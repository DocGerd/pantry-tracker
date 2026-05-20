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
 * This is the smallest seam that admits a double — there is no full DI
 * rewrite, no `CameraPreview` refactor. SR-75 introduces this; the same
 * shape is referenced by 4 downstream Wave 3 tickets (#76, #77, #78, #82
 * androidTest counterparts), so the API is intentionally small.
 */
interface CameraSource {
    /**
     * Cold flow of decoded barcode strings. Tests typically back this with a
     * `MutableSharedFlow<String>` and call `emit(barcode)` to fire a single
     * scan event. Each emission is forwarded to
     * `ScanViewModel.onBarcodeDecoded(barcode)` exactly the way a real ML
     * Kit decode callback would be — the same sanitize / de-dup / resolve
     * pipeline runs.
     */
    val barcodes: Flow<String>
}
