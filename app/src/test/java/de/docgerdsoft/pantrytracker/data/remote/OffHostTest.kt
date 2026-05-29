package de.docgerdsoft.pantrytracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OffHostTest {
    @Test
    fun `every host baseUrl is https and ends with a slash`() {
        OffHost.entries.forEach { host ->
            assertTrue(host.name, host.baseUrl.startsWith("https://"))
            assertTrue(host.name, host.baseUrl.endsWith("/"))
        }
    }

    @Test
    fun `fromBaseUrl round-trips every entry`() {
        OffHost.entries.forEach { host ->
            assertEquals(host, OffHost.fromBaseUrl(host.baseUrl))
        }
    }

    @Test
    fun `fromBaseUrl returns null for an unknown host`() {
        assertEquals(null, OffHost.fromBaseUrl("https://evil.example/"))
    }
}
