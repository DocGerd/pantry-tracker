# Jazzer fuzz-test inputs for `OffApiClientFuzzTest`

This README lives as a **sibling** of `OffApiClientFuzzTestInputs/`,
deliberately outside the Jazzer inputs walk path. Jazzer's JUnit 5 driver
walks every file directly under the class-level `<ClassName>Inputs/`
directory and feeds it to **every** `@FuzzTest` method as a class-shared
seed
([source](https://github.com/CodeIntelligenceTesting/jazzer/blob/main/docs/dev-junit-implementation-details.md#resources-tests)),
so this file must NOT live inside `OffApiClientFuzzTestInputs/`. Putting it
one level up keeps the documentation discoverable next to the inputs
directory it documents, without polluting the seed corpus.

## Inputs directory convention

The actual seed corpus + regression inputs are under:

```
src/test/resources/<package-as-dirs>/<ClassName>Inputs/<methodName>/
```

For `OffApiClientFuzzTest.decodeOffProductResponse_doesNotCrashOnArbitraryInput`
that resolves to
`app/src/test/resources/de/docgerdsoft/pantrytracker/data/remote/OffApiClientFuzzTestInputs/decodeOffProductResponse_doesNotCrashOnArbitraryInput/`.

Files in that directory are:

- **Seed corpus** Jazzer mutates from when fuzzing
  (`./gradlew :app:fuzzTest` — env var `JAZZER_FUZZ=1` is set by that task).
- **Regression inputs** Jazzer replays in regression mode (no env var).
  Crashing inputs found during a fuzz run land here automatically; commit
  them to lock the regression in.

## Adding a new `@FuzzTest` method on this class

If you add another `@FuzzTest`-annotated method, create a sibling subdirectory
under `OffApiClientFuzzTestInputs/<newMethodName>/` for its inputs. Do **not**
put files directly under `OffApiClientFuzzTestInputs/` unless you genuinely
want those bytes treated as a seed for every fuzz method on the class.

See [SR-144](https://github.com/DocGerd/pantry-tracker/issues/144) for the
rationale.
