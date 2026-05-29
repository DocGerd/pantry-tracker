#!/usr/bin/env bash
# verify-migration-2-3.sh
# ---------------------------------------------------------------------------
# Upgrade-install verification for MIGRATION_2_3 (v2 -> v3 schema, #191).
#
# PURPOSE
#   Exercises the full install-over-install path that the schema-level
#   MigrationTest (MigrationTestHelper round-trip) cannot cover: downloads the
#   v1.2.0 release APK (schema v2), installs it, seeds two rows, builds + installs
#   v1.3 on top (no uninstall), and verifies the data survives MIGRATION_2_3 and
#   the two new opt-in restock columns (lowLimit, defaultBuyAmount) exist with
#   their back-fill defaults (lowLimit NULL, defaultBuyAmount 1).
#
# PREREQUISITES
#   1. Android emulator (or USB device) accessible via adb.
#      Default AVD: pantry_pixel6_api34 — override via EMULATOR_AVD env var.
#      If no emulator is running, set BOOT_EMULATOR=1 to launch it headlessly.
#      See scripts/uat/README.md for AVD creation instructions.
#   2. Android SDK (adb, emulator) on PATH or via ANDROID_HOME.
#   3. gh CLI authenticated (for the v1.2.0 APK download).
#   4. Signing props for v1.3 assembleRelease (see §B in docs/release/SHIPPING.md).
#      If GRADLE_USER_HOME is redirected, bridge them first:
#        grep '^PANTRY_TRACKER_RELEASE_' ~/.gradle/gradle.properties \
#          > "${GRADLE_USER_HOME}/gradle.properties"
#      If props are missing the build still succeeds but produces
#      app-release-unsigned.apk — the script detects this and exits clearly
#      before the install step. The v1.3 APK MUST be signed with the same
#      keystore as v1.2.0 (lifetime cert SHA-256 ec9a4bb8…b3d9), or the
#      upgrade install fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
#
# DATA SEEDING — RUN-AS CONSTRAINT
#   The v1.2.0 APK is release-signed (not debuggable), so 'adb shell run-as'
#   is unavailable. This script uses 'adb root' instead, which works on
#   google_apis (non-user) emulator images such as pantry_pixel6_api34, but
#   NOT on google_apis_playstore (user-build) images.
#
# IDEMPOTENCY
#   Re-running on a dirty emulator always works: the package is uninstalled at
#   the start of each run.
#
# USAGE
#   scripts/uat/verify-migration-2-3.sh                       # emulator already running
#   BOOT_EMULATOR=1 scripts/uat/verify-migration-2-3.sh       # boot the AVD first
# ---------------------------------------------------------------------------

set -euo pipefail

EMULATOR_AVD="${EMULATOR_AVD:-pantry_pixel6_api34}"
BOOT_EMULATOR="${BOOT_EMULATOR:-0}"
OLD_TAG="v1.2.0"
PKG="de.docgerdsoft.pantrytracker"
DB_NAME="pantry-tracker.db"
DB_PATH="/data/data/${PKG}/databases/${DB_NAME}"
EMULATOR_PID=""

log()  { echo "[verify-migration-2-3] $*"; }
die()  { echo "[verify-migration-2-3] ERROR: $*" >&2; exit 1; }

cleanup() {
  if [[ -n "${OLD_APK:-}" && -f "${OLD_APK}" ]]; then
    rm -f "${OLD_APK}"
  fi
  if [[ -n "${EMULATOR_PID}" ]]; then
    log "Shutting down emulator (pid ${EMULATOR_PID})..."
    kill "${EMULATOR_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 0. Boot emulator if requested. Bare `emulator` is rarely on PATH (adb lives
#    in platform-tools, which is on PATH; emulator/ usually is not) — without
#    explicit discovery, `emulator &` silently fails and adb wait-for-device
#    hangs forever.
# ---------------------------------------------------------------------------
if [[ "${BOOT_EMULATOR}" == "1" ]]; then
  if [[ -n "${EMULATOR_BIN:-}" ]]; then
    :
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
  boot_timeout=120
  while [[ "${boot_timeout}" -gt 0 ]]; do
    boot_status=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '[:space:]') || boot_status=""
    if [[ "${boot_status}" == "1" ]]; then
      log "Emulator fully booted."
      break
    fi
    sleep 2
    boot_timeout=$((boot_timeout - 2))
  done
  if [[ "${boot_status:-}" != "1" ]]; then
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
# 2. Download the v1.2.0 release APK (schema v2)
# ---------------------------------------------------------------------------
OLD_APK=$(mktemp --suffix=.apk)
# mktemp creates the file empty; `gh release download --output` refuses to
# overwrite an existing file. --clobber tells it to.
log "Downloading ${OLD_TAG} APK to ${OLD_APK}..."
gh release download "${OLD_TAG}" \
  --pattern '*.apk' \
  --repo DocGerd/pantry-tracker \
  --clobber \
  --output "${OLD_APK}"
log "${OLD_TAG} APK size: $(wc -c < "${OLD_APK}") bytes"

# ---------------------------------------------------------------------------
# 3. Install v1.2.0
# ---------------------------------------------------------------------------
log "Installing ${OLD_TAG}..."
adb install "${OLD_APK}"

# ---------------------------------------------------------------------------
# 4. Seed rows — requires adb root (release APK is not debuggable)
# ---------------------------------------------------------------------------
log "Requesting adb root to seed DB..."
if ! adb root 2>&1 | grep -qv "adbd is already running as root\|restarting adbd as root"; then
  adb root || die "adb root failed. ${OLD_TAG} is a release (non-debuggable) APK, so seeding requires root. \
Use a google_apis emulator image (e.g. system-images;android-34;google_apis;x86_64), not google_apis_playstore. \
See scripts/uat/README.md."
fi
sleep 1
adb wait-for-device

log "Launching ${OLD_TAG} once to let Room create the database..."
adb shell am start -n "${PKG}/.MainActivity"
sleep 3

if ! adb shell "test -f ${DB_PATH}"; then
  die "Database file not found at ${DB_PATH} after first launch. Room may not have initialised yet."
fi

NOW_MILLIS=$(date +%s)000

log "Seeding 2 test rows into products table (v2 schema)..."
# Schema v2 products columns: id, barcode, name, brand, imageUrl, quantity,
# createdAt, updatedAt (off_lookup_cache also exists at v2 but is irrelevant here).
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

log "Stopping ${OLD_TAG} app..."
adb shell am force-stop "${PKG}"

# ---------------------------------------------------------------------------
# 5. Build the v1.3 release APK
# ---------------------------------------------------------------------------
log "Building v1.3 release APK (./gradlew :app:assembleRelease)..."
./gradlew :app:assembleRelease

V13_SIGNED="app/build/outputs/apk/release/app-release.apk"
V13_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"

if [[ -f "${V13_SIGNED}" ]]; then
  NEW_APK="${V13_SIGNED}"
  log "v1.3 release APK: ${V13_SIGNED}"
elif [[ -f "${V13_UNSIGNED}" ]]; then
  die "v1.3 build is unsigned; bridge signing properties per docs/release/SHIPPING.md §B before running. \
Expected '${V13_SIGNED}' but found only '${V13_UNSIGNED}'. \
Hint: grep '^PANTRY_TRACKER_RELEASE_' ~/.gradle/gradle.properties > \"\${GRADLE_USER_HOME}/gradle.properties\""
else
  die "Neither ${V13_SIGNED} nor ${V13_UNSIGNED} found after assembleRelease. Build may have failed."
fi

# ---------------------------------------------------------------------------
# 6. Install v1.3 OVER v1.2.0 (no uninstall — triggers MIGRATION_2_3)
# ---------------------------------------------------------------------------
log "Installing v1.3 over ${OLD_TAG} (upgrade install — triggers MIGRATION_2_3)..."
adb install -r "${NEW_APK}"

# ---------------------------------------------------------------------------
# 7. Launch v1.3 — Room runs MIGRATION_2_3 on first open
# ---------------------------------------------------------------------------
log "Launching v1.3 app (triggers MIGRATION_2_3)..."
adb shell am start -n "${PKG}/.MainActivity"
sleep 5

# ---------------------------------------------------------------------------
# 8. Verify rows preserved and the new restock columns applied
# ---------------------------------------------------------------------------
log "Verifying rows preserved after migration..."
POST_ROW_COUNT=$(adb shell "sqlite3 '${DB_PATH}' 'SELECT COUNT(*) FROM products;'" | tr -d '[:space:]')
log "Row count post-migration: ${POST_ROW_COUNT}"
if [[ "${POST_ROW_COUNT}" != "2" ]]; then
  die "Row count changed during migration! Expected 2, got ${POST_ROW_COUNT}. Data may have been lost."
fi

log "Verifying the two new columns exist (lowLimit, defaultBuyAmount)..."
COL_CHECK=$(adb shell "sqlite3 '${DB_PATH}' \
  \"SELECT COUNT(*) FROM pragma_table_info('products') WHERE name IN ('lowLimit','defaultBuyAmount');\"" | tr -d '[:space:]')
if [[ "${COL_CHECK}" != "2" ]]; then
  die "Expected 2 new columns after MIGRATION_2_3, found ${COL_CHECK}. Migration may not have run."
fi

log "Verifying back-fill defaults (lowLimit NULL, defaultBuyAmount 1)..."
LOW_NULL_COUNT=$(adb shell "sqlite3 '${DB_PATH}' 'SELECT COUNT(*) FROM products WHERE lowLimit IS NULL;'" | tr -d '[:space:]')
if [[ "${LOW_NULL_COUNT}" != "2" ]]; then
  die "Expected both rows to back-fill lowLimit=NULL, got ${LOW_NULL_COUNT}/2."
fi
DEFAULT_BUY=$(adb shell "sqlite3 '${DB_PATH}' \"SELECT defaultBuyAmount FROM products WHERE barcode='0000000000001';\"" | tr -d '[:space:]')
if [[ "${DEFAULT_BUY}" != "1" ]]; then
  die "Expected defaultBuyAmount=1 back-fill, got '${DEFAULT_BUY}'."
fi

log "Verifying seeded row content survived..."
ROW_A_QTY=$(adb shell "sqlite3 '${DB_PATH}' \"SELECT quantity FROM products WHERE barcode='0000000000001';\"" | tr -d '[:space:]')
ROW_B_QTY=$(adb shell "sqlite3 '${DB_PATH}' \"SELECT quantity FROM products WHERE barcode='0000000000002';\"" | tr -d '[:space:]')
if [[ "${ROW_A_QTY}" != "3" || "${ROW_B_QTY}" != "5" ]]; then
  die "Row content changed during migration! Expected qty 3/5, got ${ROW_A_QTY}/${ROW_B_QTY}."
fi

# ---------------------------------------------------------------------------
# 9. Scan logcat for crashes. Match canonical crash signatures only — broad
#    `grep -i AndroidRuntime` false-positives on routine D/I zygote noise.
# ---------------------------------------------------------------------------
log "Scanning logcat for crashes..."
CRASHES=$(adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|^E/AndroidRuntime' || true)
if [[ -n "${CRASHES}" ]]; then
  echo "--------- CRASH LOG ---------"
  echo "${CRASHES}"
  echo "------------------------------"
  die "Crash detected in logcat. MIGRATION_2_3 upgrade-install: FAIL"
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
log "Rows preserved: ${POST_ROW_COUNT}/2 (qty 3/5 intact)"
log "New columns: lowLimit + defaultBuyAmount present; defaults NULL / 1"
log "Logcat crashes: none"
echo ""
echo "MIGRATION_2_3 upgrade-install: PASS"
