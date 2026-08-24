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
# Governance-enforced blocks (CLAUDE.md hard rule: "only humans merge to develop
# or main"):
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
#   A Dependabot-authored PR based on `develop` that GitHub reports as CLEAN may
#   be merged without a human click — but that carve-out is NOT enforced by this
#   hook, and no merge command is ever allowed from the shell. It runs through
#   the GitHub MCP `merge_pull_request` tool, whose parameters are typed rather
#   than parsed. See the long note further down, and CLAUDE.md for the checklist.
#
# A normalization pre-pass builds a UNION MATCH PROBE — the raw command plus
# several rewrites modelling what bash does before exec (quote removal, line
# continuations, backslashes, parameter expansion, brace lists, $-quote
# decoding). Governance regexes match if the pattern appears in ANY candidate,
# so `git push origin "main"`, `ma\in`, `main$x` and `$'\x6dain'` are all caught
# despite spelling something else literally. A UNION is required rather than one
# rewritten string: a single lossy rewrite can DESTROY a match, which is exactly
# how an earlier version let `merge$x` through.
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
# It also builds the MATCH PROBE — see the long note above `command_match` below.
# Both come from this one python invocation because the hook runs on every single
# Bash call and a second interpreter start is pure overhead.
# shellcheck disable=SC2016  # the python body must reach python unexpanded
if ! parsed=$(printf '%s' "$input" | python3 -c '
import codecs, json, re, sys
SQ = chr(39)   # written this way: the program itself is inside a single-quoted shell string
d = json.load(sys.stdin)
ti = d.get("tool_input", {})
if "command" not in ti:
    sys.exit(3)
cmd = ti["command"]
if not isinstance(cmd, str):
    sys.exit(3)

EXPANSION = r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*"

def ansi_decode(s):
    # $-quoted strings: bash decodes \155, \x6d, m before exec, so the
    # literal text can spell a keyword no regex would recognise.
    def sub(m):
        try:
            return codecs.decode(m.group(1), "unicode_escape")
        except Exception:
            return m.group(1)
    return re.sub(r"\$" + SQ + r"([^" + SQ + r"]*)" + SQ, sub, s)

q = cmd.replace(chr(34), "").replace(SQ, "")
lc = q.replace("\\\n", "")                      # line continuations joined
cands = [
    cmd,                                        # exactly as typed
    q,                                          # quotes removed
    lc,                                         # + continuations joined
    lc.replace("\\", ""),                       # + backslashes removed
    re.sub(EXPANSION, "", lc),                  # expansions vanish (unset var)
    re.sub(EXPANSION, " ", lc),                 # expansions become a separator
    # ${x-merge} / ${x:-merge} / ${x=w} / ${x+w}: an unset variable expands to
    # the WORD, so the keyword is spelled inside the braces and neither
    # deleting nor blanking the expansion reveals it.
    re.sub(r"\$\{[A-Za-z_][A-Za-z0-9_]*:?[-=+?]([^}]*)\}", r"\1", lc),
    re.sub(r"[{},]", " ", lc),                  # brace lists flattened
    ansi_decode(cmd),
    ansi_decode(q).replace("\\", ""),
]
print(cmd)
print("__HOOK_SPLIT_a7f3__")
print("\n".join(cands))
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

# Split the two payloads back apart. If the marker is missing the parse was not
# what we expect, so fail closed rather than guessing.
# No trailing newline in the marker: for an empty command every candidate is
# empty too, so the whole probe is newlines and `$( )` strips them — leaving the
# marker at the very end of $parsed with nothing after it.
_marker=$'\n__HOOK_SPLIT_a7f3__'
case "$parsed" in
    *"$_marker"*) : ;;
    *) cat >&2 <<MSG
block-dangerous-bash hook: could not split the parsed command from the match
probe. Failing closed — exiting 2.
MSG
       exit 2 ;;
esac
command="${parsed%%"$_marker"*}"
command_match="${parsed#*"$_marker"}"
command_match="${command_match#$'\n'}"

# Empty command string from Claude Code is benign (no command to inspect → no
# dangerous flag possible). Distinct from "field missing" above which is fail-closed.
if [[ -z "$command" ]]; then
    exit 0
fi

# The message write is wrapped in `{ … } || true` so that a failed write to
# stderr (closed or full fd) cannot make `set -e` abort the function BEFORE
# `exit 2` — which would exit 1, and a would-be block would read as an allow.
reject() {
    { cat >&2 <<MSG
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
    } || true
    exit 2
}

reject_governance() {
    { cat >&2 <<MSG
Refusing to run this command: it would land code on a protected branch without
a human merge.

Detected: $1
Full command: $command

This repository's hard governance rule (CLAUDE.md "only humans merge to develop
or main") forbids Claude from invoking any of:
    git push <...>:main         (incl. \`git push origin main\`, \`HEAD:main\`, \`:main\`)
    a PR merge from the shell   (unconditionally — see below)

The audit-trail gate is the human's explicit click on "Merge pull request".
No exceptions for one-line reverts, "UAT-verified" hotfixes, wrap-up phases,
or ambiguous "do the rest" / "continue" instructions. When in doubt, ASK.

There is ONE standing exception (granted 2026-08-24, issue #297): a Dependabot-
authored PR based on \`develop\` that GitHub reports CLEAN. It does NOT run from
the shell. Deciding from command text whether something is a merge is a
deny-list over shell syntax, and three rounds of adversarial review showed it
cannot be closed — so this hook refuses every shell merge, and the carve-out
goes through the GitHub MCP \`merge_pull_request\` tool, whose owner/repo/number
are typed parameters rather than parsed text.

That is the sanctioned route, not a way around this hook: verify author, base
branch, OPEN/non-draft state, a \`dependabot/\` head and mergeStateStatus CLEAN
first — the checklist is in CLAUDE.md. Anything else still needs the maintainer.
MSG
    } || true
    exit 2
}

# Governance carve-out (granted 2026-08-24, issue #297) — NOT enforced here.
#
# This hook used to gate the merge command: parse the PR number out of the
# command text, verify author/base/state/mergeState against the API, and exit 0
# when all of it held. Three rounds of adversarial review killed that design.
#
# The gate itself was fine. The problem is upstream of it: deciding WHETHER to
# gate is a pattern match over shell text, and that is irreducibly a deny-list.
# You cannot allow-list "is this a merge?" — you have to recognise it, and
# recognising it means re-implementing bash expansion. Round 1 closed shell
# operators, round 2 closed backslashes, round 3 closed parameter expansion,
# ANSI-C quoting and brace lists. Every round found a fresh family, and one
# round's fix re-opened the previous round's hole. A matcher miss is not a
# degraded check — the hook ends in `exit 0`, so it is a TOTAL bypass.
#
# So the merge command is refused unconditionally again, and the carve-out runs
# through the GitHub MCP `merge_pull_request` tool instead, which takes owner,
# repo and pullNumber as TYPED PARAMETERS. No shell, no quoting, no expansion —
# the parsing problem does not exist on that path.
#
# The three conditions are verified before that call rather than by this hook.
# See CLAUDE.md "Hard governance rule" for the checklist. That is a real
# trade-off, stated plainly: the conditions are no longer machine-enforced. It
# buys the removal of an entire false-allow class that repeatedly proved it
# could not be closed by parsing.

# These run against the UNION PROBE, not the raw text: `git push "--force" x`
# and `--fo\rce` both reach a real force-push while the literal text matches
# nothing. Word-boundaried via [[:space:]] (catches spaces, tabs,
# newlines, etc.). The trailing class also accepts `=` so `--force=value` and
# `--force-with-lease=ref:expected_sha` (documented git syntax) can't slip past.
if [[ " $command_match " =~ (^|[[:space:]])--no-verify([[:space:]=]|$) ]]; then reject "--no-verify"; fi
if [[ " $command_match " =~ (^|[[:space:]])--force([[:space:]=]|$) ]]; then reject "--force"; fi
if [[ " $command_match " =~ (^|[[:space:]])--force-with-lease([[:space:]=]|$) ]]; then reject "--force-with-lease"; fi

# git commit -n / git push -f need git-aware matching to avoid false positives
# (e.g. `tar -f` or `grep -n`). The lead-in is intentionally permissive — it
# allows arbitrary word tokens between `git` and the subcommand so all of
# `git -c x=y commit`, `git --git-dir=foo commit`, and chained commands like
# `git status && git push -f` still match. The cost is over-matching across
# quoted strings containing the literal text `git push`, which is acceptable
# (see "Known limitation" in the header).
# NOTE on [[:blank:]] vs [[:space:]] in the SPANNING loops below. These three
# regexes walk across argv tokens, and [[:space:]] includes NEWLINE -- which let a
# match straddle the newline-joined candidates of the union probe. A real command
# false-blocked on exactly that: the word appearing inside an `echo` on one line,
# and a harmless `gh pr list` on another, matched as if they were one invocation
# even though the word came FIRST.
#
# Shell words live on one line (line continuations are already normalized into the
# candidates), so confining these loops to blanks confines a match to a single
# line, and therefore to a single candidate. The simple word-boundaried FLAG
# checks further up keep [[:space:]] deliberately: they match ONE token, so they
# cannot straddle anything.
#
# This matters beyond tidiness. A guard that fires on ordinary commands teaches
# you to route around it, which is a security failure with a friendly face.
git_lead='(^|[[:space:]\;\|\&\(`])git([[:blank:]]+[^[:space:];|&]+)*[[:blank:]]+'
if [[ "$command_match" =~ ${git_lead}commit[[:space:]] ]] && \
   [[ " $command_match " =~ (^|[[:space:]])-n([[:space:]=]|$) ]]; then
    reject "git commit -n"
fi
if [[ "$command_match" =~ ${git_lead}push[[:space:]] ]] && \
   [[ " $command_match " =~ (^|[[:space:]])-f([[:space:]=]|$) ]]; then
    reject "git push -f"
fi

# Governance: only humans merge to main (CLAUDE.md hard rule).
#
# $command_match is the UNION MATCH PROBE built during parsing: the raw command
# plus several normalizations, concatenated with newlines. A governance regex
# matches if the pattern appears in ANY of them.
#
# Why a union and not one rewritten string. The obvious approach — rewrite the
# command once and match that — is what the first version did, and it was WRONG
# in the dangerous direction. Stripping `$` turned `gh pr merge$x 4242 --admin`
# into `...mergex...`, which no longer matches the `merge` word boundary, so it
# exited 0 — while bash, with $x unset, executes a real --admin merge. The RAW
# text would have matched, because `$` is not alphanumeric. A single lossy
# rewrite can DESTROY a match; only a union can be relied on to add them.
#
# The candidates model what bash actually does before exec: quote removal,
# line-continuation joining, backslash removal, parameter expansion (both to
# nothing and to a separator, since an unset variable does one and a set one
# usually the other), brace-list flattening, and $-quote decoding — the last
# because `$'\155erge'` spells a keyword that no regex over the literal text
# can see.
#
# Over-matching is free: a match only routes into a gate that re-validates the
# RAW command and rejects anything non-canonical, or into a flat reject. The
# original $command is what reject messages print, so the user sees what they
# actually typed.

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
push_main_regex="${git_lead}push[[:blank:]]+([^[:space:]\;\|\&]+[[:blank:]]+)*\+?(refs/heads/|[^[:space:]\;\|\&]*:(refs/heads/)?)?main(\^[^[:space:]\;\|\&]*)?([[:space:]\;\|\&\)\`]|$)"
if [[ "$command_match" =~ $push_main_regex ]]; then
    reject_governance "git push to main"
fi

# Governance: `gh pr merge` is the other path that lands code on a protected
# branch. The leading char class includes `(` and backtick so subshell forms
# like `echo $(gh pr merge 47)` and `` `gh pr merge 47` `` are caught. Single-
# quoted so the literal backtick isn't interpreted as command substitution.
#
# UNCONDITIONAL. The carve-out is not enforced here — see the long note above.
#
# The pattern stays deliberately wide even though it now only ever rejects.
# Over-matching costs a false block on a command that merely mentions the phrase
# (annoying, and the reason commit messages go through `git commit -F`);
# under-matching costs an unblocked merge. Hence: any non-identifier lead-in, an
# optional leading path (`/usr/bin/gh`), and arbitrary flag tokens BOTH between
# `gh` and `pr` and between `pr` and `merge` — `gh pr -R o/r merge 5` is a real
# merge invocation, because cobra resolves the subcommand past a parent flag.
# The trailing `[^A-Za-z0-9_.-]*` on the `gh` token catches `$(which gh)` and
# its backtick form via the union probe.
gh_pr_merge_regex='(^|[^A-Za-z0-9_.-])([^[:space:]]*/)?gh[^A-Za-z0-9_.-]*([[:blank:]]+[^[:space:];|&]+)*[[:blank:]]+pr([[:blank:]]+[^[:space:];|&]+)*[[:blank:]]+merge([^[:alnum:]_-]|$)'
if [[ "$command_match" =~ $gh_pr_merge_regex ]]; then
    reject_governance "PR merge from the shell"
fi

# The REST/GraphQL merge endpoints reach the same place as the porcelain command
# and are invisible to the pattern above. They carry no PR-number-and-flags shape
# the gate can validate, so they are refused outright rather than gated — and
# CLAUDE.md actively steers sessions toward `gh api` for other things, which
# makes an inadvertent bypass realistic rather than theoretical.
api_merge_regex='pulls/[0-9]+/merge|mergePullRequest'
if [[ "$command_match" =~ $api_merge_regex ]]; then
    reject_governance "PR merge via the GitHub API (use the canonical command, or ask the maintainer)"
fi

exit 0
