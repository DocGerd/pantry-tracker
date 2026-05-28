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
there is no on-call rotation, but reports are not dropped.

### Scope

In scope:
- Code in this repository (`DocGerd/pantry-tracker`).
- The CI/CD pipeline in `.github/workflows/`.

Out of scope:
- Third-party dependencies — please report to their maintainers directly, then
  optionally let us know so we can pin/upgrade.
- Vulnerabilities requiring root access on a user's device.

## Verifying release signatures (v1.3.0 onward)

Starting with v1.3.0, the `app-release.apk` published to each GitHub
Release is accompanied by a [keyless cosign](https://docs.sigstore.dev/cosign/signing/signing_with_blobs/)
signature and [SLSA Build L3](https://slsa.dev/spec/v1.0/levels#build-l3)
provenance attestation. Both are produced by
[`.github/workflows/release.yml`](.github/workflows/release.yml) on
`release: published` — there is no manual signing step for the maintainer.

To verify the APK before installing, download all four assets from the
release page (`app-release.apk`, `app-release.apk.sig`, `app-release.apk.pem`,
`app-release.apk.intoto.jsonl`) and run:

```bash
cosign verify-blob \
  --certificate app-release.apk.pem \
  --signature app-release.apk.sig \
  --certificate-identity-regexp 'https://github.com/DocGerd/pantry-tracker/.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  app-release.apk
```

Successful verification prints `Verified OK` and exits 0. A failure means
either the APK was modified after signing, or the signature did not come
from a GitHub Actions workflow in this repository.

Releases v1.0.x / v1.1.x / v1.2.x predate this workflow and are signed
**only** with the Android jarsigner keystore (cert SHA-256
`ec9a4bb8…b3d9`). They have no cosign signature or SLSA attestation;
they were not retroactively signed because an OIDC token minted today
cannot bind to the original release-time identity.

## See also

- [`docs/security-posture.md`](docs/security-posture.md) — what we do
  proactively (CI gates, dependency hygiene, build hardening) and the
  rationale for any structural OpenSSF Scorecard zeros.
