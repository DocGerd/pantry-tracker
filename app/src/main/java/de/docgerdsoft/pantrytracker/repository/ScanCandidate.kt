package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product

/**
 * What `ProductRepository.lookupForPreview` returns — a barcode resolved to either an
 * already-persisted Room row (confirm = applyDelta) or a fresh OFF-sourced candidate
 * that hasn't been persisted yet (confirm = addNew). Splitting these two so the
 * confirm path can't accidentally treat an unpersisted candidate as a persisted row
 * (which used to silently no-op via dao.findById(0) returning null).
 */
sealed interface ScanCandidate {
    val barcode: String
    val name: String
    val brand: String?
    val imageUrl: String?

    /** Already in the local DB. confirmAdd → applyDelta(product.id, +N). */
    data class Persisted(val product: Product) : ScanCandidate {
        override val barcode get() = product.barcode!!
        override val name get() = product.name
        override val brand get() = product.brand
        override val imageUrl get() = product.imageUrl
    }

    /** Resolved via OFF, not yet persisted. confirmAdd → addNew(name, brand, barcode, imageUrl, +N). */
    data class FromOff(
        override val barcode: String,
        override val name: String,
        override val brand: String?,
        override val imageUrl: String?,
    ) : ScanCandidate
}
