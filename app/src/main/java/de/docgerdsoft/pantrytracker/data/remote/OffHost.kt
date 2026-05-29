package de.docgerdsoft.pantrytracker.data.remote

/**
 * The four Open Food Facts sister hosts the lookup chain walks, in priority
 * order (#61). Encoding the host set as an enum removes the "bogus host string"
 * failure mode that the previous `List<String>` allowed: a `resolvingHost` can
 * now only ever be one of these four values, enforced by the compiler instead
 * of by KDoc + a runtime loop.
 *
 * `baseUrl` is the canonical trailing-slash form `URLBuilder().takeFrom(...)`
 * expects. [fromBaseUrl] is the inverse, used by the Room `TypeConverter` to
 * rehydrate cache rows persisted under the older string schema (the stored
 * value is the baseUrl, so no data migration is needed — see
 * `Converters.stringToOffHost`).
 *
 * Because the stored value IS `baseUrl`, this literal doubles as the on-disk
 * cache key, not just a request URL: editing a `baseUrl` string is a
 * persistence-format change that orphans every existing cache row for that host
 * (they become "unknown host" and degrade to a re-fetch via
 * `Converters.stringToOffHost`). Treat a URL edit as a schema change, not a typo
 * fix.
 */
enum class OffHost(val baseUrl: String) {
    FOOD("https://world.openfoodfacts.org/"),
    BEAUTY("https://world.openbeautyfacts.org/"),
    PET_FOOD("https://world.openpetfoodfacts.org/"),
    PRODUCTS("https://world.openproductsfacts.org/"),
    ;

    companion object {
        /** Inverse of [baseUrl]; null for any string outside the known set. */
        fun fromBaseUrl(url: String): OffHost? = entries.firstOrNull { it.baseUrl == url }
    }
}
