# Pantry Tracker — documentation index

A map of the `docs/` tree. Start at the top and follow the reading order.

## Reading order

1. **[Architecture (arc42)](architecture/01-introduction-and-goals.md)** — the
   12-section arc42 set: goals, constraints, context, building blocks, runtime
   views, deployment, crosscutting concepts, decisions, quality, risks, glossary.
2. **[Security posture](security-posture.md)** — the living security overview
   (threat model, data handling, release integrity).
3. **[Security tracking](security/)** — OpenSSF Best-Practices tracking and dated
   security-review notes.
4. **[Release runbook](release/SHIPPING.md)** — how a version is built, signed,
   tagged, and published (the v1 sideload path is §B).
5. **[Architecture Decision Records](adr/)** — the numbered ADRs (start at
   [0000](adr/0000-record-architecture-decisions.md)).
6. **[UAT](uat/)** — the user-acceptance checklist used for release sign-off.
7. **[Design specs & plans](superpowers/)** — dated brainstorming specs and
   implementation plans (the *why* behind recent work).

## Conventions

- Diagrams are **GitHub-rendered Mermaid** fenced blocks (no build step).
- ADRs are append-only once `Accepted`; a reversal is a new ADR with a
  `Superseded by` line on the old one.
- `docs/` is the canonical handoff source of truth (see
  [`GOVERNANCE.md`](../GOVERNANCE.md)).
