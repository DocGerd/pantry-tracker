# Governance

This document describes how decisions are made in **Pantry Tracker** and who is
responsible for what. It is deliberately honest about the project's size:
Pantry Tracker is a small, single-maintainer hobby app for tracking a household
kitchen pantry, not a multi-stakeholder foundation project. The model below
reflects that reality rather than borrowing ceremony the project does not need.

## Model

Pantry Tracker follows a **single-maintainer (BDFL-style)** model. The
maintainer is the final decision-maker on scope, design, and what ships. This is
not a committee or a voting body; it is one person maintaining a focused app,
with a transparent, written process so that decisions and their rationale are
visible to anyone reading the repository.

The maintainer commits to keeping that process transparent: every change is
tracked by a public issue, lands through a reviewed pull request, and — for
anything that shapes the system — is recorded in the
[arc42 architecture docs](docs/architecture/).

## How decisions are made

- **Proposing a change.** Anyone may open an issue (bug report, feature
  request, or question) using the templates in
  [`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/). Discussion happens on the
  issue.
- **Accepting a change.** Work is done on a short-lived
  `<type>/<tracker-id>-<slug>` branch off `develop`, opened as a pull request
  into `develop` that links its issue with `Closes #N`. Every PR is reviewed
  before it merges (see [Code review in `CONTRIBUTING.md`](CONTRIBUTING.md#code-review));
  the maintainer is the only approver and merger. The full branching model is
  **GitFlow**, documented in
  [`CONTRIBUTING.md`](CONTRIBUTING.md#workflow-gitflow).
- **Architectural decisions.** Choices that shape the system are written up in
  the arc42 docs in [`docs/architecture/`](docs/architecture/), with the design
  rationale captured alongside the design specs under
  [`docs/superpowers/specs/`](docs/superpowers/specs/), so the *why* behind a
  decision outlives the conversation that produced it.
- **Disagreements.** Raise them on the relevant issue or PR thread. The
  maintainer makes the final call and records the reasoning in the thread or the
  architecture docs.

## Branch and merge model

Pantry Tracker follows **GitFlow** with two long-lived branches: `develop` is
the integration branch and the repository default, and `main` is release-tagged
and production. Neither is ever pushed to directly — all work lands via pull
request. Day-to-day `<type>/<tracker-id>-<slug>` branches (`feat`, `fix`,
`chore`, `docs`, `security`) are cut off `develop` and PR'd back into `develop`.
A `release/<version>` branch is cut off `develop` and PR'd into **both** `main`
and `develop`; `main` is then tagged. An urgent `hotfix/<slug>` branches off
`main` and is back-merged into `develop`. The full convention lives in
[`CONTRIBUTING.md`](CONTRIBUTING.md#workflow-gitflow); the release procedure is
in [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md).

Branch protection (forward-looking, tracked in #100) covers **both `develop`
and `main`** once the repo is public. Note: the classic
`required_linear_history` rule must **not** be enabled — GitFlow's release flow
PRs `release/*` into both `main` and `develop`, which creates merge commits the
classic rule forbids with no bypass. The aim is a clean first-parent mainline
instead.

### Only humans merge to `develop` and `main`

The project's single hardest rule: **every change reaching `develop` or `main`
must go through a pull request that the human maintainer merges.** No automated
agent — including any Claude Code session working in this repository — runs `gh
pr merge` or pushes to `develop` or `main`, for any reason. There are no
exceptions for one-line reverts, "verified" hotfixes, or wrap-up phases. The
audit trail is the maintainer's explicit click on "Merge pull request"; once the
repository is public, branch protection on both `develop` and `main` enforces
this mechanically (require a PR, require review, block direct pushes). This rule
is restated in [`CLAUDE.md`](CLAUDE.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Key roles and current holders

For a single-maintainer project these roles are all currently held by the same
person; they are listed separately so the *responsibilities* are explicit and so
the list stays meaningful if the project ever grows.

| Role | Responsibility | Current holder |
|---|---|---|
| **Maintainer / project lead** | Final say on scope, design, and releases; reviews and merges all PRs. | [@DocGerd](https://github.com/DocGerd) |
| **Security contact** | Receives and triages vulnerability reports; see [`SECURITY.md`](SECURITY.md). Reports go through GitHub [private security advisories](https://github.com/DocGerd/pantry-tracker/security/advisories/new). | [@DocGerd](https://github.com/DocGerd) |
| **Release manager** | Cuts releases via the GitFlow `release/*` flow (off `develop`, PR'd into `main` + `develop`, then tags `main`), builds the signed APK, and publishes the GitHub Release (see [`docs/release/SHIPPING.md`](docs/release/SHIPPING.md)). | [@DocGerd](https://github.com/DocGerd) |
| **Code-of-Conduct enforcement** | Handles conduct reports per [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md). | [@DocGerd](https://github.com/DocGerd) |

## Becoming a maintainer

There is no formal maintainer-promotion process today because the project does
not yet need one. If Pantry Tracker attracts sustained contribution, the
maintainer will revisit this document to add additional maintainers and a shared
decision process at that point. Until then, the fastest way to influence
direction is a well-argued issue or a clean pull request.

## Continuity & succession

Pantry Tracker is single-maintainer today (bus factor = 1 — the same structural condition the Scorecard `Contributors` check surfaces, accept-risk per `docs/security-posture.md`). `access_continuity` is satisfied by a *documented plan*, not by headcount.

**Trigger.** The continuity plan applies if the maintainer is unresponsive on issues or security advisories for **8 consecutive weeks**.

**How the project continues.** The repository is public and Apache-2.0, so anyone may fork and continue without permission. A fork picking up maintenance should: (1) branch from the latest `develop`; (2) treat the GitHub issue tracker and `docs/` at the fork point as canonical; (3) **rotate signing keys** — the lifetime release signing cert (SHA-256 `ec9a4bb8…b3d9`) cannot move to a fork, so a continuation must issue updates under a new key using Android's v3 signature scheme key-rotation (existing users will need a clean install across the key boundary; document this in the fork's release notes).

**Out-of-band contact.** The private security channel in `SECURITY.md` is the way to reach the maintainer if GitHub itself is the blocker.

**What is canonical for handoff.** The `main` branch, the tagged releases, and the `docs/` tree (architecture, security-posture, release runbook) constitute the authoritative project state.

## Related documents

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — how to contribute, the GitFlow
  workflow, PR requirements, and the code-review process.
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) — expected behaviour and how to
  report conduct issues.
- [`SECURITY.md`](SECURITY.md) — how to report a vulnerability.
- [`CLAUDE.md`](CLAUDE.md) — the operational guide (workflow, tooling,
  project-local configuration).
