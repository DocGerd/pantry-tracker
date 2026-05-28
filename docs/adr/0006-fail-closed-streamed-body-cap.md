# 0006. Fail-closed 256 KB streamed body cap on OFF responses

## Status

Accepted — 2026-05-28

(Backfills the SR-14 / PR #58 decision landed 2026-05-27, which itself
closed a security gap left by the v1.1.0 header-keyed hotfix.)

## Context

The app's only outbound HTTP surface is the Open Food Facts product-lookup
endpoint (see [ADR-0003](0003-ktor-off-multi-host-fallback.md)). The OFF
response is a single-product JSON envelope observed at **5–20 KB** in
practice. The threat model is "be a good API citizen and don't let a
hostile or accidentally-huge response exhaust app memory" — defence-in-depth,
not active attack mitigation.

The implementation went through two iterations:

**v1.1.0 header-keyed cap.** A first version installed a
`HttpResponseValidator` that read `Content-Length` and rejected
responses advertising a length greater than 256 KB. The cap was endorsed
by every unit test (Ktor's `MockEngine` faithfully serves the
`Content-Length` header that tests configure).

The cap was effectively dead in production: **OFF's CDN ships product
responses with HTTP/1.1 chunked transfer encoding and omits
`Content-Length`** (see arc42 §11 — SR-24 follow-up). With no
`Content-Length` to inspect, the validator did nothing on the actual
production path. Worse: an earlier iteration that *rejected* responses
without a `Content-Length` header (fail-closed on absence) shipped briefly
and bricked every scan on a real device, because every legitimate OFF
response lacks the header. The lesson — "Real-device UAT is non-negotiable
for HTTP-client changes" — is in the project memory.

PR #58 / SR-14 closed the gap with a **streamed counting read**.

## Decision

`OffApiClient.readBoundedBody()` performs a counting read of the response
body via `bodyAsChannel()`, accumulating bytes in a `ByteArrayOutputStream`
and throwing `OversizedResponseException` as soon as the running total
exceeds `MAX_BODY_BYTES = 256 * 1024` (256 KB):

```kotlin
private suspend fun readBoundedBody(response: HttpResponse): ByteArray {
    val channel = response.bodyAsChannel()
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(BODY_READ_CHUNK_BYTES)   // 8 KiB
    var total = 0L
    while (!channel.isClosedForRead) {
        val n = channel.readAvailable(chunk, 0, chunk.size)
        if (n <= 0) break
        total += n
        if (total > MAX_BODY_BYTES) throw OversizedResponseException(total)
        buffer.write(chunk, 0, n)
    }
    return buffer.toByteArray()
}
```

Decision details:

1. **Cap operates on actual bytes received**, not on a header that may
   never appear. Holds regardless of transfer encoding.
2. **Cap value: 256 KB** — a 10x safety factor over the 5–20 KB observed
   single-product JSON.
3. **Strict-greater-than** — `total > MAX_BODY_BYTES`. A response of
   exactly 256 KB is allowed (the boundary test
   `lookup_atExactlyCapBytes_succeeds` pins this).
4. **`OversizedResponseException extends IOException`** so it composes
   with the existing IO-failure flow inside `lookupOnce`, but has a
   **dedicated catch arm above the generic IOException arm** so the cap
   breach gets a distinct WARNING log line — forensically separating
   "hostile oversized response" from "user wifi dropped" without
   grepping `e.toString()` for the subclass name.
5. **Fail closed** — a breach maps to `HostResult.Error`, which
   short-circuits the OFF host fallback chain (it does NOT walk to
   Beauty Facts on an oversized Food response — same fail-fast policy as
   every other non-404 error; see [ADR-0003](0003-ktor-off-multi-host-fallback.md)).
6. **Bounded peak memory.** The accumulator buffer is bounded at roughly
   `MAX_BODY_BYTES + BODY_READ_CHUNK_BYTES` (256 KB + 8 KiB), because the
   check fires only *after* one chunk's worth of bytes is appended. The
   over-read is at most one 8 KiB chunk, not the whole response.

The constant `MAX_BODY_BYTES` is `internal` so
[`OffApiClientTest`](../../app/src/test/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClientTest.kt)
can reference the cap value rather than duplicate the literal — keeps the
production constant and the test boundary in lockstep.

Three boundary test cases pin the contract: at-exact-cap (passes),
at-cap+1 (rejected), chunked-sub-cap (passes through). A
`lookup_chunkedOversizedBody_failsFastAfterCapBytes` test discriminates
broken-vs-fixed code: it uses valid `OffApiEnvelope` JSON padded with
trailing whitespace, so it fails on the v1.1.0 header-only path and
passes on the streamed-cap path.

## Consequences

**Positive.**

- Bounded memory on the OFF lookup path: peak allocation is ~256 KB +
  one 8 KiB chunk, independent of the actual response size.
- Resilient to chunked transfer encoding — OFF's actual production
  shape. Closes the v1.1.0 silent-failure window.
- Distinct WARNING log line for cap breaches lets operators triage
  "hostile response" separately from "wifi dropped" in logcat without
  string-matching on exception types.
- Symmetric with the rest of the OFF error policy: any non-404 failure
  fails fast and falls through to manual entry (same UX as a 5xx or
  timeout).
- `internal class OversizedResponseException` lets tests `assertThrows`
  on the exact subclass — a regression that re-threw a plain
  `IOException` would silently pass against the supertype.

**Negative.**

- The cap fires only *after* the over-cap chunk has been buffered —
  peak memory is ~256 KB + 8 KiB, not exactly 256 KB. Acceptable
  given the threat model (defence in depth, not active attack
  mitigation); a streaming JSON parser could in principle reject earlier
  but at the cost of giving up `kotlinx.serialization`'s ergonomics.
- 256 KB is a hard ceiling. If OFF ever ships a single-product response
  larger than that (e.g. embeds a high-resolution image as base64), the
  cap would silently degrade those scans to manual-entry. The factor-of-10
  safety margin and the observed 5–20 KB range make this very unlikely
  but the cap is a known clamp.
- **JSON-structure depth is not bounded.** The cap is byte-counted; a
  256 KB payload of nested-JSON-depth abuse (`{"a":{"a":{"a":…}}}`)
  would still parse and could in principle blow the parser stack. v1.3
  follow-up tracked as
  [#59](https://github.com/DocGerd/pantry-tracker/issues/59).
- **Real-device UAT is non-negotiable.** The v1.1.0 regression that
  motivated this change was endorsed by every unit test and bricked
  every scan on a real device. The same lesson applies to any future
  change touching request/response headers or body validation — JVM
  tests with `MockEngine` cannot reproduce CDN-specific behaviour like
  chunked encoding. See `CLAUDE.md` § "Things that have bitten past
  sessions" and the test plan in PR #58.

## References

- Original PR / spec: SR-14 / PR
  [#58](https://github.com/DocGerd/pantry-tracker/pull/58) —
  "fix(#52): stream-bound the OFF response body cap". Issue
  [#52](https://github.com/DocGerd/pantry-tracker/issues/52) — the security
  gap report.
- arc42 §8.9 row "Response body cap":
  [`docs/architecture/08-crosscutting-concepts.md`](../architecture/08-crosscutting-concepts.md)
  — the canonical prose source for the policy.
- arc42 §11 (Risks and technical debt) — SR-24 follow-up reference to
  chunked-encoding behaviour:
  [`docs/architecture/11-risks-and-technical-debt.md`](../architecture/11-risks-and-technical-debt.md).
- CHANGELOG entry: [`CHANGELOG.md`](../../CHANGELOG.md) §"Security" / §"Changed"
  — the v1.2.0 body-cap rewire note.
- Implementation:
  - [`app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt)
    — `readBoundedBody()`, `OversizedResponseException`, `MAX_BODY_BYTES`,
    `BODY_READ_CHUNK_BYTES`.
  - Tests: `OffApiClientTest` — three boundary tests pin the contract;
    the new `lookup_chunkedOversizedBody_failsFastAfterCapBytes` test
    discriminates broken-vs-fixed code.
- Related ADR: [`0003-ktor-off-multi-host-fallback.md`](0003-ktor-off-multi-host-fallback.md)
  — defines the fail-closed policy that `OversizedResponseException`
  short-circuits into.
- v1.3 follow-up: nested-JSON-depth bounding — issue
  [#59](https://github.com/DocGerd/pantry-tracker/issues/59).
- Lesson: `CLAUDE.md` § "Things that have bitten past sessions" — "Real-
  device UAT is non-negotiable for HTTP-client changes."
