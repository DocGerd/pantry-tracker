package de.docgerdsoft.pantrytracker.repository

import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.data.local.ProductDao
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val clock: Clock = Clock.System,
) : ProductRepository {

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
}
