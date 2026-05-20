#!/usr/bin/env bash
# SR-80: verify-r8-keep-rules.sh
#
# Static-inspection script: dump the post-R8 DEX class list from the release
# APK and cross-reference it against every @Serializable, @Entity, @Dao, and
# @Database class discovered in app/src/main/.  Fails loudly (exit 1) with a
# remediation hint when a class is missing from the DEX.
#
# Prerequisites:
#   - Run ./gradlew :app:assembleRelease first (either signed or unsigned).
#   - apkanalyzer from the Android SDK cmdline-tools (auto-discovered or
#     set APKANALYZER explicitly).
#
# Usage:
#   scripts/uat/verify-r8-keep-rules.sh
#
# Optional env overrides:
#   APKANALYZER    — path to apkanalyzer binary (default: auto-discovered)
#   APK_PATH       — path to the release APK     (default: auto-discovered)
#   MAPPING_PATH   — path to R8 mapping.txt      (default: auto-discovered)

set -euo pipefail

# ---------------------------------------------------------------------------
# Script root — resolve relative to the worktree root, not the caller's cwd
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# ---------------------------------------------------------------------------
# Colour helpers
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { printf "${GREEN}[OK]${NC}  %s\n" "$*"; }
warn()  { printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
fail()  { printf "${RED}[FAIL]${NC} %s\n" "$*"; }
fatal() { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Locate apkanalyzer
# ---------------------------------------------------------------------------
if [[ -n "${APKANALYZER:-}" ]]; then
    APKANALYZER_BIN="$APKANALYZER"
elif [[ -x "$HOME/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer" ]]; then
    APKANALYZER_BIN="$HOME/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer"
elif command -v apkanalyzer &>/dev/null; then
    APKANALYZER_BIN="$(command -v apkanalyzer)"
else
    fatal "apkanalyzer not found.
  Expected at: ~/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer
  Install it via: sdkmanager \"cmdline-tools;latest\"
  Or set APKANALYZER=/path/to/apkanalyzer before running this script."
fi

# ---------------------------------------------------------------------------
# Locate the release APK (prefer signed, fall back to unsigned)
# ---------------------------------------------------------------------------
APK_DIR="$REPO_ROOT/app/build/outputs/apk/release"

if [[ -n "${APK_PATH:-}" ]]; then
    APK_FILE="$APK_PATH"
else
    if [[ -f "$APK_DIR/app-release.apk" ]]; then
        APK_FILE="$APK_DIR/app-release.apk"
    elif [[ -f "$APK_DIR/app-release-unsigned.apk" ]]; then
        APK_FILE="$APK_DIR/app-release-unsigned.apk"
        warn "Using unsigned APK ($APK_FILE). R8 stripping check is still valid — signing is post-R8."
    else
        fatal "Release APK not found in $APK_DIR.
  Run: ./gradlew :app:assembleRelease
  Then re-run this script."
    fi
fi

# ---------------------------------------------------------------------------
# Locate the R8 mapping file (optional — apkanalyzer works without it but
# class names in the dump will be obfuscated if minification renamed them)
# ---------------------------------------------------------------------------
DEFAULT_MAPPING="$REPO_ROOT/app/build/outputs/mapping/release/mapping.txt"
if [[ -n "${MAPPING_PATH:-}" ]]; then
    MAPPING_FILE="$MAPPING_PATH"
elif [[ -f "$DEFAULT_MAPPING" ]]; then
    MAPPING_FILE="$DEFAULT_MAPPING"
else
    MAPPING_FILE=""
fi

# ---------------------------------------------------------------------------
# Dump post-R8 DEX packages
# ---------------------------------------------------------------------------
printf "\n=== R8 keep-rule survival check ===\n"
printf "APK:     %s\n" "$APK_FILE"
printf "Mapping: %s\n" "${MAPPING_FILE:-"(none — minification disabled or mapping absent)"}"
printf "\n"

APKANALYZER_ARGS=( dex packages --defined-only )
if [[ -n "$MAPPING_FILE" ]]; then
    APKANALYZER_ARGS+=( --proguard-mappings "$MAPPING_FILE" )
fi
APKANALYZER_ARGS+=( "$APK_FILE" )

# Write the DEX dump to a temp file rather than a shell variable.
# Rationale: the dump is 350K+ lines (39 MB).  Piping `printf "%s\n" "$var"`
# through `grep -q` with `set -o pipefail` causes grep to exit early on the
# first match, SIGPIPE-ing printf, and the non-zero printf exit propagates as
# a pipeline failure — turning a successful grep into a false "not found".
# Reading from a temp file via `grep <file>` avoids the broken-pipe scenario.
DEX_TMPFILE="$(mktemp /tmp/pantry-r8-dex-XXXXXX)"
trap 'rm -f "$DEX_TMPFILE"' EXIT

"$APKANALYZER_BIN" "${APKANALYZER_ARGS[@]}" > "$DEX_TMPFILE" 2>&1

if [[ ! -s "$DEX_TMPFILE" ]]; then
    fatal "apkanalyzer returned empty output. Is the APK a valid release build?"
fi

# ---------------------------------------------------------------------------
# Helper: check if a fully-qualified class name appears in the DEX dump.
# apkanalyzer --defined-only outputs one line per symbol; class lines look like:
#   C d <num> <num> <bytes>\tde.docgerdsoft.pantrytracker.data.local.Product
# We match the FQ name as a tab-prefixed, end-of-line token.
# grep directly on the tmpfile avoids the set -o pipefail / SIGPIPE trap
# that fires when piping a large variable through grep -q.
# ---------------------------------------------------------------------------
class_in_dex() {
    local fq="$1"
    # Escape dots so they're treated as literals in the regex (not "any char").
    # The DEX line ends with <tab><FQ_class_name> and nothing more, so we
    # anchor on the tab prefix and end-of-line to avoid false positives like
    # "Product" matching "ProductDao".
    local escaped="${fq//./\\.}"
    grep -qE $'\t'"${escaped}"'$' "$DEX_TMPFILE"
}

# ---------------------------------------------------------------------------
# Helper: extract the package name and annotated class names from a Kotlin file.
#
# Strategy: for each file that grep already flagged as containing the annotation,
# read the file line-by-line:
#   1. Capture the "package X.Y.Z" declaration.
#   2. When we see the target annotation (@Serializable / @Entity / etc.), set
#      a flag, then on the next "class ", "data class ", "interface ", or
#      "object " line extract the simple name.
#   3. Combine package + simple name → FQ name.
#
# This correctly handles:
#   - "internal data class Foo("  — strip internal/data/abstract/sealed prefix
#   - Multiple annotated classes in one file
#   - Annotation with arguments, e.g. @Entity(tableName = "products")
# ---------------------------------------------------------------------------
extract_fq_names() {
    local file="$1"
    local annotation_pattern="$2"   # e.g. "@Serializable\b" or "@Entity\b"

    python3 - "$file" "$annotation_pattern" <<'PYEOF'
import sys, re

filepath = sys.argv[1]
annotation_pattern = re.compile(sys.argv[2])

# Class-declaration pattern: optional modifiers before the keyword
# Covers: class, data class, sealed class, abstract class, internal class,
#         interface, object (including companion objects — we skip those)
class_decl = re.compile(
    r'^\s*(?:(?:public|internal|private|protected|sealed|data|abstract|open|inner|value|'
    r'annotation|enum|fun)\s+)*'
    r'(?:class|interface|object)\s+(\w+)'
)

package_name = ""
with open(filepath, encoding="utf-8") as f:
    lines = f.readlines()

for line in lines:
    m = re.match(r'^\s*package\s+([\w.]+)', line)
    if m:
        package_name = m.group(1)
        break

# State machine:
#   IDLE          — scanning for the target annotation
#   IN_ANNOTATION — saw the annotation; skipping multi-line args until parens balance
#   AFTER_ANNOTATION — args closed; scanning for the class declaration
IDLE, IN_ANNOTATION, AFTER_ANNOTATION = 0, 1, 2
state = IDLE
paren_depth = 0

for line in lines:
    stripped = line.strip()

    if state == IDLE:
        if annotation_pattern.search(stripped):
            # Count unmatched open parens on this line to detect multi-line args
            opens = stripped.count('(')
            closes = stripped.count(')')
            depth = opens - closes
            if depth <= 0:
                # Single-line annotation (e.g. @Serializable or @Entity(tableName="x"))
                state = AFTER_ANNOTATION
            else:
                state = IN_ANNOTATION
                paren_depth = depth

    elif state == IN_ANNOTATION:
        # Skip annotation argument lines, tracking paren balance
        opens = stripped.count('(')
        closes = stripped.count(')')
        paren_depth += opens - closes
        if paren_depth <= 0:
            state = AFTER_ANNOTATION

    elif state == AFTER_ANNOTATION:
        # Skip blank lines, comments, and other annotations between the
        # target annotation and the class declaration
        if not stripped or stripped.startswith('//') or stripped.startswith('*'):
            continue
        if stripped.startswith('@'):
            # Another annotation — account for its parens too
            opens = stripped.count('(')
            closes = stripped.count(')')
            depth = opens - closes
            if depth > 0:
                state = IN_ANNOTATION
                paren_depth = depth
            # else: single-line annotation, stay in AFTER_ANNOTATION
            continue
        m = class_decl.match(line)
        if m:
            simple_name = m.group(1)
            if package_name:
                print(f"{package_name}.{simple_name}")
            else:
                print(simple_name)
        # Whether or not we found a class decl, reset to IDLE
        state = IDLE
PYEOF
}

# ---------------------------------------------------------------------------
# Discover annotated classes from source
# ---------------------------------------------------------------------------
SRC_DIR="$REPO_ROOT/app/src/main"

printf "Discovering annotated classes from %s...\n\n" "$SRC_DIR"

SERIALIZABLE_CLASSES=()
ENTITY_CLASSES=()
DAO_CLASSES=()
DATABASE_CLASSES=()

while IFS= read -r file; do
    while IFS= read -r fq; do
        [[ -n "$fq" ]] && SERIALIZABLE_CLASSES+=("$fq")
    done < <(extract_fq_names "$file" '@Serializable\b')
done < <(grep -rln '@Serializable\b' "$SRC_DIR" --include='*.kt')

while IFS= read -r file; do
    while IFS= read -r fq; do
        [[ -n "$fq" ]] && ENTITY_CLASSES+=("$fq")
    done < <(extract_fq_names "$file" '@Entity\b')
done < <(grep -rln '@Entity\b' "$SRC_DIR" --include='*.kt')

while IFS= read -r file; do
    while IFS= read -r fq; do
        [[ -n "$fq" ]] && DAO_CLASSES+=("$fq")
    done < <(extract_fq_names "$file" '@Dao\b')
done < <(grep -rln '@Dao\b' "$SRC_DIR" --include='*.kt')

while IFS= read -r file; do
    while IFS= read -r fq; do
        [[ -n "$fq" ]] && DATABASE_CLASSES+=("$fq")
    done < <(extract_fq_names "$file" '@Database\b')
done < <(grep -rln '@Database\b' "$SRC_DIR" --include='*.kt')

# Also verify the Converters class (Room TypeConverters used by AppDatabase).
# Grep for the @TypeConverter annotation to find the file, then find the
# enclosing class.
CONVERTERS_CLASSES=()
while IFS= read -r file; do
    pkg=""
    pkg="$(grep -m1 '^package ' "$file" | awk '{print $2}' || true)"
    # Find the class declaration in the file
    class_name="$(grep -m1 -E '^\s*(class|data class|object)\s+\w+' "$file" \
        | sed -E 's/^\s*(class|data class|object)\s+(\w+).*/\2/' || true)"
    if [[ -n "$pkg" && -n "$class_name" ]]; then
        CONVERTERS_CLASSES+=("${pkg}.${class_name}")
    fi
done < <(grep -rln '@TypeConverter\b' "$SRC_DIR" --include='*.kt')

# ---------------------------------------------------------------------------
# Read proguard-rules.pro for remediation-hint comparisons
# ---------------------------------------------------------------------------
PROGUARD_FILE="$REPO_ROOT/app/proguard-rules.pro"
PROGUARD_CONTENT=""
if [[ -f "$PROGUARD_FILE" ]]; then
    PROGUARD_CONTENT="$(cat "$PROGUARD_FILE")"
fi

# ---------------------------------------------------------------------------
# Check function: test one class, print result, track failures
# ---------------------------------------------------------------------------
FAILURES=0

check_class() {
    local fq="$1"
    local annotation="$2"

    if class_in_dex "$fq"; then
        info "$fq  (${annotation})"

        # Remediation hint: if this class is NOT in proguard-rules.pro, note it.
        # A class present in the DEX now but absent from explicit -keep rules
        # relies only on library consumer rules (e.g. from Room/kotlinx.serialization
        # AARs). Alert the developer so they can decide whether an explicit rule
        # is warranted.
        local simple="${fq##*.}"
        if ! printf '%s\n' "$PROGUARD_CONTENT" | grep -q "$simple"; then
            warn "  ${fq} has no explicit -keep in proguard-rules.pro."
            warn "  If you enable R8 minification, add:"
            warn "    -keep class ${fq} { *; }"
        fi
    else
        fail "class ${fq} was stripped (${annotation})"
        printf "  Remediation: add to app/proguard-rules.pro:\n"
        printf "    -keep class %s { *; }\n\n" "$fq"
        FAILURES=$(( FAILURES + 1 ))
    fi
}

# ---------------------------------------------------------------------------
# Run the checks
# ---------------------------------------------------------------------------
printf '%s\n' "--- @Serializable classes ---"
if [[ ${#SERIALIZABLE_CLASSES[@]} -eq 0 ]]; then
    warn "No @Serializable classes found in $SRC_DIR"
else
    for cls in "${SERIALIZABLE_CLASSES[@]}"; do
        check_class "$cls" "@Serializable"
    done
fi

printf '\n%s\n' "--- @Entity classes ---"
if [[ ${#ENTITY_CLASSES[@]} -eq 0 ]]; then
    warn "No @Entity classes found in $SRC_DIR"
else
    for cls in "${ENTITY_CLASSES[@]}"; do
        check_class "$cls" "@Entity"
    done
fi

printf '\n%s\n' "--- @Dao interfaces ---"
if [[ ${#DAO_CLASSES[@]} -eq 0 ]]; then
    warn "No @Dao interfaces found in $SRC_DIR"
else
    for cls in "${DAO_CLASSES[@]}"; do
        check_class "$cls" "@Dao"
    done
fi

printf '\n%s\n' "--- @Database classes ---"
if [[ ${#DATABASE_CLASSES[@]} -eq 0 ]]; then
    warn "No @Database classes found in $SRC_DIR"
else
    for cls in "${DATABASE_CLASSES[@]}"; do
        check_class "$cls" "@Database"
    done
fi

printf '\n%s\n' "--- Room TypeConverters (Converters) ---"
if [[ ${#CONVERTERS_CLASSES[@]} -eq 0 ]]; then
    warn "No @TypeConverter classes found in $SRC_DIR"
else
    for cls in "${CONVERTERS_CLASSES[@]}"; do
        check_class "$cls" "@TypeConverter"
    done
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
printf "\n=== Summary ===\n"
TOTAL=$(( ${#SERIALIZABLE_CLASSES[@]} + ${#ENTITY_CLASSES[@]} + ${#DAO_CLASSES[@]} + ${#DATABASE_CLASSES[@]} + ${#CONVERTERS_CLASSES[@]} ))

if [[ $FAILURES -eq 0 ]]; then
    info "All $TOTAL annotated classes present in the DEX. R8 keep rules look good."
    exit 0
else
    fail "$FAILURES of $TOTAL annotated classes were stripped by R8."
    printf "\nAdd the -keep rules printed above to:\n  %s\n" "$PROGUARD_FILE"
    exit 1
fi
