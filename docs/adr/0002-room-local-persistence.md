# 0002. Room as the only local-persistence layer

## Status

Accepted — 2026-05-28

(Backfills arc42 ADR-002, in effect since milestone M0; reaffirmed in v1.2
when the second table `off_lookup_cache` and `MIGRATION_1_2` landed.)

## Context

The app needs a structured local store for the pantry. Quality goal #2 is
"offline-first" and quality goal #4 is "no surprise data loss" — the local
DB is the source of truth for the user's inventory; the network (Open Food
Facts) is enrichment only (see [ADR-0003](0003-ktor-off-multi-host-fallback.md)).

Required capabilities:

- One small table for v1 (`products`), with the v1.2 milestone adding a
  second table for the OFF lookup cache (`off_lookup_cache`).
- Substring search over product names — implies SQL `LIKE` queries, which
  rules out key-value-only stores.
- A clean unit-test story: tests must be runnable on a plain JVM (no
  emulator dependency for the unit-test loop).
- Stable schema-migration story — quality goal #4 forbids the
  `fallbackToDestructiveMigration` escape hatch on schema mismatch.

Alternatives considered:

- **[Room][room] (Jetpack)** — the Android-conventional ORM-ish layer over
  SQLite, with annotation-driven DAOs, kotlinx-coroutines `suspend` and
  `Flow` support, and a `MigrationTestHelper` that can drive migrations off
  exported JSON schemas.
- **[SQLDelight][sqldelight]** — type-safe SQL generation from `.sq` files.
  Strong choice; rejected mostly on Android-ecosystem inertia (more
  contributors will recognise Room).
- **Raw SQLite** (`SupportSQLiteDatabase` directly) — minimal dependency
  surface but loses query-time type-safety, kotlinx-coroutines integration,
  and the migration-testing harness.
- **DataStore + JSON** — a single JSON blob in `Preferences`/`DataStore<T>`.
  Rejected because substring-search over the blob is O(n) on every keystroke
  and DataStore is the wrong primitive for relational queries.
- **Realm** — extra dependency, NoSQL semantics that don't match the data
  shape, abandoned in favour of `RealmKotlin` which adds yet more weight.

[room]: https://developer.android.com/training/data-storage/room
[sqldelight]: https://cashapp.github.io/sqldelight/

## Decision

The app uses **Room** with **KSP**-generated DAOs and **exported schemas**.

- One Gradle dependency family (`androidx.room.{runtime,ktx,compiler}`), DAO
  code-generation runs through KSP (not KAPT) to keep build times low.
- One database file (`pantry-tracker.db`) defined by
  [`AppDatabase`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/local/AppDatabase.kt),
  exposing two DAOs: `ProductDao` (the inventory) and `OffLookupCacheDao`
  (the v1.2 OFF response cache).
- **Exported schemas.** `exportSchema = true` writes
  `app/schemas/<db>/<version>.json` on every build; these JSON files are
  committed to the repo and shipped into the androidTest APK
  (`sourceSets.androidTest.assets.srcDirs("$projectDir/schemas")`) so
  `MigrationTestHelper` can validate migrations against the actual prior
  schema.
- **No `fallbackToDestructiveMigration`.** Per the v1 spec §7, a schema
  mismatch is a programmer error and must crash the app — never silently
  wipe the user's pantry. Every schema bump requires an explicit
  `Migration` registered via `.addMigrations(...)` at the
  `Room.databaseBuilder` call site
  ([`AppContainer.real()`](../../app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt)).
- **The Migration class is the contract.** v1.2 adds `off_lookup_cache`,
  declared in
  [`AppDatabaseMigrations.kt`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/local/AppDatabaseMigrations.kt)
  as `MIGRATION_1_2`. The on-device drive-through of this migration is
  automated by [`scripts/uat/verify-migration-1-2.sh`](../../scripts/uat/verify-migration-1-2.sh)
  (SR-81), which installs v1.1.0, seeds rows, installs v1.2 on top, and
  asserts the rows survive plus the new table exists.

## Consequences

**Positive.**

- Conventional. A reader who has worked on any modern Android app knows
  Room and can read the DAOs end-to-end.
- KSP keeps build times low — no `kapt` stub-generation step.
- `MigrationTestHelper` + exported JSON schemas turns migration testing
  from "run it on a device and pray" into a unit test that fails loud on
  any schema-vs-migration drift.
- Same Room artifacts work on a plain JVM (via Robolectric in-memory) for
  the bulk of the test suite, satisfying the "no emulator for unit tests"
  constraint.
- Type-safe DAO surface — column-mismatch bugs surface at compile time.

**Negative.**

- KSP is still maturing relative to KAPT; a Room/KSP version mismatch has
  occasionally produced cryptic errors in past sessions (mitigated by
  pinning both in `libs.versions.toml`).
- Forbidding `fallbackToDestructiveMigration` means a schema bump shipped
  without a `Migration` crashes the app on first launch on existing
  installs — but that is by design (better than silently wiping the
  pantry); the static-inspection and on-device drive-through scripts catch
  the omission before release.
- Two-DAO model + two-table cache adds a small amount of coordination
  cost: cache writes happen lazily inside `ProductRepositoryImpl`, and
  the test fakes need to cover both DAOs. Acceptable for the privacy +
  perf wins (cache reduces server-side leakage of which products a user
  scans).
- Room's compile-time `@Query` SQL validation only fires when the DAO is
  built — a hand-edited `Migration` can drift from the entity definitions
  without immediate compile-time signal; `MigrationTestHelper` catches it
  in unit tests.

## References

- Original prose source: [`docs/architecture/09-architecture-decisions.md`](../architecture/09-architecture-decisions.md)
  §"ADR-002 — Room as the only persistence layer".
- Quality goals driving this: [`docs/architecture/01-introduction-and-goals.md`](../architecture/01-introduction-and-goals.md)
  §1.2 row 2 (offline-first) and row 4 (no surprise data loss).
- Cross-cutting concerns: [`docs/architecture/08-crosscutting-concepts.md`](../architecture/08-crosscutting-concepts.md)
  §8.7 (testing layers) and §8.9 (data-at-rest).
- v1 design spec: [`docs/superpowers/specs/2026-05-16-kitchen-inventory-design.md`](../superpowers/specs/2026-05-16-kitchen-inventory-design.md)
  §"Local DB" row + §"Programmer error" handling.
- Implementation:
  - [`app/src/main/java/de/docgerdsoft/pantrytracker/data/local/`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/local/)
    — entities, DAOs, `AppDatabase`, migrations.
  - [`app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt`](../../app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt)
    — the `Room.databaseBuilder` call site where `MIGRATION_1_2` is wired.
  - [`app/schemas/`](../../app/schemas/) — exported schema JSON.
- Migration verification: SR-81 / PR
  [#87](https://github.com/DocGerd/pantry-tracker/pull/87) — emulator-drive
  runbook + script at [`scripts/uat/verify-migration-1-2.sh`](../../scripts/uat/verify-migration-1-2.sh).
- Release procedure: [`docs/release/SHIPPING.md`](../release/SHIPPING.md) —
  see the migration-runbook entry in the v1.2 release checklist.
