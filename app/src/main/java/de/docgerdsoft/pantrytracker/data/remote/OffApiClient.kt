package de.docgerdsoft.pantrytracker.data.remote

import android.util.Log
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
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "OffApiClient"

/**
 * Open Food Facts v2 product lookup. Returns null on miss, 4xx/5xx, blank barcode,
 * request timeout, JSON parse failure, or network IOException — the only exception
 * that escapes to the caller is CancellationException (rethrown for structured
 * concurrency, so a caller cancelling our job actually cancels us).
 *
 * Production uses the no-arg secondary constructor, which builds a real
 * OkHttp-backed HttpClient. Tests inject a MockEngine-backed HttpClient via the
 * primary (HttpClient-accepting) constructor.
 *
 * Per spec §7: OFF miss == network failure == drop to manual entry. No retry,
 * no log-and-hang, no surfaced exceptions other than cancellation.
 */
class OffApiClient internal constructor(private val httpClient: HttpClient) : OffLookup {

    constructor() : this(defaultClient())

    // Spec §7 maps OFF network/payload failures to "miss" (null) so the caller drops
    // into manual entry. Exceptions are logged at WARN (one per scan max — not
    // logcat-spammy) so flaky-network reports have a stack trace, but the user-
    // visible signal is the manual-entry sheet, not a toast or error dialog.
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
            // Covers network down, DNS failures, connect/socket timeouts (OkHttp
            // surfaces timeout as SocketTimeoutException which is an IOException).
            @Suppress("SwallowedException")
            Log.w(TAG, "OFF lookup network error for $barcode", e)
            null
        } catch (e: JsonConvertException) {
            @Suppress("SwallowedException")
            Log.w(TAG, "OFF lookup JSON conversion error for $barcode", e)
            null
        } catch (e: SerializationException) {
            @Suppress("SwallowedException")
            Log.w(TAG, "OFF lookup serialization error for $barcode", e)
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
