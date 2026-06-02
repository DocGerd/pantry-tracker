#!/usr/bin/env bash
# PreToolUse hook: reject `./gradlew wrapper` invocations that would drop the
# distributionSha256Sum pin from gradle/wrapper/gradle-wrapper.properties.
#
# Why this guard exists:
#   `gradle/wrapper/gradle-wrapper.properties` pins BOTH `distributionUrl` and
#   `distributionSha256Sum` (SR-5: defends against a MITM swapping the Gradle
#   distribution archive). The Gradle `Wrapper` task only WRITES the SHA when it
#   is told the value — either via the `--gradle-distribution-sha256-sum` CLI
#   flag or task configuration. This repo pins the SHA in the .properties file
#   directly (the Wrapper task is NOT configured with it), so ANY `wrapper`
#   regeneration that omits the flag rewrites the file and SILENTLY DROPS the
#   pin. The wrapper then refuses to start on the *next* invocation — by which
#   point the bad properties file is already written/committed (commit 4bd41d8
#   shows this already bit once). `ci.yml`'s SR-5 assertion catches the missing
#   pin, but only on push to main/develop or on a PR; a plain feature-branch
#   push runs nothing, so an unpinned wrapper lands silently and breaks the
#   local dev loop before CI ever sees it.
#
# Rule:
#   Block (exit 2) when the command is a `./gradlew` invocation running the
#   `wrapper` task UNLESS the same command also carries
#   `--gradle-distribution-sha256-sum` (the only flag that re-writes the SHA
#   atomically). This deliberately also blocks BARE `./gradlew wrapper` — with
#   no Wrapper-task SHA config in build.gradle.kts, a bare regeneration drops
#   the pin just as `--gradle-version` does. The error message quotes the
#   atomic two-flag form from docs/release/SHIPPING.md § "`distributionSha256Sum`
#   must move atomically with `distributionUrl`".
#
# Fail-closed: if JSON parsing fails (python3 missing, malformed input, schema
# changed), the hook exits 2 with a diagnostic — refusing the action is the
# only safe default for a guard hook. A guard that silently allows when it
# can't tell what's happening is worse than no guard at all.
#
# The command is split at shell separators (`;` `|` `&` backtick `(` `)`
# newline) and each segment judged in isolation, so a token or flag in a
# DIFFERENT command segment never crosses over — `./gradlew tasks | grep
# wrapper` is correctly allowed, and `./gradlew wrapper; echo
# --gradle-distribution-sha256-sum` is correctly blocked (the flag lives in the
# wrong segment). See the segment-splitting comment in the body.
#
# Known limitations (consistent with block-dangerous-bash.sh):
# - Matches on raw command text WITHIN a segment. A `wrapper` token used as an
#   argument *value* in the same gradlew segment (e.g. `./gradlew help --task
#   wrapper`), or echoing the literal `./gradlew wrapper …` as documentation in
#   one segment, still false-positives. Prefer the Write/Edit tools for such
#   text, or add the SHA flag.
# - Cannot catch deferred shell expansion (`t=wrapper; ./gradlew $t`); the
#   literal text carries no `wrapper` task-token at hook-evaluation time.
#
# Test:
#   echo '{"tool_input":{"command":"./gradlew wrapper --gradle-version 9.6"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=2 (unpinned wrapper upgrade — must block).
#   echo '{"tool_input":{"command":"./gradlew wrapper"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=2 (bare regeneration also drops the SHA — no task config).
#   echo '{"tool_input":{"command":"./gradlew wrapper --gradle-version 9.6 --gradle-distribution-sha256-sum abc123"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (atomic two-flag form — allowed).
#   echo '{"tool_input":{"command":"./gradlew :wrapper --gradle-distribution-sha256-sum=abc"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (:wrapper task token + =-attached SHA flag — allowed).
#   echo '{"tool_input":{"command":"./gradlew :app:test"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (no wrapper task — unrelated gradle task).
#   echo '{"tool_input":{"command":"./gradlew :app:assembleDebug --rerun-tasks"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (no wrapper task).
#   echo '{"tool_input":{"command":"git status gradle/wrapper/gradle-wrapper.properties"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (wrapper inside a path, not a gradlew task token).
#   echo '{"tool_input":{"command":"./gradlew wrapper; echo --gradle-distribution-sha256-sum=fake"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=2 (segment-anchored: a SHA flag in a DIFFERENT segment must NOT excuse the bare wrapper).
#   echo '{"tool_input":{"command":"./gradlew clean --gradle-distribution-sha256-sum x; ./gradlew wrapper"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=2 (the bare wrapper segment lacks its own SHA flag).
#   echo '{"tool_input":{"command":"./gradlew clean && ./gradlew wrapper --gradle-version 9.6 --gradle-distribution-sha256-sum=x"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (positive control: the SHA flag belongs to the wrapper segment within a chain).
#   echo '{"tool_input":{"command":"./gradlew tasks --all | grep wrapper"}}' \
#     | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=0 (segment-anchored: a `wrapper` token in a DIFFERENT segment is not the wrapper task).
#   echo 'not json' | bash .claude/hooks/block-gradle-wrapper-pin.sh ; echo "exit=$?"
# Expected: exit=2 (fail-closed on parse failure).
set -euo pipefail

input="$(cat)"

# Fail-closed JSON parsing — same shape as block-dangerous-bash.sh /
# block-lockfile-edits.sh for consistency. python3 must exist, JSON must parse,
# tool_input.command must be present (the Bash tool always populates it). Any
# failure → exit 2.
if ! command=$(printf '%s' "$input" | python3 -c '
import json, sys
d = json.load(sys.stdin)
ti = d.get("tool_input", {})
if "command" not in ti:
    sys.exit(3)
print(ti["command"])
'); then
    cat >&2 <<MSG
block-gradle-wrapper-pin hook: failed to parse Bash tool input.
Failing closed — exiting 2 to block the action.

Possible causes:
- python3 missing from PATH
- malformed JSON on stdin
- tool_input.command field absent or schema changed

If this is a false alarm, fix the hook before re-running the action.
MSG
    exit 2
fi

# Empty command string is benign (no command to inspect).
if [[ -z "$command" ]]; then
    exit 0
fi

reject() {
    cat >&2 <<MSG
Refusing to run this command: it regenerates the Gradle wrapper WITHOUT pinning
distributionSha256Sum, which silently drops the SR-5 SHA pin from
gradle/wrapper/gradle-wrapper.properties.

Offending invocation: $1
Full command: $command

Use the atomic two-flag form (the ONLY invocation that rewrites both
distributionUrl and distributionSha256Sum together), e.g.:

    ./gradlew wrapper --gradle-version X.Y.Z \\
      --gradle-distribution-sha256-sum \$(curl -sSL \\
        https://services.gradle.org/distributions/gradle-X.Y.Z-all.zip.sha256)

See docs/release/SHIPPING.md § "\`distributionSha256Sum\` must move atomically
with \`distributionUrl\`". \`.github/workflows/ci.yml\` asserts the pin on every
PR, but a feature-branch push runs no CI — so an unpinned wrapper bricks the
local dev loop before CI sees it. Fix the invocation; do not bypass this guard.
MSG
    exit 2
}

# Split the command into segments at shell separators (`;` `|` `&` backtick
# `(` `)` and newline) so a token or flag in a DIFFERENT command segment cannot
# influence the decision for THIS gradlew invocation. This anchoring mirrors
# block-dangerous-bash.sh scoping `main` to a single `git push`; here each
# `gradlew … wrapper …` segment is judged in isolation. Without it:
#   • `./gradlew wrapper; echo --gradle-distribution-sha256-sum` would falsely
#     PASS (the SHA flag lives in the wrong segment), and
#   • `./gradlew tasks | grep wrapper` would falsely BLOCK (the `wrapper` token
#     lives in the wrong segment).
sep_chars=$';|&()\x60'   # shell separators, incl. backtick (\x60)
segmented="${command//[$sep_chars]/$'\n'}"

while IFS= read -r segment; do
    [[ -z "$segment" ]] && continue
    # This segment must be a `gradlew` invocation (token preceded by
    # start/whitespace/`/`, so `foogradlew` does NOT match) AND run the
    # `wrapper` task (token preceded by whitespace or `:` for `:wrapper`, and
    # followed by whitespace — so it does NOT match `wrapper` inside a path
    # like `gradle/wrapper/…`, nor in `gradle-wrapper.properties`).
    [[ " $segment " =~ (^|[[:space:]/])gradlew[[:space:]] ]] || continue
    [[ " $segment " =~ [[:space:]:]wrapper[[:space:]] ]] || continue
    # The wrapper task runs in THIS segment. Allow ONLY if the SHA-pinning flag
    # is present IN THE SAME SEGMENT (space form
    # `--gradle-distribution-sha256-sum VALUE` or attached form `…=VALUE`).
    if [[ ! " $segment " =~ [[:space:]]--gradle-distribution-sha256-sum([[:space:]=]) ]]; then
        reject "$(printf '%s' "$segment" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
    fi
done <<< "$segmented"

exit 0
