# Milestone 5 — Item Detail Screen — Design Spec

**Status:** Approved 2026-05-17 (always-editable name with focus-loss commit; observe-and-pop on delete; ±1 stepper)
**Tracking issue:** [#6](https://github.com/DocGerd/pantry-tracker/issues/6)

## Goal

Polish & safety-net for inventory rows: a place to fix bad names, correct miscounts, and delete items without rescanning. Tapping a row on the home screen navigates to a detail screen that shows the product (image + inline-editable name + brand + barcode + current quantity + "Last updated") and provides three correction affordances: rename, manual stepper, and delete (with confirm).

## Non-goals

- **Bulk operations** (multi-select on home, batch delete/rename). Not in scope for v1.
- **Edit barcode / brand**. Per §6.3 those are read-only on detail. Brand is set at OFF lookup or via manual-entry; barcode is the row key. Changing them is rare and out of scope.
- **Photo upload / image replacement**. v1 only displays the OFF-sourced image (or the placeholder if null). Camera-based or gallery-based image picking is not in scope.
- **Undo for delete**. Confirm dialog is the only safety net. A "Recently deleted" recycling bin is M6+ if ever.
- **Long-press stepper for ±N**. v1 is ±1 per tap. If users complain, M6 polish can add long-press repeat.
- **History / audit log** of changes. The single `updatedAt` timestamp is enough for "is this stale".

## Architecture

One new feature folder `ui/detail/` mirroring `ui/home/`. The screen observes the row via a new `repository.observeById(id)` (Room `Flow<Product?>`) so external changes (e.g., another scan that bumps the quantity) reflect live. When the observed flow emits `null` (product deleted from this screen OR elsewhere), the screen navigates back automatically.

```
ui/detail/
  ├─ DetailScreen.kt         (Composable)
  ├─ DetailViewModel.kt
  └─ DetailUiState.kt

ui/common/
  └─ RelativeTime.kt         (pure formatter: Instant + now → "2 hours ago")

ProductRepository / ProductRepositoryImpl
  └─ observeById(id: Long): Flow<Product?>     NEW

ProductDao
  └─ @Query("SELECT * FROM Product WHERE id = :id")
     fun observeById(id: Long): Flow<Product?>  NEW

PantryTrackerNavGraph
  └─ Routes.DETAIL = "detail/{productId}"      NEW
     composable with type-safe Long arg

ui/home/HomeScreen.kt
  ├─ HomeScreen gains `onProductClick: (Long) -> Unit` callback
  └─ ProductRow gains a `Modifier.clickable { onClick() }`
```

## Decisions

### D1. Always-editable name TextField with commit-on-focus-loss

The name field is a Material 3 `OutlinedTextField` that's always editable (no toggle between "view mode" and "edit mode"). Local state mirrors the field; commit fires on focus-loss OR explicit `Done` IME action — NOT per-keystroke.

Why: avoids per-keystroke DB round-trips and noisy `updatedAt` bumps. Click-to-edit toggling is more UI surface for the same outcome.

The repo's existing `rename(id, newName)` already short-circuits when the name equals the current value (no write, no `updatedAt` bump) per M2 — covers the "focused, didn't change anything, lost focus" case.

### D2. observeById + auto-pop on null

`DetailViewModel` collects `repository.observeById(productId)` into a `StateFlow<Product?>` exposed via UI state. The screen renders nothing (or a brief progress indicator) while initial = null-but-loading; once the flow emits a real Product, the full layout renders. If the flow ever emits `null` after a real emission, the screen pops back to home automatically.

This handles three cases uniformly:
- The user taps Delete → confirm → repo.delete fires → observeById emits null → auto-pop.
- An external delete (theoretical — no other path exists in v1, but defends M4-style auto-delete-on-zero if ever added).
- Stale nav arg (id no longer exists when screen opens) — flow emits null immediately → auto-pop.

Distinguishing initial-load null from deleted null: track a boolean `everSeen` in VM state; only auto-pop after `everSeen` flips true.

### D3. Manual stepper = ±1 per tap

Two IconButtons: `Remove` ("−") and `Add` ("+"). Each tap fires `repository.applyDelta(id, ±1)`. The "−" button is disabled when current quantity = 0 (clamping at 0 happens repo-side regardless, but disabling the affordance removes the silent-no-op UX risk from M4).

Quantity-typed input (set quantity to specific value): out of scope. The stepper is for ±1 corrections. For larger jumps users can still tap repeatedly or re-scan.

### D4. Delete with confirm dialog

Trash IconButton (top-right or bottom) opens a Material 3 `AlertDialog`:
- Title: "Delete this product?"
- Body: "\"{name}\" will be removed from your inventory. This can't be undone."
- Confirm: red `TextButton("Delete")` → `repository.delete(id)` → observeById emits null → auto-pop.
- Dismiss: `TextButton("Cancel")` → dialog closes; no state change.

### D5. Relative-time formatter buckets

`RelativeTime.format(then: Instant, now: Instant): String` returns:

| Delta | Output |
|---|---|
| < 60 s | "just now" |
| < 60 min | "$n minutes ago" (singular handled: "1 minute ago") |
| < 24 h | "$n hours ago" |
| < 7 d | "$n days ago" |
| < 4 weeks | "$n weeks ago" |
| ≥ 4 weeks | "$n months ago" (rough — 30-day months) |

Negative delta (future timestamp — shouldn't happen but possible if device clock drifts): return "just now".

Pure function in `ui/common/RelativeTime.kt` — fully unit-testable with no Android dependencies.

Recompose cadence: the screen passes `Clock.System.now()` at composition time. The string can go stale if the user lingers on the screen for a long time, but that's an acceptable v1 trade-off (no `LaunchedEffect` ticker). M6 polish can add a 60-second tick if needed.

### D6. Navigation

`Routes.DETAIL = "detail/{productId}"` with productId as a Long nav-arg (parsed via `it.arguments?.getLong("productId")`). NavController.navigate("detail/$id") from home row click. Back-pop returns to home.

### D7. Test coverage

`DetailViewModelTest` (plain JVM):
- rename emits → repository.rename called with right args; no name change → repo's short-circuit means no call recorded
- stepper plus → applyDelta(id, +1); stepper minus → applyDelta(id, -1)
- delete → repository.delete called; subsequent null emission triggers a navigation-back signal
- observeById emits null initially → renders nothing yet (`everSeen = false`)
- observeById emits Product then null → navigation-back signal fires

`RelativeTimeTest` (plain JVM):
- bucket boundaries: 59s/60s, 59min/60min, 23h/24h, 6d/7d, 27d/28d
- singular vs plural ("1 minute ago" vs "2 minutes ago")
- negative delta returns "just now"

`DetailScreenTest` (androidTest, Compose UI test):
- Renders product name + brand + barcode + quantity for a given fixture Product
- Stepper + button performs click → matches the rendered quantity update
- Trash + Delete confirmation → confirm dispatch fires
- Inline-edit field with new text + focus-loss → rename callback fires
- (Smoke-test bar — full coverage stays in the VM test)

## Acceptance

- [ ] Tapping a row on home navigates to the detail screen.
- [ ] Detail renders image (Coil), name (inline-editable), brand, barcode, current quantity, "Last updated" relative time.
- [ ] Inline name edit → focus loss → `repository.rename(id, newName)` called.
- [ ] Manual −/+ stepper performs `applyDelta(id, ±1)` without going through the camera flow.
- [ ] Trash icon → confirm dialog → `delete(id)` → screen pops back to home automatically.
- [ ] DetailViewModel tests cover rename + stepper + delete + auto-pop on null.
- [ ] RelativeTime tests cover all five buckets.
- [ ] DetailScreenTest smoke-renders the screen for a Product fixture (androidTest; matches the existing HomeScreenTest pattern).
- [ ] CI green (build + Detekt + OSV + Gitleaks).
- [ ] M5 PR review (always-pr-review including the M2.85 toolkit subagents) green, all findings fixed + threads resolved.

## Why now

M5 closes the "I miscounted" / "I named it wrong" / "I'm out of this" loops without needing a rescan. After M5 the app is functionally complete for daily use (M6 is polish). M5 is also lower-risk than M4 was — fewer state-transition paths, smaller coroutine surface — so it's a good place to validate that the M2.85 toolkit's subagents continue to ship cleanly on a less coroutine-heavy PR.
