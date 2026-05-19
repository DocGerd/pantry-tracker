---
name: pr-review
description: Standard Pantry Tracker multi-agent PR review cycle using the GitHub MCP — dispatch toolkit agents, post inline findings as a batched review, fix every finding, verify, hand off to human for merge, capture lessons. Use when reviewing, addressing comments on, posting findings to, resolving review threads on, or deciding "ready to merge" for a PR — including phrasings like "review PR #N", "address comments on #N", "is #N ready to merge", "post the review", "resolve the threads".
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
- GitHub MCP available — the `mcp__plugin_github_github__*` schemas are
  deferred; fetch each tool's schema via `ToolSearch select:<name>` (or
  `ToolSearch select:name1,name2,...`) before its first call in a session.
  If the MCP isn't installed, fall back to the older `gh` CLI path
  (`.claude/skills/post-finding/`) — the bash `!`-mangling issue that
  motivated the MCP path is back in scope on that fallback.

## Steps

### 1. Fetch PR details + diff

Pull what the review subagents need to reason about the change.

- `pull_request_read` method=get        → metadata, head SHA, mergeable state
- `pull_request_read` method=get_diff   → unified diff
- `pull_request_read` method=get_files  → file-level summary (use for large PRs)

### 2. Dispatch the multi-agent review

If the user has separately triggered `/ultrareview` (canonical, cloud-billed
— Claude cannot launch it), wait for those findings instead of dispatching
the local toolkit.

Otherwise: **invoke the `pr-review-toolkit:review-pr` skill** as the default.
It dispatches the full specialist set (code-reviewer, silent-failure-hunter,
pr-test-analyzer, type-design-analyzer, comment-analyzer, code-simplifier)
in parallel against the PR diff.

**Lightweight alternative** for one-file or mechanical PRs (docs-only renames,
trivial config bumps, single-line fixes) — dispatch only the relevant subset
of `pr-review-toolkit:*` agents in parallel. Skip the full 6-agent set only
when the diff is small + mechanical AND the implementer self-report calls out
every change; verify diffs yourself instead.

### 3. Consolidate findings as one batched inline review

**Zero-findings shortcut:** if all dispatched reviewers report zero findings,
skip step 3 entirely and proceed directly to step 5 → step 6's APPROVE path.

Otherwise: each finding becomes one inline comment on the diff, all under a
single review submission. The pending-review pattern lets you batch.

a. Create the pending review (no `event` → stays pending):

   `pull_request_review_write` method=create  owner=… repo=… pullNumber=…

b. For each finding, add one inline comment to the pending review:

   `add_comment_to_pending_review`
     owner=<owner> repo=<repo> pullNumber=<n>
     path=<file>
     line=<line>                      (last line if multi-line)
     startLine=<line>                 (only for multi-line ranges)
     subjectType=LINE
     side=RIGHT
     body=<finding text + suggested fix>

   Body convention: one short paragraph stating the issue, a fenced suggested
   diff or fix snippet, and (if relevant) a cross-reference to the CLAUDE.md
   "Things that have bitten past sessions" section.

c. Submit the pending review:

   `pull_request_review_write` method=submit_pending
     event=REQUEST_CHANGES            (or COMMENT — see note below)
     body=<one-line summary listing how many findings, grouped by severity>

   ⚠ **GitHub forbids self-REQUEST_CHANGES.** When reviewing your own PR (the
   common solo-dev case), use `event=COMMENT` — the inline threads still post;
   only the formal "changes requested" status is blocked. Reserve
   `REQUEST_CHANGES` for reviews of other contributors' PRs.

d. Capture thread node IDs for the resolve step.

   The MCP's `get_review_comments` returns thread metadata + comments but
   **not** the thread node ID that `resolve_thread` needs. Fall back to
   GraphQL — call `gh` from Python (bash mangles `!` in GraphQL queries
   even inside quoted heredocs). Key threads by `threadId` (`path:line`
   collides when findings share a line). Paginate via `after=<endCursor>`
   while `pageInfo.hasNextPage` is true.

   ```graphql
   query($owner: String!, $repo: String!, $pr: Int!) {
     repository(owner: $owner, name: $repo) {
       pullRequest(number: $pr) {
         reviewThreads(first: 100) {
           nodes { id isResolved
             comments(first: 1) { nodes { databaseId path line } } } } } } }
   ```

### 4. Fix every finding on the same branch

No "won't fix" without explicit user OK. Cluster related fixes per commit;
reference the finding in the commit message body (helps the audit trail).

After each fix lands and is pushed, resolve the matching thread:

   `pull_request_review_write` method=resolve_thread
     owner=<owner> repo=<repo> pullNumber=<n>   (schema-required even though
                                                 unused for this method;
                                                 omitting → InputValidationError)
     threadId=PRRT_kwDOxxx

Resolving an already-resolved thread is a no-op, so it's safe to re-run.

### 5. Verification gate

Invoke `superpowers:verification-before-completion` to enforce
evidence-before-assertion, then run the project gates documented in
CLAUDE.md §Commands (`./gradlew :app:detekt`, `:app:lint`, `:app:test`) plus:

- `pull_request_read` method=get_check_runs → CI green on the PR's head SHA

For HTTP-client / header / response-policy changes: real-device UAT per
[`docs/uat/v1-uat-checklist.md`](../../../docs/uat/v1-uat-checklist.md) is
non-negotiable. JVM tests with `MockEngine` cannot reproduce CDN-specific
behavior like chunked transfer encoding — see the v1.1 OFF-chunked-response
incident.

### 6. Ready-to-merge handoff

Once all threads are resolved (`isResolved=true`) and CI is green on the
head SHA:

a. Post a one-line APPROVE summary:

   `pull_request_review_write` method=create
     event=APPROVE
     body="Ready to merge — review clean, CI green."          (N=0 path)
       OR
     body="Ready to merge — N findings resolved, CI green."   (N≥1 path)

b. STOP. The human clicks "Merge pull request".

⚠ **Claude MUST NOT cause `main` to advance via any vector.** Per CLAUDE.md's
hard governance rule, this is non-negotiable. The bash hook at
`.claude/hooks/block-dangerous-bash.sh` blocks the shell-based paths it
knows about but cannot reach the MCP. **When in doubt about any
merge-adjacent action, ASK.** Known vectors:

- `mcp__plugin_github_github__merge_pull_request`  (MCP — only Claude observing this list gates it)
- `gh pr merge`                                     (gh CLI — bash hook gates)
- `git push origin main`                            (direct push — bash hook gates)

### 7. After merge, capture lessons

Once the human has merged, invoke `claude-md-management:revise-claude-md` to
land any new lessons into CLAUDE.md. Apply the eviction criterion documented
there — don't duplicate lessons already encoded in a CI rule, lint rule,
hook, or load-bearing doc.

## Cross-reference

When drafting finding bodies, cross-reference the "Things that have bitten
past sessions" section in CLAUDE.md — many findings map to a named gotcha
there (Closes vs Refs, `runCatching`/CancellationException, detekt list-keys,
Compose `assert()`, control chars in Kotlin source, HTTP/real-device UAT).
