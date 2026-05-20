package de.docgerdsoft.pantrytracker.ui.scan

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPreview
import de.docgerdsoft.pantrytracker.ui.scan.components.ErrorSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.LoadingSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.ManualEntrySheet
import de.docgerdsoft.pantrytracker.ui.scan.components.NotInInventorySheet
import de.docgerdsoft.pantrytracker.ui.scan.components.ScanPreviewSheet
import de.docgerdsoft.pantrytracker.ui.theme.AddGreen
import de.docgerdsoft.pantrytracker.ui.theme.RemoveRed
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
    cameraSource: CameraSource? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    BindTestCameraSource(cameraSource, viewModel)

    val topBarColor = if (state.mode == ScanMode.Add) AddGreen else RemoveRed
    val topBarTitle = if (state.mode == ScanMode.Add) "Scan to Add" else "Scan to Remove"

    // Haptic on transition into Preview/ManualEntry (i.e. each successful decode).
    // CONFIRM was added in API 30 (Android 11); fall back to KEYBOARD_TAP on
    // older devices (minSdk=26 → API 26-29 coverage). Avoids Lint InlinedApi
    // and silently-undefined-behaviour on pre-R devices.
    LaunchedEffect(state.phase) {
        if (state.phase is ScanUiState.Phase.Preview ||
            state.phase is ScanUiState.Phase.ManualEntry
        ) {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(constant)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Skip the real CameraX/ML Kit binding when a test [CameraSource]
            // is wired — the test drives `onBarcodeDecoded` via the
            // `LaunchedEffect` above. Binding CameraX in a Compose UI test on
            // an emulator without a back camera surfaces as
            // `IllegalArgumentException` (no compatible camera) and routes the
            // screen straight to `Phase.Error`, which would prevent any of
            // the scan-flow assertions from ever firing.
            if (cameraSource == null) {
                CameraPreview(
                    onBarcode = viewModel::onBarcodeDecoded,
                    onCameraError = { e ->
                        viewModel.onCameraError(e.message ?: "camera unavailable")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            when (val phase = state.phase) {
                ScanUiState.Phase.Idle -> Unit
                is ScanUiState.Phase.Loading -> LoadingSheet(
                    barcode = phase.barcode,
                    onCancel = viewModel::dismissPreview,
                )
                is ScanUiState.Phase.Preview -> ScanPreviewSheet(
                    candidate = phase.candidate,
                    pendingQuantity = phase.pendingQuantity,
                    mode = state.mode,
                    onQuantityChange = viewModel::setQuantity,
                    onConfirm = viewModel::confirm,
                    onDismiss = viewModel::dismissPreview,
                )
                is ScanUiState.Phase.ManualEntry -> ManualEntrySheet(
                    barcode = phase.barcode,
                    pendingQuantity = phase.pendingQuantity,
                    onQuantityChange = viewModel::setQuantity,
                    onSubmit = viewModel::submitManualEntry,
                    onDismiss = viewModel::dismissPreview,
                )
                is ScanUiState.Phase.NotInInventory -> NotInInventorySheet(
                    barcode = phase.barcode,
                    onSwitchToAdd = viewModel::onSwitchToAdd,
                    onDismiss = viewModel::dismissPreview,
                )
                is ScanUiState.Phase.Error -> ErrorSheet(
                    message = phase.message,
                    onDismiss = viewModel::dismissPreview,
                )
            }
        }
    }
}

/**
 * Test-only seam: when a [CameraSource] is injected (via
 * `AppContainer.cameraSource`, only set in instrumented Compose UI tests),
 * collect barcodes from its flow and forward them to the same
 * `onBarcodeDecoded` entry point a real ML Kit decode would call.
 * Production passes `null` and the real `CameraPreview` composable in
 * [ScanScreen] drives the callback the same way it always has. The collect
 * is keyed on the source identity so a future "swap source at runtime"
 * wouldn't leak a stale collector. See `CameraSource` KDoc for rationale.
 *
 * Exceptions from the upstream flow are routed to `viewModel.onCameraError`
 * — the same channel real `CameraPreview` failures use — so a misbehaving
 * source surfaces as `Phase.Error` rather than silently killing the
 * `LaunchedEffect` and leaving the screen stuck in `Phase.Idle`.
 * `CancellationException` is explicitly rethrown so structured concurrency
 * still tears down on screen exit.
 *
 * Extracted from [ScanScreen]'s body to keep that function under detekt's
 * LongMethod / CyclomaticComplexMethod thresholds.
 */
@Composable
private fun BindTestCameraSource(
    cameraSource: CameraSource?,
    viewModel: ScanViewModel,
) {
    if (cameraSource == null) return
    LaunchedEffect(cameraSource) {
        try {
            cameraSource.barcodes.collect { barcode ->
                viewModel.onBarcodeDecoded(barcode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            viewModel.onCameraError(e.message ?: "camera source error")
        }
    }
}
