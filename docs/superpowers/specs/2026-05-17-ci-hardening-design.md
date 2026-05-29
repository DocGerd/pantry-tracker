# Milestone 2.5 — CI Hardening (free tier) — Design Spec

**Status:** Approved 2026-05-17 (user picked "recommended lean set" + "now, before M3" + "all in one PR")
**Tracking issue:** [#11](https://github.com/DocGerd/pantry-tracker/issues/11)

## Goal

Add free-tier code-quality and vulnerability-scanning gates to CI so M3's first network dependencies (Open Food Facts client) land into a hardened pipeline. All additions are free for a private personal GitHub repo.

## Non-goals

- **CodeQL** — requires paid GitHub Advanced Security on private personal repos. Revisit only if/when the repo goes public.
- **SonarCloud / Sonar** — free tier is public-repos-only.
- **Kover (code coverage)** — defer to the M6 polish pass.
- **Mandatory branch-protection rules** — out of scope; user can enable in the GitHub UI separately.
- **Release-signing workflow** — out of scope until v1 ship.

## Architecture

Two-workflow split: keep `ci.yml` focused on build/test/lint (extended with Detekt); add a sibling `security.yml` for OSV-Scanner and Gitleaks. Security scans run on a different cadence (push/PR + weekly) and can fail independently of the build. A separate file is easier to skim/disable.

```
.github/
  ├── workflows/
  │     ├── ci.yml          (existing — extend with detektDebug step)
  │     └── security.yml    (NEW — osv-scanner + gitleaks jobs)
  └── dependabot.yml        (NEW)
detekt-config.yml           (NEW — minimal overrides, only if defaults flag silly things)
SECURITY.md                 (NEW)
app/build.gradle.kts        (modified — Detekt plugin + detekt {} block)
gradle/libs.versions.toml   (modified — detekt version + library aliases)
```

## Tool decisions

### Detekt (Kotlin static analysis)

- Apply `io.gitlab.arturbosch.detekt` Gradle plugin via the version catalog.
- Add `detekt-formatting` runtime dep so Detekt also enforces ktlint-style formatting rules.
- Start with built-in defaults; commit `detekt-config.yml` only if defaults flag obviously-wrong things.
- Wire `detektDebug` as a separate step in `ci.yml` (after the existing `assembleDebug testDebugUnitTest lintDebug` step) so a Detekt failure is distinguishable from a build failure in the GitHub Checks UI.
- **Failure policy:** zero baseline — fix any first-run findings inline. If the first run is unexpectedly noisy (>15 issues), the implementer snapshots `detekt-baseline.xml`, fixes nothing in this PR, and opens a follow-up issue for the cleanup.

### OSV-Scanner (CVE scan for Gradle deps)

- Use `google/osv-scanner-action@v2` (officially maintained by Google).
- Triggers: `push` to `main`, `pull_request`, and weekly cron (Monday 06:00 UTC).
- **Failure threshold:** fail on `HIGH`+. `MEDIUM`/`LOW` are reported but non-blocking — avoids being held hostage by low-severity issues in transitive test deps.
- SARIF output uploaded as artifact for later inspection. (Code Scanning upload requires GHAS on private — skip.)

### Gitleaks (secret scanning in diffs)

- Use `gitleaks/gitleaks-action@v2`.
- Triggers: PR + push to `main`.
- Scans the diff (not the whole history) — fast and focused.
- Defense-in-depth: pairs with GitHub-native secret-scanning push protection, which is enabled at repo-settings level.
- **Failure policy:** any leak fails the workflow.
- License: gitleaks-action v2 is free for personal accounts; verify in README before adding.

### Dependabot

- `.github/dependabot.yml` configures two ecosystems:
  - `gradle` at the project root — monitors `app/build.gradle.kts` + `gradle/libs.versions.toml`
  - `github-actions` — monitors `.github/workflows/*.yml`
- **Cadence:** weekly, Monday 08:00 UTC.
- **Grouping:** `minor-and-patch` group per ecosystem so 10 minor bumps land in 1 PR instead of 10.
- **Auto-merge:** NOT enabled — user reviews each PR.

### SECURITY.md

- Standard template: GitHub Security Advisory link as private contact channel, 90-day disclosure timeline, scope = this repo only, supported versions = `main` branch.

### Repo settings (one-off `gh api` calls, not a PR)

```bash
gh api -X PUT repos/DocGerd/pantry-tracker/vulnerability-alerts
gh api -X PUT repos/DocGerd/pantry-tracker/automated-security-fixes
gh api -X PATCH repos/DocGerd/pantry-tracker \
  -F security_and_analysis[secret_scanning][status]=enabled \
  -F security_and_analysis[secret_scanning_push_protection][status]=enabled
```

These run from the user's local shell with their authenticated `gh` — not from CI. Document the commands in the PR description.

## Sequencing & PR shape

**One PR** lands everything: Detekt plumbing, OSV workflow, Gitleaks workflow, `dependabot.yml`, `SECURITY.md`, and the spec/plan docs. Repo-settings calls are documented in the PR body for the user to run before merging.

Within the PR, commits are split for review hygiene:

1. Spec + plan docs
2. Detekt plugin + config + CI wiring + first-run fixes (if any)
3. `security.yml` with both OSV and Gitleaks jobs
4. `.github/dependabot.yml`
5. `SECURITY.md`

## Acceptance criteria

- CI is green on the PR with both `build` and `security` workflows passing.
- Detekt runs as a CI step; one deliberate violation (e.g. `MagicNumber`) introduced in a sanity check fails the build, then is reverted.
- OSV-Scanner output appears as a SARIF artifact on the run.
- Gitleaks runs without findings on the clean diff.
- A second sanity-check commit committing a fake AWS-key-shaped string is blocked by both Gitleaks (CI) and GitHub push protection (server-side). Both checks then revert.
- `dependabot.yml` is valid (GitHub renders the config in the repo settings UI).
- After merge, the next M3 PR runs all three gates as part of normal CI.

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Detekt first run flags many M0–M2 issues | If >15, snapshot baseline + open follow-up issue. If ≤15, fix inline. |
| OSV-Scanner false-positives on transitive test deps | `HIGH`+ threshold avoids most noise. Document override path in PR body. |
| gitleaks-action license changes | v2 is free for personal accounts as of writing. Pinned to a specific tag, not `@latest`. |
| Dependabot opens many PRs immediately | `minor-and-patch` grouping reduces to 1 PR per ecosystem. Activates only after merge, not during. |
| `security.yml` consumes Actions minutes | Detekt + OSV + Gitleaks together run <5 min. Free tier is 2000 min/month. Negligible. |
