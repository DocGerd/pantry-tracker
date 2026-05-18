package de.docgerdsoft.pantrytracker.data.remote

import de.docgerdsoft.pantrytracker.BuildConfig
import de.docgerdsoft.pantrytracker.util.barcodeHint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

// java.util.logging works in both Android (forwarded to logcat at debuggable) and
// plain JVM unit tests (writes to System.err). Avoids android.util.Log throwing
// "Method w in android.util.Log not mocked" in non-Robolectric tests.
private val logger: Logger = Logger.getLogger("OffApiClient")

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
        // Per #30 / SR-2: validate format before any URL construction. Returning
        // null (not throwing) matches the "OFF miss == drop to manual entry"
        // contract — the caller can't distinguish a bad-format reject from a 404,
        // which is exactly the user-visible behavior we want. No log: a malformed
        // barcode is a known input, not an exceptional event.
        if (!BARCODE_PATTERN.matches(barcode)) return null
        return try {
            val response: HttpResponse = httpClient.get {
                // Component URL builder so each path segment is percent-encoded
                // explicitly; the prior string-interpolation form trusted the
                // caller's barcode to be URL-safe (see SR-2).
                url {
                    takeFrom(OFF_BASE_URL)
                    appendPathSegments("api", "v2", "product", "$barcode.json")
                    parameters.append("fields", OFF_FIELDS)
                }
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
            logger.log(Level.WARNING, "OFF lookup network error for ${barcode.barcodeHint()}", e)
            null
        } catch (e: JsonConvertException) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "OFF lookup JSON conversion error for ${barcode.barcodeHint()}", e)
            null
        } catch (e: SerializationException) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "OFF lookup serialization error for ${barcode.barcodeHint()}", e)
            null
        } catch (e: IllegalArgumentException) {
            // Belt-and-suspenders: a URL-parser surprise the regex didn't catch
            // (or one the engine surfaces after construction) maps to a miss
            // instead of escaping to the camera screen as Phase.Error.
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "OFF lookup URL/argument error for ${barcode.barcodeHint()}", e)
            null
        }
    }

    companion object {
        // Derive from BuildConfig so a version bump in app/build.gradle.kts
        // automatically updates what OFF sees in its server-side analytics.
        // Avoids the trap where the app ships v1.0 but identifies itself as
        // v0.1 forever because the constant was forgotten.
        private val USER_AGENT: String =
            "PantryTracker/${BuildConfig.VERSION_NAME} (https://github.com/DocGerd/pantry-tracker)"
        private const val TIMEOUT_MILLIS = 8_000L

        private const val OFF_BASE_URL = "https://world.openfoodfacts.org/"
        private const val OFF_FIELDS = "code,product_name,brands,image_url,status"

        // EAN-8 .. ITF-14 covers every numeric symbology ML Kit can decode in
        // the formats we enable; 6 is the lower bound because EAN-8 minus the
        // check digit is sometimes scanned as 7, and the manual-entry sheet
        // accepts down to 6 for partial codes (matches the OFF API's tolerance).
        private val BARCODE_PATTERN = Regex("^[0-9]{6,14}$")

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
