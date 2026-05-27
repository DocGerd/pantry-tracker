# Open-source the pantry-tracker repository — design

> **Date:** 2026-05-27
> **Status:** Approved (brainstorming complete; awaiting execution go-ahead)
> **Template reference:** [`DocGerd/hangarfit`](https://github.com/DocGerd/hangarfit) — the
> maintainer's existing public-repo pattern. This spec mirrors hangarfit's structure
> where it fits and calls out every deliberate divergence.

## Goal

Flip the `DocGerd/pantry-tracker` GitHub repository from **private → public** as a
properly-licensed, community-health-complete open-source project. Distribution of the
app itself is **unchanged**: signed sideload APK on GitHub Releases. This is about
opening the *code and its history*, not a new distribution channel.

## Locked decisions

| Decision | Choice | Rationale |
|---|---|---|
| What "going public" means | Open-source the repo | Distribution stays sideload-APK; no Play Store / F-Droid in this scope |
| License | **Apache-2.0** | Android-ecosystem standard (AOSP), explicit patent grant; matches hangarfit |
| Sequencing | **Flip early, polish in the open** | The flip unblocks free CI + CodeQL + secret-scanning immediately, which directly helps the in-flight Wave 3 test PRs |
| Maturity level | **Full hangarfit parity** | CodeQL + OpenSSF Scorecard + Best Practices badge + ADR backfill + security-posture doc + `.editorconfig` |
| Branch model | **Keep trunk-based on `main`** (NOT hangarfit's GitFlow) | pantry-tracker's entire CLAUDE.md workflow assumes `main` + feature branches + PR. Importing `develop` would be a regression |
| Governance | Single-maintainer; **only humans merge to `main`** | Already the project's hard rule; `GOVERNANCE.md` documents it, branch protection enforces it post-flip |
| Internal artifacts (`CLAUDE.md`, `.claude/`, `docs/superpowers/`) | **Keep public** | Confirmed by hangarfit precedent — these are an asset (disciplined-process signal), not a liability |

## The triple unblock

Going public is not just a visibility change — for this repo it removes three constraints
at once, all currently caused by the repo being a *private* personal repo:

1. **Free GitHub Actions minutes** → the billing block that currently lands every PR's CI
   red (including the #84 emulator job) disappears.
2. **Free CodeQL code-scanning** → previously GHAS-paid-only on private repos.
3. **Free GitHub secret-scanning** → previously GHAS-paid-only.

Consequence: the project's "use Gitleaks-in-CI because CodeQL/secret-scanning are paid"
workaround can be revisited post-flip. (Memory note `reference_github_security_features_free_tier`
should be updated once this lands.)

## The flip is the only irreversible step

Everything before the flip is reversible (delete a doc, revert a commit). The flip itself
exposes the full 246-commit history permanently. Therefore:

- A **full git-history secret scan is a hard go/no-go gate** before the flip.
- Per the project's governance rule, **the human performs the flip** (`Settings → Change
  visibility → Public`, or `gh repo edit DocGerd/pantry-tracker --visibility public`).
  Claude prepares a go/no-go checklist; Claude never flips the repo.

## Gap analysis vs the hangarfit template

Already present (keep): `CHANGELOG.md`, `SECURITY.md`, `CLAUDE.md`, `.claude/{agents,hooks,skills,settings.json}`,
`docs/architecture/` (arc42), `docs/superpowers/`, `.github/{dependabot.yml, workflows/ci.yml, workflows/security.yml}`.

To create (essentials): `LICENSE`, `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`,
`GOVERNANCE.md`, `.github/CODEOWNERS`, `.github/ISSUE_TEMPLATE/{bug,feature,question,config}`,
`.github/pull_request_template.md`, `.editorconfig`, repo description + topics, `.mcp.json` (= existing issue #91).

To create (maturity tier, post-flip): `.github/workflows/codeql.yml`, `.github/workflows/scorecard.yml`,
OpenSSF Best Practices badge registration, `docs/security-posture.md`, `docs/adr/` backfill.

Already excluded correctly: `.claude/settings.local.json` (gitignored), `.claude/worktrees/`
(untracked), `HANDOVER.md` (untracked — add to `.gitignore` to harden against accidental commit).

## Milestones

### 🏁 Milestone "Open Source: Pre-flight" — must be green before the flip

| # | Issue | Notes |
|---|---|---|
| OSS-1 | Full git-history secret scan + `.claude/settings.json` review | **Safety gate. Claude runs it directly.** `gitleaks detect` over all history. |
| OSS-2 | `LICENSE` (Apache-2.0) + source-header convention | `LICENSE` + optional `NOTICE`; document header convention in CONTRIBUTING |
| OSS-3 | `README.md` | Overview, install (sideload), build, tech stack, badges. Screenshots = placeholder, real ones deferred (needs emulator) |
| OSS-4 | Community-health docs | `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (Contributor Covenant), `GOVERNANCE.md` (**trunk-based**, single-maintainer, only-humans-merge) |
| OSS-5 | `.github/` templates | `CODEOWNERS`, `ISSUE_TEMPLATE/{bug,feature,question,config}`, `pull_request_template.md` (encode "every PR links an issue") |
| OSS-6 | Repo hygiene & metadata | `.gitignore` `HANDOVER.md`; `.editorconfig`; `.claude/README.md`; set repo description + topics |

**Flip gate:** When OSS-1…OSS-6 are merged, Claude posts a go/no-go checklist. The human flips the repo public.

### 🌱 Milestone "Open Source: In the Open" — after the flip

| # | Issue | Notes |
|---|---|---|
| OSS-7 | Verify the triple unblock | Confirm Actions go green; enable free secret-scanning; re-trigger a run as proof |
| OSS-8 | Branch protection on `main` | Require PR + review; mechanically enforce the governance rule |
| OSS-9 | `codeql.yml` | Free on public; reconcile with existing `security.yml`/gitleaks |
| OSS-10 | `scorecard.yml` + OpenSSF Best Practices badge | Register the Best Practices project; add badges to README |
| OSS-11 | `docs/security-posture.md` | Document structural Scorecard zeros + what we do instead (mirrors hangarfit) |
| OSS-12 | `docs/adr/` backfill | Extract existing decisions (manual DI, Room, Ktor+OFF fallback, RNG-not-Paparazzi, R8, fail-closed body cap, …) into ADR format + `0000-record-architecture-decisions` + template |

## Relationship to in-flight work

- **Wave 3 UAT-automation PRs (#76/#77/#78/#82)** are independent of open-sourcing and run
  in parallel. They benefit from the flip (real CI). They consume the merged #88 fixtures.
- **v1.2 release** (feature-complete on `main`, unreleased) is a separate track. Open-sourcing
  does not require shipping v1.2 first. Recommend shipping v1.2 whenever its release criteria
  are met, independent of this work.
- **UAT umbrella #73 (Wave 4 close-out)** is unaffected.

## Out of scope (explicitly deferred)

- Google Play Store / F-Droid distribution — separate future roadmap if ever pursued.
- Switching to GitFlow / a `develop` branch — pantry-tracker stays trunk-based.
- Real app screenshots in the README — follow-up once an emulator capture is run.
- Relicensing existing release tags — Apache-2.0 applies going forward; historical tags are unchanged.

## This-session execution (full fan-out)

1. **Claude, directly:** run OSS-1 secret scan (safety gate); write + this spec; create the
   2 GitHub milestones and the OSS-2…OSS-12 issues; create existing-work tracking is already
   in #76/#77/#78/#82/#90/#91/#92.
2. **Dispatch 6 worktree subagents in parallel** (per `dispatching-parallel-agents` + project
   worktree hygiene — absolute paths, `pwd`/branch verification, explicit `git add`):
   - 4 × Wave 3: #76, #77, #78, #82
   - 1 × OSS Pre-flight docs PR (OSS-2…OSS-6 as a single reviewable PR)
   - 1 × #91 `.mcp.json`
3. **Claude runs directly (no worktree):** review cycles for #90 and #92.
4. Each PR → diff review → `pr-review` cycle → hand off. **Nothing merges without the human.**
5. Post-merge of OSS pre-flight PR → go/no-go checklist → human flips public → Milestone
   "In the Open" (OSS-7…OSS-12) in a follow-up session.

## Self-review

- **Placeholders:** none — every issue has a concrete file map.
- **Consistency:** branch model stated once (trunk-based) and reinforced in GOVERNANCE note; no GitFlow leakage.
- **Scope:** two milestones, each independently shippable; pre-flight gates the flip, in-the-open is post-flip polish.
- **Ambiguity:** "keep internal artifacts public" is explicit and precedent-backed; flip actor is explicitly the human.
