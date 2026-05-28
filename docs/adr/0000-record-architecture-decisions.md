# 0000. Record architecture decisions

## Status

Accepted — 2026-05-28

## Context

The project already has a substantial body of design decisions documented in
prose. The canonical locations are:

- [`docs/architecture/09-architecture-decisions.md`](../architecture/09-architecture-decisions.md)
  — short-form ADRs embedded in the arc42 document (numbered ADR-001 …
  ADR-010).
- [`docs/architecture/`](../architecture/) — the rest of the arc42 sections
  (introduction & goals, system scope, building blocks, runtime view,
  cross-cutting concepts, etc.) which carry rationale woven into the prose.
- [`docs/superpowers/specs/`](../superpowers/specs/) — dated per-milestone /
  per-feature design specs (the kitchen-inventory v1 spec, v1.1 fallbacks &
  undo, the open-source-repo design, etc.).

That structure has worked well during private-repo development, but it has
two problems as the project goes public:

1. **Discoverability.** A reader landing on the repo cold has no
   "decisions index" — the architecture document is the closest thing, but
   it's organised around arc42 sections, not around discrete decisions.
2. **Immutability + audit trail.** Decisions captured in prose tend to be
   edited in place when the surrounding paragraph is revised. The git history
   carries the audit trail, but it's not easy to ask "what did we decide
   about X, and when?".

This ADR establishes that going forward — and retroactively for the big
load-bearing decisions — the project will capture architecture decisions as
Architecture Decision Records (ADRs) in this directory.

## Decision

1. **Format.** Use the [Michael Nygard ADR format][nygard]: one decision per
   file, with sections `Status`, `Context`, `Decision`, `Consequences`, and
   `References`. The template lives at [`template.md`](template.md).
2. **Numbering.** ADRs are numbered sequentially starting at `0000`. The
   number is part of the filename and the document title (`NNNN-kebab-case-
   slug.md`). Numbers are never reused, even if an ADR is later superseded.
3. **Immutability.** Once an ADR is in `Accepted` status, its body is
   immutable except for typo fixes and clarifying edits that do not change
   the decision. If a decision is reversed or revised, write a **new** ADR
   that supersedes the old one; add a `Status` line on the old ADR pointing
   forward (e.g. `Superseded by 00NN-…`).
4. **Scope.** Use an ADR for any decision that:
   - constrains future code (a framework choice, a library choice, a
     persistence layer, a build-tool flag with security or behaviour
     consequences); or
   - is non-obvious and warrants explaining (a deliberate non-choice — "we
     are NOT using Hilt"); or
   - cuts across the whole codebase rather than living inside a single class.
   Smaller per-feature decisions stay in the design spec under
   `docs/superpowers/specs/`.
5. **Relationship to the arc42 document.** The arc42 §9 short-form ADRs
   (ADR-001 … ADR-010) remain — they predate this directory and continue to
   serve as the in-document quick reference. New ADRs land here. The first
   batch of ADRs in this directory (`0001` … `0006`) backfills six
   load-bearing decisions that were previously documented only in prose.

[nygard]: https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions

## Consequences

**Positive.**

- One canonical, discoverable index of architecture decisions.
- Per-decision git history — `git log docs/adr/0003-…` shows exactly when a
  decision was made and how it was amended, independent of changes to
  surrounding prose.
- Immutability rule + supersede chain makes it explicit when a decision has
  been reversed, rather than the reader silently reading a half-edited
  paragraph.
- Better signal to outside contributors that the project takes decisions
  seriously and records them, which matters now that the repo is public.

**Negative.**

- Small per-decision overhead. Every architecturally-significant choice now
  needs an ADR file, not just a paragraph in the design spec.
- Two-source problem: arc42 §9 still contains the original short-form ADRs
  (kept for now to avoid disrupting the arc42 structure). Readers may need
  to consult both until the arc42 entries are migrated or replaced with
  forward-pointers — a deliberate, low-priority follow-up.
- The "one decision per file" granularity sometimes forces a judgement call
  on what counts as one decision (e.g. "use Room" and "exported Room schema"
  could be one ADR or two). The bias is one ADR per decision the reader
  would search for independently.

## References

- Michael Nygard, [_Documenting Architecture Decisions_](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions),
  2011 — the format and rationale.
- [`docs/architecture/09-architecture-decisions.md`](../architecture/09-architecture-decisions.md)
  — the existing arc42 short-form ADRs, still the canonical source for
  ADR-001 … ADR-010 until those are migrated.
- [`docs/superpowers/specs/2026-05-27-open-source-repo-design.md`](../superpowers/specs/2026-05-27-open-source-repo-design.md)
  — open-source repo design spec; this ADR backfill is OSS-12 in that spec's
  milestone "Open Source: In the Open".
- GitHub issue [#104](https://github.com/DocGerd/pantry-tracker/issues/104)
  — tracking issue for the ADR-directory introduction + backfill.
- [`template.md`](template.md) — the template used for every new ADR.
