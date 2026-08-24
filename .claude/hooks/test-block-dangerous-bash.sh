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

HOOK="$(dirname "${BASH_SOURCE[0]}")/block-dangerous-bash.sh"
pass=0 fail=0

# run <expected_exit> <label> <command-text>
run() {
    local expected="$1" label="$2" cmd="$3" actual payload
    payload=$(python3 -c 'import json,sys; print(json.dumps({"tool_input":{"command":sys.argv[1]}}))' "$cmd")
    printf '%s' "$payload" | bash "$HOOK" >/dev/null 2>&1
    actual=$?
    if [[ "$actual" == "$expected" ]]; then
        pass=$((pass + 1))
        printf '  ok   %-58s exit=%s\n' "$label" "$actual"
    else
        fail=$((fail + 1))
        printf '  FAIL %-58s exit=%s (expected %s)\n' "$label" "$actual" "$expected"
    fi
}

# raw <expected_exit> <label> <stdin-text>   — for malformed-input cases
raw() {
    local expected="$1" label="$2" body="$3" actual
    printf '%s' "$body" | bash "$HOOK" >/dev/null 2>&1
    actual=$?
    if [[ "$actual" == "$expected" ]]; then
        pass=$((pass + 1)); printf '  ok   %-58s exit=%s\n' "$label" "$actual"
    else
        fail=$((fail + 1)); printf '  FAIL %-58s exit=%s (expected %s)\n' "$label" "$actual" "$expected"
    fi
}

echo "== escape-hatch flags =="
run 2 "git commit --no-verify"          'git commit --no-verify -m x'
run 2 "--force-with-lease=<val> bypass" 'git push --force-with-lease=origin/main'
run 2 "git -c lead-in + commit -n"      'git -c user.name=x commit -n -m foo'
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
run 2 "force-refspec +main"             'git push origin +main'
run 2 "eval-wrapped push to main"       'eval "git push origin main"'
run 0 "main as SRC (main:foo)"          'git push origin main:foo'
run 0 "branch merely contains main"     'git push origin feature/main-cleanup'
run 0 "main after && (FP guard)"        'git push origin feature/foo && echo main'

echo "== merge carve-out (#297) =="
run 0 "gh pr view is not a merge"       'gh pr view 47'
run 2 "no explicit PR number"           'gh pr merge'
run 2 "--admin never allowed"           'gh pr merge 289 --admin'
run 2 "compound command"                'gh pr checks 1 && gh pr merge 1'
run 2 "ambiguous: two integers"         'gh pr merge 1 2'
# Single quotes are deliberate here: these fixtures must reach the hook as the
# LITERAL text `$(...)` / backticks. Expanding them would run the merge for real.
# shellcheck disable=SC2016
run 2 "subshell form"                   'echo $(gh pr merge 47)'
# shellcheck disable=SC2016
run 2 "backtick form"                   'echo `gh pr merge 47`'
run 2 "[net] merged PR rejected"        'gh pr merge 289 --merge'
run 2 "[net] nonexistent PR"            'gh pr merge 999999 --merge'

# --- stubbed-gh cases ---------------------------------------------------------
# The live cases above can only ever assert a REJECT — there is rarely an open,
# green Dependabot PR lying around, and a suite that never exercises the allow
# path would pass just as happily if the carve-out were dead code. So: put a
# fake `gh` on PATH and drive each condition independently. `timeout` resolves
# its child through PATH (execvp), so the stub is picked up by the hook's
# `timeout -k 5 25 gh ...` calls unchanged.
STUB_DIR=$(mktemp -d)
trap 'rm -rf "$STUB_DIR"' EXIT
cat >"$STUB_DIR/gh" <<'STUB'
#!/usr/bin/env bash
# Minimal `gh` stand-in: understands `pr view` and `pr checks`, driven by env.
if [[ "${1:-}" == "pr" && "${2:-}" == "view" ]]; then
    printf '%s|%s|%s|%s\n' "$STUB_AUTHOR" "$STUB_BASE" "$STUB_STATE" "$STUB_DRAFT"
    exit 0
fi
if [[ "${1:-}" == "pr" && "${2:-}" == "checks" ]]; then
    exit "$STUB_CHECKS_RC"
fi
exit 1
STUB
chmod +x "$STUB_DIR/gh"

# stub <expected> <label> <author> <base> <state> <draft> <checks_rc>
stub() {
    local expected="$1" label="$2" actual payload
    export STUB_AUTHOR="$3" STUB_BASE="$4" STUB_STATE="$5" STUB_DRAFT="$6" STUB_CHECKS_RC="$7"
    payload=$(python3 -c 'import json,sys; print(json.dumps({"tool_input":{"command":sys.argv[1]}}))' 'gh pr merge 4242 --merge')
    printf '%s' "$payload" | PATH="$STUB_DIR:$PATH" bash "$HOOK" >/dev/null 2>&1
    actual=$?
    if [[ "$actual" == "$expected" ]]; then
        pass=$((pass + 1)); printf '  ok   %-58s exit=%s\n' "$label" "$actual"
    else
        fail=$((fail + 1)); printf '  FAIL %-58s exit=%s (expected %s)\n' "$label" "$actual" "$expected"
    fi
}

echo "== carve-out conditions, stubbed gh =="
stub 0 "ALLOW: dependabot->develop, green"  'app/dependabot'  develop OPEN   false 0
stub 0 "ALLOW: REST login spelling"         'dependabot[bot]' develop OPEN   false 0
stub 2 "author is a human"                  'DocGerd'         develop OPEN   false 0
stub 2 "author is another bot"              'app/renovate'    develop OPEN   false 0
stub 2 "base is main, not develop"          'app/dependabot'  main    OPEN   false 0
stub 2 "base is a release branch"           'app/dependabot'  release/1.5.0 OPEN false 0
stub 2 "PR already merged"                  'app/dependabot'  develop MERGED false 0
stub 2 "PR closed"                          'app/dependabot'  develop CLOSED false 0
stub 2 "PR is a draft"                      'app/dependabot'  develop OPEN   true  0
stub 2 "checks pending (rc=8)"              'app/dependabot'  develop OPEN   false 8
stub 2 "checks failing (rc=1)"              'app/dependabot'  develop OPEN   false 1
stub 2 "checks timed out (rc=124)"          'app/dependabot'  develop OPEN   false 124
unset STUB_AUTHOR STUB_BASE STUB_STATE STUB_DRAFT STUB_CHECKS_RC

NET_OK="no"
if timeout -k 5 20 gh auth status </dev/null >/dev/null 2>&1; then NET_OK="yes"; fi

echo
echo "passed=$pass failed=$fail   gh-authenticated=$NET_OK"
[[ "$NET_OK" == "yes" ]] || echo "  NOTE: [net] cases passed only via the fail-closed path — coverage is partial."

[[ "$fail" -eq 0 ]]
