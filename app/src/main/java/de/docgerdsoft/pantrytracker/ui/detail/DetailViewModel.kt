package de.docgerdsoft.pantrytracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

// java.util.logging works in both Android (forwarded to logcat) and plain JVM
// unit tests (writes to System.err) — avoids android.util.Log throwing
// "Method X not mocked" in non-Robolectric tests.
private val logger: Logger = Logger.getLogger("DetailViewModel")

class DetailViewModel(
    private val repository: ProductRepository,
    private val productId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { initialize() }
    }

    // Spec D2: stale nav arg must auto-pop. A one-shot findById precheck
    // distinguishes "row never existed" (pop immediately) from "row exists,
    // start watching" (any subsequent null = deletion → pop). Drops the
    // previous `everSeen` flag — after the precheck succeeds, any null from
    // observeById is unambiguously a deletion event.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun initialize() {
        val initial = try {
            repository.findById(productId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "findById($productId) precheck failed", e)
            _uiState.update { it.copy(shouldNavigateBack = true) }
            return
        }
        if (initial == null) {
            _uiState.update { it.copy(shouldNavigateBack = true) }
            return
        }
        _uiState.update { it.copy(product = initial) }
        collectProduct()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun collectProduct() {
        try {
            repository.observeById(productId).collect { product ->
                _uiState.update { state ->
                    if (product != null) {
                        state.copy(product = product)
                    } else {
                        state.copy(product = null, shouldNavigateBack = true)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "observeById($productId) failed", e)
        }
    }

    /** Called when the screen has navigated away (Compose consumed the signal). */
    fun onNavigatedBack() {
        _uiState.update { it.copy(shouldNavigateBack = false) }
    }

    @Suppress("TooGenericExceptionCaught")
    fun rename(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.rename(productId, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "rename($productId) failed", e)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun stepperDelta(delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            try {
                repository.applyDelta(productId, delta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "applyDelta($productId, $delta) failed", e)
            }
        }
    }

    fun requestDelete() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    @Suppress("TooGenericExceptionCaught")
    fun confirmDelete() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
        viewModelScope.launch {
            try {
                repository.delete(productId)
                // observeById emits null → state.everSeen is true → shouldNavigateBack = true.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "delete($productId) failed", e)
            }
        }
    }
}
