package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.datetime.Instant

/**
 * DEV-ONLY: a handful of barcoded products preloaded into the DB on first launch so
 * M2 scan flow has something to recognise without M3's Open Food Facts lookup.
 *
 * DELETE this file (and the `seedDevProductsIfEmpty` call in
 * [de.docgerdsoft.pantrytracker.di.AppContainer]) as the first step of Milestone 3.
 */
internal object DevSeedProducts {
    fun list(now: Instant): List<Product> = listOf(
        Product(barcode = "5449000000996", name = "Coca-Cola 0.5L", brand = "Coca-Cola",
            quantity = 0, createdAt = now, updatedAt = now),
        Product(barcode = "8001505005707", name = "Spaghetti 500g", brand = "Barilla",
            quantity = 0, createdAt = now, updatedAt = now),
        Product(barcode = "4006381333931", name = "Sparkling Water 1L", brand = "Gerolsteiner",
            quantity = 0, createdAt = now, updatedAt = now),
    )
}
