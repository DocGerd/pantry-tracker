package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val offLookup: OffLookup = NullOffLookup,
    private val clock: Clock = Clock.System,
) : ProductRepository {

    private object NullOffLookup : OffLookup {
        override suspend fun lookup(barcode: String): Nothing? = null
    }

    override fun observeProducts(): Flow<List<Product>> = dao.observeAll()

    override fun search(query: String): Flow<List<Product>> = dao.search(query)

    override suspend fun findById(id: Long): Product? = dao.findById(id)

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

    override suspend fun lookupForPreview(code: String): Product? {
        if (code.isBlank()) return null
        findLocalByBarcode(code)?.let { return it }
        val off = offLookup.lookup(code) ?: return null
        val name = off.productName?.takeIf { it.isNotBlank() } ?: return null
        val now = clock.now()
        return Product(
            id = 0,
            barcode = code,
            name = name,
            brand = off.brands?.takeIf { it.isNotBlank() },
            imageUrl = off.imageUrl?.takeIf { it.isNotBlank() },
            quantity = 0,
            createdAt = now,
            updatedAt = now,
        )
    }
}
