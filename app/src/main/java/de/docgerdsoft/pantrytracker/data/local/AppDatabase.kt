package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Product::class],
    version = 1,
    // Schema export is disabled until app/schemas/ is committed under version control.
    // Re-enable (true) at the same time as the first @Database version bump so the
    // pre-/post-migration schemas land in git for the Migration test.
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        const val DB_NAME = "pantry-tracker.db"
    }
}
