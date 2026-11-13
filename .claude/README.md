# Project-local Claude Code config

This directory holds **team-shared** [Claude Code](https://docs.claude.com/en/docs/claude-code)
configuration that every contributor inherits automatically by checking out the
repo. It is committed deliberately: the guardrails and tooling here encode the
project's hard rules (only humans merge to `develop` or `main`; never skip hooks or
force-push; never hand-edit the machine-generated lockfile) so a freshly-cloned
checkout gets the same behaviour by default. The operational guide that ties it
all together is [`CLAUDE.md`](../CLAUDE.md) in the repo root.

## What's here

| Path | Status | Purpose |
|---|---|---|
| `settings.json` | committed | Team defaults — the `PreToolUse` hook registrations (see below). |
| `settings.local.json` | **gitignored** | Optional per-contributor / per-session override. |
| `hooks/` | committed | The guard scripts the hooks invoke. |
| `agents/` | committed | Specialised review subagents (see below). |
| `skills/` | committed | End-to-end workflow recipes (see below). |
| `README.md` | committed | This file. |

## The hooks

`settings.json` registers **`PreToolUse`** guards, shared with every
contributor on clone. They are **blocking** safety rails — they exit non-zero to
stop a tool call before it lands, rather than reporting after the fact.

### `hooks/block-lockfile-edits.sh` (Edit / Write / MultiEdit)

Before an edit lands, this hook inspects the target path. The Gradle dependency
lockfile (`app/gradle.lockfile`) is machine-generated and is regenerated from
the resolved dependency graph (`./gradlew :app:dependencies --write-locks`);
hand-editing it produces a snapshot that diverges from what the build actually
resolves. The guard blocks edits to it so the only path to changing the lockfile
is the regeneration command.

### `hooks/block-dangerous-bash.sh` (Bash)

This hook **pattern-matches the full command-line text** of a Bash call and
blocks the project's forbidden git operations: force-push, `--no-verify`
(skipping hooks), pushes to `main`, and every shell merge (`gh pr merge`),
unconditionally. The only Claude-side merge, a green Dependabot PR into
`develop`, goes through the GitHub MCP `merge_pull_request` tool and is checked
by Claude, not by this hook (CLAUDE.md §Hard governance rule). These mirror the hard governance rules
in [`CLAUDE.md`](../CLAUDE.md) and [`GOVERNANCE.md`](../GOVERNANCE.md). Because it
matches the whole command line, a forbidden flag name appearing inside a commit
message body is also caught — rephrase ("skip hooks", "force-push") if you need
to mention one in commit copy.

### `hooks/block-gradle-wrapper-pin.sh` (Bash)

Blocks `./gradlew wrapper` unless the same command carries
`--gradle-distribution-sha256-sum`. A bare regeneration rewrites
`gradle-wrapper.properties` and silently drops the `distributionSha256Sum` pin;
the atomic two-flag form is in
[`docs/release/SHIPPING.md`](../docs/release/SHIPPING.md).

## The agents

`agents/` holds specialised review subagents used during the multi-agent PR
review cycle (see [`CONTRIBUTING.md`](../CONTRIBUTING.md) → Code review):

- `kotlin-coroutines-reviewer` — reviews coroutine / `suspend` code, watching for
  the `runCatching`-swallows-`CancellationException` class of bug and other
  structured-concurrency pitfalls.
- `android-test-environment-reviewer` — reviews Android test code (Robolectric,
  Compose UI tests, instrumented tests) for environment-specific footguns such
  as bare `assert()` being a no-op on ART.
- `compose-ui-reviewer` — reviews Jetpack Compose UI code under `ui/` for
  composition/recomposition footguns: the `LocalContext` string-resource lint
  trap, stale-capturing effect keys, bare `.format()`.

## The skills

`skills/` holds end-to-end workflow recipes:

- `pr-review` — the canonical PR-review cycle: dispatch the review, post findings
  as inline threads, fix them, resolve the threads, and hand off to the human for
  merge.
- `post-finding` — a `gh api`-based fallback for posting an inline review thread
  when the GitHub MCP isn't available.
- `milestone-start` — scaffolds a new milestone (branch + spec stub + plan stub).
- `release` — the GitFlow release-cut recipe: version bump on a `release/X.Y.Z`
  branch, signed-APK build (with the `GRADLE_USER_HOME` signing-props bridge),
  Room-migration UAT when the schema changed, dependency lock, then — after the
  **human** merges to `main`+`develop` — tag the merge commit and create a
  one-shot immutable GitHub Release. Distils [`docs/release/SHIPPING.md`](../docs/release/SHIPPING.md) §B
  and the bitten gotchas (immutable-release one-shot, signing-props masking,
  tag-burn). Human-initiated; respects the only-humans-merge rule.
- `wrap` — standardised end-of-session handover.

## Adding new automations

New entries — additional hooks, agents, or skills — should also live under
`.claude/` so they ship to every contributor on clone. Keep team defaults
conservative and aligned with the project's hard rules; the lockfile and
dangerous-bash guards exist precisely so a contributor (human or agent) can't
accidentally violate them.
