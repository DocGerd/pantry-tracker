# OpenSSF Best Practices badge tracking

> **Source-of-truth for walking the BadgeApp form at
> https://www.bestpractices.dev/projects/13017.**
> Mirrors the tracking table from #140 so the table is preserved in the repo
> and survives issue archival or edits.
> Re-sync this doc with the BadgeApp form whenever criteria change
> (status changes, new questions added, justification text updated).

## How to use

1. Open https://www.bestpractices.dev/projects/13017 → **Edit**.
2. For each row below, locate the matching question on the form and set the
   dropdown to the listed status.
3. Copy the Justification cell verbatim into the per-question "Justification"
   textarea on the form.
4. After all passing rows are `Met` or `N/A`, submit (final) for the passing
   badge.
5. Continue with silver.

**Canonical IDs source:** [BadgeApp `criteria.yml`](https://github.com/coreinfrastructure/best-practices-badge/blob/main/criteria/criteria.yml).

**Scorecard linkage:** raising this from `InProgress` → `passing` lifts the Scorecard `CII-Best-Practices` check from 2/10 → 5/10; silver lifts it to 8/10; gold to 10/10.

## Passing badge — 67 criteria

| ID | Question (one-line) | Status to set | Justification text to paste |
|---|---|---|---|
| `description_good` | Project description present | `Met` | See [README.md](https://github.com/DocGerd/pantry-tracker/blob/develop/README.md) header. |
| `interact` | Discussion mechanism exists | `Met` | [GitHub Issues](https://github.com/DocGerd/pantry-tracker/issues) and PR comment threads. |
| `contribution` | Contribution process documented | `Met` | See [CONTRIBUTING.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CONTRIBUTING.md). |
| `contribution_requirements` | Contribution requirements documented | `Met` | See [CONTRIBUTING.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CONTRIBUTING.md) §How to contribute. |
| `floss_license` | FLOSS license | `Met` | Apache-2.0 — see [LICENSE](https://github.com/DocGerd/pantry-tracker/blob/develop/LICENSE). |
| `floss_license_osi` | OSI-approved license | `Met` | Apache-2.0 is OSI-approved (https://opensource.org/license/apache-2-0). |
| `license_location` | LICENSE in standard location | `Met` | [LICENSE](https://github.com/DocGerd/pantry-tracker/blob/develop/LICENSE) at repo root. |
| `documentation_basics` | Basic docs exist | `Met` | [README.md](https://github.com/DocGerd/pantry-tracker/blob/develop/README.md) plus [docs/architecture/](https://github.com/DocGerd/pantry-tracker/tree/develop/docs/architecture) (arc42). |
| `documentation_interface` | External interface documented | `N/A` | Android sideload APK — no external programmatic interface. |
| `sites_https` | Project sites use HTTPS | `Met` | github.com is HTTPS-only. |
| `discussion` | Public discussion archived | `Met` | All discussion via GitHub Issues + PR comments — public-archived. |
| `english` | English used | `Met` | All public docs are in English. |
| `maintained` | Project actively maintained | `Met` | Three releases in 10 days (v1.0.0 → v1.2.0); weekly security scans active — see [CHANGELOG.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CHANGELOG.md). |
| `repo_public` | Public VCS repository | `Met` | https://github.com/DocGerd/pantry-tracker is public since 2026-05-27. |
| `repo_track` | VCS tracks all changes | `Met` | Git via GitHub. |
| `repo_interim` | Interim versions kept ≥ 6 months | `Met` | Full git history retained on GitHub indefinitely. |
| `repo_distributed` | Distributed VCS | `Met` | Git. |
| `version_unique` | Releases have unique versions | `Met` | SemVer tags v1.0.0 / v1.1.0 / v1.2.0 — see [tags](https://github.com/DocGerd/pantry-tracker/tags). |
| `version_semver` | SemVer used | `Met` | See [CHANGELOG.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CHANGELOG.md). |
| `version_tags` | Releases tagged | `Met` | See [tags](https://github.com/DocGerd/pantry-tracker/tags). |
| `release_notes` | Release notes per release | `Met` | See [CHANGELOG.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CHANGELOG.md) and [GitHub Releases](https://github.com/DocGerd/pantry-tracker/releases). |
| `release_notes_vulns` | Release notes identify fixed CVEs | `Met` | [CHANGELOG.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CHANGELOG.md) calls out security-relevant fixes (e.g. v1.0.1 chunked-encoding fix). |
| `report_process` | Reporting process documented | `Met` | See [SECURITY.md](https://github.com/DocGerd/pantry-tracker/blob/develop/SECURITY.md) and [`.github/ISSUE_TEMPLATE/`](https://github.com/DocGerd/pantry-tracker/tree/develop/.github/ISSUE_TEMPLATE). |
| `report_tracker` | Reports tracked in tracker | `Met` | [GitHub Issues](https://github.com/DocGerd/pantry-tracker/issues). |
| `report_responses` | Bug reports responded to | `Met` | See closed issues — maintainer responds to all reports. |
| `enhancement_responses` | Enhancement reports responded to | `Met` | Same — see closed issues. |
| `report_archive` | Reports publicly archived | `Met` | GitHub Issues are public-archived. |
| `vulnerability_report_process` | Vuln-report process documented | `Met` | See [SECURITY.md](https://github.com/DocGerd/pantry-tracker/blob/develop/SECURITY.md) §Reporting. |
| `vulnerability_report_private` | Private channel for vuln reports | `Met` | Private channel (GitHub Security Advisory) documented in [SECURITY.md](https://github.com/DocGerd/pantry-tracker/blob/develop/SECURITY.md) §Reporting. |
| `vulnerability_report_response` | Vuln reports responded to | `N/A` | No vuln reports received to date. |
| `build` | Build system exists | `Met` | Gradle — see [`build.gradle.kts`](https://github.com/DocGerd/pantry-tracker/blob/develop/build.gradle.kts) and [`app/build.gradle.kts`](https://github.com/DocGerd/pantry-tracker/blob/develop/app/build.gradle.kts). |
| `build_common_tools` | Common build tools | `Met` | Gradle + Android SDK — both standard. |
| `build_floss_tools` | Built with FLOSS tools | `Met` | Gradle (Apache-2.0); Android SDK FLOSS components; OpenJDK 21 (Temurin). |
| `test` | Automated test suite | `Met` | JUnit 4, Robolectric, Turbine — see [`app/src/test/`](https://github.com/DocGerd/pantry-tracker/tree/develop/app/src/test) and [`app/src/androidTest/`](https://github.com/DocGerd/pantry-tracker/tree/develop/app/src/androidTest). |
| `test_invocation` | Test invocation documented | `Met` | `./gradlew :app:test` — see [CLAUDE.md §Commands](https://github.com/DocGerd/pantry-tracker/blob/develop/CLAUDE.md). |
| `test_most` | Tests cover most new code | `Met` | All PRs include unit tests; multi-agent PR review gates on this. |
| `test_continuous_integration` | Tests run on every commit | `Met` | [`.github/workflows/ci.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/ci.yml) runs unit tests on every PR + push to develop/main. |
| `test_policy` | Test-addition policy documented | `Met` | See [`.github/pull_request_template.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/pull_request_template.md). |
| `tests_are_added` | Tests added with new features | `Met` | Enforced via PR template + multi-agent review. |
| `tests_documented_added` | Test-addition policy documented | `Met` | See [CONTRIBUTING.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CONTRIBUTING.md). |
| `warnings` | Compiler warnings used | `Met` | Detekt is configured strict — see [`detekt-config.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/detekt-config.yml); Lint baseline=0. |
| `warnings_fixed` | Warnings fixed promptly | `Met` | CI fails on any Detekt or Lint warning — see [`.github/workflows/ci.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/ci.yml). |
| `warnings_strict` | Strictest warning level | `Met` | Detekt with full rule set; CodeQL `security-and-quality` queries. |
| `know_secure_design` | Maintainer knows secure-design basics | `Met` | See [`docs/security/`](https://github.com/DocGerd/pantry-tracker/tree/develop/docs/security) review notes. |
| `know_common_errors` | Knows common implementation errors | `Met` | OWASP Mobile Top 10 considered — see [docs/security/security-review-2026-05-17.md](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/security/security-review-2026-05-17.md). |
| `crypto_published` | Crypto algorithms public | `Met` | No bespoke crypto; uses Android Keystore + standard TLS only. |
| `crypto_call` | Calls existing crypto | `Met` | Uses Android platform crypto exclusively. |
| `crypto_floss` | Crypto impl is FLOSS | `Met` | Android platform crypto (AOSP — Apache-2.0). |
| `crypto_keylength` | ≥ 112-bit key length | `Met` | TLS 1.2+ via Ktor/OkHttp defaults; APK signing key is 2048-bit RSA. |
| `crypto_working` | No known-broken algorithms | `N/A` | No bespoke crypto. |
| `crypto_weaknesses` | No known-weak algorithms | `Met` | No bespoke crypto; TLS 1.2+ defaults. |
| `crypto_pfs` | PFS supported | `Met` | TLS 1.2+ with ECDHE — Android platform default. |
| `crypto_password_storage` | Passwords hashed-and-salted | `N/A` | App stores no passwords. |
| `crypto_random` | CSPRNG used | `N/A` | App does no security-relevant randomness. |
| `delivery_mitm` | MITM countermeasures on download | `Met` | HTTPS-only GitHub Releases; APK signed with cert SHA-256 `ec9a4bb8…b3d9` — see [`docs/release/SHIPPING.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/release/SHIPPING.md). |
| `delivery_unsigned` | Cryptographic hashes published OR signed | `Met` | SHA-256 of each APK is listed on the GitHub Release page; APK additionally jarsigner-signed. |
| `vulnerabilities_fixed_60_days` | High-sev vulns fixed ≤ 60 days | `Met` | v1.0 → v1.2 cadence is days, not months — see [CHANGELOG.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CHANGELOG.md). |
| `vulnerabilities_critical_fixed` | No critical vulns in latest release | `Met` | OSV-Scanner gates merges — see [`.github/workflows/security.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/security.yml); 0 known vulns at v1.2.0. |
| `no_leaked_credentials` | No leaked credentials | `Met` | Gitleaks runs on every PR — see [`.github/workflows/security.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/security.yml). |
| `static_analysis` | Static analysis used | `Met` | CodeQL (`security-and-quality`) + Detekt — see [`.github/workflows/codeql.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/codeql.yml). |
| `static_analysis_common_vulnerabilities` | SA looks for common vulns | `Met` | CodeQL `security-and-quality` query suite covers CWE Top 25. |
| `static_analysis_fixed` | SA findings fixed before release | `Met` | CI gates merges on CodeQL alerts. |
| `static_analysis_often` | SA runs at least weekly | `Met` | CodeQL on every PR + Wednesday schedule (see [`codeql.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/codeql.yml)). |
| `dynamic_analysis` | Dynamic analysis used | `Unmet` | No DAST currently — accept-risk for a client-side Android app with one untrusted input surface (OFF JSON). |
| `dynamic_analysis_unsafe` | Memory-unsafe languages analyzed | `N/A` | 100% Kotlin (memory-safe JVM target). |
| `dynamic_analysis_enable_assertions` | Assertions on during dyn-analysis | `N/A` | Same. |
| `dynamic_analysis_fixed` | Dyn-analysis findings fixed | `N/A` | Same. |

`dynamic_analysis` is the only `Unmet` row at passing level — leaving it Unmet is acceptable because the passing badge allows a small number of unmet criteria with justification.

## Silver badge — adds ~55 criteria on top of passing

| ID | Question (one-line) | Status to set | Justification text to paste |
|---|---|---|---|
| `achieve_passing` | Has earned passing badge | `Met` | (auto-set once passing is achieved) |
| `contribution_requirements` | Contribution requirements (silver level) | `Met` | See [CONTRIBUTING.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CONTRIBUTING.md) §How to contribute + PR template. |
| `dco` | DCO or CLA in place | `Unmet` | Not yet — would require enabling DCO bot + `Signed-off-by` enforcement. Track separately if pursuing silver. |
| `governance` | Project governance documented | `Met` | See [GOVERNANCE.md](https://github.com/DocGerd/pantry-tracker/blob/develop/GOVERNANCE.md). |
| `code_of_conduct` | Code of conduct documented | `Met` | See [CODE_OF_CONDUCT.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CODE_OF_CONDUCT.md). |
| `roles_responsibilities` | Roles documented | `Met` | See [GOVERNANCE.md](https://github.com/DocGerd/pantry-tracker/blob/develop/GOVERNANCE.md) §Roles. |
| `access_continuity` | Access-continuity plan | `Unmet` | Single-maintainer; succession plan TBD — track via separate ticket. |
| `bus_factor` | Bus factor ≥ 2 | `Unmet` | Currently 1 — structural; same accept-risk as Scorecard Contributors check. |
| `documentation_roadmap` | Roadmap documented | `Unmet` | No `ROADMAP.md` yet — track via separate ticket (link to v1.3 milestone planned). |
| `documentation_architecture` | Architecture documented | `Met` | See [docs/architecture/](https://github.com/DocGerd/pantry-tracker/tree/develop/docs/architecture) (arc42). |
| `documentation_security` | Security architecture documented | `Met` | See [docs/security-posture.md](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/security-posture.md) + [docs/security/](https://github.com/DocGerd/pantry-tracker/tree/develop/docs/security). |
| `documentation_quick_start` | Quick-start guide | `Met` | See [README.md §Install](https://github.com/DocGerd/pantry-tracker/blob/develop/README.md). |
| `documentation_current` | Docs kept current | `Met` | CHANGELOG and arc42 reviewed per release. |
| `documentation_achievements` | Achievements documented | `Met` | [CHANGELOG.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CHANGELOG.md). |
| `accessibility_best_practices` | Accessibility considered | `Met` | Compose semantics + content-description audit — see [`docs/uat/v1-uat-checklist.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/uat/v1-uat-checklist.md). |
| `internationalization` | I18n considered | `Unmet` | Currently English-only; no i18n framework adopted yet. |
| `sites_password_security` | Project sites enforce password security | `N/A` | Project has no auth-bearing sites. |
| `maintenance_or_update` | Project is maintained or updated | `Met` | Three releases in 10 days; weekly security scans active. |
| `report_tracker` | Report tracker (silver level) | `Met` | [GitHub Issues](https://github.com/DocGerd/pantry-tracker/issues) with labels and milestones. |
| `vulnerability_report_credit` | Reporter credit policy | `Met` | See [SECURITY.md](https://github.com/DocGerd/pantry-tracker/blob/develop/SECURITY.md) §Disclosure. |
| `vulnerability_response_process` | Vuln response process with timelines | `Met` | See [SECURITY.md](https://github.com/DocGerd/pantry-tracker/blob/develop/SECURITY.md) §Response window. |
| `coding_standards` | Coding standards documented | `Met` | See [CLAUDE.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CLAUDE.md) and [`detekt-config.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/detekt-config.yml). |
| `coding_standards_enforced` | Coding standards enforced | `Met` | Detekt gates CI — see [`.github/workflows/ci.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/ci.yml). |
| `build_standard_variables` | Standard build variables | `Met` | Gradle conventions used — see [`app/build.gradle.kts`](https://github.com/DocGerd/pantry-tracker/blob/develop/app/build.gradle.kts). |
| `build_preserve_debug` | Debug info preserved separately | `Met` | R8 mapping file uploaded with each release — see [`docs/release/SHIPPING.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/release/SHIPPING.md). |
| `build_non_recursive` | Non-recursive build | `Met` | Single Gradle project, no recursive make. |
| `build_repeatable` | Build is repeatable | `Met` | Pinned Gradle distribution + lockfile at release tags — see [`gradle/wrapper/gradle-wrapper.properties`](https://github.com/DocGerd/pantry-tracker/blob/develop/gradle/wrapper/gradle-wrapper.properties) and [`docs/release/SHIPPING.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/release/SHIPPING.md). |
| `installation_common` | Common install method | `Met` | Sideload APK from GitHub Releases — see [`docs/release/SHIPPING.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/release/SHIPPING.md). |
| `installation_standard_variables` | Standard install variables | `N/A` | Android sideload — no install-time variables. |
| `installation_development_quick` | Quick dev install | `Met` | `./gradlew :app:installDebug` — see [CLAUDE.md §Commands](https://github.com/DocGerd/pantry-tracker/blob/develop/CLAUDE.md). |
| `external_dependencies` | External deps listed | `Met` | [`gradle/libs.versions.toml`](https://github.com/DocGerd/pantry-tracker/blob/develop/gradle/libs.versions.toml) + [`app/gradle.lockfile`](https://github.com/DocGerd/pantry-tracker/blob/develop/app/gradle.lockfile) at release tags. |
| `dependency_monitoring` | Dependencies actively monitored | `Met` | Dependabot weekly — see [`.github/dependabot.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/dependabot.yml); OSV-Scanner in CI. |
| `updateable_reused_components` | Reused components updateable | `Met` | All deps via Gradle version catalog — see [`gradle/libs.versions.toml`](https://github.com/DocGerd/pantry-tracker/blob/develop/gradle/libs.versions.toml). |
| `interfaces_current` | Public interfaces current | `N/A` | No public programmatic interfaces (Android app). |
| `automated_integration_testing` | CI runs integration tests | `Met` | Robolectric + emulator-backed `connectedDebugAndroidTest` — see [`.github/workflows/ci.yml`](https://github.com/DocGerd/pantry-tracker/blob/develop/.github/workflows/ci.yml). |
| `regression_tests_added50` | Regression tests for ≥ 50% bug fixes | `Met` | PR template enforces; see closed bugfix PRs (e.g. #117, #53). |
| `test_statement_coverage80` | ≥ 80% statement coverage | `Unmet` | No coverage threshold gate currently — track via separate ticket. |
| `test_policy_mandated` | Test addition mandatory | `Met` | PR template + multi-agent review enforce this. |
| `tests_documented_added` | Test-addition policy documented (silver) | `Met` | See [CONTRIBUTING.md](https://github.com/DocGerd/pantry-tracker/blob/develop/CONTRIBUTING.md). |
| `warnings_strict` | Strictest warnings (silver level) | `Met` | Detekt full ruleset + Lint baseline=0 + CodeQL `security-and-quality`. |
| `implement_secure_design` | Secure-design implemented | `Met` | See [docs/security-posture.md](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/security-posture.md); R8 + signed APKs + scoped storage. |
| `crypto_weaknesses` | No known-weak algorithms (silver) | `Met` | TLS 1.2+ defaults; no bespoke crypto. |
| `crypto_algorithm_agility` | Crypto algorithm agility | `Met` | TLS negotiates via Android platform — supports algorithm transitions. |
| `crypto_credential_agility` | Crypto credential agility | `Met` | APK signing keystore can be rotated (Android signature-scheme v3 supports key rotation). |
| `crypto_used_network` | Network crypto current | `Met` | TLS 1.2+ via Ktor/OkHttp defaults. |
| `crypto_tls12` | TLS 1.2+ used | `Met` | Android minSdk 26 mandates TLS 1.2+; no override in [`OffApiClient`](https://github.com/DocGerd/pantry-tracker/blob/develop/app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt). |
| `crypto_certificate_verification` | Cert verification on | `Met` | Default Ktor/OkHttp behavior; no override in [`OffApiClient`](https://github.com/DocGerd/pantry-tracker/blob/develop/app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt). |
| `crypto_verification_private` | Private keys not in repo | `Met` | Keystore stored outside repo — see [`docs/release/SHIPPING.md`](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/release/SHIPPING.md) §B. |
| `signed_releases` | Releases cryptographically signed | `Unmet` | APKs jarsigner-signed but Scorecard wants cosign/SLSA — track via separate ticket (see related issue on Signed-Releases). |
| `version_tags_signed` | Version tags signed | `Unmet` | Tags not currently GPG-signed — track via separate ticket. |
| `input_validation` | Input validation considered | `Met` | OFF API responses parse-guarded; barcode input validated — see related issue #59 (parse-depth bound). |
| `hardening` | Hardening features used | `Met` | R8 minify + shrinkResources enabled — see #62 / [`app/build.gradle.kts`](https://github.com/DocGerd/pantry-tracker/blob/develop/app/build.gradle.kts). |
| `assurance_case` | Assurance case documented | `Unmet` | No formal assurance case authored; consider adding to [docs/security-posture.md](https://github.com/DocGerd/pantry-tracker/blob/develop/docs/security-posture.md). |
| `static_analysis_common_vulnerabilities` | SA common vulns (silver) | `Met` | CodeQL `security-and-quality` covers CWE Top 25. |
| `dynamic_analysis_unsafe` | Dyn-analysis on unsafe code (silver) | `N/A` | 100% Kotlin (memory-safe). |

`Unmet` rows at silver level: `dco`, `access_continuity`, `bus_factor`, `documentation_roadmap`, `internationalization`, `test_statement_coverage80`, `signed_releases`, `version_tags_signed`, `assurance_case` — file follow-up tickets for the ones you want to close before claiming silver. Three of them (`signed_releases`, `bus_factor` ≈ Contributors, structural-zero accept-risk) overlap with sibling Scorecard tickets.

## Maintenance

- Update this doc whenever a row's status or justification changes on the form.
- When the BadgeApp `criteria.yml` source adds new questions, re-export and
  append them here.
- After every form submission, re-run Scorecard
  (`gh workflow run scorecard.yml --ref develop`) and record the
  CII-Best-Practices score change in this doc's commit message.
