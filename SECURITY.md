# Security Policy

## Supported Versions

Only the `main` branch is supported. There are no released versions yet.

## Reporting a Vulnerability

Please report security vulnerabilities **privately**. Do NOT open a public GitHub
issue for security-sensitive findings.

- **Preferred:** open a GitHub Security Advisory draft via
  https://github.com/DocGerd/pantry-tracker/security/advisories/new
  (a private channel that routes directly to the maintainer).

### What to include

- A clear description of the issue and where it lives in the code (file path + line).
- Steps to reproduce or a proof-of-concept.
- The impact you've assessed (data exposure, privilege escalation, etc.).
- Your contact for follow-up.

### What to expect

This is a personal hobby project — there are no formal SLAs and no on-call
rotation. The following are best-effort targets, not commitments:

- Acknowledgement: typically within **14 days**.
- First assessment: typically within **30 days**.
- Coordinated fix and disclosure: typically within **180 days**, longer for
  complex issues (we will agree on a timeline together).

The GitHub Security Advisory channel is monitored on a best-effort basis;
there is no on-call rotation, but reports are not dropped. If a security
report goes unanswered, see the **Continuity & succession** plan in
[`GOVERNANCE.md`](GOVERNANCE.md) — the project is Apache-2.0 and may be
forked and continued.

### Scope

In scope:
- Code in this repository (`DocGerd/pantry-tracker`).
- The CI/CD pipeline in `.github/workflows/`.

Out of scope:
- Third-party dependencies — please report to their maintainers directly, then
  optionally let us know so we can pin/upgrade.
- Vulnerabilities requiring root access on a user's device.

## Verifying a release

Every `app-release.apk` on a GitHub Release is signed with the project's
**lifetime Android signing certificate** (SHA-256 `ec9a4bb8…b3d9`, unchanged
since v1.0.0), and its **SHA-256** is recorded in that release's notes. To
verify a download before installing:

```bash
# 1) integrity — compare against the SHA-256 printed on the Release page
sha256sum app-release.apk

# 2) authenticity — confirm the signing certificate
apksigner verify --print-certs app-release.apk
# expect: "Signer #1 certificate SHA-256 digest: ec9a4bb8…b3d9"
```

A mismatch on either check means the artifact is not an authentic release —
do not install it. The signing identity is stable across all v1.x updates; a
future key change would be announced in the affected release's notes.

**Build/asset attestation.** Because this repository has GitHub immutable
releases enabled, GitHub automatically generates a Sigstore-backed
[artifact attestation](https://docs.github.com/en/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds)
binding each release asset's digest to the tag and commit. With `gh` ≥ 2.49:

```bash
gh attestation verify app-release.apk -R DocGerd/pantry-tracker
```

> An earlier `.github/workflows/release.yml` attempted additional keyless
> cosign + SLSA-generator artifacts, but that attach-after-publish design is
> incompatible with immutable releases and has been retired (issue #210); the
> auto-generated attestation above supersedes its integrity role. Adding
> *source-level* build provenance (reproducible builds, or CI-side signing) is
> tracked in #210. Releases v1.0.x–v1.2.x are jarsigner-signed with the same
> lifetime cert.

## See also

- [`docs/security-posture.md`](docs/security-posture.md) — what we do
  proactively (CI gates, dependency hygiene, build hardening) and the
  rationale for any structural OpenSSF Scorecard zeros.
