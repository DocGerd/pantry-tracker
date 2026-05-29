package de.docgerdsoft.pantrytracker.data.remote

import org.junit.Assert.fail
import org.junit.Test

class OffApiClientDepthTest {
    @Test
    fun `body within depth bound is accepted`() {
        val json = """{"status":1,"product":{"code":"x","product_name":"y"}}"""
        // Should not throw.
        OffApiClient.enforceMaxNestingDepth(json.encodeToByteArray())
    }

    @Test
    fun `body exceeding depth bound is rejected`() {
        val deep = buildString {
            repeat(OffApiClient.MAX_JSON_DEPTH + 5) { append("{\"a\":") }
            append("1")
            repeat(OffApiClient.MAX_JSON_DEPTH + 5) { append("}") }
        }
        try {
            OffApiClient.enforceMaxNestingDepth(deep.encodeToByteArray())
            fail("expected OverNestedResponseException to be thrown")
        } catch (@Suppress("SwallowedException") e: OffApiClient.Companion.OverNestedResponseException) {
            // expected — the guard must throw on a body nested beyond MAX_JSON_DEPTH
        }
    }

    @Test
    fun `braces inside a string literal do not count toward depth`() {
        // A product_name full of braces must NOT trip the guard.
        val braces = "{".repeat(OffApiClient.MAX_JSON_DEPTH + 10)
        val json = """{"status":1,"product":{"product_name":"$braces"}}"""
        OffApiClient.enforceMaxNestingDepth(json.encodeToByteArray()) // no throw
    }
}
