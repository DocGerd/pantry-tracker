---
name: pr-review
description: Standard Pantry Tracker multi-agent PR review cycle using the GitHub MCP — dispatch toolkit agents, post inline findings as a batched review, fix every finding, verify, hand off to human for merge, capture lessons. Use when about to open or resume review on a PR.
---

# PR Review — standard cycle

Codifies the always-pr-review workflow for this repo. Reads as a 7-step recipe
that future-Claude can follow without re-deriving the GitHub-MCP tool names,
the forbid list, or the project-specific gotchas each session.

## Preconditions

- PR exists and has a linked issue in the body — `Closes #N` if the PR fully
  resolves the issue, `Refs #N` only if it's a partial step. If no issue
  exists yet, create one first via
  `mcp__plugin_github_github__issue_write` method=create.
- Working tree clean on the PR's branch.
- GitHub MCP loaded (`mcp__plugin_github_github__*` available via ToolSearch).
  If not, ask the user to install/enable it before proceeding — falling back
  to `gh` CLI bypasses the resolve-thread native call and reintroduces the
  bash `!`-mangling problem.

## Steps

### 1. Fetch PR details + diff

Pull what the review subagents need to reason about the change.

- `pull_request_read` method=get        → metadata, head SHA, mergeable state
- `pull_request_read` method=get_diff   → unified diff
- `pull_request_read` method=get_files  → file-level summary (use for large PRs)

### 2. Dispatch the multi-agent review

**Default: invoke the `pr-review-toolkit:review-pr` skill.** It dispatches the
full specialist set (code-reviewer, silent-failure-hunter, pr-test-analyzer,
type-design-analyzer, comment-analyzer, code-simplifier) in parallel against
the PR diff.

**Lightweight alternative** for one-file or mechanical PRs (docs-only renames,
trivial config bumps, single-line fixes) — dispatch these three in one
message, in parallel:

- `pr-review-toolkit:code-reviewer`        — correctness + conventions
- `pr-review-toolkit:silent-failure-hunter` — error handling + security smells
- `pr-review-toolkit:code-simplifier`      — style, clarity, dead abstractions

Skip the full toolkit only when the implementer self-report is thorough and
the diff is auditable in one read (see the
`feedback_subagent_review_proportionality` memory).

### 3. Consolidate findings as one batched inline review

Each finding becomes one inline comment on the diff, all under a single
review submission. The pending-review pattern lets you batch.

a. Create the pending review (no `event` → stays pending):

   `pull_request_review_write` method=create  owner=… repo=… pullNumber=…

b. For each finding, add one inline comment to the pending review:

   `add_comment_to_pending_review`
     path=<file>
     line=<line>                      (last line if multi-line)
     startLine=<line>                 (only for multi-line ranges)
     subjectType=LINE
     side=RIGHT
     body=<finding text + suggested fix>

   Body convention: one short paragraph stating the issue, a fenced suggested
   diff or fix snippet, and (if relevant) the project gotcha it maps to
   (e.g. "runCatching swallows CancellationException").

c. Submit the pending review:

   `pull_request_review_write` method=submit_pending
     event=REQUEST_CHANGES            (use COMMENT only if every finding is a nit)
     body=<one-line summary listing how many findings, grouped by severity>

d. Capture the thread node IDs for the resolve step:

   `pull_request_read` method=get_review_comments

   Returns each thread's `id` (e.g. `PRRT_kwDOxxx`), `isResolved`, and the
   comments it contains. Record `{path:line → threadId}` mappings.

### 4. Fix every finding on the same branch

No "won't fix" without explicit user OK. Cluster related fixes per commit;
reference the finding in the commit message body (helps the audit trail).

After each fix lands and is pushed, resolve the matching thread:

   `pull_request_review_write` method=resolve_thread  threadId=PRRT_kwDOxxx

The MCP exposes this natively — no GraphQL plumbing, no Python-subprocess
workaround for bash `!`-mangling. Resolving an already-resolved thread is a
no-op, so it's safe to re-run in a loop.

### 5. Verification gate

Invoke `superpowers:verification-before-completion` to enforce
evidence-before-assertion, then run the project gates:

- `./gradlew :app:detekt`
- `./gradlew :app:lint`
- `./gradlew :app:test`
- `pull_request_read` method=get_check_runs → CI green on the PR's head SHA

For HTTP-client / header / response-policy changes: real-device UAT per
[`docs/uat/v1-uat-checklist.md`](../../../docs/uat/v1-uat-checklist.md) is
non-negotiable. JVM tests with `MockEngine` cannot reproduce CDN-specific
behavior like chunked transfer encoding — see the v1.1 OFF-chunked-response
incident.

### 6. Ready-to-merge handoff

Once all findings are resolved (every thread `isResolved=true`), all gates
green, and CI green on the head SHA:

a. Post a one-line APPROVE summary:

   `pull_request_review_write` method=create
     event=APPROVE
     body="Ready to merge — N findings resolved, CI green."

b. STOP. The human clicks "Merge pull request".

⚠ **Claude MUST NOT invoke any of the following — no exceptions, no
one-line-revert hotfixes, no ambiguous "do the rest" instructions:**

- `mcp__plugin_github_github__merge_pull_request`  (MCP merge)
- `gh pr merge`                                     (gh CLI merge)
- `git push origin main`                            (direct push to main)

The bash hook at `.claude/hooks/block-dangerous-bash.sh` (added in #64) blocks
the two `git`/`gh` shell-based vectors, but does NOT cover the MCP merge call
— this skill's forbid list is the only gate for that vector. When in doubt,
ASK before any merge-adjacent action.

### 7. After merge, capture lessons

Once the human has merged, invoke `claude-md-management:revise-claude-md` to
land any new lessons into CLAUDE.md.

Apply the eviction criterion already in CLAUDE.md — only land lessons that
are NOT already encoded in one of:

- a CI workflow rule
- a detekt or lint rule
- a pre-commit / pre-push hook
- a load-bearing doc (SHIPPING.md, UAT checklist, etc.)

If the lesson is already encoded somewhere that gates the relevant action, a
CLAUDE.md entry is duplication — skip it.

## Project-specific gotchas worth surfacing in findings

- **`Closes #N` vs `Refs #N`**: only `Closes` auto-closes the linked issue on
  merge. Bitten on PRs #47, #49, #50 — all used `Refs`, all required manual
  issue closure.
- **detekt list-key overrides**: in `detekt-config.yml`, any list-valued field
  (`excludes`, `ignoreNumbers`, `comments`, `ignoreAnnotated`, …) REPLACES the
  bundled default — re-list every default entry first or the rule silently
  starts firing on test paths.
- **`runCatching` in suspend code**: swallows `CancellationException`. Use
  `try/catch (CancellationException) { throw }` then catch the broader type
  — otherwise a cancelled `viewModelScope` job can still race a state write.
- **Compose androidTest `assert()`**: bare `assert()` is a no-op on ART, so a
  failing assertion silently passes. Use `org.junit.Assert.*` or the Compose
  test-rule assertions (`assertExists()`, `assertIsDisplayed()`).
- **Control characters in Kotlin source**: write them as `\uXXXX` escapes,
  not literal control bytes. Editors and the Write tool faithfully persist
  whatever byte landed there.
- **HTTP / header / response-policy changes**: real-device UAT non-negotiable
  (MockEngine doesn't emit chunked encoding).

## Why this skill exists

The PR review cycle has been documented in three places — CLAUDE.md prose,
the `post-finding` skill (gh-api-based, now superseded by the MCP path for
this workflow), and accumulated memory feedback. Each session re-derived the
exact tool calls and re-discovered the project gotchas.

This skill collapses that into one entry point. The GitHub MCP eliminates
the previous biggest source of friction (GraphQL resolveReviewThread via
Python subprocess to dodge bash `!`-mangling) and adds a new governance gap
(MCP-based merge bypasses the bash hook) — both are addressed inline above.
