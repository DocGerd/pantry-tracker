# Pantry Tracker · Milestone 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the home screen's **Scan to Add** button to a working camera + barcode-scanning flow. Decoding a barcode that exists in the local DB shows a bottom sheet with the product preview and a quantity stepper; confirming applies `+N` via `ProductRepository.applyDelta`. Decoding an *unknown* barcode shows a friendly "not in your inventory yet" state for now — Open Food Facts lookup arrives in M3.

**Architecture:** Add Compose Navigation (Home ↔ Scan) so M5 can extend it. New `ui.scan` package mirrors `ui.home`'s shape: `ScanUiState`, `ScanViewModel` (with a 3-state machine `Idle → Decoded(barcode) → Confirmed`), `ScanScreen` composable. CameraX `Preview` + `ImageAnalysis` wire ML Kit's `BarcodeScanning` as the analyzer; the analyzer runs on a dedicated executor with `STRATEGY_KEEP_ONLY_LATEST`. Camera permission is requested via the standard `ActivityResultContracts.RequestPermission` API (no Accompanist).

**Tech Stack additions (on top of M1):** `androidx.navigation:navigation-compose` 2.8.x · `androidx.camera:camera-camera2 / -lifecycle / -view` 1.4.x · `com.google.mlkit:barcode-scanning` 17.3.x. No Accompanist Permissions (deprecated; use the official Activity Result API).

---

## Pre-flight: branch + GitHub Issue

```bash
git -C /home/pkuhn/inventory-androic switch main
git -C /home/pkuhn/inventory-androic pull --ff-only
git -C /home/pkuhn/inventory-androic switch -c milestone-2-scan-add-known
```

The merged M2 PR will close [Issue #3](https://github.com/DocGerd/pantry-tracker/issues/3).

## File layout (delta from main)

```
app/src/main/java/de/docgerdsoft/pantrytracker/
├── MainActivity.kt                      (modified: host nav graph)
├── PantryTrackerNavGraph.kt             (new: Home ↔ Scan routes)
├── di/
│   └── AppContainer.kt                  (modified: seed dev products on first run)
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt                (modified: wire Scan-to-Add button to nav)
│   └── scan/                            (new package)
│       ├── ScanUiState.kt
│       ├── ScanViewModel.kt
│       ├── ScanScreen.kt
│       ├── components/
│       │   ├── CameraPreview.kt         (CameraX + ML Kit ImageAnalysis)
│       │   ├── PermissionRequest.kt     (camera-permission rationale UI)
│       │   └── ScanResultSheet.kt       (bottom sheet for known barcode)
│       └── DevSeedProducts.kt           (dev-only seed list; removed in M3)

app/src/main/AndroidManifest.xml         (modified: add CAMERA permission)
app/src/test/java/de/docgerdsoft/pantrytracker/
└── ui/scan/
    └── ScanViewModelTest.kt             (new: state-machine tests)

gradle/libs.versions.toml                (modified: nav-compose, camerax, mlkit aliases)
app/build.gradle.kts                     (modified: declare new deps)
```

## Test commands you'll keep typing

| What | Command |
|---|---|
| All JVM unit tests | `./gradlew testDebugUnitTest` |
| Just scan-VM | `./gradlew testDebugUnitTest --tests "*ScanViewModelTest*"` |
| Build APK | `./gradlew assembleDebug` |
| Install on device | `./gradlew installDebug` |

Camera + decode end-to-end requires a real device or emulator with a virtual camera; manual smoke test only.

---

## Task 1: Add navigation-compose + CameraX + ML Kit dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions and library aliases to `gradle/libs.versions.toml`**

In the `[versions]` block add:

```toml
navigationCompose = "2.8.5"
camerax = "1.4.0"
mlkitBarcodeScanning = "17.3.0"
```

In the `[libraries]` block add:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcodeScanning" }
```

- [ ] **Step 2: Add `implementation` lines to `app/build.gradle.kts` `dependencies` block**

After the existing `implementation(libs.androidx.lifecycle.runtime.compose)` line and before the Room block, add:

```kotlin
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.barcode.scanning)
```

- [ ] **Step 3: Verify the version catalog is well-formed (syntax-only check, since we cannot build locally)**

```bash
grep -E "(navigationCompose|camerax|mlkit)" /home/pkuhn/inventory-androic/gradle/libs.versions.toml
```

Expected: shows the three new version entries.

- [ ] **Step 4: Commit**

```bash
git -C /home/pkuhn/inventory-androic add gradle/libs.versions.toml app/build.gradle.kts
git -C /home/pkuhn/inventory-androic commit -m "M2: Add navigation-compose, CameraX, ML Kit barcode-scanning deps"
```

---

## Task 2: Manifest — declare CAMERA permission + uses-feature

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permission and feature declarations**

Insert these BEFORE the `<application>` tag (right after the opening `<manifest>`):

```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature
        android:name="android.hardware.camera.any"
        android:required="false" />
```

`required="false"` is intentional: an emulator without a camera should still install the app (manual entry still works). Real-world Pixel/Samsung devices always have cameras.

- [ ] **Step 2: Verify the manifest still validates**

```bash
xmllint --noout /home/pkuhn/inventory-androic/app/src/main/AndroidManifest.xml && echo "manifest OK"
```

Expected: `manifest OK`.

- [ ] **Step 3: Commit**

```bash
git -C /home/pkuhn/inventory-androic add app/src/main/AndroidManifest.xml
git -C /home/pkuhn/inventory-androic commit -m "M2: Declare CAMERA permission + uses-feature in manifest"
```

---

## Task 3: Dev-seed products on first launch

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/DevSeedProducts.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt`

This is **dev-only**, removed in M3 when OFF lookup arrives. The whole file gets deleted then.

- [ ] **Step 1: Create the seed list**

`app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/DevSeedProducts.kt`:

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.data.local.Product
import kotlinx.datetime.Instant

/**
 * DEV-ONLY: a handful of barcoded products preloaded into the DB on first launch so
 * M2 scan flow has something to recognise without M3's Open Food Facts lookup.
 *
 * DELETE this file (and the `seedDevProductsIfEmpty` call in [de.docgerdsoft.pantrytracker.di.AppContainer])
 * as the first step of Milestone 3.
 */
internal object DevSeedProducts {
    fun list(now: Instant): List<Product> = listOf(
        Product(barcode = "5449000000996", name = "Coca-Cola 0.5L", brand = "Coca-Cola",
            quantity = 0, createdAt = now, updatedAt = now),
        Product(barcode = "8001505005707", name = "Spaghetti 500g", brand = "Barilla",
            quantity = 0, createdAt = now, updatedAt = now),
        Product(barcode = "4006381333931", name = "Sparkling Water 1L", brand = "Gerolsteiner",
            quantity = 0, createdAt = now, updatedAt = now),
    )
}
```

- [ ] **Step 2: Modify `AppContainer.kt` to seed on first launch**

Replace the file with:

```kotlin
package de.docgerdsoft.pantrytracker.di

import android.content.Context
import androidx.room.Room
import de.docgerdsoft.pantrytracker.data.local.AppDatabase
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import de.docgerdsoft.pantrytracker.repository.ProductRepositoryImpl
import de.docgerdsoft.pantrytracker.ui.scan.DevSeedProducts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    // DEV-ONLY: seeds three known barcodes so M2's scan flow has something to recognise.
    // Remove this call AND DevSeedProducts.kt as the first task of M3.
    private fun seedDevProductsIfEmpty() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val dao = db.productDao()
            val existing = dao.observeAll().first()
            if (existing.isEmpty()) {
                val now = Clock.System.now()
                DevSeedProducts.list(now).forEach { dao.upsert(it) }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git -C /home/pkuhn/inventory-androic add \
  app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/DevSeedProducts.kt \
  app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: Dev-seed three known barcodes on first launch (removed in M3)"
```

---

## Task 4: `ScanUiState` (state machine)

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanUiState.kt`

- [ ] **Step 1: Create the state class**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import de.docgerdsoft.pantrytracker.data.local.Product

/** UI state for the Scan screen. The phase is a small sealed hierarchy modelling the
 *  scan → decode → confirm flow; everything else lives outside the phase so it
 *  survives transitions. */
data class ScanUiState(
    val phase: Phase = Phase.Idle,
) {
    sealed interface Phase {
        /** Camera is open, waiting for a barcode. */
        data object Idle : Phase

        /** A barcode was decoded and matched a row in the local DB. */
        data class Preview(
            val product: Product,
            val pendingQuantity: Int,
        ) : Phase

        /** A barcode was decoded but is not in the local DB.
         *  For M2 this is the terminal "Not seeded" state. M3 replaces this with the
         *  Open Food Facts lookup + manual-entry fallback flow. */
        data class UnknownBarcode(val barcode: String) : Phase
    }
}
```

- [ ] **Step 2: Commit**

```bash
git -C /home/pkuhn/inventory-androic add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanUiState.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: ScanUiState with Idle / Preview / UnknownBarcode phases"
```

---

## Task 5: `ScanViewModel` + tests (TDD)

**Files:**
- Test: `app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import app.cash.turbine.test
import de.docgerdsoft.pantrytracker.data.local.Product
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private lateinit var fake: FakeProductRepository
    private lateinit var vm: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeProductRepository()
        vm = ScanViewModel(fake)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialPhase_isIdle() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(ScanUiState.Phase.Idle, state.phase)
        }
    }

    @Test
    fun onBarcodeDecoded_knownBarcode_movesToPreviewWithDefaultQuantityOne() = runTest {
        val now = Clock.System.now()
        val product = Product(id = 1, barcode = "5449000000996", name = "Coca-Cola 0.5L",
            quantity = 0, createdAt = now, updatedAt = now)
        fake.seed(product)

        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onBarcodeDecoded("5449000000996")
            val state = awaitItem()
            val preview = state.phase as ScanUiState.Phase.Preview
            assertEquals("Coca-Cola 0.5L", preview.product.name)
            assertEquals(1, preview.pendingQuantity)
        }
    }

    @Test
    fun onBarcodeDecoded_unknownBarcode_movesToUnknownBarcode() = runTest {
        vm.uiState.test {
            awaitItem() // initial Idle
            vm.onBarcodeDecoded("0000000000000")
            val state = awaitItem()
            val unknown = state.phase as ScanUiState.Phase.UnknownBarcode
            assertEquals("0000000000000", unknown.barcode)
        }
    }

    @Test
    fun onBarcodeDecoded_ignoredWhenAlreadyInPreviewPhase() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "5449000000996", name = "Coca-Cola",
            quantity = 0, createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("5449000000996")
            awaitItem() // Preview
            vm.onBarcodeDecoded("5449000000996") // duplicate scan while sheet is open
            expectNoEvents()
        }
    }

    @Test
    fun setQuantity_belowOne_clampsToOne() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem() // Preview pendingQuantity=1
            vm.setQuantity(0)
            expectNoEvents() // already at 1, no new emission
            vm.setQuantity(-5)
            expectNoEvents()
            assertEquals(1, (vm.uiState.value.phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun setQuantity_positiveValue_updatesPreview() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem()
            vm.setQuantity(6)
            val state = awaitItem()
            assertEquals(6, (state.phase as ScanUiState.Phase.Preview).pendingQuantity)
        }
    }

    @Test
    fun confirmAdd_appliesDeltaAndReturnsToIdle() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem()
            vm.setQuantity(3)
            awaitItem()
            vm.confirmAdd()
            val state = awaitItem()
            assertEquals(ScanUiState.Phase.Idle, state.phase)
        }
        assertEquals(1L to 3, fake.lastDelta)
    }

    @Test
    fun cancelPreview_returnsToIdleWithoutCallingRepository() = runTest {
        val now = Clock.System.now()
        fake.seed(Product(id = 1, barcode = "x", name = "P", quantity = 0,
            createdAt = now, updatedAt = now))

        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("x")
            awaitItem()
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
        assertNull(fake.lastDelta)
    }

    @Test
    fun dismissUnknownBarcode_returnsToIdle() = runTest {
        vm.uiState.test {
            awaitItem()
            vm.onBarcodeDecoded("nope")
            assertTrue(awaitItem().phase is ScanUiState.Phase.UnknownBarcode)
            vm.dismissPreview()
            assertEquals(ScanUiState.Phase.Idle, awaitItem().phase)
        }
    }

    private class FakeProductRepository : ProductRepository {
        private val byBarcode = mutableMapOf<String, Product>()
        var lastDelta: Pair<Long, Int>? = null

        fun seed(p: Product) {
            byBarcode[p.barcode!!] = p
        }

        override fun observeProducts(): Flow<List<Product>> = MutableStateFlow(emptyList<Product>()).asStateFlow()
        override fun search(query: String): Flow<List<Product>> = MutableStateFlow(emptyList<Product>()).asStateFlow()
        override suspend fun findById(id: Long): Product? = byBarcode.values.firstOrNull { it.id == id }
        override suspend fun findLocalByBarcode(code: String): Product? = byBarcode[code]
        override suspend fun addNew(name: String, brand: String?, barcode: String?, imageUrl: String?, initialQuantity: Int): Long = 0L
        override suspend fun applyDelta(productId: Long, delta: Int) {
            lastDelta = productId to delta
        }
        override suspend fun rename(productId: Long, newName: String) = Unit
        override suspend fun delete(productId: Long) = Unit
    }
}
```

- [ ] **Step 2: Run the test — expect compile error (`Unresolved reference: ScanViewModel`)**

```bash
./gradlew testDebugUnitTest --tests "*ScanViewModelTest*" 2>&1 | tail -10
```

(Skip the run if you have no JDK locally; CI will exercise it.)

- [ ] **Step 3: Create `ScanViewModel.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Called from the camera analyzer when ML Kit successfully decodes a barcode. */
    fun onBarcodeDecoded(barcode: String) {
        if (_uiState.value.phase !is ScanUiState.Phase.Idle) return
        viewModelScope.launch {
            val product = repository.findLocalByBarcode(barcode)
            _uiState.update {
                it.copy(
                    phase = if (product != null) {
                        ScanUiState.Phase.Preview(product, pendingQuantity = 1)
                    } else {
                        ScanUiState.Phase.UnknownBarcode(barcode)
                    },
                )
            }
        }
    }

    fun setQuantity(value: Int) {
        val phase = _uiState.value.phase as? ScanUiState.Phase.Preview ?: return
        val clamped = value.coerceAtLeast(1)
        if (clamped == phase.pendingQuantity) return
        _uiState.update { it.copy(phase = phase.copy(pendingQuantity = clamped)) }
    }

    fun confirmAdd() {
        val phase = _uiState.value.phase as? ScanUiState.Phase.Preview ?: return
        viewModelScope.launch {
            repository.applyDelta(productId = phase.product.id, delta = phase.pendingQuantity)
            _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
        }
    }

    /** Used by the Cancel button on the preview sheet AND the "Not in inventory" close button. */
    fun dismissPreview() {
        _uiState.update { it.copy(phase = ScanUiState.Phase.Idle) }
    }
}
```

- [ ] **Step 4: Run tests — expect green**

```bash
./gradlew testDebugUnitTest --tests "*ScanViewModelTest*"
```

- [ ] **Step 5: Commit**

```bash
git -C /home/pkuhn/inventory-androic add \
  app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt \
  app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: ScanViewModel with 3-phase state machine + tests"
```

---

## Task 6: `CameraPreview` composable (CameraX + ML Kit analyzer)

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/CameraPreview.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan.components

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX preview with a ML Kit barcode analyzer. Calls [onBarcode] with the
 * decoded raw value (EAN-13/EAN-8/UPC-A/UPC-E only). The caller is responsible
 * for de-duplicating rapid repeat detections (the ScanViewModel does that).
 */
@Composable
fun CameraPreview(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember<ExecutorService> { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build()
        )
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val cameraProvider = providerFuture.get()

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstNotNullOfOrNull { it.rawValue }
                                        ?.let(onBarcode)
                                }
                                .addOnFailureListener { e ->
                                    Log.w("CameraPreview", "ML Kit decode failed", e)
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    } }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git -C /home/pkuhn/inventory-androic add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/CameraPreview.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: CameraPreview composable with CameraX + ML Kit barcode analyzer"
```

---

## Task 7: `PermissionRequest` composable

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/PermissionRequest.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * Wraps [content] in a camera-permission gate. While the permission has not been
 * granted, shows a rationale + Grant button. Tapping Grant launches the system
 * permission dialog. If the user has previously denied "don't ask again", the
 * button label switches to "Open settings" and routes them to the app-settings page.
 */
@Composable
fun CameraPermissionGate(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (!ok) permanentlyDenied = !context.shouldShowCameraRationale()
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Camera permission needed",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pantry Tracker uses the camera to scan barcodes on products you add or remove. The camera image is processed entirely on-device — nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (permanentlyDenied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                } else {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            }) {
                Text(if (permanentlyDenied) "Open settings" else "Grant permission")
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowCameraRationale(): Boolean {
    // shouldShowRequestPermissionRationale is on Activity, not Context — we'd need
    // to cast. For v1 simplicity, assume any denied response after the first launch
    // means "permanently denied" and route to settings; the user can always try the
    // Grant button first and let the system show its native dialog.
    return false
}

private const val dp = 1 // anchor to ensure dp import retained (not actually used)
private val Int.dpInt get() = this // ditto
```

Note the two anchors at the bottom: those are dead-code holders to prevent a lint cleanup from removing the `import androidx.compose.ui.unit.dp` line. **Remove them now** — `Modifier.height(8.dp)` etc. already use the unit; just import `androidx.compose.ui.unit.dp` explicitly:

```kotlin
import androidx.compose.ui.unit.dp
```

…and delete the trailing two lines. Resulting file has just the composable, its two `Context` helpers, and clean imports.

- [ ] **Step 2: Commit**

```bash
git -C /home/pkuhn/inventory-androic add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/PermissionRequest.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: CameraPermissionGate composable with rationale + settings fallback"
```

---

## Task 8: `ScanResultSheet` composable

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/ScanResultSheet.kt`

- [ ] **Step 1: Create the bottom sheet**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.docgerdsoft.pantrytracker.data.local.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreviewSheet(
    product: Product,
    pendingQuantity: Int,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(product.name, style = MaterialTheme.typography.titleLarge)
            product.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onQuantityChange(pendingQuantity - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrement quantity")
                }
                Text(
                    text = pendingQuantity.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                IconButton(onClick = { onQuantityChange(pendingQuantity + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increment quantity")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm) { Text("Confirm Add") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownBarcodeSheet(
    barcode: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Not in your inventory yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Barcode: $barcode\n\nThis barcode isn't seeded yet. Open Food Facts lookup arrives in the next milestone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git -C /home/pkuhn/inventory-androic add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/ScanResultSheet.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: ScanPreviewSheet + UnknownBarcodeSheet composables"
```

---

## Task 9: `ScanScreen` composable (haptic + glue)

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanScreen.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPermissionGate
import de.docgerdsoft.pantrytracker.ui.scan.components.CameraPreview
import de.docgerdsoft.pantrytracker.ui.scan.components.ScanPreviewSheet
import de.docgerdsoft.pantrytracker.ui.scan.components.UnknownBarcodeSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    // Haptic on transition into Preview/UnknownBarcode (i.e. on each successful decode).
    LaunchedEffect(state.phase) {
        if (state.phase !is ScanUiState.Phase.Idle) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan to Add") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            CameraPermissionGate {
                CameraPreview(
                    onBarcode = viewModel::onBarcodeDecoded,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        when (val phase = state.phase) {
            is ScanUiState.Phase.Preview -> {
                ScanPreviewSheet(
                    product = phase.product,
                    pendingQuantity = phase.pendingQuantity,
                    onQuantityChange = viewModel::setQuantity,
                    onConfirm = viewModel::confirmAdd,
                    onDismiss = viewModel::dismissPreview,
                )
            }
            is ScanUiState.Phase.UnknownBarcode -> {
                UnknownBarcodeSheet(
                    barcode = phase.barcode,
                    onDismiss = viewModel::dismissPreview,
                )
            }
            ScanUiState.Phase.Idle -> Unit
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git -C /home/pkuhn/inventory-androic add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanScreen.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: ScanScreen composable wiring camera + permission + sheets"
```

---

## Task 10: Navigation graph + HomeScreen wire-up

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/MainActivity.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt`

- [ ] **Step 1: Create the nav graph**

```kotlin
package de.docgerdsoft.pantrytracker

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.docgerdsoft.pantrytracker.di.AppContainer
import de.docgerdsoft.pantrytracker.ui.home.HomeScreen
import de.docgerdsoft.pantrytracker.ui.home.HomeViewModel
import de.docgerdsoft.pantrytracker.ui.scan.ScanScreen
import de.docgerdsoft.pantrytracker.ui.scan.ScanViewModel

object Routes {
    const val HOME = "home"
    const val SCAN_ADD = "scan/add"
}

@Composable
fun PantryTrackerNavGraph(container: AppContainer) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HomeViewModel(container.productRepository) }
                },
            )
            HomeScreen(
                viewModel = vm,
                onScanAddClick = { navController.navigate(Routes.SCAN_ADD) },
                onScanRemoveClick = { /* wired in M4 */ },
            )
        }
        composable(Routes.SCAN_ADD) {
            val vm: ScanViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ScanViewModel(container.productRepository) }
                },
            )
            ScanScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
```

- [ ] **Step 2: Update `HomeScreen.kt` signature to accept the two scan callbacks**

Change the function signature from `HomeScreen(viewModel: HomeViewModel)` to:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanAddClick: () -> Unit,
    onScanRemoveClick: () -> Unit,
) {
```

Then replace the two existing TODO inline comments in `ScanButtonsRow`:

```kotlin
            ScanButtonsRow(
                onAddClick = onScanAddClick,
                onRemoveClick = onScanRemoveClick,
            )
```

Remove the now-stale `// TODO: wire to barcode-scan ...` comments.

- [ ] **Step 3: Replace `MainActivity.kt`**

```kotlin
package de.docgerdsoft.pantrytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.docgerdsoft.pantrytracker.ui.theme.PantryTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PantryTrackerApp).container
        setContent {
            PantryTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PantryTrackerNavGraph(container = container)
                }
            }
        }
    }
}
```

The hand-rolled `viewModels<HomeViewModel> { ... }` delegate is no longer needed; ViewModels are now scoped to each composable destination via `viewModel { factory }`.

- [ ] **Step 4: Update the existing `HomeScreenTest` androidTest to pass the new callback parameters**

In `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt`, every `HomeScreen(viewModel = vm)` call becomes:

```kotlin
HomeScreen(viewModel = vm, onScanAddClick = {}, onScanRemoveClick = {})
```

- [ ] **Step 5: Commit**

```bash
git -C /home/pkuhn/inventory-androic add \
  app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt \
  app/src/main/java/de/docgerdsoft/pantrytracker/MainActivity.kt \
  app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt \
  app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt
git -C /home/pkuhn/inventory-androic commit -m "M2: NavHost + wire Scan-to-Add button to scan route"
```

---

## Task 11: Push branch + open PR (DO NOT MERGE)

- [ ] **Step 1: Push**

```bash
git -C /home/pkuhn/inventory-androic push --set-upstream origin milestone-2-scan-add-known
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --repo DocGerd/pantry-tracker \
  --title "Milestone 2: Scan to Add for known barcodes" \
  --body "$(cat <<'EOF'
Closes #3

Adds CameraX + ML Kit barcode scanning end-to-end for barcodes that already exist in the local DB. Decoding a known barcode opens a bottom sheet with the product preview and a quantity stepper; confirming applies +N via ProductRepository.applyDelta. Decoding an unknown barcode shows a friendly "not in your inventory yet" sheet — Open Food Facts lookup lands in M3.

Three dev-only seeded barcodes (Coca-Cola, Barilla Spaghetti, Gerolsteiner) are inserted on first launch so the scan flow has something to recognise before M3 ships. The seed file and the AppContainer.init call get deleted as the first task of M3.

Spec deviations: none. Plan deviations: navigation-compose was added (the plan implied a state-based screen switch but the doc didn't pin it down) — Compose Navigation is the idiomatic Android choice and gives us the scaffolding M5's Detail screen will need.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --base main
```

- [ ] **Step 3: Watch CI**

```bash
gh pr checks --watch
```

- [ ] **Step 4: Report PR URL and CI status. DO NOT MERGE.**

---

## Done — what shipped

- Scan to Add button on the home screen → camera screen.
- Camera permission UX (rationale + settings fallback).
- CameraX preview + ML Kit decoding EAN-13/EAN-8/UPC-A/UPC-E.
- Bottom sheet with quantity stepper on known-barcode decode; Confirm applies +N.
- Friendly "not in inventory yet" sheet for unknown barcodes (placeholder for M3).
- Haptic feedback on every successful decode.
- 9 new `ScanViewModelTest` cases covering the full state machine.

## Deferred to later milestones

| Decision | Comes back in |
|---|---|
| Open Food Facts lookup + manual-entry fallback for unknown barcodes | Milestone 3 |
| Scan-to-Remove flow (symmetric to Add) | Milestone 4 |
| Item detail screen + rename + manual stepper | Milestone 5 |
| App icon, theme polish, lint sweep | Milestone 6 |
| Dev-seeded products | Removed in Milestone 3 task 1 |
