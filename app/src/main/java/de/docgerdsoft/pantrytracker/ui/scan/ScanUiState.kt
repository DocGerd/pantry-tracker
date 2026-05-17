package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.data.local.Product

/** UI state for the Scan screen. The phase is a small sealed hierarchy modelling
 *  the scan → decode → confirm flow. */
data class ScanUiState(
    val phase: Phase = Phase.Idle,
) {
    sealed interface Phase {
        /** Camera is open, waiting for a barcode. */
        data object Idle : Phase

        /** A barcode was decoded and matched a row in the local DB. */
        data class Preview(
            val product: Product,
            val pendingQuantity: Int,
        ) : Phase

        /** A barcode was decoded but is not in the local DB.
         *  For M2 this is the terminal "Not seeded" state. M3 replaces this with
         *  the Open Food Facts lookup + manual-entry fallback flow. */
        data class UnknownBarcode(val barcode: String) : Phase
    }
}
