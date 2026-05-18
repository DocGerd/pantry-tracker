# Pantry Tracker — Claude operating notes

Standalone Android Kotlin/Compose app for whole-number kitchen inventory.
Single `:app` module. v1.0.0 shipped 2026-05-18 as a signed sideload APK.

This file is loaded into every Claude Code session in this repo. Keep it
high-signal — pointers, not duplication of the source-of-truth docs.

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK (auto-signed with debug keystore)
./gradlew :app:assembleRelease        # release APK (needs the 4 keystore props — see below)
./gradlew :app:test                   # JVM unit tests (JUnit 4, Robolectric, Turbine)
./gradlew :app:detekt                 # static analysis; CI gates on this
./gradlew :app:lint                   # AGP Lint
./gradlew :app:dependencies --write-locks   # regenerate gradle.lockfile (gitignored except at release tags)
```

## Working conventions

### PR review

**Every PR opened in this repo goes through the multi-agent review cycle.**
Workflow is:

1. Open the PR with `Closes #N` or `Refs #N` in the body — no PR ships without
   a linked issue. If none exists, `gh issue create` first.
2. Run the multi-agent review (e.g. `/ultrareview` or `/review`).
3. Post each finding as an **inline review thread** on the diff (not a single
   summary comment) so each one can be resolved independently.
4. Fix all findings on the same branch.
5. After each fix lands, **resolve the corresponding inline thread** via the
   GraphQL `resolveReviewThread` mutation. (`gh api graphql` mangles `!` in
   GraphQL syntax even inside quoted heredocs — call `gh` from Python
   `subprocess` instead.)
6. Only then declare ready-to-merge.

### Hooks, signing, force

- **Never `--no-verify`, `--no-gpg-sign`, `--force`, `--force-with-lease`**
  unless the user explicitly asks. If a pre-commit / pre-push hook fails,
  fix the underlying issue — the hook is load-bearing (detekt + secret
  scanning gate every commit).
- Force-pushing to `main` is never appropriate; warn if asked.

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
    repository/                 # ProductRepository — wraps DAO + OFF fallback
    ui/                         # Compose screens (home, scan, detail) + theme
    model/                      # domain types
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
