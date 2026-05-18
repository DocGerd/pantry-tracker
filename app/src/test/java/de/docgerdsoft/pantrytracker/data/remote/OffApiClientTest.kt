package de.docgerdsoft.pantrytracker.data.remote

import de.docgerdsoft.pantrytracker.util.JulLogCapture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

    // -- #30 / SR-2: format validation rejects hostile input before any network call --

    @Test
    fun lookup_belowMinLength_returnsNull_withoutHittingNetwork() = runTest {
        assertRejectedWithoutNetwork("12345") // 5 digits, regex min is 6
    }

    @Test
    fun lookup_aboveMaxLength_returnsNull_withoutHittingNetwork() = runTest {
        assertRejectedWithoutNetwork("123456789012345") // 15 digits, regex max is 14
    }

    @Test
    fun lookup_nonDigit_returnsNull_withoutHittingNetwork() = runTest {
        assertRejectedWithoutNetwork("abc1234567")
    }

    @Test
    fun lookup_queryInjection_returnsNull_withoutHittingNetwork() = runTest {
        // The most dangerous input shape: would have spliced &token=… into the
        // OFF request as a query parameter with the old string-interpolation URL.
        assertRejectedWithoutNetwork("123?token=bar")
    }

    @Test
    fun lookup_pathInjection_returnsNull_withoutHittingNetwork() = runTest {
        assertRejectedWithoutNetwork("../../admin")
    }

    @Test
    fun lookup_fragmentInjection_returnsNull_withoutHittingNetwork() = runTest {
        assertRejectedWithoutNetwork("123#bar")
    }

    @Test
    fun lookup_newlineInjection_returnsNull_withoutHittingNetwork() = runTest {
        // The validator runs on the raw input — \n is a non-digit so the regex
        // rejects without needing to invoke sanitize first.
        assertRejectedWithoutNetwork("\n5449000000996")
    }

    @Test
    fun lookup_rtlOverridePrefix_returnsNull_withoutHittingNetwork() = runTest {
        assertRejectedWithoutNetwork("‮123456789012")
    }

    // -- #30 / SR-2: valid input still hits OFF, via the component URL builder --

    @Test
    fun lookup_validBarcode_buildsComponentUrl() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val sut = OffApiClient(clientCapturing(captured))

        sut.lookup("5449000000996")

        assertEquals(1, captured.size)
        val url = captured[0].url
        assertEquals("world.openfoodfacts.org", url.host)
        assertEquals("/api/v2/product/5449000000996.json", url.encodedPath)
        assertEquals(
            "code,product_name,brands,image_url,status",
            url.parameters["fields"],
        )
    }

    // -- #30 belt-and-suspenders: any IllegalArgumentException the engine throws
    //    (e.g. an exotic URL parser surprise the regex didn't catch) still maps
    //    to a null lookup rather than escaping to the caller. --

    @Test
    fun lookup_illegalArgumentException_returnsNull() = runTest {
        val sut = OffApiClient(clientThrowing(IllegalArgumentException("bad URL")))
        assertNull(sut.lookup("5449000000996"))
    }

    // -- #30 / SR-2 telemetry: malformed-input rejections still leave a trace --

    @Test
    fun lookup_rejectedFormat_logsFineWithHint() = runTest {
        JulLogCapture("OffApiClient").use { capture ->
            val sut = OffApiClient(
                HttpClient(MockEngine { error("rejected input must not reach the engine") }) {
                    install(ContentNegotiation) { json() }
                },
            )
            assertNull(sut.lookup("123?token=bar"))

            // FINE-level so a hostile-input burst is reproducible via
            // `adb shell setprop log.tag.OffApiClient VERBOSE` without polluting
            // default logcat output. Hint must be present, raw payload absent.
            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected hint in: $joined", joined.contains("123?…ar") || joined.contains("<short>"))
            assertFalse(
                "full hostile input '123?token=bar' leaked: $joined",
                joined.contains("123?token=bar"),
            )
        }
    }

    // -- #31 / SR-10: barcode redacted to hint in WARNING log lines --

    @Test
    fun lookup_networkError_logsHintNotFullBarcode() = runTest {
        JulLogCapture("OffApiClient").use { capture ->
            val sut = OffApiClient(clientThrowing(IOException("offline")))
            sut.lookup("5449000000996")

            val joined = capture.messages().joinToString(" | ")
            assertTrue("expected hint '5449…96' in: $joined", joined.contains("5449…96"))
            assertFalse(
                "full barcode '5449000000996' leaked into log: $joined",
                joined.contains("5449000000996"),
            )
        }
    }

    private suspend fun assertRejectedWithoutNetwork(input: String) {
        var hits = 0
        val sut = OffApiClient(
            HttpClient(MockEngine { hits++; error("should not be reached for input: $input") }) {
                install(ContentNegotiation) { json() }
            },
        )
        assertNull("expected null lookup for input: $input", sut.lookup(input))
        assertEquals("expected 0 network calls for input: $input", 0, hits)
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

    // Captures every request that reaches the engine; responds 404 so the
    // SUT's null-on-404 path runs and the test focuses purely on URL shape.
    private fun clientCapturing(out: MutableList<HttpRequestData>): HttpClient =
        HttpClient(MockEngine { request ->
            out += request
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
        }) {
            install(ContentNegotiation) { json() }
        }
}
