# Security Review — Pantry Tracker v1.0 (Pre-Release)

- **Date:** 2026-05-17
- **Reviewed commit:** `46b761d` (main, post PR #25 + #26 + #27)
- **Package:** `de.docgerdsoft.pantrytracker`
- **versionName / versionCode:** 1.0.0 / 2
- **Mode:** detection only — no source files modified
- **Output:** this register + suggested-tickets table; no GitHub issues opened

## Executive summary

The repo is in strong shape for a v1.0 ship. **No Critical, no High findings.**
6 Medium findings cluster around three themes: log lines leak more than they
should (3 of 6), the OFF API URL is built from un-validated user input (1),
and supply-chain hardening has two release-time gaps (Gradle distribution
SHA pin, lockfile commit at tag). 15 Low findings are defense-in-depth
hardening, half of them around the same input/log/storage path. 6 Info items
are documentation/hygiene.

No finding is a release blocker for v1.0 in absolute terms, but **SR-2, SR-3,
SR-4, SR-5, and SR-6 are recommended to land before tagging v1.0.0** — they're
small, well-scoped fixes that close concrete attacker leverage or release-time
auditability.

Existing posture is unusually strong for a hobby v1: Detekt + Android Lint +
Gitleaks + OSV-Scanner + Dependabot all run in CI; GitHub Actions are
SHA-pinned; signing config externalized and validated (PR #27 hardening verified
intact); `allowBackup="false"`; HTTPS-only; no custom crypto; no
SharedPreferences; no WebView; no exported components beyond MAIN/LAUNCHER.

## Methodology

Five parallel review tracks, scoped to be independent:

| Track | Scope | Agent / model |
|---|---|---|
| A | Manifest & component surface | `general-purpose` / Sonnet |
| B | Network, TLS & input validation | `general-purpose` / Opus |
| C | Storage, crypto & logging | `general-purpose` / Sonnet |
| D | Build, signing, supply chain & CI | `general-purpose` / Opus |
| E | Local tool runs (OSV, Detekt, Lint, Gitleaks) | `general-purpose` / Sonnet |

Tools consulted at HEAD `46b761d`:

| Tool | Version | Result |
|---|---|---|
| Detekt | 1.23.8 | **0 findings** (pre-existing same-day report at `app/build/reports/detekt/`) |
| Android Lint | 9.2.1 | **7 warnings, 0 errors** — 1 Security category (`DataExtractionRules`), 4 Correctness, 2 Usability |
| Gitleaks | (manual git-grep sweep — binary install blocked) | **0 findings** across 167-commit history |
| OSV-Scanner | v2.3.8 (CI run) | **Refer to the latest GitHub Actions Security workflow run for `46b761d`** — Track E could not regenerate the lockfile locally |

## Severity rubric

| Level | Definition |
|---|---|
| **Critical** | Exploitable today, no user action needed, data/integrity loss |
| **High** | Exploitable with realistic conditions OR release-blocker policy |
| **Medium** | Defense-in-depth gap, real but indirect attacker leverage |
| **Low** | Hardening worth doing but no concrete attacker path |
| **Info** | Documentation, hygiene, observation |

---

## Findings register

| ID | Sev | Title | Location |
|---|---|---|---|
| SR-1 | Medium | `MainActivity` missing explicit `android:taskAffinity=""` | `app/src/main/AndroidManifest.xml:22` |
| SR-2 | Medium | Barcode interpolated into OFF API URL without validation or encoding | `app/src/main/.../OffApiClient.kt:52-56` |
| SR-3 | Medium | `confirm()` failure log serializes full `ScanCandidate` (barcode, name, brand, imageUrl) | `app/src/main/.../ScanViewModel.kt:168` |
| SR-4 | Medium | `submitManualEntry()` failure log includes user-typed product name | `app/src/main/.../ScanViewModel.kt:227` |
| SR-5 | Medium | Gradle distribution not SHA-pinned (`distributionSha256Sum` missing) | `gradle/wrapper/gradle-wrapper.properties` |
| SR-6 | Medium | `gradle.lockfile` not committed at release tag — auditability gap | `.gitignore:25`, `app/build.gradle.kts:145-149` |
| SR-7 | Low | No explicit `network_security_config.xml` (API 26/27 cleartext default exposes Coil image fetches) | `app/src/main/AndroidManifest.xml:11-19` |
| SR-8 | Low | Library-injected `ACCESS_NETWORK_STATE` permission undocumented in source manifest | merged manifest only |
| SR-9 | Low | R8/minification disabled for release builds | `app/build.gradle.kts:91`, `app/proguard-rules.pro` |
| SR-10 | Low | Barcode logged verbatim in `OffApiClient` error paths (×3) | `OffApiClient.kt:66, 70, 74` |
| SR-11 | Low | Barcode in `ScanViewModel.resolveBarcode` failure log | `ScanViewModel.kt:100` |
| SR-12 | Low | Barcode + brand logged in `ProductRepositoryImpl` INFO discard log | `ProductRepositoryImpl.kt:79` |
| SR-13 | Low | Barcode not normalized at input boundary (control chars / RTL / length cap) | scan + manual-entry boundary |
| SR-14 | Low | JSON string fields unbounded — memory DoS via malformed OFF response | `OffProductResponse.kt:13-25` |
| SR-15 | Low | Coil `ImageLoader` uses defaults — externally-controlled `image_url` allows `file://`, `content://`, oversize | `PantryTrackerApp.kt:17-19`, `ProductRepositoryImpl.kt:86` |
| SR-16 | Low | Lint `DataExtractionRules` warning — `allowBackup="false"` deprecated on API 31+ | `AndroidManifest.xml:13` |
| SR-17 | Low | Room `exportSchema = false` — no schema baseline for future migrations | `AppDatabase.kt:13` |
| SR-18 | Low | `ci.yml` missing top-level `permissions:` block (defaults to read/write `GITHUB_TOKEN`) | `.github/workflows/ci.yml` |
| SR-19 | Low | Release build hardening flags not explicit (`isDebuggable`, `isJniDebuggable`) | `app/build.gradle.kts` release buildType |
| SR-20 | Low | No pre-commit secret-scanning hook — Gitleaks only server-side | repo root (no `.pre-commit-config.yaml`) |
| SR-21 | Low | No semantic SAST (CodeQL paid for private; Semgrep free not configured) | `.github/workflows/` |
| SR-22 | Info | No CI assertion that `debuggable="false"` in release merged manifest | `.github/workflows/ci.yml` |
| SR-23 | Info | `android:enableOnBackInvokedCallback` not set (UX hardening, not security) | `AndroidManifest.xml` |
| SR-24 | Info | Ktor client has no response-body size cap (8 s timeout is the only de-facto bound) | `OffApiClient.kt:88-98` |
| SR-25 | Info | `java.util.logging` routing on Android not documented in code (JUL→logcat bridge) | repo-wide |
| SR-26 | Info | No `CLAUDE.md` at repo root capturing workflow/security guardrails | repo root |
| SR-27 | Info | Lint `InlinedApi` — `HapticFeedbackConstants.CONFIRM` (API 30) no SDK guard at `minSdk=26` | `ScanScreen.kt:48` |

---

## Detailed findings

### SR-1 · Medium · `MainActivity` missing explicit `android:taskAffinity=""`

**Location:** `app/src/main/AndroidManifest.xml:22` (`<activity android:name=".MainActivity">`)

With `taskAffinity` omitted, Android assigns the default affinity equal to the
application package name. A second app that explicitly sets `taskAffinity` to
`de.docgerdsoft.pantrytracker` and launches with `FLAG_ACTIVITY_NEW_TASK` can,
under specific launch-mode conditions, inject itself into the task history in a
way that surfaces its UI when the user returns to Pantry Tracker from Recents.
Mitigations exist on API 28+, but setting `taskAffinity=""` is the accepted
defense-in-depth recommendation for single-activity launcher apps.

**Recommendation:** add `android:taskAffinity=""` to the `<activity>` element.
Zero functional impact for a single-activity Compose app.

---

### SR-2 · Medium · Barcode interpolated into OFF API URL without validation or encoding

**Location:** `app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt:52-56`

```kotlin
val response: HttpResponse = httpClient.get(
    "https://world.openfoodfacts.org/api/v2/product/$barcode.json",
) { url.parameters.append("fields", "code,product_name,brands,image_url,status") }
```

Ktor's `HttpClient.get(urlString)` feeds the string to `URLBuilder.takeFrom`,
which **parses** per RFC 3986 but **does not percent-encode** path content —
it trusts the caller. Observed parser behavior for hostile barcodes:

| Input | Resulting URL | Risk |
|---|---|---|
| `foo?token=bar` | `…/product/foo` with `?token=bar&fields=…` | Injects a query parameter into OFF request |
| `foo#bar` | `…/product/foo.json` (fragment dropped) | Wrong product fetched silently |
| `../../admin` | `…/product/../../admin.json` → server normalizes to `/admin.json` | Wrong API endpoint hit |
| `\nGET /evil` | `IllegalArgumentException` | Not in `catch` list — propagates as Phase.Error |
| `..%2f` | `…/product/..%2f.json` | Server-side normalization decides |

ML Kit's `FORMAT_EAN_*/UPC_*` filter makes `rawValue` numeric-only in practice,
but (a) the format filter is a one-line builder change from being wider, (b)
`submitManualEntry` forwards arbitrary user-typed strings as barcodes when the
camera mode is bypassed, and (c) defense at the boundary is cheap.

**Recommendation:** validate barcode with `Regex("^[0-9]{6,14}$")` and return
null for mismatches; switch to Ktor's component URL builder (`url { appendPathSegments(…) }`)
so percent-encoding is explicit. Also add `IllegalArgumentException` to the
`catch` arms.

---

### SR-3 · Medium · `confirm()` failure log serializes full `ScanCandidate`

**Location:** `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt:168`

```kotlin
logger.log(Level.WARNING, "confirm() failed (mode=${state.mode}, phase=$phase)", e)
```

`$phase` is `ScanUiState.Phase.Preview`, whose `candidate` field transitively
exposes barcode, name, brand, and imageUrl via Kotlin's auto-generated
`data class` `toString()`. WARNING-level JUL records route to `Log.w` via the
Android JUL→logcat bridge — accessible to `adb logcat`, bug-report ZIPs, and
any future crash-reporting SDK breadcrumbs.

**Recommendation:** replace `phase=$phase` with `phaseType=${phase::class.simpleName}`.
The diagnostic value (which phase) is preserved; the leak (which product) is closed.

---

### SR-4 · Medium · `submitManualEntry()` failure log includes user-typed product name

**Location:** `app/src/main/java/de/docgerdsoft/pantrytracker/ui/scan/ScanViewModel.kt:227`

```kotlin
logger.log(Level.WARNING, "submitManualEntry(name=$trimmed, qty=$initialQuantity) failed", e)
```

`$trimmed` is the user-typed product name — the most user-controlled text in
the app. The exception + stack trace already give all the diagnostic value;
the name adds nothing but a privacy leak. This log site was **not** in the
initial scouting inventory of 7 sites — discovered during Track C deep dive.

**Recommendation:** drop `name=$trimmed` entirely. Keep `qty`.

---

### SR-5 · Medium · Gradle distribution not SHA-pinned

**Location:** `gradle/wrapper/gradle-wrapper.properties` (no `distributionSha256Sum` key)

`validateDistributionUrl=true` only validates that the URL points at
`services.gradle.org/distributions/`. It does **not** verify the integrity of
the downloaded archive. A TLS MITM against `services.gradle.org` or compromise
of its CDN would yield a tampered Gradle that the wrapper executes with full
developer/CI privileges. The wrapper jar (48 KB) is committed and git-protected,
but the multi-megabyte distribution it downloads is not.

**Recommendation:** add `distributionSha256Sum=…` for Gradle 9.5.1. Official
checksum at `https://services.gradle.org/distributions/gradle-9.5.1-all.zip.sha256`.
Use `./gradlew wrapper --gradle-version=X.Y.Z --gradle-distribution-sha256-sum=…`
when upgrading so the value moves atomically.

---

### SR-6 · Medium · `gradle.lockfile` not committed at release tag

**Location:** `.gitignore:25`, `app/build.gradle.kts:145-149`

Locking is configured for `releaseRuntimeClasspath` + `debugRuntimeClasspath`,
but the lockfile is gitignored and regenerated per CI run. Day-to-day this is
defensible (Dependabot doesn't update Gradle lockfiles → committing forces
manual `--write-locks` per dep PR), but **at the v1.0.0 release commit there
is no artifact-level evidence of what shipped**. Post-hoc CVE forensics
("did v1.0.0 ship with vulnerable transitive X?") has no concrete answer.

**Recommendation:** keep the day-to-day no-commit policy, but add a step to
`docs/release/SHIPPING.md`: at release-tag time, regenerate the lockfile
(`./gradlew :app:dependencies --write-locks`) and commit it as part of the
release commit, so the tagged commit carries the lockfile.

---

### SR-7 · Low · No explicit `network_security_config.xml`

**Location:** `app/src/main/AndroidManifest.xml:11-19`

With `minSdk=26`, the platform default for **API 26/27** still allows cleartext
HTTP. The OFF endpoint is hard-coded HTTPS, but Coil's `AsyncImage(model = imageUrl)`
will load whatever URL the OFF response provides. A rogue/poisoned OFF entry
returning `http://attacker.com/…` would succeed on API 26/27 — IP + scan-timestamp
correlation leaked. Explicit NSC also documents intent in `git diff` and creates
the file pinning would live in if v2 ever wants it.

**Recommendation:** create `app/src/main/res/xml/network_security_config.xml`
with `<base-config cleartextTrafficPermitted="false">`; reference via
`android:networkSecurityConfig` in `<application>`.

---

### SR-8 · Low · Library-injected `ACCESS_NETWORK_STATE` permission undocumented

**Location:** release merged manifest line 23; absent from source manifest

GMS / ML Kit / Data Transport injects `ACCESS_NETWORK_STATE` (normal-protection,
auto-granted). Invisible to readers of the source manifest. No harm today;
documentation gap only.

**Recommendation:** add an XML comment in the source manifest listing the
expected library-injected permissions so future reviewers/lint baselines aren't
surprised.

---

### SR-9 · Low · R8/minification disabled for release builds

**Location:** `app/build.gradle.kts:91` (`isMinifyEnabled = false`); `app/proguard-rules.pro` empty

Release APK ships with full class names, dead code paths, and library
internals unobfuscated. Not exploitable today (no secrets in APK, no DRM,
no anti-tamper requirement). Recommend deferring to v1.1 (rationale in the
"R8/minification decision" subsection below).

---

### SR-10 · Low · Barcode logged verbatim in `OffApiClient` error paths

**Location:** `OffApiClient.kt:66, 70, 74`

Three `WARNING`-level logs include full barcode value. Per-line bargining:

```kotlin
logger.log(Level.WARNING, "OFF lookup network error for $barcode", e)
logger.log(Level.WARNING, "OFF lookup JSON conversion error for $barcode", e)
logger.log(Level.WARNING, "OFF lookup serialization error for $barcode", e)
```

**Recommendation:** 4-2 truncation:
`val barcodeHint = "${barcode.take(4)}…${barcode.takeLast(2)}"`.

---

### SR-11 · Low · Barcode in `ScanViewModel.resolveBarcode` failure log

**Location:** `ScanViewModel.kt:100`

`logger.log(Level.WARNING, "resolveBarcode($barcode) failed", e)` — same
4-2 truncation recommendation as SR-10.

---

### SR-12 · Low · Barcode + brand logged in `ProductRepositoryImpl` INFO discard log

**Location:** `ProductRepositoryImpl.kt:79`

```kotlin
logger.log(Level.INFO, "OFF hit for $code discarded — name blank, brand=${off.brands}")
```

Both barcode and OFF-returned brand string in plain text. Brand provides zero
diagnostic value over a truncated barcode hint.

**Recommendation:** apply 4-2 truncation; drop brand.

---

### SR-13 · Low · Barcode not normalized at input boundary

**Location:** ML Kit → `ScanViewModel.onBarcodeDecoded` and
`ScanViewModel.submitManualEntry` boundaries

The un-normalized barcode flows into URL path (SR-2 angle), Room column
(`Product.barcode: String?`, no length cap), log lines (SR-10, SR-11, SR-12),
and Compose `Text(barcode)`. Control chars produce malformed logcat lines and
oddly-rendered DB rows; RTL override codepoints in Compose `Text` are a known
display-spoofing vector.

**Recommendation:** centralize `String.sanitizeBarcode()` that strips control
chars + U+202A/U+202B/U+202D/U+202E/U+2066-U+2069 and length-caps at 32; call
once at the boundary. Complements SR-2.

---

### SR-14 · Low · JSON string fields unbounded

**Location:** `app/src/main/.../data/remote/OffProductResponse.kt:13-25`

`OffProduct.productName`, `brands`, `imageUrl`, `code` are unbounded `String?`.
A 50 MB `product_name` from a buggy or hostile OFF response would (a) be
buffered into memory by Ktor's `response.body<OffApiEnvelope>()`, (b) be
written into SQLite, (c) attempt to render in Compose `Text`. The 8 s request
timeout is the only de-facto cap.

**Recommendation:** cap deserialized strings at sane bounds (256 chars for
name/brand, 2048 for `image_url`) — implement as `.take(N)` in the mapping
from `OffProduct` → `ScanCandidate.FromOff` in `ProductRepositoryImpl.kt:82-87`.

---

### SR-15 · Low · Coil `ImageLoader` uses defaults — externally-controlled `image_url`

**Location:** `PantryTrackerApp.kt:17-19`, `ProductRepositoryImpl.kt:86`,
`DetailScreen.kt:168-177`, `ScanResultSheet.kt:82-89`

`ImageLoader.Builder(ctx).build()` accepts defaults. Coil 3 auto-registers
`HttpUriFetcher`, `FileFetcher`, `ContentUriFetcher`, `ResourceUriFetcher`.
An OFF entry with `image_url: "file:///proc/self/maps"` would attempt a local
file read; `content://` would prompt unrelated content providers; a 4 GB JPEG
would OOM the decoder.

**Recommendation:** at the data layer in `ProductRepositoryImpl.kt:86`:
```kotlin
imageUrl = off.imageUrl?.takeIf { it.startsWith("https://") && it.length < 2048 }
```
Cleanest fix; complements SR-14. Optionally also restrict the `ImageLoader`'s
registered fetchers.

---

### SR-16 · Low · Lint `DataExtractionRules` warning

**Location:** `app/src/main/AndroidManifest.xml:13`

Android Lint warns: `android:allowBackup` is deprecated on API 31+. The intent
(disable backup + device transfer) is correct, but the modern mechanism
requires a `res/xml/data_extraction_rules.xml` referenced via
`android:dataExtractionRules`.

**Recommendation:** create `data_extraction_rules.xml` with
`<cloud-backup disableIfNoEncryptionCapabilities="true">` and
`<device-transfer disableIfNoEncryptionCapabilities="true">`; reference from
manifest. Pairs naturally with SR-7 (both are XML configs referenced from
the application element).

---

### SR-17 · Low · Room `exportSchema = false`

**Location:** `app/src/main/.../data/local/AppDatabase.kt:13`

Not a v1.0 blocker (version=1, nothing to migrate from). Becomes relevant
at v1.1 — without an exported schema JSON, `MigrationTestHelper` has no
baseline to test against. Code comment acknowledges and defers.

**Recommendation:** before the first version bump, set `exportSchema = true`,
configure `room.schemaLocation` in KSP arguments, commit `app/schemas/.../1.json`.

---

### SR-18 · Low · `ci.yml` missing top-level `permissions:` block

**Location:** `.github/workflows/ci.yml`

`security.yml` correctly declares `permissions: contents: read` at job level.
`ci.yml` inherits the repo default, which for older repos can be read/write.
The `build` job only needs `contents: read`.

**Recommendation:** add `permissions: contents: read` at the workflow level
in `ci.yml`.

---

### SR-19 · Low · Release build hardening flags not explicit

**Location:** `app/build.gradle.kts` release `buildType`

`isDebuggable` and `isJniDebuggable` rely on AGP defaults (false). Explicit
declaration costs two lines and defends against future merge-time accidents.

**Recommendation:** set `isDebuggable = false` and `isJniDebuggable = false`
explicitly.

---

### SR-20 · Low · No pre-commit secret-scanning hook

**Location:** repo root (no `.pre-commit-config.yaml`, no `.husky/`, no `lefthook.yml`)

Gitleaks runs server-side, blocking PR merge — but the secret is already in
the remote git history by the time CI flags it. Purge requires `git filter-repo`.

**Recommendation:** for a single-developer repo with externalized signing keys
(already done), accept-risk is reasonable. Revisit if contributor base grows.
Track D's recommendation: defer.

---

### SR-21 · Low · No semantic SAST

**Location:** `.github/workflows/`

Detekt + Lint catch style + a small set of well-known patterns. Neither does
taint tracking or dataflow analysis. CodeQL would, but is paid for private
repos. Semgrep with `p/kotlin` + `p/security-audit` is the free middle ground.

**Recommendation:** defer until repo flips public (CodeQL free) — see
"CodeQL/SAST decision" subsection. Optionally add Semgrep now.

---

## Info findings (hygiene/documentation)

### SR-22 · Info · No CI assertion for `debuggable="false"` in release merged manifest

Debug merged manifest contains `android:debuggable="true"` and exported
`PreviewActivity` (both expected from `debugImplementation` Compose tooling),
absent from release merged manifest (verified). A future accidental
`buildTypes.release { debuggable = true }` would not be caught automatically.

**Recommendation:** confirm AGP Lint rule `HardcodedDebugMode` is enabled on
release variant in CI, OR add a Gradle assertion script.

### SR-23 · Info · `enableOnBackInvokedCallback` not set

Not security. Opt-in flag for predictive back navigation on Android 13+.

### SR-24 · Info · Ktor client has no response-body size cap

Only the 8 s timeout caps response size; over a fast connection a malicious
server could push much more than 50 MB through. Track B already flags the
deserialization side (SR-14) — this is the transport side.

**Recommendation:** install Ktor `HttpResponseValidator` rejecting
`Content-Length > 256 KB`, OR accept-risk and rely on the timeout + Android
process limits.

### SR-25 · Info · `java.util.logging` routing on Android not documented in code

JUL records flow through Android's built-in JUL→logcat bridge: WARNING→`Log.w`,
INFO→`Log.i`, FINE→`Log.d` (suppressed by default in release). Non-obvious to
future contributors.

**Recommendation:** add a one-line comment in the app's first `Logger.getLogger`
call site.

### SR-26 · Info · No `CLAUDE.md` at repo root

Workflow expectations (PR review mandatory, no `--no-verify`, detekt config
list-key convention, signing-config property names) live only in the user's
private global config. New contributors / future Claude sessions don't see
them.

**Recommendation:** add a `CLAUDE.md` linking SECURITY.md, SHIPPING.md, and
listing the workflow guardrails.

### SR-27 · Info · Lint `InlinedApi` — `HapticFeedbackConstants.CONFIRM`

`ScanScreen.kt:48` uses an API-30 constant at `minSdk=26`. No crash risk
(constant is inlined); haptic feedback silently does nothing on API 26-29.

**Recommendation:** add `Build.VERSION.SDK_INT >= 30` guard, OR fall back to
`HapticFeedbackConstants.KEYBOARD_TAP` on older devices.

---

## Trade-off recommendations

### R8 / minification decision

**Defer to v1.1; document v1.0 risk acceptance.** Reasoning: (1) no
exploitable risk today — no secrets in APK, no DRM; (2) first-time minify
enablement is high-bug-density, especially around kotlinx.serialization
`@Serializable` classes which typically need explicit `-keep` rules; (3) v1.0
is days from ship and there's no on-device CI (KVM not enabled) — UAT walk
would need re-running against minified APK; (4) the defense-in-depth value is
real but small — Compose reverse engineering is non-trivial even unobfuscated.
v1.1 is the right slot: turn on, run full UAT on minified APK, add
reflective-path smoke androidTest cases.

### Certificate pinning decision

**Don't pin for v1.0; defer to v2 or never.** Reasoning: the threat model is
thin — worst a MITM can do is return a fake product name + image which the
user sees on screen before tapping Save. There's no auth token to steal, no
PII in flight, no privileged action behind the response. Against that, OFF is
community-run infrastructure on Let's Encrypt-style 90-day rotations, and a
pin mismatch bricks the lookup path with no remote-update channel (no
Firebase Remote Config, no backend to consult). If pinning is revisited,
`<pin-set>` in `network_security_config.xml` with at least one backup pin and
an `<expiration>` is the right shape — but it requires an operational
playbook this project doesn't have.

### `network_security_config.xml` decision

**Add it for v1.0** (SR-7 above) — even without pinning. One file +
`cleartextTrafficPermitted="false"` normalizes behavior across API 26-36,
makes future pinning a one-line edit, is `git diff`-auditable.

### `gradle.lockfile` commit decision

**Day-to-day no-commit; commit at release-tag.** Reasoning in SR-6 above —
this is the lowest-friction policy that still answers "what artifacts shipped
in v1.0.0?" post-hoc.

### CodeQL / SAST decision

**Defer until repo flips public** (CodeQL free for public, paid for private
under GHAS). Crossover happens exactly when the public release happens.
Semgrep is the free interim option if comfort with false-positive triage is
there.

### Pre-commit hooks decision

**Accept-risk for v1; revisit if contributor count grows beyond one.**
Signing keys externalized (PR #27), no credential-shaped values handled in the
repo, server-side Gitleaks catches anything reaching remote. Pre-commit
framework + maintenance + onboarding friction outweighs the marginal risk
reduction for a single-developer repo.

---

## Suggested tickets

One ticket per finding (or merged where the fix is the same code change).
Severity → suggested label mapping:

- Medium → `security`, `release-recommended`
- Low → `hardening`
- Info → `hygiene`

| Ticket | Sev | Title | Acceptance criteria sketch |
|---|---|---|---|
| T-1 | Medium | Set `taskAffinity=""` on MainActivity | Attribute present in source manifest; release merged manifest verified; Lint passes |
| T-2 | Medium | Validate barcode + use Ktor component URL builder in OffApiClient | Numeric-only `^[0-9]{6,14}$` accepted; non-numeric returns null without network call; `IllegalArgumentException` caught; unit tests cover hostile `?`/`#`/whitespace/control-char inputs |
| T-3 | Medium | Redact `confirm()` failure log in ScanViewModel | Log emits `phaseType=${phase::class.simpleName}` only — no barcode/name/brand/imageUrl in output |
| T-4 | Medium | Remove user-typed name from `submitManualEntry` failure log | Log contains `qty` only; `name` parameter not logged |
| T-5 | Medium | Pin Gradle distribution SHA-256 | `distributionSha256Sum` set in `gradle-wrapper.properties`; documented in SHIPPING.md that value moves atomically with `distributionUrl` |
| T-6 | Medium | Commit `gradle.lockfile` at release-tag time | Release checklist in SHIPPING.md includes `--write-locks` step; v1.0.0 tagged commit contains `app/gradle.lockfile` |
| T-7 | Low | Add `network_security_config.xml` with cleartext denied | File exists; referenced in manifest; debug build attempting cleartext URL throws |
| T-8 | Low | Document library-injected permissions in manifest | XML comment listing `ACCESS_NETWORK_STATE` (and any other GMS-injected entries) present in source manifest |
| T-9 | Low | Enable R8 minify + resource shrink (v1.1 milestone) | `isMinifyEnabled=true`; `isShrinkResources=true`; release APK passes full UAT smoke; kotlinx.serialization `@Serializable` rules added |
| T-10 | Low | Redact barcode in all OFF-related log lines (4-2 truncation) | `OffApiClient.kt:66,70,74`, `ScanViewModel.kt:100`, `ProductRepositoryImpl.kt:79` all use truncation helper; brand dropped from repo log |
| T-11 | Low | Sanitize barcode at input boundary | `String.sanitizeBarcode()` helper; control chars + RTL-override codepoints stripped; length-cap 32; called once in `onBarcodeDecoded` and `submitManualEntry`; unit tests per char class |
| T-12 | Low | Length-cap OFF response string fields | Name/brand truncated to 256 chars; imageUrl rejected (null) if >2048 chars or scheme != `https://`; unit test with 1 MB name fixture |
| T-13 | Low | Restrict OFF `image_url` scheme + length at persistence | `ProductRepositoryImpl.kt:86` accepts only `https://` + length<2048; unit tests for `file://`, `content://`, `http://`, oversized, well-formed |
| T-14 | Low | Add `data_extraction_rules.xml` for API 31+ backup config | File exists with `<cloud-backup>` + `<device-transfer>` set to `disableIfNoEncryptionCapabilities="true"`; Lint `DataExtractionRules` warning gone |
| T-15 | Low | Enable Room `exportSchema` ahead of first DB version bump | `exportSchema = true`; `app/schemas/.../1.json` committed; `MigrationTestHelper` test skeleton added |
| T-16 | Low | Add `permissions: contents: read` to CI workflow | Top-level `permissions:` block in `ci.yml`; build still passes |
| T-17 | Low | Set release hardening flags explicitly | `isDebuggable=false` and `isJniDebuggable=false` set in release buildType |
| T-18 | Low | (Optional) Add pre-commit Gitleaks hook | `pre-commit` framework installed; `gitleaks` hook configured; clean run on full repo. Open until contributor count > 1. |
| T-19 | Low | (Optional / deferred) Add SAST workflow | When repo flips public, CodeQL workflow added scanning java/kotlin; OR Semgrep workflow with documented suppression policy |
| T-20 | Info | (Optional) CI assertion that release manifest has `debuggable=false` | CI fails if `android:debuggable="true"` appears in release merged manifest; `HardcodedDebugMode` Lint rule confirmed on release variant |
| T-21 | Info | (Optional) Cap Ktor response body size | `HttpResponseValidator` rejects responses with `Content-Length > 256 KB`; synthetic 1 MB response test fails fast |
| T-22 | Info | Document JUL → logcat bridge in app startup | One-line comment at first `Logger.getLogger(...)` call site explaining level routing on Android |
| T-23 | Info | Add `CLAUDE.md` at repo root | Covers PR review workflow, no `--no-verify`, detekt list-key convention, signing-config property names; cross-links SECURITY.md and SHIPPING.md |
| T-24 | Info | Guard `HapticFeedbackConstants.CONFIRM` by API level | `Build.VERSION.SDK_INT >= 30` guard, OR fallback to `KEYBOARD_TAP` for API 26-29; Lint `InlinedApi` warning gone |
| T-25 | Info | (Optional) Set `enableOnBackInvokedCallback` | Flag set; back-gesture tested on Android 13+ |

**Suggested ordering for landing before v1.0.0 tag:**
T-2, T-3, T-4 (one tight commit on the scan/log path) — T-5, T-6 (release-time
auditability, two-line + checklist update) — T-1 (one-line manifest change).
That's ~5 small focused commits covering all 6 Medium findings. Everything
else (Low + Info) is post-v1.0 hardening.

---

## Audited & clean

Items checked and found to require no action:

**Manifest & components**
- `android:allowBackup="false"` in source AND both merged manifests
- `tools:replace="android:allowBackup"` not needed (no library overrides it)
- `android:usesCleartextTraffic` correctly absent (API 28+ default = deny)
- `android:debuggable` correctly absent from release merged manifest
- `android:extractNativeLibs="false"` in both merged manifests
- `MainActivity.onCreate` reads only `savedInstanceState`; no intent extras processed
- `MainActivity` launch mode = default (`standard`); no task-hijack risk from mode
- `CameraPermissionGate.kt` correctly implements runtime permission state machine
- `POST_NOTIFICATIONS` correctly absent (app posts no notifications)
- No `<queries>` block needed (no `resolveActivity` / `queryIntentActivities` calls)
- All library-injected components (`ProfileInstallReceiver`, etc.) carry `exported=false` or are guarded by signature-level permissions

**Network / TLS**
- No `HostnameVerifier`, `X509TrustManager`, `SSLContext`, or `CertificatePinner` overrides
- Only network literal is `https://world.openfoodfacts.org/…`; no `http://` strings outside `xmlns:`
- No legacy network primitives (`HttpURLConnection`, raw `Socket`, etc.)
- Coroutine cancellation discipline correct: `CancellationException` explicitly rethrown before broad catches; no `runCatching` in suspend code
- Timeouts well within ANR budget (8 s each leg)
- User-Agent: app name + version, no device identifier or PII
- No polymorphic JSON deserialization

**Storage / crypto**
- Room schema reviewed — no PII, no credentials, no health/financial data; just product inventory
- All `@Query` annotations use Room parameterized bindings; no `SimpleSQLiteQuery`, no raw SQL string concatenation
- `fallbackToDestructiveMigration` correctly absent (schema mismatch crashes loudly)
- No SharedPreferences, DataStore, EncryptedSharedPreferences anywhere
- No crypto API usage anywhere (`Cipher`, `KeyStore`, `MessageDigest`, `SecureRandom`, `Random`, `Math.random`, `Tink`, `BouncyCastle`)
- No `print` / `println` / `System.out` / `System.err` in main source set
- No `StrictMode` setup that could leak in release

**Build / signing / supply chain**
- Signing config (PR #27 hardening verified intact): all 4 properties validated; fails fast with named missing properties; `~` rejected (absolute paths only); file existence checked at configuration time; `providers.gradleProperty()` used; no password or path passed through `println`
- Release `signingConfig` correctly conditional on `storeFile` being populated
- `settings.gradle.kts`: `repositoriesMode = FAIL_ON_PROJECT_REPOS` set; only `google()` + `mavenCentral()` + `gradlePluginPortal()`; no `jcenter()`, no `mavenLocal()`, no custom HTTP URLs, no `includeBuild`
- Gradle wrapper: official HTTPS distribution URL, `validateDistributionUrl=true`, `retries=0`, wrapper jar committed and git-protected (SHA pin is SR-5)
- `gradle.properties`: no secrets; `android.useAndroidX=true`; no suspicious flags
- `local.properties`: gitignored, never in git history
- Dependabot config: covers `gradle` + `github-actions`; weekly schedule; minor/patch grouped; PR limit 5
- All GitHub Actions SHA-pinned (40-char commit SHA + version comment) in both workflows
- CI triggers: only `push`/`pull_request` to main + weekly cron; no `pull_request_target`, no `workflow_dispatch`, no fork-PR amplification
- Both workflows use `cancel-in-progress: true` on ref-scoped concurrency groups
- `security.yml` declares least-privilege `contents: read` (plus `pull-requests: read` for Gitleaks)
- OSV-Scanner has explicit `test -f app/gradle.lockfile` gate against silent no-op regression
- No version pinned to `+` or open-range in `libs.versions.toml`
- Top-dep version cross-check (manual, OSV owns deeper scan): AGP 9.2.1, Kotlin 2.3.21, Ktor 3.5.0, kotlinx.serialization 1.11.0, Compose BOM 2026.05.00, Room 2.8.4, ML Kit barcode 17.3.0 — all current, no known CVEs at these pinned versions
- No `println` in any build script

**Tool runs**
- Detekt: 0 findings
- Android Lint: 0 errors, 0 fatals, 7 warnings (1 surfaced as SR-16, 1 as SR-27; rest cosmetic)
- Gitleaks (manual git-grep substitute): 0 findings across 167-commit history; `local.properties` never committed; `PANTRY_TRACKER_RELEASE_*` only ever in `app/build.gradle.kts`
- OSV-Scanner: deferred — authoritative answer is the GitHub Actions Security workflow run on `46b761d`

## Out of scope (explicit)

- Fixing any finding (per user instruction — detection only)
- Opening GitHub issues (suggestions live in this report)
- Dynamic/runtime testing — no APK install, no MITM proxy, no fuzzing
- Reverse-engineering compiled artifacts
- Threat modeling features not yet built
- Security of the Open Food Facts backend itself (out of repo)
- Modifying CI / Detekt / Lint / OSV configs (those are findings, not changes)
- GitHub branch-protection rules — not visible from the repo; recommend documenting in SECURITY.md whether enabled

---

*Report generated by 5-track multi-agent review. Source reports at
`/tmp/track-{a,b,c,d,e}-report.md` (ephemeral). Zero source files modified
during the review — verified by `git status` post-run.*
