# Pantry Tracker — Claude operating notes

Standalone Android Kotlin/Compose app for whole-number kitchen inventory.
Two Gradle modules: `:app` (the Android app) and `:detekt-rules` (a pure-JVM
module holding the custom detekt rule set — see the ErrorTone note below).
Latest release: v1.3.1 (2026-05-29). All releases ship as signed sideload
APKs on GitHub Releases — note v* releases are immutable (asset must be
attached at creation; see SHIPPING.md). CHANGELOG.md holds per-version history.

This file is loaded into every Claude Code session in this repo. Keep it
high-signal — pointers, not duplication of the source-of-truth docs.

## Hard governance rule: only humans merge to develop or main

The repo follows **GitFlow**: `develop` is the integration branch + default,
`main` is release-tagged/production. **Every change reaching `develop` OR
`main` MUST go through a PR that the human merges.** Claude may open PRs, push
to feature branches, dispatch review subagents, post + resolve inline threads,
create tags, build APKs, and create GitHub Releases — but MUST NOT invoke `gh
pr merge` or `git push origin develop`/`git push origin main` (or any
equivalent that lands code on either protected branch). No exceptions for
one-line reverts, "UAT-verified" hotfixes, wrap-up phases, or ambiguous "do
the rest" / "continue" instructions. When in doubt, ASK before merging. The
audit-trail gate is the human's explicit click on "Merge pull request".

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK (auto-signed with debug keystore)
./gradlew :app:assembleRelease        # release APK (needs the 4 keystore props — see below)
./gradlew :app:test                   # JVM unit tests (JUnit 4, Robolectric, Turbine)
./gradlew :app:detekt                 # static analysis; CI gates on this
./gradlew :app:lint                   # AGP Lint
./gradlew :app:dependencies --write-locks   # regenerate app/gradle.lockfile (tracked on develop; see SHIPPING.md for the release-tag regen step)
```

## Working conventions

### PR review

**Every PR opened in this repo goes through the multi-agent review cycle.**

- **Issue tracker:** <https://github.com/DocGerd/pantry-tracker/issues>
- **Branch naming:** day-to-day branches are `<type>/<tracker-id>-<slug>`,
  cut off `develop` and PR'd into `develop` (`--base develop`) — e.g.
  `chore/sr-26-claude-md`, `security/v1-final-hardening`. Types in use:
  `chore`, `docs`, `feat`, `fix`, `security`. GitFlow also uses
  `release/<version>` (off `develop`, PR'd into **both** `main` and `develop`,
  then tag `main`) and `hotfix/<slug>` (off `main`, back-merged into
  `develop`). See [`CONTRIBUTING.md`](CONTRIBUTING.md#workflow-gitflow).

Workflow:

1. Open the PR with a linked issue in the body — no PR ships without one. If
   none exists, `gh issue create` first. Use `Closes #N` (or `Fixes #N` /
   `Resolves #N`) when the PR fully resolves the issue — these auto-close it
   on merge. Use `Refs #N` only when the PR is a partial step and the issue
   should stay open. Picking `Refs` for a PR that *does* fully resolve the
   issue leaves the issue open after merge and requires a manual close —
   bitten on v1.1's #47/#49/#50.
2. Run the multi-agent review. The canonical entry point is **user-triggered
   `/ultrareview`** (cloud, billed — Claude cannot launch it itself). The
   Claude-invokable local equivalent is the `pr-review-toolkit:review-pr`
   skill, or dispatch individual `pr-review-toolkit:*` subagents in parallel.
3. Post each finding as an **inline review thread** on the diff (not a single
   summary comment) so each one can be resolved independently. The `pr-review`
   skill at `.claude/skills/pr-review/` is the canonical end-to-end recipe
   for this whole workflow — it wraps `pr-review-toolkit:review-pr` from
   step 2 and covers posting, fixing, resolving, and hand-off. For this step
   specifically it batches findings into a single pending review via the
   GitHub MCP's `pull_request_review_write`. The older `post-finding` skill
   at `.claude/skills/post-finding/` is a `gh api`-based fallback for sessions
   without the GitHub MCP installed.
4. Fix all findings on the same branch.
5. After each fix lands, **resolve the corresponding inline thread** via
   `pull_request_review_write method=resolve_thread` (the MCP handles the
   resolve call; thread IDs are still discovered via GraphQL — see the
   `pr-review` skill §3d). Fallback when the GitHub MCP isn't available:
   the GraphQL `resolveReviewThread` mutation called from Python `subprocess`
   (`gh api graphql` mangles `!` even inside quoted heredocs).
6. Only then declare ready-to-merge.

### Subagent & worktree hygiene

When dispatching subagents into git worktrees for parallel work:

- **Pass the absolute worktree path** in the agent prompt, not a relative one.
  A subagent's cwd is the worktree root, but relative paths in the prompt are
  resolved against the parent's cwd and have caused re-dispatch in past
  Sprint-style sessions.
- **The agent verifies `pwd` and `git branch --show-current`** match the
  expected worktree before touching files. Cheap insurance against the agent
  landing in the wrong tree.
- **Stage by name, never `git add -A`** inside a subagent. A subagent commit
  that swept up unrelated working-tree changes has happened before and needs
  a soft-reset to recover; explicit `git add <file> <file>` makes the commit
  scope auditable from the prompt alone.

These extend (don't duplicate) the global `~/.claude/CLAUDE.md` rules on
`git -C` vs bare `git` inside worktrees.

### Hooks (repo-specific notes)

The global `~/.claude/CLAUDE.md` already forbids skipping hooks and
force-pushing — those rules are not restated here. Repo-specific:

- The pre-commit / pre-push hooks gate on **detekt + secret scanning**; a
  hook failure means real signal, fix the underlying issue.
- `.claude/hooks/block-dangerous-bash.sh` **pattern-matches the full
  command-line text**, not just argv. Consequence: a forbidden flag name
  appearing inside a `git commit -m "..."` body (even as documentation) is
  also blocked. If you need to mention one of the forbidden flags in commit
  copy, rephrase ("skip hooks", "force-push") instead of using the literal
  flag.

### detekt config: list keys REPLACE, not merge

[`detekt-config.yml`](detekt-config.yml) overrides specific rules on top of
the bundled defaults. When you override a **list-valued** field (`excludes`,
`ignoreNumbers`, `comments`, `ignoreAnnotated`, ...), Detekt **replaces** the
default list instead of merging into it. Concrete consequence: if you add one
new excluded path to `MagicNumber.excludes`, you must also re-list every
default exclusion (`**/test/**`, `**/androidTest/**`, `**/*.kts`, ...) or the
rule silently starts firing on test paths it was never meant to police.

The existing `MagicNumber.excludes` block already mirrors the default list —
follow that pattern when adding new list overrides.

### Release signing config

Release builds read four Gradle properties from `~/.gradle/gradle.properties`
(or CI env vars). The keystore lives outside the repo:

```
PANTRY_TRACKER_RELEASE_STORE_FILE       # absolute path; no ~ expansion
PANTRY_TRACKER_RELEASE_STORE_PASSWORD
PANTRY_TRACKER_RELEASE_KEY_ALIAS
PANTRY_TRACKER_RELEASE_KEY_PASSWORD
```

Three valid configurations, enforced by [`app/build.gradle.kts`](app/build.gradle.kts):

| State | `assembleRelease` produces |
|---|---|
| All four set | `app-release.apk` — signed, installable |
| None set | `app-release-unsigned.apk` — *not* installable; build-size only |
| Some-but-not-all | `GradleException` at configuration time, naming the missing props |

`STORE_FILE` must be an absolute path — Gradle's `file()` does not expand `~`,
and a typo'd path fails at configuration time (not during
`validateSigningRelease`).

Full one-time setup: [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md) §B.

### Release process

See [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md). The v1 path is
section B (sideload of release APK). Two non-obvious bits:

- **Dependency lockfile**: `app/gradle.lockfile` is **tracked on `develop`**
  per Gradle's "lockfiles should be checked in to source control" guidance;
  Dependabot keeps it current alongside `gradle/libs.versions.toml`. At a
  release tag, `--write-locks` runs once more and commits the diff (if any)
  in a dedicated `chore(release): lock dependencies` commit for post-hoc
  CVE forensics. See [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md)
  §"Release-tag dependency-lock procedure" and the "Things that have bitten"
  lesson on lockfile tracking below for the full reasoning.
- **Gradle wrapper upgrades**: `./gradlew wrapper --gradle-version X.Y.Z`
  alone drops `distributionSha256Sum`. Use the atomic two-flag form documented
  in SHIPPING.md "Note: `distributionSha256Sum` must move atomically …".
  `.github/workflows/ci.yml` asserts the pin on every PR.

## Layout

```
detekt-rules/                   # pure-JVM module: custom detekt rules (ErrorTone)
  src/main/kotlin/.../detekt/   # ErrorToneRule + PantryRuleSetProvider
  src/test/kotlin/.../detekt/   # ErrorToneRuleTest — proof the rule fires
app/                            # the Android :app module
  src/main/java/de/docgerdsoft/pantrytracker/
    data/local/                 # Room entities, DAOs, AppDatabase
    data/remote/                # Ktor client for Open Food Facts (OffApiClient)
    di/                         # AppContainer — manual constructor wiring (no Hilt in v1)
    repository/                 # ProductRepository — wraps DAO + OFF fallback
    ui/                         # Compose screens (home, scan, detail) + theme
    util/                       # small cross-cutting helpers
    MainActivity.kt, PantryTrackerApp.kt, PantryTrackerNavGraph.kt
docs/
  adr/                          # Architecture Decision Records (backfill track)
  architecture/                 # arc42 — load-bearing for design-decision context
  release/SHIPPING.md           # release procedure + gotchas
  security/                     # dated security review notes (e.g. 2026-05-17)
  superpowers/specs/            # design specs incl. v1 kitchen-inventory-design.md
  superpowers/plans/            # dated implementation plans
  uat/                          # UAT checklist used for v1 sign-off
scripts/uat/                    # Claude-runnable emulator UAT automation (+ README)
CHANGELOG.md                    # release notes, terse-by-policy
SECURITY.md                     # disclosure policy — see below
```

## Security

Reporting policy lives in [`SECURITY.md`](SECURITY.md). For findings raised
during development, the backlog and accept-risk decisions live in tracked
GitHub issues (search label `security`). Dated security-review documents land
under `docs/security/`.

## Things that have bitten past sessions

*Eviction criterion: remove an entry when one of these is true — the
underlying library version that caused it is no longer in
`app/gradle.lockfile`; the convention has been encoded as a detekt / lint
rule; the CI workflow, pre-commit hook, or build script that surfaced it
now catches the problem; or the workflow doc the entry mirrors has been
restructured to make the lesson load-bearing on its own.*

- **`runCatching` swallows `CancellationException`**. In `suspend` code use a
  plain `try/catch` and rethrow `CancellationException` explicitly — otherwise
  a cancelled `viewModelScope` job can still race a state write into a
  successful-looking flow.
- **Compose `androidTest` asserts**: bare `assert()` is a no-op on ART, so a
  failing assertion silently passes. Use `org.junit.Assert.*`
  (`assertEquals`, `assertTrue`, ...) or the Compose test-rule assertions
  (`assertExists()`, `assertIsDisplayed()`). `assertDoesNotExist()` is a
  member function on `SemanticsNodeInteraction`, not an extension — no
  import needed.
- **Control characters in Kotlin test source**: write them as `\uXXXX` escapes
  (six visible source characters), not literal control bytes. Tooling that
  reads the file otherwise faithfully writes whatever byte landed there.
- **Real-device UAT is non-negotiable for HTTP-client changes.** JVM tests
  with `MockEngine` cannot reproduce CDN-specific behaviour like chunked
  transfer encoding (OFF chunks everything and omits `Content-Length`). A
  header-keyed policy that all unit tests endorse can still brick every
  scan on a real device. Anything that touches request/response headers,
  body validation, or response classification needs a real-device smoke
  test before merging — see `docs/uat/v1-uat-checklist.md`.
- **Release-procedure gotchas** (`GRADLE_USER_HOME` redirect masking signing
  props; `gh release create` returning a spurious `422 ReleaseAsset.name
  already exists` after a successful upload) are documented in
  [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md) "Common gotchas" —
  consult that table before debugging a release-build or release-publish
  failure.
- **User-facing error messages must start with `"Couldn't <verb>: ..."`.**
  Every snackbar, ErrorSheet, or Toast that surfaces a repository or system
  failure must use this prefix — NOT "Could not ...", "Error: ...", or a raw
  `java.lang.Exception` string. The `<reason>` is typically
  `e.message ?: "unknown error"` — never a stack trace. Violation example
  found and fixed: `HomeScreen.kt` once used `"Could not delete ..."`.

  This is partly enforced statically by `ErrorToneRule` (SR-78), living in the
  standalone **`:detekt-rules`** module and wired into `:app:detekt` via
  `detektPlugins(project(":detekt-rules"))`. The rule is **PSI-only** (no type
  resolution) and deliberately conservative. What it catches:
  - `ScanUiState.Phase.Error("...")` (an error-ONLY sink) — any literal not
    starting with `"Couldn't "` fails the build.
  - `showSnackbar(message = "...")` / `Toast.makeText(..., "...", ...)` (SHARED
    sinks that also carry success copy like `"Deleted X"`) — flagged ONLY when
    the literal opens with a recognised wrong-tone phrase ("Could not", "Error",
    "Failed", "Unable to", "Cannot", "Can't", "Couldnt"). This is what keeps the
    success-snackbar from being a false positive.

  Known **blind spots** (NOT caught — still a human responsibility): a message
  passed as a `val` reference rather than an inline literal (e.g. `DetailViewModel`
  builds `error = "Couldn't $op: …"` and `DetailScreen` passes that `String` to
  `showSnackbar` — one hop away); string concatenation; `getString(R.string.…)`
  resource lookups; interpolation-first templates (`"${e.message}"`); and a
  wrong-tone error in a shared sink with an unrecognised opener.

  Two non-obvious detekt gotchas that bit while building this rule: (1) **detekt
  SILENTLY swallows per-file rule-execution exceptions by default** — a rule that
  throws produces BUILD SUCCESSFUL with zero findings, looking exactly like a
  passing run. Surface them with `debug = true` on the `Detekt` task (it then
  fails loud) or rely on the proof test below. (2) **A stale Gradle daemon can
  pin an old `detektPlugins(project(...))` jar** mid-iteration — after editing the
  rule, `./gradlew --stop` before re-running `:app:detekt` or you may be testing
  the previous rule build. The regression guard is `:detekt-rules:test`
  (`ErrorToneRuleTest`), which lints sample snippets directly and asserts exact
  finding counts (fire on violations, silent on conforming + success copy) — run
  it whenever you touch the rule. Eviction criterion: when `ErrorToneRuleTest`
  is deleted or the `pantry.ErrorTone` entry leaves `detekt-config.yml`.
- **Before-PR checklist for UAT scripts & instrumented tests lives in
  [`scripts/uat/README.md`](scripts/uat/README.md) §"Before-PR end-to-end
  checklist".** Always-on reminder of what's there: any bash script with
  PATH-resolved binaries, `gh`-CLI calls, or logcat regex scanning needs a
  fresh-shell (non-interactive) run before merge — static gates +
  "BUILD SUCCESSFUL" miss PATH hangs, the `mktemp`/`--clobber` download race,
  and logcat false-positives. For filtering one instrumented test:
  `connectedDebugAndroidTest` rejects `--tests` (use
  `-Pandroid.testInstrumentationRunnerArguments.class=<FQN>`), and you must
  `adb uninstall` **both** `de.docgerdsoft.pantrytracker` and `.test` first or
  a stale release-signed install yields a silent `0 tests` run. Consult that
  section before adding a script or filtering an instrumented test.
- **Revoking a HELD runtime permission kills the shared `androidTest` process.**
  Signature: the in-flight test reports as FAILED, then the run aborts with no
  logcat for the crashed pid (`ActivityManager: Killing <pid>: permissions
  revoked`). Mitigation: never call `revokeRuntimePermission` on a held
  permission in a shared instrumentation process — use a test seam (see
  `CameraPermissionGate.isCameraGranted` for the pattern); for tests that pin
  state another way, just delete the revoke. Source: PR #118 / issue #117.
- **Custom `AndroidJUnitRunner.newApplication()` is the reliable Application
  swap for `androidTest`.** The androidTest manifest's `android:name` does
  NOT win because the runtime Application class is resolved from the
  *target* (app-under-test) manifest; the runner's `newApplication()`
  override is what actually swaps it. See `PantryTestRunner.kt:42` for the
  canonical pattern. Note: the swap is **global** — every instrumented test
  runs against the test Application, so the test Application must call
  `super.onCreate()` and stay transparent unless a test opts into an
  override. (It is not itself a root cause of "Process crashed" — that's a
  separate gotcha covered by the entry above; see issue #117 root-cause
  analysis.)
- **Detekt custom rules belong in a standalone Gradle module + proof test.**
  Compiling a detekt rule into the app test source set is silently inert; the
  rule never fires. The `ErrorToneRule` extraction in SR-78 (`:detekt-rules`
  module + a proof test) is the working pattern.
- **Keep `app/gradle.lockfile` tracked on `develop`.** Per
  [Gradle's official docs](https://docs.gradle.org/current/userguide/dependency_locking.html):
  "Lockfiles should be checked in to source control." Dependabot updates
  `gradle.lockfile` alongside `gradle/libs.versions.toml` since GA in June
  2025; the historical Version-Catalog interaction bug
  ([dependabot-core #12557](https://github.com/dependabot/dependabot-core/issues/12557))
  was fixed in [dependabot-core PR #12853](https://github.com/dependabot/dependabot-core/pull/12853),
  merged 2026-02-03. The earlier untrack-on-develop pattern (PR #112)
  was a workaround for that now-fixed bug; do **not** reintroduce
  `git rm --cached app/gradle.lockfile` as a release-procedure step.
  If a future dependabot PR ever updates only `libs.versions.toml`
  without the lockfile, regenerate locally
  (`./gradlew :app:dependencies --write-locks`) and amend the PR —
  do not revert to untracking. The release-prep procedure still
  re-runs `--write-locks` and commits any diff before the tag, but
  the post-tag untrack is gone.
- **On-device CI or a local emulator catches instrumented-test bugs that
  static analysis and multi-agent PR review cannot.** PR #118's
  `ActivityManager: Killing <pid>: permissions revoked` logcat was invisible
  to every review surface but instant on a local emulator boot. Generalises
  the **Real-device UAT is non-negotiable for HTTP-client changes** entry
  above to instrumented-test debugging — for any instrumented-test
  regression that doesn't reproduce in JVM unit tests, run
  `:app:connectedDebugAndroidTest` on a local emulator (or push and let the
  CI emulator job at `.github/workflows/ci.yml` run
  `reactivecircus/android-emulator-runner` before declaring ready).
- **GitFlow ruleset constraints for this repo.** Keep `develop` as the
  default branch — feature PRs target develop and rely on the
  default-branch behaviour of `Closes #N` to auto-close issues on merge
  (GitHub only honours closing-keywords on merges to the default branch).
  Branch protection is split across two Repository Rulesets since
  2026-05-28 (#158): **16948699 "Protect main"** (strict up-to-date
  *required*) and **16993554 "Protect develop"** (strict up-to-date
  *not* required, to avoid integration-branch rebase churn). Keep
  `required_linear_history` *off* on **both**: release-prep merges from
  `release/<version>` into `main` are non-fast-forward by construction
  and would be blocked otherwise.
- **CodeQL Kotlin extraction needs three load-bearing build flags.**
  [`.github/workflows/codeql.yml`](.github/workflows/codeql.yml) runs the
  Gradle build under a CodeQL `LD_PRELOAD` tracer; for the tracer to
  actually see Kotlin compilation on this 100% Kotlin Android project,
  ALL THREE must hold simultaneously: (a) **Kotlin compiler in-process**
  (`-Pkotlin.compiler.execution.strategy=in-process`) — the default
  out-of-process Kotlin daemon JVM is outside the tracer scope;
  (b) **build cache disabled** (`clean` + `--no-build-cache`) — a
  `compileDebugKotlin FROM-CACHE` from a prior ci.yml run means no
  compiler process runs for the tracer to capture; (c) **trace mode, NOT
  `build-mode: none`** — for the `java-kotlin` combined language `none`
  is a Java-only dependency-graph scanner that yields an empty source
  archive on a Kotlin-only codebase (this repo has zero `.java` files).
  Each failure mode produces the identical "no source code seen during
  build" finalize error, so a passing Android Gradle build inside the
  tracer is NOT sufficient evidence the DB is non-empty — watch the
  actual `Analyze (java-kotlin)` check conclusion. Diagnosed across
  PR #131 iterations 1-3; commit `a33a449` is the working config and
  codeql.yml's build-step comment block documents the full chain.
  Eviction criterion: codeql.yml is removed, or CodeQL ships native
  source-only Kotlin extraction for `java-kotlin`.
- **Scorecard check scoring is not always proportional.** Two checks
  surprised us on 2026-05-28: **Branch-Protection** uses *tiered*
  scoring (each tier must be fully satisfied to count any next-tier
  work — solo maintainer can't reach Tier 2 because "require ≥1
  reviewer" is structurally infeasible, so the score is capped at
  Tier 1 = 3/10 regardless of how many Tier 5 toggles get flipped).
  **Fuzzing** only recognizes a narrow whitelist (OSS-Fuzz,
  ClusterFuzzLite, Go-native fuzz, Haskell QuickCheck/Hedgehog/
  validity/SmallCheck, JS/TS fast-check, Erlang proper/quickcheck)
  — **Jazzer is not on the list**, so PR #157 added real fuzz
  coverage without moving the Scorecard score. When sizing Scorecard
  tickets, read the per-check docs at
  https://github.com/ossf/scorecard/blob/main/docs/checks.md before
  predicting score movements. Bitten on #139 (predicted 3→6,
  got 3→3) and #144 (predicted 0→10, got 0→0). Eviction criterion:
  when Scorecard publishes per-check eligibility hints in the
  workflow output (would make pre-prediction unnecessary).
- **`.kts` + `java.time.Duration` inside a Gradle DSL block needs
  an explicit import.** Fully-qualified `java.time.Duration.ofMinutes(N)`
  may fail with `Unresolved reference 'time'` inside task-config
  blocks — the Gradle `java { }` extension shadows the `java`
  package root in DSL context. Fix: `import java.time.Duration` at
  file top, then use bare `Duration.ofMinutes(N)` in the body. Same
  shadow likely applies to other `java.*` package-root references
  Gradle reuses as extensions — when in doubt, import explicitly.
  Bitten on PR #157's `:app:fuzzTest` task. Eviction criterion: when
  Kotlin DSL resolution prefers package roots over Gradle extensions,
  or when `build.gradle.kts` is replaced by an alternative build
  system.
- **Robolectric tests add 0% to the on-the-fly JaCoCo report.** The
  Gradle `jacoco` plugin instruments classes at load time via its
  on-the-fly agent, but Robolectric's sandbox classloader reloads app
  classes from the original (un-instrumented) bytes, so the coverage
  probes never fire. Symptom: a class heavily exercised *only* by a
  `@RunWith(RobolectricTestRunner)` test (or the RNG screenshot tests)
  shows 0% in `jacocoTestReport`. Verified empirically in #182 — a
  passing Robolectric test calling `Converters` left the whole report
  at 0 covered / 15360 missed. Consequence: only **plain-JVM**
  (non-Robolectric) tests move the JVM-only number; the Compose UI
  screens (~69% of all instructions) and anything needing
  Robolectric/`Context` are unreachable for JVM-only coverage under the
  current build config. The combined 86.85% that clears the OpenSSF
  Silver `test_statement_coverage80` MUST comes from the **emulator
  `androidTest` `.ec`**, not Robolectric JVM tests. Do NOT run
  `:app:jacocoTestCoverageVerification` off-emulator — its 0.80 gate
  fails on the JVM-only ~19–25% BY DESIGN. To credit Robolectric/
  Compose-UI lines on the JVM you'd need JaCoCo **offline
  instrumentation** in `app/build.gradle.kts` — a maintainer build
  decision, deliberately not actioned in #182. Eviction criterion:
  `app/build.gradle.kts` switches to JaCoCo offline instrumentation, or
  the on-the-fly `jacoco` plugin is replaced.
