# Pantry Tracker — Claude operating notes

Standalone Android Kotlin/Compose app for whole-number kitchen inventory.
Two Gradle modules: `:app` (the Android app) and `:detekt-rules` (a pure-JVM
module holding the custom detekt rule set — see the ErrorTone note below).
Latest release: see CHANGELOG.md / GitHub Releases. All releases ship as signed sideload
APKs on GitHub Releases — note v* releases are immutable (asset must be
attached at creation; see SHIPPING.md). CHANGELOG.md holds per-version history.

This file is loaded into every Claude Code session in this repo. Keep it
high-signal — pointers, not duplication of the source-of-truth docs.

## Hard governance rule: only humans merge to develop or main

The repo follows **GitFlow**: `develop` is the integration branch + default,
`main` is release-tagged/production. **Every change reaching `develop` OR
`main` MUST go through a PR that the human merges** (one carve-out, below).
Claude may open PRs, push to feature branches, dispatch review subagents,
post + resolve inline threads, create tags, build APKs, and create GitHub
Releases — but MUST NOT invoke `gh pr merge` or `git push origin
develop`/`git push origin main` (or any equivalent that lands code on either
protected branch). No exceptions for
one-line reverts, "UAT-verified" hotfixes, wrap-up phases, or ambiguous "do
the rest" / "continue" instructions. When in doubt, ASK before merging. The
audit-trail gate is the human's explicit click on "Merge pull request".

**Standing carve-out (2026-08-24, #297):** Claude MAY merge a **Dependabot-
authored PR based on `develop` with every check green**, without asking —
after the normal review verdict is posted. Nothing else: not `main` in any
form, not a human-authored PR, not a pending or failing check.
**Never from the shell.** `.claude/hooks/block-dangerous-bash.sh` refuses every
shell merge unconditionally. The carve-out runs through the GitHub MCP
**`merge_pull_request`** tool, whose owner/repo/number are typed parameters
rather than parsed text — the only path with no command-parsing problem to get
wrong. A hook gate was tried and abandoned; see #297 for why three rounds of
adversarial review could not close it.

The conditions are therefore **verified by Claude, not by machinery**. Check all
five against `gh pr view <N> --json author,baseRefName,state,isDraft,headRefName,mergeStateStatus`
before calling the tool, and abort on any miss:

| | Required |
|---|---|
| `author.login` | `app/dependabot` (GraphQL) or `dependabot[bot]` (REST) |
| `baseRefName` | `develop` — **never** `main` |
| `state` / `isDraft` | `OPEN` and not a draft |
| `headRefName` | starts `dependabot/` |
| `mergeStateStatus` | `CLEAN` — **not** `gh pr checks` rc=0, which only means "nothing that has *posted* is failing" |

Post the review verdict on the PR first; the merge is the last step. Regression
suite for the hook: `bash .claude/hooks/test-block-dangerous-bash.sh`.

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
   the GraphQL `resolveReviewThread` mutation via `gh api graphql -f query='...'`
   (single-quoted, `!` passes through unchanged; see the `post-finding` skill).
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
  flag. To *author* text containing a blocked literal (test fixtures, docs),
  use the **Write/Edit tools** — the hook intercepts Bash only. Its matcher is
  deliberately **wide**, and matches a *union* of normalizations (quotes,
  backslashes, `$x`, `${x-word}`, `$'\x6d'`, brace lists, line continuations)
  because a single lossy rewrite can *destroy* a match — that bug let
  `merge$x` through once. Consequence, hit repeatedly: a `git commit -m "…"`
  whose message merely *mentions* the merge command is blocked. Write the
  message to a file with the **Write tool** and use `git commit -F <file>` —
  that command text is clean.

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
  failure uses this prefix, never "Could not ...", "Error: ..." or a raw
  exception string; the `<reason>` is `e.message ?: "unknown error"`, never a
  stack trace. `ErrorToneRule` (`:detekt-rules`, wired into `:app:detekt`, PSI-only
  and deliberately conservative) enforces it: any literal passed to
  `ScanUiState.Phase.Error(...)` must start with `"Couldn't "`; literals in the
  shared sinks `showSnackbar(message = ...)` / `Toast.makeText(...)` fail only
  when they open with a recognised wrong-tone phrase, so success copy stays
  clean. Not caught, still a human check: messages passed as a `val` reference,
  concatenation, `getString(R.string.…)`, and interpolation-first templates.
  Two detekt gotchas: a rule that throws is swallowed silently (BUILD
  SUCCESSFUL, zero findings; set `debug = true` on the `Detekt` task to see it),
  and a stale Gradle daemon can pin an old `detektPlugins(project(...))` jar
  (`./gradlew --stop` after editing the rule). Run `:detekt-rules:test`
  (`ErrorToneRuleTest`) whenever the rule changes. Eviction criterion: when
  `ErrorToneRuleTest` is deleted or the `pantry.ErrorTone` entry leaves
  `detekt-config.yml`.
- **Before-PR checklist for UAT scripts & instrumented tests lives in
  [`scripts/uat/README.md`](scripts/uat/README.md) §"Before-PR end-to-end
  checklist".** Consult it when adding/changing a UAT script or filtering one
  instrumented test: static gates + "BUILD SUCCESSFUL" do NOT catch the
  fresh-shell PATH/`gh`/logcat footguns or the `connectedDebugAndroidTest`
  flag + stale-install footguns — a non-interactive fresh-shell run is
  required before merge. (Full detail + copy-paste commands live in that
  README section; kept there, not here, to stay out of every-session context.)
  Eviction criterion: when that README section is deleted or the `scripts/uat/`
  automation track is retired.
- **Revoking a HELD runtime permission kills the shared `androidTest` process.**
  The in-flight test reports FAILED, then the run aborts with no logcat for the
  crashed pid (`ActivityManager: Killing <pid>: permissions revoked`). Never call
  `revokeRuntimePermission` on a held permission in a shared instrumentation
  process; use a test seam (pattern: `CameraPermissionGate.isCameraGranted`), or
  delete the revoke.
- **Custom `AndroidJUnitRunner.newApplication()` is the reliable Application
  swap for `androidTest`.** The androidTest manifest's `android:name` does NOT
  win, because the runtime Application class is resolved from the target
  (app-under-test) manifest. The swap is global: every instrumented test runs
  against the test Application, so it must call `super.onCreate()` and stay
  transparent unless a test opts into an override. Pattern:
  `PantryTestRunner.newApplication()`.
- **Detekt custom rules belong in a standalone Gradle module + proof test.**
  A rule compiled into the app test source set is silently inert and never
  fires. Working pattern: `ErrorToneRule` in `:detekt-rules` plus its proof test.
- **Keep `app/gradle.lockfile` tracked on `develop`.** Gradle's guidance is to
  check lockfiles in
  ([docs](https://docs.gradle.org/current/userguide/dependency_locking.html)), and
  Dependabot now updates the lockfile alongside `gradle/libs.versions.toml`. Do
  **not** reintroduce `git rm --cached app/gradle.lockfile` as a release step.
  If a Dependabot PR updates only `libs.versions.toml`, regenerate locally
  (`./gradlew :app:dependencies --write-locks`) and amend the PR; do not revert to
  untracking. Release prep re-runs `--write-locks` and commits any diff before
  the tag.
- **On-device CI or a local emulator catches instrumented-test bugs that static
  analysis and multi-agent PR review cannot** (e.g. a process kill that leaves
  no trace on any review surface). For an instrumented-test regression that does
  not reproduce in JVM unit tests, run `:app:connectedDebugAndroidTest` on a
  local emulator, or let the CI emulator job in `.github/workflows/ci.yml` run
  before declaring ready. Extends the real-device UAT entry above.
- **GitFlow ruleset constraints for this repo.** Keep `develop` the default
  branch: feature PRs target it and rely on `Closes #N` auto-closing issues, which
  GitHub honours only on merges to the default branch. Branch protection is two
  Repository Rulesets, "Protect main" (strict up-to-date required) and "Protect
  develop" (not required, to avoid integration-branch rebase churn). Keep
  `required_linear_history` off on both: `release/<version>` into `main` is
  non-fast-forward by construction and would be blocked.
- **CodeQL Kotlin extraction needs three load-bearing build flags**
  ([`.github/workflows/codeql.yml`](.github/workflows/codeql.yml)): (a) Kotlin
  compiler in-process (`-Pkotlin.compiler.execution.strategy=in-process`), since
  the out-of-process daemon is outside the `LD_PRELOAD` tracer; (b) build cache
  off (`clean` + `--no-build-cache`), since a `FROM-CACHE` compile runs no
  compiler for the tracer to see; (c) trace mode, not `build-mode: none`, which
  yields an empty source archive on a Kotlin-only codebase. Each failure gives the
  same "no source code seen during build" error, and a passing Gradle build is not
  evidence the DB is non-empty: read the `Analyze (java-kotlin)` check conclusion.
  The workflow's build-step comment block has the full chain. Eviction criterion:
  codeql.yml is removed, or CodeQL ships native source-only Kotlin extraction for
  `java-kotlin`.
- **Scorecard check scoring is not always proportional.** Branch-Protection is
  tiered: each tier must be fully satisfied before the next counts, and "require
  >=1 reviewer" is infeasible for a solo maintainer, so the score is capped at the
  lowest tier however many higher toggles are on. Fuzzing recognises only a narrow
  whitelist of frameworks and Jazzer is not on it, so real fuzz coverage does not
  move the score. Read the
  [per-check docs](https://github.com/ossf/scorecard/blob/main/docs/checks.md)
  before predicting a score change when sizing a ticket. Eviction criterion: when
  Scorecard publishes per-check eligibility hints in the workflow output.
- **`.kts` + `java.time.Duration` inside a Gradle DSL block needs an explicit
  import.** Fully-qualified `java.time.Duration.ofMinutes(N)` can fail with
  `Unresolved reference 'time'` because the Gradle `java { }` extension shadows
  the `java` package root in DSL context. `import java.time.Duration` at file top
  and use bare `Duration`; import explicitly for other `java.*` roots too.
  Eviction criterion: Kotlin DSL resolution prefers package roots over Gradle
  extensions, or `build.gradle.kts` is replaced.
- **Robolectric tests add 0% to the on-the-fly JaCoCo report.** The `jacoco`
  plugin instruments at class-load time, but Robolectric's sandbox classloader
  reloads app classes from the un-instrumented bytes, so the probes never fire.
  Only plain-JVM tests move the JVM-only number; Compose UI screens and anything
  needing Robolectric/`Context` are covered only by the emulator `androidTest`
  `.ec`, which is what clears the OpenSSF Silver `test_statement_coverage80` MUST.
  Do NOT run `:app:jacocoTestCoverageVerification` off-emulator: its 0.80 gate
  fails on the JVM-only number by design. Crediting Robolectric lines would need
  JaCoCo offline instrumentation in `app/build.gradle.kts`, a maintainer decision.
  Eviction criterion: `app/build.gradle.kts` switches to offline instrumentation,
  or the on-the-fly `jacoco` plugin is replaced.
- **Regenerating Robolectric screenshot goldens runs through CI, and CI only
  fires on PRs.** The goldens (`app/src/test/snapshots/*.png`) are
  `@GraphicsMode(NATIVE)`/Skia byte-exact and host-non-portable: a local (WSL)
  render differs from `ubuntu-latest` even for unchanged content, so NEVER commit
  dev-box-rendered goldens. `ScreenshotTestBase.compareOrWrite` has no record
  flag: deleting a stale golden makes the test write a fresh one and `fail()`
  once. Procedure: delete the affected goldens, open/push the PR (a
  feature-branch push runs nothing, since `ci.yml` triggers on `pull_request` and
  pushes to main/develop only), let the `build` job fail, `gh run download <id> -n
  screenshot-goldens-actual` (the `if: failure()` upload step in `ci.yml`),
  review each PNG visually, commit, push. Do not `@Ignore` goldens instead of
  regenerating. Scope the blast radius both ways before the multi-round regen:
  every themed screen renders through `PantryTrackerTheme`, so a re-mapped role
  can stale all themed goldens, while a change to roles no golden renders stales
  none. `grep` the screenshot-test sources (`app/src/test/.../screenshot/`) for the
  changed `colorScheme.<role>` names plus `Surface(`/`Card(`/`tonalElevation`
  first; no match means no golden stales. The `icon_*` goldens stale only on an
  icon-asset change (`AppIconScreenshotTest` paints on a fixed background).
  Eviction criterion: the `upload-artifact` golden-emit step leaves `ci.yml`, or
  the screenshot suite is removed.
- **Reading string resources inside a Compose coroutine trips the
  `LocalContextGetResourceValueCall` lint check** (error severity on the repo's
  Compose BOM), e.g. `context.getString(R.string.x, arg)` inside a
  `LaunchedEffect { flow.collect { … } }`. `stringResource()` is `@Composable`-only
  and cannot be called from the collect block, so resolve the format templates
  with `stringResource()` in composition above the `LaunchedEffect` and apply
  `String.format(Locale.getDefault(), template, arg)` per event inside the
  coroutine. Use the explicit-`Locale` form; the bare Kotlin `.format()` trips
  detekt's `ImplicitDefaultLocale`. The lint only tracks values originating from
  `LocalContext.current`, so passing a `Context` function parameter (as
  `RelativeTime.format(context, …)` does) is the other valid escape. Eviction
  criterion: the lint check is downgraded/removed, or the Compose BOM stops
  flagging it at error severity.
- **A stale local Gradle cache can fail at an *earlier* task than CI, masking the
  real failure.** A corrupted incremental/KSP cache artifact
  (`java.io.EOFException` at `:app:kspDebugUnitTestKotlin`) can precede, and hide,
  the actual `:app:lintDebug` error CI reports. When a local CI-reproduction hits
  an `EOFException` or odd cache error at a task CI passed, `./gradlew clean`
  before trusting the failure point. Eviction criterion: incremental KSP stops
  emitting cache EOFExceptions, or the repo pins `--no-build-cache` for local
  repro.
- **`codecov/project` never posts on this repo: the coverage gate is the emulator
  `androidTest` job, not Codecov.** Codecov computes the comparison and posts
  `codecov/patch` but drops `codecov/project` from the status and check-runs APIs;
  this is an account/installation-level fault, not plan-gated (the check is free
  for public repos), so do not re-investigate the plan hypothesis. The required
  coverage check on the develop ruleset is the `androidTest` job
  (`:app:jacocoTestCoverageVerification` at the 0.80 LINE gate on the merged unit +
  instrumented report); the main ruleset stays build-only. General rule: never
  promote a check to required until you have observed it post green on a real PR,
  because a required check that never posts deadlocks every merge. CI has no path
  filters, so any PR, even docs-only, is a valid observation vehicle. If
  `codecov/project` is wanted again, the only lever is a Codecov support ticket,
  and set `codecov.yml` `project.target` to `auto` first (its partial-counting
  metric sits below the JaCoCo LINE number and would post red). Eviction
  criterion: `codecov.yml` is removed, or `codecov/project` starts posting and is
  promoted to required.
- **A Mermaid `sequenceDiagram` participant id must not be a reserved keyword,
  and `off`/`on` are reserved (case-insensitively).** The project shorthand "OFF"
  (Open Food Facts) as `participant OFF` fails the entire diagram on GitHub's
  renderer (`Parse error … Expecting 'ACTOR', got 'off'`). Rename the id and alias
  the display: `participant OFFApi as Open Food Facts`. Message-text punctuation
  after the `:` is safe; the trap is keyword collisions in participant positions.
  `stateDiagram-v2` transition labels are stricter: avoid unquoted `()` and
  literal `"`. Run the `verify-mermaid` skill (vendored `mermaid.min.js` + headless
  Chrome, no Node, no network) before pushing a diagram: it catches the reserved
  participant ids deterministically and renders a PNG per changed doc. Do not
  trust a subagent's grammar-reasoning verdict that a diagram is render-safe, and
  do not reach for kroki/mermaid.ink (blocked as exfil destinations); the rendered
  GitHub PR view stays the final check. Eviction criterion: a Mermaid
  linter/renderer runs in CI, or the repo stops embedding Mermaid.
- **`manifest_path` on a Dependabot alert from Automatic Dependency Submission is
  an information-free label, not a classpath hint.** Every Maven alert reports
  `settings.gradle.kts`, which declares no dependencies; the label is stamped on
  the whole force-resolved graph. Find the real source with
  `./gradlew :app:dependencies` / `./gradlew buildEnvironment`. Three levers: (1)
  `:app` project configs via `resolutionStrategy.eachDependency` in
  `app/build.gradle.kts`; (2) the root buildscript/plugin classpath via
  `buildscript { configurations.classpath { resolutionStrategy.force(…) } }` in
  `build.gradle.kts`, which is forceable despite what `docs/security-posture.md`
  says; (3) genuinely blocked, so accept-risk (e.g. the KGP alert, where the force
  resolves but leaks a prerelease `kotlin-stdlib` into `debugRuntimeClasspath` and
  breaks dependency locking). A resolution pin expires: new advisories can move
  the vulnerable range up to include the pinned version, so re-derive the current
  first-patched version instead of trusting the alert's `first_patched_version`.
  Dependabot never opens a PR for this alert class (no parsed manifest declares
  the versions), so waiting for one is a permanent no-op. Eviction criterion:
  Automatic Dependency Submission starts reporting real per-classpath manifest
  paths.
- **Never let parsed shell text decide an ALLOW: parse only to deny, and put
  carve-outs at the typed tool layer.** For a Bash call the only description of the
  action is the command string, so deciding whether it is a merge is irreducibly a
  deny-list that means re-implementing bash expansion; every fix to such a matcher
  introduced a defect of its own (a bypass, or an over-block of ordinary commands),
  and it does not converge. `block-dangerous-bash.sh` therefore refuses all shell
  merges, and the Dependabot carve-out runs through the GitHub MCP tool, whose
  parameters are typed. The hook still parses text for the push-to-`main` rule and
  the escape-hatch flags, which is fine because those only deny. Before adding any
  guard: if the policy needs facts not in argv (author, base branch, merge state),
  or the input is attacker-chosen text, check where the values arrive typed.
  Corollary: the matcher is permanently incomplete and a miss is a total bypass
  (the hook ends in `exit 0`), so treat it as a speed bump; the governance rule at
  the top of this file is the binding control. Eviction criterion: the hook stops
  pattern-matching command text.
