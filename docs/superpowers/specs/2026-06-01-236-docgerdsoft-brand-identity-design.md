# Design — DocGerdSoft brand identity (issue #236)

**Date:** 2026-06-01
**Issue:** [#236](https://github.com/DocGerd/pantry-tracker/issues/236) — *Apply
DocGerdSoft brand identity (Material 3 scheme + refined adaptive icon)*. Supersedes
#237; the first integration attempt (PR #238) was **reverted** because the
screenshot-golden blast radius was under-scoped.
**Branch:** `brand/docgerdsoft-identity` (fresh off `develop`; the orphan commits
from the reverted PR #238 — which used an abandoned `@Ignore` approach — are
discarded).

---

## 1. Goal

Apply the DocGerdSoft product-level brand identity to Pantry Tracker:

1. A refined **adaptive launcher icon** (foreground geometry + Android-13
   monochrome layer).
2. A **full Material 3 light/dark colour scheme** seeded from Fern `#4F7942`
   (every role mapped to hex), replacing the current `primary`-only override.
3. A **README hero banner** (`docs/brand/hero.svg`, vector stopgap).
4. **Store-style screenshots** (Home / Scan / Detail / dark Home) filling the
   README "Screenshots" gap.

App typography (`PantryTypography`) is **unchanged**. Legal: `© 2026 Patrick
Kuhn`, no company/®/™, no EnBW/THW colours.

### Source of truth

The brand design is **locked** by two artifacts; this spec does not re-decide it,
only records execution:

- **Issue #236** — the canonical implementation brief (icon geometry, M3 role
  table, working diff, Findings).
- **`design_handoff_pantry_brand`** — the Claude Design "Hand off to Claude Code"
  export (brand-spec HTML with the `#ptFg` icon symbol + `.readme-hero` block,
  `pantry.css` with the M3 role table + hero gradient). It is a *design
  reference*, not code to copy. Provided by the user; the load-bearing
  coordinates/values are reproduced verbatim in §3–§5 below so this spec is
  self-contained.

---

## 2. Execution decisions (the open forks, resolved)

| Fork | Decision | Why |
|---|---|---|
| **Golden-regen CI mechanism** | **Upload-artifact on failure** — an `if: failure()` step in the `build` job uploads the freshly-written goldens. | Least-privilege (no `contents: write`); matches the issue's stated "linchpin fix"; one CI run emits all 11 goldens for download + commit. Keeps the OpenSSF least-privilege posture. |
| **Store screenshots (Deliverable 4)** | **Capture now** on the local emulator (`pantry_pixel6_api34`). | Completes the README "Screenshots" gap in this PR; a documented capture recipe exists (see §6). |
| **PR structure** | **Single PR** closing #236. | One review cycle, one human merge. The CI golden-emit step rides along. |
| **`<monochrome>` on `ic_launcher_round.xml`** | **Keep the mirror** (both `ic_launcher.xml` and `ic_launcher_round.xml`). | The two adaptive-icon XMLs are otherwise identical; mirroring keeps round-mask launchers themed too. Resolves issue open-Q #6. |
| **Verb accents (`AddGreen`/`RemoveRed`)** | **Unchanged literals**, outside the M3 scheme; no dark-mode variants added. | Explicit issue scope ("stay as literals in `Color.kt`"). The handoff's lighter dark verb tones are noted as a *future* enhancement, out of scope here. |
| **Hero SVG fidelity** | **`docs/brand/hero.svg` with `<text>` + sans-serif/mono fallback** stack; **crisp PNG deferred** to the maintainer. | GitHub's SVG sanitizer strips web fonts (Geist won't render); the issue explicitly calls the SVG a "vector stopgap" and defers the raster. Hand-authored glyph paths are brittle and unnecessary for a stopgap. |

---

## 3. Adaptive launcher icon (Deliverable 1)

**Foreground** (`app/src/main/res/drawable/ic_launcher_foreground.xml`):
three rounded canisters in three heights with recessed lids, on the DocGerdSoft
datum-stroke shelf. All white `#FFFFFF`. VectorDrawable has no `rx`, so each
rounded rect becomes an arc path (the issue diff provides the exact `pathData`).

Geometry (108×108 viewport, all inside the Ø66dp safe circle centred at 54,54):

| Element | x | y | w | h | rx |
|---|---|---|---|---|---|
| Left body | 29 | 49 | 14 | 20 | 3.2 |
| Left lid | 31 | 44.4 | 10 | 3.6 | 1.5 |
| Center body | 47 | 40 | 14 | 29 | 3.2 |
| Center lid | 49 | 35.4 | 10 | 3.6 | 1.5 |
| Right body | 65 | 45 | 14 | 24 | 3.2 |
| Right lid | 67 | 40.4 | 10 | 3.6 | 1.5 |
| Shelf | (28,72) → (80,72), strokeWidth 4, round cap | | | | |

**Monochrome layer** (Android 13+): add `<monochrome
android:drawable="@drawable/ic_launcher_foreground"/>` to **both**
`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.

**Background:** `colors.xml` → `ic_launcher_background` stays `#4F7942` (no
change).

---

## 4. Material 3 colour scheme (Deliverable 2)

Replace `Theme.kt`'s `lightColorScheme(primary = Fern)` /
`darkColorScheme(primary = Fern)` with full hand-built schemes (role → hex,
verbatim from the handoff; surface-tint tonal variants left for M3 to derive):

| Role | Light | Dark | | Role | Light | Dark |
|---|---|---|---|---|---|---|
| primary | `#4F7942` | `#B4D49F` | | background | `#F8FBF1` | `#11140E` |
| onPrimary | `#FFFFFF` | `#21380E` | | onBackground | `#191D16` | `#E1E4D9` |
| primaryContainer | `#D0EFC0` | `#385030` | | surface | `#F8FBF1` | `#11140E` |
| onPrimaryContainer | `#102E03` | `#D0EFC0` | | onSurface | `#191D16` | `#E1E4D9` |
| secondary | `#54624D` | `#BBCBAD` | | surfaceVariant | `#DEE5D8` | `#424940` |
| onSecondary | `#FFFFFF` | `#263420` | | onSurfaceVariant | `#424940` | `#C2C9BB` |
| secondaryContainer | `#D7E8CC` | `#3C4B35` | | surfaceContainer | `#ECEFE4` | `#1D211A` |
| onSecondaryContainer | `#121F0E` | `#D7E8CC` | | outline | `#72796D` | `#8C9387` |
| tertiary | `#386569` | `#A0CFD3` | | outlineVariant | `#C2C9BB` | `#424940` |
| onTertiary | `#FFFFFF` | `#003739` | | inverseSurface | `#2E322B` | `#E1E4D9` |
| tertiaryContainer | `#BCEBEF` | `#1E4E51` | | inverseOnSurface | `#EFF2E8` | `#2E322B` |
| onTertiaryContainer | `#002022` | `#BCEBEF` | | inversePrimary | `#B4D49F` | `#4F7942` |
| error | `#BA1A1A` | `#FFB4AB` | | onError | `#FFFFFF` | `#690005` |
| errorContainer | `#FFDAD6` | `#93000A` | | onErrorContainer | `#410002` | `#FFDAD6` |

`AddGreen #2A6A2A` / `RemoveRed #8A2A2A` stay as literals in `Color.kt`
(unchanged). `surfaceContainer` is a valid named param at this project's
material3 1.4.0.

The `Color.kt` KDoc is updated to reflect that Fern is now the light `primary`
seed for a full inline tonal expansion (no longer a primary-only override).

---

## 5. README hero (Deliverable 3)

Commit a **self-contained** `docs/brand/hero.svg`, inserted as the first content
line of `README.md` (between the description and `## Scope / Status`), and add a
`<!-- … -->` note that the crisp 2560×1120 PNG export is deferred.

Layout (verbatim values from `pantry.css` `.readme-hero*`):

- **Canvas:** 1280×560, `border-radius` lg.
- **Background:** `radial-gradient(130% 120% at 18% 16%, #5C8A4D 0%, #4F7942 46%,
  #39592F 100%)`.
- **Datum grid overlay:** 44px white grid (`rgba(255,255,255,.6)` 1px lines),
  opacity .16, masked `radial-gradient(circle at 88% 78%, #000, transparent 62%)`.
- **Inverted icon tile (left):** ~184px squircle, background `#FBFBFC`, the
  `#ptFg` jars in Fern `#4F7942` (inverted from the launcher), drop-shadow
  `0 18px 40px rgba(20,30,12,.45)`.
- **Eyebrow (right):** `#dgsMark` delta (18px, white) + `DocGerdSoft · open
  tools`, mono, uppercase, letter-spacing .14em, `rgba(255,255,255,.82)`.
- **Title:** `Pantry Tracker`, 68px, weight 600, letter-spacing −.03em, white.
- **One-liner:** "Scan a grocery barcode, confirm, and it's in your pantry. Fully
  offline, single-user, on-device. No accounts, no analytics." (max ~42ch).
- **Pill row:** `Android 8.0+` · `Jetpack Compose · Material 3` · `Room · offline`
  · `Apache-2.0` — fill `rgba(255,255,255,.13)`, border `rgba(255,255,255,.22)`,
  mono, pill radius.

SVG symbols `#ptFg` and `#dgsMark` are inlined into the file's `<defs>` (no
external `<use href>`). Fonts use a fallback stack
(`Geist, system-ui, -apple-system, "Segoe UI", Helvetica, Arial, sans-serif` /
`"Geist Mono", ui-monospace, Menlo, monospace`); Geist is not embedded.

Exact `#ptFg` symbol (for the hero tile; uses SVG `<rect rx>`, *not* the
VectorDrawable arc form):

```xml
<symbol id="ptFg" viewBox="0 0 108 108">
  <g fill="currentColor">
    <rect x="29" y="49" width="14" height="20" rx="3.2"/>
    <rect x="31" y="44.4" width="10" height="3.6" rx="1.5"/>
    <rect x="47" y="40" width="14" height="29" rx="3.2"/>
    <rect x="49" y="35.4" width="10" height="3.6" rx="1.5"/>
    <rect x="65" y="45" width="14" height="24" rx="3.2"/>
    <rect x="67" y="40.4" width="10" height="3.6" rx="1.5"/>
  </g>
  <line x1="28" y1="72" x2="80" y2="72" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>
</symbol>
```

Exact `#dgsMark` symbol:

```xml
<symbol id="dgsMark" viewBox="0 0 100 100">
  <path d="M50 17.09 L69.87 51.5 L30.13 51.5 Z"/>
  <path d="M26.96 57 L73.04 57 L88 82.91 L12 82.91 Z"/>
</symbol>
```

---

## 6. Screenshot goldens — the linchpin

The theme change invalidates **all 11** screenshot goldens, not just icon/theme:
the 3 icon goldens change because `ic_launcher_foreground.xml` changes, and the
8 theme-dependent goldens (`Theme`, `GreyedRow`, `CoilImage`, `FontScale` ×2 each)
change because the full M3 scheme re-maps every role they render through.

`ScreenshotTestBase.compareOrWrite()` has **no record-mode flag**: a missing
golden is written to disk and then `fail()`s. The goldens are Robolectric-NATIVE /
Skia **byte-exact and host-specific** (the screenshot ADR: "golden regeneration is
mandatory after a Robolectric/SDK bump"; the base-class KDoc: renders
"deterministically on the same host"). So canonical bytes **must** come from the
CI runner (`ubuntu-latest`, JDK 21 Temurin), never a local WSL render.

### Regeneration sequence

1. **Add the CI golden-emit step** — in `.github/workflows/ci.yml`, after the
   "Build, unit-test, lint" step in the `build` job:
   ```yaml
   - name: Upload screenshot goldens on test failure
     if: failure()
     uses: actions/upload-artifact@<pinned-sha>  # v7.x, SHA-pinned per repo policy
     with:
       name: screenshot-goldens-actual
       path: |
         app/src/test/snapshots/**/*.png
         app/src/test/snapshots/**/*_actual.png
       if-no-files-found: warn
   ```
   Also add `app/src/test/snapshots/*_actual.png` to `.gitignore` (currently
   missing) so diagnostic renders are never committed. This is a **permanent
   golden-maintenance improvement**, not a one-off.
2. **Delete all 11 goldens** and commit the source changes + deletion; push.
3. CI `build` runs `testDebugUnitTest`: all 11 missing → each written then
   `fail()`s (test execution continues across methods, so all 11 are emitted in
   one run) → the `if: failure()` step uploads them.
4. **Download + inspect + commit:** `gh run download` the artifact; visually
   review each PNG (icon across full/circular/square masks; light/dark theme;
   greyed row; coil image; font scale), commit the 11 canonical CI bytes to the
   branch, push.
5. CI `build` goes green → unblocks the `androidTest` job (`needs: build`) → the
   0.80 LINE coverage gate runs. Final human visual sign-off.

---

## 7. Store screenshots (Deliverable 4)

Install the brand-themed **debug** build on `pantry_pixel6_api34`; capture four
frames in the real M3 scheme:

- **Home / Detail / dark Home:** seed Room via `run-as <pkg> sqlite3` (SQL via
  stdin, not arg); SystemUI **demo-mode** for clean status-bar chrome (time 9:30,
  signal/wifi/battery). Sample content per the handoff (Barilla Spaghetti ×2,
  Mutti Crushed Tomatoes ×4, Alnatura Whole Milk ×1, Bertolli Olive Oil ×1, Sea
  Salt ×3, Tilda Basmati Rice ×0 out-of-stock).
- **Scan:** the camera renders **black** in `screencap` and `FakeCameraSource` is
  androidTest-only, so the candidate sheet needs a **throwaway, uncommitted**
  `onBarcodeDecoded(...)` trigger (and skipping `CameraPreview`) — **reverted
  before any commit**.

Commit to `docs/screenshots/` and wire into the README "Screenshots" section.

---

## 8. Verification → review → hand-off

1. Local pre-push gates: `:app:test` (non-screenshot tests pass; only goldens
   fail by design), `:detekt-rules:test`, `:app:detekt`, `:app:lint`.
2. Push → drive the golden CI loop (§6) to full green (`build` + `androidTest` +
   `Fuzz regression`).
3. Open the PR with `Closes #236`; copy labels/milestone from the issue
   (`gh pr create` doesn't inherit them).
4. Run the standard multi-agent PR review (`pr-review` skill): post findings as
   inline threads, fix on-branch, resolve each thread, re-review after fixes.
5. Hand off to the human for the merge to `develop`. **Claude does not merge.**

---

## 9. Risks / gotchas

1. **Detekt `MagicNumber` on the expanded `Theme.kt`** (~45 `Color(0x…)`
   literals). If it fires, add `Theme.kt` to `MagicNumber.excludes` **re-listing
   every existing default exclude** (`**/test/**`, `**/androidTest/**`, `**/*.kts`,
   …) — Detekt *replaces* list-valued overrides, it does not merge. Verify
   `:app:detekt` locally before push.
2. **Host-non-portable goldens** — never commit locally-rendered bytes; only the
   CI-emitted artifact is canonical (§6). This is the exact trap that reverted
   PR #238.
3. **Scan-screenshot trigger** is an uncommitted local hack — confirm it's
   reverted before staging (`git status` clean of `app/` source beyond the brand
   changes).
4. **`hero.svg` is a known stopgap** — Geist falls back to system fonts on
   GitHub; the crisp PNG stays a maintainer action (issue Deliverable 5).
5. **No `@Ignore` shortcut** — keep the entire screenshot suite enabled; drop the
   issue diff's two `@Ignore` hunks and the snapshots/README skip-note (the
   abandoned PR #238 approach).
6. **`upload-artifact` only fires on step failure** — confirmed compatible:
   `testDebugUnitTest` *does* fail on a missing/mismatched golden, so the
   `if: failure()` gate triggers and the written PNGs are present.

---

## 10. Out of scope

- The crisp 2560×1120 README hero **PNG** (maintainer action; SVG stopgap ships
  now).
- Dark-mode variants of the verb accents (`AddGreen`/`RemoveRed`) — kept as the
  existing single literals per issue scope.
- Any change to `PantryTypography` or app type.
- Promoting any new CI check to *required* (the golden-emit step is diagnostic,
  not a gate).
