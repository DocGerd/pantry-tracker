package de.docgerdsoft.pantrytracker.ui.scan.components

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX preview with a ML Kit barcode analyzer. Calls [onBarcode] with the decoded
 * raw value (EAN-13/EAN-8/UPC-A/UPC-E only). Per-frame decode failures are logged at
 * WARN; permanent scanner failures (MlKitException MODULE_UNAVAILABLE /
 * MODEL_HASH_MISMATCH) and camera-bind failures are surfaced via [onCameraError] so
 * the caller can transition to an error UI state per spec §7. The caller is also
 * responsible for de-duplicating rapid repeat detections (see
 * [de.docgerdsoft.pantrytracker.ui.scan.ScanViewModel.onBarcodeDecoded]).
 */
@Composable
fun CameraPreview(
    onBarcode: (String) -> Unit,
    onCameraError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember<ExecutorService> { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build()
        )
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val cameraProvider = providerFuture.get()

                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeFrame(imageProxy, scanner, onBarcode, onCameraError)
                        } }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (e: IllegalArgumentException) {
                        // e.g. no back camera on this device (tablets, foldables, some emulators)
                        onCameraError(e)
                    } catch (e: IllegalStateException) {
                        // Camera already bound or in a wrong lifecycle state
                        onCameraError(e)
                    } catch (e: CameraUnavailableException) {
                        onCameraError(e)
                    }
                } catch (e: ExecutionException) {
                    onCameraError(e.cause ?: e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    onCameraError(e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@OptIn(ExperimentalGetImage::class)
@SuppressLint("UnsafeOptInUsageError")
private fun analyzeFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onBarcode: (String) -> Unit,
    onCameraError: (Throwable) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcode)
        }
        .addOnFailureListener { e ->
            if (e is MlKitException && (
                    e.errorCode == MlKitException.MODULE_UNAVAILABLE ||
                        e.errorCode == MlKitException.MODEL_HASH_MISMATCH
                    )
            ) {
                // Permanent scanner failure — every frame will fail. Surface to caller.
                onCameraError(e)
            } else {
                // Transient per-frame failure (blurry, no barcode, etc.). Log at debug
                // to avoid logcat spam; this fires many times per second.
                Log.d("CameraPreview", "ML Kit decode skipped: ${e.message}")
            }
        }
        .addOnCompleteListener { imageProxy.close() }
}
