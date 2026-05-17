# Milestone 5 — Item Detail Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tap row → detail screen with inline-editable name, manual ±1 stepper, trash-confirm-delete. Auto-pop on delete (via observeById → null). Relative-time "Last updated".

**Architecture:** New `ui/detail/` feature folder mirroring `ui/home/`. New `repository.observeById(id): Flow<Product?>` + matching DAO query. Pure-function `RelativeTime` formatter in `ui/common/`. Nav route `detail/{productId}` with Long arg.

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 + Coil + kotlinx-datetime (all existing). No new dependencies.

**Tracking issue:** [#6](https://github.com/DocGerd/pantry-tracker/issues/6)
**Spec:** `docs/superpowers/specs/2026-05-17-milestone-5-item-detail-design.md`

---

### Task 1: Repository + DAO — `observeById(id): Flow<Product?>`

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/data/local/ProductDao.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepository.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt`

- [ ] **Step 1: ProductDao — add the @Query**

```kotlin
@Query("SELECT * FROM Product WHERE id = :id")
fun observeById(id: Long): Flow<Product?>
```

Place next to the existing `observeAll`/`search` queries.

- [ ] **Step 2: ProductRepository interface — add the method**

```kotlin
fun observeById(id: Long): Flow<Product?>
```

- [ ] **Step 3: ProductRepositoryImpl — implement**

```kotlin
override fun observeById(id: Long): Flow<Product?> = dao.observeById(id)
```

Simple pass-through; no transformation needed. The Flow emits the current row, then re-emits whenever the row updates (or null when deleted).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/data/local/ProductDao.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepository.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/repository/ProductRepositoryImpl.kt
git commit -m "feat(m5): observeById Flow<Product?> on repo + dao"
```

---

### Task 2: `RelativeTime` formatter + unit tests

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/common/RelativeTime.kt`
- Create: `app/src/test/java/de/docgerdsoft/pantrytracker/ui/common/RelativeTimeTest.kt`

- [ ] **Step 1: Implement the formatter**

```kotlin
package de.docgerdsoft.pantrytracker.ui.common

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/** Bucketed human-readable relative time. "just now" if delta < 60s or
 *  negative (clock drift); coarser buckets up to "N months ago" (~30d).
 *  Pure function — no Android dependencies; fully testable. */
object RelativeTime {
    fun format(then: Instant, now: Instant): String {
        val delta = now - then
        return when {
            delta < 60.seconds -> "just now"
            delta < 60.minutes -> pluralize(delta.inWholeMinutes.toInt(), "minute")
            delta < 24.hours -> pluralize(delta.inWholeHours.toInt(), "hour")
            delta < 7.days -> pluralize(delta.inWholeDays.toInt(), "day")
            delta < 28.days -> pluralize((delta.inWholeDays / 7).toInt(), "week")
            else -> pluralize((delta.inWholeDays / 30).toInt(), "month")
        }
    }

    private fun pluralize(n: Int, unit: String): String =
        if (n == 1) "1 $unit ago" else "$n ${unit}s ago"
}
```

- [ ] **Step 2: Tests covering all five buckets + boundaries + negative delta**

```kotlin
package de.docgerdsoft.pantrytracker.ui.common

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = Instant.fromEpochSeconds(1_000_000)
    private fun back(d: kotlin.time.Duration) = now - d

    @Test fun justNow_under60s() {
        assertEquals("just now", RelativeTime.format(back(0.seconds), now))
        assertEquals("just now", RelativeTime.format(back(59.seconds), now))
    }

    @Test fun minutes_from60sTo59min() {
        assertEquals("1 minute ago", RelativeTime.format(back(60.seconds), now))
        assertEquals("1 minute ago", RelativeTime.format(back(1.minutes), now))
        assertEquals("2 minutes ago", RelativeTime.format(back(2.minutes), now))
        assertEquals("59 minutes ago", RelativeTime.format(back(59.minutes), now))
    }

    @Test fun hours_from60minTo23h() {
        assertEquals("1 hour ago", RelativeTime.format(back(60.minutes), now))
        assertEquals("23 hours ago", RelativeTime.format(back(23.hours), now))
    }

    @Test fun days_from24hTo6d() {
        assertEquals("1 day ago", RelativeTime.format(back(24.hours), now))
        assertEquals("6 days ago", RelativeTime.format(back(6.days), now))
    }

    @Test fun weeks_from7dTo27d() {
        assertEquals("1 week ago", RelativeTime.format(back(7.days), now))
        assertEquals("3 weeks ago", RelativeTime.format(back(21.days), now))
    }

    @Test fun months_from28dOnward() {
        assertEquals("0 months ago", RelativeTime.format(back(28.days), now)) // 28/30 = 0 — boundary quirk acceptable
        assertEquals("1 month ago", RelativeTime.format(back(30.days), now))
        assertEquals("6 months ago", RelativeTime.format(back(180.days), now))
    }

    @Test fun negativeDelta_returnsJustNow() {
        // future timestamp (clock drift) — don't render "-1 minutes ago".
        assertEquals("just now", RelativeTime.format(now + 5.seconds, now))
    }
}
```

Note the 28-day boundary quirk: at delta=28d, weeks-bucket exits (< 28d is false), months-bucket activates, and 28/30 integer-divides to 0 → "0 months ago". Acceptable for v1 (the user is unlikely to see exactly-28-days). If it becomes a complaint, tweak the bucket threshold or the divisor.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/common/RelativeTime.kt \
        app/src/test/java/de/docgerdsoft/pantrytracker/ui/common/RelativeTimeTest.kt
git commit -m "feat(m5): RelativeTime formatter + tests for all 5 buckets"
```

---

### Task 3: `DetailViewModel` + `DetailUiState`

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailUiState.kt`
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailViewModel.kt`

- [ ] **Step 1: DetailUiState**

```kotlin
package de.docgerdsoft.pantrytracker.ui.detail

import de.docgerdsoft.pantrytracker.data.local.Product

data class DetailUiState(
    val product: Product? = null,
    /** True once observeById has emitted a non-null Product at least once. Used to
     *  distinguish initial-loading null from deleted null (the latter triggers nav-back). */
    val everSeen: Boolean = false,
    /** Set true when the row has been observed to disappear (deleted). The screen
     *  consumes this once to trigger NavController.popBackStack(). */
    val shouldNavigateBack: Boolean = false,
    /** True while the trash-confirm dialog is open. */
    val showDeleteConfirm: Boolean = false,
)
```

- [ ] **Step 2: DetailViewModel**

```kotlin
package de.docgerdsoft.pantrytracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.docgerdsoft.pantrytracker.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

private val logger: Logger = Logger.getLogger("DetailViewModel")

class DetailViewModel(
    private val repository: ProductRepository,
    private val productId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observeById(productId).collect { product ->
                    _uiState.update { state ->
                        when {
                            product != null -> state.copy(product = product, everSeen = true)
                            state.everSeen -> state.copy(product = null, shouldNavigateBack = true)
                            else -> state // initial-load null, ignore
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "observeById($productId) failed", e)
            }
        }
    }

    /** Called when the user navigates away (Compose consumed the signal). */
    fun onNavigatedBack() {
        _uiState.update { it.copy(shouldNavigateBack = false) }
    }

    @Suppress("TooGenericExceptionCaught")
    fun rename(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.rename(productId, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "rename($productId) failed", e)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun stepperDelta(delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            try {
                repository.applyDelta(productId, delta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "applyDelta($productId, $delta) failed", e)
            }
        }
    }

    fun requestDelete() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    @Suppress("TooGenericExceptionCaught")
    fun confirmDelete() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
        viewModelScope.launch {
            try {
                repository.delete(productId)
                // observeById will emit null → state.everSeen is true → shouldNavigateBack = true.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                @Suppress("SwallowedException")
                logger.log(Level.WARNING, "delete($productId) failed", e)
            }
        }
    }
}
```

**Coroutine discipline notes (the kotlin-coroutines-reviewer agent will check these):**
- Every `try/catch` in `viewModelScope.launch` has `catch (CancellationException) { throw e }` as FIRST arm.
- `java.util.logging.Logger` (not `android.util.Log`) for JVM-test-friendliness — matches `OffApiClient` + `ScanViewModel` pattern.
- No `runCatching` anywhere.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailUiState.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailViewModel.kt
git commit -m "feat(m5): DetailViewModel + UiState — observe row, rename, stepper, delete-with-confirm"
```

---

### Task 4: `DetailScreen` Composable

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailScreen.kt`

- [ ] **Step 1: Implement DetailScreen**

Structure:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val product = state.product

    // Auto-pop on delete (or stale id).
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            viewModel.onNavigatedBack()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::requestDelete,
                        enabled = product != null,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        if (product == null) {
            // Initial loading — minimal placeholder.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Product image (Coil, with placeholder if null).
            AsyncImage(
                model = product.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )

            // Inline-editable name — commits on focus-loss / Done IME.
            var localName by remember(product.name) { mutableStateOf(product.name) }
            OutlinedTextField(
                value = localName,
                onValueChange = { localName = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (localName.isNotBlank() && localName != product.name) {
                        viewModel.rename(localName)
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        // Commit when focus is lost AND value changed.
                        if (!focusState.isFocused && localName.isNotBlank() && localName != product.name) {
                            viewModel.rename(localName)
                        }
                    },
            )

            // Read-only brand + barcode.
            product.brand?.let {
                Text("Brand: $it", style = MaterialTheme.typography.bodyMedium)
            }
            product.barcode?.let {
                Text("Barcode: $it", style = MaterialTheme.typography.bodyMedium)
            }

            // Quantity stepper.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quantity:", style = MaterialTheme.typography.bodyLarge)
                IconButton(
                    onClick = { viewModel.stepperDelta(-1) },
                    enabled = product.quantity > 0,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity")
                }
                Text(
                    product.quantity.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.widthIn(min = 40.dp),
                )
                IconButton(onClick = { viewModel.stepperDelta(+1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
                }
            }

            // Last-updated relative time.
            Text(
                "Last updated ${RelativeTime.format(product.updatedAt, Clock.System.now())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Delete-confirm dialog.
        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete this product?") },
                text = { Text("\"${product.name}\" will be removed from your inventory. This can't be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::confirmDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
                },
            )
        }
    }
}
```

Imports go at the top per the existing screen files. Match the style used in `ScanScreen.kt` for consistency.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/detail/DetailScreen.kt
git commit -m "feat(m5): DetailScreen — image, inline-editable name, stepper, delete dialog"
```

---

### Task 5: Nav graph route + Home row click

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add Routes.DETAIL + composable**

```kotlin
object Routes {
    const val HOME = "home"
    const val SCAN_ADD = "scan/add"
    const val SCAN_REMOVE = "scan/remove"
    const val DETAIL = "detail/{productId}"

    fun detail(id: Long) = "detail/$id"
}
```

Composable:
```kotlin
composable(
    Routes.DETAIL,
    arguments = listOf(navArgument("productId") { type = NavType.LongType }),
) { backStackEntry ->
    val productId = backStackEntry.arguments?.getLong("productId") ?: return@composable
    val factory = remember(container, productId) {
        viewModelFactory {
            initializer { DetailViewModel(container.productRepository, productId) }
        }
    }
    val vm: DetailViewModel = viewModel(factory = factory)
    DetailScreen(
        viewModel = vm,
        onNavigateBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 2: HomeScreen — add onProductClick wiring**

Add `onProductClick: (Long) -> Unit` parameter to `HomeScreen`. Pass through to `ProductRow` (or just call it from the row's click modifier). In the nav graph, wire: `onProductClick = { id -> navController.navigate(Routes.detail(id)) }`.

```kotlin
@Composable
private fun ProductRow(
    product: Product,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Add Modifier.combinedClickable { onClick = onClick, onLongClick = onLongPress }
    // OR layer a Modifier.clickable on top of the existing long-press modifier.
}
```

Use `combinedClickable` from `androidx.compose.foundation` for both tap + long-press in one modifier.

- [ ] **Step 3: Verify wiring**

```bash
grep -nE "Routes.DETAIL|onProductClick|combinedClickable" \
    app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt \
    app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt
git commit -m "feat(m5): wire detail route + Home row tap → navigate"
```

---

### Task 6: `DetailViewModelTest`

**Files:**
- Create: `app/src/test/java/de/docgerdsoft/pantrytracker/ui/detail/DetailViewModelTest.kt`

- [ ] **Step 1: Tests**

Cover (each as a separate `@Test fun ...`):
- `observeById_emitsProduct_thenNull_setsShouldNavigateBack` — emit Product → assert product visible + everSeen=true; emit null → assert shouldNavigateBack=true.
- `observeById_initialNull_doesNotSetNavigateBack` — flow emits null first → shouldNavigateBack stays false (everSeen guard).
- `rename_trimsAndCallsRepository` — `vm.rename("  Coke  ")` → repo.rename called with `(id, "Coke")`.
- `rename_blankName_isNoOp` — `vm.rename("   ")` → repo NOT called.
- `stepperDelta_positive_callsApplyDeltaPositive` — `vm.stepperDelta(+1)` → repo.applyDelta(id, +1).
- `stepperDelta_negative_callsApplyDeltaNegative` — `vm.stepperDelta(-1)` → repo.applyDelta(id, -1).
- `stepperDelta_zero_isNoOp` — `vm.stepperDelta(0)` → repo NOT called.
- `requestDelete_setsShowDeleteConfirm` — `vm.requestDelete()` → state.showDeleteConfirm == true.
- `cancelDelete_clearsShowDeleteConfirm` — request then cancel → false.
- `confirmDelete_callsDeleteAndClearsDialog` — request then confirm → repo.delete called + showDeleteConfirm=false.
- `onNavigatedBack_clearsFlag` — set shouldNavigateBack via emit-null then onNavigatedBack() → false again.

Use a `FakeProductRepository` modeled on the existing one in `ScanViewModelTest`. The repo's `observeById(id)` returns a MutableStateFlow<Product?> the test can push values into.

Use `Dispatchers.setMain(UnconfinedTestDispatcher())` in @Before / `resetMain()` in @After.

- [ ] **Step 2: Commit**

```bash
git add app/src/test/java/de/docgerdsoft/pantrytracker/ui/detail/DetailViewModelTest.kt
git commit -m "test(m5): DetailViewModel — observe-null nav, rename, stepper, delete-with-confirm"
```

---

### Task 7: `DetailScreenTest` Compose smoke test (androidTest)

**Files:**
- Create: `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/detail/DetailScreenTest.kt`

- [ ] **Step 1: Smoke test**

Modeled on the existing `HomeScreenTest`. Use `createComposeRule()`. Set up:
- A `FakeProductRepository` that emits a single fixture Product via `observeById`.
- Wrap DetailScreen in PantryTrackerTheme + Surface.
- Assertions:
  - `onNodeWithText("Coke").assertIsDisplayed()` (the fixture name)
  - `onNodeWithText("3").assertIsDisplayed()` (the quantity)
  - `onNodeWithContentDescription("Increase quantity").performClick()` — then assert quantity bumped to 4 OR (more loosely) that `applyDelta(+1)` was recorded on the fake.
  - `onNodeWithContentDescription("Delete").performClick()` — confirms dialog opens (assert "Delete this product?" displayed).

Keep tight — this is a smoke test, not exhaustive UI coverage (the VM tests carry the heavy lifting).

- [ ] **Step 2: Commit**

```bash
git add app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/detail/DetailScreenTest.kt
git commit -m "test(m5): DetailScreen Compose smoke test — render + click flows"
```

---

### Task 8: Push + PR + multi-agent review

- [ ] **Step 1: Push branch**
- [ ] **Step 2: Open PR closing #6**
- [ ] **Step 3: Dispatch all 7 reviewers in parallel** (5 standard + the 2 M2.85 toolkit subagents). Toolkit dogfood iteration #2.
- [ ] **Step 4: Post inline threads** via `/post-finding` (gh api POST /pulls/N/comments) for findings.
- [ ] **Step 5: Fix findings, push, resolve threads.**
- [ ] **Step 6: CI green + hand off for merge.**
