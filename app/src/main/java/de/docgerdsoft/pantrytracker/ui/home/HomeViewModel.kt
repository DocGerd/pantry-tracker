package de.docgerdsoft.pantrytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showAddSheet = MutableStateFlow(false)
    private val pendingDelete = MutableStateFlow<Product?>(null)

    private val productsFlow = query
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) repository.observeProducts() else repository.search(q.trim())
        }

    val uiState: StateFlow<HomeUiState> = combine(
        query, productsFlow, showAddSheet, pendingDelete,
    ) { q, products, sheet, pending ->
        HomeUiState(
            query = q,
            products = products,
            showAddSheet = sheet,
            pendingDelete = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    fun setQuery(q: String) {
        query.value = q
    }

    fun openAddSheet() {
        showAddSheet.value = true
    }

    fun dismissAddSheet() {
        showAddSheet.value = false
    }

    fun submitAdd(name: String, initialQuantity: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || initialQuantity <= 0) return
        viewModelScope.launch {
            repository.addNew(name = trimmed, initialQuantity = initialQuantity)
        }
        showAddSheet.value = false
    }

    fun requestDelete(product: Product) {
        pendingDelete.value = product
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        viewModelScope.launch {
            repository.delete(target.id)
        }
        pendingDelete.value = null
    }
}
