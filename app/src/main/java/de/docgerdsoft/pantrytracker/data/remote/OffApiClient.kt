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
import io.ktor.http.URLBuilder
import io.ktor.http.Url
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

// JUL logger — works in both Android (the default JUL handler forwards records
// to logcat) and plain JVM unit tests (writes to System.err). Avoids
// android.util.Log throwing "Method w in android.util.Log not mocked" in
// non-Robolectric tests.
//
// JUL → logcat level mapping on Android (also applies at the other five
// Logger.getLogger(...) call sites in this codebase — see DetailViewModel,
// ScanViewModel, CameraPermissionGate, CameraPreview, ProductRepositoryImpl):
//   SEVERE  → Log.e        WARNING → Log.w        INFO    → Log.i
//   CONFIG  → Log.i        FINE    → Log.d        FINER   → Log.d
//   FINEST  → Log.v
// JUL's default Logger level is INFO, so FINE/FINER/FINEST drop at the JUL
// layer unless explicitly raised; Log.d / Log.v are also filtered out of
// default logcat output, so effectively only WARNING+ is user-visible in
// release builds.
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
        // Format gate (SR-2). Returning null (not throwing) matches the "OFF
        // miss == drop to manual entry" contract — the caller can't distinguish
        // a bad-format reject from a 404, which is the user-visible behaviour
        // we want. Logged at FINE so the (silent-to-the-user) rejection still
        // leaves a trace operators can surface via
        // `adb shell setprop log.tag.OffApiClient VERBOSE` when investigating
        // a hostile-input report.
        if (!BARCODE_PATTERN.matches(barcode)) {
            logger.log(Level.FINE, "OFF lookup rejected malformed input ${barcode.barcodeHint()}")
            return null
        }
        return when (val r = lookupOnce(OFF_BASE_URL, barcode)) {
            is HostResult.Found -> r.product
            HostResult.NotFound -> null
            HostResult.Error -> null
        }
    }

    private sealed interface HostResult {
        data class Found(val product: OffProduct) : HostResult
        data object NotFound : HostResult
        data object Error : HostResult
    }

    /**
     * Performs a single-host OFF v2 product lookup against [baseUrl]. Caller
     * dispatches into this function from [lookup]; after Task 1.2 the public
     * [lookup] iterates over multiple hosts on `NotFound`.
     *
     * Catch-arm contract:
     * - **CancellationException** rethrown for structured concurrency, so a
     *   caller cancelling our job actually cancels us.
     * - **IOException** covers network down, DNS failures, and connect/socket
     *   timeouts — OkHttp surfaces `HttpTimeout` failures as
     *   `SocketTimeoutException` (an `IOException`), so a separate
     *   `HttpRequestTimeoutException` catch is intentionally absent.
     * - **JsonConvertException / SerializationException** at WARNING — parse
     *   failures suggest a server-side surprise; logged but not user-visible.
     * - **IllegalArgumentException** at SEVERE — engine-runtime only (URL
     *   building is outside the `try`, and a regex-validated 6-14-digit
     *   barcode produces only safe path segments). The SEVERE level is
     *   deliberate: an exotic engine failure should be loud in crash-report
     *   aggregators despite the null return.
     */
    private suspend fun lookupOnce(baseUrl: String, barcode: String): HostResult {
        // Component URL builder so each path segment is percent-encoded
        // explicitly (SR-2). Built outside the `try` so any IAE is a
        // programmer error (e.g. malformed baseUrl constant) and surfaces
        // in dev rather than silently degrading prod to manual-entry-forever.
        val url: Url = URLBuilder().apply {
            takeFrom(baseUrl)
            appendPathSegments("api", "v2", "product", "$barcode.json")
            parameters.append("fields", OFF_FIELDS)
        }.build()
        return try {
            classifyResponse(httpClient.get(url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "OFF lookup network error for ${barcode.barcodeHint()} on $baseUrl", e)
            HostResult.Error
        } catch (e: JsonConvertException) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "OFF lookup JSON conversion error for ${barcode.barcodeHint()} on $baseUrl", e)
            HostResult.Error
        } catch (e: SerializationException) {
            @Suppress("SwallowedException")
            logger.log(Level.WARNING, "OFF lookup serialization error for ${barcode.barcodeHint()} on $baseUrl", e)
            HostResult.Error
        } catch (e: IllegalArgumentException) {
            @Suppress("SwallowedException")
            logger.log(Level.SEVERE, "OFF lookup engine-runtime IAE for ${barcode.barcodeHint()} on $baseUrl", e)
            HostResult.Error
        }
    }

    private suspend fun classifyResponse(response: HttpResponse): HostResult {
        if (response.status.value == HTTP_NOT_FOUND) return HostResult.NotFound
        if (!response.status.isSuccess()) return HostResult.Error
        val envelope = response.body<OffApiEnvelope>()
        if (envelope.status != OFF_STATUS_FOUND) return HostResult.NotFound
        val product = envelope.product ?: return HostResult.NotFound
        return HostResult.Found(product)
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

        // HTTP 404 is the only "not found" signal we distinguish from a generic
        // transport error; on the OFF fallback chain (Task 1.2) it's the trigger
        // to try the next host. Other 4xx/5xx codes mean the request never
        // exited our trust boundary cleanly, so we fail closed (Error).
        private const val HTTP_NOT_FOUND = 404

        // OFF envelope's status flag — 1 == hit, 0 == miss. Hoisted from a
        // literal to give the comparison in classifyResponse a name; the OFF
        // schema docs use the same convention.
        private const val OFF_STATUS_FOUND = 1

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
