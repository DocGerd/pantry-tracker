package de.docgerdsoft.pantrytracker.di

import android.content.Context
import androidx.room.Room
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.data.local.MIGRATION_1_2
import de.docgerdsoft.pantrytracker.data.local.MIGRATION_2_3
import de.docgerdsoft.pantrytracker.data.remote.OffApiClient
import de.docgerdsoft.pantrytracker.data.remote.OffLookup
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl
import de.docgerdsoft.pantrytracker.ui.scan.CameraSource

/**
 * Manual DI container for the app. Owns the single instance of every wired
 * dependency. Tests pass a `productRepository` directly (typically a fake);
 * production code uses [real] which wires up Room + the OFF client.
 *
 * [cameraSource] is the SR-75 test seam: instrumented Compose UI tests pass
 * a `FakeCameraSource` (from `app/src/androidTest/.../testfixtures/`) so
 * synthetic barcode events drive the scan flow without a real camera. In
 * production [real] wires it to `null` and `ScanScreen` renders the real
 * `CameraPreview` composable as before — no production behaviour change.
 */
class AppContainer(
    val productRepository: ProductRepository,
    val cameraSource: CameraSource? = null,
) {
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
            val offLookup: OffLookup = OffApiClient()
            return AppContainer(
                ProductRepositoryImpl(
                    dao = db.productDao(),
                    offLookup = offLookup,
                    offLookupCacheDao = db.offLookupCacheDao(),
                ),
            )
        }
    }
}
