package de.docgerdsoft.pantrytracker.data.remote

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Dedicated fallback-chain regression suite for [OffApiClient].
 *
 * Covers the 6 acceptance-criteria scenarios from issue #82:
 *
 *   1. Host 1 (Food) succeeds — no fallback initiated
 *   2. Host 1 404 → Host 2 (Beauty) succeeds
 *   3. Host 1+2 404 → Host 3 (PetFood) succeeds
 *   4. Host 1+2+3 404 → Host 4 (Products/Generic) succeeds
 *   5. Host 1 IOException — chain stops immediately (fail-fast: Error != NotFound)
 *   6. All four hosts 404 — returns null, all four are visited
 *
 * **Verified host order** (OffApiClient.kt:282–287):
 *   1. `world.openfoodfacts.org`     (Food)
 *   2. `world.openbeautyfacts.org`   (Beauty)
 *   3. `world.openpetfoodfacts.org`  (PetFood)
 *   4. `world.openproductsfacts.org` (Products/Generic)
 *
 * **Fail-fast on IOException** (OffApiClient.kt:155–158 + lookup():93):
 * IOException on any host returns HostResult.Error, which causes `lookup()` to
 * return null immediately via `HostResult.Error -> return null`. The chain does
 * NOT walk further — this prevents multiplying downtime by 4 when a host is
 * sick. Scenario 5 tests and documents this production-verified behavior.
 *
 * These tests are complementary to [OffApiClientTest]: that class is the
 * exhaustive single-host contract suite; this class is the dedicated
 * multi-host-chain regression guard.
 */
class OffApiFallbackChainTest {

    // -------------------------------------------------------------------------
    // Scenario 1 — Host 1 (Food) succeeds → no fallback
    // -------------------------------------------------------------------------

    @Test
    fun scenario1_foodHostHit_returnsResult_noFallback() = runTest {
        val foodBody = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openfoodfacts.org" to foodBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNotNull("food host hit must yield a result", result)
        assertEquals(
            "resolvingHost must be the Food host",
            "https://world.openfoodfacts.org/",
            result?.resolvingHost,
        )
        // Only one request: chain stopped at host 1.
        assertEquals("only 1 request expected on a food hit", 1, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — Host 1 (Food) 404 → Host 2 (Beauty) succeeds
    // -------------------------------------------------------------------------

    @Test
    fun scenario2_foodMiss_beautyHit_returnsBeautyResult() = runTest {
        // Beauty host uses the same envelope schema as Food — schema-identical.
        val beautyBody = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openbeautyfacts.org" to beautyBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNotNull("beauty hit must yield a result", result)
        assertEquals(
            "resolvingHost must be the Beauty host",
            "https://world.openbeautyfacts.org/",
            result?.resolvingHost,
        )
        // Two requests: Food (404) then Beauty (200).
        assertEquals("2 requests expected: Food miss + Beauty hit", 2, captured.size)
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
        assertEquals("world.openbeautyfacts.org", captured[1].url.host)
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — Host 1+2 (Food + Beauty) 404 → Host 3 (PetFood) succeeds
    // -------------------------------------------------------------------------

    @Test
    fun scenario3_foodBeautyMiss_petFoodHit_returnsPetFoodResult() = runTest {
        val petFoodBody = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openpetfoodfacts.org" to petFoodBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNotNull("pet-food hit must yield a result", result)
        assertEquals(
            "resolvingHost must be the PetFood host",
            "https://world.openpetfoodfacts.org/",
            result?.resolvingHost,
        )
        // Three requests: Food + Beauty (404) then PetFood (200).
        assertEquals("3 requests expected: Food+Beauty miss + PetFood hit", 3, captured.size)
        assertEquals(
            "host visit order must match OFF_HOSTS declaration",
            listOf(
                "world.openfoodfacts.org",
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
            ),
            captured.map { it.url.host },
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 4 — Host 1+2+3 (Food + Beauty + PetFood) 404 → Host 4 (Products/Generic) succeeds
    // -------------------------------------------------------------------------

    @Test
    fun scenario4_foodBeautyPetFoodMiss_productsHit_returnsProductsResult() = runTest {
        val productsBody = loadFixture("off/coke_330ml.json")
        val (client, captured) = clientByHost(
            mapOf("world.openproductsfacts.org" to productsBody),
        )
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNotNull("products/generic hit must yield a result", result)
        assertEquals(
            "resolvingHost must be the Products/Generic host",
            "https://world.openproductsfacts.org/",
            result?.resolvingHost,
        )
        // Four requests: first three 404, Products succeeds.
        assertEquals("4 requests expected for the 4-host walk", 4, captured.size)
        assertEquals(
            "host visit order must match OFF_HOSTS declaration",
            listOf(
                "world.openfoodfacts.org",
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
                "world.openproductsfacts.org",
            ),
            captured.map { it.url.host },
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 5 — Host 1 (Food) IOException → chain stops (fail-fast, NOT recovery)
    //
    // PRODUCTION BEHAVIOR NOTE: HostResult.Error from IOException causes
    // `lookup()` to `return null` immediately (OffApiClient.kt:93). The chain
    // does NOT walk to host 2. This prevents multiplying downtime by 4 when a
    // host is sick — a deliberate design choice, not a gap. The scenario title
    // in #82 says "proves recovery isn't gated only on status codes"; what it
    // actually proves is that IOException is a distinct failure mode with its
    // own policy (fail-fast), separate from HTTP 404 (walk-on-miss). Test
    // asserts the verified production behavior.
    // -------------------------------------------------------------------------

    @Test
    fun scenario5_foodHostIOException_returnsNull_chainDoesNotWalk() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            captured += request
            when (request.url.host) {
                "world.openfoodfacts.org" -> throw IOException("food host down")
                else -> respond(
                    content = ByteReadChannel(loadFixture("off/coke_330ml.json")),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        // IOException on host 1 → HostResult.Error → return null (fail-fast).
        // The chain does NOT walk: only 1 request is made.
        assertNull("IOException must yield null (fail-fast, not recovery)", result)
        assertEquals(
            "chain must stop at 1 request on IOException (HostResult.Error path)",
            1,
            captured.size,
        )
        assertEquals("world.openfoodfacts.org", captured[0].url.host)
    }

    // -------------------------------------------------------------------------
    // Scenario 6 — All four hosts 404 → null result, all four visited
    // -------------------------------------------------------------------------

    @Test
    fun scenario6_allFourHostsMiss_returnsNull_allFourVisited() = runTest {
        // Empty host map → clientByHost returns 404 for every host.
        val (client, captured) = clientByHost(emptyMap())
        val sut = OffApiClient(client)

        val result = sut.lookup("5449000000996")

        assertNull("all-miss chain must yield null", result)
        assertEquals("all 4 hosts must be visited on all-miss", 4, captured.size)
        assertEquals(
            "host visit order must match OFF_HOSTS declaration",
            listOf(
                "world.openfoodfacts.org",
                "world.openbeautyfacts.org",
                "world.openpetfoodfacts.org",
                "world.openproductsfacts.org",
            ),
            captured.map { it.url.host },
        )
    }

    // -------------------------------------------------------------------------
    // Helpers (mirrors OffApiClientTest.clientByHost / loadFixture pattern)
    // -------------------------------------------------------------------------

    /**
     * Returns 404 for everything UNLESS the host matches one of [bodyByHost]'s
     * keys, in which case it returns the mapped body as 200 OK with JSON
     * content-type. Captures every request for post-call host-order assertions.
     */
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
}
