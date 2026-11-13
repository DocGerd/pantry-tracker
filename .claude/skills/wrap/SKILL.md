---
name: wrap
description: Wrap up a session with a standardized handover — summarize completed work, propose any new lessons for the project CLAUDE.md, write/update HANDOVER.md (current branch, open PRs, next steps, blockers), verify a clean working tree, then report final status. Use when finishing a work session, before context compaction, when handing off to another agent or instance, or when the user says "wrap up", "we're done for today", "hand off", or similar end-of-session cues.
---

# Wrap

1. Summarize work this session — PRs opened (the human merges every PR into `develop` or `main`; Claude's only exception is a green Dependabot PR into `develop`, merged via the GitHub MCP `merge_pull_request` tool, never from the shell), issues closed, commits landed.
2. Propose any durable lessons for the **project** `CLAUDE.md` at the repo root (not `~/.claude/CLAUDE.md`). Use `/revise-claude-md` in the main session; propose the changes, don't edit `CLAUDE.md` directly.
3. Write/update `HANDOVER.md` at the repo root with: current branch, open PRs, next steps, blockers. Cross-reference the CLAUDE.md entries from step 2 rather than restating them.
4. Confirm clean working tree via `git status`. If dirty, list the uncommitted/untracked files, halt, and tell the user; re-invoke wrap after they've resolved it.
5. Report final status to the user.
