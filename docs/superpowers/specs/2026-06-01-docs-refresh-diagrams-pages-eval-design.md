# Design — Docs refresh, Mermaid diagrams, Pages evaluation & codecov promotion

> **Date:** 2026-06-01
> **Issues:** [#223](https://github.com/DocGerd/pantry-tracker/issues/223) (docs content + diagrams), [#224](https://github.com/DocGerd/pantry-tracker/issues/224) (docs-delivery decision); plus the deferred **codecov/project** required-check promotion from #219.
> **Status:** Approved (brainstorming) — pending implementation plan.
> **Milestone:** Open Source: In the Open (#2).

## Context

The tracked documentation is comprehensive (arc42, ADRs, security posture, release
runbook, UAT) but has drifted in a few concrete places since the v1.x releases, and
several rich flows are still ASCII-only. Separately, #219's coverage gate landed on
`develop` but its final step — promoting `codecov/project` to a **required** status
check — was deferred to "the next PR against develop," because Codecov only posts
that check on a PR once the default branch carries a `codecov.yml` baseline.

This design coordinates three independent-but-related work items into **two PRs**:

| Track | Issue | PR | Nature |
|---|---|---|---|
| **A** | #223 | PR #1 → `develop`, `Closes #223` | Docs **content** — stale fixes, index, ~9 Mermaid diagrams |
| **Codecov** | #219 (tail) | rides on PR #1 | Admin ruleset PATCH, gated on observed green |
| **B** | #224 | PR #2 → `develop`, `Closes #224` | Docs **delivery decision** — MkDocs pilot → ADR-0007 |

The issue author confirmed #223 and #224 are independent and may land in either
order; a docs site is a *render* of #223's content. We sequence **A → B** because
(1) PR #1 is the first PR after `codecov.yml`'s baseline merged, so it is the natural
trigger to first observe `codecov/project`; and (2) the #224 MkDocs pilot can use
#223's merged Mermaid diagrams as its rendering test fixture, while the pilot's local
Mermaid renderer doubles as an empirical syntax cross-check for #223 (we have no
Node/`mermaid-cli` toolchain).

## Governance

Per the repo's hard rule: **only the human merges to `develop`/`main`.** Claude opens
both PRs, runs the multi-agent review cycle (`pr-review` skill), posts findings as
inline threads, fixes them, resolves the threads, and hands off — the human clicks
Merge. The **codecov ruleset PATCH is GitHub admin configuration, not a code merge**,
so it is Claude's to run via `gh api` — but only on an *observed* green check, never
blind (a required check that never posts deadlocks all future merges).

---

## Track A — #223 docs refresh + diagrams (PR #1)

### A1. Stale-content fixes (§A)

| File / lines | Current (stale) | Fix |
|---|---|---|
| `SECURITY.md:3-5` | "Only the `main` branch is supported. There are no released versions yet." | Replace with a real Supported-Versions policy: v1.x releases ship as signed sideload APKs; the **latest release (v1.3.1)** is supported. Keep consistent with the already-release-aware "Verifying a release" section (lines 50-85). |
| `README.md:19-26` | Version enumeration stops at v1.1.0; "**v1.2** is feature-complete on `main` but **not yet released**." | Extend through **v1.3.1**; remove the "not yet released" bullet (v1.2.0 shipped 2026-05-28, v1.3.1 on 2026-05-29). Add a `releases/latest` link. |
| `README.md:36-40` | "_Screenshots are a follow-up._ … tracked separately" | Repoint at a **new tracking issue** (filed as part of this work — see A5). No emulator capture in this PR. |
| `docs/security-posture.md:4` **and** `:672` | "Last reviewed: **2026-05-28**" (two occurrences) | Bump **both** to 2026-06-01 **and** add a small content touch reflecting the v1.3.1 cosign/SLSA retirement (PR #210/#211): provenance is now apksigner cert + SHA-256 + GitHub auto-attestation, not cosign/SLSA. A pure date-bump would be inaccurate. |
| `docs/security/openssf-best-practices-tracking.md:111` | "No `ROADMAP.md` yet" note on the `documentation_roadmap` row | **Discovered drift** — `ROADMAP.md` exists at repo root. Flip the row's note to reflect that it is satisfiable; same stale-content class. (Form-side bestpractices.dev edit is out of scope; this is just the tracked-doc note.) |
| Coverage figures (repo-wide) | — | **Confirm-only.** Verified already consistent at **~85.93% line** in every tracked file (CLAUDE.md, `app/build.gradle.kts`, CHANGELOG, ci.yml, tracking doc). No edit needed; the §A item is already satisfied (closed by #219's `fccad4c`/`55e7934`). |

### A2. `docs/README.md` index (§B)

Add a top-level `docs/` landing page: a reading-order map of the tree
(`architecture/` arc42 → `security-posture.md` → `security/` → `release/SHIPPING.md`
→ `adr/` → `uat/` → `superpowers/`), so a newcomer landing in `docs/` has a
navigation entry point. This also becomes the in-repo "nav" win that #224's no-go
branch would lean on.

### A3. Mermaid diagrams (§C) — full set (~9)

GitHub renders Mermaid natively in blobs and PR diffs. Use conservative, well-supported
syntax (`stateDiagram-v2`, `flowchart TD`, `erDiagram`, `gitGraph`, `sequenceDiagram`).

1. **Scan-flow state machine** — new `## 6.5` in `docs/architecture/06-runtime-view.md`,
   a `stateDiagram-v2` of `ScanUiState.Phase` (source-confirmed in `ScanUiState.kt`):
   `Idle → Loading(barcode) → { Preview(candidate,qty) | ManualEntry(barcode,qty) |
   NotInInventory(barcode) [Remove-mode only, init-guarded] | Error(message: UiText) }`,
   with confirm → repository → back to `Idle` and error/recovery edges.
2. **GitFlow** — `gitGraph` under `CONTRIBUTING.md` `## Workflow (GitFlow)` (line 62):
   feature off `develop` → merge to `develop`; `release/X` off `develop` → merge to
   `main` + tag `vX.Y.Z` on `main` + back-merge to `develop`.
3. **Room data model** — `erDiagram` in `docs/architecture/05-building-block-view.md`
   (new subsection after §5.3). Two **independent** entities (no enforced FK; prose
   note that `off_lookup_cache.barcode` is a soft, non-FK correspondence to
   `products.barcode`):
   - `products`: `id BIGINT PK (autoGenerate)`, `barcode TEXT UQ nullable`,
     `name TEXT`, `brand TEXT nullable`, `imageUrl TEXT nullable`, `quantity INT`,
     `lowLimit INT nullable` (null ⇒ untracked), `defaultBuyAmount INT default 1`,
     `createdAt INTEGER (Instant epoch-ms)`, `updatedAt INTEGER`.
   - `off_lookup_cache`: `barcode TEXT PK`, `name TEXT (non-blank invariant)`,
     `brand TEXT nullable`, `imageUrl TEXT nullable`, `resolvingHost (OffHost enum
     via Converters)`, `fetchedAt INTEGER (Instant epoch-ms, 30-day TTL)`.
4. **System context** — `flowchart` (or Mermaid C4Context if acceptable) replacing/
   augmenting the ASCII boxes under `docs/architecture/03-system-scope-and-context.md`
   `## 3.1 Business context`: User ↔ App ↔ {OFF API (4-host chain, anonymous HTTPS
   GET), Android OS (CameraX, ML Kit, Room/SQLite, Settings), on-device file storage}.
5. **Release pipeline** — `flowchart TD` under `docs/release/SHIPPING.md`
   `## B.`: keystore-props gate → build → (schema changed? → migration UAT) →
   pre-tag lockfile regen → **human merge** → tag on `main` → immutable one-shot
   `gh release create` → SHA-256 + cert + attestation verify.
6–9. **Convert all four `06.1`–`06.4` ASCII sequences** to Mermaid `sequenceDiagram`
   (scan→add OFF-hit; scan→remove not-in-inventory; camera-permission settings
   round-trip; detail rename repository-throws). Keep the surrounding "Key invariants"
   prose intact.

> Implementation note: read `Product.kt` + `OffLookupCacheEntry.kt` (done — columns
> above are source-confirmed) before drafting the `erDiagram`.

### A4. CLAUDE.md — codecov-bootstrap lesson

Add a "Things that have bitten" entry capturing the Codecov bootstrap sequence
(project/patch post only on PRs; `codecov/project` first materializes on the first PR
after `codecov.yml`'s baseline merges to the default branch; never promote a
never-posted check to required). Folds the cross-cutting lesson into PR #1.

### A5. Screenshots tracking issue

`gh issue create` a "capture app screenshots (emulator)" issue (milestone: Open
Source: In the Open), linked from the README note (A1). Per the decision, no capture
in this PR.

### A6. Verification (Track A)

- Docs-only + CLAUDE.md ⇒ `:app:detekt` / CI semantics unaffected; confirm the
  `androidTest`/build jobs still pass on the PR.
- Internal-link check on touched files (no dead links).
- **Mermaid rendering** confirmed by the human viewing the rendered PR/blob on GitHub
  (acceptance = "renders on GitHub"); opportunistically cross-checked by the #224
  MkDocs pilot's local renderer.
- Acceptance (issue #223): all §A items corrected; `docs/README.md` added; ≥5 Mermaid
  diagrams rendering (we ship ~9); docs-only, CI unaffected.

---

## Cross-cutting — Codecov `codecov/project` promotion (rides on PR #1)

**State (verified 2026-06-01):** both rulesets currently require only
`{"context":"build"}`. Develop (`16993554`) `strict_required_status_checks_policy:
false`; main (`16948699`) `true`. `codecov/project` has **never posted** on any
observed PR — only the informational `codecov/patch`. CI has **zero path filters**, so
PR #1 (docs-only) still runs the full emulator + JaCoCo + Codecov upload.

**Procedure:**

1. After PR #1's CI completes, `gh pr checks <#1>` → **confirm `codecov/project` is
   green** (expected: docs-only ⇒ no source delta ⇒ no coverage regression vs. the
   ~85.93% baseline).
2. For the **develop** ruleset `16993554` only (this session): `gh api
   repos/DocGerd/pantry-tracker/rulesets/16993554` → in the full ruleset JSON, append
   `{"context":"codecov/project"}` to `rules[].parameters.required_status_checks`
   (keeping `{"context":"build"}`), preserving `strict_required_status_checks_policy:
   false`, `do_not_enforce_on_create: false`, and the sibling `deletion` /
   `non_fast_forward` / `pull_request` rules → `PUT … --input <file>`.
3. Verify: re-GET the ruleset; confirm `required_status_checks` now lists `build` +
   `codecov/project`; confirm PR #1 still shows the (now-required) check green.

**Main ruleset deferred (deliberate divergence from #219's note):** `codecov.yml` is
on `develop` only — **not yet on `main`** (main is at v1.3.1, pre-#219). Promoting
`codecov/project` to required on `16948699` now risks **deadlocking the next
`release/* → main` PR** (the check may not post until `codecov.yml` first reaches
main). **Defer** the main promotion to the next release: once `codecov.yml` lands on
main and `codecov/project` is observed green on a main-targeting PR, PATCH `16948699`
the same way. (Record this as the new resume point.)

**Guard:** if `codecov/project` does **not** post green on PR #1 (e.g., the Codecov
repo isn't actually registered, making the upload a silent no-op), **STOP and surface
it** — do not add a required check that never posts. The local
`:app:jacocoTestCoverageVerification` LINE-0.80 step in the `androidTest` job remains
the self-hosted mirror meanwhile.

The tracking doc + CHANGELOG already say `codecov/project` is "added as a required
check once it first reports green," so no doc change is needed beyond confirming.

---

## Track B — #224 docs-delivery decision (PR #2)

### B1. Time-boxed MkDocs Material pilot (throwaway branch)

- Branch off `develop` (throwaway — discarded after the pilot regardless of outcome).
- `pip install --user mkdocs-material` — Linux-native pip, **no `sudo`**, **no
  `/mnt/c` WSL-boundary crossing**. Transient toolchain; not committed unless the
  decision is "go".
- Minimal `mkdocs.yml`: `docs_dir: docs/`, `theme: material`,
  `markdown_extensions: [pymdownx.superfences (mermaid custom_fence)]`, nav covering
  the arc42 set.
- **Verify empirically:** (a) MkDocs consumes the existing arc42 `.md` with minimal
  refactor; (b) **Mermaid renders** (test against #223's merged diagrams); (c)
  full-text **search** works; (d) the Actions deploy would be cheap. Capture findings
  (notes; screenshots optional).

### B2. ADR-0007 — the decision

Author `docs/adr/0007-<slug>.md` mirroring ADR-0006's structure (Status / Context /
Decision / Consequences [Positive/Negative] / References). Record the **go/no-go
decision + rationale** against the issue's five criteria (audience & discovery;
maintenance budget; source of truth; dependency constraints; versioning), grounded in
the pilot findings. The pilot **informs** the decision; it does not predetermine it.
Hard constraints feeding the rationale: GOVERNANCE pins `docs/` as the canonical
source of truth (a site must remain a *render*); the repo has **zero Python/Node
toolchain** (~46 tracked `.md` files); GitHub already renders Mermaid natively.

### B3. Branch on outcome

- **No-go:** make the README docs entry points prominent (the new `docs/README.md`
  from A2 is the in-repo nav win); discard the pilot branch.
- **Go:** file a follow-up implementation issue (toolchain + `gh-pages` deploy
  workflow + `docs/` → site mapping). The pilot branch's `mkdocs.yml` informs it.

### B4. Decouple OpenSSF criteria

ADR-0007 explicitly notes that a docs site closes **neither** `documentation_roadmap`
(a bestpractices.dev form edit — `ROADMAP.md` already exists) **nor** `assurance_case`
(a separate writing task). Do not justify a site on these.

### B5. Acceptance (issue #224)

Decision recorded as ADR-0007 naming the chosen option + rationale; **if go**, a
follow-up implementation issue filed; **if no-go**, README docs entry points made
prominent.

---

## Sequencing summary

1. **PR #1** (Track A): branch `docs/223-refresh-diagrams` off `develop`; implement
   A1–A5; open PR (`Closes #223`, copy labels/milestone from the issue); multi-agent
   review → fix → resolve.
2. **Codecov:** on PR #1 green, confirm `codecov/project`, PATCH develop ruleset
   `16993554`, verify. → hand off PR #1 to human.
3. **(Human merges PR #1.)**
4. **PR #2** (Track B): MkDocs pilot (throwaway branch) → ADR-0007 → branch
   `docs/224-pages-eval` off `develop` carrying ADR-0007 (+ README prominence if
   no-go); open PR (`Closes #224`); multi-agent review → fix → resolve → hand off.
5. **(Human merges PR #2.)**
6. **Memory:** update the resume point — develop codecov/project required; **main
   promotion still pending the next release PR**.

## Risks & mitigations

- **`codecov/project` never posts** (Codecov repo unregistered / silent-no-op upload):
  promotion guard STOPs and surfaces; the local 0.80 JaCoCo gate remains the mirror.
- **Main-ruleset release deadlock:** avoided by deferring the main promotion (above).
- **Mermaid syntax / GitHub rendering:** conservative syntax + human eyeball in the PR
  + #224 pilot cross-check.
- **MkDocs pilot toolchain:** `pip --user`, throwaway branch, nothing committed unless
  "go" — no lasting toolchain footprint, no WSL-boundary/sudo violation.
- **security-posture content accuracy:** the cosign/SLSA-retirement touch (A1) keeps
  the doc truthful rather than a misleading date-bump.

## Out of scope

- Publishing docs to a live site **in this work** (that is #224's *decision*; any
  "go" build is a separate follow-up issue).
- The `assurance_case` document (a separate OpenSSF writing task).
- The bestpractices.dev **form** edit for `documentation_roadmap` (only the tracked-doc
  note is touched here).
- Screenshot **capture** (deferred to the A5 tracking issue).
- The **main** ruleset codecov promotion (deferred to the next release PR).
