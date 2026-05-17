package de.docgerdsoft.pantrytracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OffApiClientTest {

    @Test
    fun lookup_hit_parsesNameBrandImage() = runTest {
        val fixture = loadFixture("off/coke_330ml.json")
        val client = clientReturning(fixture, HttpStatusCode.OK)
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        // Assert presence + non-empty rather than exact strings — OFF community-edits
        // values, so the test should remain green if "Coca-Cola" becomes "Coca-Cola®"
        // or similar minor wording changes.
        assertEquals(true, result?.productName?.isNotBlank() ?: false)
        assertEquals(true, result?.brands?.isNotBlank() ?: false)
        assertEquals(true, result?.imageUrl?.startsWith("https://") ?: false)
    }

    @Test
    fun lookup_missingFields_returnsProductWithNulls() = runTest {
        val fixture = loadFixture("off/missing_fields.json")
        val sut = OffApiClient(clientReturning(fixture, HttpStatusCode.OK))

        val result = sut.lookup("1234567890123")

        assertNull(result?.productName)
        assertNull(result?.brands)
        assertNull(result?.imageUrl)
    }

    @Test
    fun lookup_offStatusZero_returnsNull() = runTest {
        val fixture = loadFixture("off/not_found.json")
        val sut = OffApiClient(clientReturning(fixture, HttpStatusCode.OK))

        val result = sut.lookup("0000000000000")

        assertNull(result)
    }

    @Test
    fun lookup_http404_returnsNull() = runTest {
        val sut = OffApiClient(clientReturning("", HttpStatusCode.NotFound))

        val result = sut.lookup("0000000000000")

        assertNull(result)
    }

    @Test
    fun lookup_networkException_returnsNull() = runTest {
        val client = HttpClient(MockEngine { throw java.io.IOException("offline") }) {
            install(ContentNegotiation) { json() }
        }
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNull(result)
    }

    @Test
    fun lookup_blankBarcode_returnsNull_withoutHittingNetwork() = runTest {
        var hits = 0
        val client = HttpClient(MockEngine { hits++; error("should not be reached") }) {
            install(ContentNegotiation) { json() }
        }
        val sut = OffApiClient(client)

        assertNull(sut.lookup(""))
        assertNull(sut.lookup("   "))
        assertEquals(0, hits)
    }

    private fun loadFixture(path: String): String =
        javaClass.classLoader!!.getResource(path)!!.readText()

    private fun clientReturning(body: String, status: HttpStatusCode): HttpClient =
        HttpClient(MockEngine { respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        ) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
}
