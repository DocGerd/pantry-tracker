package de.docgerdsoft.pantrytracker.ui.detail

import de.docgerdsoft.pantrytracker.data.local.Product

data class DetailUiState(
    val product: Product? = null,
    /** True once observeById has emitted a non-null Product at least once. Used to
     *  distinguish initial-loading null from deleted null (the latter triggers nav-back). */
    val everSeen: Boolean = false,
    /** Set true when the row has been observed to disappear (deleted). The screen
     *  consumes this once to trigger NavController.popBackStack(). */
    val shouldNavigateBack: Boolean = false,
    /** True while the trash-confirm dialog is open. */
    val showDeleteConfirm: Boolean = false,
)
