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

        /** A barcode was decoded but no matching `Product` exists locally.
         *  Terminal state in the current scan flow; the user dismisses back to Idle.
         *  (An Open Food Facts lookup + manual-entry fallback is planned to replace
         *  this terminal state.) */
        data class UnknownBarcode(val barcode: String) : Phase

        /** A scan/confirm operation failed (DB write, camera bind, OFF lookup, etc.).
         *  The UI shows the message and lets the user dismiss back to Idle. Per spec §7
         *  we surface failures inline rather than swallowing them. */
        data class Error(val message: String) : Phase
    }
}
