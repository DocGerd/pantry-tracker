package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.OffLookupCacheDao
import de.docgerdsoft.pantrytracker.data.local.OffLookupCacheEntry
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.util.barcodeHint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

// java.util.logging works in both Android (forwarded to logcat) and plain JVM
// unit tests. Matches the project's logging convention (DetailViewModel,
// ScanViewModel, OffApiClient, CameraPreview).
private val logger: Logger = Logger.getLogger("ProductRepositoryImpl")

// 30-day TTL bounds cache growth + the staleness window for OFF community edits.
// Spec calls for lazy eviction on read; no scheduled cleanup job.
private val OFF_CACHE_TTL: Duration = 30.days

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val offLookup: OffLookup,
    private val offLookupCacheDao: OffLookupCacheDao,
    private val clock: Clock = Clock.System,
) : ProductRepository {

    override fun observeProducts(): Flow<List<Product>> = dao.observeAll()

    override fun search(query: String): Flow<List<Product>> = dao.search(query)

    override suspend fun findById(id: Long): Product? = dao.findById(id)

    override fun observeById(id: Long): Flow<Product?> = dao.observeById(id)

    override suspend fun findLocalByBarcode(code: String): Product? = dao.findByBarcode(code)

    override suspend fun addNew(
        name: String,
        brand: String?,
        barcode: String?,
        imageUrl: String?,
        initialQuantity: Int,
    ): Long {
        val now = clock.now()
        val id = dao.upsert(
            Product(
                barcode = barcode,
                name = name,
                brand = brand,
                imageUrl = imageUrl,
                quantity = initialQuantity.coerceAtLeast(0),
                createdAt = now,
                updatedAt = now,
            ),
        )
        // A barcode that just landed in the pantry must never read back from the
        // OFF cache — the pantry row is the source of truth and may carry a
        // user-edited name/brand. Skip on null barcode (manual entry).
        //
        // Cache delete is best-effort: the pantry row above is the source of
        // truth, and `findLocalByBarcode` runs before any cache read on the
        // next preview, so a stale cache row is shadowed and harmless. A
        // throw here would surface as "Couldn't save: …" to the user despite
        // the pantry write having committed — see PR #60 finding I3.
        if (barcode != null) {
            try {
                offLookupCacheDao.delete(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                @Suppress("SwallowedException")
                logger.log(
                    Level.WARNING,
                    "OFF lookup cache evict-on-addNew failed for ${barcode.barcodeHint()} — stale row will be shadowed by pantry row",
                    e,
                )
            }
        }
        return id
    }

    override suspend fun applyDelta(productId: Long, delta: Int) {
        val existing = dao.findById(productId) ?: return
        val newQuantity = (existing.quantity + delta).coerceAtLeast(0)
        if (newQuantity == existing.quantity) return
        dao.upsert(existing.copy(quantity = newQuantity, updatedAt = clock.now()))
    }

    override suspend fun rename(productId: Long, newName: String) {
        val existing = dao.findById(productId) ?: return
        if (existing.name == newName) return
        dao.upsert(existing.copy(name = newName, updatedAt = clock.now()))
    }

    override suspend fun delete(productId: Long) {
        dao.deleteById(productId)
    }

    override suspend fun restore(product: Product) {
        // Upsert preserves every field of the captured Product. The
        // autoGenerate=true primary key honours non-zero ids, so the row
        // reappears under its original id.
        dao.upsert(product)
    }

    // ReturnCount: the function is intentionally guard-clause heavy — blank
    // input, pantry hit, fresh-cache hit, OFF miss, OFF-hit-without-name each
    // get their own early return. Folding any of them into nested branches
    // would be less readable than the linear "check, return, else continue"
    // shape. The cache check (added in #48 / C.4) raised the count from 5
    // to 6; the underlying complexity hasn't changed.
    @Suppress("ReturnCount")
    override suspend fun lookupForPreview(code: String): ScanCandidate? {
        if (code.isBlank()) return null
        findLocalByBarcode(code)?.let { return ScanCandidate.Persisted(it) }
        val now = clock.now()
        // Cache lookup sits BETWEEN the pantry check and the OFF call so a
        // committed pantry row always wins over a stale cached preview.
        // Read failure is best-effort: a throw here (schema corruption,
        // locked DB, type-converter failure) would short-circuit the OFF
        // network call and turn a transient cache-table problem into "all
        // scans fail" — see PR #60 finding I2. Treat as cache miss instead.
        val cached: OffLookupCacheEntry? = try {
            offLookupCacheDao.findByBarcode(code)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            @Suppress("SwallowedException")
            logger.log(
                Level.WARNING,
                "OFF lookup cache read failed for ${code.barcodeHint()} — treating as miss",
                e,
            )
            null
        }
        // `fetchedAt <= now` guards against clock-skew: a future `fetchedAt`
        // (clock that ran fast then was corrected, or DB tampering) would
        // otherwise make `(now - fetchedAt)` negative — always `<= TTL`, so
        // the row would be treated as fresh forever (PR #60 finding M1).
        if (cached != null && cached.fetchedAt <= now && (now - cached.fetchedAt) <= OFF_CACHE_TTL) {
            return ScanCandidate.FromOff(
                barcode = cached.barcode,
                name = cached.name,
                brand = cached.brand,
                imageUrl = cached.imageUrl,
            )
        }
        val result = offLookup.lookup(code) ?: return null
        val off = result.product
        val name = off.productName?.takeIf { it.isNotBlank() }
        if (name == null) {
            // C6: OFF returned a status=1 envelope but product_name was blank/absent.
            // We can't preview without a name — drop to manual entry. Log at INFO so
            // the discard is auditable without being logcat-noisy. SR-12: the
            // barcode is redacted to a hint and the OFF brand string is dropped
            // entirely (it adds no diagnostic value over the hint).
            logger.log(Level.INFO, "OFF hit for ${code.barcodeHint()} discarded — name blank")
            return null
        }
        val candidate = ScanCandidate.FromOff(
            barcode = code,
            name = capOffText(code, "name", name),
            brand = off.brands?.takeIf { it.isNotBlank() }?.let { capOffText(code, "brand", it) },
            imageUrl = gateImageUrl(code, off.imageUrl),
        )
        // Cache the post-gating shape so re-scans return the same capped /
        // filtered values without re-running the OFF host-fallback chain.
        // Upsert failure is best-effort: the OFF lookup ALREADY succeeded
        // and the candidate is what we owe the caller. A throw here would
        // surface as `Phase.Error("Couldn't read inventory: …")` despite
        // the resolve being successful — see PR #60 finding I1. Same
        // log-and-continue pattern as `capOffText` / `gateImageUrl`.
        try {
            offLookupCacheDao.upsert(
                OffLookupCacheEntry(
                    barcode = candidate.barcode,
                    name = candidate.name,
                    brand = candidate.brand,
                    imageUrl = candidate.imageUrl,
                    resolvingHost = result.resolvingHost,
                    fetchedAt = now,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            @Suppress("SwallowedException")
            logger.log(
                Level.WARNING,
                "OFF lookup cache upsert failed for ${code.barcodeHint()} — cache miss next time",
                e,
            )
        }
        return candidate
    }
}

private const val MAX_OFF_TEXT_LENGTH = 256

// Naming reflects the strict-less-than predicate so a future reader doesn't
// mis-read the constant as inclusive.
private const val MAX_IMAGE_URL_LENGTH_EXCLUSIVE = 2048

// Defensive cap, not data-driven — OFF has no documented max product_name
// length and multi-language entries can legitimately exceed 256. SR-14: the
// actual driver is the 50 MB worst case that would otherwise stream through
// Compose Text + Room SQLite before any UI affordance fires. Truncation is
// silent-to-the-user (the SR-14 attack matters more than the rare legitimate
// long name) but auditable via INFO log when it fires — so a hostile-input
// campaign leaves a forensic trail. The persisted Room record carries the
// truncated value; the raw input is by definition hostile or buggy data.
//
// File-scope rather than a class method to keep `ProductRepositoryImpl` under
// the Detekt TooManyFunctions threshold without adding a `@Suppress`.
private fun capOffText(code: String, field: String, raw: String): String {
    val capped = raw.take(MAX_OFF_TEXT_LENGTH)
    if (capped.length < raw.length) {
        logger.log(
            Level.INFO,
            "OFF $field truncated for ${code.barcodeHint()} — ${raw.length} -> $MAX_OFF_TEXT_LENGTH",
        )
    }
    return capped
}

// SR-15: drop the URL entirely unless it's https and within 2 KB. Anything
// else (`file://`, `content://`, http, or a 4 GB URL whose query string alone
// OOMs the decoder) is an attacker-influenced sink we don't render.
// AsyncImage hides the image area gracefully on null. Coil 3 has no
// per-fetcher disable on `ImageLoader.Builder`; the alternative is
// hand-rolling a `ComponentRegistry` without the default file/content
// fetchers. Upstream filtering at the URL boundary is the smaller surface.
//
// Scheme check is case-INsensitive: RFC 3986 §3.1 declares URI schemes
// case-insensitive, and OkHttp/Coil normalise on the way in, so a legitimate
// `Https://images.openfoodfacts.org/...` must not be dropped. Rejection logs
// at FINE with the redacted hint + categorical reason (`scheme` or
// `length=N`) — never the raw URL (attacker-controlled content). Matches the
// SR-2 precedent at OffApiClient.kt:64.
private fun gateImageUrl(code: String, raw: String?): String? {
    if (raw == null) return null
    val httpsOk = raw.startsWith("https://", ignoreCase = true)
    val sizeOk = raw.length < MAX_IMAGE_URL_LENGTH_EXCLUSIVE
    if (httpsOk && sizeOk) return raw
    val reason = if (!httpsOk) "scheme" else "length=${raw.length}"
    logger.log(Level.FINE, "OFF image_url dropped for ${code.barcodeHint()} — $reason")
    return null
}
