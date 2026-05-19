package de.docgerdsoft.pantrytracker.data.remote

import de.docgerdsoft.pantrytracker.BuildConfig
import de.docgerdsoft.pantrytracker.util.barcodeHint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
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
 * Open Food Facts v2 product lookup. Walks [OFF_HOSTS] (Food → Beauty →
 * PetFood → Products) only on HTTP 404; any other failure on the current host
 * (5xx, IOException, parse failure, IllegalArgumentException, or an OFF
 * contract violation like `status=1` with a null product) short-circuits to
 * null so a sick host can't multiply downtime by 4. Happy path = a single
 * request to `world.openfoodfacts.org`.
 *
 * Returns null on miss (all four hosts returned 404), on a blank or
 * malformed-format barcode (SR-2 gate), or on any non-cancellation exception
 * thrown by the HTTP / decode pipeline. The only exception that escapes to
 * the caller is CancellationException (rethrown for structured concurrency,
 * so a caller cancelling our job actually cancels us).
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
        for (host in OFF_HOSTS) {
            when (val r = lookupOnce(host, barcode)) {
                is HostResult.Found -> return r.product
                HostResult.NotFound -> continue
                HostResult.Error -> return null // don't multiply downtime by 4 across sister hosts
            }
        }
        return null
    }

    private sealed interface HostResult {
        data class Found(val product: OffProduct) : HostResult
        data object NotFound : HostResult
        data object Error : HostResult
    }

    /**
     * Performs a single-host OFF v2 product lookup against [baseUrl]. Caller
     * [lookup] dispatches one call to this helper per host in [OFF_HOSTS],
     * advancing on [HostResult.NotFound] and short-circuiting on
     * [HostResult.Error].
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
     *
     * @return [HostResult.Found] on 200 + valid envelope (`status=1` + non-null
     *   product); [HostResult.NotFound] on HTTP 404 or envelope `status != 1`
     *   (status-sentinel miss — chain walks); [HostResult.Error] on any other
     *   outcome (non-success HTTP, OFF contract violation `status=1` with null
     *   product, IOException, parse error, engine-runtime IAE — all logged,
     *   chain short-circuits). Throws only [CancellationException].
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
            classifyResponse(httpClient.get(url), barcode, baseUrl)
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

    private suspend fun classifyResponse(
        response: HttpResponse,
        barcode: String,
        baseUrl: String,
    ): HostResult {
        if (response.status.value == HTTP_NOT_FOUND) return HostResult.NotFound
        if (!response.status.isSuccess()) {
            // Non-success non-404: this host is sick (5xx, 401, 403, ...). Logged
            // at WARNING so operators triaging "scans aren't working" see the
            // status code in logcat; every other Error branch already logs, and
            // the silent-error gap on this branch was the only outlier.
            logger.log(
                Level.WARNING,
                "OFF lookup non-success HTTP ${response.status.value} for ${barcode.barcodeHint()} on $baseUrl",
            )
            return HostResult.Error
        }
        val envelope = response.body<OffApiEnvelope>()
        if (envelope.status != OFF_STATUS_FOUND) return HostResult.NotFound
        val product = envelope.product
        if (product == null) {
            // OFF contract violation: `status=1` must come with a product object.
            // Treat the same as a 5xx — "this host is sick, don't walk the
            // sister hosts". The chain walks for genuine misses (status=0 / 404),
            // not for upstream protocol bugs.
            logger.log(
                Level.WARNING,
                "OFF contract violation: status=1 with null product for ${barcode.barcodeHint()} on $baseUrl",
            )
            return HostResult.Error
        }
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

        // SR-24: hard cap on OFF response body size. Defence-in-depth against a
        // hostile or accidentally-huge response. OFF single-product JSON observed
        // at 5-20 KB; 256 KB is a 10x safety factor. The cap is strict-greater-
        // than — a Content-Length of exactly MAX_BODY_BYTES is allowed.
        private const val MAX_BODY_BYTES: Long = 256L * 1024L

        // Extends IOException deliberately — the existing `catch (e: IOException)`
        // arm in lookupOnce already maps it to HostResult.Error so no new catch
        // arm is needed.
        private class OversizedResponseException(size: Long) :
            IOException("OFF response body exceeds cap: $size bytes")

        private val OFF_HOSTS: List<String> = listOf(
            "https://world.openfoodfacts.org/",
            "https://world.openbeautyfacts.org/",
            "https://world.openpetfoodfacts.org/",
            "https://world.openproductsfacts.org/",
        )
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
            HttpResponseValidator {
                // SR-24: reject responses whose advertised body exceeds the cap
                // before any parse happens. Bodies without Content-Length (chunked)
                // are not size-checked at the header stage — acceptable for v1.1
                // because OFF's CDN sends Content-Length for product responses.
                validateResponse { response ->
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_BODY_BYTES) {
                        throw OversizedResponseException(contentLength)
                    }
                }
            }
            defaultRequest {
                headers.append(HttpHeaders.UserAgent, USER_AGENT)
            }
        }
    }
}
