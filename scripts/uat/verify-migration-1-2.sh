#!/usr/bin/env bash
# verify-migration-1-2.sh
# ---------------------------------------------------------------------------
# Upgrade-install verification for MIGRATION_1_2 (v1 -> v2 schema).
#
# PURPOSE
#   Exercises the full install-over-install path that AppDatabaseMigration1To2Test
#   cannot cover at schema-level only: downloads v1.1.0, installs it, seeds two
#   rows, installs v1.2 on top (no uninstall), and verifies the data survives the
#   migration and the new off_lookup_cache table exists.
#
# PREREQUISITES
#   1. Android emulator (or USB device) accessible via adb.
#      Default AVD: pantry_pixel6_api34 — override via EMULATOR_AVD env var.
#      If no emulator is running, set BOOT_EMULATOR=1 and the script will
#      launch the named AVD headlessly before proceeding.
#      See scripts/uat/README.md for AVD creation instructions.
#   2. Android SDK (adb, emulator) on PATH or via ANDROID_HOME.
#   3. gh CLI authenticated (for v1.1.0 APK download).
#   4. Signing props bridged for v1.2 assembleRelease (see §B in
#      docs/release/SHIPPING.md). If props are missing the build still succeeds
#      but produces app-release-unsigned.apk — the script detects this and
#      exits with a clear message before the install step.
#
# DATA SEEDING — RUN-AS CONSTRAINT
#   v1.1.0 ships only a release-signed APK (release-signed = not debuggable).
#   'adb shell run-as <pkg>' requires the app to be debuggable, so direct
#   sqlite3 injection via run-as is NOT available against the v1.1.0 release
#   APK. This script uses 'adb root' to gain access instead.
#   'adb root' is supported on google_apis (non-user) emulator system images
#   such as 'system-images;android-34;google_apis;x86_64' (pantry_pixel6_api34).
#   It is NOT supported on google_apis_playstore (user-build) images.
#   If 'adb root' fails, the script exits with instructions to use a
#   google_apis image, not a google_apis_playstore image.
#
# IDEMPOTENCY
#   Re-running on a dirty emulator always works: the package is uninstalled at
#   the start of each run before proceeding.
#
# USAGE
#   # Use the default AVD (pantry_pixel6_api34), emulator already running:
#   scripts/uat/verify-migration-1-2.sh
#
#   # Boot the emulator automatically:
#   BOOT_EMULATOR=1 scripts/uat/verify-migration-1-2.sh
#
#   # Use a different AVD:
#   EMULATOR_AVD=pantry_test_api35 BOOT_EMULATOR=1 scripts/uat/verify-migration-1-2.sh
#
# ---------------------------------------------------------------------------

set -euo pipefail

EMULATOR_AVD="${EMULATOR_AVD:-pantry_pixel6_api34}"
BOOT_EMULATOR="${BOOT_EMULATOR:-0}"
PKG="de.docgerdsoft.pantrytracker"
DB_NAME="pantry-tracker.db"
DB_PATH="/data/data/${PKG}/databases/${DB_NAME}"
EMULATOR_PID=""

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

log()  { echo "[verify-migration-1-2] $*"; }
die()  { echo "[verify-migration-1-2] ERROR: $*" >&2; exit 1; }

cleanup() {
  if [[ -n "${V11_APK:-}" && -f "${V11_APK}" ]]; then
    rm -f "${V11_APK}"
  fi
  if [[ -n "${EMULATOR_PID}" ]]; then
    log "Shutting down emulator (pid ${EMULATOR_PID})..."
    kill "${EMULATOR_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 0. Boot emulator if requested
# ---------------------------------------------------------------------------

# Discover the emulator binary. Bare `emulator` is rarely on PATH — typical
# Android SDK installs put adb in $ANDROID_HOME/platform-tools (commonly on
# PATH) but leave $ANDROID_HOME/emulator/ off PATH. Without explicit
# discovery, `emulator &` silently fails and the subsequent
# `adb wait-for-device` hangs forever waiting for a device that was never
# spawned.
if [[ "${BOOT_EMULATOR}" == "1" ]]; then
  if [[ -n "${EMULATOR_BIN:-}" ]]; then
    : # honour explicit override
  elif command -v emulator >/dev/null 2>&1; then
    EMULATOR_BIN="emulator"
  elif [[ -x "${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator" ]]; then
    EMULATOR_BIN="${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator"
  else
    die "emulator binary not found. Set EMULATOR_BIN, add \$ANDROID_HOME/emulator to PATH, or boot manually and re-run with BOOT_EMULATOR=0."
  fi

  log "Booting AVD: ${EMULATOR_AVD} (headless) via ${EMULATOR_BIN}..."
  "${EMULATOR_BIN}" -avd "${EMULATOR_AVD}" -no-window -no-audio -gpu swiftshader_indirect &
  EMULATOR_PID=$!
  log "Emulator PID: ${EMULATOR_PID}. Waiting for boot (up to 120 s)..."
  adb wait-for-device
  # Wait for the system to be fully booted (sys.boot_completed=1)
  local_timeout=120
  while [[ "${local_timeout}" -gt 0 ]]; do
    boot_status=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '[:space:]') || boot_status=""
    if [[ "${boot_status}" == "1" ]]; then
      log "Emulator fully booted."
      break
    fi
    sleep 2
    local_timeout=$((local_timeout - 2))
  done
  if [[ "${boot_status}" != "1" ]]; then
    die "Emulator did not boot within 120 s. Check AVD name and host KVM support."
  fi
else
  log "Waiting for an already-running device/emulator..."
  adb wait-for-device
fi

# ---------------------------------------------------------------------------
# 1. Idempotent: uninstall any prior copy
# ---------------------------------------------------------------------------

log "Uninstalling ${PKG} (if present)..."
adb uninstall "${PKG}" 2>/dev/null || true

# ---------------------------------------------------------------------------
# 2. Download v1.1.0 release APK from GitHub Releases
# ---------------------------------------------------------------------------

V11_APK=$(mktemp --suffix=.apk)
# `mktemp` creates the file empty; `gh release download --output` refuses
# to overwrite an existing file. --clobber tells it to.
log "Downloading v1.1.0 APK to ${V11_APK}..."
gh release download v1.1.0 \
  --pattern '*.apk' \
  --repo DocGerd/pantry-tracker \
  --clobber \
  --output "${V11_APK}"
log "v1.1.0 APK size: $(wc -c < "${V11_APK}") bytes"

# ---------------------------------------------------------------------------
# 3. Install v1.1.0
# ---------------------------------------------------------------------------

log "Installing v1.1.0..."
adb install "${V11_APK}"

# ---------------------------------------------------------------------------
# 4. Seed rows — requires adb root (release APK is not debuggable)
# ---------------------------------------------------------------------------
# v1.1.0 ships a release-signed APK (not debuggable), so 'adb shell run-as'
# is unavailable. We use 'adb root' instead, which works on google_apis
# emulator system images (not google_apis_playstore / user-build images).
# See header comment for the full constraint description.

log "Requesting adb root to seed DB..."
if ! adb root 2>&1 | grep -qv "adbd is already running as root\|restarting adbd as root"; then
  adb root || die "adb root failed. \
v1.1.0 is a release (non-debuggable) APK, so data seeding requires root access. \
Use a google_apis emulator image (e.g. system-images;android-34;google_apis;x86_64), \
not a google_apis_playstore image. See scripts/uat/README.md for AVD setup."
fi
# Brief pause for adbd to restart after root request
sleep 1
adb wait-for-device

log "Launching v1.1.0 once to let Room create the database..."
adb shell am start -n "${PKG}/.MainActivity"
sleep 3

# Verify the database file exists now
if ! adb shell "test -f ${DB_PATH}"; then
  die "Database file not found at ${DB_PATH} after first launch. Room may not have initialised yet."
fi

NOW_MILLIS=$(date +%s)000

log "Seeding 2 test rows into products table (v1 schema)..."
# Schema v1 columns: id (PK autoincrement), barcode (TEXT), name (TEXT NOT NULL),
# brand (TEXT), imageUrl (TEXT), quantity (INTEGER NOT NULL),
# createdAt (INTEGER NOT NULL epoch millis), updatedAt (INTEGER NOT NULL epoch millis)
# Note: spec used 'last_updated' and 'pantry.db' — actual column/filename differs.
adb shell "sqlite3 '${DB_PATH}' \
  \"INSERT INTO products(barcode, name, quantity, createdAt, updatedAt) \
    VALUES ('0000000000001', 'Migration Test A', 3, ${NOW_MILLIS}, ${NOW_MILLIS}); \
   INSERT INTO products(barcode, name, quantity, createdAt, updatedAt) \
    VALUES ('0000000000002', 'Migration Test B', 5, ${NOW_MILLIS}, ${NOW_MILLIS});\""

ROW_COUNT=$(adb shell "sqlite3 '${DB_PATH}' 'SELECT COUNT(*) FROM products;'" | tr -d '[:space:]')
log "Row count after seeding: ${ROW_COUNT}"
if [[ "${ROW_COUNT}" != "2" ]]; then
  die "Expected 2 rows after seeding, got ${ROW_COUNT}. Seeding failed."
fi

log "Stopping v1.1.0 app..."
adb shell am force-stop "${PKG}"

# ---------------------------------------------------------------------------
# 5. Build v1.2 release APK
# ---------------------------------------------------------------------------

log "Building v1.2 release APK (./gradlew :app:assembleRelease)..."
./gradlew :app:assembleRelease

V12_SIGNED="app/build/outputs/apk/release/app-release.apk"
V12_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"

if [[ -f "${V12_SIGNED}" ]]; then
  V12_APK="${V12_SIGNED}"
  log "v1.2 release APK: ${V12_SIGNED}"
elif [[ -f "${V12_UNSIGNED}" ]]; then
  die "v1.2 build is unsigned; bridge signing properties per docs/release/SHIPPING.md §B before running. \
Expected '${V12_SIGNED}' but found only '${V12_UNSIGNED}'. \
Hint: grep '^PANTRY_TRACKER_RELEASE_' ~/.gradle/gradle.properties > \"\${GRADLE_USER_HOME}/gradle.properties\""
else
  die "Neither ${V12_SIGNED} nor ${V12_UNSIGNED} found after assembleRelease. Build may have failed."
fi

# ---------------------------------------------------------------------------
# 6. Install v1.2 OVER v1.1.0 (no uninstall — triggers MIGRATION_1_2)
# ---------------------------------------------------------------------------

log "Installing v1.2 over v1.1.0 (upgrade install — triggers MIGRATION_1_2)..."
adb install -r "${V12_APK}"

# ---------------------------------------------------------------------------
# 7. Launch v1.2 — Room runs MIGRATION_1_2 on first open
# ---------------------------------------------------------------------------

log "Launching v1.2 app (triggers MIGRATION_1_2)..."
adb shell am start -n "${PKG}/.MainActivity"
sleep 5

# ---------------------------------------------------------------------------
# 8. Verify rows preserved and new schema applied
# ---------------------------------------------------------------------------

log "Verifying rows preserved after migration..."
POST_ROW_COUNT=$(adb shell "sqlite3 '${DB_PATH}' 'SELECT COUNT(*) FROM products;'" | tr -d '[:space:]')
log "Row count post-migration: ${POST_ROW_COUNT}"
if [[ "${POST_ROW_COUNT}" != "2" ]]; then
  die "Row count changed during migration! Expected 2, got ${POST_ROW_COUNT}. Data may have been lost."
fi

log "Verifying off_lookup_cache table exists (new in v1.2)..."
TABLE_CHECK=$(adb shell "sqlite3 '${DB_PATH}' \
  \"SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='off_lookup_cache';\"" | tr -d '[:space:]')
if [[ "${TABLE_CHECK}" != "1" ]]; then
  die "off_lookup_cache table missing after MIGRATION_1_2. Migration may not have run."
fi

log "Verifying seeded row content survived..."
ROW_A_QTY=$(adb shell "sqlite3 '${DB_PATH}' \
  \"SELECT quantity FROM products WHERE barcode='0000000000001';\"" | tr -d '[:space:]')
ROW_B_QTY=$(adb shell "sqlite3 '${DB_PATH}' \
  \"SELECT quantity FROM products WHERE barcode='0000000000002';\"" | tr -d '[:space:]')
if [[ "${ROW_A_QTY}" != "3" || "${ROW_B_QTY}" != "5" ]]; then
  die "Row content changed during migration! Expected qty 3/5, got ${ROW_A_QTY}/${ROW_B_QTY}."
fi

# ---------------------------------------------------------------------------
# 9. Scan logcat for crashes
# ---------------------------------------------------------------------------

log "Scanning logcat for crashes..."
# Match canonical crash signatures only:
#   - "FATAL EXCEPTION" — the standard Android crash banner
#   - "E AndroidRuntime" — error-level messages from the AndroidRuntime tag
#     (the only severity that means "the runtime is crashing", as opposed to
#     D/I messages which are routine zygote/lifecycle noise)
# Broad patterns like `grep -i AndroidRuntime` produce false positives on
# every boot — D-level "START Zygote" and I-level "VM exiting result code 0"
# are not crashes.
CRASHES=$(adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|^E/AndroidRuntime' || true)
if [[ -n "${CRASHES}" ]]; then
  echo "--------- CRASH LOG ---------"
  echo "${CRASHES}"
  echo "------------------------------"
  die "Crash detected in logcat. MIGRATION_1_2 upgrade-install: FAIL"
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------

log "Rows preserved: ${POST_ROW_COUNT}/2"
log "off_lookup_cache table: present"
log "Logcat crashes: none"
echo ""
echo "MIGRATION_1_2 upgrade-install: PASS"
