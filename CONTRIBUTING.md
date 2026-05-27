# Contributing to Pantry Tracker

Thanks for looking at the code. **Pantry Tracker** is a small, single-user
Android app for kitchen-pantry inventory, maintained by
[DocGerd](https://github.com/DocGerd). The operational guide — workflow,
tooling, project-local conventions, and the things that have bitten past
sessions — lives in [`CLAUDE.md`](CLAUDE.md); the architecture lives under
[`docs/architecture/`](docs/architecture/). Read those before a substantial
change; it will save you a round-trip on review.

---

## Code of Conduct

This project adopts the [Contributor Covenant](CODE_OF_CONDUCT.md). By taking
part — issues, pull requests, discussions — you agree to uphold it. Report
unacceptable behaviour via a
[private security advisory](https://github.com/DocGerd/pantry-tracker/security/advisories/new)
or a direct message to [@DocGerd](https://github.com/DocGerd), as described in
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

---

## First-time setup

You need:

- **JDK 21** on your `PATH`.
- An **Android SDK** with the API 36 platform and build tools installed
  (point `local.properties` / `ANDROID_HOME` at it).

The project builds with the Gradle wrapper — no global Gradle install needed:

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:test                 # JVM unit tests (JUnit 4, Robolectric, Turbine)
./gradlew :app:detekt               # Kotlin static analysis
./gradlew :app:lint                 # Android Lint
```

---

## Issues first

Every change — bug fix, feature, docs update — starts with a GitHub issue, so
there's a clear record of what was intended and why. If no issue exists for the
work, open one first.

When you report a bug use the **Bug report** template; for a feature or a
question use the matching template. All live in
[`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/).

**Every pull request links its issue in the body** using `Closes #N` (or
`Fixes #N` / `Resolves #N`) when the PR fully resolves the issue — those
auto-close it on merge — or `Refs #N` when the PR is only a partial step and the
issue should stay open. No PR ships without an issue link.

---

## Workflow (trunk-based on `main`)

Pantry Tracker is **trunk-based**: `main` is the single long-lived branch, and
every change lands through a short-lived feature branch and a reviewed pull
request. **There is no `develop` branch and no release branch.** Never push
directly to `main` — see [`GOVERNANCE.md`](GOVERNANCE.md) for why only the
human maintainer merges.

The standard loop:

```bash
git switch main && git pull
git switch -c <type>/<tracker-id>-<slug>

# ... write code, add tests, commit ...

git push -u origin <type>/<tracker-id>-<slug>
gh pr create --base main --title "type(scope): short summary" --body "Closes #N ..."
```

### Branch naming

Branches follow `<type>/<tracker-id>-<slug>`, where the tracker id is the issue
number (e.g. `sr-42`). One branch per issue.

| Type | Use for |
|---|---|
| `feat` | New features |
| `fix` | Bug fixes |
| `chore` | Build, tooling, dependency, and housekeeping changes |
| `docs` | Documentation-only changes |
| `security` | Security hardening |

Examples: `docs/sr-94-oss-preflight`, `fix/sr-46-undo-restore`,
`security/v1-final-hardening`.

---

## Commit style

Follow the conventional-commit pattern: `<type>(<scope>): <summary>`.

```
feat(sr-44): walk OFF sister hosts on 404
fix(sr-46): preserve every column on delete UNDO
chore(release): lock dependencies
```

Keep the summary under ~72 characters and in the imperative mood. Common types
match the branch types above (`feat`, `fix`, `chore`, `docs`, `security`).

---

## Hooks and the quality gate

Two gates protect every change:

- **CI** runs on every PR. It builds the debug APK, runs the JVM unit tests and
  Android Lint, and — the hard static-analysis gate — runs **Detekt**
  (`.github/workflows/ci.yml`). A separate Security workflow runs **Gitleaks**
  (secret scanning) and **OSV-Scanner** against the resolved dependency
  lockfile (`.github/workflows/security.yml`). A red check blocks merge; fix the
  underlying issue rather than working around it.
- **Claude Code guard hooks** ship in [`.claude/`](.claude/) (see
  [`.claude/README.md`](.claude/README.md)). When you work through the Claude
  Code CLI, a `PreToolUse` hook blocks edits to the machine-generated Gradle
  lockfile, and another pattern-matches the command line to block dangerous git
  operations (force-push, `--no-verify`, direct pushes to `main`). These mirror
  the project's hard rules so a violation is caught before it reaches CI.

**Never bypass a gate.** Do not pass `--no-verify`, never force-push, and never
land code on `main` directly. If a gate fails, that is real signal — fix the
cause.

---

## Code review

**Every PR opened in this repo goes through a multi-agent review cycle before it
is considered ready to merge.** Reviewers file findings as **inline review
threads on the diff**, not as a single summary comment, so each finding is tied
to its line and can be resolved independently. Work through every open thread:
fix the code (preferred) or reply with a clear rationale, then mark the thread
resolved. The end-to-end recipe — dispatching the review, posting findings,
fixing, and resolving threads — is documented in the project's `pr-review`
skill under [`.claude/skills/`](.claude/skills/).

Only declare a PR ready to merge once CI is green and all review threads are
resolved.

---

## Approval and merge

**Merging is maintainer-only, and only a human merges to `main`.** Once your PR
is green and all review threads are resolved, post a comment saying it's ready
for final review and wait for the maintainer to merge. This is a hard
governance rule (see [`GOVERNANCE.md`](GOVERNANCE.md) and
[`CLAUDE.md`](CLAUDE.md)): no automated agent runs `gh pr merge` or pushes to
`main`. The audit trail is the maintainer's explicit click on "Merge pull
request".

---

## Source-file headers (Apache-2.0)

The project is licensed under [Apache-2.0](LICENSE). New source files do not
currently carry per-file license headers — the repository-level `LICENSE` file
governs the whole tree. If you add a header to a file (for example when
contributing a file you also publish elsewhere), use the standard Apache-2.0
short form:

```kotlin
/*
 * Copyright 2026 DocGerdSoft (Patrick Kuhn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

By contributing, you agree that your contributions are licensed under the same
Apache-2.0 terms as the rest of the project.

---

## Where the design lives

- [`docs/architecture/`](docs/architecture/) — arc42 architecture docs; read
  [§1 Introduction and Goals](docs/architecture/01-introduction-and-goals.md)
  and [§3 System Scope and Context](docs/architecture/03-system-scope-and-context.md)
  first.
- [`CHANGELOG.md`](CHANGELOG.md) — what changed per release.
- [`CLAUDE.md`](CLAUDE.md) — the operational guide: workflow, tooling,
  project-local config, and the gotchas that have bitten past sessions.

If something in the codebase seems strange, check `CLAUDE.md` and the arc42 docs
first — the privacy and offline-first constraints drive several non-obvious
decisions (e.g. severing ML Kit's telemetry pipeline at the manifest level).
