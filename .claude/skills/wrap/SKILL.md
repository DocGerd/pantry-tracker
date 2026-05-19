---
name: wrap
description: Wrap up a session with a standardized handover — summarize completed work, fold any new lessons into the project CLAUDE.md, write/update HANDOVER.md (current branch, open PRs, next steps, blockers), verify a clean working tree, then report final status. Use when finishing a work session, before context compaction, when handing off to another agent or instance, or when the user says "wrap up", "we're done for today", "hand off", or similar end-of-session cues.
---

# Wrap

1. Summarize work this session — PRs opened (Claude never merges to `main`; the human does), issues closed, commits landed.
2. Fold any durable lessons into the **project** `CLAUDE.md` at the repo root (not `~/.claude/CLAUDE.md`). Prefer the `claude-md-management:revise-claude-md` skill.
3. Write/update `HANDOVER.md` at the repo root with: current branch, open PRs, next steps, blockers. Cross-reference the CLAUDE.md entries from step 2 rather than restating them.
4. Confirm clean working tree via `git status`. If dirty, list uncommitted/untracked files and refuse to declare the wrap complete — let the user decide commit / stash / discard.
5. Report final status to the user.
