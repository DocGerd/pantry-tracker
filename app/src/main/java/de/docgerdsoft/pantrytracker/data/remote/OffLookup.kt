package de.docgerdsoft.pantrytracker.data.remote

/**
 * Indirection seam for the OFF HTTP client so [ProductRepository] depends on a
 * small interface rather than the Ktor-bound [OffApiClient]. Tests substitute a
 * map-backed fake without spinning up a [io.ktor.client.HttpClient].
 *
 * Contract matches [OffApiClient.lookup]: returns `null` on miss / 4xx / 5xx /
 * IOException / blank barcode; throws only on `CancellationException`.
 */
interface OffLookup {
    suspend fun lookup(barcode: String): OffProduct?
}
