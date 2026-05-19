package de.docgerdsoft.pantrytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showAddSheet = MutableStateFlow(false)
    private val pendingDelete = MutableStateFlow<Product?>(null)

    // BUFFERED: a back-to-back delete burst must not drop snackbar events —
    // see HomeViewModelTest.confirmDelete_twice_emitsTwoSeparateEvents_noDrop.
    // The screen drains synchronously in a LaunchedEffect, so the buffer
    // depth only matters during VM init / config-change races.
    private val snackbarEventsChannel = Channel<SnackbarEvent>(Channel.BUFFERED)

    /**
     * One-shot snackbar trigger stream. Collect inside a [LaunchedEffect]
     * in the host composable.
     *
     * Channel-backed (not [StateFlow]) so recomposition doesn't accidentally
     * re-fire the snackbar for an event that already played. Each event
     * arrives at exactly one collector — matches Material 3's "show once"
     * snackbar semantics and the documented edge case where a second delete
     * during the snackbar window dismisses the previous snackbar (the first
     * UNDO closure is unreachable, the first deletion stays final).
     */
    val snackbarEvents: Flow<SnackbarEvent> = snackbarEventsChannel.receiveAsFlow()

    private val productsFlow = query.flatMapLatest { q ->
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
        started = SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MILLIS),
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
            showAddSheet.value = false
        }
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
            pendingDelete.value = null
            // Emit AFTER delete completes so a UNDO tap can rely on the row
            // being gone — restore() then re-inserts under the original id.
            snackbarEventsChannel.send(SnackbarEvent.Deleted(target))
        }
    }

    /**
     * Restore a previously-deleted [product] by id+all-fields. Called from the
     * snackbar's UNDO action; the captured instance is the one
     * [SnackbarEvent.Deleted] carried, so timestamps round-trip unchanged.
     */
    fun undoDelete(product: Product) {
        viewModelScope.launch {
            repository.restore(product)
        }
    }
}
