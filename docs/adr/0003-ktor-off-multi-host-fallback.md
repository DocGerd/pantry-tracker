# 0003. Ktor + Open Food Facts multi-host fallback chain

## Status

Accepted — 2026-05-28

(Backfills the HTTP-client and OFF-host-chain decision. The Ktor + OFF
single-host choice has been in effect since milestone M2; the four-host
fallback chain was added in v1.1.0, 2026-05-19.)

## Context

The app's only outbound network dependency is **Open Food Facts (OFF)**
— specifically the JSON product-lookup endpoint
`/api/v2/product/<barcode>.json`. OFF is consulted only when scanning a
barcode the app has not seen before, to enrich the row with a product name,
brand, and image URL (see arc42 ADR-004 — "Local-first inventory,
network-optional enrichment").

Two orthogonal decisions had to be made:

1. **HTTP client choice.** The Android-ecosystem options at v1 were [Ktor
   client][ktor] (the JetBrains/Kotlin-multiplatform-aligned choice), Retrofit
   + OkHttp (the historically-dominant choice), or raw OkHttp / `HttpURLConnection`.
2. **Host coverage.** OFF is the food-products database — but a kitchen
   pantry contains cosmetics, pet food, cleaning products, etc., all of
   which have barcodes that OFF will reject as 404. The OFF project family
   covers these via three sister databases: Open Beauty Facts, Open Pet Food
   Facts, and Open Products Facts. All four serve the same
   `/api/v2/product/<barcode>.json` schema.

At v1.0 the app hit only `world.openfoodfacts.org`. Commit `95c7b9f`
explicitly recorded "non-food OFF-miss as v1.1+ out-of-scope item". The v1.1
[fallbacks-and-undo design spec][v11spec] then designed the four-host chain
that ships today.

The non-obvious choice in the chain design was **what to do on a non-404
failure**. Two options:

- **Walk on every failure** ("if Food returned 500, maybe Beauty is healthy
  — try it"). Bounds worst-case latency at `4 × timeout ≈ 32 s` of
  back-to-back timeouts when an upstream is sick.
- **Walk only on 404; fail fast on any other failure** ("a 500 from Food
  means *Food* is sick — it does NOT imply the barcode might exist on
  Beauty"). Caps worst-case latency at "one slow OFF request, then three
  fast 404s".

The 256 KB response-body cap that defends against an oversized response is
covered separately in [ADR-0006](0006-fail-closed-streamed-body-cap.md).

[ktor]: https://ktor.io/docs/client.html
[v11spec]: ../superpowers/specs/2026-05-18-v1.1-fallbacks-and-undo-design.md

## Decision

1. **HTTP client: Ktor with the OkHttp engine.** Ktor's
   `HttpClient(OkHttp)` configuration sets an 8 s request/connect/socket
   timeout, a `User-Agent` derived from `BuildConfig.VERSION_NAME` so the
   header tracks future version bumps automatically, and content-negotiation
   wired to `kotlinx.serialization`. JSON decoding routes through a single
   hoisted `OFF_JSON: Json = Json { ignoreUnknownKeys = true }` inside
   `classifyResponse` so the OFF envelope tolerates upstream schema
   additions.

2. **OFF fallback chain: four hosts, in order.** The
   [`OFF_HOSTS`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt)
   constant is:

   ```
   https://world.openfoodfacts.org/
   https://world.openbeautyfacts.org/
   https://world.openpetfoodfacts.org/
   https://world.openproductsfacts.org/
   ```

   The happy path is a **single** request to the first host. On any miss the
   chain advances to the next host.

3. **Fail fast on non-404 errors.** The chain walks **only on HTTP 404**
   (and on the OFF status-sentinel miss `envelope.status != 1`, which OFF
   uses as an in-band 404). Every other host-level failure — non-success
   HTTP, IOException, JSON parse error, OFF contract violation (`status=1`
   with a null product), oversized response, engine-runtime
   `IllegalArgumentException` — short-circuits the chain and returns
   `null` to the caller (manual-entry fallback).

4. **`lookup()` returns null on every miss / failure; throws nothing except
   `CancellationException`.** OFF is an optional enrichment, not an oracle.
   Returning `null` lets the caller drop into the manual-entry sheet
   uniformly, regardless of whether the cause was 404, network down, or a
   parse error. The single exception that propagates is
   `CancellationException` (rethrown explicitly so structured concurrency
   continues to work — see "Things that have bitten past sessions" in
   `CLAUDE.md` on `runCatching` swallowing CE).

## Consequences

**Positive.**

- Ktor's HttpClient is testable end-to-end without a real network — tests
  inject a `MockEngine`-backed `HttpClient` via the primary constructor
  (`OffApiClient internal constructor(private val httpClient: HttpClient)`),
  while production uses the no-arg secondary constructor that builds a real
  OkHttp-backed client.
- Four-host fallback covers the bulk of non-food kitchen items (shampoo,
  cleaning products, pet food) which would otherwise force manual-entry on
  every scan.
- 404-only walk caps observable worst-case latency. A single sick host
  cannot drag the user through ~32 s of timeouts before the manual-entry
  sheet appears.
- `lookup()` returning `null` for every non-cancellation failure mode means
  the caller — `ProductRepositoryImpl` — has exactly one error path to
  handle: "OFF didn't give me a product, fall through to manual entry".
- User-Agent derived from `BuildConfig.VERSION_NAME` keeps OFF's server-side
  analytics in sync with what the app actually shipped — no risk of
  identifying as the wrong version after a release.

**Negative.**

- The `OFF_HOSTS` order is hard-coded. A barcode that lives only on the
  fourth host (Open Products Facts) costs four HTTP round-trips on first
  lookup. Mitigated in v1.2 by the local OFF response cache (`off_lookup_cache`),
  which short-circuits the chain on re-scans for 30 days per barcode.
- "Fail fast on non-404" means a transient 5xx on the first host turns the
  scan into a manual-entry fallback even if Beauty Facts would have served
  the product. Acceptable: the user always has the manual entry escape, and
  the alternative (walking on 5xx) trades a rare-but-correct enrichment for
  a routine bad-UX latency spike.
- Ktor adds a dependency family (`ktor-client-core`,
  `ktor-client-okhttp`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json`) on top of the kotlinx.serialization
  runtime. Acceptable: this is the only HTTP surface in the app and the
  test-injection ergonomics with `MockEngine` are excellent.
- The fallback chain is silent on the user side — the app shows the
  resolved product regardless of which host answered. The
  `OffLookupResult` carries a `resolvingHost` field so the cache and any
  future telemetry can reason about it, but the user doesn't see it.

**Real-device UAT non-negotiable.** Per the "Things that have bitten past
sessions" lesson in `CLAUDE.md`: anything that touches request/response
headers, body validation, or response classification needs a real-device
smoke test before merging. JVM tests with `MockEngine` cannot reproduce
CDN-specific behaviour like chunked transfer encoding (OFF chunks
everything and omits `Content-Length`). The v1.1.0 hotfix that header-keyed
the body cap was endorsed by every unit test and bricked every scan on a
real device — the lesson stands.

## References

- v1.1 design spec — the canonical source: [`docs/superpowers/specs/2026-05-18-v1.1-fallbacks-and-undo-design.md`](../superpowers/specs/2026-05-18-v1.1-fallbacks-and-undo-design.md)
  §"Item 1 — Non-food OFF-miss fallback".
- arc42 ADR-004 — "Local-first inventory, network-optional enrichment":
  [`docs/architecture/09-architecture-decisions.md`](../architecture/09-architecture-decisions.md).
- arc42 §3.1 "Business context" + §8.9 "Security" — the third-party
  network-endpoint table in [`docs/architecture/03-system-scope-and-context.md`](../architecture/03-system-scope-and-context.md)
  and [`docs/architecture/08-crosscutting-concepts.md`](../architecture/08-crosscutting-concepts.md).
- Implementation: [`app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/remote/OffApiClient.kt)
  — `OFF_HOSTS`, `lookup()`, `lookupOnce()`, `classifyResponse()`.
- Cache (v1.2): [`OffLookupCacheDao`](../../app/src/main/java/de/docgerdsoft/pantrytracker/data/local/) +
  `MIGRATION_1_2` — see [ADR-0002](0002-room-local-persistence.md).
- Body cap: see [ADR-0006](0006-fail-closed-streamed-body-cap.md).
- Real-device UAT lesson: `CLAUDE.md` § "Things that have bitten past
  sessions" → "Real-device UAT is non-negotiable for HTTP-client changes."
