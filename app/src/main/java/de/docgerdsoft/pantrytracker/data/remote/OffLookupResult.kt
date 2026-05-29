package de.docgerdsoft.pantrytracker.data.remote

/**
 * What `OffLookup.lookup` returns on success — the resolved [OffProduct]
 * together with the OFF host that served it. The host is captured for the
 * v1.2 lookup cache (#48): re-scans of cached barcodes short-circuit the
 * 4-host fallback chain, and the host is also a useful diagnostic.
 *
 * `OffProduct` stays a pure DTO of the OFF JSON schema; `resolvingHost` is
 * lookup-time metadata that belongs alongside the product, not inside it.
 *
 * `resolvingHost` is typed as [OffHost] (#61) so only the four known hosts
 * are representable — the compiler enforces what KDoc + a runtime loop
 * previously expressed by convention.
 */
data class OffLookupResult(
    val product: OffProduct,
    val resolvingHost: OffHost,
)
