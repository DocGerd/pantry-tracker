# 4. Solution Strategy

The top-level decisions that distinguish this app from the cookie-cutter
"Android sample app" baseline.

## 4.1 Local-first inventory, network-optional enrichment

The user's pantry lives in Room. The OFF API is consulted **only on a barcode
the user has never scanned before**, and only to enrich the row with a name +
brand + image URL. If OFF is unreachable or returns a miss, the scan flow
falls through to manual entry — the user types the name themselves, the row
still gets created.

Consequence: the app is fully usable on a plane. No "offline mode" code path
to maintain because online vs. offline isn't a distinct mode — OFF is just an
optional input to one step.

## 4.2 Manual DI via a single `AppContainer`

No Hilt, no Koin, no Dagger. The `Application.onCreate` constructs an
`AppContainer(this)` which owns the Room database, the `OffApiClient`, and
the `ProductRepository`. Composables grab it via the `Application` cast in
`MainActivity.onCreate` and pass it down the nav graph.

Consequence: zero codegen for DI. The wiring is one file, ~25 lines, easy to
trace. Trade-off: adding a new dependency means an explicit edit to
`AppContainer` — there's no auto-wiring. Acceptable for a single-module app.

## 4.3 Compose-only UI with typed `UiState` per screen

Every screen has a `<Screen>ViewModel` that exposes a `StateFlow<<Screen>UiState>`.
The composable observes via `collectAsStateWithLifecycle()`. Side effects
(repository writes, navigation requests) are dispatched as method calls on
the ViewModel — never directly from the composable.

Consequence: each screen can be unit-tested against the ViewModel (Turbine
on the flow) and Compose-tested against a fake `ProductRepository` (no
emulator-touching `android.*` from unit tests). No MVI framework, no Redux.
Trade-off: there is duplication between `<X>UiState` shapes when two screens
need overlapping fields — acceptable because the screens are few.

## 4.4 Permission gate as a composable-local state machine

Camera permission is modelled as a `sealed interface CameraPermissionPhase`
(Unknown / Granted / SoftDenied / HardDenied) computed in a stateful gate
composable, and rendered by a *pure* `CameraPermissionGateContent` that
takes the phase as a parameter. The split lets Compose tests drive each
phase directly without an emulator-based permission grant/deny dance.

The gate is wrapped around both scan routes at the nav-graph level —
`ScanScreen` itself never sees the permission state.

## 4.5 Error surfaces as inline UI state, never silently swallowed

Repository operations that fail surface as a typed `error: String?` (or
`Phase.Error(...)`) on the screen's UiState — the screen renders this as a
Snackbar or error sheet. The canonical message tone is
`"Couldn't <verb>: <reason>"`. Errors are also JUL-logged with a stack
trace at the catch site so they survive into logcat.

Consequence: silent failures are a code-review violation, not a default.
Audited milestone-wide in M6.

## 4.6 Multi-agent PR review as part of the dev loop

Every PR opened in this workflow gets dispatched to 5–7 specialized review
agents in parallel (general code review, error-handling auditor, type-design
analyzer, test-coverage analyzer, comment auditor, Kotlin coroutines
specialist, Android test environment specialist). Findings post as inline
review threads; fixes commit; threads resolve via GraphQL.

Consequence: review catches what a single human reviewer skims past — the
M6 PR review caught a real lost-recovery-path regression in the new
permission gate that would have shipped silently otherwise.
