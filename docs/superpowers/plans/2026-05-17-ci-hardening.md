# CI Hardening (Milestone 2.5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add free-tier code-quality + vulnerability-scanning gates to CI before M3's network code lands.

**Architecture:** Two-workflow split (`ci.yml` extended with Detekt, sibling `security.yml` for OSV + Gitleaks) + Dependabot config + SECURITY.md. Single PR. Repo-settings flips are documented for the user to run manually.

**Tech stack:** Detekt (Gradle plugin), `google/osv-scanner-action@v2`, `gitleaks/gitleaks-action@v2`, GitHub-native Dependabot.

**Tracking issue:** [#11](https://github.com/DocGerd/pantry-tracker/issues/11)
**Spec:** `docs/superpowers/specs/2026-05-17-ci-hardening-design.md`

---

### Task 1: Add Detekt to the build (plugin + version catalog + config skeleton)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `detekt-config.yml` (only if Task 2 needs overrides — leave for now)

- [ ] **Step 1: Add Detekt version + library/plugin aliases to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
detekt = "1.23.7"
```

Under `[libraries]` add (for the formatting plugin runtime dep):
```toml
detekt-formatting = { group = "io.gitlab.arturbosch.detekt", name = "detekt-formatting", version.ref = "detekt" }
```

Under `[plugins]` add:
```toml
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

- [ ] **Step 2: Apply the Detekt plugin in `app/build.gradle.kts`**

In the `plugins { }` block at the top, add:
```kotlin
alias(libs.plugins.detekt)
```

Below `android { }` block (or near the top after `plugins`), add a `detekt { }` configuration block:
```kotlin
detekt {
    // Use built-in defaults; only commit detekt-config.yml if Step 4 finds we need overrides.
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    // Don't fail the Gradle build mid-task; CI runs `detektDebug` as its own step.
    ignoreFailures = false
}
```

In the `dependencies { }` block, add:
```kotlin
detektPlugins(libs.detekt.formatting)
```

- [ ] **Step 3: Run Detekt locally and capture the first-run output**

Run: `./gradlew detektDebug --no-daemon 2>&1 | tee /tmp/detekt-first-run.log`

Expected: either a clean pass (`BUILD SUCCESSFUL`) or a list of issues with file:line:rule references.

- [ ] **Step 4: Triage first-run findings**

Count the issues. Decision tree:
- **0 issues:** continue to Step 6.
- **1–15 issues:** fix each one. Common easy fixes:
  - `MagicNumber` → extract `const val`
  - `MaxLineLength` → wrap the line
  - `UnusedPrivateMember` → delete or annotate
  - `LongParameterList` → if it's a Compose function, add `@Suppress("LongParameterList")` with a comment that Compose preview params count.
  - `MagicNumber` in tests → add the rule's `ignoreNumbers` for tests via `detekt-config.yml`.
- **>15 issues:** STOP. Snapshot a baseline (`./gradlew detektBaselineDebug`), commit `detekt-baseline.xml`, open a follow-up issue titled "Detekt baseline cleanup" listing the rule counts, and document the deferral in the PR body.

If `detekt-config.yml` ends up being needed, generate it with `./gradlew detektGenerateConfig` (writes to `app/config/detekt/detekt.yml`), move it to repo root as `detekt-config.yml`, then point `detekt { config.setFrom(files("$rootDir/detekt-config.yml")) }`.

- [ ] **Step 5: Re-run and verify clean**

Run: `./gradlew detektDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Detekt setup**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
# Only add these if Steps 4 generated them:
git add detekt-config.yml detekt-baseline.xml 2>/dev/null || true
# Plus any source files fixed in Step 4.
git commit -m "ci: add Detekt with formatting rules; fix first-run findings"
```

---

### Task 2: Wire Detekt into the existing CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add a Detekt step after the build/test/lint step**

In `.github/workflows/ci.yml`, after the existing "Build, unit-test, lint" step, add:

```yaml
      - name: Detekt (Kotlin static analysis)
        if: steps.gate.outputs.ready == 'true'
        run: ./gradlew --no-daemon detektDebug
```

Keep it as a *separate* step (not appended to the previous `./gradlew` call) so a Detekt failure is visually distinct from a build failure in the GitHub Checks UI.

- [ ] **Step 2: Commit the workflow change**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: run Detekt as a separate CI step"
```

---

### Task 3: Add `security.yml` workflow with OSV-Scanner

**Files:**
- Create: `.github/workflows/security.yml`

- [ ] **Step 1: Create the security workflow with OSV-Scanner**

Create `.github/workflows/security.yml`:

```yaml
name: Security

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    # Weekly Monday 06:00 UTC — catches new CVEs disclosed since the last PR.
    - cron: '0 6 * * 1'

concurrency:
  group: security-${{ github.ref }}
  cancel-in-progress: true

jobs:
  osv-scan:
    name: OSV-Scanner (Gradle deps)
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4
      - name: Run OSV-Scanner
        uses: google/osv-scanner-action/osv-scanner-action@v2.2.0
        with:
          # Scan the whole repo; OSV detects Gradle lockfiles + version catalogs automatically.
          scan-args: |-
            --recursive
            --skip-git
            ./
          # Fail only on HIGH or CRITICAL severity to avoid noise from LOW transitive deps.
          fail-on-vuln: true
        env:
          OSV_SCANNER_FAIL_ON_SEVERITY: HIGH
      - name: Upload SARIF artifact
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: osv-scanner-results
          path: results.sarif
          if-no-files-found: ignore
```

Note: confirm `OSV_SCANNER_FAIL_ON_SEVERITY` is the actual env var the action reads — check `google/osv-scanner-action` v2.2.0 README before committing. If the action uses a different mechanism (e.g. a CLI flag), update `scan-args` accordingly. If neither works, `fail-on-vuln: true` will fail on ANY finding; that's acceptable for a first pass — open a follow-up if it's too strict.

- [ ] **Step 2: Trigger a sanity check**

Push the branch and verify the OSV-Scanner step runs successfully on current deps (expected: clean — none of our deps have known HIGH CVEs as of 2026-05-17).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/security.yml
git commit -m "ci: add OSV-Scanner workflow for Gradle dep CVE scanning"
```

---

### Task 4: Add Gitleaks job to `security.yml`

**Files:**
- Modify: `.github/workflows/security.yml`

- [ ] **Step 1: Append a gitleaks job to the security workflow**

In `.github/workflows/security.yml`, add a second job under `jobs:`:

```yaml
  gitleaks:
    name: Gitleaks (secret scan)
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: read
    steps:
      - uses: actions/checkout@v4
        with:
          # Gitleaks scans diff between base and head; needs history.
          fetch-depth: 0
      - name: Run Gitleaks
        uses: gitleaks/gitleaks-action@v2.3.7
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          # GITLEAKS_LICENSE not required for personal accounts.
          # Scan only the PR diff on PR events; full repo on push to main.
          GITLEAKS_CONFIG: ''  # use built-in default ruleset
```

Verify the `gitleaks-action@v2.3.7` README — confirm it's free for personal accounts AND that the env var names above are current (the action's contract has shifted between v2 minor releases). If the README says a license key IS required, fall back to using the `gitleaks` CLI directly:

```yaml
      - name: Install gitleaks
        run: |
          curl -sSL -o gitleaks.tar.gz https://github.com/gitleaks/gitleaks/releases/download/v8.21.2/gitleaks_8.21.2_linux_x64.tar.gz
          tar -xzf gitleaks.tar.gz
          sudo mv gitleaks /usr/local/bin/
      - name: Run gitleaks on diff
        run: |
          if [ "${{ github.event_name }}" = "pull_request" ]; then
            gitleaks detect --log-opts="${{ github.event.pull_request.base.sha }}..${{ github.sha }}" --verbose
          else
            gitleaks detect --verbose
          fi
```

The CLI fallback is fine — the action only buys convenience.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/security.yml
git commit -m "ci: add Gitleaks secret scan to security workflow"
```

---

### Task 5: Add `.github/dependabot.yml`

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Create the Dependabot config**

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
      day: monday
      time: "08:00"
      timezone: Etc/UTC
    open-pull-requests-limit: 5
    groups:
      gradle-minor-patch:
        update-types:
          - minor
          - patch
    labels:
      - dependencies
      - gradle

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
      day: monday
      time: "08:00"
      timezone: Etc/UTC
    open-pull-requests-limit: 5
    groups:
      actions-minor-patch:
        update-types:
          - minor
          - patch
    labels:
      - dependencies
      - github-actions
```

- [ ] **Step 2: Commit**

```bash
git add .github/dependabot.yml
git commit -m "ci: add Dependabot config for gradle + github-actions (weekly, grouped)"
```

---

### Task 6: Add `SECURITY.md`

**Files:**
- Create: `SECURITY.md`

- [ ] **Step 1: Create the disclosure policy**

```markdown
# Security Policy

## Supported Versions

Only the `main` branch is supported. There are no released versions yet.

## Reporting a Vulnerability

Please report security vulnerabilities **privately**. Do NOT open a public GitHub
issue for security-sensitive findings.

- **Preferred:** open a GitHub Security Advisory draft via
  https://github.com/DocGerd/pantry-tracker/security/advisories/new

### What to include

- A clear description of the issue and where it lives in the code (file path + line).
- Steps to reproduce or a proof-of-concept.
- The impact you've assessed (data exposure, privilege escalation, etc.).
- Your contact for follow-up.

### What to expect

- Acknowledgement within **7 days**.
- A first assessment within **14 days**.
- A coordinated fix and disclosure within **90 days** of acknowledgement, unless
  the issue is particularly complex (in which case we will agree on a longer
  timeline together).

### Scope

In scope:
- Code in this repository (`DocGerd/pantry-tracker`).
- The CI/CD pipeline in `.github/workflows/`.

Out of scope:
- Third-party dependencies — please report to their maintainers directly, then
  optionally let us know so we can pin/upgrade.
- Vulnerabilities requiring root access on a user's device.
```

- [ ] **Step 2: Commit**

```bash
git add SECURITY.md
git commit -m "docs: add SECURITY.md disclosure policy"
```

---

### Task 7: Document repo-settings flips in the PR body

**Files:** none (PR description only)

- [ ] **Step 1: Note the commands the user must run before/after merge**

These are NOT a code change — they're API calls that flip server-side flags. Document them in the PR description so the user runs them with their own authenticated `gh`:

```bash
# Enable Dependabot alerts (free, native)
gh api -X PUT repos/DocGerd/pantry-tracker/vulnerability-alerts

# Enable Dependabot security updates (free, native)
gh api -X PUT repos/DocGerd/pantry-tracker/automated-security-fixes

# Enable Secret Scanning + Push Protection (free for personal private since 2024)
gh api -X PATCH repos/DocGerd/pantry-tracker \
  -f security_and_analysis[secret_scanning][status]=enabled \
  -f security_and_analysis[secret_scanning_push_protection][status]=enabled
```

These are mentioned in the PR body, NOT executed by the implementer.

---

### Task 8: Push branch + open PR + watch CI

- [ ] **Step 1: Push the branch**

```bash
git push -u origin ci-hardening
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "Milestone 2.5: CI hardening (Detekt + OSV + Gitleaks + Dependabot)" \
  --body "<see task 7 + spec link + acceptance criteria from spec>"
```

Body should link to issue #11, the spec doc, and include the repo-settings `gh api` commands the user runs before merge.

- [ ] **Step 3: Watch CI**

Run: `gh run watch <run-id> --exit-status`

Both workflows (`CI` and `Security`) must go green. If either fails, fix in-place and re-push.

- [ ] **Step 4: Hand off — do NOT merge**

Per standing instruction: user merges. After CI green, run the multi-agent PR review per the "Always pr review" feedback, post inline findings, fix all findings, then report ready-for-merge.

---

## Self-review

- [x] All 6 spec deliverables (Detekt, OSV, Gitleaks, dependabot.yml, SECURITY.md, settings doc) covered by tasks 1–7.
- [x] No placeholders or "implement later" stubs.
- [x] Each task has explicit files, code, commands, and a commit step.
- [x] Sanity-check items from the spec acceptance criteria (deliberate violation, fake secret) are NOT in this plan — they're verification the user does after merge, separate from the implementation.
