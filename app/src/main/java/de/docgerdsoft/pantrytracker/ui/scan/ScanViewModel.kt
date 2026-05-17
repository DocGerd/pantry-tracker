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
        if (isAlreadyShowing(barcode)) return
        lookupJob?.cancel()
        _uiState.update { it.copy(phase = ScanUiState.Phase.Loading(barcode)) }
        lookupJob = viewModelScope.launch { resolveBarcode(barcode) }
    }

    private fun isAlreadyShowing(barcode: String): Boolean =
        when (val current = _uiState.value.phase) {
            is ScanUiState.Phase.Loading -> current.barcode == barcode
            is ScanUiState.Phase.Preview -> current.product.barcode == barcode
            is ScanUiState.Phase.ManualEntry -> current.barcode == barcode
            ScanUiState.Phase.Idle, is ScanUiState.Phase.Error -> false
        }

    // Catches Exception (not Throwable — Errors should crash per spec §7). The repository
    // surface is wide (Room + OFF HTTP) and we don't want a random SQLException to crash
    // the camera screen; surfacing as Phase.Error matches spec §7 "user-facing → inline".
    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveBarcode(barcode: String) {
        val resolved = try {
            repository.lookupForPreview(barcode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    phase = ScanUiState.Phase.Error(
                        "Couldn't read inventory: ${e.message ?: "unknown error"}",
                    ),
                )
            }
            return
        }
        val newPhase = if (resolved != null) {
            ScanUiState.Phase.Preview(resolved, pendingQuantity = 1)
        } else {
            ScanUiState.Phase.ManualEntry(barcode, pendingQuantity = 1)
        }
        _uiState.update { it.copy(phase = newPhase) }
    }

    /** Used by both the Preview stepper and the ManualEntry quantity input. */
    fun setQuantity(value: Int) {
        val clamped = value.coerceAtLeast(1)
        // StateFlow.update + equals already short-circuits emission when the new
        // state equals the old one, so we don't need a manual pendingQuantity-equals
        // guard. The when only routes Preview/ManualEntry through their copy methods.
        _uiState.update { state ->
            when (val phase = state.phase) {
                is ScanUiState.Phase.Preview ->
                    state.copy(phase = phase.copy(pendingQuantity = clamped))
                is ScanUiState.Phase.ManualEntry ->
                    state.copy(phase = phase.copy(pendingQuantity = clamped))
                else -> state
            }
        }
    }

    // See resolveBarcode for the TooGenericExceptionCaught suppression rationale.
    @Suppress("TooGenericExceptionCaught")
    fun confirmAdd() {
        val phase = _uiState.value.phase as? ScanUiState.Phase.Preview ?: return
        viewModelScope.launch {
            val newPhase: ScanUiState.Phase = try {
                repository.applyDelta(productId = phase.product.id, delta = phase.pendingQuantity)
                ScanUiState.Phase.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
    // See resolveBarcode for the TooGenericExceptionCaught suppression rationale.
    @Suppress("TooGenericExceptionCaught")
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
            } catch (e: Exception) {
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
