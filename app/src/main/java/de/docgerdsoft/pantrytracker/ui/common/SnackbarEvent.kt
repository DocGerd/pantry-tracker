package de.docgerdsoft.pantrytracker.ui.common

import de.docgerdsoft.pantrytracker.data.local.Product

/**
 * One-shot UI events from a ViewModel that drive a bottom snackbar.
 *
 * Carried over a `kotlinx.coroutines.channels.Channel` (not a `StateFlow`)
 * so a recomposition doesn't accidentally re-fire the snackbar for an event
 * that already played. Each event is delivered to exactly one collector,
 * which mirrors Material 3's "show once" snackbar semantics.
 *
 * Lives in `ui/common` rather than `ui/home` because the shape is screen-agnostic
 * and the failure variants are reusable from any feature that wants the same
 * pattern (detail-screen rename, scan-confirm, etc.).
 */
sealed interface SnackbarEvent {
    /**
     * Emitted after a successful delete. The captured [product] is what UNDO
     * will restore — it carries every field of the original row (including
     * `id`, `createdAt`, and `updatedAt`), so the post-undo row is
     * identity-equal to the pre-delete one (see `ProductRepository.restore`).
     */
    data class Deleted(val product: Product) : SnackbarEvent

    /**
     * Emitted when the actual `repository.delete` call threw (non-cancellation).
     * Carries the user-visible [name] so the collector can render
     * `"Could not delete <name>"`. The pending-delete dialog has already been
     * cleared by the time this fires — the UI must not be left in a
     * half-confirmed state on failure.
     */
    data class DeleteFailed(val name: String) : SnackbarEvent

    /**
     * Emitted when the actual `repository.restore` call (invoked from the
     * snackbar UNDO action) threw (non-cancellation). Carries the user-visible
     * [name] so the collector can render `"Could not undo delete of <name>"`.
     * The row stays deleted — UNDO is best-effort and we tell the user instead
     * of silently failing.
     */
    data class RestoreFailed(val name: String) : SnackbarEvent
}
