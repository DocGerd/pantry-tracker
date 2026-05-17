---
name: milestone-start
description: Scaffold a new milestone — creates branch + spec stub + plan stub matching the M2/M2.5/M3 pattern. Use when starting work on the next milestone from the project issue tracker.
---

# Milestone Start

Scaffold a new milestone branch and seed `docs/superpowers/specs/` + `docs/superpowers/plans/` with stubs that match the established M2 / M2.5 / M3 pattern.

## Inputs

Two positional arguments: `<number> <slug>`.
- `<number>` — milestone number (e.g., `4`, `2.85`). Used in branch name and doc titles.
- `<slug>` — kebab-case feature slug (e.g., `scan-to-remove`, `claude-automation-toolkit`).

If invoked without arguments, ask the user for both.

## Steps

1. **Verify clean working tree:**
   ```bash
   git status --porcelain
   ```
   If non-empty, refuse and tell the user to commit or stash first.

2. **Sync main + create branch:**
   ```bash
   git switch main
   git pull --ff-only origin main
   git switch -c "m<number>-<slug>"
   ```
   Branch naming follows M3 (`m3-off-lookup`) and M2.5 (`ci-hardening` was an exception — going forward use `m<number>-<slug>` consistently).

3. **Resolve today's date:**
   ```bash
   date -u +%Y-%m-%d
   ```

4. **Write spec stub** to `docs/superpowers/specs/<date>-milestone-<number>-<slug>-design.md`:

   ```markdown
   # Milestone <number> — <Title from slug, Title Case> — Design Spec

   **Status:** Draft (awaiting brainstorming)
   **Tracking issue:** TBD — create after brainstorming via:
       gh issue create --title "Milestone <number>: <one-line summary>" --body "..."
       (See issues #11 / #20 for umbrella-style templates.)

   ## Goal
   TBD

   ## Non-goals
   - TBD

   ## Architecture
   TBD

   ## Acceptance
   - TBD

   ## Why now
   TBD
   ```

5. **Write plan stub** to `docs/superpowers/plans/<date>-milestone-<number>-<slug>.md`:

   ```markdown
   # Milestone <number> — <Title> — Implementation Plan

   > **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

   **Goal:** TBD (from spec)
   **Architecture:** TBD (from spec)
   **Tech Stack:** TBD

   **Tracking issue:** TBD
   **Spec:** `docs/superpowers/specs/<date>-milestone-<number>-<slug>-design.md`

   ---

   ### Task 1: TBD

   **Files:**
   - Create / Modify / Test: TBD

   - [ ] **Step 1: TBD**
   ```

6. **Tell the user what to do next:**
   > "Branch `m<number>-<slug>` created, spec + plan stubs at `<paths>`. Next step: invoke `superpowers:brainstorming` to refine the design before filling in the spec."

## Why this exists

Setting up a new milestone is a four-step ritual we've done four times (M0, M1, M2, M2.5, M3) — and forgetting one step (e.g., wrong date format, branch off the wrong base) is easy. Skills are exactly the right primitive for repeatable scaffolding.
