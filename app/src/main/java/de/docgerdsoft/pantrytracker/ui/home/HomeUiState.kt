package de.docgerdsoft.pantrytracker.ui.home

import de.docgerdsoft.pantrytracker.data.local.Product

data class HomeUiState(
    val query: String = "",
    val products: List<Product> = emptyList(),
    val showAddSheet: Boolean = false,
    val pendingDelete: Product? = null,
)
