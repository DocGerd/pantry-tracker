package de.docgerdsoft.pantrytracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

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
class OffApiClient internal constructor(private val httpClient: HttpClient) : OffLookup {

    constructor() : this(defaultClient())

    // Spec §7 explicitly maps OFF network/payload failures to "miss" (null) so the
    // caller drops into manual entry. The catches below intentionally discard the
    // exception — we don't even log because per-frame ML Kit decode failures already
    // flood logcat in the camera path and this would add noise without value.
    @Suppress("SwallowedException")
    override suspend fun lookup(barcode: String): OffProduct? {
        if (barcode.isBlank()) return null
        return try {
            val response: HttpResponse = httpClient.get(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json",
            ) {
                url.parameters.append("fields", "code,product_name,brands,image_url,status")
            }
            if (!response.status.isSuccess()) return null
            val envelope = response.body<OffApiEnvelope>()
            if (envelope.status != 1) null else envelope.product
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: HttpRequestTimeoutException) {
            null
        } catch (e: JsonConvertException) {
            null
        } catch (e: SerializationException) {
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
