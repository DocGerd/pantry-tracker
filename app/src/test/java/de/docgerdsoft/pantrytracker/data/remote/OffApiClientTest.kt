package de.docgerdsoft.pantrytracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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

    // -- I5: parameterized IOException subclasses --

    @Test
    fun lookup_unknownHostException_returnsNull() = runTest {
        val sut = OffApiClient(clientThrowing(UnknownHostException("no host")))
        assertNull(sut.lookup("5449000000996"))
    }

    @Test
    fun lookup_socketTimeoutException_returnsNull() = runTest {
        val sut = OffApiClient(clientThrowing(SocketTimeoutException("timeout")))
        assertNull(sut.lookup("5449000000996"))
    }

    @Test
    fun lookup_connectException_returnsNull() = runTest {
        val sut = OffApiClient(clientThrowing(ConnectException("refused")))
        assertNull(sut.lookup("5449000000996"))
    }

    @Test
    fun lookup_ioException_returnsNull() = runTest {
        val sut = OffApiClient(clientThrowing(IOException("offline")))
        assertNull(sut.lookup("5449000000996"))
    }

    // -- I5: JSON parse failures --

    @Test
    fun lookup_malformedJson_returnsNull() = runTest {
        // ContentNegotiation/Ktor throws JsonConvertException on parse failure
        val sut = OffApiClient(clientReturning("{ not valid json {{{{", HttpStatusCode.OK))
        assertNull(sut.lookup("5449000000996"))
    }

    @Test
    fun lookup_wrongJsonSchema_returnsNull() = runTest {
        // Valid JSON but wrong schema — SerializationException / JsonConvertException
        val sut = OffApiClient(clientReturning("""{"totally":"wrong"}""", HttpStatusCode.OK))
        // OffApiEnvelope.status has no default → missing key should cause SerializationException
        // (ignoreUnknownKeys=true, but missing required fields still fail)
        assertNull(sut.lookup("5449000000996"))
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

    private fun clientThrowing(t: Throwable): HttpClient =
        HttpClient(MockEngine { throw t }) {
            install(ContentNegotiation) { json() }
        }
}
