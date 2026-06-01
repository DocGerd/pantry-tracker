# 0007. Keep documentation as markdown-in-repo (no docs site)

## Status

Accepted — 2026-06-01

(Resolves the time-boxed evaluation in issue
[#224](https://github.com/DocGerd/pantry-tracker/issues/224); the content a site
would have rendered was refreshed separately in
[#223](https://github.com/DocGerd/pantry-tracker/issues/223) / PR #226.)

## Context

The project's documentation is ~46 tracked markdown files under `docs/` (arc42 12-section
architecture, ADRs, security posture, the SHIPPING release runbook, UAT), rendered
natively by GitHub and linked from `README.md` / `CONTRIBUTING.md` / `GOVERNANCE.md`.
[`GOVERNANCE.md`](../../GOVERNANCE.md) §"What is canonical for handoff" pins the
`docs/` tree as the authoritative project state, so any published site must remain a
**render** of the repo, never a fork of canonical content.

The genuine gap with the status quo is **external discoverability** (the docs are not
indexed for non-GitHub search and there is no site nav/search/breadcrumbs); the cost of
a site is **tooling + maintenance** on a solo-maintained, fast-cadence Kotlin/Android
repo that currently has **zero Python/Node toolchain**. #224 mandated deciding this
with a time-boxed evaluation rather than an upfront commitment.

We ran a time-boxed **MkDocs Material** pilot (the leading technical-docs option;
favored over blog-oriented Jekyll and enterprise-scale Docusaurus) in a throwaway
venv pointed at the existing `docs/` tree. Findings:

- **Mermaid: works.** All diagram fences render as `class="mermaid"` divs; Material
  bundles `mermaid@11` — the same engine family GitHub uses, so diagrams render
  identically in both.
- **Search: works.** A 720-entry full-text index builds with zero configuration.
- **Build: fast and clean** (~1.4 s; isolated venv, no `sudo`, no system pollution).
- **arc42 consumption is *not* low-friction.** A non-strict build emitted **37
  broken cross-tree links**: the docs deliberately link *outward* to repo-root files —
  `CHANGELOG.md`, `app/build.gradle.kts` and `.kt` source, `SECURITY.md`,
  `.github/workflows/`, `scripts/` — which a `docs_dir: docs` site can neither resolve
  nor include. Shipping a clean site would require rewriting those links to absolute
  GitHub URLs (brittle) or adding `gen-files`/monorepo tooling (ongoing maintenance).

So MkDocs is technically capable, but the existing docs are deeply interwoven with the
repo root, and the GOVERNANCE "render-not-fork" constraint is precisely what the 37
cross-tree links would fracture.

## Decision

We keep documentation as **markdown-in-repo**, rendered by GitHub, and do **not** adopt
a docs site at this time.

1. No static-site generator, `mkdocs.yml`, or `gh-pages` deploy workflow is added.
   No Python/Node toolchain is introduced.
2. In-repo discoverability is the supported path: the
   [`docs/README.md`](../README.md) index (added in #223) is the documentation entry
   point, and `README.md` surfaces the documentation entry points prominently.
3. This decision is **reversible**: if reaching non-GitHub readers ever becomes a real,
   evidenced need, a future ADR supersedes this one and a docs site is implemented as a
   render of `docs/` (the pilot showed Mermaid + search work; the cross-tree links are
   the work item to budget).

This decision is **not** justified by — and does not affect — two OpenSSF criteria that
are sometimes conflated with "publish the docs": `documentation_roadmap`
(`ROADMAP.md` already exists; the criterion is gated on a bestpractices.dev *form*
link, not a site) and `assurance_case` (a separate writing task, `docs/security/`).
Both are closed by content, independently of delivery medium.

## Consequences

**Positive.**

- Zero new toolchain or CI surface on a solo, fast-cadence repo — nothing extra to keep
  green, patch, or pin.
- Single source of truth preserved: every doc change is PR-reviewed in the same flow as
  code, and the GOVERNANCE "render-not-fork" constraint can't be violated by a drifting
  site.
- No cross-tree link maintenance: the docs keep linking directly to source/config files
  at the repo root (CHANGELOG, build files, workflows) — exactly the links a docs-only
  site breaks.
- Mermaid already renders on GitHub (proven in #223), so the headline "diagrams as a
  picture" benefit is already realized with no site.

**Negative.**

- **No external discoverability.** The docs remain indexed only via GitHub; a search
  engine or a reader off the repo won't find them through a docs site. This is the
  accepted cost — judged acceptable for a single-user, sideload-APK project whose
  realistic audience reads on GitHub.
- **No site nav / search / breadcrumbs.** Navigation is the `docs/README.md` index plus
  GitHub's file tree; there is no full-text site search or cross-page breadcrumb. Long
  arc42 pages rely on GitHub's in-page anchors.
- **Mobile reading is GitHub-mediocre.** Wide tables and code blocks render as GitHub
  shows them, not as a mobile-optimized theme would.
- The decision must be **revisited** if a concrete external-audience need appears;
  the trigger is evidenced demand, not aesthetics.

## References

- Decision issue:
  [#224](https://github.com/DocGerd/pantry-tracker/issues/224) — "evaluate GitHub Pages
  (or a docs site) vs. markdown-in-repo".
- Content the decision governs:
  [#223](https://github.com/DocGerd/pantry-tracker/issues/223) / PR #226 — docs refresh
  + Mermaid diagrams (the material a site would render).
- Design spec:
  [`docs/superpowers/specs/2026-06-01-docs-refresh-diagrams-pages-eval-design.md`](../superpowers/specs/2026-06-01-docs-refresh-diagrams-pages-eval-design.md).
- Canonical-handoff constraint:
  [`GOVERNANCE.md`](../../GOVERNANCE.md) §"What is canonical for handoff".
- In-repo discoverability entry point: [`docs/README.md`](../README.md) — the docs
  index added in #223.
- Pilot: MkDocs Material 9.7.6 in a throwaway venv against `docs/` — Mermaid ✅,
  search ✅ (720 entries), 37 broken cross-tree links (ephemeral; not committed).
