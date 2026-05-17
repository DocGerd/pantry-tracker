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

- Acknowledgement within **7 days**.
- A first assessment within **14 days**.
- A coordinated fix and disclosure within **90 days** of acknowledgement, unless
  the issue is particularly complex (in which case we will agree on a longer
  timeline together).

### Scope

In scope:
- Code in this repository (`DocGerd/pantry-tracker`).
- The CI/CD pipeline in `.github/workflows/`.

Out of scope:
- Third-party dependencies — please report to their maintainers directly, then
  optionally let us know so we can pin/upgrade.
- Vulnerabilities requiring root access on a user's device.
