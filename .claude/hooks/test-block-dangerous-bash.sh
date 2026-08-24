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
# The hook makes no network calls and has no allow path for merges, so every
# case here is deterministic and offline. The carve-out it used to gate now runs
# through the GitHub MCP tool, which is not reachable from a Bash command and so
# is out of this suite's scope.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1
HOOK="$HERE/block-dangerous-bash.sh"
pass=0 fail=0

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

echo "== PR merge from the shell: always refused =="
run 0 "gh pr view is not a merge"       'gh pr view 47'
run 2 "no explicit PR number"           'gh pr merge'                         'PR merge from the shell'
run 2 "--admin"                         'gh pr merge 289 --admin'             'PR merge from the shell'
run 2 "--admin with backslash"          'gh pr merge 289 --ad\min'            'PR merge from the shell'
run 2 "--auto defers past the snapshot" 'gh pr merge --auto 4242 --merge'     'PR merge from the shell'
run 2 "&& chaining"                     'gh pr checks 1 && gh pr merge 1'     'PR merge from the shell'
run 2 "newline chaining"                'gh pr merge 4242 --merge
gh pr merge 4243 --merge'                                                     'PR merge from the shell'
run 2 "bare & background operator"      'sleep 1 & gh pr merge 4242 --merge'  'PR merge from the shell'
run 2 "semicolon chaining"              'gh pr merge 4242; echo done'         'PR merge from the shell'
run 2 "process substitution"            'gh pr merge 4242 --body <(cat x)'    'PR merge from the shell'
run 2 "flag value harvested as PR num"  'gh pr merge --body 4242 my-branch'   'PR merge from the shell'
run 2 "branch positional, not a number" 'gh pr merge my-feature-branch'       'PR merge from the shell'
run 2 "URL positional"                  'gh pr merge https://github.com/o/r/pull/5' 'PR merge from the shell'
run 2 "--repo retarget"                 'gh pr merge 4242 --repo evil/other'  'PR merge from the shell'
run 2 "-R retarget"                     'gh pr merge 4242 -R evil/other'      'PR merge from the shell'
run 2 "GH_REPO= env prefix"             'GH_REPO=evil/other gh pr merge 4242' 'PR merge from the shell'
run 2 "path-qualified binary"           '/usr/bin/gh pr merge 4242 --merge'   'PR merge from the shell'
run 2 "root-level flag before pr"       'gh -R evil/other pr merge 4242'      'PR merge from the shell'
run 2 "unicode digits"                  'gh pr merge ٤٢'                      'PR merge from the shell'
run 2 "two integers"                    'gh pr merge 1 2'                     'PR merge from the shell'
run 2 "trailing garbage on number"      'gh pr merge 5x'                      'PR merge from the shell'
# Single quotes are deliberate: these two must reach the hook as the LITERAL
# text. Expanding them would run a real merge.
# shellcheck disable=SC2016
run 2 "subshell form"                   'echo $(gh pr merge 47)'              'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "backtick form"                   'echo `gh pr merge 47`'               'PR merge from the shell'

echo "== matcher under-match regressions (round 2) =="
# Each of these is a TOTAL bypass if the matcher misses it: the hook's last
# statement is `exit 0`, so an unmatched merge runs with no verification at all.
run 2 "flag between pr and merge"       'gh pr -R evil/other merge 4242 --merge'   'PR merge from the shell'
run 2 "--repo between pr and merge"     'gh pr --repo evil/other merge 4242'       'PR merge from the shell'
run 2 "backslash inside merge"          'gh pr merg\e 4242 --admin'                'PR merge from the shell'
run 2 "backslash inside pr"             'gh p\r merge 4242 --merge'                'PR merge from the shell'
run 2 "backslash inside gh"             'g\h pr merge 4242 --merge'                'PR merge from the shell'
run 2 "backslash before merge"          'gh pr \merge 4242 --merge'                'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "ANSI-C quoted subcommand"        'gh pr $'"'"'merge'"'"' 4242'              'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "binary via command substitution" '$(which gh) pr merge 4242 --merge'        'PR merge from the shell'
run 2 "backslash inside main (push)"    'git push origin ma\in'
run 2 "aliasing the subcommand"         'gh alias set m pr\ merge'                 'PR merge from the shell'

echo "== expansion-evasion families (round 3) =="
# Every case here is a command bash EXECUTES as a merge (or a push to main) while
# the literal text spells something else. A single lossy rewrite cannot catch
# these -- stripping `$` turns `merge$x` into `mergex` and DESTROYS the match --
# which is why the hook matches a union of normalizations.
# shellcheck disable=SC2016
run 2 "sigil-adjacent expansion"        'gh pr merge$x 4242 --admin'               'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "braced expansion adjacent"       'gh pr merge${Z} 4242 --admin'             'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "IFS as separator"                'gh${IFS}pr${IFS}merge${IFS}4242'          'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "expansion inside keyword"        'gh pr me${x}rge 4242 --admin'             'PR merge from the shell'
# shellcheck disable=SC2016
run 2 "default-value expansion"         'gh pr ${x-merge} 4242 --admin'            'PR merge from the shell'
run 2 "ANSI-C octal keyword"            'gh pr $'"'"'\155erge'"'"' 4242 --admin'   'PR merge from the shell'
run 2 "ANSI-C hex keyword"              'gh pr $'"'"'\x6derge'"'"' 4242'           'PR merge from the shell'
run 2 "brace list splits the words"     'gh pr {merge,4242}'                       'PR merge from the shell'
run 2 "line continuation splits merge"  'gh pr mer\
ge 4242 --admin'                                                                   'PR merge from the shell'
# The push-to-main branch is the CORE rule, not the carve-out -- same families.
# shellcheck disable=SC2016
run 2 "push to main via expansion"      'git push origin main$x'
run 2 "push to main via ANSI-C"         'git push origin $'"'"'\x6dain'"'"''
run 2 "push to main via continuation"   'git push origin ma\
in'
# Escape-hatch flags were previously tested only against the raw text.
run 2 "quoted --force"                  'git push "--force" origin x'
run 2 "backslashed --force"             'git push --fo\rce origin x'

echo "== false-positive guards: a match must not straddle lines =="
# The spanning loops once used [[:space:]] (which includes newline), so a match
# could straddle the newline-joined candidates of the union probe. These are all
# ordinary commands that were being BLOCKED. A guard that fires on normal work
# teaches you to route around it.
run 0 "word on an earlier line than gh pr"  'echo "closed by the merge?"
gh pr list --state open'
run 0 "word after an unrelated gh pr"       'gh pr list --state open
echo "the merge landed"'
run 0 "gh pr view then the word"            'gh pr view 298
echo "done: merge"'
run 0 "word alone on its own line"          'echo merge
git status'
run 0 "git push on one line, main on next"  'git push origin feature/x
echo main'
# ...but a real invocation ON ONE LINE of a multi-line command must STILL block.
run 2 "real one on a later line"            'echo hello
gh pr merge 5 --squash'
run 2 "real one on an earlier line"         'gh pr merge 5
echo done'
run 2 "real push to main on a later line"   'echo hello
git push origin main'

echo "== API merge vectors also refused =="
run 2 "gh api PUT pulls/N/merge"        'gh api -X PUT repos/o/r/pulls/5/merge'    'GitHub API'
run 2 "gh api --method PUT"             'gh api --method PUT repos/o/r/pulls/5/merge' 'GitHub API'
run 2 "graphql mergePullRequest"        'gh api graphql -f query=mergePullRequest' 'GitHub API'

NET_OK="no"
if timeout -k 5 20 gh auth status </dev/null >/dev/null 2>&1; then NET_OK="yes"; fi

echo
echo "passed=$pass failed=$fail   gh-authenticated=$NET_OK"
[[ "$NET_OK" == "yes" ]] || echo "  NOTE: [net] cases passed only via the fail-closed path — coverage is partial."

[[ "$fail" -eq 0 ]]
