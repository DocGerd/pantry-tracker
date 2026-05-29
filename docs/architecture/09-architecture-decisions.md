# 9. Architecture Decisions

Short-form ADRs. Each captures the choice, the alternatives considered, and
what made the pick win.

> Going forward, new ADRs live under [`docs/adr/`](../adr/) in canonical
> Michael Nygard format. The short-form ADRs below pre-date that directory
> and remain the source of truth for ADR-001 … ADR-010; ADRs 0001–0006 in
> `docs/adr/` backfill the most load-bearing decisions in the longer
> Nygard form.

## ADR-001 — Manual DI via `AppContainer`, not Hilt

**Status:** accepted (M0)
**Context:** Single-module Android app, single developer.
**Decision:** Hand-rolled `AppContainer` constructed in `Application.onCreate`,
ViewModels built with `viewModelFactory { initializer { ... } }`.
**Alternatives:** Hilt (chosen by ~90% of modern Android apps), Koin, manual + service-locator.
**Why:** Hilt buys auto-wiring and graph correctness at the cost of an
annotation-processing layer, a custom build plugin, a learning curve, and a
generated graph that's hard to debug. For one module and ~5 wired components,
the explicit version is shorter, easier to trace, and never breaks at runtime
in a way the type system didn't catch at compile time.
**Reconsider when:** A second consumer of `ProductRepository` (a Wear OS
companion app, a backup-export module) lands. Then Hilt's auto-wiring pays
for itself.

## ADR-002 — Room as the only persistence layer

**Status:** accepted (M0)
**Context:** Need a structured store for the pantry; need queries over
barcode + name + arbitrary substrings.
**Decision:** Room (Jetpack), KSP-generated DAO, single `products` table.
**Alternatives:** SQLDelight, raw SQLite, DataStore + JSON, Realm.
**Why:** Room is the Android-conventional choice; KSP (not KAPT) keeps
build times reasonable; the DAO surface is small enough that a query DSL
would be overkill. SharedPreferences / DataStore was rejected because the
search query needs SQL `LIKE`.

## ADR-003 — Compose-only, no XML layouts

**Status:** accepted (M0)
**Context:** Clean-slate v1.
**Decision:** Every screen is a Composable. The only XML in `res/` is the
bootstrap theme + adaptive icon manifests.
**Alternatives:** View-system XML + Fragments, hybrid.
**Why:** Compose is Google's path forward and the dev loop (preview,
recomposition) is significantly faster. Some onboarding cost — but the
project owner is already learning Compose, and writing two UI stacks for
one app is wasteful.

## ADR-004 — Local-first inventory, network-optional enrichment

**Status:** accepted (M0/M2)
**Context:** Inventory is the user's source of truth. OFF is a 3rd-party
catalogue we don't control.
**Decision:** Room holds the inventory. OFF is consulted only when scanning
a barcode we haven't seen before, only to enrich the row with a name. Any
OFF failure (404, 5xx, timeout, network down, JSON parse fail, blank
product_name) falls through to manual entry; the user types the name. See
[solution strategy](04-solution-strategy.md#41-local-first-inventory-network-optional-enrichment).
**Alternatives:** OFF-first (worse UX offline), local cache of OFF responses
(adds a second store to keep coherent).
**Why:** Privacy + offline + "OFF as input not oracle" all pull in the same
direction.

## ADR-005 — Typed `UiState` per screen, no MVI framework

**Status:** accepted (M0)
**Context:** Need to model screen state cleanly without buying into a
heavyweight architecture pattern.
**Decision:** Each ViewModel exposes `StateFlow<<Screen>UiState>`. UiState
is a data class with explicit nullable fields for sheets / dialogs /
errors. Composable observes via `collectAsStateWithLifecycle`. ViewModel
methods mutate via `_uiState.update { it.copy(...) }`.
**Alternatives:** MVI (Orbit, MVIKotlin), Redux-style reducers, Compose
`MutableState` directly in the ViewModel.
**Why:** MVI's intent/result/state plumbing is overkill for ~5 screens.
`StateFlow + UiState` is the minimum that gives test-friendly state +
collectability + a single source of truth per screen.

## ADR-006 — `java.util.logging`, not `android.util.Log`

**Status:** accepted (M2)
**Context:** Unit tests run on plain JVM; `android.util.Log` throws
"Method X not mocked" without Robolectric.
**Decision:** Project-wide use of `java.util.logging.Logger`. JUL is
forwarded to logcat on Android.
**Alternatives:** android.util.Log (breaks unit tests), Timber (added
dependency for negligible benefit at this scale), SLF4J + a logback
backend (overkill).
**Why:** JUL is in the JDK, requires no new dependency, and works in
every test mode. The only loss is Timber's per-class tag inference — we
get that explicitly by naming the `Logger.getLogger("...")` parameter.

## ADR-007 — Permission gate as a Compose state machine, not a ViewModel

**Status:** accepted (M6)
**Context:** Camera permission is needed for the scan screens. The
behaviour differs by Android version + the user's denial history.
**Decision:** A `sealed interface CameraPermissionPhase` (Unknown /
Granted / SoftDenied / HardDenied) computed inside a stateful gate
composable; the actual UI is a pure `CameraPermissionGateContent(phase, ...)`
that the Compose test drives directly.
**Alternatives:** ViewModel-owned permission state (would need
`SavedStateHandle` + lifecycle-aware re-checks), Accompanist Permissions
library (adds a dependency for a small surface).
**Why:** The permission flow is presentation-layer concern, has no data
beyond the phase, and the split-into-stateful-plus-pure makes it
emulator-free testable. ViewModel-ownership would buy nothing and would
spread the concern across two files.

## ADR-008 — No crash reporter / analytics in v1.0

**Status:** accepted (M0, reaffirmed M6)
**Context:** Quality goal #1 is privacy. The app is single-user, sideload.
**Decision:** Nothing leaves the device except OFF barcode lookups. No
Firebase, no Sentry, no GA, no Adjust.
**Alternatives:** Firebase Crashlytics (free), Sentry self-hosted.
**Why:** No external stakeholders, no SLA, no fleet to monitor. The cost
(privacy + dependency + setup) outweighs the benefit (a stack trace for
crashes that don't happen). Re-evaluate at v1.1 if real users join.

## ADR-009 — No background work in v1

**Status:** accepted (M0)
**Context:** Could prefetch OFF, schedule cache cleanup, etc.
**Decision:** Every I/O operation is foreground, driven by a user gesture.
No `WorkManager`, no `JobScheduler`, no `Service`.
**Alternatives:** Prefetch OFF on first launch (worse first-launch
experience, premature optimization), background sync (no remote to sync to).
**Why:** Battery + simplicity + nothing-to-debug-at-3am.

## ADR-010 — CI compiles androidTest but doesn't run it

**Status:** accepted (M6)
**Context:** Compose UI tests live in `app/src/androidTest/` and need an
emulator (or device) to run.
**Decision:** CI runs `:app:assembleDebugAndroidTest` (compile only).
Emulator-based execution is left to local development + the manual UAT
checklist.
**Alternatives:** GitHub Actions hosted emulator (slow, flaky), Firebase
Test Lab (paid, external dependency), skip androidTest entirely (loses
the FakeRepository drift catch).
**Why:** Compile-only catches the most common drift (a new
`ProductRepository` method that a Fake forgot to override) cheaply.
Emulator runs are an environment investment for a later milestone.
