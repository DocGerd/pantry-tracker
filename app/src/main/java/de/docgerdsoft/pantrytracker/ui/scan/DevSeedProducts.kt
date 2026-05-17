package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.datetime.Instant

/**
 * DEV-ONLY: a handful of barcoded products preloaded into the DB on first launch so
 * the scan flow has something to recognise before the Open Food Facts lookup feature
 * ships.
 *
 * DELETE this file (and the `seedDevProductsIfEmpty` call in
 * [de.docgerdsoft.pantrytracker.di.AppContainer]) before merging the OFF lookup
 * feature — the dev seed becomes redundant once unknown barcodes can be looked up
 * online.
 */
internal object DevSeedProducts {
    @Deprecated(
        message = "Remove with the Open Food Facts lookup feature",
        level = DeprecationLevel.WARNING,
    )
    fun list(now: Instant): List<Product> = listOf(
        Product(barcode = "5449000000996", name = "Coca-Cola 0.5L", brand = "Coca-Cola",
            quantity = 0, createdAt = now, updatedAt = now),
        Product(barcode = "8001505005707", name = "Spaghetti 500g", brand = "Barilla",
            quantity = 0, createdAt = now, updatedAt = now),
        Product(barcode = "4006381333931", name = "Sparkling Water 1L", brand = "Gerolsteiner",
            quantity = 0, createdAt = now, updatedAt = now),
    )
}
