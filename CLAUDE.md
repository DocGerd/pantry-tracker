# Pantry Tracker — Claude operating notes

Standalone Android Kotlin/Compose app for whole-number kitchen inventory.
Two Gradle modules: `:app` (the Android app) and `:detekt-rules` (a pure-JVM
module holding the custom detekt rule set — see the ErrorTone note below).
v1.0.0 shipped 2026-05-18; v1.1.0 (Fallbacks & undo) shipped 2026-05-19, both
as signed sideload APKs on GitHub Releases.

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
./gradlew :app:dependencies --write-locks   # regenerate gradle.lockfile (see .gitignore for day-to-day exclusion; SHIPPING.md for the release-tag exception)
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

- **Dependency lockfile at release tags**: `app/gradle.lockfile` is gitignored
  day-to-day but force-committed in a dedicated `chore(release): lock
  dependencies` commit immediately before the tag, so post-hoc CVE forensics
  has an immutable record of what shipped.
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
  architecture/                 # arc42 — load-bearing for design-decision context
  release/SHIPPING.md           # release procedure + gotchas
  security/                     # dated security review notes (e.g. 2026-05-17)
  superpowers/specs/            # design specs incl. v1 kitchen-inventory-design.md
  uat/                          # UAT checklist used for v1 sign-off
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
- **UAT scripts and bash automation: fresh-host end-to-end execution
  is non-negotiable before declaring ready.** `bash -n` + `:app:detekt`
  + the implementer subagent's "BUILD SUCCESSFUL" self-report are
  necessary but not sufficient — the implementer's shell typically
  has `$ANDROID_HOME/emulator/` and `gh` on PATH; a non-interactive
  fresh shell (CI runner, a different agent, a colleague's dev box)
  typically does NOT. SR-81's `scripts/uat/verify-migration-1-2.sh`
  shipped with three bugs that none of those gates caught, surfacing
  only on a re-run in a fresh shell: (1) bare `emulator -avd …` assumed
  `$PATH` contained the SDK's `emulator/` directory — silent hang on
  `adb wait-for-device` when only `platform-tools` was on PATH;
  (2) `mktemp` + `gh release download --output` race — gh refuses to
  overwrite the empty file `mktemp` created, needs `--clobber`;
  (3) `grep -iE 'AndroidRuntime'` matched benign D/I-level Zygote-start
  and VM-exit lines on every clean boot — script would have reported
  `FAIL` on every successful migration. Pattern: any script with
  PATH-resolved binaries, gh-CLI invocations, or logcat regex scanning
  needs an actual run in a non-interactive shell on a fresh host
  before merge. Evict once a "before-PR end-to-end" checklist for new
  scripts lands in `scripts/uat/README.md`.
- **Revoking a HELD runtime permission kills the shared `androidTest` process.**
  Signature: the in-flight test reports as FAILED, then the run aborts with no
  logcat for the crashed pid (`ActivityManager: Killing <pid>: permissions
  revoked`). Mitigation: never call `revokeRuntimePermission` on a held
  permission in a shared instrumentation process — use a test seam (see
  `CameraPermissionGate.isCameraGranted` for the pattern); for tests that pin
  state another way, just delete the revoke. Source: PR #118 / issue #117.
- **Custom `AndroidJUnitRunner.newApplication()` is the reliable Application
  swap for `androidTest`.** Manifest `android:name` does NOT win; the runner's
  `newApplication()` override does. It is **global** — be aware it can
  interact with unrelated tests — but is NOT itself a root cause of "Process
  crashed" by itself (see #117 root-cause analysis).
- **Detekt custom rules belong in a standalone Gradle module + proof test.**
  Compiling a detekt rule into the app test source set is silently inert; the
  rule never fires. The `ErrorToneRule` extraction in SR-78 (`:detekt-rules`
  module + a proof test) is the working pattern.
- **Post-tag `gradle.lockfile` must be `git rm --cached`-d** on `develop` after
  the release tag, or `activateDependencyLocking()` will pin develop to the
  release's lockfile and defeat dependabot. The release tag itself needs the
  lockfile committed for CVE forensics; `develop` does not.
- **On-device CI or a local emulator catches instrumented-test bugs that
  static analysis and multi-agent PR review cannot.** PR #118's
  `ActivityManager: Killing <pid>: permissions revoked` logcat was invisible
  to every review surface but instant on a local emulator boot. Generalises
  the "Real-device UAT non-negotiable" entry to instrumented-test debugging.
- **GitFlow specifics for this repo.** `develop` as default branch is what
  lets `Closes #N` auto-close on feature merges; `required_linear_history`
  would block release-prep merges into `main` (the merge commit is not a
  fast-forward) — keep linear-history *off* in the ruleset.
