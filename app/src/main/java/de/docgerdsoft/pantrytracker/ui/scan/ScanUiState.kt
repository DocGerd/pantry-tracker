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

        /** A barcode was decoded; the repository is resolving it (local lookup
         *  followed by Open Food Facts if local missed). Shows a brief indicator. */
        data class Loading(val barcode: String) : Phase

        /** Resolved product (from local DB or OFF) is ready for the user to confirm
         *  with a chosen quantity. */
        data class Preview(
            val product: Product,
            val pendingQuantity: Int,
        ) : Phase

        /** Repository couldn't resolve the barcode (local miss + OFF miss / network
         *  failure / OFF hit without a product_name). User fills in a name + quantity
         *  to add the product manually, keyed to this barcode. */
        data class ManualEntry(
            val barcode: String,
            val pendingQuantity: Int,
        ) : Phase

        /** A scan/confirm operation failed (DB write, camera bind, etc.). The UI
         *  shows the message and lets the user dismiss back to Idle. Per spec §7
         *  we surface failures inline rather than swallowing them. */
        data class Error(val message: String) : Phase
    }
}
