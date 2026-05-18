# Changelog

All notable changes to **Pantry Tracker** are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For install instructions see [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md).
For architecture documentation see [`docs/architecture/`](docs/architecture/).

---

## [1.0.0] — TBD (footnote link active once the v1.0 tag is pushed)

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
