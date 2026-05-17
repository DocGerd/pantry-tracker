package de.docgerdsoft.pantrytracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Open Food Facts v2 product lookup. Returns `null` on miss, 4xx/5xx, or any
 * IOException (network down, DNS, timeout) — never throws to the caller per
 * spec §7 (OFF miss == network failure == drop to manual entry).
 *
 * `CancellationException` is re-thrown explicitly so structured concurrency
 * still works when the caller (typically [ScanViewModel]) cancels the lookup
 * after a new scan or sheet dismissal.
 *
 * Default constructor builds a production HttpClient. Tests pass a MockEngine-
 * backed client via the second constructor.
 */
class OffApiClient internal constructor(private val httpClient: HttpClient) {

    constructor() : this(defaultClient())

    suspend fun lookup(barcode: String): OffProduct? {
        if (barcode.isBlank()) return null
        return try {
            val response: HttpResponse = httpClient.get(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
            ) {
                url.parameters.append("fields", "code,product_name,brands,image_url,status")
            }
            if (!response.status.isSuccess()) return null
            val envelope = response.body<OffApiEnvelope>()
            if (envelope.status != 1) null else envelope.product
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e  // structured concurrency — never swallow cancellation
        } catch (e: Exception) {
            // Per spec §7: recoverable network failures map to "miss" (null);
            // caller drops into manual entry. No retry, no log-and-hang.
            null
        }
    }

    companion object {
        private const val USER_AGENT =
            "PantryTracker/0.1 (https://github.com/DocGerd/pantry-tracker)"
        private const val TIMEOUT_MILLIS = 8_000L

        private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
                socketTimeoutMillis = TIMEOUT_MILLIS
            }
            defaultRequest {
                headers.append(HttpHeaders.UserAgent, USER_AGENT)
            }
        }
    }
}
