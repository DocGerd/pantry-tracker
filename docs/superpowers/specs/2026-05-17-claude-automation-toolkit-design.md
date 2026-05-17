# Milestone 2.85 — Claude Code Automation Toolkit — Design Spec

**Status:** Approved 2026-05-17 (user picked "one umbrella issue with checklist" + "in-repo for other contributors" for all 6 items)
**Tracking issue:** [#20](https://github.com/DocGerd/pantry-tracker/issues/20)

## Goal

Codify the working agreements established across M0–M3 (always-pr-review workflow, hook discipline, the specific bug classes we've already hit twice) into project-scoped Claude Code automation. Every contributor who clones the repo — including future Claude sessions — gets the same guardrails by default, without depending on the user's `~/.claude/CLAUDE.md`.

## Non-goals

- **Global (`~/.claude/`) versions of these.** All six items live in-repo so a fresh clone is fully configured. Repo-level `.claude/settings.json` additively layers on top of any user globals.
- **MCP servers.** The recommender suggested `context7` (live docs) and the GitHub MCP. Defer until a concrete need surfaces; M4 may be the trigger but doesn't require them.
- **`disable-model-invocation` on the skills.** Neither skill has destructive side effects on its own (`milestone-start` just writes files + creates a branch; `post-finding` posts a review thread, which is reversible). Both Claude and user invocation paths stay open.
- **Pre-commit Git hooks (e.g., installing detekt as a local git hook).** Out of scope — CI already runs detekt; a local hook would slow committers without adding signal we don't already have.
- **Settings.json schema validation in CI.** YAGNI for a 2-hook config.

## Architecture

All artifacts live under `.claude/` in the repo. Hooks are configured via `settings.json`; skills and subagents are markdown files Claude Code auto-discovers.

```
.claude/
├── settings.json                            (NEW — hooks config, committed)
├── settings.local.json                      (EXISTING — gitignored personal prefs, unchanged)
├── hooks/
│   ├── block-lockfile-edits.sh              (NEW — PreToolUse Edit/Write guard)
│   └── block-dangerous-bash.sh              (NEW — PreToolUse Bash guard)
├── skills/
│   ├── milestone-start/SKILL.md             (NEW)
│   └── post-finding/SKILL.md                (NEW)
└── agents/
    ├── kotlin-coroutines-reviewer.md        (NEW)
    └── android-test-environment-reviewer.md (NEW)
```

The two hook scripts are extracted to standalone `.sh` files (not inlined into `settings.json`) so they can be tested locally with `bash -n`, run by hand to inspect output, and edited without escaping JSON. The skill content lives entirely in `SKILL.md` (no companion scripts) — both skills are pure instruction-driven workflows that Claude executes via existing tools.

## Tool decisions

### A1. Hook — block edits to `app/gradle.lockfile`

- **Trigger:** `PreToolUse` matching `Edit|Write|MultiEdit`.
- **Logic:** the hook receives the tool input on stdin as JSON; if `file_path` ends with `app/gradle.lockfile`, exit with code 2 and a message explaining that Security CI regenerates the file on every run (`./gradlew --no-daemon :app:dependencies --write-locks`) so manual edits silently drift the OSV-Scanner gate.
- **Why script not config-only:** Claude Code hooks support shell commands inline, but a script keeps the regex + message editable and shell-testable.
- **Bypass:** if a contributor genuinely needs to commit a locked version (rare — e.g., demonstrating reproducibility), they can delete the hook block from their local `.claude/settings.local.json` for the session. The repo-shipped config stays strict.

### A2. Hook — block `--no-verify` / `--force` / `--force-with-lease` in Bash

- **Trigger:** `PreToolUse` matching `Bash`.
- **Logic:** read `command` from stdin JSON; if it contains any of the three flag patterns (as standalone tokens — not e.g. `--no-verify-ssl` for curl), exit with code 2.
- **Patterns matched:** `--no-verify`, `--force`, `--force-with-lease`, `-f` after `git push`, `git commit -n`. Carefully word-bounded so `find -f` (which doesn't exist but `find -follow` does) and `mv -f` are NOT blocked — we're targeting Git footguns, not all `-f`.
- **Codifies:** the global rule from `~/.claude/CLAUDE.md` so contributors without that file are still protected. Documents which exact flags are blocked in the rejection message.

### B1. Skill — `milestone-start`

- **Invocation:** `/milestone-start <number> <slug>` (e.g., `/milestone-start 4 scan-to-remove`).
- **What it does:**
  1. Fetches origin/main, switches to a fresh branch named `m<number>-<slug>` (matches M3's `m3-off-lookup`).
  2. Creates `docs/superpowers/specs/YYYY-MM-DD-milestone-<number>-design.md` from a template that prompts the user through brainstorming.
  3. Creates `docs/superpowers/plans/YYYY-MM-DD-milestone-<number>.md` from a template matching the M3 plan structure.
  4. Reminds Claude to invoke `superpowers:brainstorming` next.
- **Frontmatter:** `disable-model-invocation: false` (Claude can also invoke it when context warrants), no `user-invocable: false` (user-typed `/milestone-start` works).
- **What it doesn't do:** create the GitHub umbrella issue — that's a separate decision the user makes after the brainstorming. The skill produces a comment in the spec stub: "Once approved, run `gh issue create` per the milestone-N umbrella issue template (see issue #11/#20 for examples)."

### B2. Skill — `post-finding`

- **Invocation:** `/post-finding <PR#> <file:line> "<finding text>"` (and a separate `/resolve-finding <thread-id>` form, or — to keep the surface small — just document the GraphQL resolve mutation inside the same SKILL.md).
- **What it does:**
  1. Calls `gh api -X POST repos/:owner/:repo/pulls/<PR>/comments` with the right `commit_id` (resolved from `gh pr view`), `path`, `line`, `body` payload to create an inline review thread.
  2. Returns the thread node-id (extracted from response) so the user / Claude can later resolve via the GraphQL `resolveReviewThread` mutation.
  3. Documents the resolve mutation verbatim in the SKILL.md so Claude can run it from memory without re-deriving the GraphQL each time.
- **Codifies:** the always-pr-review workflow (saved in memory `feedback_always_pr_review`) — "post threads with findings; fix all findings and resolve threads."

### C1. Subagent — `kotlin-coroutines-reviewer`

- **Targets:** the exact patterns we've already hit:
  - `runCatching { ... }` in a `suspend` function or inside `viewModelScope.launch { ... }` — swallows `CancellationException` and breaks structured concurrency (M3 round 1 finding).
  - `viewModelScope.launch { dao.write() ; _uiState.update { ... } }` — fire-and-forget where a thrown DB exception silently fails to surface in UI state (M2 finding pattern).
  - Missing `Job` cancellation in `onCleared` / lifecycle hooks where a long-running job is started.
  - `catch (e: Exception)` in suspend code that doesn't re-throw `CancellationException`.
- **Model:** standard (sonnet — pattern recognition over deep reasoning).
- **Frontmatter:** describes when to invoke proactively ("after writing code involving viewModelScope.launch, suspend functions, or coroutine error handling").

### C2. Subagent — `android-test-environment-reviewer`

- **Targets:**
  - `android.util.Log` / `android.os.*` / any `android.*` API used from a plain JVM unit test (not `@RunWith(RobolectricTestRunner::class)`) — fails at runtime with `"Method X not mocked"` (M3 OffApiClient finding).
  - Robolectric `@Config(sdk = [...])` mismatch against `compileSdk` / `targetSdk` in `app/build.gradle.kts`.
  - Missing `Dispatchers.setMain` / `resetMain` in tests that use `viewModelScope` or `runTest`.
  - Use of `Thread.sleep` / `runBlocking` in coroutine tests where `runTest` + `advanceTimeBy` is correct.
  - `@Test` methods that mutate shared static state without `@Before`/`@After` reset.
- **Model:** standard.
- **Frontmatter:** describes proactive invocation ("after writing JVM unit tests for Android code, especially anything touching ViewModel, Repository, or data-layer code that might use android.* APIs").

## Acceptance

- A fresh clone of the repo, opened in Claude Code, surfaces `/milestone-start` and `/post-finding` in the skill list, and the two reviewers in the agent list.
- Attempting `Edit` on `app/gradle.lockfile` is blocked with a message naming the regeneration step.
- Attempting `git commit --no-verify -m "x"` in a Bash call is blocked with a message listing the three forbidden flags.
- The next milestone PR's multi-agent review (M4) dispatches both new reviewers in parallel alongside the existing five.
- Both hook scripts pass `bash -n` syntax check and have a documented "what stdin looks like" example at the top so future editors don't have to guess.

## Testing strategy

The toolkit IS the test infrastructure for M4. Pragmatic verification:

- **Hooks:** unit-test by piping representative JSON to each script in a one-off Bash session — `echo '{"tool_input":{"file_path":"app/gradle.lockfile"}}' | bash .claude/hooks/block-lockfile-edits.sh ; echo "exit=$?"` should print `exit=2`. Document the test commands in the script header comments so they're self-verifying.
- **Skills:** invoke `/milestone-start 99 dry-run` in a throwaway branch, verify the spec + plan stubs land at the expected paths, then delete the branch. (Not gated in CI — this is human-checked once before merge.)
- **Subagents:** verified live during M4's PR review. Plant one known violation each in M4's first commit (deliberately use `runCatching` in a suspend block; deliberately use `android.util.Log.w` in a plain JVM test) and confirm the reviewers flag them. Remove the violations before final merge.

## Why now (before M4)

M4 (Scan to Remove) reuses the camera/coroutine surface that produced the two M3 bugs. Landing the coroutines + Android-test reviewers before M4 means the next PR's review catches the same class of bugs by reflex instead of relying on me to remember. The hooks are pure infrastructure — they cost ~zero ongoing maintenance and immediately enforce the rules I've already committed to.
