# Security Policy

## Supported Versions

Only the `main` branch is supported. There are no released versions yet.

## Reporting a Vulnerability

Please report security vulnerabilities **privately**. Do NOT open a public GitHub
issue for security-sensitive findings.

- **Email:** claude@docgerdsoft.de
- **Alternative:** open a GitHub Security Advisory draft via
  https://github.com/DocGerd/pantry-tracker/security/advisories/new

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

For time-sensitive issues, the GitHub Security Advisory channel is preferred —
it routes to a notification channel I check more reliably than email.

### Scope

In scope:
- Code in this repository (`DocGerd/pantry-tracker`).
- The CI/CD pipeline in `.github/workflows/`.

Out of scope:
- Third-party dependencies — please report to their maintainers directly, then
  optionally let us know so we can pin/upgrade.
- Vulnerabilities requiring root access on a user's device.
