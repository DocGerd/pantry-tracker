# Jazzer fuzz-test inputs for `OffApiClientFuzzTest`

This directory follows the Jazzer JUnit 5 inputs-directory convention:

```
src/test/resources/<package-as-dirs>/<ClassName>Inputs/<methodName>/
```

For `OffApiClientFuzzTest.decodeOffProductResponse_doesNotCrashOnArbitraryInput`
that resolves to
`src/test/resources/de/docgerdsoft/pantrytracker/data/remote/OffApiClientFuzzTestInputs/decodeOffProductResponse_doesNotCrashOnArbitraryInput/`.

Files in `decodeOffProductResponse_doesNotCrashOnArbitraryInput/` are:

- **Seed corpus** Jazzer mutates from when fuzzing
  (`./gradlew :app:fuzzTest` — env var `JAZZER_FUZZ=1` is set by that task).
- **Regression inputs** Jazzer replays in regression mode (no env var).
  Crashing inputs found during a fuzz run land here automatically; commit
  them to lock the regression in.

See [SR-144](https://github.com/DocGerd/pantry-tracker/issues/144) for the
rationale.
