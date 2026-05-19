package de.docgerdsoft.pantrytracker.di

import android.content.Context
import androidx.room.Room
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.local.MIGRATION_1_2
import de.docgerdsoft.pantrytracker.data.remote.OffApiClient
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl

/**
 * Manual DI container for the app. Owns the single instance of every wired
 * dependency. Tests pass a `productRepository` directly (typically a fake);
 * production code uses [real] which wires up Room + the OFF client.
 */
class AppContainer(val productRepository: ProductRepository) {
    companion object {
        fun real(context: Context): AppContainer {
            // Intentionally no fallbackToDestructiveMigration: per spec §7, a schema mismatch
            // is "programmer error" and must crash, not silently wipe the user's pantry. Add
            // a proper Migration via .addMigrations(...) before bumping the @Database version.
            val db: AppDatabase = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                AppDatabase.DB_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .build()
            val offLookup: OffLookup = OffApiClient()
            return AppContainer(ProductRepositoryImpl(db.productDao(), offLookup))
        }
    }
}
