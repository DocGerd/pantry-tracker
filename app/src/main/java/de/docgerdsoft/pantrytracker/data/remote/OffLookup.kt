package de.docgerdsoft.pantrytracker.data.remote

/**
 * Indirection seam for the OFF HTTP client so [ProductRepository] depends on a
 * small interface rather than the Ktor-bound [OffApiClient]. Tests substitute a
 * map-backed fake without spinning up a [io.ktor.client.HttpClient].
 *
 * Contract matches [OffApiClient.lookup]: returns `null` on miss / 4xx / 5xx /
 * IOException / blank barcode; on a hit returns an [OffLookupResult] carrying
 * both the resolved [OffProduct] and the OFF host that served it (used by the
 * v1.2 lookup cache, #48). Throws only on `CancellationException`.
 */
interface OffLookup {
    suspend fun lookup(barcode: String): OffLookupResult?
}
