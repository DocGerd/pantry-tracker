---
name: post-finding
description: Post an inline PR review thread via gh api, capture the thread node-id, and document the GraphQL resolveReviewThread mutation for later resolution. Use when triaging multi-agent PR review findings.
---

# Post Finding

Post a single review-comment finding as an inline thread on a PR, and capture the thread node-id so it can be resolved after the fix lands.

This codifies the always-pr-review workflow: after the multi-agent review surfaces findings, each is posted as its own inline thread (not bundled into a summary comment), fixed in a follow-up commit, then resolved via GraphQL.

## Inputs

`<PR#> <file>:<line> "<finding text>"`

Example:
```
/post-finding 18 app/.../ScanViewModel.kt:55 "runCatching swallows CancellationException — switch to try/catch (CE) { throw }"
```

## Steps

### Posting the thread

1. **Resolve commit SHA** (the comment must anchor to a specific commit):
   ```bash
   COMMIT=$(gh pr view <PR#> --json headRefOid -q .headRefOid)
   ```

2. **Post the comment** (creates a new review thread anchored to <file>:<line>):
   ```bash
   gh api -X POST repos/:owner/:repo/pulls/<PR#>/comments \
     -f commit_id="$COMMIT" \
     -f path="<file>" \
     -F line=<line> \
     -f side=RIGHT \
     -f body="<finding text>"
   ```
   Capture the response — the `id` field is the comment id; the `pull_request_review_id` and `node_id` matter for the GraphQL step.

3. **Note the thread node-id** in your TODO list / review tracker:
   `Thread <node_id> — <finding text> — STATUS: open`

### Resolving after fix

When the fix is committed (referencing the thread in the commit message helps), resolve the thread:

```bash
gh api graphql -f query='
  mutation($threadId: ID!) {
    resolveReviewThread(input: {threadId: $threadId}) {
      thread { isResolved }
    }
  }' -f threadId="<thread_node_id>"
```

The thread's `node_id` is on the *thread*, not the *comment* — fetch it via:

```bash
gh api graphql -f query='
  query($owner: String!, $repo: String!, $pr: Int!) {
    repository(owner: $owner, name: $repo) {
      pullRequest(number: $pr) {
        reviewThreads(first: 50) {
          nodes { id isResolved comments(first: 1) { nodes { body } } }
        }
      }
    }
  }' -f owner="$(gh repo view --json owner -q .owner.login)" \
     -f repo="$(gh repo view --json name -q .name)" \
     -F pr=<PR#>
```

The `owner` / `repo` are resolved dynamically from `gh repo view` so the skill keeps working after a fork, rename, or org transfer.

Match by the comment body prefix; that node `id` is what `resolveReviewThread` wants.

## Workflow integration

Use after dispatching the multi-agent PR review (`/pr-review-toolkit:review-pr`):
1. Each agent surfaces 0..N findings.
2. For each finding: invoke `/post-finding <PR#> <file:line> "<text>"` → record thread node-id.
3. Make the fix commits (cluster related fixes per commit; reference the threads in commit messages).
4. After CI is green, resolve each thread.
5. Final step: post a summary comment listing thread links + "all resolved, ready to merge".

## Why this exists

The GraphQL `resolveReviewThread` mutation is non-obvious and hard to remember mid-session (`gh api` only exposes it through the raw query interface). The two-step "post a comment to create a thread, then resolve the thread by its node-id" dance has tripped me up multiple times. Documenting it here means future-Claude reads the skill and Just Does The Right Thing instead of re-deriving it.
