package de.docgerdsoft.pantrytracker.ui.buylist

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.ui.common.UiText

data class BuyListUiState(
    val items: List<Product> = emptyList(),
    /** A user-facing error from the last failed repository operation (load /
     *  restock). Rendered as a Snackbar; consumed once via
     *  [BuyListViewModel.dismissError]. Its English (source) resolution opens with
     *  `"Couldn't "` per the SR-78 error-tone convention; localized resolutions
     *  follow the locale's idiom. */
    val error: UiText? = null,
)
