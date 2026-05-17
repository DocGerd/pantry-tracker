# 2. Architecture Constraints

## 2.1 Technical constraints

| # | Constraint | Source / rationale |
|---|------------|--------------------|
| TC-1 | **Android 8.0 (API 26) minimum** | Adaptive launcher icons require it; older devices are vanishingly rare in 2026. |
| TC-2 | **Android 16 (API 36) target** | Latest stable at the time of v1.0. Forces edge-to-edge + the modern permission flow. (Android 15 is API 35; API 36 is Android 16 Baklava.) |
| TC-3 | **Kotlin 2.x with K2 compiler** | Project-wide; non-negotiable from M0. |
| TC-4 | **Jetpack Compose for all UI** | No XML layouts. Material 3 theme tokens. |
| TC-5 | **Room as the only persistence layer** | No SharedPreferences for inventory data; no remote cache database. |
| TC-6 | **JVM 21 source + target** | Matches CI's Temurin 21 toolchain. |
| TC-7 | **No external DI framework** | Manual DI via `AppContainer`. Hilt was considered and rejected (overkill for one screen module). |
| TC-8 | **Single Gradle module** | `:app`. Module splitting was deferred until there is a second consumer of any building block. |

## 2.2 Organizational constraints

| # | Constraint | Notes |
|---|------------|-------|
| OC-1 | **Single maintainer** | All design decisions optimize for one-person comprehension. |
| OC-2 | **Every milestone gets a spec + plan + PR review** | The workflow ritual is encoded in `docs/superpowers/specs/`, `docs/superpowers/plans/`, and a CLAUDE-Code-driven multi-agent review on every PR. |
| OC-3 | **No external dependencies on people** | OFF is the only third-party service; if it goes down, the app degrades to manual entry rather than failing. |
| OC-4 | **License: source code is private** | No public Play Store listing for v1.0; sideloaded onto the maintainer's device. |

## 2.3 Conventions

| # | Convention | Where enforced |
|---|------------|----------------|
| CV-1 | **Logging via `java.util.logging.Logger`**, not `android.util.Log` | Avoids "Method X in android.util.Log not mocked" in unit tests. See `ScanViewModel`, `OffApiClient`, `DetailViewModel`, `CameraPreview` — all use JUL. |
| CV-2 | **Every `catch (Exception)` in suspend code rethrows `CancellationException` first** | Otherwise the cancel-cooperative coroutine machinery breaks. Detekt enforces a related rule. See [crosscutting concepts](08-crosscutting-concepts.md#error-handling). |
| CV-3 | **User-facing error tone: `"Couldn't <verb>: <reason>"`** | Audited in M6. Reference template: `DetailViewModel.surfaceError`. |
| CV-4 | **Typed `UiState` data class per screen**, exposed as `StateFlow<X>` | No screen reaches directly into the repository — all observation flows through the ViewModel. |
| CV-5 | **Compose tests in `app/src/androidTest/`, unit tests in `app/src/test/`** | CI compiles both via `:app:assembleDebugAndroidTest`; unit tests run via `:app:testDebugUnitTest`. Compose tests run on emulator or device, not in CI. |
| CV-6 | **No `fallbackToDestructiveMigration`** on the Room database | A schema mismatch is a bug, not a user-data-wipe event. See `AppContainer`. |
| CV-7 | **Detekt + Kotlin formatter are mandatory CI gates** | Configured in `detekt-config.yml`; runs as a separate CI step that survives compile failures. |
