# Milestone 4 — Scan to Remove — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Remove mode end-to-end on the existing ScanScreen / ScanViewModel. Mode is a UI state field initialized from the route; Remove + local miss yields a "Not in your inventory yet" sheet with one-tap Switch-to-Add recovery that re-resolves the captured barcode through the Add path without re-scanning.

**Architecture:** Mode-as-mutable-UI-state on `ScanUiState`; two routes seed initial mode at VM construction; `confirmAdd()` becomes `confirm()` and dispatches by `(mode, phase)`; new `Phase.NotInInventory(barcode)` for Remove-mode local miss; OFF lookup is skipped entirely in Remove mode.

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 (existing). No new dependencies.

**Tracking issue:** [#5](https://github.com/DocGerd/pantry-tracker/issues/5)
**Spec:** `docs/superpowers/specs/2026-05-17-milestone-4-scan-to-remove-design.md`

---

### Task 1: Add `ScanMode` enum + extend `ScanUiState`

**Files:**
- Create: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanMode.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanUiState.kt`

- [ ] **Step 1: Create `ScanMode.kt`**

```kotlin
package de.docgerdsoft.pantrytracker.ui.scan

enum class ScanMode { Add, Remove }
```

- [ ] **Step 2: Add `mode` field + `NotInInventory` phase to `ScanUiState.kt`**

In the data class declaration, add `val mode: ScanMode = ScanMode.Add` as the first field (default Add for backward-compat with the existing tests; M4 nav graph passes explicitly). In the sealed `Phase` hierarchy, add:

```kotlin
data class NotInInventory(val barcode: String) : Phase
```

Place it alphabetically/logically next to `ManualEntry` so the file stays scannable.

- [ ] **Step 3: Verify the file compiles (no test yet)**

```bash
ls app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanMode.kt
grep "NotInInventory\|mode:" app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanUiState.kt
```
Expected: file exists; grep shows both additions.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanMode.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanUiState.kt
git commit -m "feat(m4): ScanMode enum + NotInInventory phase + mode in UiState"
```

---

### Task 2: Mode-aware `ScanViewModel` — branching `resolveBarcode` + `confirm` + `onSwitchToAdd`

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt`

- [ ] **Step 1: Add `initialMode` constructor parameter**

Change the primary constructor:
```kotlin
class ScanViewModel(
    private val repository: ProductRepository,
    initialMode: ScanMode = ScanMode.Add,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState(mode = initialMode))
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()
    ...
```

Default `Add` preserves existing tests that don't pass mode.

- [ ] **Step 2: Branch `resolveBarcode` on mode**

Replace the body of `resolveBarcode(barcode)` so the resolution path depends on `_uiState.value.mode`:

```kotlin
private suspend fun resolveBarcode(barcode: String) {
    val mode = _uiState.value.mode
    val newPhase: ScanUiState.Phase = try {
        when (mode) {
            ScanMode.Add -> {
                val resolved = repository.lookupForPreview(barcode)
                if (resolved != null) ScanUiState.Phase.Preview(resolved, pendingQuantity = 1)
                else ScanUiState.Phase.ManualEntry(barcode, pendingQuantity = 1)
            }
            ScanMode.Remove -> {
                val local = repository.findLocalByBarcode(barcode)
                if (local != null) {
                    ScanUiState.Phase.Preview(
                        ScanCandidate.Persisted(local),
                        pendingQuantity = 1,
                    )
                } else {
                    ScanUiState.Phase.NotInInventory(barcode)
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ScanUiState.Phase.Error("Couldn't read inventory: ${e.message ?: "unknown error"}")
    }
    _uiState.update { state ->
        val owns = (state.phase as? ScanUiState.Phase.Loading)?.barcode == barcode
        if (owns) state.copy(phase = newPhase) else state
    }
}
```

**Important** — coroutine try/catch shape: explicit `catch (e: CancellationException) { throw e }` BEFORE `catch (e: Exception)`. Per the kotlin-coroutines-reviewer agent rules (and the M3 fix that motivated them), `runCatching` is forbidden in suspend context.

- [ ] **Step 3: Rename `confirmAdd()` → `confirm()`; dispatch by (mode, phase)**

```kotlin
@Suppress("TooGenericExceptionCaught")
fun confirm() {
    val state = _uiState.value
    val phase = state.phase as? ScanUiState.Phase.Preview ?: return
    confirmJob?.cancel()
    confirmJob = viewModelScope.launch {
        val newPhase: ScanUiState.Phase = try {
            when (state.mode) {
                ScanMode.Add -> when (val c = phase.candidate) {
                    is ScanCandidate.Persisted -> repository.applyDelta(c.product.id, phase.pendingQuantity)
                    is ScanCandidate.FromOff -> repository.addNew(
                        name = c.name, brand = c.brand, barcode = c.barcode,
                        imageUrl = c.imageUrl, initialQuantity = phase.pendingQuantity,
                    )
                }
                ScanMode.Remove -> {
                    val persisted = phase.candidate as? ScanCandidate.Persisted ?: return@launch
                    repository.applyDelta(persisted.product.id, -phase.pendingQuantity)
                }
            }
            ScanUiState.Phase.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ScanUiState.Phase.Error("Couldn't save: ${e.message ?: "unknown error"}")
        }
        _uiState.update { s ->
            if (s.phase === phase) s.copy(phase = newPhase) else s
        }
    }
}
```

- [ ] **Step 4: Add `onSwitchToAdd()`**

```kotlin
/** From the NotInInventory phase: flip mode to Add and re-resolve the captured barcode. */
fun onSwitchToAdd() {
    val phase = _uiState.value.phase as? ScanUiState.Phase.NotInInventory ?: return
    _uiState.update { it.copy(mode = ScanMode.Add) }
    // Re-enter the Loading→resolve flow with the captured barcode.
    onBarcodeDecoded(phase.barcode)
}
```

- [ ] **Step 5: Update `isAlreadyShowing` to recognize NotInInventory + setQuantity max in Remove**

Add a `NotInInventory` branch returning `current.barcode == barcode` for the deduplication check:

```kotlin
private fun isAlreadyShowing(barcode: String): Boolean =
    when (val current = _uiState.value.phase) {
        is ScanUiState.Phase.Loading -> current.barcode == barcode
        is ScanUiState.Phase.Preview -> current.candidate.barcode == barcode
        is ScanUiState.Phase.ManualEntry -> current.barcode == barcode
        is ScanUiState.Phase.NotInInventory -> current.barcode == barcode
        ScanUiState.Phase.Idle, is ScanUiState.Phase.Error -> false
    }
```

And in `setQuantity`, clamp to max in Remove + Persisted:

```kotlin
fun setQuantity(value: Int) {
    _uiState.update { state ->
        when (val phase = state.phase) {
            is ScanUiState.Phase.Preview -> {
                val max = if (state.mode == ScanMode.Remove) {
                    (phase.candidate as? ScanCandidate.Persisted)?.product?.quantity ?: Int.MAX_VALUE
                } else Int.MAX_VALUE
                val clamped = value.coerceIn(1, max.coerceAtLeast(1))
                state.copy(phase = phase.copy(pendingQuantity = clamped))
            }
            is ScanUiState.Phase.ManualEntry ->
                state.copy(phase = phase.copy(pendingQuantity = value.coerceAtLeast(1)))
            else -> state
        }
    }
}
```

- [ ] **Step 6: Replace any remaining calls to `confirmAdd()` in the codebase with `confirm()`**

```bash
grep -rn "confirmAdd" app/src/main/java app/src/test/java
```
Replace each via Edit. (UI calls in ScanResultSheet.kt will be addressed in Task 4; tests in Task 5.)

- [ ] **Step 7: Verify file compiles (manual visual review — no JDK locally)**

```bash
grep -nE "fun (confirm|resolveBarcode|onSwitchToAdd|setQuantity|isAlreadyShowing)\b" app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt
```
Expected: all five present with the new signatures.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt
git commit -m "feat(m4): mode-aware ScanViewModel — Remove path + onSwitchToAdd"
```

---

### Task 3: Routes + nav-graph wiring for Scan to Remove

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt`

- [ ] **Step 1: Add `Routes.SCAN_REMOVE` constant + composable**

In `object Routes`:
```kotlin
const val SCAN_REMOVE = "scan/remove"
```

Add a sibling `composable(Routes.SCAN_REMOVE)` block to the NavHost that mirrors `SCAN_ADD` but passes `ScanMode.Remove` to the factory:

```kotlin
composable(Routes.SCAN_REMOVE) {
    val factory = remember(container) {
        viewModelFactory {
            initializer { ScanViewModel(container.productRepository, initialMode = ScanMode.Remove) }
        }
    }
    val vm: ScanViewModel = viewModel(factory = factory)
    ScanScreen(
        viewModel = vm,
        onNavigateBack = { navController.popBackStack() },
    )
}
```

Update the existing `SCAN_ADD` factory to pass `initialMode = ScanMode.Add` explicitly (no default — explicit at the seam keeps grep-discoverability).

- [ ] **Step 2: Wire the home-screen `onScanRemoveClick` TODO**

Replace `onScanRemoveClick = { /* TODO: wire to scan-remove flow */ }` with `onScanRemoveClick = { navController.navigate(Routes.SCAN_REMOVE) }`.

- [ ] **Step 3: Add `ScanMode` import**

```kotlin
import de.docgerdsoft.pantrytracker.ui.scan.ScanMode
```

- [ ] **Step 4: Verify**

```bash
grep -nE "SCAN_REMOVE|initialMode" app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt
```
Expected: Routes.SCAN_REMOVE declared + used; both VM factories pass `initialMode = ScanMode.{Add,Remove}` explicitly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerNavGraph.kt
git commit -m "feat(m4): wire scan/remove route — onScanRemoveClick navigates with ScanMode.Remove"
```

---

### Task 4: Mode-aware UI — ScanScreen title/color + ScanResultSheet variants

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanScreen.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/ScanResultSheet.kt`

- [ ] **Step 1: ScanScreen — mode-driven title text**

Read `uiState.mode` and render:
- Title: `if (mode == ScanMode.Add) "Scan to Add" else "Scan to Remove"`.
- Top-bar accent color tracks `mode` using `AddGreen` / `RemoveRed` from `ui.theme.Color`. Apply via `TopAppBarDefaults.topAppBarColors(containerColor = ...)` so the visual cue is unmistakable.

- [ ] **Step 2: ScanResultSheet — Preview variant: mode-aware Confirm button**

Read mode from state passed into the composable. The Confirm button:
- Label: `if (mode == Add) "Confirm Add" else "Confirm Remove"`.
- Color: `AddGreen` / `RemoveRed`.
- The stepper max is already clamped by `ScanViewModel.setQuantity` (Task 2 Step 5) — the UI just renders whatever `pendingQuantity` is in state. Optional: pass max into stepper for `+` button disable past max — but state-clamp is sufficient.

- [ ] **Step 3: ScanResultSheet — NEW NotInInventory variant**

When `phase is Phase.NotInInventory`:
```kotlin
Column(...) {
    Text(
        "Not in your inventory yet",
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        "Barcode ${phase.barcode} isn't tracked yet. Add it instead?",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onSwitchToAdd,
        colors = ButtonDefaults.buttonColors(containerColor = AddGreen),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Switch to Add")
    }
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}
```

The composable signature gains an `onSwitchToAdd: () -> Unit` parameter; ScanScreen passes `viewModel::onSwitchToAdd`.

- [ ] **Step 4: Verify**

```bash
grep -nE "Scan to Remove|RemoveRed|NotInInventory|onSwitchToAdd|Switch to Add" \
    app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanScreen.kt \
    app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/ScanResultSheet.kt
```
Expected: all five substrings present.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanScreen.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/components/ScanResultSheet.kt
git commit -m "feat(m4): mode-aware ScanScreen UI — Remove title/color + NotInInventory sheet"
```

---

### Task 5: Tests for the new Remove-mode behaviors

**Files:**
- Modify: `app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt`

- [ ] **Step 1: Update any existing tests that referenced `confirmAdd()` → `confirm()`**

```bash
grep -n "confirmAdd" app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt
```
Replace each call.

- [ ] **Step 2: Add four new tests covering Remove-mode behavior**

```kotlin
@Test
fun remove_localHit_showsPreviewWithStepperMaxAtQuantity() = runTest {
    val product = Product(id = 1, barcode = "111", name = "Coke", quantity = 5,
        createdAt = nowInstant, updatedAt = nowInstant)
    val repo = FakeRepository(
        localByBarcode = mapOf("111" to product),
    )
    val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

    vm.onBarcodeDecoded("111")
    advanceUntilIdle()

    val phase = vm.uiState.value.phase as ScanUiState.Phase.Preview
    assertEquals(1, phase.pendingQuantity)
    // setQuantity above current quantity clamps to current quantity.
    vm.setQuantity(99)
    val coerced = (vm.uiState.value.phase as ScanUiState.Phase.Preview).pendingQuantity
    assertEquals(5, coerced)
}

@Test
fun remove_localMiss_yieldsNotInInventory_andDoesNotCallOff() = runTest {
    val fakeOff = FakeOffLookup()
    val repo = FakeRepository(offLookup = fakeOff)
    val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

    vm.onBarcodeDecoded("222")
    advanceUntilIdle()

    val phase = vm.uiState.value.phase
    assertTrue("expected NotInInventory, was $phase", phase is ScanUiState.Phase.NotInInventory)
    assertEquals("222", (phase as ScanUiState.Phase.NotInInventory).barcode)
    assertEquals(0, fakeOff.callCount)  // critical: Remove mode skips OFF entirely
}

@Test
fun remove_notInInventory_switchToAdd_flipsModeAndReresolves() = runTest {
    // Local miss + OFF hit → after switch, Preview(FromOff)
    val fakeOff = FakeOffLookup()
    fakeOff.stub("333", OffProduct(productName = "Pepsi", brands = "PepsiCo", imageUrl = "https://x"))
    val repo = FakeRepository(offLookup = fakeOff)
    val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

    vm.onBarcodeDecoded("333")
    advanceUntilIdle()
    assertTrue(vm.uiState.value.phase is ScanUiState.Phase.NotInInventory)

    vm.onSwitchToAdd()
    advanceUntilIdle()

    assertEquals(ScanMode.Add, vm.uiState.value.mode)
    val preview = vm.uiState.value.phase as ScanUiState.Phase.Preview
    assertEquals("Pepsi", (preview.candidate as ScanCandidate.FromOff).name)
}

@Test
fun remove_confirm_appliesNegativeDelta() = runTest {
    val product = Product(id = 7, barcode = "444", name = "Milk", quantity = 3,
        createdAt = nowInstant, updatedAt = nowInstant)
    val repo = FakeRepository(localByBarcode = mapOf("444" to product))
    val vm = ScanViewModel(repo, initialMode = ScanMode.Remove)

    vm.onBarcodeDecoded("444")
    advanceUntilIdle()
    vm.setQuantity(2)
    vm.confirm()
    advanceUntilIdle()

    assertEquals(listOf(7L to -2), repo.deltaCalls)
    assertEquals(ScanUiState.Phase.Idle, vm.uiState.value.phase)
}
```

`FakeRepository` is the existing test double in this file; if it doesn't already record `applyDelta` calls in a `deltaCalls` list, extend it minimally to do so (one `MutableList<Pair<Long, Int>>` field + a record in `applyDelta`).

- [ ] **Step 3: Smoke-validate test structure**

```bash
grep -nE "@Test\s*$|fun remove_" app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt
```
Expected: four new `fun remove_*` tests present.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModelTest.kt
git commit -m "test(m4): Remove-mode ScanViewModel — stepper max, no-OFF, switch-to-Add, negative delta"
```

---

### Task 6: Push, open PR, multi-agent review (including the new toolkit subagents), fix, hand off

- [ ] **Step 1: Push branch**

```bash
git push -u origin m4-scan-to-remove
```

- [ ] **Step 2: Open PR closing #5**

```bash
gh pr create --title "Milestone 4: Scan to Remove" --body "..."
```
Body summarizes the 5 commits + closes #5 footer.

- [ ] **Step 3: Multi-agent PR review per always-pr-review**

Dispatch in parallel:
- `code-reviewer`
- `comment-analyzer`
- `silent-failure-hunter`
- `pr-test-analyzer` (M4 adds 4 new tests — relevant this time)
- `type-design-analyzer` (M4 adds `ScanMode` enum + `Phase.NotInInventory` data class — relevant)
- **`kotlin-coroutines-reviewer`** (NEW — first integration test of the M2.85 toolkit; ScanViewModel adds suspend branching + new viewModelScope.launch in confirm)
- **`android-test-environment-reviewer`** (NEW — ScanViewModelTest is a plain JVM test; verify no `android.*` leak + Dispatchers.setMain in place)

- [ ] **Step 4: Post inline threads via `/post-finding` skill** (or its documented `gh api POST /pulls/N/comments` form) for any findings.

- [ ] **Step 5: Fix findings, push, resolve threads via GraphQL `resolveReviewThread`.**

- [ ] **Step 6: Verify CI green + hand off for merge.**
