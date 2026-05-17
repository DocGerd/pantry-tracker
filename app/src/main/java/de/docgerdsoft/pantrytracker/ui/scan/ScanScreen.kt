package de.docgerdsoft.pantrytracker.ui.scan

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPreview
import de.docgerdsoft.pantrytracker.ui.scan.components.ErrorSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.ScanPreviewSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.UnknownBarcodeSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    // Haptic on transition into Preview/ManualEntry (i.e. each successful decode).
    LaunchedEffect(state.phase) {
        if (state.phase is ScanUiState.Phase.Preview ||
            state.phase is ScanUiState.Phase.ManualEntry
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan to Add") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            CameraPermissionGate {
                CameraPreview(
                    onBarcode = viewModel::onBarcodeDecoded,
                    onCameraError = { e ->
                        viewModel.onCameraError(e.message ?: "Camera unavailable")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            when (val phase = state.phase) {
                is ScanUiState.Phase.Preview -> {
                    ScanPreviewSheet(
                        product = phase.product,
                        pendingQuantity = phase.pendingQuantity,
                        onQuantityChange = viewModel::setQuantity,
                        onConfirm = viewModel::confirmAdd,
                        onDismiss = viewModel::dismissPreview,
                    )
                }
                is ScanUiState.Phase.ManualEntry -> {
                    // TODO(T5): replace with ManualEntrySheet; routing to UnknownBarcodeSheet
                    //  temporarily to keep compilation green until T5 implements the new sheet.
                    UnknownBarcodeSheet(
                        barcode = phase.barcode,
                        onDismiss = viewModel::dismissPreview,
                    )
                }
                is ScanUiState.Phase.Loading -> Unit // TODO(T5): add LoadingSheet
                is ScanUiState.Phase.Error -> {
                    ErrorSheet(
                        message = phase.message,
                        onDismiss = viewModel::dismissPreview,
                    )
                }
                ScanUiState.Phase.Idle -> Unit
            }
        }
    }
}
