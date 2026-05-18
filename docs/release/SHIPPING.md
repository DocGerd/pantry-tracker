# Shipping Pantry Tracker to a device

Three paths, in order of how much setup they take. Pick one.

| Path | When | Setup cost | Auto-updates |
|------|------|-----------|--------------|
| **A. `adb install` of debug APK** | Your own device, dev/QA | Lowest | No |
| **B. Sideload of release APK** | v1.0 single-user install (this is the v1 plan) | One-time keystore + signing config | No |
| **C. Firebase App Distribution / Play Store** | Beta testers, eventual public launch | Higher (Firebase account or developer fee) | Yes |

Sections below detail each. For v1.0 the recommended path is **B**, and **A**
remains useful day-to-day.

---

## A. `adb install` — dev-loop install

The fastest way to get *your latest commit* onto your device. Works for any
debug APK. No signing setup needed (Gradle uses an auto-generated debug
keystore).

### Prerequisites

1. **Android SDK platform-tools** on your machine (`adb` lives here).
   ```bash
   # If you already have Android Studio:
   which adb
   # If not, on macOS:
   brew install --cask android-platform-tools
   # On Linux:
   sudo apt install android-tools-adb        # Debian/Ubuntu
   sudo pacman -S android-tools              # Arch
   ```
2. **USB debugging enabled on the device.**
   - Settings → About phone → tap "Build number" 7 times (enables Developer
     options)
   - Settings → System → Developer options → enable "USB debugging"
   - Plug the phone in. First connection asks you to authorize the
     computer's RSA key on the device — tap Allow.

### Build + install

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` means "reinstall, keeping data" — your pantry survives.

To launch the just-installed app from the CLI:
```bash
adb shell am start -n de.docgerdsoft.pantrytracker/.MainActivity
```

### Pulling logcat for debugging

```bash
adb logcat -s ScanViewModel OffApiClient DetailViewModel CameraPreview CameraPermissionGate
```

The JUL loggers used in this app forward to logcat with the class name as
the tag — see [arc42 §8.4](../architecture/08-crosscutting-concepts.md#84-logging).

### When this is wrong

- The debug build has `applicationId = "de.docgerdsoft.pantrytracker"` and
  is **signed with the debug keystore**. It is not the right binary to give
  to anyone else — they'd be running an unsigned build of your dev branch.

---

## B. Sideload a release APK — the v1.0 path

This is what ships v1.0. Sideloading means: build a release-signed APK,
copy it to the device, the user taps it in their file manager, Android
installs it.

The catch: a release APK must be **signed with your own keystore**. The
keystore is irreplaceable — if you lose it, you can never sign an update.

### One-time keystore setup

```bash
# Create the keystore. Pick strong passwords; record them in a password manager.
keytool -genkey -v \
  -keystore ~/keystores/pantry-tracker-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias pantrytracker
# It prompts for: keystore password, key password, your name, org, locality.
# 10000 days is Google's recommended validity (~27 years).
```

**Back this up immediately.** Recommended: an encrypted backup somewhere
that survives a laptop loss (1Password vault, a backed-up encrypted disk
image, a printed copy in a safe). Without it, you cannot ship v1.0.x updates.

### One-time Gradle signing config

The release keystore must NOT live in git. Read its location and passwords
from `~/.gradle/gradle.properties` (or env vars in CI):

Add to `~/.gradle/gradle.properties` (on your machine, not in the repo):
```properties
# Path MUST be absolute — Gradle's file() resolves relative to the :app
# module dir, and does NOT expand ~. /home/you/... or /Users/you/... works;
# ~/keystores/... does not.
PANTRY_TRACKER_RELEASE_STORE_FILE=/home/you/keystores/pantry-tracker-release.jks
PANTRY_TRACKER_RELEASE_STORE_PASSWORD=<keystore password>
PANTRY_TRACKER_RELEASE_KEY_ALIAS=pantrytracker
PANTRY_TRACKER_RELEASE_KEY_PASSWORD=<key password>
```

The matching `signingConfigs.release { ... }` block lives in
[`app/build.gradle.kts`](../../app/build.gradle.kts) and reads these four
properties. Three configurations are supported:

- **All four set** → release signing wired; `assembleRelease` produces
  `app-release.apk` (signed, installable).
- **None set** → release signing left unconfigured; `assembleDebug` works
  normally; `assembleRelease` produces `app-release-unsigned.apk` (note the
  suffix — that file cannot be installed; useful only for build-size checks).
- **Some-but-not-all set** → configuration fails fast with a
  `GradleException` naming the missing properties, so a typo in one
  property doesn't surface as a generic `MissingValueException` that breaks
  even `assembleDebug`.

The store-file path is checked for existence at configuration time, so a
typo'd path fails with a clear message instead of surfacing late during
`validateSigningRelease`.

### Build the release APK

```bash
./gradlew :app:assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
# If you see "No such file" — only app-release-unsigned.apk exists — your
# keystore properties weren't picked up. Re-check ~/.gradle/gradle.properties.
```

If signing succeeded, `apksigner verify` should pass:
```bash
$ANDROID_HOME/build-tools/<latest>/apksigner verify \
  app/build/outputs/apk/release/app-release.apk
# On success: prints "Verifies" and exits 0.
# (Add --quiet to suppress the "Verifies" line if scripting.)
```

### Get it onto a device

Two flavors:

**Your own device, fastest:**
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

(`adb install` works with release APKs too — the device doesn't know or
care, as long as the signature is valid.)

**Someone else's device (sideload):**
1. Email / Signal / cloud-share the `.apk` file to the recipient.
2. On the recipient's device:
   - Settings → Security → "Install unknown apps" → enable for their
     file manager / browser / messaging app (Android 8+).
   - Tap the `.apk` file.
   - Tap Install.

The recipient sees the standard install dialog plus an "Unknown source"
warning (because the APK isn't from the Play Store). After install, no
updates arrive automatically — every new version is another sideload.

### Updating an existing install

The new APK must be signed with the **same keystore** as the previous
install. Android refuses to install an update with a different signing
identity (and the recipient can't override this without uninstalling
first, which wipes their data).

This is why R-5 in [arc42 §11](../architecture/11-risks-and-technical-debt.md)
calls keystore loss a catastrophic risk.

---

## C. Firebase App Distribution / Play Store

The "real" distribution paths. Not used for v1.0 but documented so the
upgrade path is clear.

### Firebase App Distribution

- Free, no Play Store fee, supports invited testers by email.
- Setup: create a Firebase project, link your app, install the
  `firebase-appdistribution` Gradle plugin, configure a service account
  for CI.
- Workflow:
  ```bash
  ./gradlew :app:appDistributionUploadRelease \
    --serviceCredentialsFile=ci-service-account.json \
    --testers="someone@example.com,otherperson@example.com" \
    --releaseNotes="See CHANGELOG.md"
  ```
- Testers get an email with a link; tapping it from the device installs
  the APK and **auto-updates** subsequent versions.

### Play Store internal track

- One-time: Google Play Developer account ($25).
- One-time: switch from APK to AAB output (`./gradlew :app:bundleRelease`).
- One-time: create the listing on the Play Console, upload the first AAB
  to the "Internal testing" track.
- Subsequent uploads land on the same track; testers from your tester
  group get an auto-update via Play Store like any other app.

Both paths require the same release keystore from section B. The keystore
is the load-bearing piece either way.

---

## v1.0 release-cut checklist

When it's time to ship v1.0 (not part of this PR — pre-flight only):

1. [ ] `app/build.gradle.kts`: bump `versionCode = 2`, `versionName = "1.0.0"`
2. [ ] Wire the `signingConfigs.release { ... }` block from section B
       above into `app/build.gradle.kts`. Verify `signingConfig` is
       referenced in `buildTypes.release`.
3. [ ] Create the keystore (`keytool -genkey ...`). Back it up.
4. [ ] Populate the four `PANTRY_TRACKER_RELEASE_*` properties in
       `~/.gradle/gradle.properties`. **Do not commit them.**
5. [ ] `./gradlew :app:assembleRelease` and verify with `apksigner verify`.
6. [ ] Walk the [v1.0 UAT checklist](../uat/v1-uat-checklist.md) on a real
       device using the release APK. Sign off when all green.
7. [ ] **Lock dependencies for the tag** — see
       [§ Release-tag dependency-lock procedure](#release-tag-dependency-lock-procedure)
       below. Produces one extra commit immediately before the tag.
8. [ ] Tag the commit: `git tag -a v1.0 -m "v1.0 release" && git push origin v1.0`
9. [ ] Create a GitHub Release attaching `app-release.apk`:
       `gh release create v1.0 app/build/outputs/apk/release/app-release.apk --notes-file CHANGELOG.md`
10. [ ] Install the release APK on your daily-driver device.

### Release-tag dependency-lock procedure

Day-to-day, `app/gradle.lockfile` is **gitignored** — it's regenerated each
Security-CI run from the current resolved dependency graph (commenting a
developer-local snapshot would cause OSV-Scanner to report drift no one can
explain). At a release tag, the priority flips: the tag commit needs an
**immutable record of exactly what shipped** so post-hoc CVE forensics has
a concrete answer.

Run these commands as the commit immediately preceding the tag (between
checklist steps 6 and 8 above):

```bash
# Regenerate the lockfile from the current resolved graph.
./gradlew :app:dependencies --write-locks

# Force-add (it's gitignored) and commit on its own so the diff is
# reviewable as "the dependency snapshot for v1.0.0", not buried in
# unrelated work.
git add -f app/gradle.lockfile
git commit -m "chore(release): lock dependencies for v1.0.0"
```

Then proceed with step 8 (`git tag -a ...`). The tag commit will include
the lockfile; the immediately-following commit on `main` will not (because
day-to-day no-commit policy resumes, and the file's gitignored). That's
intentional — the lockfile is a release artifact, not a maintained file.

### Note: `distributionSha256Sum` must move atomically with `distributionUrl`

`gradle/wrapper/gradle-wrapper.properties` pins both `distributionUrl` and
`distributionSha256Sum` for SR-5 (defends against a MITM that swaps the
Gradle distribution archive). When upgrading the Gradle wrapper, use:

```bash
./gradlew wrapper --gradle-version X.Y.Z \
  --gradle-distribution-sha256-sum $(curl -sSL \
    https://services.gradle.org/distributions/gradle-X.Y.Z-all.zip.sha256)
```

This is the **only** invocation form that updates both fields atomically.
Running plain `./gradlew wrapper --gradle-version X.Y.Z` updates the URL
but leaves the *old* SHA in place — the next wrapper run will then refuse
to start because the new archive doesn't match the old hash.

---

## Common gotchas

| Symptom | Cause | Fix |
|---------|-------|-----|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on update | New APK signed with a different keystore than the previous install | Uninstall the old version first (loses data), or — if the keystore is recoverable — re-sign with the right keystore |
| `INSTALL_FAILED_USER_RESTRICTED` | Sideload disabled by MDM / parental controls | Enable in Settings → Security → Install unknown apps (per-app for Android 8+) |
| `adb` says "device unauthorized" | First-connection RSA prompt was missed / dismissed on the device | `adb kill-server && adb start-server`, replug, watch the device for the prompt |
| `apksigner: ERROR: …minSdkVersion…` | Build-tools version too old for the configured `minSdk` | `sdkmanager "build-tools;<latest>"`, then call `apksigner` from the new build-tools dir |
| First scan on a fresh install always errors | ML Kit barcode model is still downloading from Play Services | Wait ~30 s, retry. See [arc42 §11 R-2](../architecture/11-risks-and-technical-debt.md). |
| Build fails on `assembleRelease` with "key not found" but assembleDebug works | Gradle properties not set in `~/.gradle/gradle.properties` | Confirm the four `PANTRY_TRACKER_RELEASE_*` keys are present and non-empty |
| `assembleRelease` succeeds but `app/build/outputs/apk/release/app-release.apk` doesn't exist (only `app-release-unsigned.apk` does) | Keystore properties absent or empty — AGP emitted unsigned APK | Populate the four `PANTRY_TRACKER_RELEASE_*` properties (see §B above) and re-run |
| `GradleException: Incomplete release-signing config. Missing: …` on any Gradle command | Some keystore properties are set but not all four | Set the missing one(s), OR unset `PANTRY_TRACKER_RELEASE_STORE_FILE` to revert to the unsigned-release fallback |
| `PANTRY_TRACKER_RELEASE_STORE_FILE points at <path> which does not exist` | Path typo, moved keystore, or used `~` (Gradle doesn't expand it) | Use an absolute path (`/home/you/...` not `~/...`) and confirm the file is at that path |
