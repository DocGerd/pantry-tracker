# 8. Crosscutting Concepts

## 8.1 Theming

Material 3 with a single `Fern` (`#4F7942`) seed colour passed to
`lightColorScheme(primary = Fern)` and `darkColorScheme(primary = Fern)`.
**Only the `primary` slot is overridden** — the rest of the scheme uses
M3's hardcoded Baseline palette. True seed-derived tonal expansion (dynamic
colour) is not wired up.

Two extra colours sit outside the M3-derived scheme so the verbs stay
distinguishable across light/dark:

| Color | Hex | Used by |
|-------|-----|---------|
| `AddGreen` | `#2A6A2A` | `ScanButtonsRow` "Scan to Add" button container |
| `RemoveRed` | `#8A2A2A` | `ScanButtonsRow` "Scan to Remove" button container |

Typography: `PantryTypography` in `ui/theme/Type.kt`.

Adaptive launcher icon: `mipmap-anydpi-v26/ic_launcher{,_round}.xml` with
foreground `drawable/ic_launcher_foreground.xml` (three white jars on a
horizontal shelf line) over `@color/ic_launcher_background` (`#4F7942`,
matching Fern).

## 8.2 State exposure

Every screen exposes a typed UiState as `StateFlow<X>`:

```kotlin
val uiState: StateFlow<HomeUiState> = combine(...).stateIn(
    viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = HomeUiState())
```

Consumed in the composable:

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

No screen reaches directly into the repository — observation flows through
the ViewModel. The `WhileSubscribed(5_000)` keeps the underlying flow alive
for 5 s after the last collector unsubscribes, so config changes (rotation)
don't restart it.

## 8.3 Persistence

| Concern | Choice |
|---------|--------|
| Schema mismatch behaviour | **Crashes**. No `fallbackToDestructiveMigration`. Per spec §7, the user's pantry is sacred — a missed migration must be loud, not silent. |
| Time stamps | `kotlin.time.Instant` (from kotlinx-datetime). All `Product` rows carry `createdAt` + `updatedAt`. The repository owns the `Clock` (defaults to `Clock.System`; tests inject a fixed clock). |
| Unique constraints | `Index(value = ["barcode"], unique = true)` — a duplicate-barcode upsert overwrites the existing row by design. |
| Schema export | Disabled until `app/schemas/` is committed. Re-enable at the first `@Database(version = 2)` bump. |

## 8.4 Logging

Convention: `private val logger: Logger = Logger.getLogger("<ClassName>")`
using `java.util.logging`, never `android.util.Log`. JUL is forwarded to
logcat on Android and to `System.err` in plain JVM unit tests — avoiding
the "Method X in android.util.Log not mocked" footgun in Robolectric-free
test contexts.

Levels:

| Level | When |
|-------|------|
| `Level.SEVERE` | Permanent unrecoverable conditions (ML Kit `MODEL_HASH_MISMATCH`) |
| `Level.WARNING` | Catch-site logging for any caught exception that's surfaced to the user. Always paired with `@Suppress("SwallowedException")` because the catch-and-surface pattern is legitimate even though Detekt flags it. |
| `Level.INFO` | OFF hit with blank product_name (rare drop-to-manual-entry case) |
| `Level.FINE` | Per-frame transient failures (ML Kit decode skip) — `FINE` is the JUL equivalent of `Log.d`, filtered out by default |

## 8.5 Error handling

The canonical pattern (`DetailViewModel.surfaceError`):

```kotlin
@Suppress("TooGenericExceptionCaught")
private fun surfaceError(operation: String, e: Exception) {
    @Suppress("SwallowedException")
    logger.log(Level.WARNING, "$operation failed", e)
    _uiState.update {
        it.copy(error = "Couldn't $operation: ${e.message ?: "unknown error"}")
    }
}
```

Four properties every `catch (Exception)` site must have:

1. **Rethrow `CancellationException` first.** Without this, structured
   concurrency breaks — see [feedback memory](../../../.claude/projects/-home-pkuhn-inventory-androic/memory/feedback_runcatching_swallows_cancellation.md)
   for the historical bug class. Use plain try/catch, never `runCatching`,
   for suspend code.
2. **Log via JUL** with `@Suppress("SwallowedException")`.
3. **Surface to user-visible UiState** (typed `error` field or
   `Phase.Error(...)`).
4. **Tone: `"Couldn't <verb>: <reason>"`** — verb is the operation name,
   reason is `e.message ?: "unknown error"`.

The M6 polish-pass audited every `catch (Exception)` site in `app/src/main`
against this checklist.

## 8.6 Coroutines

| Convention | Reason |
|------------|--------|
| All ViewModel side effects via `viewModelScope.launch { ... }` | Auto-cancelled on `onCleared`. |
| Track long-running jobs in `var fooJob: Job?` and cancel before re-launching | Prevents stale callbacks from clobbering new state. |
| No `runCatching` in suspend code | Swallows `CancellationException` silently. Use `try { } catch (e: CancellationException) { throw e } catch (e: Exception) { … }`. |
| Phase-ownership guards on async writes | `if (s.phase === phase) s.copy(phase = newPhase) else s` — referential equality prevents an in-flight result from overwriting a fresh phase. |

## 8.7 Testing strategy

Three test layers:

| Layer | Where | What |
|-------|-------|------|
| **Unit tests** (`app/src/test/`) | Plain JVM via Robolectric where Android APIs needed | ViewModels, repository, OFF client, Room (in-memory). Run on every PR via `:app:testDebugUnitTest`. |
| **Compose UI tests** (`app/src/androidTest/`) | Emulator or device | Per-screen rendering + interactions with a Fake repository. Compiled by CI via `:app:assembleDebugAndroidTest`, not run on CI. |
| **End-to-end UAT** | Emulator or device | `HappyPathUatTest` walks the entire v1 user flow (scan-add → list → detail rename → scan-remove → confirm gone). Plus a manual [UAT checklist](../uat/v1-uat-checklist.md) for visual / device-specific checks. |

## 8.8 Dependency injection

One `AppContainer`, manually instantiated. ViewModels are constructed via
`viewModelFactory { initializer { ... } }` in the nav graph; the container
is grabbed via the `Application` cast.

No `@Inject`, no `@Module`, no Hilt component graph. Adding a new dependency
is an explicit `val foo: Foo = ...` in `AppContainer`.

## 8.9 Resource organization

| Folder | Contents |
|--------|----------|
| `res/drawable` | Vector drawables. Just `ic_launcher_foreground.xml` in v1. |
| `res/mipmap-anydpi-v26` | `ic_launcher.xml` + `ic_launcher_round.xml` (adaptive icon manifests). |
| `res/values/colors.xml` | Only `ic_launcher_background` — UI colours live in Kotlin (`Color.kt`). |
| `res/values/strings.xml` | `app_name` only. No localization yet — deferred to v1.1. |
| `res/values/themes.xml` | Material You bootstrap theme (used before Compose takes over). |
| `res/values-night/themes.xml` | Dark variant of the bootstrap theme. |
