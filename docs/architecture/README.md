# Architecture Documentation — Pantry Tracker

This folder is the project's [arc42](https://arc42.org) architecture documentation.
Each numbered file maps to a standard arc42 section.

| # | Section | File |
|---|---------|------|
| 1 | Introduction & Goals | [01-introduction-and-goals.md](01-introduction-and-goals.md) |
| 2 | Architecture Constraints | [02-architecture-constraints.md](02-architecture-constraints.md) |
| 3 | System Scope & Context | [03-system-scope-and-context.md](03-system-scope-and-context.md) |
| 4 | Solution Strategy | [04-solution-strategy.md](04-solution-strategy.md) |
| 5 | Building Block View | [05-building-block-view.md](05-building-block-view.md) |
| 6 | Runtime View | [06-runtime-view.md](06-runtime-view.md) |
| 7 | Deployment View | [07-deployment-view.md](07-deployment-view.md) |
| 8 | Crosscutting Concepts | [08-crosscutting-concepts.md](08-crosscutting-concepts.md) |
| 9 | Architecture Decisions | [09-architecture-decisions.md](09-architecture-decisions.md) |
| 10 | Quality Requirements | [10-quality-requirements.md](10-quality-requirements.md) |
| 11 | Risks & Technical Debt | [11-risks-and-technical-debt.md](11-risks-and-technical-debt.md) |
| 12 | Glossary | [12-glossary.md](12-glossary.md) |

## What lives where

- **High-level overview / why this exists** → section 1, 3, 4
- **What the code looks like inside** → section 5 (static) + section 6 (dynamic)
- **Why specific tools were picked** → section 9 (architecture decisions)
- **Operational gotchas** → section 7 (deployment), 11 (risks)
- **Detailed per-milestone designs** → `docs/superpowers/specs/` (each milestone has its own design spec)
- **Detailed per-milestone implementation plans** → `docs/superpowers/plans/`

The arc42 sections give the always-true overview. The per-milestone specs/plans
are the historical record of how the system got built and the trade-offs made
at each step.
