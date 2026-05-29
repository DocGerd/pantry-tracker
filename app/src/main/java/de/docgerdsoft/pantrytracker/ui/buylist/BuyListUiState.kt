package de.docgerdsoft.pantrytracker.ui.buylist

import de.docgerdsoft.pantrytracker.data.local.Product

data class BuyListUiState(
    val items: List<Product> = emptyList(),
    /** A user-facing error from the last failed repository operation (load /
     *  restock). Rendered as a Snackbar; consumed once via
     *  [BuyListViewModel.dismissError]. Always opens with `"Couldn't "`. */
    val error: String? = null,
)
