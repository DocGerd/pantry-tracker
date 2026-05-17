package de.docgerdsoft.pantrytracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private var lookupJob: Job? = null

    /**
     * Dispatch a barcode to the repository for resolution. De-duplicates the same
     * barcode while it's already being shown (Loading/Preview/ManualEntry); cancels
     * any prior in-flight lookup when a new barcode arrives. Blank barcodes (which
     * ML Kit can surface from low-confidence frames) are dropped.
     *
     * Spec §6.2 decision matrix: local hit → Preview from row; local miss + OFF hit
     * → Preview with id=0; local miss + OFF miss / failure → ManualEntry.
     */
    fun onBarcodeDecoded(barcode: String) {
        if (barcode.isBlank()) return
        when (val current = _uiState.value.phase) {
            is ScanUiState.Phase.Loading -> if (current.barcode == barcode) return
            is ScanUiState.Phase.Preview -> if (current.product.barcode == barcode) return
            is ScanUiState.Phase.ManualEntry -> if (current.barcode == barcode) return
            ScanUiState.Phase.Idle, is ScanUiState.Phase.Error -> Unit
        }
        lookupJob?.cancel()
        _uiState.update { it.copy(phase = ScanUiState.Phase.Loading(barcode)) }
        lookupJob = viewModelScope.launch {
            // try/catch instead of runCatching — runCatching swallows
            // CancellationException, which would race with dismissPreview()'s
            // Idle write and stamp Phase.Error on top of a dismissed sheet.
            val resolved = try {
                repository.lookupForPreview(barcode)
            } catch (e: CancellationException) {
                throw e  // structured concurrency: let cancellation propagate
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        phase = ScanUiState.Phase.Error(
                            "Couldn't read inventory: ${e.message ?: "unknown error"}",
                        ),
                    )
                }
                return@launch
            }
            val newPhase = if (resolved != null) {
                ScanUiState.Phase.Preview(resolved, pendingQuantity = 1)
            } else {
                ScanUiState.Phase.ManualEntry(barcode, pendingQuantity = 1)
            }
            _uiState.update { it.copy(phase = newPhase) }
        }
    }

    /** Used by both the Preview stepper and the ManualEntry quantity input. */
    fun setQuantity(value: Int) {
        val clamped = value.coerceAtLeast(1)
        _uiState.update { state ->
            when (val phase = state.phase) {
                is ScanUiState.Phase.Preview ->
                    if (phase.pendingQuantity == clamped) state
                    else state.copy(phase = phase.copy(pendingQuantity = clamped))
                is ScanUiState.Phase.ManualEntry ->
                    if (phase.pendingQuantity == clamped) state
                    else state.copy(phase = phase.copy(pendingQuantity = clamped))
                else -> state
            }
        }
    }

    fun confirmAdd() {
        val phase = _uiState.value.phase as? ScanUiState.Phase.Preview ?: return
        viewModelScope.launch {
            val newPhase: ScanUiState.Phase = try {
                repository.applyDelta(productId = phase.product.id, delta = phase.pendingQuantity)
                ScanUiState.Phase.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ScanUiState.Phase.Error("Couldn't save: ${e.message ?: "unknown error"}")
            }
            _uiState.update { it.copy(phase = newPhase) }
        }
    }

    /**
     * Insert a brand-new product keyed to the manually-entered barcode. Validates
     * non-blank name + positive quantity; silently no-ops on invalid input (the sheet
     * stays open). On DB failure transitions to Error per spec §7.
     */
    fun submitManualEntry(name: String, initialQuantity: Int) {
        val phase = _uiState.value.phase as? ScanUiState.Phase.ManualEntry ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || initialQuantity <= 0) return
        viewModelScope.launch {
            val newPhase: ScanUiState.Phase = try {
                repository.addNew(
                    name = trimmed,
                    brand = null,
                    barcode = phase.barcode,
                    imageUrl = null,
                    initialQuantity = initialQuantity,
                )
                ScanUiState.Phase.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ScanUiState.Phase.Error("Couldn't save: ${e.message ?: "unknown error"}")
            }
            _uiState.update { it.copy(phase = newPhase) }
        }
    }

    /** Dismiss any non-Idle phase back to Idle; cancels any in-flight lookup. */
    fun dismissPreview() {
        lookupJob?.cancel()
        _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
    }

    /** Called by CameraPreview when the camera or scanner permanently fails. */
    fun onCameraError(message: String) {
        _uiState.update { it.copy(phase = ScanUiState.Phase.Error(message)) }
    }
}
