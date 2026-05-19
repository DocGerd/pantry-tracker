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
# Governance-enforced blocks (CLAUDE.md hard rule: "only humans merge to main"):
#   git push <...>main    any push whose destination ref is `main`, in any
#                         common refspec form: bare `main`, `HEAD:main`, `:main`
#                         (delete), `abc123:main`, `refs/heads/main`,
#                         `HEAD:refs/heads/main`, `+main` / `+refs/heads/main`
#                         (force-refspec without --force flag). Anchored to the
#                         same `git push` invocation so unrelated mentions of
#                         "main" elsewhere in the command line do NOT trigger
#                         (e.g. `git push origin feature/foo && echo main` is
#                         allowed). Tolerates `main` as the SRC of a refspec
#                         (`main:foo` pushes local main to remote foo — fine)
#                         and branch names that merely *contain* "main"
#                         (`feature/main-cleanup`).
#   gh pr merge           the other path that lands code on main.
#
# A quote-stripping pre-pass normalizes the command before governance matching,
# so `git push origin "main"`, `eval "git push origin main"`, and
# `bash -c "gh pr merge 47"` are all caught despite the wrapping quotes.
#
# Fail-closed: if JSON parsing fails (python3 missing, malformed input, schema
# changed), the hook exits 2 with a diagnostic — refusing the action is the
# only safe default for a guard hook. A guard that silently allows when it
# can't tell what's happening is worse than no guard at all.
#
# Known limitations:
# - Matches on raw command text. A Bash command whose *body* contains these
#   flag literals (e.g. `cat <<EOF` heredoc echoing "--force" as data, or
#   `grep --no-verify file`) will false-positive and be blocked. If you need
#   to write/echo this text, prefer the Write or Edit tools (which this hook
#   doesn't match) or rename the literal.
# - Cannot catch deferred shell expansion. `r=main; git push origin $r` and
#   `branch=$(echo main); git push origin $branch` slip through because the
#   literal text contains no `main` ref-token at hook-evaluation time. There
#   is no general defense against this in a text-pattern hook; policy +
#   review must catch variable-laundering attempts.
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
#   echo '{"tool_input":{"command":"git push origin main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (governance: only humans merge to main).
#   echo '{"tool_input":{"command":"git push origin HEAD:main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (refspec dst = main).
#   echo '{"tool_input":{"command":"git push origin :main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (delete-refspec, dst still main).
#   echo '{"tool_input":{"command":"gh pr merge 47 --squash"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (governance: only humans merge to main).
#   echo '{"tool_input":{"command":"git push origin main:foo"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (main is SRC, not dst — pushing local main to remote foo).
#   echo '{"tool_input":{"command":"git push origin feature/main-cleanup"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (branch name merely contains "main").
#   echo '{"tool_input":{"command":"gh pr view 47"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (gh pr view is not gh pr merge).
#   echo '{"tool_input":{"command":"git push origin refs/heads/main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (full ref form, dst still main).
#   echo '{"tool_input":{"command":"git push origin +main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (force-refspec without --force flag).
#   echo '{"tool_input":{"command":"eval \"git push origin main\""}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (quote-strip pre-pass catches eval/bash -c wrappers).
#   echo '{"tool_input":{"command":"echo $(gh pr merge 47)"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (subshell form; leading char class includes `(`).
#   echo '{"tool_input":{"command":"git push origin feature/foo && echo main"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=0 (FP fix: `main` outside the same `git push` invocation).
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

reject_governance() {
    cat >&2 <<MSG
Refusing to run this command: it would land code on main without a human merge.

Detected: $1
Full command: $command

This repository's hard governance rule (CLAUDE.md "only humans merge to main")
forbids Claude from invoking any of:
    git push <...>:main         (incl. \`git push origin main\`, \`HEAD:main\`, \`:main\`)
    gh pr merge <n>             (any flavor)

The audit-trail gate is the human's explicit click on "Merge pull request".
No exceptions for one-line reverts, "UAT-verified" hotfixes, wrap-up phases,
or ambiguous "do the rest" / "continue" instructions. When in doubt, ASK.
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
git_lead='(^|[[:space:]\;\|\&\(`])git([[:space:]]+[^[:space:]]+)*[[:space:]]+'
if [[ "$command" =~ ${git_lead}commit[[:space:]] ]] && \
   [[ " $command " =~ (^|[[:space:]])-n([[:space:]=]|$) ]]; then
    reject "git commit -n"
fi
if [[ "$command" =~ ${git_lead}push[[:space:]] ]] && \
   [[ " $command " =~ (^|[[:space:]])-f([[:space:]=]|$) ]]; then
    reject "git push -f"
fi

# Governance: only humans merge to main (CLAUDE.md hard rule).
#
# Quote-stripping pre-pass: normalize the command by removing ASCII single and
# double quotes before the governance regexes run. This lets `git push origin
# "main"`, `eval "git push origin main"`, and `bash -c "gh pr merge 47"` match
# despite the wrapping quotes. The original $command is preserved for the
# reject message so the user sees what they actually typed.
command_match="${command//\"/}"
command_match="${command_match//\'/}"

# Single combined regex (not two independent matches): require `main` to appear
# as the *destination* of the same `git push` invocation. Walking through it:
#   ${git_lead}push[[:space:]]+        the `git push ` invocation
#   ([^[:space:]\;\|\&]+[[:space:]]+)* zero or more intermediate argv tokens,
#                                       stopping at any shell separator (so
#                                       `... feature/foo && echo main` cannot
#                                       reach the `main` token from this push)
#   \+?                                 optional force-refspec prefix `+`
#   (refs/heads/                        full ref form `refs/heads/main`
#   |[^[:space:]\;\|\&]*:(refs/heads/)?)?  OR colon-prefixed dst forms:
#                                       `HEAD:main`, `:main`, `abc:main`,
#                                       `HEAD:refs/heads/main`
#   main                                the dst ref itself
#   (\^[^[:space:]\;\|\&]*)?            optional revspec suffix `^...`
#   ([[:space:]\;\|\&\)`]|$)            end of the argv token. Includes `)`
#                                       and backtick so subshell forms like
#                                       `echo $(git push origin main)` and
#                                       `` `git push origin main` `` terminate
#                                       correctly.
#
# Allowed-by-design (the regex correctly does NOT match these):
#   git push origin main:foo            main is SRC, not dst (colon AFTER main)
#   git push origin feature/main-x      branch name merely contains "main"
#                                       (preceded by `/` but not in dst position)
#   git push origin feature/foo && echo main  the `&&` breaks argv-token continuity
push_main_regex="${git_lead}push[[:space:]]+([^[:space:]\;\|\&]+[[:space:]]+)*\+?(refs/heads/|[^[:space:]\;\|\&]*:(refs/heads/)?)?main(\^[^[:space:]\;\|\&]*)?([[:space:]\;\|\&\)\`]|$)"
if [[ "$command_match" =~ $push_main_regex ]]; then
    reject_governance "git push to main"
fi

# Governance: `gh pr merge` is the other path that lands code on main. The
# leading char class includes `(` and backtick so subshell forms like
# `echo $(gh pr merge 47)` and `` `gh pr merge 47` `` are caught. Single-quoted
# so the literal backtick isn't interpreted as command substitution.
gh_pr_merge_regex='(^|[[:space:]\;\|\&\(`])gh[[:space:]]+pr[[:space:]]+merge([[:space:]]|$)'
if [[ "$command_match" =~ $gh_pr_merge_regex ]]; then
    reject_governance "gh pr merge"
fi

exit 0
