package de.docgerdsoft.pantrytracker.data.local

import androidx.room.TypeConverter
import de.docgerdsoft.pantrytracker.data.remote.OffHost
import kotlin.time.Instant

class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? =
        value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter
    fun offHostToString(value: OffHost?): String? = value?.baseUrl

    // Loud-on-corruption by design: existing cache rows only ever hold one of
    // the four OffHost.baseUrl strings (production write path), so an unknown
    // value means a manual DB edit or a future migration bug — surface it the
    // same way OffLookupCacheEntry.init surfaces a blank name, rather than
    // silently inventing a host. The OFF cache is non-load-bearing (worst case:
    // a cache miss re-walks the chain), so a thrown read here degrades to a
    // re-fetch, not data loss.
    @TypeConverter
    fun stringToOffHost(value: String?): OffHost? =
        value?.let { stored ->
            OffHost.fromBaseUrl(stored)
                ?: throw IllegalArgumentException("Unknown OFF host in cache: $stored")
        }
}
