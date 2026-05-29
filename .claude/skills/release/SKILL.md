---
name: release
description: Cut a Pantry Tracker release the GitFlow way — version bump on a release/X.Y.Z branch, signed-APK build (with the GRADLE_USER_HOME signing-props bridge), Room-migration UAT when the schema changed, dependency lock, then — after the HUMAN merges to main+develop — tag the merge commit and create a one-shot immutable GitHub Release. Use when the human is cutting/shipping a new version (vX.Y.Z), preparing a release branch, or says "ship", "cut a release", "do the release", "publish vX.Y.Z". Codifies docs/release/SHIPPING.md §B and the bitten gotchas (immutable-release one-shot, signing-props masking, tag-burn, spurious 422).
---

# Release — GitFlow release-cut recipe

Codifies [`docs/release/SHIPPING.md`](../../../docs/release/SHIPPING.md) §B so a
release can be cut without re-deriving the sequence, the signing-props bridge,
or the immutable-release gotchas each time. **SHIPPING.md is the source of
truth** — this skill is the actionable distillation, not a replacement. When the
two disagree, SHIPPING.md wins; update this skill.

This skill is **human-initiated**. Releases have irreversible side effects
(tags, immutable GitHub Releases). Run it when the human says to ship.

## Governance — read before any step

Per [`CLAUDE.md`](../../../CLAUDE.md) §"only humans merge to develop or main"
and [`GOVERNANCE.md`](../../../GOVERNANCE.md):

- **The human merges** the `release/X.Y.Z` PR into **both** `main` and
  `develop`. Claude MUST NOT `gh pr merge`, `git push origin main`, or
  `git push origin develop`.
- **Claude MAY**: build APKs, push the *release branch*, open PRs, **create and
  push tags** (`git push origin vX.Y.Z` is a tag push, not a protected-branch
  push — allowed), and **create GitHub Releases**.
- The dependency-lock commit therefore goes on the **release branch
  (pre-merge)**, NOT directly on `main` — it rides into `main` through the human's
  merge, producing an identical shipped lockfile without any direct `main` push.
  (SHIPPING.md frames it as "a commit on main"; that wording is for a human
  doing it by hand. The release-branch placement is the Claude-safe equivalent.)
- There is a **HARD STOP** at step 7. Do not proceed to tagging until the human
  confirms the merge has landed.

## Preconditions

- Clean working tree; on `develop` (or about to branch from it).
- Release signing props available — the four `PANTRY_TRACKER_RELEASE_*` keys in
  `~/.gradle/gradle.properties` (see SHIPPING.md §B "One-time Gradle signing
  config"). Without them, `assembleRelease` silently emits
  `app-release-unsigned.apk`.
- A local emulator if the release contains a Room schema migration (for the
  upgrade-install UAT in step 4) — see [`scripts/uat/README.md`](../../../scripts/uat/README.md).
- The version numbers decided: next `versionCode` (integer +1) and `versionName`.

## Steps

### 1. Cut the release branch off develop

```bash
git switch -c release/X.Y.Z origin/develop
```

### 2. Version bump + CHANGELOG

- `app/build.gradle.kts`: bump `versionCode` (+1) and `versionName = "X.Y.Z"`.
- `CHANGELOG.md`: promote `[Unreleased]` → `[X.Y.Z] — <date>` (terse-by-policy).

### 3. Build + sign the release APK — bridge the signing props first

If `GRADLE_USER_HOME` is redirected (e.g. `/tmp/gradle-user-home` in the WSL
sandbox), Gradle reads `$GRADLE_USER_HOME/gradle.properties`, NOT
`~/.gradle/gradle.properties`, and you silently get an **unsigned** APK. Bridge
the props first:

```bash
grep '^PANTRY_TRACKER_RELEASE_' ~/.gradle/gradle.properties \
  > "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

./gradlew :app:assembleRelease
ls -lh app/build/outputs/apk/release/    # MUST be app-release.apk, NOT app-release-unsigned.apk
```

If you see only `app-release-unsigned.apk`, the props didn't take — re-check the
bridge and `~/.gradle/gradle.properties`. The filename suffix is AGP's only
signal. (See SHIPPING.md "Common gotchas" for the full table.)

### 4. Room-migration UAT — ONLY if the release changes the schema

If this release bumps the Room schema (new `MIGRATION_(n-1)_n`), the
upgrade-install path is **non-negotiable** before tagging — JVM tests can't
prove an over-the-top install preserves data. Run the matching emulator script:

```bash
# emulator already running; or prefix BOOT_EMULATOR=1 to auto-boot the AVD
scripts/uat/verify-migration-<n-1>-<n>.sh
```

It downloads the prior release APK, seeds rows, installs this build on top, and
asserts the data survived + new columns back-filled. Exits non-zero on failure.
It also leaves a signed `app-release.apk`, doubling as a signing-continuity
check (`install -r` over the prior version only succeeds with the lifetime
keystore). See `scripts/uat/README.md` for AVD prerequisites (incl. `libpulse0`).

If no schema change: skip, but still walk the relevant
[UAT checklist](../../../docs/uat/v1-uat-checklist.md) scenarios on a real device.

### 5. Verify signature + R8 keep-rules

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# expect: "Signer #1 certificate SHA-256 digest: ec9a4bb8…b3d9" (lifetime cert)

scripts/uat/verify-r8-keep-rules.sh   # confirms @Serializable/@Entity/@Dao/@Database survived R8
```

A cert SHA-256 other than `ec9a4bb8…b3d9` means the wrong keystore — STOP, every
prior install would reject the update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

### 6. Lock dependencies on the release branch (pre-merge)

Capture the exact resolved-dependency snapshot for post-hoc CVE forensics.
`app/gradle.lockfile` is tracked on `develop`; regenerate once more here so the
tag's lockfile provably matches what shipped. Do this on the **release branch**
(not `main` — see Governance):

```bash
./gradlew :app:dependencies --write-locks
if ! git diff --quiet app/gradle.lockfile; then
  git add app/gradle.lockfile
  git commit -m "chore(release): lock dependencies for vX.Y.Z"
else
  echo "Lockfile already current; no lock commit needed."
fi
```

Do **not** `git rm --cached` the lockfile post-release (the untrack-on-develop
pattern was a now-fixed dependabot workaround; see CLAUDE.md).

### 7. Open the release PR(s) — then HARD STOP for the human

Commit the version bump + CHANGELOG, push the release branch, and open the PR
into **both** `main` and `develop` (GitFlow). Link the milestone/issues. Run the
`pr-review` skill as usual.

```bash
git push -u origin release/X.Y.Z
gh pr create --base main   --head release/X.Y.Z --title "release: vX.Y.Z" --body "…"
gh pr create --base develop --head release/X.Y.Z --title "release: vX.Y.Z (back-merge)" --body "…"
```

> ⛔ **HARD STOP.** A human reviews and merges both PRs. Claude does NOT merge and
> does NOT push `main`/`develop`. Wait for the human to confirm the merge landed.

### 8. After the human merges — tag the merge commit on main

Fetch the merged `main` and tag its HEAD (the merge commit). The tag *push* is
allowed; do NOT push the `main` branch.

```bash
git fetch origin main
git tag -a vX.Y.Z origin/main -m "vX.Y.Z release"
git push origin vX.Y.Z
```

> **Immutable tags (ruleset 16948700).** A `v*` tag, once it hosts a release,
> can never host another. If you burn a tag (see step 9 gotcha), cut the next
> patch version — do not try to reuse it. (v1.3.0 was burned this way → v1.3.1.)

### 9. Create the GitHub Release — ONE-SHOT, APK attached at creation

Releases here are **immutable**: assets are frozen at publish, so the
create-then-`gh release upload` two-step **fails**, and `gh release delete` to
retry **burns the tag name forever**. Attach the APK at creation, one-shot:

```bash
gh release create vX.Y.Z \
  app/build/outputs/apk/release/app-release.apk \
  --title "vX.Y.Z — <headline>" \
  --notes-file <notes-or-CHANGELOG-section>
```

- **Never** the two-step. **Never** `gh release delete` to fix a mistake.
- If `gh` returns `HTTP 422: ReleaseAsset.name already exists` — that 422 is
  **spurious**; the upload usually *succeeded*. Verify with `gh release view
  vX.Y.Z` before assuming failure. Do NOT delete-and-recreate.

### 10. Verify the published release

```bash
sha256sum app/build/outputs/apk/release/app-release.apk      # record in notes
gh attestation verify app/build/outputs/apk/release/app-release.apk -R DocGerd/pantry-tracker
```

GitHub auto-generates a Sigstore artifact attestation per asset on immutable
releases (the former cosign + SLSA `release.yml` was retired — issue #210).
Record the APK SHA-256 + cert SHA-256 (`ec9a4bb8…b3d9`) in the release notes so
downstream users can verify integrity + authenticity.

## After release

- Update the project memory `project_*_resume_point.md` (version shipped, tag,
  APK SHA-256) per the `wrap` skill.
- If the cut surfaced a NEW gotcha, fold it into SHIPPING.md "Common gotchas"
  and/or CLAUDE.md (eviction-criteria-aware) — don't duplicate what a CI gate
  already catches.

## Cross-reference

Bitten gotchas this skill guards against, all in
[`docs/release/SHIPPING.md`](../../../docs/release/SHIPPING.md) "Common gotchas"
and CLAUDE.md "Things that have bitten past sessions": immutable-release
one-shot / tag-burn; `GRADLE_USER_HOME` masking → unsigned APK; spurious 422;
`distributionSha256Sum` atomic wrapper upgrade; lockfile-stays-tracked.
