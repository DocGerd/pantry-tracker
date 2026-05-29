# Changelog

All notable changes to **Pantry Tracker** are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For install instructions see [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md).
For architecture documentation see [`docs/architecture/`](docs/architecture/).

---

## [Unreleased]

### Changed

- Home list rows now show the manufacturer beneath the product name (#190).

## [1.2.0] — 2026-05-28

### Added

- Offline cache for OFF lookups of non-pantry barcodes. Re-scanning a
  product that was previewed (but not added) now resolves locally —
  no network call, no 4-host fallback walk. 30-day TTL; cache row is
  deleted when the barcode is committed to the pantry. (#48)

### Changed

- Release builds now use R8 minify + `shrinkResources`. APK size
  reduced from 40,523,977 bytes (~40.5 MB) at v1.1.0 to 24,140,473
  bytes (~24.1 MB) at v1.2.0 — a 40.4% reduction. (SR-9, refs #36)

### Security

- Replace header-only OFF response body cap with streamed enforcement.
  The 256 KB cap now fires on chunked responses regardless of
  Content-Length advertisement, closing the gap left by the v1.1.0
  hotfix. (#52)
- R8 strips unused code from the release artifact, reducing attack
  surface. Belt-and-braces `-keep` rules in `app/proguard-rules.pro`
  preempt the "first-time R8 enable strips reflection target"
  symptom for kotlinx.serialization @Serializable types
  (`OffProduct`, `OffApiEnvelope`) and Room entities/DAOs (`Product`,
  `OffLookupCacheEntry`, `ProductDao`, `OffLookupCacheDao`, plus
  `AppDatabase` and `Converters` defensively). (SR-9, refs #36)

### Tests / quality

- 194 → 218 JVM unit tests.
- **RNG screenshot harness** added (#74 / SR-74) using Robolectric
  Native Graphics + golden-PNG diff. Covers icon variants, theme,
  font-scale, and Coil image rendering — retires UAT §0 rows 2-4,
  §2 rows 1, 2, and 4, §11 last row (greyed-row 45% opacity check),
  and v1.2 §11 Coil row.
- **Scan-flow Compose UI tests** (#75 / SR-75) cover OFF-hit, OFF-miss
  timeout fallback, in-inventory remove, and not-in-inventory +
  switch-to-Add — retires UAT §7 (8 of 9), §8 (5 of 6), §11 (5), §12 (2).
- **Search UI test** (#76 / SR-76) — retires UAT §9 (5 rows).
- **Camera permission deep-link + onResume tests** (#77 / SR-77) —
  retires UAT §4 (5 of 6), §5 (5 of 7), §6 (3 of 8).
- **Rotation + error-tone tests + detekt rule** (#78 / SR-78) — config-change
  + error tone; the `ErrorToneRule` extracted to a standalone
  `:detekt-rules` Gradle module with a proof test. Retires UAT §14
  (configuration change row) and §15 (error tone).
- **CI emulator job** (#79 / SR-79) on
  `reactivecircus/android-emulator-runner@v2.37.0`, API 35, PR-gating
  `connectedDebugAndroidTest` runs. ~5-8 min added per PR.
- **R8 static-inspection script** (#80 / SR-80) verifies `@Serializable` +
  `@Entity` classes survive minification in the release APK.
- **MIGRATION_1_2 emulator-drive runbook** (#81 / SR-81) — script + docs
  for on-device migration smoke; retires UAT v1.2 §1 upgrade-install row.
- **OFF resilience tests** (#82 / SR-82) — fallback-chain matrix +
  cache offline replay; retires UAT v1.2 §3-5 (3) + §12 (1).
- **androidTest permission-revoke fix** (SR-117 / PR #118) — adds
  `CameraPermissionGate.isCameraGranted` test seam so revoking a held
  runtime permission no longer kills the shared instrumentation
  process.
- **Room schema baseline committed** under `app/schemas/` (#57 / SR-17),
  unlocking `MigrationTestHelper`-based migration tests against the
  v1 → v2 schema delta.
- **UAT checklist umbrella closed** (#73) — every retired row annotated
  with `[automated by SR-N]`; new "Stays human-only" appendix
  enumerates the irreducibly-physical items + the v1.2 OFF CDN chunked
  encoding rule.
- **CLAUDE.md lessons fold** (#119) — 6 new entries in "Things that
  have bitten past sessions" covering revoke-of-held-permission,
  custom AndroidJUnitRunner.newApplication, detekt custom rules in
  standalone module, post-tag lockfile untrack, on-device CI catches
  what static review can't, GitFlow ruleset constraints.

---

## [1.1.0] — 2026-05-19

Fallbacks & undo milestone. All three feature items shipped:

### Added

- **Non-food product coverage.** OFF lookup now walks Open Food Facts → Open Beauty Facts → Open Pet Food Facts → Open Products Facts on `404`, so cosmetics, pet food, and household products that aren't on the food endpoint still resolve through their sibling databases (#44).
- **Delete UNDO.** After confirming a delete, a snackbar offers UNDO for a few seconds. Restored items preserve their original id and every column (name, brand, image_url, quantity, createdAt, updatedAt); `restore()` is wrapped in `NonCancellable` so a backgrounded VM can't lose the row mid-write (#46).
- **Surfaced failure feedback.** Both delete and undo paths now surface failures via `SnackbarEvent.DeleteFailed` / `RestoreFailed` instead of silently swallowing exceptions into `viewModelScope`'s `SupervisorJob`. The user sees *"Could not delete X"* / *"Could not undo delete of X"* if the DB rejects the operation.

### Changed

- **Dialog copy.** Confirmation dialog reads *"Delete {name}?"* / *"Delete {name} from your pantry?"* — the UNDO snackbar carries the reversibility message now, so the old *"Cannot be undone in v1"* apology is gone. Snackbar uses the matching verb (*"Deleted X"*) for consistency across dialog → button → snackbar.

### Security

- **OFF response body cap (SR-24).** The HTTP client now rejects OFF responses advertising `Content-Length > 256 KB` before parse, bounding worst-case memory + latency on a malformed response. **Known limitation:** OFF's CDN uses chunked transfer encoding and omits `Content-Length` on real responses, so chunked / no-`Content-Length` responses currently pass through the cap. A streaming-bounded body read for proper defence-in-depth is tracked as a v1.2 follow-up.
- **CancellationException contract preserved** across all new suspend code: every `try/catch` in `OffApiClient` + `HomeViewModel` rethrows CE explicitly. No `runCatching` introductions.

### Privacy

- **OFF lookup now walks up to four sister-project hosts on 404** (v1.0 used a single host). The chain is `world.openfoodfacts.org` (food) → `world.openbeautyfacts.org` (cosmetics) → `world.openpetfoodfacts.org` (pet food) → `world.openproductsfacts.org` (everything else). The happy path is still a single request to OFF; the chain only walks on `404` (5xx, timeout, network error, and a `status=1`-with-null-product OFF contract violation all fail fast — so a sick host can't multiply downtime by 4). Every request still carries only the scanned barcode plus the static `PantryTracker/1.1.0 (<repo URL>)` User-Agent — no user identifier, no cookie, no device fingerprint.

### Tests / quality

- 175 → 194 JVM unit tests (+19 over v1.0); `OffApiClientTest` grew from 32 → 45 cases (full chain matrix + body-cap boundary trio + chunked-bypass + first-and-second-host cancellation propagation + new `SnackbarEvent.{DeleteFailed,RestoreFailed}` emission pinning).
- `HappyPathUatTest` (instrumented) gained 2 new walks: delete-then-undo and delete-then-snackbar-dismiss.
- `SnackbarEvent` moved from `ui/home/` to `ui/common/` so v1.2 features can adopt the same channel pattern without churn.

---

## [1.0.0] — 2026-05-18

First public-ready release. Single-user, sideloaded — no Play Store presence.

### Added — inventory

- **Local pantry stored on-device** via Room SQLite. Survives force-stop, reboot, and app updates. A schema mismatch crashes the app rather than silently wiping pantry data (no destructive migration).
- **Browse and search** from the Home screen — alphabetical list, instant substring search, out-of-stock rows greyed (kept visible for quick re-add).
- **Two empty-state layouts** depending on context:
  - Empty pantry + blank query → two-CTA layout (**Scan to Add** / **Add manually**).
  - Empty pantry + active query → small "No matches for *…*" hint, no CTAs.

### Added — adding products

- **Scan-to-Add**: tap **Scan to Add**, point at an EAN-13 / EAN-8 / UPC-A / UPC-E barcode, the app resolves it against [Open Food Facts](https://world.openfoodfacts.org). On a hit, a preview sheet shows name + brand + product image + quantity stepper; confirm adds the row.
- **OFF-miss fallback** drops the user into manual entry pre-filled with the scanned barcode — no error, no retry storm.
- **Manual entry** from the FAB (Home) or the "Add manually" CTA (empty state) — type the name and quantity, no barcode required.

### Added — removing products

- **Scan-to-Remove**: tap **Scan to Remove**, scan a barcode of something in inventory, the preview sheet's quantity stepper is clamped to the current quantity; confirm applies a negative delta.
- **Not-in-inventory detection**: scanning an unknown barcode (or a known one already at quantity 0) shows a "Not in inventory" sheet with a **Switch to Add** button that flips mode and re-resolves the same barcode through the Add flow.
- **Detail-screen stepper** for ±1 adjustments without scanning.
- **Long-press → confirm → Delete** on a Home row, or the trash-can icon on the detail screen.

### Added — item detail

- Tap any row → detail screen with product image (if available), inline-editable name (commits on focus loss or IME Done), brand, barcode, quantity stepper, last-updated timestamp.
- Stale nav-arg handling: opening a detail screen for a product that no longer exists auto-pops back to Home instead of leaving a stuck screen.

### Added — theme + icon

- **Material 3** theme with **Fern** (`#4F7942`) as the M3 `primary` slot; remaining roles (secondary, tertiary, surface, error, …) come from M3's Baseline palette (no seed-derived tonal expansion). Light + dark scheme follow the system setting.
- **Adaptive launcher icon** — three white jars on a horizontal shelf, white-on-fern. Adapts to round / squircle / teardrop launcher masks; the foreground vector stays inside the 66dp safe zone.

### Added — camera-permission UX

- **In-context rationale dialog** before the system permission prompt — explains *why* we need the camera and that nothing leaves the device.
- **SoftDenied recovery** (denied without "don't ask again"): "Try again" affordance re-triggers the system prompt.
- **HardDenied recovery** (denied with "don't ask again"): "Open settings" deep-links to the app's permission page; after granting, returning to the app **auto-resumes** to the scan flow via an `ON_RESUME` permission re-check.
- **Settings-intent fallback**: on devices where `ACTION_APPLICATION_DETAILS_SETTINGS` is disabled (some MDM-locked or stripped builds), the user gets a "Couldn't open settings on this device" Toast instead of a silent dead button.

### Added — error UX

- Every failure surfaces a user-visible message in the canonical `"Couldn't <verb>: <reason>"` tone (`Couldn't open camera: …`, `Couldn't rename: …`, etc.). No raw stack-trace strings, no silent failures.
- Repository operations log via `java.util.logging` so logcat keeps a stack trace for diagnosis without leaking the message into the UI twice.

### Privacy

- **No accounts, no analytics, no crash reporter.** The app makes no outbound network calls of its own except the single OFF lookup (`GET world.openfoodfacts.org/api/v2/product/<barcode>.json`). The request carries only the scanned barcode plus a static `PantryTracker/<version> (<repo URL>)` User-Agent — no user identifier, no cookie, no device fingerprint.
- **ML Kit telemetry is disarmed at the manifest level.** Google's ML Kit barcode-scanning artifact transitively pulls in `google-datatransport`, which by default registers a `cct` backend that uploads SDK-usage events to `firebaselogging.googleapis.com` via a JobScheduler. We remove the three components that make that pipeline live — `TransportBackendDiscovery`, `JobInfoSchedulerService`, and `AlarmManagerSchedulerBroadcastReceiver` — via `tools:node="remove"` in our `AndroidManifest.xml`. The barcode detector itself does not depend on the transport; events queued by ML Kit fail backend-discovery and are silently dropped, so nothing leaves the device. The standard `firebase_data_collection_default_enabled=false` flag is **not** sufficient in standalone (no-Firebase-project) mode — it is only honoured by `FirebaseInitProvider`, which ML Kit does not register.
- **No cleartext network traffic.** OFF is HTTPS-only; no certificate pinning (intentional — pinning would break the app on routine cert rotation).
- **`android:allowBackup = false`** — Google Backup does NOT auto-restore the pantry on a fresh install. Trade-off documented in [arc42 §8.9](docs/architecture/08-crosscutting-concepts.md#89-security).
- **Permissions: `CAMERA` (runtime), `INTERNET` and `ACCESS_NETWORK_STATE` (install-time).** No location, contacts, storage, microphone, or sensor permissions. `ACCESS_NETWORK_STATE` is pulled in transitively by the HTTP stack to let it check connectivity before retrying; it does not query SSIDs and is not user-personally-identifying.

### Build / quality

- **CI runs on every PR**: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest` (compile-only — emulator runs are local), Detekt static analysis, Gitleaks secret scan, OSV-Scanner against the Gradle runtime classpath lockfile.
- **End-to-end UAT test** (`HappyPathUatTest`) walks the user-visible state machine (add → list → detail → rename → stepper → delete) through the real navigation graph with an in-memory repository.
- **Manual UAT checklist** at [`docs/uat/v1-uat-checklist.md`](docs/uat/v1-uat-checklist.md) covers the visual + device-specific paths that can't be automated (theme, icon rendering, OEM permission flows, real-barcode scanning).
- **arc42 architecture docs** at [`docs/architecture/`](docs/architecture/) covering all 12 standard sections.

### Out of scope for v1.0 — planned for v1.1+

- Localization (UI strings are inline English in Kotlin; no `strings.xml` extraction yet).
- Crash reporter (Sentry / Firebase Crashlytics).
- Background work of any kind (no `WorkManager`, no foreground service).
- Pantry sync / multi-device support — would require an account system that contradicts the privacy goal.
- Non-food product auto-resolution — v1.0 queries [Open Food Facts](https://world.openfoodfacts.org) only; non-food items (cleaning supplies, beauty, pet food) typically OFF-miss and fall through to the manual-entry sheet with the barcode pre-filled. Querying the sister Open Products Facts / Open Beauty Facts / Open Pet Food Facts endpoints would close the gap.
- Expiry-date tracking on `Product`.
- CSV / JSON export for pantry backup (paired with `allowBackup = false`).
- Batch-scan mode for unloading a full grocery bag in one camera session.
- Wear OS companion.
- Animations polish — default Compose transitions only.
- Tablet / foldable adaptive layouts.

### Install / distribution

- **Sideload-only** for v1.0. Signed APK distributed via GitHub Releases. See [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md) for the three install paths (`adb install` of debug APK, sideload of release APK, optional Firebase App Distribution).
- **Minimum Android version:** 8.0 (API 26).
- **Target Android version:** 16 (API 36).

### Acknowledgements

- Product data from [Open Food Facts](https://world.openfoodfacts.org) (Open Database License).
- Barcode decoding via [Google ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning).
- Built on Jetpack Compose, Material 3, Room, CameraX, Ktor, Coil, Detekt.

---

[1.0.0]: https://github.com/DocGerd/pantry-tracker/releases/tag/v1.0.0
