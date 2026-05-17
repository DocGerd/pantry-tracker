# Milestone 6 — Polish Pass — Design Spec

**Status:** Approved — ready for plan
**Tracking issue:** TBD — create at plan time via `gh issue create --title "Milestone 6: Polish pass" --body "..."` (see issues #11 / #20 for umbrella templates).

## Goal

Make the v1-feature-complete app feel finished. After M0–M5 the app *works* (scan to add, scan to remove, item detail with rename + stepper, local-first inventory with OFF lookup). M6 closes the gap between "works" and "ships" along five touch-points: theme + colours, app icon, home empty-state, camera-permission rationale, error-state audit.

## Non-goals

- No new features. Every M6 task either changes how an existing surface looks/feels or audits an existing behaviour.
- No global crash reporter / remote logging service. That's v1.1 territory.
- No illustration-grade artwork. The icon is a custom mark, but it's a simple vector — not a full scene.
- No dynamic-colour (Material You). We picked a fixed seed so the app's identity is consistent across devices.

## Architecture

Five independent slices, each with a small blast radius.

### 1. Theme — fixed seed colour

**Seed:** `Fern = Color(0xFF4F7942)` (warm pantry green).

**Files:**
- `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt` — add `Fern` + any anchor neutrals.
- `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt` — replace the existing colour-scheme wiring with `lightColorScheme(primary = Fern, …)` + `darkColorScheme(primary = Fern, …)` generated from the seed. Drop any `dynamicLightColorScheme` / `dynamicDarkColorScheme` branches.

**Verification:** manual smoke on light + dark. No automated test — visual correctness.

### 2. App icon — custom adaptive mark

**Concept:** stylised shelf with three jars, white-on-fern. Bold enough to read at 48dp (launcher tile minimum).

**Files:**
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` — `<adaptive-icon>` referencing background + foreground drawables.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — vector, white on transparent, three jars on a horizontal shelf line. Sized to the 108dp adaptive canvas with the 66dp inner safe zone respected (nothing within 21dp of any edge or the launcher mask will clip it).
- `app/src/main/res/values/colors.xml` — `<color name="ic_launcher_background">#4F7942</color>`.
- **Cleanup:** `minSdk = 26` (Android 8) means we don't need legacy PNG `ic_launcher.png` / `ic_launcher.webp` fallbacks. Delete them — one canonical adaptive asset.

**Verification:** manual smoke on a real device launcher. No automated test.

### 3. Home empty state — typographic CTA

**Trigger:** product list is empty AND search query is empty (i.e. truly empty pantry, not "no search matches").

**Layout:** centred `Column`: "Your pantry is empty" (`titleLarge`), helper line "Tap Scan to Add or + to start tracking" (`bodyMedium`, `onSurfaceVariant`), two prominent buttons stacked or side-by-side: `[Scan to Add]` and `[Add manually]`.

**File:** `app/src/main/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreen.kt` — branch the existing list body:

```kotlin
when {
    state.products.isEmpty() && state.query.isBlank() -> EmptyState(
        onScanAdd = onScanAddClick,
        onAddManual = viewModel::openAddSheet,
    )
    state.products.isEmpty() -> NoMatchesHint(query = state.query)  // existing/small
    else -> ProductList(...)
}
```

Extract `EmptyState` as a private `@Composable` in the same file. **`NoMatchesHint` is intentionally a separate (smaller) affordance** — when a search returns zero rows, the user does not want a giant "your pantry is empty" CTA; they want a hint that their query had no matches.

**Test:** Compose test `HomeScreenEmptyStateTest` covering three cases:
- empty products + empty query → both empty-state buttons visible
- empty products + non-empty query → no empty state, "no matches" hint visible
- non-empty products → list visible, no empty state

### 4. Camera-permission gate

**State machine** (composable-local, no ViewModel):

```
Unknown ──first composition──┐
                             ▼
                       checkSelfPermission
                       /              \
                  GRANTED            DENIED
                     │                 │
                     ▼                 ▼
                  Granted        rationale dialog ─cancel─▶ NotAsked
                     │                 │
                     │              continue
                     │                 │
                     │                 ▼
                     │           system prompt
                     │           /          \
                     │       GRANTED      DENIED
                     │          │            │
                     │          │            ▼
                     │          │      shouldShowRationale?
                     │          │       /            \
                     │          │     yes            no
                     │          │      │              │
                     ▼          ▼      ▼              ▼
                  Granted    Granted SoftDenied  HardDenied
                                       │            │
                                       │            ▼
                                       │       "Open settings"
                                       │       affordance
                                       └────────┐
                                                ▼
                                   in-screen affordance
                                   to retry rationale
```

**File:** `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/CameraPermissionGate.kt` (NEW).

```kotlin
@Composable
fun CameraPermissionGate(
    onPermissionGranted: () -> Unit,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit,
)
```

Internals:
- `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` for the system prompt.
- `LocalContext.current as? Activity`'s `shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)` to distinguish SoftDenied from HardDenied.
- Snackbar with "Open settings" action on HardDenied — intent is `ACTION_APPLICATION_DETAILS_SETTINGS` with `package:<appId>` URI.
- All state hoisted to `remember { mutableStateOf(PermissionPhase.Unknown) }`.

**Wire-in:** `PantryTrackerNavGraph.kt` wraps both `scan/add` and `scan/remove` composables with the gate. No change to `ScanViewModel` — permission is a presentation concern.

**Manifest:** `<uses-permission android:name="android.permission.CAMERA" />` (verify it's already declared).

**Test:** Compose test `CameraPermissionGateTest`: render with the gate in each phase via parameter injection (the phase is hoisted to support this) → verify rationale dialog visible / system-prompt-was-invoked tracked via fake / "Open settings" button visible / wrapped content visible respectively.

### 5. Error-state audit

**Sweep scope:** every `catch (Exception)` in `app/src/main/**/*.kt`.

**Per-catch checklist:**
1. Rethrows `CancellationException` first (project rule — runCatching swallows CE, so use try/catch).
2. Logs via `java.util.logging.Logger` (not `android.util.Log`).
3. Sets a user-visible state field — `error: String?` on a UiState, or transitions a `Phase` into an Error variant.
4. User-facing message follows the pattern `"Couldn't <verb>: <reason>"` where `<reason>` is `e.message ?: "unknown error"`. Drop bare strings like `"Network timeout"` — wrap them.

**Known sites to audit** (non-exhaustive — sweep finds the rest):
- `DetailViewModel.kt` — already canonical; use as the template.
- `ScanViewModel.kt` — normalize Phase.Error messages.
- `ProductRepositoryImpl.kt` — repository catches that today log silently get a thrown variant or surface via the caller's UiState.
- `OffApiClient.kt` — already returns a domain result type; verify the calling viewmodel surfaces failures.

**Folded-in cleanup:** `app/src/androidTest/java/de/docgerdsoft/pantrytracker/ui/home/HomeScreenTest.kt:58-79` — the inline `FakeRepository` is missing `observeById` (added in M5) and `lookupForPreview` (added in M3). Add both overrides. This is task #71 from the prior session; lives in M6 because "what does CI not catch?" is the same theme as the error audit.

**Open trade-off (implementation-time decision, not a spec blocker):** extend `.github/workflows/ci.yml` to also run `:app:assembleAndroidTest` so that future drift in androidTest `FakeRepository` overrides surfaces at PR time. Doesn't *run* the Compose tests (those need an emulator), just compiles them — so it's cheap. If we don't add it, the next interface evolution will break androidTest the same way and we won't notice until someone runs it locally. **Default: add it.** Override during implementation if the build-time cost on CI is unacceptable.

**Verification:** no automated test for the sweep itself (it's a one-time audit), but each touched message should already be covered by the existing per-VM error tests in DetailViewModelTest / ScanViewModelTest. Re-running the existing suite is the gate.

## Acceptance

Each touch-point must:

- **Theme.** Light + dark schemes both render with `Fern` as primary. No dynamic-colour code remains.
- **Icon.** Adaptive icon renders correctly on a real launcher (manual). Legacy PNG fallbacks deleted.
- **Empty state.** First launch shows the empty state with both CTAs. Tapping `+` opens the add sheet; tapping `Scan to Add` navigates to scan. Non-empty query with zero matches shows the small "no matches" hint, NOT the big empty state. Compose test covers all three branches.
- **Permission gate.** First scan tap on a fresh install shows the rationale dialog. Continue triggers the system prompt. Grant → ScanScreen renders. Deny → "Open settings" affordance visible. Deny "don't ask again" → "Open settings" deep-links to the app's permission screen. Compose test covers granted / soft-denied / hard-denied phases.
- **Error audit.** Every `catch (Exception)` in `app/src/main` matches the canonical pattern (CE rethrow, JUL log, user-visible state, "Couldn't <verb>: <reason>" tone). Task #71 fix lands. CI optionally extended to `assembleAndroidTest`.

## Why now

v1 is feature-complete after M5. Shipping requires the app to feel finished, not just functional. M6 is the last milestone before tag-v1.0.

## Test strategy

Theme + icon are visual — manual smoke only. Empty state + permission gate are Compose-testable; the audit relies on existing per-VM error tests. No new test infra needed — existing JUnit + Robolectric + Compose-test setup covers everything.

## Out of scope (explicit)

- Animations / transitions polish.
- Onboarding tutorial.
- "Welcome back" first-launch detection.
- App rating prompt.
- Localization (German UI). The user is on a German keyboard but the v1 UI is English; localization can land in v1.1 with `strings.xml` resources.
- Crash reporter (Firebase Crashlytics / Sentry). v1.1.
- Dependabot v1.1 follow-ups (e.g., reviewing the wider 31-package bump's runtime behaviour beyond what compiles).
