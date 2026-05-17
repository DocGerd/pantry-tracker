# 1. Introduction and Goals

## 1.1 Requirements Overview

**Pantry Tracker** is a single-user Android app for tracking what's in the
kitchen pantry by scanning product barcodes.

Primary use cases:

1. **Add stock** — point the phone at a barcode, the app looks up the product
   on Open Food Facts (OFF), the user confirms quantity and the row is added
   to a local inventory.
2. **Remove stock** — same scan flow, but decrements an existing row instead
   of adding. Removing the last unit leaves the row at quantity 0 (greyed in
   the list); a long-press deletes the row entirely.
3. **Browse / search** — a Home screen lists everything in the pantry,
   alphabetical and searchable.
4. **Edit a single item** — tap a row to open the detail screen, where the
   user can rename, adjust quantity with a stepper, or delete.

Non-goals for v1: multi-user sharing, shopping lists, recipe planning,
expiry-date tracking, barcode formats other than EAN/UPC, accounts of any kind.

## 1.2 Quality Goals

In priority order:

| # | Quality | What this means in practice |
|---|---------|-----------------------------|
| 1 | **Privacy** | Nothing leaves the device except OFF barcode lookups. No analytics, no crash reporter, no accounts. The OFF request is anonymous and carries only the barcode. |
| 2 | **Offline-first** | The app is fully usable without network — only the *enrichment* step (resolving a barcode to a product name on first scan) needs OFF. Local inventory is the source of truth. |
| 3 | **Fast cold-start to scan** | From launcher tap to a live camera preview should feel instant. The scan loop is the most-frequent action. |
| 4 | **No surprise data loss** | A schema mismatch crashes the app rather than silently wiping the user's pantry (no `fallbackToDestructiveMigration`). Every repository operation that fails surfaces a user-visible error. |
| 5 | **Maintainable by one person** | Conventional Compose + Room + manual DI; no Hilt, no MVI framework, no code generation beyond Room. A new contributor (or the same person six months later) can read one screen end-to-end without chasing magic. |

## 1.3 Stakeholders

| Role | Concern |
|------|---------|
| **End user** (single household, "me") | Add / remove products in seconds; never lose pantry data |
| **Maintainer** (also "me") | Ship updates without breaking the inventory; debug issues from a stack trace alone |
| **Open Food Facts** | Be a good API citizen: identify with a User-Agent, no retry storms, no bulk crawling |

There are no external stakeholders (no commercial customers, no compliance
auditors, no Play Store reviewers — the app is sideloaded for v1.0).

## 1.4 Project context

The app was built milestone-by-milestone with a written design spec and
implementation plan committed before each milestone landed. The historical
record lives under [`docs/superpowers/`](../superpowers/).
