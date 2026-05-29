# UAT scripts — Claude-runnable emulator automation

This directory contains bash scripts that Claude (or a developer) can run against
a local Android emulator to drive deterministic UAT scenarios. Each script is
idempotent — re-running on a dirty emulator still works.

---

## Prerequisites (all scripts)

All scripts require:

- **`adb`** on PATH — from Android SDK platform-tools (`$ANDROID_HOME/platform-tools/`)
- **`gh` CLI** authenticated (`gh auth status`) — for GitHub Release downloads
- **An accessible emulator or USB device** (`adb devices` shows at least one device)
- **`libpulse0`** installed on the host (even with `-no-audio`, the emulator
  binary has a build-time dependency on `libpulse.so.0`):
  ```
  sudo apt install -y libpulse0
  ```
  Symptom if missing: `emulator: error while loading shared libraries: libpulse.so.0`.

### Emulator system image: google_apis vs google_apis_playstore

Several scripts (including `verify-migration-1-2.sh`) need `adb root` to access
app-private data on a release-signed (non-debuggable) APK. `adb root` is only
supported on **`google_apis`** system images, NOT on `google_apis_playstore`
(user-build) images.

- Use: `system-images;android-34;google_apis;x86_64`
- Avoid: `system-images;android-34;google_apis_playstore;x86_64`

---

## Creating the AVD

### Option A — dev-host AVD (pantry_pixel6_api34, already exists)

The dev host already has `pantry_pixel6_api34` created. Use it directly:

```bash
EMULATOR_AVD=pantry_pixel6_api34 BOOT_EMULATOR=1 scripts/uat/verify-migration-1-2.sh
```

### Option B — create a fresh API-34 AVD (google_apis, x86_64)

```bash
# Install the system image (if not already present)
sdkmanager "system-images;android-34;google_apis;x86_64"

# Create the AVD — uses a Pixel 6 hardware profile
avdmanager create avd \
  -n pantry_pixel6_api34 \
  -k "system-images;android-34;google_apis;x86_64" \
  -d "pixel_6"

# Verify the AVD exists
emulator -list-avds
```

### Option C — create an API-35 AVD (pantry_test_api35)

The spec for issue #81 referenced `pantry_test_api35`. To create it:

```bash
sdkmanager "system-images;android-35;google_apis;x86_64"

avdmanager create avd \
  -n pantry_test_api35 \
  -k "system-images;android-35;google_apis;x86_64" \
  -d "pixel_6"
```

Use by setting `EMULATOR_AVD=pantry_test_api35` when running a script.

### Verifying KVM + emulator are usable

```bash
# Check KVM is accessible
ls -la /dev/kvm    # should show crw-rw---- with group kvm

# Check emulator binary's shared-lib deps all resolve
emulator -version

# Boot test (replaces emulator -accel-check, which is necessary but not sufficient)
emulator -avd pantry_pixel6_api34 -no-window -no-audio -gpu swiftshader_indirect &
E_PID=$!
sleep 30
adb wait-for-device
adb shell getprop sys.boot_completed   # expect "1"
kill $E_PID
```

---

## Scripts

### `verify-migration-1-2.sh` — MIGRATION_1_2 upgrade-install verification (SR-81)

**What it does:**

1. Optionally boots the named AVD headlessly (`BOOT_EMULATOR=1`)
2. Uninstalls any prior copy of `de.docgerdsoft.pantrytracker` (idempotent)
3. Downloads v1.1.0 release APK from GitHub Releases (`gh release download`)
4. Installs v1.1.0
5. Uses `adb root` + `sqlite3` to seed 2 test rows (v1 schema: `products` table,
   columns `barcode`, `name`, `quantity`, `createdAt`, `updatedAt`)
6. Builds the v1.2 release APK via `./gradlew :app:assembleRelease`
7. Detects unsigned build and exits with a clear error if signing props are not bridged
8. Installs v1.2 on top of v1.1.0 (no uninstall — triggers `MIGRATION_1_2`)
9. Launches the app (Room runs the migration on first open)
10. Verifies: row count preserved (2), `off_lookup_cache` table exists, row content intact
11. Scans logcat for FATAL/AndroidRuntime entries

**Usage:**

```bash
# Emulator already running (default AVD pantry_pixel6_api34):
scripts/uat/verify-migration-1-2.sh

# Auto-boot the emulator:
BOOT_EMULATOR=1 scripts/uat/verify-migration-1-2.sh

# Different AVD:
EMULATOR_AVD=pantry_test_api35 BOOT_EMULATOR=1 scripts/uat/verify-migration-1-2.sh
```

**Signing props prerequisite:**

The v1.2 install-over step requires a release-signed APK. Bridge the signing
properties before running:

```bash
grep '^PANTRY_TRACKER_RELEASE_' ~/.gradle/gradle.properties \
  > "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"
```

See `docs/release/SHIPPING.md §B` for full setup.

**Tracked issue:** [#81](https://github.com/DocGerd/pantry-tracker/issues/81)

---

### `permission-real-prompt.sh` — camera-permission flow emulator verification (SR-77)

**What it does:**

Drives the camera-permission scenarios that cannot be covered by instrumented
Compose tests (the OS permission dialog lives in `com.android.permissioncontroller`,
outside the app process). Covers three scenarios:

- **Scenario A** — §4 row 6 + §5 row 7 (Allow path): revoke camera permission,
  launch app, grant via `pm grant` (equivalent to Allow in system prompt),
  verify the app holds camera permission.
- **Scenario B** — §5 row 6 (SoftDenied Try again → Allow): revoke, relaunch,
  re-grant, verify.
- **Scenario C** — §6 row 6 (HardDenied → Settings grant → onResume recovery):
  revoke, launch in HardDenied state, grant from "Settings" (pm grant), resume
  the app, verify camera permission is granted on resume.

Camera-preview rendering and real OS dialog tapping remain **human-only** (see
the `NOTE` in the script output and `docs/uat/v1-uat-checklist.md` §4-6).

**Usage:**

```bash
# Emulator already running:
scripts/uat/permission-real-prompt.sh

# Auto-boot emulator:
BOOT_EMULATOR=1 scripts/uat/permission-real-prompt.sh

# Custom AVD or APK:
EMULATOR_AVD=pantry_test_api35 APK_PATH=app/build/outputs/apk/debug/app-debug.apk \
  BOOT_EMULATOR=1 scripts/uat/permission-real-prompt.sh
```

**Tracked issue:** [#77](https://github.com/DocGerd/pantry-tracker/issues/77)

---

## Planned scripts (not yet implemented)

The following scripts are referenced in the issue tracker and will be added
to this directory when their issues are implemented:

*(No pending planned scripts.)*

(`verify-r8-keep-rules.sh` for issue #80 ships in PR #86 — it will be added to
the "## Scripts" section above once that PR merges; mention it here only if
this PR merges first and #86 is still open at that point.)

---

## Before-PR end-to-end checklist

`bash -n`, `:app:detekt`, and an implementer's "BUILD SUCCESSFUL" self-report
are **necessary but not sufficient**. The implementer's shell usually has
`$ANDROID_HOME/emulator/` and `gh` on PATH; a non-interactive fresh shell (CI
runner, a different agent, a colleague's box) usually does **not**. Before a PR
that adds or changes a script here, or runs instrumented tests, complete this:

### For adding or changing a bash script

Run it **in a non-interactive shell on a fresh host** (not just the authoring
shell) before declaring it ready. Three real bugs (SR-81's
`verify-migration-1-2.sh`) passed every static gate and surfaced only on a
fresh-shell re-run:

1. **PATH-resolved binaries** — bare `emulator -avd …` assumes the SDK's
   `emulator/` dir is on `$PATH`; if only `platform-tools` is, it silently
   hangs on `adb wait-for-device`. Resolve binaries explicitly or check PATH up
   front.
2. **`gh` + `mktemp` download race** — `gh release download --output <file>`
   refuses to overwrite the empty file `mktemp` just created; pass `--clobber`.
3. **logcat regex false-positives** — `grep -iE 'AndroidRuntime'` matches benign
   D/I-level Zygote-start and VM-exit lines on every clean boot, so the script
   would `FAIL` on every *successful* migration. Anchor patterns to FATAL crash
   lines, not substring matches.

Rule of thumb: any script with PATH-resolved binaries, `gh`-CLI invocations, or
logcat regex scanning needs an actual fresh-shell run before merge.

### For filtering a single instrumented test locally

Two footguns, both surfaced only on a real emulator run (#191's `MIGRATION_2_3`),
never in the JVM gate or a self-report:

1. **`:app:connectedDebugAndroidTest` rejects `--tests`** ("Unknown command-line
   option '--tests'") — that flag is JVM-`Test`-task-only (e.g.
   `:app:testDebugUnitTest`). Filter instrumented tests with
   `-Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.Class>`.
2. **`INSTALL_FAILED_UPDATE_INCOMPATIBLE: … signatures do not match`** — a
   leftover *release*-signed install blocks the *debug*-keystore-signed test APK;
   the tell-tale is `Starting 0 tests / Finished 0 tests` with a fast
   `BUILD FAILED`, so nothing actually ran. Uninstall **both** packages first:

   ```bash
   adb uninstall de.docgerdsoft.pantrytracker
   adb uninstall de.docgerdsoft.pantrytracker.test
   ./gradlew :app:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=de.docgerdsoft.pantrytracker.SomeTest
   ```

Both present as a code/test failure but are CLI-flag / device-state issues, so a
verbatim retry just fails again.

---

## Notes

- All scripts run from the **repo root** (not from `scripts/uat/`).
  The Gradle commands (`./gradlew`) depend on this.
- `adb` commands use the first connected device. To target a specific device,
  set `ANDROID_SERIAL` before running: `ANDROID_SERIAL=emulator-5554 scripts/uat/verify-migration-1-2.sh`
- Scripts set `set -euo pipefail` and will abort on any unexpected error.
  The `cleanup` trap ensures temp files and auto-booted emulators are cleaned up on exit.
