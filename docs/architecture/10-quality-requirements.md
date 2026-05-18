# 10. Quality Requirements

## 10.1 Quality tree

```
Quality
├── Privacy
│   ├── Outbound traffic: only OFF, only barcode in URL
│   ├── No analytics, no crash reporter, no accounts
│   └── No location, contacts, storage permissions
├── Reliability
│   ├── Schema mismatches crash; no silent destructive migration
│   ├── Repository failures surface as user-visible errors (never silent)
│   └── In-flight job cancellation on phase changes / dismiss
├── Performance
│   ├── Cold start to scannable camera < 2 s on a mid-tier device
│   ├── Barcode-decoded → preview sheet visible < 500 ms (assuming local hit)
│   └── List scrolling smooth (LazyColumn + Compose's stable keys)
├── Usability
│   ├── Two-CTA empty pantry (scan or add manually)
│   ├── Camera-permission rationale before the system prompt
│   ├── HardDenied recovery via Settings deep-link with auto-resume detection
│   └── Error tone: "Couldn't <verb>: <reason>" — never just stack-trace strings
├── Maintainability
│   ├── Single module, < ~40 .kt files in main
│   ├── No DI framework (one wiring file)
│   ├── Every milestone has spec + plan + PR review committed
│   └── Detekt + format checks gate every PR
└── Offline-first
    ├── Inventory fully readable/editable without network
    ├── Scan-to-add degrades to manual entry on OFF failure
    └── No "online required" mode anywhere
```

## 10.2 Quality scenarios

These are the testable scenarios. The manual [UAT checklist](../uat/v1-uat-checklist.md)
covers each.

### Q1 — Privacy: nothing leaks beyond OFF

| Stimulus | Source | Expected reaction |
|----------|--------|-------------------|
| Use app for 10 minutes with mitmproxy capturing traffic | maintainer | The only outbound requests are to the OFF project family at `/api/v2/product/<barcode>.json` — `world.openfoodfacts.org` on the happy path, plus `world.openbeautyfacts.org` / `world.openpetfoodfacts.org` / `world.openproductsfacts.org` only on a `404`-walk (see [§8.9](../architecture/08-crosscutting-concepts.md#89-security)). No analytics endpoints, no crash reporter, no fingerprinting beacons. |

### Q2 — Reliability: schema mismatch is loud

| Stimulus | Source | Expected reaction |
|----------|--------|-------------------|
| Install a v0.2 APK over a v0.1 install where v0.2 has a schema change without a Migration | maintainer | App crashes with a Room `IllegalStateException`. The user's pantry data remains on disk. Fix: ship a `Migration` and reinstall. |

### Q3 — Performance: cold start to scan

| Stimulus | Source | Expected reaction |
|----------|--------|-------------------|
| Tap launcher icon on a cold-killed app | user | Within 2 s on a Pixel-class device, the Home screen is interactive. Tapping "Scan to Add" → camera preview within another 1 s. |

### Q4 — Usability: HardDenied recovery

| Stimulus | Source | Expected reaction |
|----------|--------|-------------------|
| User previously denied camera permission with "don't ask again". Returns to app, taps "Scan to Add". | user | Sees "Camera access blocked / Open settings" screen. Taps Open settings → Settings app opens to Pantry Tracker's permission page. Grants camera → presses Back → app resumes → gate auto-flips to Granted → camera preview renders. |

### Q5 — Reliability: OFF down

| Stimulus | Source | Expected reaction |
|----------|--------|-------------------|
| Scan an unknown barcode with airplane mode on | user | Within ~8 s (timeout), the scan flow transitions to the ManualEntry sheet (not a "Network error" toast). User types name, taps Add, product is saved. No retry storm in logcat. |

### Q6 — Maintainability: new ProductRepository method doesn't break androidTest

| Stimulus | Source | Expected reaction |
|----------|--------|-------------------|
| Maintainer adds a method to `ProductRepository` and forgets to override it in a `FakeRepository` in `app/src/androidTest/` | CI | `:app:assembleDebugAndroidTest` fails on the PR with a clear "Class is not abstract and does not implement member" error. PR cannot merge. |

## 10.3 What's NOT a quality goal for v1

- **No accessibility hardening beyond Compose defaults.** TalkBack and Switch
  Access should work because Compose's defaults are correct, but no audit
  was done. v1.1 if needed.
- **No internationalization.** Strings are hardcoded English in Kotlin /
  inline composables. No `strings.xml` extraction. v1.1.
- **No animations polish.** Default Compose transitions only. Bottom sheets
  are abrupt. Acceptable for v1.
- **No tablet / foldable layouts.** Single-pane Phone layouts only.
- **No theme customization by the user.** Light/dark follows system; no
  in-app override.
