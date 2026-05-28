# 0005. R8 minification + `shrinkResources` for release builds

## Status

Accepted — 2026-05-28

(Backfills the SR-9 / PR #62 decision landed 2026-05-26, in effect on the
release buildType since v1.2.0.)

## Context

Through v1.1.0 the release buildType shipped with `isMinifyEnabled = false`
and `isShrinkResources = false`. That left several known shortcomings:

- The release APK at v1.1.0 measured **~38.65 MB** (40,523,977 bytes),
  carrying a substantial amount of Kotlin stdlib, Compose, Coil, Ktor, and
  Room code that the app never reaches.
- The attack surface was larger than necessary — every public method on
  every dependency stayed reachable in the shipped DEX.
- The `lintVitalRelease` Gradle task ran against the un-shrunk output, so
  its analysis was operating on bytecode the user would never see.

R8 ([Android's default code shrinker / optimiser][r8], replaced ProGuard in
AGP 3.4) addresses these by tracking actual reachability and removing
unused classes, methods, fields, and resources from the release output.
Two structural risks pushed back against enabling it earlier:

1. **Reflection-stripping.** R8 strips classes that no source-level
   reference reaches. `kotlinx.serialization`'s `@Serializable` types and
   Room's `@Entity` / `@Dao` types are reached via reflection or generated
   code outside the user's source, so a naive first-time R8 enable
   typically breaks JSON decoding ("class not found") and Room DAO method
   resolution. The well-known mitigation is belt-and-braces `-keep` rules
   in `app/proguard-rules.pro`.
2. **Late-binding silent failures.** A class that R8 incorrectly strips
   doesn't fail at build time — it fails at runtime on the user's device,
   typically with `ClassNotFoundException` or
   `NoSuchMethodException` inside a code path that JVM unit tests don't
   reach (because tests run against the un-shrunk debug variant). The
   risk is "release APK builds successfully, installs successfully,
   crashes on first OFF lookup".

SR-9 (issue #36, sub-issue 9) addressed both risks before flipping the
flags. SR-80 (PR #86) added a follow-up static-inspection guard that
asserts the `-keep` rules actually held after the shrink.

[r8]: https://developer.android.com/build/shrink-code

## Decision

The release buildType in
[`app/build.gradle.kts`](../../app/build.gradle.kts) enables both flags:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        // SR-19: explicit declarations even though AGP defaults match.
        isDebuggable = false
        isJniDebuggable = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfigs.findByName("release")
            ?.takeIf { it.storeFile != null }
            ?.let { signingConfig = it }
    }
}
```

`app/proguard-rules.pro` carries **belt-and-braces `-keep` rules** for:

- `kotlinx.serialization` `@Serializable` types — `OffProduct`,
  `OffApiEnvelope` (kept by name so R8 cannot strip the no-arg constructor
  the generated serializer relies on).
- Room artifacts — `Product`, `OffLookupCacheEntry` (entities),
  `ProductDao`, `OffLookupCacheDao` (DAOs), `AppDatabase`, and
  `Converters` (defensively).

SR-80 follow-up: `scripts/uat/verify-r8-keep-rules.sh`, runnable via
`./gradlew :app:assembleRelease -PverifyR8=true`, statically inspects the
post-shrink DEX and asserts every `@Serializable` and `@Entity` class
survives. The check is gated on the `verifyR8` Gradle property so dev
builds are not slowed down; release UAT runs it explicitly.

`isDebuggable` and `isJniDebuggable` are explicitly set to `false`
(SR-19) even though that matches the AGP default — defending against a
future merge accident or plugin that silently flips either flag. The
manifest-level guard `verifyReleaseManifestNotDebuggable` (also SR-19 era)
asserts `android:debuggable="true"` is absent from the merged release
manifest at the end of every `assembleRelease`.

## Consequences

**Positive.**

- **APK size: 38.65 MB → 23.02 MB**, a 40.4% reduction (~16.4 MB saved)
  observed on the v1.2.0 build. Smaller download, smaller install
  footprint, smaller attack surface.
- `lintVitalRelease` now runs against the minified output — analysis
  matches what the user installs.
- R8 statically removes code paths that no `Activity` / `ContentProvider` /
  test reaches, including most of the Ktor logging plugin and large
  swaths of Coil that v1 doesn't exercise.
- The dedicated `-keep` rules + static-inspection script (SR-80) catch
  reflection-stripping regressions at build/UAT time, not at user-install
  time.

**Negative.**

- **Stack traces from release builds are obfuscated.** R8 renames classes
  and methods to short symbols. Without the mapping file
  (`app/build/outputs/mapping/release/mapping.txt`), a user-reported
  stack trace is unreadable. Mitigation: the mapping file is archived
  alongside every release tag — the release procedure in
  [`docs/release/SHIPPING.md`](../release/SHIPPING.md) covers attaching
  it to the GitHub Release as a private-but-retained artifact. (Currently
  the repo is single-user, so the practical impact is minimal.)
- **Adding new reflectively-accessed types requires updating
  `proguard-rules.pro`.** Future `@Serializable` types (a new OFF
  envelope field, a v2 API response) and new Room `@Entity` types
  (additional cache tables, settings storage) need an entry in the
  `-keep` rules and a static-inspection pass. The SR-80 script makes the
  omission loud rather than silent.
- **`isShrinkResources = true` requires `isMinifyEnabled = true`.**
  AGP refuses the combination otherwise; the two flags must move together.
- **First build of any UAT cycle takes longer.** R8 adds a non-trivial
  Gradle step. Mitigated by the AGP build cache; full
  `assembleRelease` from a clean checkout is observably slower but the
  incremental path is similar to the un-shrunk case.

## References

- Original PR / spec: SR-9 / PR
  [#62](https://github.com/DocGerd/pantry-tracker/pull/62) —
  "chore(sr-9): enable R8 minify + shrinkResources".
- Static-inspection follow-up: SR-80 / PR
  [#86](https://github.com/DocGerd/pantry-tracker/pull/86) —
  "chore(sr-80): add R8 static-inspection script for @Serializable +
  @Entity survival".
- CHANGELOG entry: [`CHANGELOG.md`](../../CHANGELOG.md) §"Security"
  — "R8 strips unused code from the release artifact, reducing attack
  surface."
- Implementation:
  - [`app/build.gradle.kts`](../../app/build.gradle.kts) — release
    buildType, `isMinifyEnabled` / `isShrinkResources` flags, the
    `verifyR8KeepRules` task wiring (gated on `-PverifyR8=true`), and the
    `verifyReleaseManifestNotDebuggable` guard.
  - [`app/proguard-rules.pro`](../../app/proguard-rules.pro) — `-keep`
    rules for `@Serializable` and Room artifacts.
  - [`scripts/uat/verify-r8-keep-rules.sh`](../../scripts/uat/verify-r8-keep-rules.sh)
    — SR-80 static-inspection script.
- Release procedure: [`docs/release/SHIPPING.md`](../release/SHIPPING.md)
  — `verifyR8` invocation in the release UAT step.
- UAT checklist: [`docs/uat/v1-uat-checklist.md`](../uat/v1-uat-checklist.md)
  §"v1.2 minified-APK pass" — checklist for the first minified release.
- Related ADR: [`0002-room-local-persistence.md`](0002-room-local-persistence.md)
  — Room `@Entity` types whose survival the `-keep` rules defend.
