# 12. Glossary

| Term | Definition |
|------|------------|
| **AGP** | Android Gradle Plugin — the Gradle plugin that drives the Android build. Version is set via the `libs.plugins.android.application` alias in `app/build.gradle.kts`. |
| **APK** | Android Package — the installable artifact produced by `assembleDebug` / `assembleRelease`. The unit of distribution for sideloaded installs. |
| **arc42** | The standard architecture-documentation template this folder follows. Originated by Gernot Starke. See [arc42.org](https://arc42.org). |
| **AAB** | Android App Bundle — the Play-Store-preferred upload format. Not used in v1.0 because we sideload. |
| **ART** | Android Runtime — the runtime on Android 5+. Disables JVM `assert()` by default, which is why we use `org.junit.Assert.assertTrue` in androidTest instead of bare `assert(...)`. |
| **CameraX** | The Jetpack camera library; abstracts over the older `camera2` API. We use `Preview` + `ImageAnalysis` + `DEFAULT_BACK_CAMERA`. |
| **Compose** | Jetpack Compose — the declarative UI framework. All of this app's UI is Compose. |
| **DAO** | Data Access Object — Room-generated class with the SQL queries. Ours is `ProductDao`. |
| **DI** | Dependency Injection. This app uses manual DI via `AppContainer`. See [ADR-001](09-architecture-decisions.md#adr-001--manual-di-via-appcontainer-not-hilt). |
| **DTO** | Data Transfer Object — the JSON shape of an external API response, before mapping to a domain type. `OffProductResponse` is our only DTO. |
| **EAN / UPC** | The barcode formats we decode (EAN-13, EAN-8, UPC-A, UPC-E). Other formats (QR codes, Code 128) are configured *out* in `BarcodeScannerOptions`. |
| **Fern** | The single seed colour `#4F7942` that drives the Material 3 `primary` slot. See [crosscutting concepts](08-crosscutting-concepts.md#81-theming). |
| **HardDenied** | Camera-permission phase: user denied + selected "don't ask again". Recoverable only via the system Settings app. |
| **JUL** | `java.util.logging` — the project's logging façade. Avoids `android.util.Log`'s "not mocked" footgun in JVM unit tests. See [ADR-006](09-architecture-decisions.md#adr-006--javautillogging-not-androidutillog). |
| **KSP** | Kotlin Symbol Processing — the build-time codegen tool that generates Room's DAO implementations. Replaces the older KAPT. |
| **M3** | Material 3 — the latest Material Design system; the foundation of the app's theme. Compose Material 3 is the implementation. |
| **ML Kit** | Google's on-device ML library. We use only the barcode-scanning module. Models live in Google Play Services on the device. |
| **MODEL_HASH_MISMATCH** | The `MlKitException` error code that signals the on-disk barcode model is corrupt; surfaces as a permanent `Phase.Error`. |
| **MVI** | Model-View-Intent — an architecture pattern. **Not** used here; we use ViewModel + typed UiState + StateFlow. See [ADR-005](09-architecture-decisions.md#adr-005--typed-uistate-per-screen-no-mvi-framework). |
| **OFF** | Open Food Facts — the open-data product database we look up barcodes against. v2 JSON API at `world.openfoodfacts.org/api/v2/product/<barcode>.json`. |
| **OkHttp** | The HTTP client engine Ktor sits on top of. Brings real timeouts (`SocketTimeoutException` → `IOException`). |
| **Pantry** | The user's local inventory. The `products` table. |
| **PR review (multi-agent)** | The dev-loop ritual where every PR is reviewed by 5–7 specialised review agents in parallel. See `feedback_always_pr_review.md` in the project's auto-memory. |
| **Room** | The Jetpack persistence library on top of SQLite. The only persistence layer in v1. See [ADR-002](09-architecture-decisions.md#adr-002--room-as-the-only-persistence-layer). |
| **ScanCandidate** | The sealed type returned by `lookupForPreview`: either `Persisted(product)` (already in our DB) or `FromOff(barcode, name, brand, imageUrl)` (resolved from OFF, not yet in our DB). |
| **SoftDenied** | Camera-permission phase: user denied, but `shouldShowRequestPermissionRationale == true` so the system will still surface the prompt on retry. |
| **StateFlow** | Kotlin coroutine cold-flow primitive that always holds a current value. The shape every ViewModel exposes for its UiState. |
| **Turbine** | The coroutines-test library for asserting on Flow emissions, e.g. `vm.uiState.test { awaitItem() }`. |
| **UAT** | User Acceptance Testing — the human gate before a release ships. See [`docs/uat/`](../uat/). |
| **UiState** | The typed data class per screen that represents everything the screen needs to render. Mutated only by the ViewModel, observed only by the composable. |
| **UnconfinedTestDispatcher** | The coroutines-test dispatcher used in unit tests so `viewModelScope.launch { }` runs synchronously. Set via `Dispatchers.setMain(...)` in `@Before`. |
