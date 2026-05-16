# Kitchen Inventory — v1 Design

**Status:** Approved for planning · **Date:** 2026-05-16

## 1. Purpose & success criterion

A standalone Android app that tracks the count of food and drink items in a single household kitchen, primarily via barcode scanning, with manual entry for non-barcoded items. Inventory data lives entirely on the device; the only network call is a public product-info lookup against [Open Food Facts](https://world.openfoodfacts.org) the first time an unknown barcode is seen.

**Success criterion (one sentence):** I can scan a Coke barcode three times after grocery shopping, scan it once after I drink one, and the home screen shows "Coke ×2" — all without internet, after the first time I scanned that product.

## 2. Scope

### In scope for v1

- Single user, single device, fully offline for inventory data.
- Whole-number quantity tracking (1, 2, 3 …) per product.
- Two scan flows from the home screen: **Scan to Add** and **Scan to Remove**. After a successful scan, the user adjusts the quantity (default 1, accepts any positive integer) before confirming — so one scan can move N units.
- Manual product entry (no barcode required) for items like fresh produce, leftovers, bulk goods.
- Open Food Facts API lookup on first scan of an unseen barcode, with manual-entry fallback when offline or no match.
- One flat inventory; live-searchable list on the home screen.
- Item detail screen for rename, manual quantity adjustment, and delete.

### Out of scope (deferred to v2+)

- Cloud sync, multi-device, or sharing.
- Expiration / best-before tracking and notifications.
- Low-stock alerts and shopping list.
- Locations (pantry / fridge / freezer) and categories / tags.
- Fractional or weight/volume quantities.
- Export / backup.
- iOS or any non-Android target.
- A persistent change history / audit log.

## 3. Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| Local DB | Room (with KSP) + kotlinx-datetime TypeConverter |
| HTTP | Ktor client (OkHttp engine) + kotlinx.serialization |
| Image loading | Coil 3 (Compose-native, disk-caches OFF product images) |
| Camera + barcode | CameraX + ML Kit `barcode-scanning` (on-device, no key, no network) |
| Async | Coroutines + Flow |
| Dependency injection | Manual constructor wiring via an `AppContainer` (no Hilt in v1) |
| Testing | JUnit 5, Turbine (`Flow` assertions), Robolectric (Room without an emulator), Compose UI test |
| Min SDK | 26 (Android 8.0) — covers >95% of devices and is the lowest version comfortable for ML Kit + CameraX + modern Compose |

Single `:app` module; organized internally by package (`ui`, `data.local`, `data.remote`, `repository`, `model`). Multi-module split is deferred.

## 4. Data model

One Room table; quantity lives on the product row.

```kotlin
@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,          // null for manual items
    val name: String,              // required
    val brand: String? = null,
    val imageUrl: String? = null,  // OFF image URL; cached by Coil
    val quantity: Int,             // ≥ 0
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### Invariants

- `quantity` is never negative. Remove operations clamp at 0.
- `barcode` is unique when present; multiple manual items may share `barcode = null`.
- `name` is non-empty.

### DAO surface

```kotlin
@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE barcode = :code LIMIT 1")
    suspend fun findByBarcode(code: String): Product?

    @Query(
        "SELECT * FROM products " +
        "WHERE name LIKE '%' || :q || '%' COLLATE NOCASE " +
        "ORDER BY name"
    )
    fun search(q: String): Flow<List<Product>>

    @Upsert suspend fun upsert(p: Product): Long
    @Delete suspend fun delete(p: Product)
}
```

## 5. Architecture

```
UI (Compose)                — HomeScreen, ScanScreen, DetailScreen + ViewModels
        │
        ▼
Repository                  — ProductRepository (single interface)
   ┌────┴────┐
   ▼         ▼
Room       Ktor + OFF client
```

The UI never decides "DB or network?" — it asks the repository for "the product behind this barcode" or "apply this quantity delta," and the repository encodes the local-first policy. OFF only ever supplies metadata (name, brand, image URL); it never inserts rows. Rows are inserted only when the user confirms.

### Repository contract (informal)

```kotlin
interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
    fun search(query: String): Flow<List<Product>>
    suspend fun findLocalByBarcode(code: String): Product?
    /**
     * Local first; if not present, attempt OFF; on miss or network failure,
     * returns null and the caller drops into manual-entry flow.
     * Returned Product has quantity = 0 and id = 0 until persisted.
     */
    suspend fun lookupForPreview(code: String): Product?
    suspend fun applyDelta(productId: Long, delta: Int)   // delta may be ±N
    suspend fun addNew(product: Product, initialQuantity: Int): Long
    suspend fun rename(productId: Long, newName: String)
    suspend fun delete(productId: Long)
}
```

### Threading & lifecycle

- DB and HTTP work on `Dispatchers.IO` (handled by Room and Ktor automatically).
- CameraX `ImageAnalysis` runs on a dedicated camera executor with backpressure strategy `STRATEGY_KEEP_ONLY_LATEST` (process the freshest frame, drop the rest).
- UI state per ViewModel is a `StateFlow<UiState>` collected via `collectAsStateWithLifecycle()`.

### Permissions

- `CAMERA` — runtime, prompted on entering the Scan screen.
- `INTERNET` (manifest only, no runtime prompt) — used solely for OFF lookups.

No notifications, no location, no storage permissions.

## 6. User flows & screens

### 6.1 Home (hybrid layout)

- Top: two large action buttons — **Scan to Add** (green +) and **Scan to Remove** (red −).
- Below: a search box.
- Below: the inventory list, sorted alphabetically (case-insensitive), one row per product showing `name + ×quantity` and an overflow menu / long-press for delete.
- Zero-quantity rows are visible but dimmed (so you can see "you have a product registered but currently zero of it").
- A small secondary action — **Add manually**, exposed as a FAB in the bottom-right corner — opens the manual-entry form without the camera.
- The list is bound to `ProductDao.observeAll()` (or `search(q)` when the search box is non-empty), so any change anywhere in the app refreshes the list automatically.

### 6.2 Scan screen (used for both Add and Remove)

- Fullscreen CameraX preview with an aim guide.
- ML Kit decodes EAN-13 / EAN-8 / UPC-A / UPC-E continuously.
- First decode triggers a short haptic vibration (no sound) and slides up a bottom sheet:
  - Product image (Coil) + name + brand.
  - Quantity stepper (default 1, accepts typed value).
  - Primary button: **Confirm Add** / **Confirm Remove** (color matches mode).
  - Cancel button returns to the camera preview.
- After Confirm, the bottom sheet dismisses and the camera returns to a "ready to scan next" state — so a six-item grocery haul is scan-confirm-scan-confirm-… without leaving the screen. Back button returns home.

#### Scan decision matrix

| Mode | Barcode in local DB | Behaviour |
|---|---|---|
| Add | Yes | Show product card from local row, stepper, Confirm `+N`. |
| Add | No, OFF reachable + match | Prefill from OFF JSON, image via Coil; on Confirm, INSERT new row with `quantity = N`. |
| Add | No, OFF miss or offline | Show a manual-entry form (name required, brand optional, image left null) instead of the OFF preview; on Confirm, INSERT row with `quantity = N`. |
| Remove | Yes | Stepper with `max = current quantity`; Confirm applies `−N`. |
| Remove | No | Friendly message: "Not in your inventory yet." with a button that switches mode to Add. |

### 6.3 Item detail

- Reached by tapping a row on the home screen.
- Shows image, name (inline-editable), brand (read-only), barcode (read-only), current quantity.
- Includes a manual `−/+` stepper to correct miscounts without scanning.
- Trash icon → confirm → `delete`.
- "Last updated" timestamp (relative; "2 hours ago") sourced from `updatedAt`.

## 7. Error handling policy

| Category | Examples | Rule |
|---|---|---|
| User-facing | Camera permission denied; OFF miss; remove of unknown barcode | Show as inline UI state. No toasts, no system dialogs except for delete confirmation. |
| Recoverable system | OFF returns 5xx; DNS fails; request timeout | Treat identically to "OFF miss" — drop into manual entry. No retries, no spinners that hang. |
| Programmer error | Room schema mismatch, non-null violation, unhandled coroutine exception | Let it crash. Don't swallow with `try/catch`. |

We do not silently swallow exceptions and we don't log-and-continue on data layer failures. If a DB write fails, the user is told and the UI doesn't lie about state.

## 8. Testing strategy

| Layer | Test type | Tool | Notes |
|---|---|---|---|
| Room DAO + migrations | Instrumented unit | Robolectric (JVM) | Highest-value: the DB is the source of truth. |
| Repository | JVM unit | JUnit + fake DAO + fake `OffApiClient` | Covers the local-first branching and OFF-miss → manual fallback. |
| OFF JSON parsing | JVM unit | Real OFF JSON fixtures in `test/resources/` | OFF schema has quirks (multilingual fields, missing fields) — pin against real responses. |
| ViewModels | JVM unit | Turbine for `StateFlow` assertions, fake repository | Asserts scan → preview → confirm state machines. |
| Compose screens | UI test | `createComposeRule()` | One smoke test per screen; no pixel comparisons. |
| End-to-end scan flow | Manual on device | Real Android phone | CameraX + ML Kit + permission flow is verified on hardware. |

Test naming convention: `methodUnderTest_state_expectedBehaviour`. Example: `applyDelta_quantityWouldGoNegative_clampsToZero`.

Out of v1 testing scope: ML Kit internals, Coil internals, Compose recomposition micro-behaviour.

## 9. Build sequence

Each milestone is a vertical slice that ends in a runnable, demonstrable app. Each gets its own tests at the layer where it lives, a manual smoke test on a real device, and a git commit.

| # | Milestone | Done when |
|---|---|---|
| 0 | Project skeleton | Empty Compose app launches on a device with correct `minSdk` / `targetSdk` / theme / package. |
| 1 | Room + manual add | "Add manually" works end-to-end: type a name + quantity, see it in the home list. Search and delete work. |
| 2 | Scan to Add for known barcodes | A handful of dev-only seeded products in the DB (removed before milestone 3): tap **Scan to Add**, aim at a real barcode that matches one of the seeds, see the bottom-sheet preview, confirm `+N`. |
| 3 | OFF lookup | New barcode → OFF fetched, preview shown, image loads via Coil, confirm inserts a new row. Offline / OFF miss falls back to manual entry. |
| 4 | Scan to Remove | Symmetric remove flow including the "not in inventory" message and `−N` clamped at 0. |
| 5 | Item detail + rename + manual stepper | Detail screen complete; bad names and miscounts can be fixed without scanning. |
| 6 | Polish pass | Theme/colours, app icon, empty-state illustration on home, camera-permission rationale, audit of error states. |

## 10. Definition of done (v1)

- All milestones complete and committed.
- Manual smoke checklist passes on at least one real Android device.
- APK installs and runs in airplane mode for everything except the first scan of a never-seen barcode.
- No silent error swallowing — every catch site either recovers visibly or rethrows.

## 11. Future-work parking lot (not built in v1)

These are noted so they can shape v1 decisions defensively but are not part of v1 scope:

- Sync layer (could later add a server but the local-first repository contract should make that additive).
- Expiration + notifications (would add an `expiresAt` column and a WorkManager job).
- Shopping list (would add a `minQuantity` column and a second screen).
- Locations (would add a `locationId` foreign key and a `locations` table).
- Backup / export (Room → JSON or sharing the DB file).
- iOS, via Kotlin Multiplatform sharing the domain + repository layers (the choice of Ktor and kotlinx-datetime is partly motivated by keeping this option open).
