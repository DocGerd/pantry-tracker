package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
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
            // the brand/image loss is auditable without being logcat-noisy.
            logger.log(Level.INFO, "OFF hit for $code discarded — name blank, brand=${off.brands}")
            return null
        }
        return ScanCandidate.FromOff(
            barcode = code,
            name = name,
            brand = off.brands?.takeIf { it.isNotBlank() },
            imageUrl = off.imageUrl?.takeIf { it.isNotBlank() },
        )
    }
}
