package de.docgerdsoft.pantrytracker.di

import android.content.Context
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl
import de.docgerdsoft.pantrytracker.ui.scan.DevSeedProducts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AppContainer(context: Context) {

    // Intentionally no fallbackToDestructiveMigration: per spec §7, a schema mismatch
    // is "programmer error" and must crash, not silently wipe the user's pantry. Add
    // a proper Migration via .addMigrations(...) before bumping the @Database version.
    private val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DB_NAME,
    ).build()

    val productRepository: ProductRepository = ProductRepositoryImpl(db.productDao())

    init {
        seedDevProductsIfEmpty()
    }

    // DEV-ONLY: seeds three known barcodes so M2's scan flow has something to
    // recognise. Remove this method AND DevSeedProducts.kt before merging the
    // Open Food Facts lookup feature (currently planned as M3).
    private fun seedDevProductsIfEmpty() {
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dao = db.productDao()
                    val existing = dao.observeAll().first()
                    if (existing.isEmpty()) {
                        val now = Clock.System.now()
                        @Suppress("DEPRECATION")
                        DevSeedProducts.list(now).forEach { dao.upsert(it) }
                    }
                }
            }.onFailure { e ->
                Log.e("AppContainer", "Dev-seed failed", e)
            }
        }
    }
}
