# 11. Risks and Technical Debt

## 11.1 Active risks

| # | Risk | Likelihood | Impact | Mitigation / current state |
|---|------|------------|--------|----------------------------|
| R-1 | **OFF v2 API breakage** | Low | Medium — scan flow degrades to manual entry until fixed | Treated as a "miss" by `OffApiClient` (returns null). User can still add the row manually. No crash. |
| R-2 | **ML Kit model download fails on first scan on a fresh install** | Medium | High — first scan is broken until Play Services downloads the model | `MlKitException.UNAVAILABLE` is treated as transient (logged at FINE, not surfaced) so we don't flash an error sheet during the normal cold-start case. User retries. Documented; no programmatic retry. |
| R-3 | **ML Kit model corrupts on disk** | Very low | High — every scan fails | `MlKitException.MODEL_HASH_MISMATCH` is surfaced as a permanent `Phase.Error` with full SEVERE-level logging. User must reinstall or clear app data. No auto-recovery. |
| R-4 | **User taps "Open settings" on a device with the Settings activity disabled** | Very low | Low — user can still uninstall + reinstall to recover | `ActivityNotFoundException` is caught, logged at WARNING, and surfaced as a Toast: "Couldn't open settings on this device". The user isn't stranded on a dead button. |
| R-5 | **Maintainer loses the release keystore** | Low at the start, grows over time | Catastrophic — can never sign an update for users running v1.0; would need a separate side-by-side install with a new keystore | Mitigation: back up the keystore + passwords to a secure offline location before v1.0 release. See [SHIPPING.md](../release/SHIPPING.md). |
| R-6 | **Sideload-only distribution means no auto-updates** | Certain | Medium — users on v1.0 stay on v1.0 unless someone hand-installs an update | Acceptable for single-user v1. If user count grows, move to Firebase App Distribution or Play Store (see [SHIPPING.md](../release/SHIPPING.md)). |

## 11.2 Known technical debt

| # | Item | Why it's debt | When to address |
|---|------|---------------|-----------------|
| TD-1 | **No schema export** (`exportSchema = false` in `@Database`) | First future `Migration` will have no reference schema to test against | Re-enable + commit `app/schemas/` **the same PR** that bumps `@Database(version = 2)`. Documented in `AppDatabase.kt`. |
| TD-2 | **Strings are inline, no `strings.xml` extraction** | Localization is mechanical drudgery once it's needed | v1.1 if a non-English user shows up. |
| TD-3 | **Versioning is `0.1.0` / versionCode 1** despite shipping a feature-complete v1 | Was never bumped; the release plan is to set it to `1.0.0` / versionCode 2 in the v1.0 release commit | Before tagging `v1.0`. |
| TD-4 | **No crash reporter** | A real bug on a real device will surface as "the app closed" with no signal back to the maintainer | v1.1 if real users join. ADR-008. |
| TD-5 | **`HomeScreenTest.FakeRepository` and `HomeScreenEmptyStateTest.FakeRepository` are duplicated** | A seventh `ProductRepository` method requires editing two files | Low priority — CI now catches drift via `assembleDebugAndroidTest`. Extract to a shared test-fixtures fake when there are 3+ duplicates. (Reviewer-flagged in M6 PR; left as low-pri.) |
| TD-6 | **Quantity stepper has no `Long`/overflow guard** | A user who scans the same barcode 9 billion times could overflow `Int` | Never. Add this when 9 billion scans is a real threat model. |
| TD-7 | **No deletion confirmation copy mentions data loss is undoable** | User might delete by accident | Acceptable per spec (`DeleteConfirmDialog` says "Cannot be undone in v1"). Consider in-app undo (snackbar with UNDO) in v1.1. |
| TD-8 | **No tests on real device CI** | Compose UI test compile-only via `assembleDebugAndroidTest`; emulator runs are manual | ADR-010 — explicit, not accidental. Revisit when reproducibility of UI tests becomes the bottleneck. |
| TD-9 | **Two `FakeRepository` types share signature drift risk** | See TD-5 | Same as TD-5. |

## 11.3 Risks explicitly accepted

| # | Risk | Why we accept it |
|---|------|------------------|
| AR-1 | **Single point of failure on OFF** for the enrichment step | The alternative (multiple catalogues) is too much code for v1. OFF's miss falls through to manual entry anyway, so the worst case is "user types the name". |
| AR-2 | **No background sync of OFF data** | There's nothing to sync — OFF is a read-only lookup, the inventory is local-only. |
| AR-3 | **No multi-device support** | One user, one device. Adding sync would require an account system, an auth server, and a sync protocol — all of which contradict ADR-008 (no servers, no accounts). |
| AR-4 | **Default Compose animations** | Polish is deferred. The app is functional without them. |

## 11.4 Open questions for v1.1+

- Should the app support exporting the pantry as CSV/JSON for backup?
- Should there be an expiry-date field on `Product`?
- Should we batch-scan (one camera session, multiple barcodes) for "I just emptied a grocery bag"?
- Is the fern-derived theme accessible (contrast ratios) for color-blind users?
- Should we add a Wear OS companion to view the inventory at the store?

None of these block v1.0.
