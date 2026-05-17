# Milestone 4 — Scan to Remove — Design Spec

**Status:** Approved 2026-05-17 (mode-as-UI-state + auto-re-resolve on Switch-to-Add)
**Tracking issue:** [#5](https://github.com/DocGerd/pantry-tracker/issues/5)

## Goal

Add the symmetric remove flow to the existing scan screen. Tapping the home screen's red **Scan to Remove** button opens the same ScanScreen / ScanViewModel in `mode = Remove`, where decoded barcodes either show the local product with a `−N` stepper (max-bounded by current quantity) or a friendly "Not in your inventory yet" state with a one-tap "Switch to Add" recovery. Re-uses M2/M3 infrastructure end-to-end; the only new behavior is mode-awareness.

## Non-goals

- **Auto-delete on quantity-reaches-zero.** Per original spec §4 ("Remove operations clamp at 0") and §6.2, a removed-to-zero row stays. Deletion lives in M5 (Item Detail screen).
- **OFF lookup in Remove mode.** The §6.2 decision matrix says "Not in your inventory yet" for the miss case — no OFF preview, no manual-add fallback. OFF is skipped entirely on Remove to make the local-miss path fast and predictable.
- **Mode switch via top-bar toggle.** "Switch to Add" only fires from the NotInInventory phase as a recovery affordance, not as a free-form mode switcher. Home-screen entry is the source-of-truth for initial mode.
- **Confirmation dialog when removing to zero.** No special "are you sure" — the row stays at quantity 0 and is trivially reversible via Scan to Add or the M5 Item Detail screen.
- **Negative stepper input.** Stepper continues to `coerceAtLeast(1)` for the pending quantity; the "remove all currently held" UX is "tap the stepper up to current quantity" not "type 0".

## Architecture

Mode is a UI state field on `ScanUiState`, not a route parameter. Two nav routes (`SCAN_ADD`, `SCAN_REMOVE`) seed the initial mode at VM construction; after that, mode is mutable so `onSwitchToAdd()` can flip it in-place without navigating. This matches the issue text's "flips mode without leaving the screen" requirement.

```
ScanMode (enum)              { Add, Remove }                               NEW
ScanUiState
  ├─ mode: ScanMode                                                        NEW field
  └─ phase: Phase
     ├─ Idle                                                               unchanged
     ├─ Loading(barcode)                                                   unchanged
     ├─ Preview(candidate, pendingQuantity)                                stepper max derived from candidate + mode at render
     ├─ ManualEntry(barcode, pendingQuantity)                              Add-only path; unreachable in Remove mode
     ├─ NotInInventory(barcode)                                            NEW — Remove-only path
     └─ Error(message)                                                     unchanged

ScanViewModel
  ├─ constructor(repository, initialMode)                                  mode param added
  ├─ resolveBarcode(barcode)                                               branches on mode
  │     ├─ Add: local hit → Preview(Persisted); local miss → OFF → Preview(FromOff) | ManualEntry
  │     └─ Remove: local hit → Preview(Persisted); local miss → NotInInventory (NO OFF call)
  ├─ confirmAdd()                                                          renamed → confirm(): mode-branched applyDelta sign
  └─ onSwitchToAdd()                                                       NEW: mode=Add + re-resolve current barcode

ScanResultSheet
  ├─ Preview variant: mode-aware Confirm color + label; stepper max = candidate.product.quantity when Remove
  ├─ ManualEntry variant: unchanged (Add-only)
  ├─ NotInInventory variant: NEW — friendly text + "Switch to Add" primary + Cancel secondary
  └─ ConfirmRemove tint: existing RemoveRed from theme

PantryTrackerNavGraph
  ├─ Routes.SCAN_ADD = "scan/add"     factory passes initialMode = Add    unchanged
  └─ Routes.SCAN_REMOVE = "scan/remove" factory passes initialMode = Remove   NEW
```

## Decisions

### D1. Mode source-of-truth

Mode is owned by `ScanUiState` (mutable UI state) but initially set from the route at VM construction. After "Switch to Add", the mode field is the authoritative source — the route the user came from is irrelevant. Back-button still pops to home (route stack unchanged).

### D2. Switch-to-Add behavior

Calling `onSwitchToAdd()` from the NotInInventory phase:
1. Captures the current `barcode` from `Phase.NotInInventory`.
2. Mutates `mode = Add` in the UI state.
3. Calls `onBarcodeDecoded(barcode)` again, which kicks the Add resolution path (local hit → Preview persisted; local miss → OFF → Preview FromOff or ManualEntry).

The transition is instantaneous from the user's perspective — the same bottom sheet stays open and morphs into the new phase. No navigation transition.

### D3. Mode-aware stepper bound

In Remove + Preview(Persisted), the stepper's max is `candidate.product.quantity`. `setQuantity(value)` becomes `value.coerceIn(1, maxForMode)` where `maxForMode` is `Int.MAX_VALUE` in Add and `candidate.product.quantity` in Remove (Persisted). FromOff in Remove is unreachable per D4.

### D4. Phase reachability matrix per mode

| Phase | Add reachable | Remove reachable |
|---|---|---|
| Idle | yes | yes |
| Loading | yes | yes |
| Preview(Persisted) | yes (local hit) | yes (local hit) |
| Preview(FromOff) | yes (local miss + OFF hit) | NO (no OFF in Remove) |
| ManualEntry | yes (local miss + OFF miss/fail) | NO |
| NotInInventory | NO | yes (local miss) |
| Error | yes | yes |

This is enforced by `resolveBarcode` branching on `mode` before dispatching to `repository.lookupForPreview` (Add) vs `repository.findLocalByBarcode` (Remove).

### D5. Confirm action signature

`confirmAdd()` is renamed to `confirm()` (it's no longer Add-specific). It reads `state.mode` and `state.phase` and dispatches:
- `Add + Preview(Persisted)` → `applyDelta(id, +pendingQuantity)`
- `Add + Preview(FromOff)` → `addNew(name=..., brand=..., barcode=..., imageUrl=..., initialQuantity=pendingQuantity)`
- `Remove + Preview(Persisted)` → `applyDelta(id, -pendingQuantity)`
- `Add + ManualEntry` → `addNew(...)` (existing path; already in submitManualEntry)
- Any other (state, mode) → no-op (defensive)

### D6. Mode-colored title & confirm button

`ScanScreen` reads `uiState.mode` and:
- Renders title text "Scan to Add" / "Scan to Remove".
- Tints the top-bar background or accent strip with `AddGreen` / `RemoveRed` (already in `ui/theme/Color.kt`).

The bottom-sheet Confirm button uses the same color via `ButtonDefaults.buttonColors(containerColor = ...)`.

### D7. Test strategy

Two test files touch the change surface:

`ScanViewModelTest`:
- `remove_localHit_showsPreviewWithMaxStepper` — assert phase + stepper coerces above-quantity to quantity.
- `remove_localMiss_returnsNotInInventory_doesNotCallOff` — assert NotInInventory phase AND `FakeOffLookup.callCount == 0`.
- `remove_notInInventory_switchToAdd_flipsModeAndReresolves` — assert mode flips + the new resolution path produces Preview/ManualEntry.
- `remove_confirm_appliesNegativeDelta` — assert repository receives `applyDelta(id, -N)`.

`ProductRepositoryImplTest` — verify the clamp-at-0 + no-op-when-unchanged invariants exist (they do, from M2). Add one test if there's a gap.

## Acceptance

- [ ] Tapping the existing **Scan to Remove** button on home opens ScanScreen with the red title bar and "Scan to Remove" text.
- [ ] Decoding a barcode present in the local DB → bottom sheet shows product card + stepper with `max = current quantity` + ConfirmRemove (red).
- [ ] Confirm calls `applyDelta(id, -N)`; repo clamps at 0; row remains at quantity 0 (does not delete).
- [ ] Decoding a barcode NOT in the local DB → bottom sheet shows "Not in your inventory yet" + "Switch to Add" button + Cancel.
- [ ] Tapping "Switch to Add" → mode flips → barcode re-resolves through the Add path (Preview or ManualEntry) — no navigation, no re-scan.
- [ ] Cancel from any Remove state returns to camera-ready.
- [ ] ViewModel tests cover the four Remove-mode cases above.
- [ ] CI green (build + Detekt + OSV + Gitleaks).
- [ ] M4 PR review (always-pr-review) catches no Critical findings; if it does, all are addressed before merge.

## Why now

M4 is the **first integration test of the M2.85 toolkit**: the kotlin-coroutines-reviewer should run cleanly on the new Remove-flow code (which adds another coroutine path), and the android-test-environment-reviewer should validate the new ScanViewModel tests. If either subagent surfaces real findings we couldn't have anticipated, that validates the toolkit's value before M5/M6 raise the surface further.
