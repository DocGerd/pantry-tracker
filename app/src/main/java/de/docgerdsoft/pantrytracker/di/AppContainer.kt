package de.docgerdsoft.pantrytracker.di

import android.content.Context
import androidx.room.Room
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.remote.OffApiClient
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl

class AppContainer(context: Context) {

    // Intentionally no fallbackToDestructiveMigration: per spec §7, a schema mismatch
    // is "programmer error" and must crash, not silently wipe the user's pantry. Add
    // a proper Migration via .addMigrations(...) before bumping the @Database version.
    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DB_NAME,
    ).build()

    private val offLookup: OffLookup by lazy { OffApiClient() }

    val productRepository: ProductRepository = ProductRepositoryImpl(db.productDao(), offLookup)
}
