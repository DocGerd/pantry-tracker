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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPreview
import de.docgerdsoft.pantrytracker.ui.scan.components.ErrorSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.LoadingSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.ManualEntrySheet
import de.docgerdsoft.pantrytracker.ui.scan.components.NotInInventorySheet
import de.docgerdsoft.pantrytracker.ui.scan.components.ScanPreviewSheet
import de.docgerdsoft.pantrytracker.ui.theme.AddGreen
import de.docgerdsoft.pantrytracker.ui.theme.RemoveRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    val topBarColor = if (state.mode == ScanMode.Add) AddGreen else RemoveRed
    val topBarTitle = if (state.mode == ScanMode.Add) "Scan to Add" else "Scan to Remove"

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
