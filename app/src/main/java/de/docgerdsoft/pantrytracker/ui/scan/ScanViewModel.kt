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

    /** Called from the camera analyzer when ML Kit successfully decodes a barcode. */
    fun onBarcodeDecoded(barcode: String) {
        if (_uiState.value.phase !is ScanUiState.Phase.Idle) return
        viewModelScope.launch {
            val product = repository.findLocalByBarcode(barcode)
            _uiState.update {
                it.copy(
                    phase = if (product != null) {
                        ScanUiState.Phase.Preview(product, pendingQuantity = 1)
                    } else {
                        ScanUiState.Phase.UnknownBarcode(barcode)
                    },
                )
            }
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
            repository.applyDelta(productId = phase.product.id, delta = phase.pendingQuantity)
            _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
        }
    }

    /** Used by both the Cancel button on the preview sheet AND the "Not in inventory" close button. */
    fun dismissPreview() {
        _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
    }
}
