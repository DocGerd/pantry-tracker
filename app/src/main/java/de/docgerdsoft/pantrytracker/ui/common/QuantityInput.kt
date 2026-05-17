package de.docgerdsoft.pantrytracker.ui.common

internal const val MAX_QUANTITY_DIGITS = 4

internal fun sanitizeQuantityInput(input: String): String =
    input.filter { it.isDigit() }.take(MAX_QUANTITY_DIGITS)
