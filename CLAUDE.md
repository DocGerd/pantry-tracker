# Pantry Tracker — Claude operating notes

Standalone Android Kotlin/Compose app for whole-number kitchen inventory.
Single `:app` module. v1.0.0 shipped 2026-05-18; v1.1.0 (Fallbacks & undo)
shipped 2026-05-19, both as signed sideload APKs on GitHub Releases.

This file is loaded into every Claude Code session in this repo. Keep it
high-signal — pointers, not duplication of the source-of-truth docs.

## Hard governance rule: only humans merge to main

**Every change reaching `main` MUST go through a PR that the human merges.**
Claude may open PRs, push to feature branches, dispatch review subagents,
post + resolve inline threads, create tags, build APKs, and create GitHub
Releases — but MUST NOT invoke `gh pr merge` or `git push origin main` (or
any equivalent that lands code on main). No exceptions for one-line reverts,
"UAT-verified" hotfixes, wrap-up phases, or ambiguous "do the rest" /
"continue" instructions. When in doubt, ASK before merging. The audit-trail
gate is the human's explicit click on "Merge pull request".

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
- **Branch naming:** `<type>/<tracker-id>-<slug>` — e.g.
  `chore/sr-26-claude-md`, `security/v1-final-hardening`. Types in use:
  `chore`, `docs`, `feat`, `fix`, `security`.

Workflow:

1. Open the PR with `Closes #N` or `Refs #N` in the body — no PR ships without
   a linked issue. If none exists, `gh issue create` first.
2. Run the multi-agent review. The canonical entry point is **user-triggered
   `/ultrareview`** (cloud, billed — Claude cannot launch it itself). The
   Claude-invokable local equivalent is the `pr-review-toolkit:review-pr`
   skill, or dispatch individual `pr-review-toolkit:*` subagents in parallel.
3. Post each finding as an **inline review thread** on the diff (not a single
   summary comment) so each one can be resolved independently. The
   `post-finding` skill at `.claude/skills/post-finding/` encodes the
   `gh api -X POST .../pulls/<n>/comments` recipe.
4. Fix all findings on the same branch.
5. After each fix lands, **resolve the corresponding inline thread** via the
   GraphQL `resolveReviewThread` mutation. (`gh api graphql` mangles `!` in
   GraphQL syntax even inside quoted heredocs — call `gh` from Python
   `subprocess` instead.)
6. Only then declare ready-to-merge.

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
app/                            # the single :app module
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

*Eviction criterion: remove an entry when the underlying library version
that caused it is no longer in `app/gradle.lockfile`, or when the
convention has been encoded as a detekt / lint rule.*

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
- **Real-device UAT is non-negotiable for HTTP-client changes.** v1.1's SR-24
  body cap passed all 45 JVM tests but failed step 2 of UAT on a known-OFF
  barcode: OFF's CDN uses chunked transfer encoding and omits
  `Content-Length`, so a "fail-closed on missing CL" policy bricked every
  lookup. The fix (commit 7599bb2) had to revert to "silently pass when CL
  absent". Lesson: anything that touches request/response headers needs a
  real-network smoke test, not just `MockEngine`.
- **`GRADLE_USER_HOME=/tmp/gradle-user-home` masks `~/.gradle/gradle.properties`.**
  The WSL sandbox config redirects Gradle's user home to a tmp dir, so the
  four `PANTRY_TRACKER_RELEASE_*` props in your real `~/.gradle/gradle.properties`
  are invisible to `assembleRelease`. Bridge them with
  `grep '^PANTRY_TRACKER_RELEASE_' ~/.gradle/gradle.properties > /tmp/gradle-user-home/gradle.properties`
  before invoking release builds, or `assembleRelease` produces
  `app-release-UNSIGNED.apk`.
- **`Closes #N` vs `Refs #N` in PR bodies**: only `Closes` (or `Fixes` /
  `Resolves`) auto-closes the issue on merge. `Refs` does not. Bit us when
  merging PR #47 left #44/#45/#46 open despite shipping their features.
- **`gh release create` 422 "ReleaseAsset.name already exists" is a false
  negative.** The CLI returns 422 even when the APK upload actually
  succeeded. Always verify via `gh release view <tag>` before retrying —
  retrying will create a phantom duplicate release that gets rolled back
  but consumes a release ID.
