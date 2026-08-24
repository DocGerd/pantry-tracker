#!/usr/bin/env bash
# Test harness for block-dangerous-bash.sh.
#
# Run:  bash .claude/hooks/test-block-dangerous-bash.sh
#
# Why a script and not copy-pasted one-liners: the fixtures below contain the
# literal text `gh pr merge`, and the hook matches on raw command text. Pasting
# a fixture straight into a shell makes the hook fire on the *test invocation*
# itself. Keeping them in a file means the command that runs the suite is just
# `bash <path>`, which matches nothing.
#
# Cases tagged [net] reach the GitHub API. Offline or unauthenticated they still
# assert exit=2, because the hook fails closed — so the suite stays green either
# way, it just proves less. NET_OK is reported at the end so a green run cannot
# be mistaken for full coverage.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1
HOOK="$HERE/block-dangerous-bash.sh"
pass=0 fail=0
# The canonical form names the repo, so the command the gate validates and the
# command gh executes refer to the same repository.
R=" --repo DocGerd/pantry-tracker"

[[ -r "$HOOK" ]] || { echo "cannot read $HOOK"; exit 1; }

STUB_DIR=$(mktemp -d) || { echo "mktemp failed"; exit 1; }
[[ -n "$STUB_DIR" && -d "$STUB_DIR" ]] || { echo "mktemp produced no dir"; exit 1; }
trap 'rm -rf -- "$STUB_DIR"' EXIT

# payload <command-text>  — emits the hook's stdin JSON, or exits non-zero.
# Guarded: an unguarded failure here would make every expect-2 case "pass"
# vacuously, since the hook would receive empty stdin and fail closed anyway.
payload() {
    python3 -c 'import json,sys; print(json.dumps({"tool_input":{"command":sys.argv[1]}}))' "$1"
}

check() {  # check <expected_exit> <label> <actual> <stderr-file> [expected_reason]
    local expected="$1" label="$2" actual="$3" errfile="$4" reason="${5:-}"
    if [[ "$actual" != "$expected" ]]; then
        fail=$((fail + 1)); printf '  FAIL %-54s exit=%s (expected %s)\n' "$label" "$actual" "$expected"
        return
    fi
    if [[ -n "$reason" ]] && ! grep -qF -- "$reason" "$errfile"; then
        fail=$((fail + 1)); printf '  FAIL %-54s exit=%s but reason not %q\n' "$label" "$actual" "$reason"
        printf '       stderr: %s\n' "$(head -3 "$errfile" | tr '\n' ' ')"
        return
    fi
    pass=$((pass + 1)); printf '  ok   %-54s exit=%s\n' "$label" "$actual"
}

# run <expected_exit> <label> <command-text> [expected_reason_substring]
run() {
    local expected="$1" label="$2" cmd="$3" reason="${4:-}" actual p
    local err="$STUB_DIR/err"
    if ! p=$(payload "$cmd"); then
        fail=$((fail + 1)); printf '  FAIL %-54s (payload build failed)\n' "$label"; return
    fi
    printf '%s' "$p" | bash "$HOOK" >/dev/null 2>"$err"
    actual=$?
    check "$expected" "$label" "$actual" "$err" "$reason"
}

# raw <expected_exit> <label> <stdin-text>   — for malformed-input cases
raw() {
    local expected="$1" label="$2" body="$3" actual
    local err="$STUB_DIR/err"
    printf '%s' "$body" | bash "$HOOK" >/dev/null 2>"$err"
    actual=$?
    check "$expected" "$label" "$actual" "$err"
}

echo "== escape-hatch flags =="
run 2 "git commit --no-verify"          'git commit --no-verify -m x'
run 2 "--force-with-lease=<val> bypass" 'git push --force-with-lease=origin/main'
run 2 "bare --force"                    'git push --force origin feature/x'
run 2 "git -c lead-in + commit -n"      'git -c user.name=x commit -n -m foo'
run 2 "git push -f"                     'git push -f origin feature/x'
run 0 "plain git commit"                'git commit -m x'
run 0 "curl --no-verify-ssl (FP guard)" 'curl --no-verify-ssl https://x'
run 0 "tar -f (FP guard)"               'tar -xvf foo.tar'

echo "== fail-closed parsing =="
raw 2 "malformed stdin"                 'not json'
raw 2 "missing tool_input.command"      '{"tool_input":{}}'
run 0 "empty command string"            ''

echo "== push-to-main governance =="
run 2 "git push origin main"            'git push origin main'
run 2 "HEAD:main refspec"               'git push origin HEAD:main'
run 2 "delete refspec :main"            'git push origin :main'
run 2 "refs/heads/main"                 'git push origin refs/heads/main'
run 2 "HEAD:refs/heads/main"            'git push origin HEAD:refs/heads/main'
run 2 "force-refspec +main"             'git push origin +main'
run 2 "revspec suffix main^"            'git push origin main^'
run 2 "eval-wrapped push to main"       'eval "git push origin main"'
run 0 "main as SRC (main:foo)"          'git push origin main:foo'
run 0 "branch merely contains main"     'git push origin feature/main-cleanup'
run 0 "main after && (FP guard)"        'git push origin feature/foo && echo main'

echo "== merge gate: non-canonical shapes rejected =="
run 0 "gh pr view is not a merge"       'gh pr view 47'
run 2 "no explicit PR number"           'gh pr merge'                         'canonical merge form'
run 2 "--admin"                         'gh pr merge 289 --admin'             'canonical merge form'
run 2 "--admin with backslash"          'gh pr merge 289 --ad\min'            'canonical merge form'
run 2 "--auto defers past the snapshot" 'gh pr merge --auto 4242 --merge'     'canonical merge form'
run 2 "&& chaining"                     'gh pr checks 1 && gh pr merge 1'     'canonical merge form'
run 2 "newline chaining"                'gh pr merge 4242 --merge
gh pr merge 4243 --merge'                                                     'canonical merge form'
run 2 "bare & background operator"      'sleep 1 & gh pr merge 4242 --merge'  'canonical merge form'
run 2 "semicolon chaining"              'gh pr merge 4242; echo done'         'canonical merge form'
run 2 "process substitution"            'gh pr merge 4242 --body <(cat x)'    'canonical merge form'
run 2 "flag value harvested as PR num"  'gh pr merge --body 4242 my-branch'   'canonical merge form'
run 2 "branch positional, not a number" 'gh pr merge my-feature-branch'       'canonical merge form'
run 2 "URL positional"                  'gh pr merge https://github.com/o/r/pull/5' 'canonical merge form'
run 2 "--repo retarget"                 'gh pr merge 4242 --repo evil/other'  'canonical merge form'
run 2 "-R retarget"                     'gh pr merge 4242 -R evil/other'      'canonical merge form'
run 2 "GH_REPO= env prefix"             'GH_REPO=evil/other gh pr merge 4242' 'canonical merge form'
run 2 "path-qualified binary"           '/usr/bin/gh pr merge 4242 --merge'   'canonical merge form'
run 2 "root-level flag before pr"       'gh -R evil/other pr merge 4242'      'canonical merge form'
run 2 "unicode digits"                  'gh pr merge ٤٢'                      'canonical merge form'
run 2 "two integers"                    'gh pr merge 1 2'                     'canonical merge form'
run 2 "trailing garbage on number"      'gh pr merge 5x'                      'canonical merge form'
# Single quotes are deliberate: these two must reach the hook as the LITERAL
# text. Expanding them would run a real merge.
# shellcheck disable=SC2016
run 2 "subshell form"                   'echo $(gh pr merge 47)'              'canonical merge form'
# shellcheck disable=SC2016
run 2 "backtick form"                   'echo `gh pr merge 47`'               'canonical merge form'

echo "== matcher under-match regressions (round 2) =="
# Each of these is a TOTAL bypass if the matcher misses it: the hook's last
# statement is `exit 0`, so an unmatched merge runs with no verification at all.
run 2 "flag between pr and merge"       'gh pr -R evil/other merge 4242 --merge'   'canonical merge form'
run 2 "--repo between pr and merge"     'gh pr --repo evil/other merge 4242'       'canonical merge form'
run 2 "backslash inside merge"          'gh pr merg\e 4242 --admin'                'canonical merge form'
run 2 "backslash inside pr"             'gh p\r merge 4242 --merge'                'canonical merge form'
run 2 "backslash inside gh"             'g\h pr merge 4242 --merge'                'canonical merge form'
run 2 "backslash before merge"          'gh pr \merge 4242 --merge'                'canonical merge form'
# shellcheck disable=SC2016
run 2 "ANSI-C quoted subcommand"        'gh pr $'"'"'merge'"'"' 4242'              'canonical merge form'
# shellcheck disable=SC2016
run 2 "binary via command substitution" '$(which gh) pr merge 4242 --merge'        'canonical merge form'
run 2 "backslash inside main (push)"    'git push origin ma\in'
run 2 "aliasing the subcommand"         'gh alias set m pr\ merge'                 'canonical merge form'

echo "== expansion-evasion families (round 3) =="
# Every case here is a command bash EXECUTES as a merge (or a push to main) while
# the literal text spells something else. A single lossy rewrite cannot catch
# these -- stripping `$` turns `merge$x` into `mergex` and DESTROYS the match --
# which is why the hook matches a union of normalizations.
# shellcheck disable=SC2016
run 2 "sigil-adjacent expansion"        'gh pr merge$x 4242 --admin'               'canonical merge form'
# shellcheck disable=SC2016
run 2 "braced expansion adjacent"       'gh pr merge${Z} 4242 --admin'             'canonical merge form'
# shellcheck disable=SC2016
run 2 "IFS as separator"                'gh${IFS}pr${IFS}merge${IFS}4242'          'canonical merge form'
# shellcheck disable=SC2016
run 2 "expansion inside keyword"        'gh pr me${x}rge 4242 --admin'             'canonical merge form'
# shellcheck disable=SC2016
run 2 "default-value expansion"         'gh pr ${x-merge} 4242 --admin'            'canonical merge form'
run 2 "ANSI-C octal keyword"            'gh pr $'"'"'\155erge'"'"' 4242 --admin'   'canonical merge form'
run 2 "ANSI-C hex keyword"              'gh pr $'"'"'\x6derge'"'"' 4242'           'canonical merge form'
run 2 "brace list splits the words"     'gh pr {merge,4242}'                       'canonical merge form'
run 2 "line continuation splits merge"  'gh pr mer\
ge 4242 --admin'                                                                   'canonical merge form'
# The push-to-main branch is the CORE rule, not the carve-out -- same families.
# shellcheck disable=SC2016
run 2 "push to main via expansion"      'git push origin main$x'
run 2 "push to main via ANSI-C"         'git push origin $'"'"'\x6dain'"'"''
run 2 "push to main via continuation"   'git push origin ma\
in'
# Escape-hatch flags were previously tested only against the raw text.
run 2 "quoted --force"                  'git push "--force" origin x'
run 2 "backslashed --force"             'git push --fo\rce origin x'

echo "== merge gate: API merge vectors refused outright =="
run 2 "gh api PUT pulls/N/merge"        'gh api -X PUT repos/o/r/pulls/5/merge'    'GitHub API'
run 2 "gh api --method PUT"             'gh api --method PUT repos/o/r/pulls/5/merge' 'GitHub API'
run 2 "graphql mergePullRequest"        'gh api graphql -f query=mergePullRequest' 'GitHub API'

echo "== merge gate: live API (canonical shape, real PR) =="
run 2 "[net] merged PR rejected"        "gh pr merge 289$R --merge"                 'is MERGED, not OPEN'
run 2 "[net] nonexistent PR"            "gh pr merge 999999$R --merge"

# --- stubbed-gh cases ---------------------------------------------------------
# The live cases above can only ever assert a REJECT — there is rarely an open,
# green Dependabot PR lying around, and a suite that never exercises the allow
# path would pass just as happily if the carve-out were dead code.
#
# The hook pins GH_BIN to an absolute path on purpose (a bare `gh` would resolve
# through a user-writable $PATH). Rather than adding a production override — a
# backdoor in the thing being secured — the seam is a one-line rewritten COPY of
# the hook, and we assert that exactly one line changed.
STUB_HOOK="$STUB_DIR/hook.sh"
sed "s#^GH_BIN=.*#GH_BIN=\"$STUB_DIR/gh\"#" "$HOOK" >"$STUB_HOOK" || exit 1
if [[ "$(diff <(sed 's/[[:space:]]*$//' "$HOOK") <(sed 's/[[:space:]]*$//' "$STUB_HOOK") | grep -c '^<')" != "1" ]]; then
    echo "FATAL: stub seam rewrote != 1 line of the hook; refusing to trust the stubbed results"
    exit 1
fi

# Contract-asserting stub. Exits 9 if the hook asks it anything unexpected, so a
# hook that queries the WRONG PR, drops --repo, or changes its --json field list
# fails the suite instead of silently passing.
cat >"$STUB_DIR/gh" <<'STUB'
#!/usr/bin/env bash
if [[ "${1:-}" == "pr" && "${2:-}" == "view" ]]; then
    [[ "${3:-}" == "$STUB_EXPECT_PR" ]] || { echo "stub: pr view ${3:-} != $STUB_EXPECT_PR" >&2; exit 9; }
    [[ "$*" == *"--repo $STUB_EXPECT_REPO"* ]] || { echo "stub: missing --repo $STUB_EXPECT_REPO" >&2; exit 9; }
    [[ "$*" == *"author,baseRefName,state,isDraft,mergeStateStatus,headRefName,commits"* ]] \
        || { echo "stub: unexpected --json field list" >&2; exit 9; }
    printf '%s\n' "$STUB_AUTHOR" "$STUB_BASE" "$STUB_STATE" "$STUB_DRAFT" \
                  "$STUB_MERGESTATE" "$STUB_HEAD" "$STUB_FOREIGN" "$STUB_NCOMMITS"
    exit 0
fi
if [[ "${1:-}" == "pr" && "${2:-}" == "checks" ]]; then
    [[ "${3:-}" == "$STUB_EXPECT_PR" ]] || { echo "stub: pr checks ${3:-} != $STUB_EXPECT_PR" >&2; exit 9; }
    exit "$STUB_CHECKS_RC"
fi
echo "stub: unexpected invocation: $*" >&2
exit 9
STUB
chmod +x "$STUB_DIR/gh"

export STUB_EXPECT_PR=4242 STUB_EXPECT_REPO="DocGerd/pantry-tracker"

# stub <expected> <label> <author> <base> <state> <draft> <mergestate> <head> <foreigncommits> <ncommits> <rc> [reason]
stub() {
    local expected="$1" label="$2" reason="${12:-}" actual p
    local err="$STUB_DIR/err"
    export STUB_AUTHOR="$3" STUB_BASE="$4" STUB_STATE="$5" STUB_DRAFT="$6" \
           STUB_MERGESTATE="$7" STUB_HEAD="$8" STUB_FOREIGN="$9" STUB_NCOMMITS="${10}" STUB_CHECKS_RC="${11}"
    if ! p=$(payload "${STUB_CMD:-gh pr merge 4242$R --merge}"); then
        fail=$((fail + 1)); printf '  FAIL %-54s (payload build failed)\n' "$label"; return
    fi
    printf '%s' "$p" | bash "$STUB_HOOK" >/dev/null 2>"$err"
    actual=$?
    check "$expected" "$label" "$actual" "$err" "$reason"
}

D=app/dependabot
echo "== carve-out conditions, stubbed gh =="
stub 0 "ALLOW: dependabot->develop, clean"  "$D" develop OPEN false CLEAN dependabot/x 0 1 0
stub 0 "ALLOW: REST login spelling" 'dependabot[bot]' develop OPEN false CLEAN dependabot/x 0 1 0
# The gate accepts four canonical shapes; three were previously untested, so
# a regex that silently stopped accepting them would not have failed the suite.
STUB_CMD="gh pr merge 4242$R"                          stub 0 "ALLOW: bare form"           "$D" develop OPEN false CLEAN dependabot/x 0 1 0
STUB_CMD="gh pr merge 4242$R --squash --delete-branch" stub 0 "ALLOW: squash+delete"        "$D" develop OPEN false CLEAN dependabot/x 0 1 0
STUB_CMD="gh pr merge 4242$R --rebase"                 stub 0 "ALLOW: rebase"               "$D" develop OPEN false CLEAN dependabot/x 0 1 0
STUB_CMD="gh pr merge 4242$R --delete-branch --merge"  stub 2 "REJECT: reversed flag order" "$D" develop OPEN false CLEAN dependabot/x 0 1 0 "canonical merge form"
STUB_CMD="gh pr merge  4242$R --merge"                 stub 2 "REJECT: double space"        "$D" develop OPEN false CLEAN dependabot/x 0 1 0 "canonical merge form"
STUB_CMD="gh pr merge 4242 --merge"                    stub 2 "REJECT: repo not named"      "$D" develop OPEN false CLEAN dependabot/x 0 1 0 "canonical merge form"
unset STUB_CMD
stub 2 "author is a human"          DocGerd develop OPEN false CLEAN dependabot/x 0 1 0 "not Dependabot"
stub 2 "author is another bot"      app/renovate develop OPEN false CLEAN dependabot/x 0 1 0 "not Dependabot"
stub 2 "base is main"               "$D" main    OPEN false CLEAN dependabot/x 0 1 0 "not 'develop'"
stub 2 "base is a release branch"   "$D" release/1.5.0 OPEN false CLEAN dependabot/x 0 1 0 "not 'develop'"
stub 2 "PR already merged"          "$D" develop MERGED false CLEAN dependabot/x 0 1 0 "not OPEN"
stub 2 "PR closed"                  "$D" develop CLOSED false CLEAN dependabot/x 0 1 0 "not OPEN"
stub 2 "PR is a draft"              "$D" develop OPEN true  CLEAN dependabot/x 0 1 0 "is a draft"
stub 2 "head branch not dependabot" "$D" develop OPEN false CLEAN feature/evil 0 1 0 "is not a 'dependabot/' branch"
stub 2 "a commit by someone else"   "$D" develop OPEN false CLEAN dependabot/x 1 2 0 "not Dependabot"
stub 2 "mergeState BLOCKED"         "$D" develop OPEN false BLOCKED dependabot/x 0 1 0 "not CLEAN"
stub 2 "mergeState BEHIND"          "$D" develop OPEN false BEHIND  dependabot/x 0 1 0 "not CLEAN"
stub 2 "mergeState UNSTABLE"        "$D" develop OPEN false UNSTABLE dependabot/x 0 1 0 "not CLEAN"
stub 2 "checks pending (rc=8)"      "$D" develop OPEN false CLEAN dependabot/x 0 1 8 "pending checks"
stub 2 "checks failing (rc=1)"      "$D" develop OPEN false CLEAN dependabot/x 0 1 1 "failing checks"
stub 2 "checks timed out (rc=124)"  "$D" develop OPEN false CLEAN dependabot/x 0 1 124 "timed out"
stub 2 "pipe in branch name"        "$D" 'develop|OPEN|false' OPEN false CLEAN dependabot/x 0 1 0 "not 'develop'"
# Round-2 findings: the foreign-author count is computed by jq (a null login —
# an author email with no linked GitHub account — counts as foreign), and the
# commit list truncates at 100, where the NEWEST commits are the unchecked ones.
stub 2 "two foreign commit authors"  "$D" develop OPEN false CLEAN dependabot/x 2 5 0 "2 commit author(s) that are not Dependabot"
stub 2 "commit list truncated at 100" "$D" develop OPEN false CLEAN dependabot/x 0 100 0 "truncates at 100"
stub 2 "zero commits reported"       "$D" develop OPEN false CLEAN dependabot/x 0 0 0 "reported no commits"
stub 2 "non-numeric foreign count"   "$D" develop OPEN false CLEAN dependabot/x '' 1 0 "non-numeric"
stub 2 "non-numeric commit count"    "$D" develop OPEN false CLEAN dependabot/x 0 'null' 0 "non-numeric"

echo "== allow path emits an audit line =="
export STUB_AUTHOR="$D" STUB_BASE=develop STUB_STATE=OPEN STUB_DRAFT=false \
       STUB_MERGESTATE=CLEAN STUB_HEAD=dependabot/x STUB_FOREIGN=0 STUB_NCOMMITS=1 STUB_CHECKS_RC=0
if payload "gh pr merge 4242$R --merge" | bash "$STUB_HOOK" 2>"$STUB_DIR/err" >/dev/null &&
   grep -q "allowing merge of PR #4242" "$STUB_DIR/err"; then
    pass=$((pass + 1)); printf '  ok   %-54s\n' "audit line on stderr"
else
    fail=$((fail + 1)); printf '  FAIL %-54s\n' "audit line on stderr"
fi

NET_OK="no"
if timeout -k 5 20 gh auth status </dev/null >/dev/null 2>&1; then NET_OK="yes"; fi

echo
echo "passed=$pass failed=$fail   gh-authenticated=$NET_OK"
[[ "$NET_OK" == "yes" ]] || echo "  NOTE: [net] cases passed only via the fail-closed path — coverage is partial."

[[ "$fail" -eq 0 ]]
