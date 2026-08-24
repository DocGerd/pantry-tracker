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
#   gh pr merge           the other path that lands code on a protected branch.
#                         NOT unconditional since 2026-08-24 — see the carve-out
#                         below.
#
# Governance CARVE-OUT (granted 2026-08-24; see issue #297):
#   A Dependabot-authored PR based on `develop` whose checks are all green may
#   be merged without a human click. Because a PreToolUse hook sees only the
#   command text — author, base branch and check status are simply not in argv —
#   the three conditions are verified against the GitHub API by
#   verify_carve_out_or_reject() rather than by pattern matching. Everything
#   else still rejects: anything targeting `main`, any human-authored PR, any
#   PR with a failing or pending check, and any command shape the function
#   cannot conclusively reason about.
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
# Expected: exit=2 — PR #47 is not an open, green, Dependabot→develop PR.
#
# Carve-out cases (#297). These reach the GitHub API, so they need network +
# an authenticated gh; offline they all fail closed to exit=2 by design.
#   echo '{"tool_input":{"command":"gh pr merge"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (no explicit PR number — cannot verify anything).
#   echo '{"tool_input":{"command":"gh pr merge 289 --admin"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (--admin bypasses branch protection; never allowed).
#   echo '{"tool_input":{"command":"gh pr checks 1 && gh pr merge 1"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (compound command — cannot bind the validated PR to the run).
#   echo '{"tool_input":{"command":"gh pr merge 1 2"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 (ambiguous — two bare integers).
#   echo '{"tool_input":{"command":"gh pr merge 289 --merge"}}' \
#     | bash .claude/hooks/block-dangerous-bash.sh ; echo "exit=$?"
# Expected: exit=2 while #289 is MERGED (the state test; note `gh pr checks`
#           returns 0 for a merged PR, so this case is the regression guard
#           proving step 2 runs before step 3).
# A genuine allow (exit=0, with an audit line on stderr) requires a real open
# Dependabot→develop PR with green checks; there is no way to assert it from a
# static fixture.
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
Refusing to run this command: it would land code on a protected branch without
a human merge.

Detected: $1
Full command: $command

This repository's hard governance rule (CLAUDE.md "only humans merge to develop
or main") forbids Claude from invoking any of:
    git push <...>:main         (incl. \`git push origin main\`, \`HEAD:main\`, \`:main\`)
    gh pr merge <n>             (except under the carve-out below)

The audit-trail gate is the human's explicit click on "Merge pull request".
No exceptions for one-line reverts, "UAT-verified" hotfixes, wrap-up phases,
or ambiguous "do the rest" / "continue" instructions. When in doubt, ASK.

The ONE standing exception (granted 2026-08-24, issue #297) is a Dependabot-
authored PR based on \`develop\` with every check green. This command did not
qualify — the reason is named above. Merging anything else still requires the
maintainer. Do not attempt to route around this hook via another tool.
MSG
    exit 2
}

# Governance carve-out (granted 2026-08-24; see issue #297).
#
# Allows exactly one shape: merging a Dependabot-authored PR based on `develop`
# whose checks are all green. Every condition is verified against the GitHub API
# because none of them is present in the command text.
#
# Fails CLOSED at every step — an unparseable command, an unreachable API, a
# timeout, a draft PR, or any unexpected field value all reject. The cost of a
# false reject is one message to the maintainer; the cost of a false allow is an
# unreviewed merge.
#
# Assumes the Bash cwd is inside this repository: `gh` resolves the target repo
# from the git remote. Outside a git repo `gh` fails, and we reject.
CARVE_OUT_AUTHORS=("app/dependabot" "dependabot[bot]")
CARVE_OUT_BASE="develop"
GH_TIMEOUT=25

verify_carve_out_or_reject() {
    local cmd="$1"
    local decision pr_number meta pr_author pr_base pr_state pr_draft checks_rc author_ok a

    # Step 1 — command shape + PR-number extraction, in python3 (already a hard
    # dependency of this hook). Compound and substituted commands are refused
    # outright: if the merge is chained, piped, or wrapped in a substitution we
    # cannot be certain the invocation we validated is the one that will run.
    if ! decision=$(printf '%s' "$cmd" | python3 -c '
import re, sys
cmd = sys.stdin.read()
if re.search(r"&&|\|\||;|\||\$\(|`|<<", cmd):
    print("REJECT:compound or substituted command containing a PR merge")
    raise SystemExit
if re.search(r"(^|\s)--admin(\s|=|$)", cmd):
    print("REJECT:PR merge with --admin (bypasses branch protection)")
    raise SystemExit
m = re.search(r"(?:^|[\s;|&(`])gh\s+pr\s+merge\b(.*)$", cmd, re.S)
if not m:
    print("REJECT:could not isolate the merge invocation")
    raise SystemExit
nums = [t for t in m.group(1).split() if t.isdigit()]
if len(nums) != 1:
    print("REJECT:need exactly one explicit PR number, found %d" % len(nums))
    raise SystemExit
print("PR:%s" % nums[0])
'); then
        reject_governance "hook could not evaluate the merge command (failing closed)"
    fi

    case "$decision" in
        PR:*)     pr_number="${decision#PR:}" ;;
        REJECT:*) reject_governance "${decision#REJECT:}" ;;
        *)        reject_governance "unrecognised hook decision (failing closed)" ;;
    esac

    # Step 2 — author, base branch, state and draft status, straight from the API.
    if ! meta=$(timeout -k 5 "$GH_TIMEOUT" gh pr view "$pr_number" \
                    --json author,baseRefName,state,isDraft \
                    --jq '[.author.login, .baseRefName, .state, (.isDraft|tostring)] | join("|")' \
                    </dev/null 2>/dev/null); then
        reject_governance "could not reach the GitHub API for PR #$pr_number (failing closed)"
    fi
    IFS='|' read -r pr_author pr_base pr_state pr_draft <<<"$meta" || true
    if [[ -z "${pr_author:-}" || -z "${pr_base:-}" || -z "${pr_state:-}" ]]; then
        reject_governance "incomplete API response for PR #$pr_number (failing closed)"
    fi

    # `gh pr view --json author` returns the GraphQL login `app/dependabot`; the
    # REST API returns `dependabot[bot]`. Accept both so this does not silently
    # start rejecting if gh switches API surface underneath us.
    author_ok=0
    for a in "${CARVE_OUT_AUTHORS[@]}"; do
        [[ "$pr_author" == "$a" ]] && author_ok=1
    done
    if [[ "$author_ok" -ne 1 ]]; then
        reject_governance "PR #$pr_number is authored by '$pr_author', not Dependabot"
    fi
    if [[ "$pr_base" != "$CARVE_OUT_BASE" ]]; then
        reject_governance "PR #$pr_number targets '$pr_base', not '$CARVE_OUT_BASE'"
    fi
    # Load-bearing, not cosmetic: `gh pr checks` returns 0 for an already-merged
    # or closed PR, so without this test a stale merge command would sail past
    # step 3 on a PR that is no longer open.
    if [[ "$pr_state" != "OPEN" ]]; then
        reject_governance "PR #$pr_number is $pr_state, not OPEN"
    fi
    if [[ "$pr_draft" != "false" ]]; then
        reject_governance "PR #$pr_number is a draft"
    fi

    # Step 3 — check status. This gh build has no `gh pr checks --json`, so the
    # documented exit-code contract IS the interface: 0 = all pass, 8 = pending,
    # anything else = failing. 124/137 are timeout's own SIGTERM/SIGKILL codes.
    set +e
    timeout -k 5 "$GH_TIMEOUT" gh pr checks "$pr_number" </dev/null >/dev/null 2>&1
    checks_rc=$?
    set -e
    case "$checks_rc" in
        0)       : ;;
        8)       reject_governance "PR #$pr_number still has pending checks" ;;
        124|137) reject_governance "timed out reading checks for PR #$pr_number (failing closed)" ;;
        *)       reject_governance "PR #$pr_number has failing checks (gh pr checks rc=$checks_rc)" ;;
    esac

    printf '%s\n' \
        "block-dangerous-bash: allowing merge of PR #$pr_number under the Dependabot carve-out (#297)." \
        "  author=$pr_author  base=$pr_base  state=$pr_state  checks=all-green" >&2
    exit 0
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

# Governance: `gh pr merge` is the other path that lands code on a protected
# branch. The leading char class includes `(` and backtick so subshell forms
# like `echo $(gh pr merge 47)` and `` `gh pr merge 47` `` are caught. Single-
# quoted so the literal backtick isn't interpreted as command substitution.
#
# Since 2026-08-24 this is a *gate*, not a flat reject: matching hands off to
# verify_carve_out_or_reject(), which either exits 0 (Dependabot → develop, all
# checks green) or calls reject_governance() with the specific reason.
gh_pr_merge_regex='(^|[[:space:]\;\|\&\(`])gh[[:space:]]+pr[[:space:]]+merge([[:space:]]|$)'
if [[ "$command_match" =~ $gh_pr_merge_regex ]]; then
    verify_carve_out_or_reject "$command_match"
fi

exit 0
