package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.repository.ScanCandidate

/** UI state for the Scan screen. The phase is a small sealed hierarchy modelling
 *  the scan → decode → confirm flow. */
data class ScanUiState(
    val mode: ScanMode = ScanMode.Add,
    val phase: Phase = Phase.Idle,
) {
    init {
        // NotInInventory is a Remove-mode-only phase per spec D4. Producer-side
        // discipline (resolveBarcode) is the primary enforcement; this require()
        // catches any future contributor who accidentally emits it from Add mode.
        require(phase !is Phase.NotInInventory || mode == ScanMode.Remove) {
            "NotInInventory phase is Remove-mode only (was mode=$mode)"
        }
    }

    sealed interface Phase {
        /** Camera is open, waiting for a barcode. */
        data object Idle : Phase

        /** A barcode was decoded; the repository is resolving it (local lookup
         *  followed by Open Food Facts if local missed). Shows a brief indicator. */
        data class Loading(val barcode: String) : Phase

        /** Resolved candidate (from local DB or OFF) is ready for the user to confirm
         *  with a chosen quantity. The candidate carries the display info (name, brand,
         *  imageUrl) and encodes whether to call applyDelta (Persisted) or addNew
         *  (FromOff) at confirm time. */
        data class Preview(
            val candidate: ScanCandidate,
            val pendingQuantity: Int,
        ) : Phase

        /** Repository couldn't resolve the barcode (local miss + OFF miss / network
         *  failure / OFF hit without a product_name). User fills in a name + quantity
         *  to add the product manually, keyed to this barcode. */
        data class ManualEntry(
            val barcode: String,
            val pendingQuantity: Int,
        ) : Phase

        /** In Remove mode: the scanned barcode either has no row in the local
         *  inventory OR has a row whose quantity is already 0 (depleted). Either way
         *  there's nothing to remove, so the UI surfaces a one-tap Switch-to-Add path
         *  (re-resolves the same barcode through the Add flow) or dismiss to Idle. */
        data class NotInInventory(val barcode: String) : Phase

        /** A scan/confirm operation failed (DB write, camera bind, etc.). The UI
         *  shows the message and lets the user dismiss back to Idle. Per spec §7
         *  we surface failures inline rather than swallowing them. */
        data class Error(val message: String) : Phase
    }
}
