package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.util.barcodeHint
import kotlinx.coroutines.flow.Flow
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Clock

// java.util.logging works in both Android (forwarded to logcat) and plain JVM
// unit tests. Matches the project's logging convention (DetailViewModel,
// ScanViewModel, OffApiClient, CameraPreview).
private val logger: Logger = Logger.getLogger("ProductRepositoryImpl")

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val offLookup: OffLookup,
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
        return dao.upsert(
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

    override suspend fun lookupForPreview(code: String): ScanCandidate? {
        if (code.isBlank()) return null
        findLocalByBarcode(code)?.let { return ScanCandidate.Persisted(it) }
        val off = offLookup.lookup(code) ?: return null
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
        return ScanCandidate.FromOff(
            barcode = code,
            name = capOffText(code, "name", name),
            brand = off.brands?.takeIf { it.isNotBlank() }?.let { capOffText(code, "brand", it) },
            imageUrl = gateImageUrl(code, off.imageUrl),
        )
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
