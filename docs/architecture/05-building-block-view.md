# 5. Building Block View

## 5.1 Level 1 — single module, four layers

The whole app lives in one Gradle module (`:app`) with the package root
`de.docgerdsoft.pantrytracker`. Inside the module the code splits into
four conventional layers:

```
                      ┌──────────────────────────────────────┐
                      │   ui.{home, scan, detail, theme,     │
                      │       common}                        │
                      │   — Compose screens + ViewModels +   │
                      │     typed UiState                    │
                      └────────────────┬─────────────────────┘
                                       │ depends on
                                       ▼
                      ┌──────────────────────────────────────┐
                      │   repository                         │
                      │   — ProductRepository interface +    │
                      │     ProductRepositoryImpl            │
                      │   — ScanCandidate sealed type        │
                      └────────────────┬─────────────────────┘
                                       │ depends on
                              ┌────────┴────────┐
                              ▼                 ▼
              ┌─────────────────────┐ ┌─────────────────────┐
              │   data.local        │ │   data.remote       │
              │   — Room database   │ │   — Ktor + OFF      │
              │   — Product entity  │ │     JSON envelope   │
              │   — ProductDao      │ │   — OffLookup port  │
              │   — Converters      │ │                     │
              └─────────────────────┘ └─────────────────────┘

                      ┌──────────────────────────────────────┐
                      │   di — AppContainer (wires the rest) │
                      └──────────────────────────────────────┘
```

Dependency direction is strictly downward: `ui → repository → data.*`.
`data.local` and `data.remote` do not know about each other; the
repository composes them. `ui` does not import from `data.*` at all.

## 5.2 The package tree (33 files in main)

| Package | Purpose | Key types |
|---------|---------|-----------|
| `de.docgerdsoft.pantrytracker` | App entry points | `PantryTrackerApp` (Application), `MainActivity`, `PantryTrackerNavGraph`, `Routes` |
| `…di` | Manual dependency injection | `AppContainer` |
| `…data.local` | Room persistence | `AppDatabase`, `Product`, `ProductDao`, `Converters` |
| `…data.remote` | OFF HTTP client | `OffApiClient`, `OffLookup` (interface), `OffProductResponse` (DTO) |
| `…repository` | Repository layer | `ProductRepository` (interface), `ProductRepositoryImpl`, `ScanCandidate` (sealed: `Persisted` / `FromOff`) |
| `…ui.home` | Home screen | `HomeScreen`, `HomeViewModel`, `HomeUiState`, `AddProductSheet` |
| `…ui.scan` | Scan flow | `ScanScreen`, `ScanViewModel`, `ScanUiState`, `ScanMode`, `CameraPermissionGate`, `CameraPermissionPhase` |
| `…ui.scan.components` | Scan sub-composables | `CameraPreview`, `ScanResultSheet` |
| `…ui.detail` | Product detail | `DetailScreen`, `DetailViewModel`, `DetailUiState` |
| `…ui.theme` | Material 3 theme | `Color` (Fern + AddGreen/RemoveRed), `Theme`, `Type` |
| `…ui.common` | Cross-screen helpers | `QuantityInput`, `RelativeTime` |

## 5.3 Level 2 — `repository` layer

The repository is the contract that the UI talks to. It is intentionally
narrow:

```kotlin
interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun search(query: String): Flow<List<Product>>
    suspend fun findById(id: Long): Product?
    fun observeById(id: Long): Flow<Product?>
    suspend fun findLocalByBarcode(code: String): Product?
    suspend fun lookupForPreview(code: String): ScanCandidate?
    suspend fun addNew(name: String, brand: String?, barcode: String?,
                      imageUrl: String?, initialQuantity: Int): Long
    suspend fun applyDelta(productId: Long, delta: Int)
    suspend fun rename(productId: Long, newName: String)
    suspend fun delete(productId: Long)
}
```

`ProductRepositoryImpl` composes:
- a `ProductDao` (Room) for all local reads/writes
- an `OffLookup` (interface, default impl `OffApiClient`) for the one
  network call inside `lookupForPreview`

`lookupForPreview` is the only method that touches the network. It is
local-first: hits Room for the barcode, returns `ScanCandidate.Persisted`
on a hit; otherwise calls OFF, returns `ScanCandidate.FromOff` on a hit,
`null` on miss/failure (per [solution strategy](04-solution-strategy.md#41-local-first-inventory-network-optional-enrichment)).

## 5.4 Level 2 — `ui.scan` package

The most complex screen. Three orthogonal building blocks:

| Block | Type | Responsibility |
|-------|------|----------------|
| `ScanViewModel` | ViewModel | Phase machine (Idle / Loading / Preview / ManualEntry / NotInInventory / Error); cancels in-flight jobs on phase changes; calls the repository on confirm |
| `CameraPermissionGate` + `CameraPermissionGateContent` | Composable pair | Stateful gate (lifecycle observer, launcher, settings intent) wrapping a pure renderer driven by `CameraPermissionPhase` |
| `CameraPreview` | Composable | CameraX preview + ML Kit barcode analyzer running on a dedicated executor; transient decode failures logged at FINE, permanent failures surfaced via `onCameraError` |
| `ScanResultSheet` (+ siblings) | Composables | Renders the active phase (preview, manual entry, not-in-inventory, error) as a bottom sheet |

The flow:
1. NavGraph routes `scan/add` or `scan/remove` to `CameraPermissionGate { ScanScreen(...) }`.
2. Gate computes the phase; if `Granted`, renders the wrapped content.
3. `ScanScreen` renders `CameraPreview` + a sheet for the current ViewModel phase.
4. ML Kit decode → `onBarcode` → `ScanViewModel.onBarcodeDecoded` → repository → phase change → recomposition.

Detailed flow diagrams: see [runtime view](06-runtime-view.md).
