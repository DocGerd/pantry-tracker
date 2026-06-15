# Plan — eliminate vulnerable Netty from the build's test toolchain

> **Tracking issue:** [#264](https://github.com/DocGerd/pantry-tracker/issues/264)
> **Date:** 2026-06-15
> **Precedent:** [#151](https://github.com/DocGerd/pantry-tracker/issues/151) (wave one), `docs/security-posture.md` §"Build-time vs. runtime exposure model".
> **Governance:** Claude opens the PR + runs review; the **human** merges. Claude does NOT merge to `develop`/`main`.

## Goal

Remove known-vulnerable `io.netty` from the build entirely — not just from the
shipped APK (where it was already absent) but from the **CI/dev test toolchain**
too — so the recurring Netty Dependabot alerts (wave one = #2–#28 / #151; wave
two = #29–#32, dismissed 2026-06-15) are resolved **at the source** rather than
dismissed per-wave.

Rationale for fixing rather than only dismissing: even though Netty never ships,
a vulnerability-free build environment is a legitimate hygiene goal (it protects
the CI runner and developer machines, and it converts *phantom* alerts into
*accurate* ones — a future CVE against the pinned version would be real and
actionable, not noise).

## Verified root cause (2026-06-15)

Netty ships nowhere and is on no *runtime* classpath:

| Surface | Netty present? |
|---|---|
| `git grep`, `app/gradle.lockfile`, `settings-gradle.lockfile`, `:buildEnvironment`, `releaseRuntimeClasspath`, `debugRuntimeClasspath` | ✗ none |
| `:app` config `unified-test-platform-core` | ✓ grpc-netty 1.57.2 → **netty 4.1.93.Final** |
| `:app` config `unified-test-platform-android-test-plugin-host-emulator-control` | ✓ grpc-netty 1.69.1 → **netty 4.1.110.Final** |

The only Netty in the whole build comes from **Google's Unified Test Platform
(UTP)** — the instrumented-test orchestrator AGP runs for `connectedAndroidTest`.
Its `com.google.testing.platform:core` and `com.android.tools.emulator:proto`
modules pull `io.grpc:grpc-netty` → Netty. These are **two `:app`-project
configurations**, which is the key fact: a Gradle `resolutionStrategy` *can*
reach them (unlike a plugin-classpath transitive, which only an AGP upgrade could
move).

GitHub's **Automatic Dependency Submission** (`gradle/actions/setup-gradle`)
force-resolves these configs and submits them, which is why Dependabot alerts on
them. The real runtime CVE surface is separately and accurately gated by
**OSV-Scanner** over `app/gradle.lockfile` (Netty verified absent there).

## The fix (chosen) — pin Netty to the patched release

`app/build.gradle.kts`:

```kotlin
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty") {
            useVersion("4.1.135.Final")
            because("CVE-2026-44249/45416/47244/48043: pin AGP UTP test-tooling Netty " +
                "to the patched release (build-time only; not shipped). See #264.")
        }
    }
}
```

**Verified locally (2026-06-15):**
- Both UTP configs now resolve **all** `io.netty:*` modules to `4.1.135.Final`
  (patched; CVE range is `≤ 4.1.134.Final`). No vulnerable Netty remains anywhere.
- `app/gradle.lockfile` is **byte-identical** after `:app:dependencies
  --write-locks` (the pin is a no-op for the shipped/runtime graph) → OSV gate
  unaffected, no lockfile churn.
- Full instrumented-test *execution* under UTP with the pinned Netty is validated
  by the **required CI `androidTest` job** (it runs `connectedDebugAndroidTest`
  through UTP and blocks merge if UTP breaks). Risk is low: 4.1.93/4.1.110 →
  4.1.135 is a binary-compatible patch bump within Netty 4.1.x, and gRPC uses
  stable Netty APIs.

## Alternatives considered (not chosen)

| Option | Why not chosen |
|---|---|
| **Disable Automatic Dependency Submission** (UI toggle) | Hides the alerts instead of fixing the dependency; loses Dependabot's native transitive coverage; UI-only (no API); leaves vulnerable Netty in the test toolchain. |
| **Upgrade AGP** | No stable AGP bumps UTP's Netty — 9.2.1 is latest stable; 9.3.0 is alpha-only. Adopting alpha build tooling for a build-time CVE is worse than the CVE. |
| **Scope the submission to `releaseRuntimeClasspath`** | Brittle (case-sensitive regex; a typo submits an empty graph that auto-resolves *real* alerts) and still leaves vulnerable Netty in the build. |
| **Dismiss-per-wave only** (status quo) | Recurring toil; vulnerable Netty stays in the toolchain. Already done for the 4 current alerts as the interim step. |

## Tasks

- [x] Verify root cause + that the only Netty is in `:app` UTP configs (forceable).
- [x] Immediate: dismiss #29–#32 as `tolerable_risk` (done; open count → 0).
- [x] Track + link: issue #264 (labels `security`+`chore`).
- [x] Add the resolution pin to `app/build.gradle.kts`; verify Netty → 4.1.135.Final and lockfile unchanged.
- [ ] PR (base `develop`, `Closes #264`): the pin + this plan + `security-posture.md` note. Run the mandatory multi-agent `/pr-review` cycle; hand off to human for merge.
- [ ] Let the required CI `androidTest` job validate UTP execution with the pinned Netty (gate; blocks merge if broken).
- [ ] Post-merge verify: next default-branch submission reports Netty `4.1.135.Final`; `gh api .../dependency-graph/sbom --jq '[.sbom.packages[]|select(.name|test("io.netty"))|.versionInfo]|unique'` shows only `4.1.135.Final`, and no new Netty alerts open.

## Residual / notes

- The pin auto-applies during the force-resolved submission too, so the SBOM
  flips to the patched version — clearing the phantom-ness at the source while
  keeping accurate future alerting.
- If a stable AGP later ships UTP with Netty > 4.1.134.Final, drop the pin block
  (noted in the code comment).
- Retroactive auto-close of the already-submitted (vulnerable) SBOM entries is
  not guaranteed (dependabot-core #15010); the 4 alerts are already dismissed, so
  this only affects how fast the SBOM version flips — manual dismissal remains the
  fallback for any straggler.

## Scorecard Branch-Protection (Code-Scanning #1) — out of scope

The lone Code-Scanning alert (Scorecard Branch-Protection 3/10) is a structural
solo-maintainer accept-risk already documented in `docs/security-posture.md`
§"Branch-Protection" and tracked by [#139](https://github.com/DocGerd/pantry-tracker/issues/139)
/ [#141](https://github.com/DocGerd/pantry-tracker/issues/141). Not a fixable
code vulnerability; no new issue.
