# 0001. Manual constructor DI via `AppContainer`, no Hilt

## Status

Accepted — 2026-05-28

(Backfills arc42 ADR-001, which has been in effect since milestone M0.)

## Context

Pantry Tracker is a single-module, single-developer Android app. The runtime
dependency graph at v1.0 is small: one `ProductRepository`, one Room
database (with two DAOs), one HTTP client (`OffApiClient`), and ~5 ViewModels
that consume the repository. Tests construct the same graph with fakes
substituted at the constructor boundary.

The Android-ecosystem default for wiring this graph is [Hilt][hilt] — a
Dagger 2-based dependency injection framework with Android-specific entry
points (`@HiltAndroidApp`, `@AndroidEntryPoint`, …). Roughly 90% of modern
Android apps use it. Two alternatives were considered:

- **Hilt** — the conventional choice. Buys auto-wiring and compile-time
  graph-correctness checks at the cost of an annotation-processing layer
  (KSP/KAPT), a custom Gradle plugin, a learning curve, and a generated
  graph that's awkward to step through in a debugger.
- **Koin** — runtime service-locator with a Kotlin DSL. Buys auto-wiring
  without the annotation processor, at the cost of moving correctness
  failures from compile time to runtime.
- **Hand-rolled manual wiring** — a plain Kotlin class that constructs each
  singleton in order; ViewModels built with the standard
  `viewModelFactory { initializer { … } }` pattern.

For five wired components and one Application class, the hand-rolled version
is shorter than the Hilt setup it replaces, never breaks at runtime in a way
the type system didn't catch at compile time, and stays trivially
debuggable. Hilt's auto-wiring pays off when the graph has tens of nodes
across multiple Gradle modules — not at this scale.

[hilt]: https://developer.android.com/training/dependency-injection/hilt-android

## Decision

The app uses **manual constructor injection** via a single
[`AppContainer`](../../app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt)
class. The container is constructed once in `PantryTrackerApp.onCreate()`
and reached from ViewModels by casting the `Application`. ViewModels are
built with the standard `viewModelFactory { initializer { … } }` API.

No Hilt, no Koin, no Dagger, no annotation processor, no generated graph.
Adding a new dependency means adding an explicit `val foo: Foo = …` line to
`AppContainer.real()` — the surface is small enough that this remains
auditable from one screen of code.

Test seams are first-class: `AppContainer` accepts a `productRepository` and
an optional `cameraSource` as constructor parameters, so test code passes a
fake repository / fake camera source directly without going through any
container override mechanism.

## Consequences

**Positive.**

- One screen of code (`AppContainer.kt`) is the entire runtime dependency
  graph. New contributors can read it end-to-end in under a minute.
- No code generation in the wiring path — Gradle build graph is simpler,
  build times faster, and IDE navigation goes straight from a ViewModel
  constructor call to the concrete construction site.
- Test substitution is a constructor parameter, not a Hilt
  `@TestInstallIn` / `@BindValue` / replace-module dance.
- No Hilt-specific gotchas: no `@AndroidEntryPoint` on every Activity, no
  Hilt-rules in Compose previews, no surprises with WorkManager / Service
  injection (the app has none of those anyway, see arc42 ADR-009).

**Negative.**

- No compile-time graph validation. A missing dependency surfaces as a
  Kotlin compile error inside `AppContainer.real()` rather than a Hilt
  graph-validation error, which is fine at this scale but would be
  unworkable across many modules.
- Adding a second consumer (a Wear OS companion app, a backup-export
  module) would require duplicating the wiring or extracting it — the
  break-even point where Hilt starts paying for itself.
- The hand-rolled style is non-idiomatic for the Android ecosystem;
  readers may expect Hilt and need to be told the rationale up front (this
  ADR exists for that reason).

**Reconsider when.** A second consumer of `ProductRepository` lands (Wear
companion app, a backup-export module, multi-module split, etc.). At that
point Hilt's auto-wiring across modules pays for itself.

## References

- Original prose source: [`docs/architecture/09-architecture-decisions.md`](../architecture/09-architecture-decisions.md)
  §"ADR-001 — Manual DI via `AppContainer`, not Hilt".
- Cross-cutting concern: [`docs/architecture/08-crosscutting-concepts.md`](../architecture/08-crosscutting-concepts.md)
  §8.8 "Dependency injection".
- Quality goal driving this choice: [`docs/architecture/01-introduction-and-goals.md`](../architecture/01-introduction-and-goals.md)
  §1.2 "Quality Goals", row 5 — "Maintainable by one person".
- v1 design spec: [`docs/superpowers/specs/2026-05-16-kitchen-inventory-design.md`](../superpowers/specs/2026-05-16-kitchen-inventory-design.md)
  §"Dependency injection" row.
- Implementation: [`app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt`](../../app/src/main/java/de/docgerdsoft/pantrytracker/di/AppContainer.kt).
- Application entry point: [`app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerApp.kt`](../../app/src/main/java/de/docgerdsoft/pantrytracker/PantryTrackerApp.kt).
