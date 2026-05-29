# 0004. Robolectric Native Graphics for Compose screenshot tests (not Paparazzi)

## Status

Accepted — 2026-05-28

(Backfills the SR-74 / PR #85 screenshot-harness decision, landed
2026-05-26.)

## Context

The v1 UAT checklist (see [`docs/uat/v1-uat-checklist.md`](../uat/v1-uat-checklist.md))
contains a handful of rows that are visually-verifiable but tedious to
re-check on every PR: the adaptive launcher icon variants, the light/dark
theme rendering of a product row, the row's 45% opacity treatment when
quantity is 0 ("greyed row"), font-scale extremes (0.85x and 1.30x), and
the Coil image slot on the detail screen.

Replacing those manual checks with **golden-image screenshot tests** moves
them from "checklist row a human walks" to "CI gate that fails on a
visual regression". Three Compose-screenshot harnesses were viable on the
JVM unit-test layer:

- **[Paparazzi][paparazzi]** — Square's library, renders Compose UI to a
  PNG using the LayoutLib runtime that ships with the Android SDK. No
  emulator dependency; runs in the JVM. The established choice in the
  Android ecosystem for the last several years.
- **[Robolectric Native Graphics (RNG)][rng]** — Robolectric mode
  `@GraphicsMode(GraphicsMode.Mode.NATIVE)` enables a real Skia-pipeline
  software rasterizer inside the Robolectric runtime. Available since
  Robolectric 4.10. Lets the existing JVM-unit-test layer (which already
  uses Robolectric for Android-API mocks) take screenshots of Compose
  content without a second harness.
- **[Google's Compose Preview Screenshot Testing][previewscreenshot]** —
  AGP-bundled, AndroidX `androidx.compose.ui:ui-test-manifest`-based,
  emulator-or-Robolectric-backed. Newer; AGP-version-coupled.

The project's existing test stack already runs Robolectric for ViewModel
and Android-API-touching unit tests (the `:app:testDebugUnitTest` task).
Adding RNG screenshot tests is `@GraphicsMode(NATIVE)` plus a golden-image
diff helper — zero new test runtimes, zero new Gradle plugins.

Paparazzi would have brought a second test runtime (its own LayoutLib
shim), a Square-published Gradle plugin (`app.cash.paparazzi`), and a
parallel test source set with its own conventions for golden management.
For ~10 golden PNGs, the second runtime is more weight than the harness
saves.

[paparazzi]: https://github.com/cashapp/paparazzi
[rng]: https://robolectric.org/blog/2023/04/19/robolectric-native-graphics/
[previewscreenshot]: https://developer.android.com/studio/preview/compose-screenshot-testing

## Decision

Compose screenshot tests use **Robolectric Native Graphics** in the JVM
unit-test source set (`app/src/test/.../screenshot/`), with golden PNGs
committed under `app/src/test/snapshots/` and **byte-for-byte** comparison.

Implementation details:

- Tests are annotated `@Config(sdk = [34])` and
  `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. The `xxhdpi` density qualifier
  is fixed so file sizes remain stable across dev machines.
- A shared
  [`ScreenshotTestBase`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/ScreenshotTestBase.kt)
  helper handles capture and diff. It renders the root decor `View`
  directly to a `Bitmap` via `View.draw` (rather than
  `SemanticsNodeInteraction.captureToImage()` which internally waits on
  `ViewTreeObserver.OnDrawListener` — that listener doesn't fire under
  Robolectric without a real hardware display).
- **Byte-for-byte diff**, no pixel tolerance. RNG renders
  deterministically on the same host (same Robolectric version, same SDK
  level, same device config), so any byte-level difference is real. A
  pixel-level tolerance would silently accept font-hinting regressions
  visible to the human eye.
- **Golden regeneration is explicit.** Deleting the stale PNG and re-running
  the test writes a new golden and fails once; review and commit; the
  next run passes. The procedure is documented in
  [`app/src/test/snapshots/README.md`](../../app/src/test/snapshots/README.md).
- **No `*_actual.png` files committed** — those are written on diff
  mismatch as a side-by-side diagnostic aid and excluded from git.

The initial harness covers 11 goldens spanning icon variants
(`icon_full_canvas`, `icon_circular_mask`, `icon_square_mask`),
theme (`theme_light_mode`, `theme_dark_mode`), font scales
(`font_scale_small`, `font_scale_large`), the greyed-row 45% opacity
treatment (`greyed_row_in_stock`, `greyed_row_out_of_stock`), and the
detail-screen Coil image slot (`coil_image_present`, `coil_image_absent`).
These retire UAT §0 rows 2–4, §2 rows 1, 2, and 4, §11 last row, and v1.2
§11 Coil row.

## Consequences

**Positive.**

- Zero new test runtime — Robolectric was already in the dependency
  tree. RNG is just a `@GraphicsMode(NATIVE)` annotation plus the
  capture helper.
- No emulator dependency for screenshot tests; the harness runs in
  `:app:testDebugUnitTest`, which is the existing CI gate.
- Byte-for-byte diff catches font-hinting and pixel-snapping regressions
  that a pixel-tolerance diff would silently accept.
- Goldens live next to the test code (`app/src/test/snapshots/`), so a
  Compose change that breaks the render shows up as a failing test +
  `*_actual.png` next to the expected golden — the diff workflow is
  immediate.
- Retires ~9 rows of manual UAT, including several that needed device-
  specific OEM launcher photographs (icon variants).

**Negative.**

- RNG is younger than Paparazzi. Render bugs in the underlying Skia
  software rasterizer can produce surprises on a Robolectric / SDK
  upgrade — golden regeneration is mandatory after either bump.
- Byte-for-byte diff is strict: any change to a Material 3 default
  colour, font, or shape ripples through every golden and forces a
  re-bless. Mitigated by the documented regeneration procedure.
- Goldens are committed as binary PNGs (~10–50 KB each). The repo carries
  the bytes; not large in absolute terms but visible in `git diff`.
- The `View.draw` workaround in `ScreenshotTestBase` exists specifically
  because `SemanticsNodeInteraction.captureToImage()` doesn't work under
  Robolectric. A future Robolectric / Compose-test version that fixes
  that could simplify the helper.

## References

- Original PR / spec: SR-74 / PR
  [#85](https://github.com/DocGerd/pantry-tracker/pull/85) —
  "feat(sr-74): add Robolectric Native Graphics screenshot tests".
- CHANGELOG entry: [`CHANGELOG.md`](../../CHANGELOG.md) §"Tests / quality"
  — "RNG screenshot harness added (#74 / SR-74)".
- Implementation:
  - [`app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/ScreenshotTestBase.kt`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/ScreenshotTestBase.kt)
    — capture + diff helper.
  - [`AppIconScreenshotTest`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/AppIconScreenshotTest.kt),
    [`ThemeScreenshotTest`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/ThemeScreenshotTest.kt),
    [`FontScaleScreenshotTest`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/FontScaleScreenshotTest.kt),
    [`GreyedRowScreenshotTest`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/GreyedRowScreenshotTest.kt),
    [`CoilImageScreenshotTest`](../../app/src/test/java/de/docgerdsoft/pantrytracker/screenshot/CoilImageScreenshotTest.kt).
  - [`app/src/test/snapshots/README.md`](../../app/src/test/snapshots/README.md)
    — golden inventory + regeneration procedure.
- UAT rows retired: [`docs/uat/v1-uat-checklist.md`](../uat/v1-uat-checklist.md)
  §0 rows 2-4, §2 rows 1, 2, and 4, §11 last row, v1.2 §11 Coil row.
- Robolectric Native Graphics announcement: <https://robolectric.org/blog/2023/04/19/robolectric-native-graphics/>.
