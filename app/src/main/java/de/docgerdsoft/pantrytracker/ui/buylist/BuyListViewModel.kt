package de.docgerdsoft.pantrytracker.ui.buylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.data.local.Product
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
// unit tests — matches DetailViewModel / ScanViewModel.
private val logger: Logger = Logger.getLogger("BuyListViewModel")

class BuyListViewModel(private val repository: ProductRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BuyListUiState())
    val uiState: StateFlow<BuyListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                repository.observeBuyingList().collect { items ->
                    _uiState.update { it.copy(items = items) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                surfaceError("load the buying list", e)
            }
        }
    }

    /** One-tap restock: add the item's [Product.defaultBuyAmount] to stock,
     *  reusing the existing clamp-and-stamp [ProductRepository.applyDelta]. The
     *  item then naturally falls off the buying list as quantity rises. */
    @Suppress("TooGenericExceptionCaught")
    fun onBought(product: Product) {
        viewModelScope.launch {
            try {
                repository.applyDelta(product.id, product.defaultBuyAmount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                surfaceError("restock", e)
            }
        }
    }

    /** Called by the screen's Snackbar after the user has seen the error. */
    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun surfaceError(operation: String, e: Exception) {
        @Suppress("SwallowedException")
        logger.log(Level.WARNING, "$operation failed", e)
        _uiState.update { it.copy(error = "Couldn't $operation: ${e.message ?: "unknown error"}") }
    }
}
