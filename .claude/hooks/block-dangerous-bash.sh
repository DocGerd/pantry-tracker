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
#   A Dependabot-authored PR based on `develop` that GitHub reports as CLEAN
#   may be merged without a human click. Because a PreToolUse hook sees only the
#   command text — author, base branch and merge state are simply not in argv —
#   those conditions are verified against the GitHub API by
#   verify_carve_out_or_reject() rather than by pattern matching.
#
#   The command itself must be the CANONICAL form and nothing else (see the
#   design note above verify_carve_out_or_reject): the gate allow-lists one
#   shape rather than trying to enumerate dangerous ones. Everything else
#   rejects — anything targeting `main`, any human-authored PR, any PR GitHub
#   does not report CLEAN, and every non-canonical command shape, harmless
#   ones included.
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
    gh pr merge <n>             (except under the carve-out below)

The audit-trail gate is the human's explicit click on "Merge pull request".
No exceptions for one-line reverts, "UAT-verified" hotfixes, wrap-up phases,
or ambiguous "do the rest" / "continue" instructions. When in doubt, ASK.

The ONE standing exception (granted 2026-08-24, issue #297) is a Dependabot-
authored PR based on \`develop\` with every check green. This command did not
qualify — the reason is named above. Merging anything else still requires the
maintainer. Do not attempt to route around this hook via another tool.
MSG
    } || true
    exit 2
}

# Governance carve-out (granted 2026-08-24; see issue #297).
#
# DESIGN NOTE — read this before editing.
#
# The first version of this gate enumerated the DANGEROUS command shapes
# (compound operators, `--admin`, …) and allowed whatever was left. An
# adversarial review of PR #298 broke that four independent ways:
#   - a bare newline or `&` chained a SECOND, unvalidated merge onto the command;
#   - `--body 4242 some-branch` made the gate validate #4242 while gh merged the
#     branch's PR instead — validated one thing, merged another;
#   - `-R other/repo` retargeted the merge at a repository the gate never looked at;
#   - `--ad\min` slipped the flag check, because bash strips the backslash only
#     AFTER the hook has already inspected the text.
#
# Enumerating the bad is the wrong shape for this problem — the attacker picks
# the input, so any list of forbidden forms is a list you have to keep complete
# forever. So the gate now accepts exactly ONE canonical command form and
# rejects everything else, including plenty of harmless variants. A merge that
# needs a non-canonical flag is the human's click, not this hook's problem.
#
#   canonical:  gh pr merge <N> [--merge|--squash|--rebase] [--delete-branch]
#
# All carve-out conditions are then verified against the GitHub API, because
# none of them is present in the command text at all.
#
# Fails CLOSED everywhere: unparseable command, unreachable API, timeout, wrong
# field count, or any unexpected value rejects. A false reject costs one message
# to the maintainer; a false allow is an unreviewed merge.
CARVE_OUT_AUTHORS=("app/dependabot" "dependabot[bot]")
CARVE_OUT_BASE="develop"
CARVE_OUT_HEAD_PREFIX="dependabot/"
# Pinned, not resolved from the git remote: `-R`/`--repo`/`GH_REPO` must not be
# able to point the gate at a different repository than the one being merged.
CARVE_OUT_REPO="DocGerd/pantry-tracker"
GH_TIMEOUT=25
# Absolute path. A bare `gh` resolves through $PATH, and any user-writable PATH
# entry ahead of /usr/bin would let a planted stub answer the gate's questions.
GH_BIN="/usr/bin/gh"

verify_carve_out_or_reject() {
    local cmd="$1"
    local decision pr_number checks_rc author_ok a raw
    local pr_author pr_base pr_state pr_draft pr_mergestate pr_head
    local pr_foreign_commits pr_commit_count
    local -a meta

    [[ -x "$GH_BIN" ]] || reject_governance "$GH_BIN is not executable (failing closed)"
    # `env -u` below sanitises the GATE's own queries, but the merge that runs
    # afterwards inherits this environment. If GH_HOST is set, the gate would
    # verify github.com while the merge went somewhere else, so refuse instead.
    [[ -z "${GH_HOST:-}" ]] || reject_governance "GH_HOST is set; the gate cannot bind the host the merge will use"
    [[ -z "${GH_CONFIG_DIR:-}" ]] || reject_governance "GH_CONFIG_DIR is set; the gate cannot bind the credentials the merge will use"

    # Step 1 — the command must be EXACTLY the canonical form, or nothing.
    #
    # Deliberately validated against the RAW command, not the quote-stripped
    # copy: stripping quotes changes what bash would actually execute, so a
    # decision made on the stripped text is a decision about a command that
    # never runs.
    # shellcheck disable=SC2016  # the python body must reach python unexpanded
    if ! decision=$(printf '%s' "$cmd" | CARVE_OUT_REPO="$CARVE_OUT_REPO" python3 -c '
import os, re, sys
cmd = sys.stdin.read().strip()
repo = os.environ["CARVE_OUT_REPO"]
# The repo must be named IN THE COMMAND, not just in the gate. Without it, `gh`
# resolves the target from the cwd git remote -- so the gate could verify this
# repo while the command merged a PR in whatever repository the shell happened
# to be sitting in. Naming it makes the validated repo and the merged repo the
# same string.
repo_flag = " --repo " + repo
# Char-class test with that one literal removed, so `/` stays illegal everywhere
# else. This single test kills every shell operator at once -- newline, CR, ";",
# "&", "|", "$", backtick, quotes, backslash, "=", "(" -- with no list to keep
# in sync as new metacharacters occur to someone.
if re.search(r"[^A-Za-z0-9 \-]", cmd.replace(repo_flag, "", 1)):
    print("REJECT:not the canonical merge form (disallowed characters)")
    raise SystemExit
CANON = re.compile(
    r"gh pr merge (?P<n>[0-9]{1,7})"
    + re.escape(repo_flag) +
    r"(?: --(?:merge|squash|rebase))?"
    r"(?: --delete-branch)?"
)
m = CANON.fullmatch(cmd)
if not m:
    print("REJECT:not the canonical merge form -- expected "
          "gh pr merge <N>" + repo_flag +
          " [--merge|--squash|--rebase] [--delete-branch]")
    raise SystemExit
print("PR:%s" % m.group("n"))
'); then
        reject_governance "hook could not evaluate the merge command (failing closed)"
    fi

    case "$decision" in
        PR:*)     pr_number="${decision#PR:}" ;;
        REJECT:*) reject_governance "${decision#REJECT:}" ;;
        *)        reject_governance "unrecognised hook decision (failing closed)" ;;
    esac

    # Step 2 — every carve-out condition, straight from the API.
    #
    # Fields come back ONE PER LINE. `|` is legal in a git branch name, so a
    # `join("|")` was genuinely ambiguous — a branch called `develop|OPEN|false`
    # is a valid ref. Newline is not: git forbids control characters in ref
    # names, so a value can never contain the delimiter.
    #
    # `env -u` strips GH_REPO/GH_HOST so the surrounding environment cannot aim
    # the gate at a different repo or host than the merge will land on.
    if ! raw=$(env -u GH_REPO -u GH_HOST \
                   timeout -k 5 "$GH_TIMEOUT" "$GH_BIN" pr view "$pr_number" \
                   --repo "$CARVE_OUT_REPO" \
                   --json author,baseRefName,state,isDraft,mergeStateStatus,headRefName,commits \
                   --jq '.author.login, .baseRefName, .state, (.isDraft|tostring), .mergeStateStatus, .headRefName, ([.commits[].authors[] | select(((.login // "") == "app/dependabot" or (.login // "") == "dependabot[bot]") | not)] | length), (.commits | length)' \
                   </dev/null 2>/dev/null); then
        reject_governance "could not reach the GitHub API for PR #$pr_number (failing closed)"
    fi
    mapfile -t meta <<<"$raw"
    if [[ "${#meta[@]}" -ne 8 ]]; then
        reject_governance "unexpected API response for PR #$pr_number (${#meta[@]} fields, expected 8) — failing closed"
    fi
    pr_author="${meta[0]}"
    pr_base="${meta[1]}"
    pr_state="${meta[2]}"
    pr_draft="${meta[3]}"
    pr_mergestate="${meta[4]}"
    pr_head="${meta[5]}"
    pr_foreign_commits="${meta[6]}"
    pr_commit_count="${meta[7]}"

    # `gh pr view --json author` returns the GraphQL login `app/dependabot`; the
    # REST API returns `dependabot[bot]`. Accept both so this does not silently
    # start rejecting if gh switches API surface underneath us.
    #
    # Note the `if` form rather than `[[ … ]] && author_ok=1`: under `set -e` a
    # failing `&&` on the LAST loop iteration makes the loop — and therefore this
    # function — return non-zero, which would exit the hook with neither 0 nor 2.
    author_ok=0
    for a in "${CARVE_OUT_AUTHORS[@]}"; do
        if [[ "$pr_author" == "$a" ]]; then author_ok=1; fi
    done
    [[ "$author_ok" -eq 1 ]] ||
        reject_governance "PR #$pr_number is authored by '$pr_author', not Dependabot"
    [[ "$pr_base" == "$CARVE_OUT_BASE" ]] ||
        reject_governance "PR #$pr_number targets '$pr_base', not '$CARVE_OUT_BASE'"
    # Load-bearing, not cosmetic: `gh pr checks` returns 0 for an already-merged
    # or closed PR, so without this test a stale merge command would sail past
    # step 3 on a PR that is no longer open.
    [[ "$pr_state" == "OPEN" ]] ||
        reject_governance "PR #$pr_number is $pr_state, not OPEN"
    [[ "$pr_draft" == "false" ]] ||
        reject_governance "PR #$pr_number is a draft"
    # `.author.login` says who OPENED the PR — it does not change when someone
    # else pushes to the head branch, and `dependabot/**` is in no ruleset. So
    # require the head branch to be Dependabot's, and every commit on it to be
    # authored by Dependabot.
    [[ "$pr_head" == "$CARVE_OUT_HEAD_PREFIX"* ]] ||
        reject_governance "PR #$pr_number head '$pr_head' is not a '$CARVE_OUT_HEAD_PREFIX' branch"
    #
    # The count of NON-Dependabot commit authors is computed by jq, not by
    # splitting a login list in the shell. Round 2 found three separate defects
    # in the string-splitting version, all of which this deletes rather than
    # patches: a commit whose author email is not linked to a GitHub account has
    # a NULL login and vanished from the list entirely; leading/trailing spaces
    # from such a null passed the "every author is Dependabot" loop; and
    # `dependabot[bot]` is itself a GLOB pattern, so unquoted word-splitting made
    # the verdict depend on which files happened to be in the process cwd.
    # `.login // ""` inside jq means an unlinked author counts as foreign.
    [[ "$pr_foreign_commits" =~ ^[0-9]+$ ]] ||
        reject_governance "PR #$pr_number returned a non-numeric commit-author count (failing closed)"
    [[ "$pr_foreign_commits" -eq 0 ]] ||
        reject_governance "PR #$pr_number has $pr_foreign_commits commit author(s) that are not Dependabot"
    # `gh pr view --json commits` truncates at 100, so on a longer PR the NEWEST
    # commits are the ones never author-checked. Refuse to guess.
    [[ "$pr_commit_count" =~ ^[0-9]+$ ]] ||
        reject_governance "PR #$pr_number returned a non-numeric commit count (failing closed)"
    [[ "$pr_commit_count" -gt 0 ]] ||
        reject_governance "PR #$pr_number reported no commits (failing closed)"
    [[ "$pr_commit_count" -lt 100 ]] ||
        reject_governance "PR #$pr_number has $pr_commit_count commits; the API list truncates at 100 (failing closed)"
    # THE authoritative check gate. `gh pr checks` rc=0 means only "nothing that
    # has posted is failing" — it is silent about a REQUIRED check that has not
    # posted at all, so a PR missing two of three required contexts still returns
    # 0. mergeStateStatus is GitHub's own verdict against the branch-protection
    # rules, so it is what actually has to be CLEAN.
    [[ "$pr_mergestate" == "CLEAN" ]] ||
        reject_governance "PR #$pr_number mergeStateStatus is $pr_mergestate, not CLEAN"

    # Step 3 — check status, as a cheap pre-filter behind mergeStateStatus. This
    # gh build has no `gh pr checks --json`, so the documented exit-code contract
    # IS the interface: 0 = all pass, 8 = pending, anything else = failing.
    # 124/137 are timeout's own SIGTERM/SIGKILL codes.
    set +e
    env -u GH_REPO -u GH_HOST timeout -k 5 "$GH_TIMEOUT" "$GH_BIN" pr checks "$pr_number" \
        --repo "$CARVE_OUT_REPO" </dev/null >/dev/null 2>&1
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
        "  author=$pr_author  head=$pr_head  base=$pr_base  state=$pr_state  mergeState=$pr_mergestate  commits=$pr_commit_count/all-dependabot" >&2
    exit 0
}

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
git_lead='(^|[[:space:]\;\|\&\(`])git([[:space:]]+[^[:space:]]+)*[[:space:]]+'
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
#
# The pattern is deliberately WIDER than the canonical form the gate accepts.
# Over-matching is safe — it just routes a command into the gate, which then
# rejects anything non-canonical. UNDER-matching is the dangerous direction: the
# hook's last statement is `exit 0`, so a merge that fails to match here runs
# with no verification whatsoever. Hence the leading class admits any
# non-identifier character, an optional leading path (`/usr/bin/gh`), and
# arbitrary root-level flags between `gh` and `pr` (`gh -R o/r pr merge`).
#
# Note the token loop appears TWICE — between `gh` and `pr`, and again between
# `pr` and `merge`. Only the first was present initially, and `gh pr -R o/r
# merge 5` slipped straight through: cobra resolves the subcommand past a
# parent flag, so that is a real merge invocation. The trailing `[^A-Za-z0-9_.-]*`
# on the `gh` token lets `$(which gh) pr merge 5` and its backtick form match
# once `$` has been stripped above.
gh_pr_merge_regex='(^|[^A-Za-z0-9_.-])([^[:space:]]*/)?gh[^A-Za-z0-9_.-]*([[:space:]]+[^[:space:]]+)*[[:space:]]+pr([[:space:]]+[^[:space:]]+)*[[:space:]]+merge([^[:alnum:]_-]|$)'
if [[ "$command_match" =~ $gh_pr_merge_regex ]]; then
    verify_carve_out_or_reject "$command"
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
