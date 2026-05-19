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
import kotlinx.coroutines.CancellationException
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

    @Test
    fun lookup_backslashPathTraversal_returnsNull_withoutHittingNetwork() = runTest {
        // Windows-flavoured variant of pathInjection — backslash is not a path
        // separator on Linux but a server framework that normalises both could
        // still mis-route, so the regex must reject either way.
        assertRejectedWithoutNetwork("123\\..\\admin")
    }

    @Test
    fun lookup_percentEncodedNul_returnsNull_withoutHittingNetwork() = runTest {
        // Pure printable ASCII — sanitize won't strip it; the regex is the only
        // gate. If the regex ever loosened beyond `[0-9]` this would slip through
        // and a downstream sink that percent-decodes the path could see a NUL.
        assertRejectedWithoutNetwork("123%00")
    }

    @Test
    fun lookup_percentEncodedCrlf_returnsNull_withoutHittingNetwork() = runTest {
        // Classic HTTP-request-splitting shape if a downstream proxy
        // percent-decodes the path. Same reasoning as %00.
        assertRejectedWithoutNetwork("123%0d%0a")
    }

    @Test
    fun lookup_leadingWhitespace_returnsNull_withoutHittingNetwork() = runTest {
        // U+0020 is printable, so sanitize keeps it (see BarcodeTextTest.
        // sanitize_keepsRegularSpace) — the regex is the only defense against a
        // space-prefixed barcode reaching the URL builder.
        assertRejectedWithoutNetwork(" 5449000000996")
    }

    // -- #30 / SR-2: valid input still hits OFF, via the component URL builder --

    @Test
    fun lookup_validBarcode_firstRequestHitsOff() = runTest {
        // After v1.1 introduced the host fallback chain, the captured count
        // depends on whether the test fixture forces a 404 (whole chain walks)
        // or a 200 (chain stops at OFF). We pin the *first* request as OFF —
        // any regression that reorders the chain (e.g. swaps Beauty to first)
        // trips this test.
        val captured = mutableListOf<HttpRequestData>()
        OffApiClient(clientCapturing(captured)).lookup("5449000000996")

        assertTrue("expected at least one request", captured.isNotEmpty())
        val first = captured[0].url
        assertEquals("https", first.protocol.name)
        assertEquals("world.openfoodfacts.org", first.host)
        assertEquals("/api/v2/product/5449000000996.json", first.encodedPath)
        assertEquals(
            "code,product_name,brands,image_url,status",
            first.parameters["fields"],
        )
    }

    // -- #30 / SR-2: regex boundary cases (a typo like `{7,13}` must fail) --

    @Test
    fun lookup_exactly6Digits_acceptedAndFirstUrlBuilt() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        OffApiClient(clientCapturing(captured)).lookup("123456")
        assertTrue(captured.isNotEmpty())
        assertEquals("/api/v2/product/123456.json", captured[0].url.encodedPath)
    }

    @Test
    fun lookup_exactly14Digits_acceptedAndFirstUrlBuilt() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        OffApiClient(clientCapturing(captured)).lookup("12345678901234")
        assertTrue(captured.isNotEmpty())
        assertEquals("/api/v2/product/12345678901234.json", captured[0].url.encodedPath)
    }

    // -- v1.1 Item 1: fallback chain --

    @Test
    fun lookup_offHit_doesNotWalkToBeauty() = runTest {
        val hit = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openfoodfacts.org" to hit),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertEquals(true, result?.productName?.isNotBlank() ?: false)
        assertEquals(1, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
    }

    @Test
    fun lookup_offMiss_beautyHit_returnsBeautyProduct_doesNotWalkFurther() = runTest {
        val beautyBody = loadFixture("off/coke_330ml.json")  // schema-identical envelope
        val (client, captured) = clientByHost(
            mapOf("world.openbeautyfacts.org" to beautyBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertEquals(true, result?.productName?.isNotBlank() ?: false)
        assertEquals(2, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
        assertEquals("world.openbeautyfacts.org", captured[1].url.host)
    }

    @Test
    fun lookup_offMissBeautyMiss_petFoodHit_doesNotWalkToProducts() = runTest {
        val petFoodBody = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openpetfoodfacts.org" to petFoodBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertEquals(true, result?.productName?.isNotBlank() ?: false)
        assertEquals(3, captured.size)
        assertEquals(
            listOf(
                "world.openfoodfacts.org",
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
            ),
            captured.map { it.url.host },
        )
    }

    @Test
    fun lookup_offMissBeautyMissPetFoodMiss_productsHit_returnsProductsProduct() = runTest {
        // Pins position-4 (Products) as an independent hit path. Without this
        // test, a typo or refactor that swaps OFF_HOSTS[2] and OFF_HOSTS[3]
        // (PetFood ↔ Products) would still pass 7 of the 8 chain tests; only
        // lookup_allFourMiss_returnsNull_walksAllFour would catch it.
        val productsBody = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openproductsfacts.org" to productsBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertEquals(true, result?.productName?.isNotBlank() ?: false)
        assertEquals(
            listOf(
                "world.openfoodfacts.org",
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
                "world.openproductsfacts.org",
            ),
            captured.map { it.url.host },
        )
    }

    @Test
    fun lookup_allFourMiss_returnsNull_walksAllFour() = runTest {
        val (client, captured) = clientByHost(emptyMap()) // everything 404
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNull(result)
        assertEquals(4, captured.size)
        assertEquals(
            listOf(
                "world.openfoodfacts.org",
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
                "world.openproductsfacts.org",
            ),
            captured.map { it.url.host },
        )
    }

    @Test
    fun lookup_off5xx_returnsNull_doesNotWalk() = runTest {
        // Regression guard for the "do not walk past a real fault" rule. A 500
        // from OFF means *that server* is sick; it does not imply the barcode
        // might exist on Beauty Facts. Walking would multiply downtime by 4.
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
            )
        }) {
            install(ContentNegotiation) { json() }
        }
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNull(result)
        assertEquals(1, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
    }

    @Test
    fun lookup_status1WithNullProduct_returnsNull_doesNotWalk() = runTest {
        // Pin the OFF-contract-violation policy: a 200 with `status=1` but no
        // `product` object is treated as "sick host" — fail-fast, do NOT walk
        // to sister projects. The chain walks for genuine misses (status=0 /
        // 404), not for upstream protocol bugs the sister hosts can't help with.
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            respond(
                content = ByteReadChannel("{\"status\":1}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNull(result)
        assertEquals("contract violation must not walk past OFF", 1, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
    }

    @Test
    fun lookup_offMiss_beautyTimeout_returnsNull_doesNotWalkFurther() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            when (request.url.host) {
                "world.openfoodfacts.org" -> respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotFound,
                )
                else -> throw SocketTimeoutException("beauty timeout")
            }
        }) {
            install(ContentNegotiation) { json() }
        }
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNull(result)
        assertEquals(2, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
        assertEquals("world.openbeautyfacts.org", captured[1].url.host)
    }

    @Test
    fun lookup_offStatusNotOne_walksToBeauty() = runTest {
        val notFoundFixture = loadFixture("off/not_found.json")
        val beautyBody = loadFixture("off/coke_330ml.json")
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            when (request.url.host) {
                "world.openfoodfacts.org" -> respond(
                    content = ByteReadChannel(notFoundFixture),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "world.openbeautyfacts.org" -> respond(
                    content = ByteReadChannel(beautyBody),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val sut = OffApiClient(client)

        val result = sut.lookup("0000000000000")

        assertEquals(true, result?.productName?.isNotBlank() ?: false)
        assertEquals(2, captured.size)
    }

    @Test
    fun lookup_cancellation_propagates() = runTest {
        // Pin the structured-concurrency contract: a cancelled caller's job
        // must actually cancel us, not silently become null.
        val client = HttpClient(MockEngine { throw CancellationException("cancelled") }) {
            install(ContentNegotiation) { json() }
        }
        val sut = OffApiClient(client)

        try {
            sut.lookup("5449000000996")
            org.junit.Assert.fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun lookup_cancellation_fromSecondHost_propagates() = runTest {
        // Defense-in-depth: pin that CE propagates from a non-first host too.
        // The first-host test above passes even if lookup()'s for-loop is
        // accidentally wrapped in `runCatching { }` (a known footgun in this
        // repo), because lookupOnce rethrows CE before reaching that
        // hypothetical wrapper. This test exercises the loop's second iteration.
        val client = HttpClient(MockEngine { request ->
            when (request.url.host) {
                "world.openfoodfacts.org" -> respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotFound,
                )
                else -> throw CancellationException("cancelled mid-chain")
            }
        }) {
            install(ContentNegotiation) { json() }
        }
        val sut = OffApiClient(client)

        try {
            sut.lookup("5449000000996")
            org.junit.Assert.fail("expected CancellationException from host 2")
        } catch (e: CancellationException) {
            // expected
        }
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
        assertCatchArmRedactsBarcode(IOException("offline"))
    }

    @Test
    fun lookup_jsonConvertException_logsHintNotFullBarcode() = runTest {
        // ContentNegotiation/Ktor throws JsonConvertException on parse failure;
        // pin the redaction for that catch arm independently of the IO arm.
        assertCatchArmRedactsBarcode(io.ktor.serialization.JsonConvertException("bad json"))
    }

    @Test
    fun lookup_serializationException_logsHintNotFullBarcode() = runTest {
        assertCatchArmRedactsBarcode(kotlinx.serialization.SerializationException("bad shape"))
    }

    @Test
    fun lookup_illegalArgumentException_logsHintNotFullBarcode() = runTest {
        // Engine-runtime IAE only (URL building is outside the try, and a
        // regex-validated barcode produces only safe path segments). Pinned at
        // SEVERE so a future refactor that drops the SEVERE designation also
        // trips this test.
        assertCatchArmRedactsBarcode(IllegalArgumentException("bad URL"))
    }

    private suspend fun assertCatchArmRedactsBarcode(thrown: Throwable) {
        JulLogCapture("OffApiClient").use { capture ->
            val sut = OffApiClient(clientThrowing(thrown))
            sut.lookup("5449000000996")
            val joined = capture.messages().joinToString(" | ")
            assertTrue(
                "expected hint '5449…96' for ${thrown::class.simpleName} in: $joined",
                joined.contains("5449…96"),
            )
            assertFalse(
                "full barcode leaked for ${thrown::class.simpleName} in: $joined",
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

    /** Returns 404 for everything UNLESS the host matches one of [bodyByHost]'s keys,
     *  in which case it returns the mapped body as 200 OK. Captures every request that
     *  reaches the engine for post-call assertions on host coverage. */
    private fun clientByHost(
        bodyByHost: Map<String, String>,
        captured: MutableList<HttpRequestData> = mutableListOf(),
    ): Pair<HttpClient, MutableList<HttpRequestData>> {
        val client = HttpClient(MockEngine { request ->
            captured += request
            val body = bodyByHost[request.url.host]
            if (body != null) {
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotFound,
                )
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return client to captured
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
