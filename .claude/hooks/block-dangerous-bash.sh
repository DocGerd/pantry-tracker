#!/usr/bin/env bash
# PreToolUse hook: reject Bash commands using git's destructive escape hatches.
#
# Blocked patterns (word-boundaried so we don't catch e.g. `curl --no-verify-ssl`,
# and =-tolerant so `--force-with-lease=ref:expected_sha` doesn't slip past):
#   --no-verify           (git commit, git push: skips hooks)
#   --force               (any subcommand)
#   --force-with-lease    (still a force-push, just safer)
#   -n on git commit      (= --no-verify shorthand; tolerates `git -c x=y commit -n`)
#   -f on git push        (= --force shorthand; tolerates `git -c x=y push -f`)
#
# Fail-closed: if JSON parsing fails (python3 missing, malformed input, schema
# changed), the hook exits 2 with a diagnostic — refusing the action is the
# only safe default for a guard hook. A guard that silently allows when it
# can't tell what's happening is worse than no guard at all.
#
# Known limitation: this hook matches on raw command text, so a Bash command
# whose *body* contains these flag literals (e.g. `cat <<EOF` heredoc that
# echoes "--force" as data, or `grep --no-verify file`) will false-positive
# and be blocked. If you need to write/echo this text, prefer the Write or
# Edit tools (which this hook doesn't match) or rename the literal.
#
# Test:
#   echo '{"tool_input":{"command":"git commit --no-verify -m x"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 with rejection.
#   echo '{"tool_input":{"command":"git push --force-with-lease=origin/main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (=-attached value bypass — must block).
#   echo '{"tool_input":{"command":"git -c user.name=x commit -n -m foo"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (git-c lead-in must not bypass).
#   echo '{"tool_input":{"command":"git commit -m x"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0.
#   echo '{"tool_input":{"command":"curl --no-verify-ssl https://x"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (word-boundaried — only standalone flag tokens match).
#   echo '{"tool_input":{"command":"tar -xvf foo.tar"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (-f without `git push` lead-in is fine).
#   echo 'not json' | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (fail-closed on parse failure).
set -euo pipefail

input="$(cat)"

# Fail-closed JSON parsing: python3 must exist, JSON must parse, tool_input.command
# must be present (Bash tool always populates it). Any failure → exit 2.
if ! command=$(printf '%s' "$input" | python3 -c '
import json, sys
d = json.load(sys.stdin)
ti = d.get("tool_input", {})
if "command" not in ti:
    sys.exit(3)
print(ti["command"])
'); then
    cat >&2 <<MSG
block-dangerous-bash hook: failed to parse Bash tool input.
Failing closed — exiting 2 to block the action.

Possible causes:
- python3 missing from PATH
- malformed JSON on stdin
- tool_input.command field absent or schema changed

If this is a false alarm, fix the hook before re-running the action.
MSG
    exit 2
fi

# Empty command string from Claude Code is benign (no command to inspect → no
# dangerous flag possible). Distinct from "field missing" above which is fail-closed.
if [[ -z "$command" ]]; then
    exit 0
fi

reject() {
    cat >&2 <<MSG
Refusing to run this command: it uses a Git escape-hatch flag.

Detected: $1
Full command: $command

This repository's policy (CLAUDE.md + automation toolkit) forbids:
    --no-verify          (skips pre-commit hooks — fix the hook failure instead)
    --force              (overwrites history — create a new commit instead)
    --force-with-lease   (still a force-push — same reasoning)
    git commit -n        (= --no-verify shorthand)
    git push -f          (= --force shorthand)

If you genuinely need to bypass (e.g., the hook is broken and being fixed in
this same PR), ask the user for explicit permission first.
MSG
    exit 2
}

# Word-boundaried matches via bash regex with [[:space:]] (catches spaces, tabs,
# newlines, etc.). The trailing class also accepts `=` so `--force=value` and
# `--force-with-lease=ref:expected_sha` (documented git syntax) can't slip past.
if [[ " $command " =~ (^|[[:space:]])--no-verify([[:space:]=]|$) ]]; then reject "--no-verify"; fi
if [[ " $command " =~ (^|[[:space:]])--force([[:space:]=]|$) ]]; then reject "--force"; fi
if [[ " $command " =~ (^|[[:space:]])--force-with-lease([[:space:]=]|$) ]]; then reject "--force-with-lease"; fi

# git commit -n / git push -f need git-aware matching to avoid false positives
# (e.g. `tar -f` or `grep -n`). The lead-in is intentionally permissive — it
# allows arbitrary word tokens between `git` and the subcommand so all of
# `git -c x=y commit`, `git --git-dir=foo commit`, and chained commands like
# `git status && git push -f` still match. The cost is over-matching across
# quoted strings containing the literal text `git push`, which is acceptable
# (see "Known limitation" in the header).
git_lead='(^|[[:space:]\;\|\&])git([[:space:]]+[^[:space:]]+)*[[:space:]]+'
if [[ "$command" =~ ${git_lead}commit[[:space:]] ]] && \
   [[ " $command " =~ (^|[[:space:]])-n([[:space:]=]|$) ]]; then
    reject "git commit -n"
fi
if [[ "$command" =~ ${git_lead}push[[:space:]] ]] && \
   [[ " $command " =~ (^|[[:space:]])-f([[:space:]=]|$) ]]; then
    reject "git push -f"
fi

exit 0
