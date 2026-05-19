package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Persistence interface for the OFF lookup cache. TTL enforcement lives in
 * [de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl] (not here)
 * so the SQL stays simple and tests can inject a fake clock.
 */
@Dao
interface OffLookupCacheDao {

    @Query("SELECT * FROM off_lookup_cache WHERE barcode = :barcode")
    suspend fun findByBarcode(barcode: String): OffLookupCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OffLookupCacheEntry)

    @Query("DELETE FROM off_lookup_cache WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
