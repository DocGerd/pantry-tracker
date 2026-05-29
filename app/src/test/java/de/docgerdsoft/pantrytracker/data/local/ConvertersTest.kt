package de.docgerdsoft.pantrytracker.data.local

import de.docgerdsoft.pantrytracker.data.remote.OffHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Instant

/**
 * Pure-JVM coverage for the Room [Converters] type-converter pair. The DAO
 * integration tests exercise these only indirectly through Robolectric, which
 * does not attribute JaCoCo coverage (sandbox classloader bypasses the
 * on-the-fly agent), so a plain unit test is the only thing that records them.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun instantToEpochMillis_roundTripsThroughEpochMilliseconds() {
        val instant = Instant.fromEpochMilliseconds(1_700_000_000_123L)
        assertEquals(1_700_000_000_123L, converters.instantToEpochMillis(instant))
    }

    @Test
    fun instantToEpochMillis_null_returnsNull() {
        assertNull(converters.instantToEpochMillis(null))
    }

    @Test
    fun epochMillisToInstant_reconstructsTheSameInstant() {
        assertEquals(
            Instant.fromEpochMilliseconds(42L),
            converters.epochMillisToInstant(42L),
        )
    }

    @Test
    fun epochMillisToInstant_null_returnsNull() {
        assertNull(converters.epochMillisToInstant(null))
    }

    @Test
    fun instant_survivesFullRoundTrip() {
        val original = Instant.fromEpochMilliseconds(-1L) // pre-epoch edge
        val millis = converters.instantToEpochMillis(original)
        assertEquals(original, converters.epochMillisToInstant(millis))
    }

    @Test
    fun `offHost round-trips through baseUrl string`() {
        OffHost.entries.forEach { host ->
            val stored = converters.offHostToString(host)
            assertEquals(host.baseUrl, stored)
            assertEquals(host, converters.stringToOffHost(stored))
        }
    }

    @Test
    fun `stringToOffHost throws on a corrupt stored host`() {
        // Loud-on-corruption, mirroring OffLookupCacheEntry.init's require(...).
        assertThrows(IllegalArgumentException::class.java) {
            converters.stringToOffHost("https://evil.example/")
        }
    }
}
