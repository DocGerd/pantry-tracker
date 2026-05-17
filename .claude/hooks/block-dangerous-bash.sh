#!/usr/bin/env bash
# PreToolUse hook: reject Bash commands using git's destructive escape hatches.
#
# Blocked patterns (word-boundaried so we don't catch e.g. `curl --no-verify-ssl`):
#   --no-verify           (git commit, git push: skips hooks)
#   --force               (any subcommand)
#   --force-with-lease    (still a force-push, just safer)
#   -n on git commit      (= --no-verify shorthand)
#   -f on git push        (= --force shorthand)
#
# Test:
#   echo '{"tool_input":{"command":"git commit --no-verify -m x"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 with rejection.
#   echo '{"tool_input":{"command":"git commit -m x"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0.
#   echo '{"tool_input":{"command":"curl --no-verify-ssl https://x"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (not blocked — only word-boundaried matches).
set -euo pipefail

input="$(cat)"
command="$(printf '%s' "$input" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("tool_input",{}).get("command",""))' 2>/dev/null || true)"

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

# Word-boundaried matches via regex with shell parameter expansion.
if [[ " $command " == *" --no-verify "* ]]; then reject "--no-verify"; fi
if [[ " $command " == *" --force "* ]]; then reject "--force"; fi
if [[ " $command " == *" --force-with-lease "* ]]; then reject "--force-with-lease"; fi

# git commit -n / git push -f need git-aware matching to avoid false positives
# (e.g. `tar -f` or `grep -n`).
if [[ "$command" =~ (^|[[:space:]\;\|\&])git[[:space:]]+commit[[:space:]] ]] && \
   [[ " $command " == *" -n "* ]]; then
    reject "git commit -n"
fi
if [[ "$command" =~ (^|[[:space:]\;\|\&])git[[:space:]]+push[[:space:]] ]] && \
   [[ " $command " == *" -f "* ]]; then
    reject "git push -f"
fi

exit 0
