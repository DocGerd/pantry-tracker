package de.docgerdsoft.pantrytracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /**
     * De-duplicates rapid repeat decodes by ignoring any callback while a
     * Preview/UnknownBarcode/Error sheet is open. Empty/blank barcodes (which ML Kit
     * can occasionally surface from low-confidence frames) are also dropped. Otherwise
     * looks up the barcode in the local DB and transitions to Preview (match) or
     * UnknownBarcode (miss). On DB failure transitions to Error per spec §7.
     */
    fun onBarcodeDecoded(barcode: String) {
        if (barcode.isBlank()) return
        if (_uiState.value.phase !is ScanUiState.Phase.Idle) return
        viewModelScope.launch {
            val newPhase: ScanUiState.Phase = runCatching {
                repository.findLocalByBarcode(barcode)
            }.fold(
                onSuccess = { product ->
                    if (product != null) {
                        ScanUiState.Phase.Preview(product, pendingQuantity = 1)
                    } else {
                        ScanUiState.Phase.UnknownBarcode(barcode)
                    }
                },
                onFailure = { e ->
                    ScanUiState.Phase.Error("Couldn't read inventory: ${e.message ?: "unknown error"}")
                },
            )
            _uiState.update { it.copy(phase = newPhase) }
        }
    }

    fun setQuantity(value: Int) {
        val phase = _uiState.value.phase as? ScanUiState.Phase.Preview ?: return
        val clamped = value.coerceAtLeast(1)
        if (clamped == phase.pendingQuantity) return
        _uiState.update { it.copy(phase = phase.copy(pendingQuantity = clamped)) }
    }

    fun confirmAdd() {
        val phase = _uiState.value.phase as? ScanUiState.Phase.Preview ?: return
        viewModelScope.launch {
            val outcome = runCatching {
                repository.applyDelta(productId = phase.product.id, delta = phase.pendingQuantity)
            }
            val newPhase: ScanUiState.Phase = outcome.fold(
                onSuccess = { ScanUiState.Phase.Idle },
                onFailure = { e ->
                    ScanUiState.Phase.Error("Couldn't save: ${e.message ?: "unknown error"}")
                },
            )
            _uiState.update { it.copy(phase = newPhase) }
        }
    }

    /** Dismiss any non-Idle phase (Preview, UnknownBarcode, or Error) back to Idle. */
    fun dismissPreview() {
        _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
    }

    /** Called by CameraPreview when the camera or scanner permanently fails. */
    fun onCameraError(message: String) {
        _uiState.update { it.copy(phase = ScanUiState.Phase.Error(message)) }
    }
}
