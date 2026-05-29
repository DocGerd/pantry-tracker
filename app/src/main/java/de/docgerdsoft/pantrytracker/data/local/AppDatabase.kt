package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Product::class, OffLookupCacheEntry::class],
    // v2: adds off_lookup_cache (#48). v3: adds opt-in restock columns
    // (`lowLimit`, `defaultBuyAmount`) to `products` (#191). Requires
    // MIGRATION_1_2 + MIGRATION_2_3 wired at the Room.databaseBuilder site
    // or the database fails to open on real devices.
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun offLookupCacheDao(): OffLookupCacheDao

    companion object {
        const val DB_NAME = "pantry-tracker.db"
    }
}
