package de.docgerdsoft.pantrytracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ScanCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel(
    private val repository: ProductRepository,
    initialMode: ScanMode = ScanMode.Add,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState(mode = initialMode))
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var lookupJob: Job? = null
    private var confirmJob: Job? = null
    private var manualEntryJob: Job? = null

    /**
     * Dispatch a barcode to the repository for resolution. De-duplicates the same
     * barcode while it's already being shown (Loading/Preview/ManualEntry); cancels
     * any prior in-flight lookup when a new barcode arrives. Blank barcodes (which
     * ML Kit can surface from low-confidence frames) are dropped.
     *
     * Spec §6.2 decision matrix: local hit → Preview (Persisted); local miss + OFF
     * hit → Preview (FromOff); local miss + OFF miss / failure → ManualEntry.
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
            is ScanUiState.Phase.Preview -> current.candidate.barcode == barcode
            is ScanUiState.Phase.ManualEntry -> current.barcode == barcode
            is ScanUiState.Phase.NotInInventory -> current.barcode == barcode
            ScanUiState.Phase.Idle, is ScanUiState.Phase.Error -> false
        }

    // Catches Exception (not Throwable — Errors should crash per spec §7). The repository
    // surface is wide (Room + OFF HTTP) and we don't want a random SQLException to crash
    // the camera screen; surfacing as Phase.Error matches spec §7 "user-facing → inline".
    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveBarcode(barcode: String) {
        val mode = _uiState.value.mode
        val newPhase: ScanUiState.Phase = try {
            when (mode) {
                ScanMode.Add -> {
                    val resolved = repository.lookupForPreview(barcode)
                    if (resolved != null) ScanUiState.Phase.Preview(resolved, pendingQuantity = 1)
                    else ScanUiState.Phase.ManualEntry(barcode, pendingQuantity = 1)
                }
                ScanMode.Remove -> {
                    val local = repository.findLocalByBarcode(barcode)
                    if (local != null) {
                        ScanUiState.Phase.Preview(
                            ScanCandidate.Persisted(local),
                            pendingQuantity = 1,
                        )
                    } else {
                        ScanUiState.Phase.NotInInventory(barcode)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ScanUiState.Phase.Error("Couldn't read inventory: ${e.message ?: "unknown error"}")
        }
        _uiState.update { state ->
            val owns = (state.phase as? ScanUiState.Phase.Loading)?.barcode == barcode
            if (owns) state.copy(phase = newPhase) else state
        }
    }

    /** Used by both the Preview stepper and the ManualEntry quantity input. */
    fun setQuantity(value: Int) {
        // StateFlow.update + equals already short-circuits emission when the new
        // state equals the old one, so we don't need a manual pendingQuantity-equals
        // guard. The when only routes Preview/ManualEntry through their copy methods.
        _uiState.update { state ->
            when (val phase = state.phase) {
                is ScanUiState.Phase.Preview -> {
                    val max = if (state.mode == ScanMode.Remove) {
                        (phase.candidate as? ScanCandidate.Persisted)?.product?.quantity ?: Int.MAX_VALUE
                    } else Int.MAX_VALUE
                    val clamped = value.coerceIn(1, max.coerceAtLeast(1))
                    state.copy(phase = phase.copy(pendingQuantity = clamped))
                }
                is ScanUiState.Phase.ManualEntry ->
                    state.copy(phase = phase.copy(pendingQuantity = value.coerceAtLeast(1)))
                else -> state
            }
        }
    }

    // Catches Exception (not Throwable): a Room SQLException from applyDelta or addNew
    // (disk full, DB corruption, constraint violation) must surface as Phase.Error per
    // spec §7 rather than propagate to the camera screen.
    @Suppress("TooGenericExceptionCaught")
    fun confirm() {
        val state = _uiState.value
        val phase = state.phase as? ScanUiState.Phase.Preview ?: return
        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            val newPhase: ScanUiState.Phase = try {
                when (state.mode) {
                    ScanMode.Add -> when (val c = phase.candidate) {
                        is ScanCandidate.Persisted -> repository.applyDelta(c.product.id, phase.pendingQuantity)
                        is ScanCandidate.FromOff -> repository.addNew(
                            name = c.name,
                            brand = c.brand,
                            barcode = c.barcode,
                            imageUrl = c.imageUrl,
                            initialQuantity = phase.pendingQuantity,
                        )
                    }
                    ScanMode.Remove -> {
                        val persisted = phase.candidate as? ScanCandidate.Persisted ?: return@launch
                        repository.applyDelta(persisted.product.id, -phase.pendingQuantity)
                    }
                }
                ScanUiState.Phase.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanUiState.Phase.Error("Couldn't save: ${e.message ?: "unknown error"}")
            }
            _uiState.update { s ->
                // Phase-ownership guard: don't clobber a fresh phase that arrived
                // while this confirm was in-flight (e.g. another scan started).
                if (s.phase === phase) s.copy(phase = newPhase) else s
            }
        }
    }

    /** From the NotInInventory phase: flip mode to Add and re-resolve the captured barcode. */
    fun onSwitchToAdd() {
        val phase = _uiState.value.phase as? ScanUiState.Phase.NotInInventory ?: return
        _uiState.update { it.copy(mode = ScanMode.Add) }
        onBarcodeDecoded(phase.barcode)
    }

    /**
     * Insert a brand-new product keyed to the manually-entered barcode. Validates
     * non-blank name + positive quantity; silently no-ops on invalid input (the sheet
     * stays open). On DB failure transitions to Error per spec §7.
     */
    // Catches Exception (not Throwable): a Room SQLException from addNew (disk full,
    // DB corruption, constraint violation) must surface as Phase.Error per spec §7
    // rather than propagate to the camera screen.
    @Suppress("TooGenericExceptionCaught")
    fun submitManualEntry(name: String, initialQuantity: Int) {
        val phase = _uiState.value.phase as? ScanUiState.Phase.ManualEntry ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || initialQuantity <= 0) return
        manualEntryJob?.cancel()
        manualEntryJob = viewModelScope.launch {
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
            _uiState.update { state ->
                // Phase-ownership guard: don't clobber a fresh phase that arrived
                // while this manual-entry submit was in-flight.
                if (state.phase === phase) state.copy(phase = newPhase) else state
            }
        }
    }

    /** Dismiss any non-Idle phase back to Idle; cancels all in-flight jobs. */
    fun dismissPreview() {
        lookupJob?.cancel()
        confirmJob?.cancel()
        manualEntryJob?.cancel()
        _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
    }

    /** Called by CameraPreview when the camera or scanner permanently fails. */
    fun onCameraError(message: String) {
        lookupJob?.cancel()
        confirmJob?.cancel()
        manualEntryJob?.cancel()
        _uiState.update { it.copy(phase = ScanUiState.Phase.Error(message)) }
    }
}
