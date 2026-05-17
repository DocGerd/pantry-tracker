# 7. Deployment View

The app ships as a single APK installed directly on a user's Android device.
There are no servers to deploy.

## 7.1 Runtime topology

```
   ┌────────────────────────────────────────────────┐
   │  User's Android device (API 26+)               │
   │  ┌────────────────────────────────────────┐    │
   │  │  Pantry Tracker process                │    │
   │  │  ┌──────────────────────────────────┐  │    │
   │  │  │  MainActivity (single Activity)  │  │    │
   │  │  │  ┌────────────────────────────┐  │  │    │
   │  │  │  │  Compose nav graph         │  │  │    │
   │  │  │  │  + AppContainer (Room,     │  │  │    │
   │  │  │  │    OffApiClient,           │  │  │    │
   │  │  │  │    repository)             │  │  │    │
   │  │  │  └────────────────────────────┘  │  │    │
   │  │  └──────────────────────────────────┘  │    │
   │  └────────────────────────────────────────┘    │
   │  ┌────────────────────────────────────────┐    │
   │  │  Room file: pantry-tracker.db          │    │
   │  │  (in app's private data dir)           │    │
   │  └────────────────────────────────────────┘    │
   │  ┌────────────────────────────────────────┐    │
   │  │  Coil image cache (disk; OFF photos)   │    │
   │  └────────────────────────────────────────┘    │
   │  ┌────────────────────────────────────────┐    │
   │  │  Google Play Services (ML Kit barcode  │    │
   │  │  model — downloaded on first scan)     │    │
   │  └────────────────────────────────────────┘    │
   └─────────────┬──────────────────────────────────┘
                 │ HTTPS (only when resolving a new barcode)
                 ▼
        ┌─────────────────────────┐
        │  world.openfoodfacts.org│
        └─────────────────────────┘
```

## 7.2 Build artifacts

| Artifact | Purpose | Where it comes from |
|----------|---------|---------------------|
| `app-debug.apk` | Dev / sideload to maintainer's device for testing | `./gradlew :app:assembleDebug` |
| `app-release.apk` | Signed APK for the public v1.0 install | `./gradlew :app:assembleRelease` (requires keystore — see [SHIPPING.md](../release/SHIPPING.md)) |
| `app-androidTest.apk` | Compose UI test runner (not user-facing) | `./gradlew :app:assembleDebugAndroidTest` |

The release APK is the only thing shipped to users. v1.0 distribution is
sideload — no Play Store presence.

## 7.3 Install paths

See [`docs/release/SHIPPING.md`](../release/SHIPPING.md) for the full procedure.
Summary:

| Path | When to use | Effort |
|------|-------------|--------|
| `adb install` (USB + Android Studio adb) | Maintainer's own device, dev/QA | Low |
| APK on the device (sideload) | Sharing with another person | Medium (recipient enables Unknown Sources) |
| Firebase App Distribution | Beta testers | Higher setup, then frictionless |
| Play Store internal track | Eventual Play Store launch | Highest setup, requires developer account |

## 7.4 What runs on what

| Component | Process | Notes |
|-----------|---------|-------|
| Compose UI | Main thread | Standard. |
| CameraX `ImageAnalysis` | Single-thread `Executors.newSingleThreadExecutor()` per `CameraPreview` composition | Disposed on `onDispose`. Backpressure: `STRATEGY_KEEP_ONLY_LATEST` — frames drop rather than queue. |
| Room I/O | `Dispatchers.IO` via Room's own dispatcher | All DAO calls are `suspend`. |
| OFF HTTP | Ktor's OkHttp engine, default dispatcher | 8 s timeout each for connect/socket/request. |
| ML Kit decode | ML Kit's internal threads | We hand it an `InputImage`; callback comes back on the calling thread. |

No `WorkManager`, no foreground service, no `JobScheduler`, no broadcast
receivers — there is no background work at all in v1.

## 7.5 Permissions

| Permission | When asked | Why |
|------------|------------|-----|
| `android.permission.CAMERA` | First entry into either scan route, via the in-context rationale dialog | Required for the scan flow; spec rationale is the only place it's needed. |
| `android.permission.INTERNET` | Granted at install (normal permission) | OFF lookups and Coil image fetches. |

No location, contacts, storage, microphone, or sensor permissions.

## 7.6 Operational concerns

The app has no telemetry. The only operational signals are:
- **Local logcat** — JUL-logged WARN / SEVERE / FINE events from
  `ScanViewModel`, `OffApiClient`, `DetailViewModel`, `CameraPreview`,
  `CameraPermissionGate`. Accessible via `adb logcat -s ScanViewModel \
  OffApiClient DetailViewModel CameraPreview CameraPermissionGate` on a
  device with USB debugging enabled.
- **Crash → user-visible Phase.Error / Snackbar** — the user sees the
  failure tone (`Couldn't <verb>: <reason>`) but no traceback is captured.

A crash reporter (Sentry / Firebase Crashlytics) is explicitly deferred to
v1.1 — see [risks](11-risks-and-technical-debt.md).
