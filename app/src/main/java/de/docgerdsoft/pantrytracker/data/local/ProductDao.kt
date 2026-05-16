package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Product?

    @Query("SELECT * FROM products WHERE barcode = :code LIMIT 1")
    suspend fun findByBarcode(code: String): Product?

    @Query(
        "SELECT * FROM products " +
            "WHERE name LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun search(query: String): Flow<List<Product>>

    @Upsert
    suspend fun upsert(product: Product): Long

    @Delete
    suspend fun delete(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)
}
