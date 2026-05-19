package de.docgerdsoft.pantrytracker.ui.home

import de.docgerdsoft.pantrytracker.data.local.Product

/**
 * One-shot UI events from [HomeViewModel] that drive the bottom snackbar.
 *
 * Carried over a `kotlinx.coroutines.channels.Channel` (not a `StateFlow`)
 * so a recomposition doesn't accidentally re-fire the snackbar for an event
 * that already played. Each event is delivered to exactly one collector,
 * which mirrors Material 3's "show once" snackbar semantics.
 */
sealed interface SnackbarEvent {
    /**
     * Emitted after a successful delete. The captured [product] is what UNDO
     * will restore — it carries the original `id`, `createdAt`, and
     * `updatedAt`, so the post-undo row is identity-equal to the pre-delete
     * one (see `ProductRepository.restore`).
     */
    data class Deleted(val product: Product) : SnackbarEvent
}
