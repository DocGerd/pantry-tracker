# Security posture

> **Status:** Living document.
> **Last reviewed:** 2026-05-28.
> **Cadence:** reviewed on every major release (next: v2.0) and whenever a
> structural item below changes (e.g. new CI workflow, signing-cert rotation,
> distribution-channel change).

This document captures how we — Pantry Tracker, a single-maintainer
Android app distributed as a signed sideload APK — reason about the
security of the code, the build pipeline, and the release process. It
exists as the human-readable counterpart to whatever automated badges
(OpenSSF Scorecard, Best Practices) end up rendering on the
[README](../README.md), and to explain the cases where a high-quality
single-maintainer project structurally cannot meet a badge ideal — and
what we do instead.

The shape of this document mirrors the
[hangarfit `security-posture.md`](https://github.com/DocGerd/hangarfit/blob/main/docs/security-posture.md)
template that the maintainer uses across personal repos. The text is
specific to pantry-tracker; the section layout is the reusable bit.

## Threat model & scope

Pantry Tracker is a **single-user**, **offline-first** Android app that
tracks kitchen inventory at integer quantities. The app runs entirely
on-device against a local Room database; the only outbound network call
is an **anonymous** barcode lookup against the public
[Open Food Facts](https://world.openfoodfacts.org/) API (and three
sister-project hosts as 404 fallbacks). No accounts, no analytics, no
crash reporter, no advertising SDK. Distribution is signed sideload
APK on GitHub Releases — there is no Play Store / F-Droid presence in
scope of this document. See
[arc42 §1 — Introduction & goals](architecture/01-introduction-and-goals.md)
and [arc42 §3 — System scope & context](architecture/03-system-scope-and-context.md)
for the full design context.

**In scope** for this posture:

- The Android app source code in `app/` and the custom-detekt-rule
  module in `detekt-rules/`.
- The build pipeline:
  [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) and
  [`.github/workflows/security.yml`](../.github/workflows/security.yml).
- The release process documented in
  [`docs/release/SHIPPING.md`](release/SHIPPING.md), including the
  signing-key management model.
- The process / governance documents that gate what reaches `main` and
  `develop`: [`CLAUDE.md`](../CLAUDE.md), [`CONTRIBUTING.md`](../CONTRIBUTING.md),
  [`GOVERNANCE.md`](../GOVERNANCE.md), and the GitHub branch-protection
  rulesets that mechanically enforce them.
- The disclosure policy at [`SECURITY.md`](../SECURITY.md).

**Out of scope** — because the project has none of these:

- Multi-tenant operations, hosted services, server-side state.
- User accounts, identity, session management.
- Any infrastructure we operate (we operate none; GitHub-hosted runners
  and GitHub.com itself are upstream services we consume).
- Vulnerabilities requiring root access on a user's device.
- Third-party dependencies' own bug pipelines — these are routed to
  their maintainers per [`SECURITY.md`](../SECURITY.md) §"Out of scope".

This scoping deliberately matches [`SECURITY.md`](../SECURITY.md) §Scope
so that the disclosure policy and this posture document agree on what we
will and won't act on.

## What we do

The controls below are the active surface, listed by category. Each
item links to the file or workflow that implements it so the reader can
verify "is this still true?" without trusting this document.

### Code review

- **Mandatory multi-agent PR review cycle.** Every PR opened in this
  repo — by the maintainer or by an automated/agent contributor — goes
  through the multi-agent review cycle documented in
  [`CLAUDE.md`](../CLAUDE.md) §"PR review". Findings are posted as
  **inline review threads** on the diff (not single summary comments),
  fixed on the same branch, and resolved per-thread via GraphQL's
  `resolveReviewThread` mutation. Ready-to-merge is only declared once
  every thread is resolved. This is the mitigation for the single-maintainer
  Scorecard Code-Review zero — see §"Structural OpenSSF Scorecard zeros"
  below.
- **Only humans merge to `develop` or `main`.** Branch protection
  rulesets on GitHub require PR merges (no direct push) on both branches,
  and the project's hard governance rule (documented in
  [`CLAUDE.md`](../CLAUDE.md) and [`GOVERNANCE.md`](../GOVERNANCE.md))
  states that automated agents MUST NOT invoke `gh pr merge` or
  `git push origin develop` / `git push origin main`. Every change
  reaching either branch has a human "Merge pull request" click on the
  audit trail.

### CI gates

- **SHA-pinned third-party Actions.** Every action used in
  [`ci.yml`](../.github/workflows/ci.yml) and
  [`security.yml`](../.github/workflows/security.yml) is pinned to a
  full commit SHA with a `# vX.Y.Z` trailing comment for human
  readability. Example, from `ci.yml`:
  `uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd  # v6.0.2`.
  This pattern is uniform across both workflows.
- **Least-privilege `GITHUB_TOKEN` permissions.** Both workflows
  declare explicit `permissions:` blocks. `ci.yml` declares
  `contents: read` at the workflow top level (covering every step
  uniformly); `security.yml` declares the same scope per-job (`osv-scan`
  uses `contents: read`; `gitleaks` adds `pull-requests: read` so the
  action can read PR metadata for the diff scan). Neither job requests
  write scopes it does not need.
  See [SR-18](https://github.com/DocGerd/pantry-tracker/issues/18) for
  the historical reason this is explicit.
- **Detekt + AGP Lint on every PR.** The `build` job in
  [`ci.yml`](../.github/workflows/ci.yml) runs `:app:detekt` (Kotlin
  static analysis with a strict project-local config in
  [`detekt-config.yml`](../detekt-config.yml)) and `lintDebug` (AGP
  Lint). The `!cancelled()` guard on the detekt step ensures static
  analysis runs even when the build/test step fails — so devs get the
  feedback in the same iteration as compile errors.
- **Custom `ErrorToneRule` detekt check.** Lives in the standalone
  `:detekt-rules` Gradle module and enforces the project-wide convention
  that user-facing error messages start with `"Couldn't <verb>: ..."`.
  Backed by `ErrorToneRuleTest` (in `:detekt-rules:test`) which lints
  sample snippets and asserts exact finding counts — included in the
  default CI Gradle invocation so a regression that makes the rule
  silently inert fails the PR.
- **Instrumented tests on an emulator.** The `androidTest` job in
  [`ci.yml`](../.github/workflows/ci.yml) boots a Google APIs x86_64
  emulator via `reactivecircus/android-emulator-runner@…` and runs
  `:app:connectedDebugAndroidTest`. This catches instrumented-test
  bugs that static analysis and PR review cannot — e.g.
  `ActivityManager: Killing <pid>: permissions revoked` from
  [issue #117](https://github.com/DocGerd/pantry-tracker/issues/117)
  was invisible to every review surface but instant on an emulator boot.
- **Gradle wrapper SHA pin guard.** [`ci.yml`](../.github/workflows/ci.yml)
  has an explicit "Assert Gradle wrapper SHA is pinned (SR-5)" step
  that fails the PR if `distributionSha256Sum=` is missing from
  `gradle/wrapper/gradle-wrapper.properties`. A bare
  `./gradlew wrapper --gradle-version X.Y.Z` silently drops the pin;
  this guard catches the regression at PR time. See
  [`docs/release/SHIPPING.md`](release/SHIPPING.md) for the atomic
  two-flag form maintainers must use.

### Dependency hygiene

- **OSV-Scanner on every push + PR + weekly schedule.** The `osv-scan`
  job in [`security.yml`](../.github/workflows/security.yml) regenerates
  the Gradle lockfile from the current resolved dependency graph and
  runs OSV-Scanner against it. Exits non-zero on any vulnerability —
  fails the PR. The weekly `0 6 * * 1` cron re-runs the scan so newly
  disclosed CVEs surface even if no PRs land.
- **Gradle lockfile tracked on `develop`.** `app/gradle.lockfile` is
  checked into source control per
  [Gradle's official guidance](https://docs.gradle.org/current/userguide/dependency_locking.html).
  Dependabot updates it alongside `gradle/libs.versions.toml`. The
  OSV scan therefore reflects what would actually ship, not what was
  last hand-resolved. The `security.yml` job also fails loud if the
  lockfile is missing — catching the silent-no-op regression we hit
  during this milestone's first iteration.
- **Dependabot enabled** via [`.github/dependabot.yml`](../.github/dependabot.yml)
  for `gradle` and `github-actions` ecosystems. Action PRs and Gradle
  PRs follow the same multi-agent review cycle as any other PR — they
  do not auto-merge.

### Secret hygiene

- **Gitleaks on every PR + push + weekly schedule.** The `gitleaks`
  job in [`security.yml`](../.github/workflows/security.yml) runs
  `gitleaks/gitleaks-action` with `fetch-depth: 0` so it can scan the
  full PR diff against the base. Pre-flight git-history secret scan
  was completed as part of OSS-1 before the repository flipped public.
- **Local pre-commit + pre-push hooks** gate on detekt + secret
  scanning (see [`CLAUDE.md`](../CLAUDE.md) §"Hooks"). A hook failure
  means real signal — fix the underlying issue rather than skipping
  the hook (which the project's hard governance rule forbids).
- **`block-dangerous-bash.sh`** pattern-matches the full command-line
  text and refuses to let agent sessions invoke obvious foot-guns
  (`gh pr merge` on main, `git push --force` to protected branches, etc.).
  This is belt-and-braces on top of the branch-protection ruleset, not
  a substitute for it.

### Build / release hardening

- **R8 minification + resource shrinking on release builds.**
  [`app/build.gradle.kts`](../app/build.gradle.kts) §`buildTypes.release`
  sets `isMinifyEnabled = true` and `isShrinkResources = true`. This
  reduces the attack surface of the shipped APK (dead-code removal),
  shrinks the artifact, and forces the test of every keep-rule we
  rely on. The `-PverifyR8=true` flag enables an optional post-build
  task (`verifyR8KeepRules`) that runs
  [`scripts/uat/verify-r8-keep-rules.sh`](../scripts/uat/verify-r8-keep-rules.sh)
  after assembly to assert annotated classes (`@Serializable`, `@Entity`)
  survived shrinking — see
  [SR-80](https://github.com/DocGerd/pantry-tracker/issues/80).
- **Release-signing isolation.** The release keystore lives **outside**
  the repository and is referenced only via four Gradle properties
  (`PANTRY_TRACKER_RELEASE_STORE_FILE`, …PASSWORD, …KEY_ALIAS,
  …KEY_PASSWORD). [`app/build.gradle.kts`](../app/build.gradle.kts)
  enforces an all-or-nothing rule: all four set → signed APK; none set
  → `app-release-unsigned.apk` (intentionally not installable);
  some-but-not-all → `GradleException` at configuration time naming
  the missing props. Full procedure: [`docs/release/SHIPPING.md`](release/SHIPPING.md) §B.
- **Cert lifetime-identity model.** The v1.0.0 signing certificate
  (SHA-256 `ec9a4bb8…b3d9`) is the lifetime identity for all v1.0.x and
  subsequent minor versions. Android refuses to install an APK signed
  by a different cert over an existing install, so cert rotation is a
  user-visible breaking change. From v1.2.0 onwards we document the
  cert SHA in each release's GitHub Release notes so a user can verify
  a downloaded APK via `apksigner verify --print-certs` before
  installing; v1.0.0 / v1.1.0 predate this convention but were signed
  with the same cert.
- **Room schema migration emulator drive.** Every Room schema migration
  is exercised end-to-end on an emulator via
  [`scripts/uat/verify-migration-1-2.sh`](../scripts/uat/verify-migration-1-2.sh)
  before the release tag. JVM-only migration tests cannot catch
  device-level migration regressions (manifest-level component changes,
  SQLite engine differences). See
  [`scripts/uat/README.md`](../scripts/uat/README.md) for the
  `verify-migration-1-2.sh` runbook.
- **Real-device UAT for HTTP-client changes.** JVM tests with
  `MockEngine` cannot reproduce CDN-specific behaviour like Open Food
  Facts' chunked transfer encoding. Any change that touches request /
  response headers, body validation, or response classification goes
  through a real-device smoke test against the public OFF API — see
  [`docs/uat/v1-uat-checklist.md`](uat/v1-uat-checklist.md).
- **No cleartext network traffic.** OFF is HTTPS-only. We do not pin
  the certificate (intentional — pinning would brick the app on a
  routine OFF cert rotation, which would be a denial-of-service against
  every user). We ship an explicit
  [`network_security_config.xml`](../app/src/main/res/xml/network_security_config.xml)
  denying cleartext for every API level we support (`minSdk = 26`
  covers API 26/27 where the platform default would otherwise allow it);
  newer API levels' `usesCleartextTraffic="false"` default also applies
  but we do not rely on it alone.

### Disclosure

- **Responsible disclosure policy.** [`SECURITY.md`](../SECURITY.md)
  documents the private-disclosure channel (a GitHub Security
  Advisory draft endpoint), best-effort acknowledgement / assessment /
  fix timelines, and the scope split between this repo's code and
  third-party dependency upstreams.
- **Dated security-review notes.** Findings raised during development
  that don't fit a normal issue (or that warrant a written record of
  an accept-risk decision) land under
  [`docs/security/`](security/). The current review note is
  [`security-review-2026-05-17.md`](security/security-review-2026-05-17.md).

## Build-time vs. runtime exposure model — Dependabot triage policy

Pantry Tracker has *zero* runtime network/parser dependencies that ship in
the APK from the packages Dependabot flags against `settings.gradle.kts`. That
file declares no runtime dependencies — only `pluginManagement` repositories
and `dependencyResolutionManagement`. The Gradle plugin classpath it resolves
pulls a separate graph of build-tool jars (the Android Gradle Plugin, the
Kotlin Gradle plugin, KSP, detekt, …) that live ONLY on the developer machine
and the CI runner — never packaged into the APK, never on an end-user device.

Dependabot does not model this distinction; it reports CVEs against
`settings.gradle.kts` with the same severity as runtime risks. They are not.

**Triage policy:**

1. For any Dependabot alert on `settings.gradle.kts`, first verify the
   vulnerable package is absent from the shipped classpath:
   `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
   (the release variant is what ships; `debugRuntimeClasspath` is the
   dev-install variant — check both). If the package returns zero matches in
   each, the alert is build-time-only.
2. If a plugin owner has shipped a *stable* version that pulls the patched
   transitive, bump it in `gradle/libs.versions.toml` and re-verify with
   `./gradlew :buildEnvironment --refresh-dependencies`. Do not adopt
   pre-release (alpha/beta/rc) build tooling solely to clear a build-time CVE.
3. If no upstream fix is available in a stable release, dismiss the Dependabot
   alert with `dismissed_reason: tolerable_risk` (the GitHub API enum value)
   referencing this section; note the lag so future-us revisits when the owner
   ships.
4. Severity-weighting reads as a *developer-machine compromise vector*
   (medium — requires running a poisoned build script), NOT *end-user device
   compromise* (zero impact).

**2026-05-28 baseline (closed issue [#151](https://github.com/DocGerd/pantry-tracker/issues/151)):**
All 28 open Dependabot alerts at this snapshot were build-time-only —
`releaseRuntimeClasspath` and `debugRuntimeClasspath` both returned zero
matches for every flagged family (`netty`, `bouncycastle`/`bcprov`/`bcpkix`,
`jose4j`, `jdom2`, `commons-lang3`, `httpclient`). Their owners and disposition:

| Family | Owner (plugin → transitive) | Disposition at snapshot |
|---|---|---|
| Netty (21 alerts, #2–#7, #9–#11, #13, #15–#16, #20–#28) | none — AGP 9.2.1 no longer pulls Netty into the plugin classpath at all | Dismissed: package absent from both the plugin classpath *and* the runtime classpath. |
| Bouncy Castle ×3 (#17–#19) | AGP `com.android.tools.build:gradle:9.2.1` → `builder` / `apkzlib` → `bcprov-jdk18on:1.79`, `bcpkix-jdk18on:1.79` | Dismissed: latest stable AGP (9.2.1) still pins 1.79; 1.84 not yet pulled by any stable AGP. Revisit when AGP ≥ the release that bumps BouncyCastle to 1.84 lands. |
| jose4j (#14) | AGP → `bundletool:1.18.3` → `jose4j:0.9.5` | Dismissed: no stable AGP yet pulls jose4j 0.9.6. Revisit when bundletool ≥ a release pulling 0.9.6 ships inside a stable AGP. |
| jdom2 (#12) | AGP → `jetifier-processor:1.0.0-beta10` → `jdom2:2.0.6` | Dismissed: no stable AGP yet pulls jdom2 2.0.6.1. Revisit when jetifier-processor bumps. |
| commons-lang3 (#8) | AGP → `commons-compress:1.27.1` → `commons-lang3:3.16.0` | Dismissed: no stable AGP yet pulls commons-lang3 3.18.0. Revisit when commons-compress bumps. |
| httpclient (#1) | AGP → `sdklib` / `analytics-library:crash` → `httpclient` (declared 4.5.6, resolved to 4.5.14) | Dismissed: AGP 9.2.1 already resolves httpclient to **4.5.14**, past the 4.5.13 patched target — the CVE is already fixed in the resolved graph, and it never reaches the APK regardless. |

The single lever for the surviving BouncyCastle/jose4j/jdom2/commons-lang3
alerts is the Android Gradle Plugin version. AGP 9.2.1 was the latest *stable*
release at this snapshot (only 9.3.0 *alphas* existed); none of those
transitives are bumped to their patched versions in any stable AGP yet, so no
`libs.versions.toml` bump could close them. They were dismissed as
`tolerable_risk` per step 3 above, to be revisited when a stable AGP pulls the
fixes.

## Structural OpenSSF Scorecard zeros — and what we do instead

OpenSSF [Scorecard](https://github.com/ossf/scorecard) is a useful
external pass — it surfaces real issues — but its scoring model assumes
a multi-maintainer project distributing through standard package
ecosystems (npm, PyPI, Maven Central, container registries). A
single-maintainer Android sideload-APK project structurally cannot
score 10/10 on every check, no matter how disciplined its process.

This section walks through each check where the structural model
diverges from what we actually ship, explains why, and points at the
compensating control where one exists. The intent is to make those
zeros legible — to a reviewer, a future maintainer, or a downstream
user looking at the badge — rather than to argue them away.

The full live result for this repo is at
[scorecard.dev/viewer](https://scorecard.dev/viewer/?uri=github.com/DocGerd/pantry-tracker);
the parent audit issue for this section is
[#141](https://github.com/DocGerd/pantry-tracker/issues/141).

### Code-Review — structural zero

**What Scorecard checks:** every commit on the default branch was
approved by at least one reviewer account distinct from the author,
via a GitHub PR review.

**Why we score zero:** the project has one maintainer. There is no
second human GitHub account that can approve PRs. Self-review is
explicitly forbidden by GitHub (`event=APPROVE` on your own PR returns
422), and even if it weren't, "distinct reviewer-account" is the metric
Scorecard measures.

**What we do instead.** Every PR is routed through the **mandatory
multi-agent PR review cycle** documented in
[`CLAUDE.md`](../CLAUDE.md) §"PR review". Concretely:

1. The PR opener (maintainer or agent) does not push a "this is done"
   marker.
2. Multi-agent review is run — locally via the `pr-review-toolkit`
   skill or in the cloud via `/ultrareview`. Each finding is posted as
   an inline review thread on the relevant diff line.
3. Findings are fixed on the same branch (new commits, never amended).
4. Each fix's corresponding inline thread is resolved via GraphQL's
   `resolveReviewThread` mutation.
5. Only once every thread is resolved is the PR declared ready-to-merge.
6. The human maintainer performs the merge click. Automated agents are
   prohibited from invoking `gh pr merge` by the project's hard
   governance rule.

This is a different review model from "second human eyeballs", and we
do not claim it is equivalent — but it is **not zero review**, and the
inline-thread + GraphQL-resolve audit trail is permanent on the PR.
The structural zero on the Scorecard badge does not reflect the actual
review surface.

### Maintained — time-resolves, then bursty

**What Scorecard checks:** see the
[Maintained check docs](https://github.com/ossf/scorecard/blob/c22063e786c11f9dd714d777a687ff7c4599b600/docs/checks.md#maintained).
The check has two failure modes:

1. The repository must have existed for at least **90 days**. A repo
   younger than that triggers an automatic warn-and-zero, regardless
   of activity ("Repository was created within the last 90 days.
   Please review its contents carefully.").
2. Once past that threshold, Scorecard looks for sustained commit +
   issue activity in the most recent 90-day window.

**Why we currently score zero.** The repository was created on GitHub
on 2026-05-16 (it flipped public on 2026-05-27, but Scorecard's
`createdRecently` probe keys off the repo `created_at`, not the
visibility-flip date). The 90-day warn-and-zero clears on
**2026-08-14** (`created_at 2026-05-16 + 90 days`). Until then, the
score is "zero by repository age" and is not a reflection of activity.
Once it clears, the second failure mode applies.

**Why the second failure mode can dip later.** Release cadence is
feature-driven, not calendar-driven. As of 2026-05-28, three minor
releases have shipped within 10 days (v1.0.0 on 2026-05-18, v1.1.0 on
2026-05-19, v1.2.0 on 2026-05-28). A multi-week quiet period between
minor releases is normal for a kitchen-inventory app — it does not
mean the project is abandoned. Note: Scorecard's Maintained check
already weights issue-tracker activity in its partial-score path, so
the score dip is the human reviewer's signal to do a deeper sniff-test,
not a recommendation to use a different metric than Scorecard does.

### Contributors — structural zero

**What Scorecard checks:** see the
[Contributors check docs](https://github.com/ossf/scorecard/blob/c22063e786c11f9dd714d777a687ff7c4599b600/docs/checks.md#contributors).
The check counts contributing companies or organizations from the
project's recent contributors (among contributors of the last 30
commits, only those with at least 5 commits are counted), derived from
the `company` field on contributor GitHub profiles **and their public
GitHub organization membership**. A diverse contributor base reduces
single-organization capture risk on dependencies the OpenSSF
ecosystem cares about.

**Why we score zero:** the project has one maintainer. There is no
second contributor whose `company` field or public org membership
could increment the count. This is the same structural cause as the
Code-Review zero — the project is a single-maintainer Android app
([`GOVERNANCE.md`](../GOVERNANCE.md)), not a multi-company effort.

**What we do instead.** Nothing — this metric correctly characterizes
the project. Two **adjacent** controls reduce the neighbouring risk
this metric proxies for (single-author supply-chain capture), without
papering over the contributor-diversity gap itself:
Pinned-Dependencies (every action SHA-pinned; every Gradle dep
version-pinned with a lockfile), and Dependency-Update-Tool (Dependabot
enabled in [`.github/dependabot.yml`](../.github/dependabot.yml)).
The Contributors badge zero remains, accurately, zero.

### Token-Permissions — high, with evidence

**Why this scores well:** both workflow files declare explicit
least-privilege `permissions:` blocks. See
[`ci.yml`](../.github/workflows/ci.yml) line 19 (`contents: read` at
workflow top level) and
[`security.yml`](../.github/workflows/security.yml) lines 20-21 and
61-63 (per-job blocks for `osv-scan` and `gitleaks`). No workflow
requests write scopes it does not need.

No mitigation required — flagged here so a reader can verify the
evidence rather than trusting the badge.

### Pinned-Dependencies — high, with evidence

**Why this scores well:** every third-party action in both workflows
is pinned to a full commit SHA with a trailing `# vX.Y.Z` comment for
readability. The two patterns Scorecard cares about — GitHub Actions
pins and language-ecosystem pins — are both covered:

- **Actions** are SHA-pinned at every `uses:` site in
  [`ci.yml`](../.github/workflows/ci.yml) and
  [`security.yml`](../.github/workflows/security.yml).
- **Gradle dependencies** are version-pinned via
  `gradle/libs.versions.toml` and additionally locked via
  `app/gradle.lockfile`, which is tracked in source control and
  regenerated by the OSV-scan job to ensure the scan reflects shippable
  state.

No mitigation required — flagged here for evidence.

### Signed-Releases — partial, by distribution-channel choice

**What Scorecard checks:** release artifacts have either GPG signatures
or SLSA provenance attestations attached to the GitHub Release. Modern
Scorecard runs prefer SLSA.

**Why we score partial.** The shipped artifact is an `app-release.apk`
signed by the Android v2/v3 APK signing scheme with the project's
release keystore. This is the correct signing for the artifact's
distribution channel — `pm install` and the OS package installer
verify the APK signature on install, and Android refuses to install an
update signed by a different cert over an existing install. Scorecard
does not currently recognise APK signing as a release signature
because it scans for GPG `.sig` / `.asc` files or `attestations.json`
SLSA provenance, neither of which a sideload-Android-APK workflow
produces.

**What we do instead.**

- **Lifetime cert identity.** The signing cert SHA-256
  (`ec9a4bb8…b3d9`) is documented in each release's notes from v1.2.0
  onwards (see
  [v1.2.0 release notes](https://github.com/DocGerd/pantry-tracker/releases/tag/v1.2.0))
  and referenced from the design spec
  [`docs/superpowers/specs/2026-05-18-v1.1-fallbacks-and-undo-design.md`](superpowers/specs/2026-05-18-v1.1-fallbacks-and-undo-design.md).
  v1.0.0 / v1.1.0 predate this convention but were signed with the
  same cert. A downloader can verify the cert via
  `apksigner verify --print-certs app-release.apk` before installing.
- **Build pipeline integrity.** Release builds are produced from a
  signed tag on `main`, with the `chore(release): lock dependencies`
  commit immediately preceding the tag capturing the exact dependency
  state shipped. See [`docs/release/SHIPPING.md`](release/SHIPPING.md)
  §"Release-tag dependency-lock procedure".
- **Build pipeline isolation.** The keystore is not in the repository
  and not in CI secrets — release builds are produced on the
  maintainer's workstation from a keystore stored outside any
  version-controlled or CI-accessible location.

**Auto-attestation (active).** With GitHub immutable releases enabled, each
release asset now receives an automatic Sigstore-backed
[artifact attestation](https://docs.github.com/en/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds)
binding its digest to the tag + commit (verify with `gh attestation verify
app-release.apk -R DocGerd/pantry-tracker`). An earlier cosign + SLSA-generator
`.github/workflows/release.yml` was attempted but **retired** — its
attach-after-publish design is incompatible with immutable releases, and the
cosign step was broken under cosign v4 (issue #210). **Source-level build
provenance** (proving how/where the binary was built) remains a future step,
gated on either reproducible builds (keystore stays offline) or CI-side
signing (keystore in CI secrets). Scorecard Signed-Releases stays partial
regardless, as it does not recognize Android APK signing.

### Branch-Protection — configured, with accepted gaps

**What Scorecard checks:** see the
[Branch-Protection check docs](https://github.com/ossf/scorecard/blob/main/docs/checks.md#branch-protection).
The check inspects the default branch (and ideally release branches)
for PR-only merges, status checks, approvers, codeowners-required
review, stale-review dismissal, up-to-date-base enforcement, and
last-push approval.

**State at last review:** branch-protection is enforced by two
Repository Rulesets, split since 2026-05-28 (see #158):

- **Ruleset 16948699 "Protect main"** — covers `refs/heads/main` only.
  `strict_required_status_checks_policy: true` (PR head must be
  up to date with `main` before merge).
- **Ruleset 16993554 "Protect develop"** — covers `refs/heads/develop`
  only. `strict_required_status_checks_policy: false` to avoid
  integration-branch rebase churn (feature PRs land on develop
  frequently; requiring up-to-date would force a rebase after every
  intervening merge).

Both rulesets share the same other rules: PR-only merges (no direct
push), the `build` job from `ci.yml` as the only required status
check, no deletion, no non-fast-forward push, and
`dismiss_stale_reviews_on_push: true` (a PR approval is voided when
new commits land, so the approval reflects the current head).
`required_linear_history` is **off** on both by design: release-prep
merges from `release/<version>` into `main` are non-fast-forward by
construction (they're merge commits that preserve the GitFlow
structure). Enabling linear history would block them. This trade-off
is documented in [`CLAUDE.md`](../CLAUDE.md) §"Things that have
bitten past sessions" → "GitFlow ruleset constraints for this repo".

Because the GitHub legacy "Branch Protection Rules" API returns 404
for branches protected by a Repository Ruleset, older Scorecard
versions may misreport these checks as zero. The current state is
verifiable via:

    gh api repos/DocGerd/pantry-tracker/rulesets
    gh api repos/DocGerd/pantry-tracker/rulesets/16948699
    gh api repos/DocGerd/pantry-tracker/rulesets/16993554

#### Accepted Scorecard gaps

The Scorecard scan on 2026-05-28 at commit `6b792c7` reports
Branch-Protection **3/10** — see the live result on
[scorecard.dev/viewer](https://scorecard.dev/viewer/?uri=github.com/DocGerd/pantry-tracker).
The six warnings split: two no-cost knobs are being enabled separately
by the maintainer in the GitHub UI (see closing paragraph), and three
are structural and deferred under the single-maintainer plus
[`GOVERNANCE.md`](../GOVERNANCE.md) only-humans-merge-to-main rule:

- **`require-approvers`** — would require at least one PR approval
  before merge. The project has one maintainer and GitHub forbids
  `event=APPROVE` on your own PR (returns 422). The compensating
  control is the mandatory multi-agent review cycle described in
  §"Code-Review — structural zero" above.
- **`codeowners-required`** — would require review from a CODEOWNER on
  any touched path. [`.github/CODEOWNERS`](../.github/CODEOWNERS) lists
  only `@DocGerd`, so requiring a CODEOWNER review collapses to the
  same self-APPROVE prohibition as `require-approvers`.
- **`last-push-approval`** — would dismiss approvals and require a
  fresh one whenever the PR head is updated. Without a second human
  account able to approve in the first place, this knob has nothing
  to gate on.

The two non-structural knobs (`stale-review-dismissal`,
`up-to-date-branches`) are being enabled separately by the maintainer
in the GitHub UI for both `develop` and `main`, tracked in the same
parent issue
[#139](https://github.com/DocGerd/pantry-tracker/issues/139); once
flipped, the Branch-Protection score is expected to move 3 → ~6.

#### Branch-Protection — structural ceiling under solo maintainership

OpenSSF Scorecard alert
[#1](https://github.com/DocGerd/pantry-tracker/security/code-scanning/1)
reports a Branch-Protection score of 3/10 on `develop` and `main`. Six of
the ten warnings are addressable; four are structurally blocked under solo
maintainership:

| Control | Branches | Why blocked |
|---|---|---|
| `require-approvers` (count ≥ 1) | develop, main | A solo maintainer's own PR cannot satisfy `approvers ≥ 1` — GitHub forbids self-approval (HTTP 422). Every PR would deadlock. |
| `codeowners-review-required` | develop, main | Same root cause: a codeowners file would list the sole maintainer; their PRs cannot be approved. |
| `last-push-approval` | develop, main | After every push (including the maintainer's own) a re-review by a different reviewer would be required. None exists. |

The other six Branch-Protection warnings (`stale-review-dismissal` +
`up-to-date-branches`, each on develop + main) are addressable today and are
tracked in issue
[#139](https://github.com/DocGerd/pantry-tracker/issues/139).

**Future trigger to re-evaluate:** when a second person (co-maintainer or
trusted contributor with merge rights) is onboarded, OR when the repo
formally migrates from solo to multi-maintainer governance. At that point
the four blocked controls become enable-without-deadlock and we should flip
them. Until then this section documents the conscious accept-risk decision;
the alert remains visible in Code-Scanning as a known structural finding.

Scorecard linkage: `Branch-Protection` is held at score 3 by these four
structural gaps. Note the [tiered-scoring caveat](https://github.com/ossf/scorecard/blob/main/docs/checks.md#branch-protection)
— the check caps at Tier 1 (3/10) until the "require ≥ 1 reviewer" tier is
satisfied, which is structurally infeasible solo. Enabling #139's two
addressable knobs is correct on the merits (defence against the
approve-then-force-push pattern) but, because they sit at a higher tier than
the unmet reviewer requirement, did **not** move the headline score off 3 on
the 2026-05-28 rescan. The 3 → ~6 figure in the preceding subsection was the
pre-rescan estimate; the observed ceiling under solo maintainership is 3 and
stands until co-maintainer onboarding.

### CII-Best-Practices — pursuing

The OpenSSF Best Practices (formerly CII) badge is in the
[OSS-10 backlog](https://github.com/DocGerd/pantry-tracker/issues/104).
Once registered, the badge will be added to the
[README](../README.md) and this section updated with the badge URL.

### Dangerous-Workflow / Webhooks / License / Vulnerabilities

These either trivially pass (Apache-2.0 license is in
[`LICENSE`](../LICENSE); no dangerous workflow patterns; OSV-Scanner
fails the build on known CVEs) or are not under the project's control
(GitHub webhooks). They are noted here only so the badge reader can
see they were considered.

### Fuzzing / SAST

- **SAST.** CodeQL is in the
  [OSS-9 backlog](https://github.com/DocGerd/pantry-tracker/issues/102)
  and will land before the next major release. Until then, the SAST
  surface is detekt (with the custom `ErrorToneRule` regression test)
  plus AGP Lint — both run on every PR by `ci.yml`.
- **Fuzzing.** A
  [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) JVM
  fuzz target exercises `OffApiClient`'s JSON-decode path (the only
  network-derived input the app parses) via the `:app:fuzzTest`
  Gradle task and `OffApiClientFuzzTest` harness under
  `app/src/test/java/.../data/remote/`. The
  [`fuzz.yml`](../.github/workflows/fuzz.yml) workflow runs it weekly
  (Mon 04:15 UTC) and on `workflow_dispatch`, capped at 5 minutes per
  Jazzer's `@FuzzTest(maxDuration)` and 7 minutes at the Actions
  job-timeout level. The job is **non-gating** — findings upload as
  workflow-run artifacts (`jazzer-findings`) rather than failing
  feature PRs, per the
  [SR-144 issue](https://github.com/DocGerd/pantry-tracker/issues/144)
  framing that this is regression-catch quality, not OSS-Fuzz-grade
  fuzzing. The harness tolerates `SerializationException` and
  `IllegalArgumentException` (mirroring `OffApiClient.lookupOnce`'s
  catch arms) and treats any other thrown type as a finding.

## Reporting a security issue

Reporting policy, channels, and best-effort timelines live in
[`SECURITY.md`](../SECURITY.md). The short form: open a GitHub
Security Advisory draft at
<https://github.com/DocGerd/pantry-tracker/security/advisories/new>.
**Do not open a public GitHub issue** for security-sensitive findings.

## Review cadence

This document is reviewed:

- **On every major release** (currently: next at v2.0). The release
  branch's `release/<version>` PR is required to confirm the doc still
  describes reality before merging into `main`.
- **Whenever a structural item changes** — e.g. a new CI workflow
  lands, a workflow's permission scope widens, the signing cert
  rotates, the distribution channel changes, or a new compensating
  control is added for one of the structural Scorecard zeros above.
- **On any update to [`SECURITY.md`](../SECURITY.md)** — the scope
  definitions must agree.

Last reviewed: **2026-05-28** (initial version, OSS-11).

## Assurance case

An assurance case is a structured argument that the software is adequately secure for its purpose. It is required by OpenSSF Best Practices Silver (`assurance_case`).

**Claim.** Pantry Tracker adequately protects its users given its threat model: a single-user, offline-first Android app with no accounts, no server, and no user-to-user data flow.

**Threat model & trust boundary.** The app's only untrusted input surfaces are (1) Open Food Facts (OFF) HTTP responses (attacker-controlled or man-in-the-middle JSON) and (2) the scanned barcode string. Everything else is local: a Room database in app-private storage and Compose UI. There is no authentication, no secrets at rest beyond the release signing key (which lives outside the repo and off-device), and no inbound network surface.

**Secure-design argument (positive).**
- Network responses are parsed defensively: a fail-closed response-body size cap (256 KB, enforced by `readBoundedBody` on actual bytes received — not on a Content-Length header that OFF's CDN omits) prevents a hostile response from exhausting memory. Parse guards classify the response from the parsed envelope, not from attacker-supplied headers — this is the lesson from the SR-24 chunked-encoding incident documented in [arc42 §8](architecture/08-crosscutting-concepts.md) and [ADR-006](adr/0006-fail-closed-streamed-body-cap.md).
- TLS is the Android platform default (1.2+, certificate verification on, no overrides in `OffApiClient`); the app ships no bespoke cryptography.
- Released APKs are `apksigner`-signed under a lifetime cert (SHA-256 `ec9a4bb8…b3d9`); install integrity is verifiable (see §"Build / release hardening" and [`docs/release/SHIPPING.md`](release/SHIPPING.md)).
- Release builds run R8 minify + resource shrinking, reducing the shipped attack surface.
- The only exported component is `MainActivity`, which handles the `android.intent.action.MAIN` launcher action; it carries no content provider, file provider, or deep-link export surface that would expose stored data to other apps.

**Weakness-countering argument (negative).** The project actively hunts for the weaknesses it cannot reason away:
- Static analysis: CodeQL `security-and-quality` + Detekt (with the custom `ErrorToneRule`) gate every PR and run on schedule.
- Dependency risk: OSV-Scanner gates merges; Dependabot monitors weekly; the plugin-classpath vs. runtime-classpath exposure model is documented in §"Build-time vs. runtime exposure model" above.
- Secret leakage: Gitleaks runs on every PR.
- Fuzz testing: a Jazzer fuzz target exercises `OffApiClient`'s JSON-decode path weekly via [`fuzz.yml`](../.github/workflows/fuzz.yml).
- Process: every change reaches `develop` or `main` only via human-merged PR with multi-agent review.

**Residual risk (honest).** Bus factor is 1 (structural — see the single-maintainer model in [`GOVERNANCE.md`](../GOVERNANCE.md)); there is no dynamic analysis (DAST) — accepted for a memory-safe Kotlin client with one narrow untrusted input. These are tracked, justified accept-risk decisions, not oversights.
