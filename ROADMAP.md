# Roadmap

This roadmap states direction, not dates. It is reviewed each minor-version release (same cadence as [CHANGELOG.md](CHANGELOG.md)). Concrete near-term work is tracked as [GitHub milestones](https://github.com/DocGerd/pantry-tracker/milestones).

## Near-term (next 1–2 releases)
- **v1.3** — see the [v1.3 milestone](https://github.com/DocGerd/pantry-tracker/milestones).
- **Open-source maturity** — finishing the [Open Source: In the Open](https://github.com/DocGerd/pantry-tracker/milestones) milestone, incl. the OpenSSF Best Practices Silver badge (#140).

## Mid-term (~6 months, aspirational)
- Additional locales — German first (#168), once the i18n framework lands.
- `OffHost` enum hardening (#61).
- JSON parse-depth bound for Open Food Facts responses (#59).
- Remaining OpenSSF Silver criteria from #140.

## Non-goals (deliberate scope decisions)
These keep the lean-v1 scope honest and prevent drift-by-request:
- Cloud sync / accounts / multi-user.
- Recipe management or meal planning.
- Google Play Store / F-Droid distribution (sideload APK stays the channel).
- Fractional / weight-based quantities (whole-number inventory by design).

## How this document is maintained
Updated on each minor-version release; near-term items graduate to CHANGELOG entries as they ship.
