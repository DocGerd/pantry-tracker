package de.docgerdsoft.pantrytracker.ui.detail

import de.docgerdsoft.pantrytracker.data.local.Product

data class DetailUiState(
    val product: Product? = null,
    /** Set true when the row is either gone at construction time (stale nav arg)
     *  or observed to disappear after a successful precheck (delete-while-open).
     *  The screen consumes this once via [DetailViewModel.onNavigatedBack] to
     *  trigger NavController.popBackStack(). */
    val shouldNavigateBack: Boolean = false,
    /** True while the trash-confirm dialog is open. */
    val showDeleteConfirm: Boolean = false,
    /** A user-facing error message from the last failed repository operation
     *  (observe, rename, stepperDelta, confirmDelete). Rendered as a Snackbar;
     *  consumed once via [DetailViewModel.dismissError]. null when no error
     *  is pending. Mirrors ScanViewModel's Phase.Error per spec §7 "user-
     *  facing → inline". */
    val error: String? = null,
) {
    init {
        // Lift the producer-side invariant to runtime. If the row is gone
        // (shouldNavigateBack = true), product must also be cleared — otherwise
        // a future refactor could leave a stale Product hanging in state while
        // the screen pops, briefly showing the wrong row on a recompose.
        require(!shouldNavigateBack || product == null) {
            "shouldNavigateBack implies product == null (got product=$product)"
        }
    }
}
