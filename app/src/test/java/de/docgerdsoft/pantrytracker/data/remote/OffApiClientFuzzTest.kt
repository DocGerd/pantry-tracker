package de.docgerdsoft.pantrytracker.data.remote

import com.code_intelligence.jazzer.junit.FuzzTest
import de.docgerdsoft.pantrytracker.data.remote.OffApiClient.Companion.OFF_JSON
import kotlinx.serialization.SerializationException

/**
 * SR-144: Jazzer fuzz harness for [OffApiClient]'s JSON decode path.
 *
 * Background. The Scorecard `Fuzzing` check is structurally 0/10 for this repo
 * because no fuzzer integrations exist. The only network-derived input the app
 * decodes is the Open Food Facts product response, parsed via
 * `kotlinx.serialization` into [OffApiEnvelope] inside [OffApiClient]. This
 * harness hands arbitrary bytes to that same decoder and asserts the decoder
 * never crashes with an exception type outside the tolerated set defined below.
 *
 * Framing per issue #144: this is **regression-catch quality, not OSS-Fuzz-grade
 * fuzzing**. The fuzz job is non-gating and time-capped at 5 minutes. We're not
 * trying to discover novel CVEs in kotlinx.serialization itself (that's
 * upstream's job) — we're trying to catch the case where a future refactor of
 * the decode pipeline accidentally widens the surface (e.g. introduces a
 * NullPointerException on a malformed input that the production code's
 * defence-in-depth `catch` block in [OffApiClient.lookupOnce] currently does
 * NOT cover — see the catch-arm contract there).
 *
 * Tolerated exception types — anything else is a finding.
 *   - [SerializationException]: the documented kotlinx.serialization error
 *     hierarchy for malformed JSON, missing fields on a `@Serializable`-with-
 *     no-default property, type mismatches, etc. The production [OffApiClient]
 *     catches this; the fuzz harness mirrors that tolerance.
 *   - [IllegalArgumentException]: kotlinx.serialization throws IAE on a small
 *     set of failure modes (notably trying to coerce a JSON literal into an
 *     unrepresentable enum value). [OffApiClient.lookupOnce] also catches
 *     this — though at SEVERE — so the fuzz harness accepts it too.
 *
 * Any other exception escaping `OFF_JSON.decodeFromString<OffApiEnvelope>(...)`
 * — `NullPointerException`, `StackOverflowError`, `OutOfMemoryError`,
 * `IllegalStateException`, anything via `Throwable` — propagates out of the
 * fuzz method and Jazzer treats it as a finding. The crashing input is saved
 * under `src/test/resources/de/docgerdsoft/pantrytracker/data/remote/`
 * `OffApiClientFuzzTestInputs/decodeOffProductResponse_doesNotCrashOnArbitraryInput/`
 * so the next regression-mode run (without `JAZZER_FUZZ=1`) replays it.
 *
 * On running this locally:
 *   - `./gradlew :app:fuzzTest` actually fuzzes for up to 5 minutes (the
 *     gradle task sets `JAZZER_FUZZ=1`).
 *   - `./gradlew :app:test` does NOT include this class — the regular unit-
 *     test task is filtered to `*Test` exclusive of `*FuzzTest` by virtue
 *     of the `fuzzTest` task being the only one configured for JUnit
 *     Platform with `includeTestsMatching("*FuzzTest")`. The default JUnit
 *     4 engine ignores `@FuzzTest`-annotated methods because the class has
 *     no JUnit 4 `@Test` annotations.
 *
 * Seed corpus: four minimal seeds under the inputs directory above —
 * minimal-valid-product, status=0-miss-envelope, empty-object, and
 * truncated-JSON. The status=0 seed is broken out as its own file because
 * the valid-shape-miss case is something Jazzer would need many millions of
 * iterations to discover on its own starting from the status=1 seed. Real
 * OFF responses captured during UAT are *not* checked in because they
 * contain large image URLs that bloat the repo; the schema is well-known
 * and the minimal seeds are sufficient for Jazzer to mutate productively.
 */
class OffApiClientFuzzTest {

    /**
     * @param input arbitrary bytes from Jazzer's mutator. We hand them straight
     *   to the kotlinx.serialization decoder via UTF-8 — `String(bytes,
     *   Charsets.UTF_8)` is lenient with malformed UTF-8 (replaces with U+FFFD),
     *   so the harness exercises the JSON decoder, not the UTF-8 decoder.
     */
    @FuzzTest(maxDuration = "5m")
    fun decodeOffProductResponse_doesNotCrashOnArbitraryInput(input: ByteArray) {
        // String(..., Charsets.UTF_8) never throws on malformed UTF-8 — it
        // replaces invalid sequences with U+FFFD. This keeps the fuzz target
        // narrowed to the JSON decoder; we are NOT fuzzing UTF-8.
        val json = String(input, Charsets.UTF_8)

        try {
            // Target: the exact entry point OffApiClient.classifyResponse uses.
            // OFF_JSON is `internal val` on OffApiClient.Companion, configured
            // with ignoreUnknownKeys = true — identical to production.
            OFF_JSON.decodeFromString<OffApiEnvelope>(json)
        } catch (_: SerializationException) {
            // Tolerated — documented kotlinx.serialization error path.
        } catch (_: IllegalArgumentException) {
            // Tolerated — kotlinx.serialization throws IAE on a small set of
            // edge cases (e.g. enum-coercion failures). Production catches it
            // at SEVERE in OffApiClient.lookupOnce; the harness mirrors that
            // tolerance. Any OTHER exception type propagates and becomes a
            // finding — that is the point.
        }
    }
}
