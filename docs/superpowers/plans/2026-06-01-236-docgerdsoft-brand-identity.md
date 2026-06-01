# DocGerdSoft Brand Identity (#236) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the DocGerdSoft brand identity — refined adaptive launcher icon, full Material 3 light/dark colour scheme seeded from Fern `#4F7942`, README hero banner, and store screenshots — to Pantry Tracker, with the entire screenshot-golden suite regenerated from CI-rendered bytes.

**Architecture:** Pure resource/theme change in `:app` plus a permanent CI golden-emit improvement. The brand design is locked by issue #236 and the `design_handoff_pantry_brand` package; this plan only executes it. The hard part is golden regeneration: all 11 Robolectric screenshot goldens go stale and are host-specific, so canonical bytes are emitted by the CI `build` job (via a new `upload-artifact` step) and committed back, never rendered locally.

**Tech Stack:** Kotlin / Jetpack Compose / Material 3 (1.4.0), Android VectorDrawable, Robolectric 4.16.1 NATIVE-graphics screenshot tests, GitHub Actions (ubuntu-latest, JDK 21 Temurin), `gh` CLI.

**Spec:** [`docs/superpowers/specs/2026-06-01-236-docgerdsoft-brand-identity-design.md`](../specs/2026-06-01-236-docgerdsoft-brand-identity-design.md)

**Branch:** `brand/docgerdsoft-identity` (already created off `develop`; the spec commit `ee0b47c` is the first commit).

---

## Task 1: Expand the Material 3 colour scheme

**Files:**
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt`
- Modify: `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt` (KDoc only)

- [ ] **Step 1: Replace `Theme.kt` with the full schemes**

Overwrite `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt` with:

```kotlin
package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Full Material 3 light/dark schemes seeded from Fern (#4F7942). Roles map
// verbatim to the DocGerdSoft brand handoff's role->hex table; surface-tint
// tonal variants not enumerated there (surfaceContainerLow/High/Highest,
// surfaceBright/Dim, scrim, surfaceTint) are left to M3 to derive. The
// AddGreen/RemoveRed verb accents stay outside this scheme (see Color.kt).
private val LightColors = lightColorScheme(
    primary = Fern,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0EFC0),
    onPrimaryContainer = Color(0xFF102E03),
    secondary = Color(0xFF54624D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E8CC),
    onSecondaryContainer = Color(0xFF121F0E),
    tertiary = Color(0xFF386569),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBEF),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FBF1),
    onBackground = Color(0xFF191D16),
    surface = Color(0xFFF8FBF1),
    onSurface = Color(0xFF191D16),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    surfaceContainer = Color(0xFFECEFE4),
    outline = Color(0xFF72796D),
    outlineVariant = Color(0xFFC2C9BB),
    inverseSurface = Color(0xFF2E322B),
    inverseOnSurface = Color(0xFFEFF2E8),
    inversePrimary = Color(0xFFB4D49F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4D49F),
    onPrimary = Color(0xFF21380E),
    primaryContainer = Color(0xFF385030),
    onPrimaryContainer = Color(0xFFD0EFC0),
    secondary = Color(0xFFBBCBAD),
    onSecondary = Color(0xFF263420),
    secondaryContainer = Color(0xFF3C4B35),
    onSecondaryContainer = Color(0xFFD7E8CC),
    tertiary = Color(0xFFA0CFD3),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF1E4E51),
    onTertiaryContainer = Color(0xFFBCEBEF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11140E),
    onBackground = Color(0xFFE1E4D9),
    surface = Color(0xFF11140E),
    onSurface = Color(0xFFE1E4D9),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BB),
    surfaceContainer = Color(0xFF1D211A),
    outline = Color(0xFF8C9387),
    outlineVariant = Color(0xFF424940),
    inverseSurface = Color(0xFFE1E4D9),
    inverseOnSurface = Color(0xFF2E322B),
    inversePrimary = Fern,
)

@Composable
fun PantryTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PantryTypography,
        content = content,
    )
}
```

- [ ] **Step 2: Update the `Color.kt` KDoc on `Fern`**

In `app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt`, replace the KDoc block above `val Fern` (the comment currently starting `/** Pantry/produce-evocative primary colour. Only the \`primary\` slot is`) with:

```kotlin
/** Pantry/produce-evocative seed colour — the single Fern accent of the
 *  DocGerdSoft "one accent per product" identity. Used as the light-mode
 *  `primary` and as the dark-mode `inversePrimary`. The full light/dark M3
 *  tonal expansion seeded from this value lives inline in [PantryTrackerTheme]
 *  (`lightColorScheme(...)` / `darkColorScheme(...)` in Theme.kt) — Fern is no
 *  longer just a primary-only override over the M3 Baseline. */
val Fern: Color = Color(0xFF4F7942)
```

Leave the `AddGreen` / `RemoveRed` declarations and their comment unchanged.

- [ ] **Step 3: Compile-check (non-screenshot tests only)**

The screenshot goldens will fail now — that is expected and handled in Task 5. Verify the code compiles and the *non-screenshot* unit tests pass:

Run: `./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest --tests '*' -x test 2>&1 | tail -20`

Simpler: just compile (screenshot failures are deferred):

Run: `./gradlew --no-daemon :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. (If the sandbox blocks Gradle, run with the project's documented sandbox-off setting — see CLAUDE.md "reference_local_android_toolchain".)

- [ ] **Step 4: Run detekt and watch `MagicNumber`**

The expanded `Theme.kt` adds ~45 `Color(0x…)` literals.

Run: `./gradlew --no-daemon :app:detekt 2>&1 | tail -30`
Expected: `BUILD SUCCESSFUL`.

If `MagicNumber` fires on `Theme.kt`: add `**/ui/theme/Theme.kt` to `MagicNumber.excludes` in `detekt-config.yml`, **re-listing every existing default exclude in that block** (`**/test/**`, `**/androidTest/**`, `**/*.kts`, …) — Detekt *replaces* list-valued overrides rather than merging (see CLAUDE.md "detekt config: list keys REPLACE"). Re-run detekt to confirm green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Theme.kt \
        app/src/main/java/de/docgerdsoft/pantrytracker/ui/theme/Color.kt
# include detekt-config.yml in this add only if Step 4 required the exclude
git commit -m "feat(theme): full Material 3 light/dark scheme seeded from Fern (#236)"
```

---

## Task 2: Refine the adaptive launcher icon

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [ ] **Step 1: Replace the foreground drawable**

Overwrite `app/src/main/res/drawable/ic_launcher_foreground.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!--
      Refined adaptive-icon foreground: three rounded pantry canisters in three
      heights (pantry rhythm) with recessed screw-top lids, sitting on the
      DocGerdSoft datum-stroke shelf. All shapes white (#FFFFFF) on the Fern
      background. Android VectorDrawable has no rect rx, so each canister body
      (rx 3.2) and lid (rx 1.5) is expressed as a rounded-rect arc path. All
      geometry sits inside the O66dp safe circle centered at 54,54, so it
      survives every launcher mask (circle / squircle / rounded / square).
    -->

    <!-- Left canister (short): body x29 y49 w14 h20 r3.2 -->
    <path
        android:pathData="M32.2,49 L39.8,49 A3.2,3.2 0 0 1 43,52.2 L43,65.8 A3.2,3.2 0 0 1 39.8,69 L32.2,69 A3.2,3.2 0 0 1 29,65.8 L29,52.2 A3.2,3.2 0 0 1 32.2,49 Z"
        android:fillColor="#FFFFFF"/>
    <!-- Left lid (recessed): x31 y44.4 w10 h3.6 r1.5 -->
    <path
        android:pathData="M32.5,44.4 L39.5,44.4 A1.5,1.5 0 0 1 41,45.9 L41,46.5 A1.5,1.5 0 0 1 39.5,48 L32.5,48 A1.5,1.5 0 0 1 31,46.5 L31,45.9 A1.5,1.5 0 0 1 32.5,44.4 Z"
        android:fillColor="#FFFFFF"/>

    <!-- Center canister (tall): body x47 y40 w14 h29 r3.2 -->
    <path
        android:pathData="M50.2,40 L57.8,40 A3.2,3.2 0 0 1 61,43.2 L61,65.8 A3.2,3.2 0 0 1 57.8,69 L50.2,69 A3.2,3.2 0 0 1 47,65.8 L47,43.2 A3.2,3.2 0 0 1 50.2,40 Z"
        android:fillColor="#FFFFFF"/>
    <!-- Center lid (recessed): x49 y35.4 w10 h3.6 r1.5 -->
    <path
        android:pathData="M50.5,35.4 L57.5,35.4 A1.5,1.5 0 0 1 59,36.9 L59,37.5 A1.5,1.5 0 0 1 57.5,39 L50.5,39 A1.5,1.5 0 0 1 49,37.5 L49,36.9 A1.5,1.5 0 0 1 50.5,35.4 Z"
        android:fillColor="#FFFFFF"/>

    <!-- Right canister (medium): body x65 y45 w14 h24 r3.2 -->
    <path
        android:pathData="M68.2,45 L75.8,45 A3.2,3.2 0 0 1 79,48.2 L79,65.8 A3.2,3.2 0 0 1 75.8,69 L68.2,69 A3.2,3.2 0 0 1 65,65.8 L65,48.2 A3.2,3.2 0 0 1 68.2,45 Z"
        android:fillColor="#FFFFFF"/>
    <!-- Right lid (recessed): x67 y40.4 w10 h3.6 r1.5 -->
    <path
        android:pathData="M68.5,40.4 L75.5,40.4 A1.5,1.5 0 0 1 77,41.9 L77,42.5 A1.5,1.5 0 0 1 75.5,44 L68.5,44 A1.5,1.5 0 0 1 67,42.5 L67,41.9 A1.5,1.5 0 0 1 68.5,40.4 Z"
        android:fillColor="#FFFFFF"/>

    <!-- Shelf = DocGerdSoft datum stroke: from (28,72) to (80,72), round cap. -->
    <path
        android:pathData="M28,72 L80,72"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="4"
        android:strokeLineCap="round"/>
</vector>
```

- [ ] **Step 2: Add the monochrome layer to both adaptive-icon XMLs**

Overwrite `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

Overwrite `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` with the **identical** content (same three lines + the new `<monochrome>` line).

- [ ] **Step 3: Verify the resources compile**

Run: `./gradlew --no-daemon :app:assembleDebug 2>&1 | tail -15`
Expected: `BUILD SUCCESSFUL`. (A malformed `pathData` fails at `:app:processDebugResources` / AAPT2.)

- [ ] **Step 4: Visually confirm the rendered icon (local emulator)**

If the emulator is available, install and eyeball the launcher icon before relying on the goldens:

Run:
```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
# open the launcher and confirm three white canisters + shelf on Fern
```
Confirm the three canisters, recessed lids, and datum shelf read correctly inside every mask (long-press the launcher → icon shape options, or check the app drawer). This is the human-checkable proof the geometry is right; the screenshot goldens lock it byte-for-byte afterward.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_launcher_foreground.xml \
        app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml \
        app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
git commit -m "feat(icon): refined pantry-canister launcher icon + monochrome layer (#236)"
```

---

## Task 3: README hero banner

**Files:**
- Create: `docs/brand/hero.svg`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Create the self-contained hero SVG**

Create `docs/brand/hero.svg`. This is a **vector stopgap** (the crisp 2560×1120 PNG is a deferred maintainer action); GitHub strips web fonts, so text uses a system-font fallback stack. Symbols are inlined (no external `<use>`). Render-check happens on the PR (there is no local SVG renderer; do not trust an agent's "renders fine" verdict — see CLAUDE.md Mermaid lesson). Tweak text positions after viewing the PR render if needed.

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 560" width="1280" height="560" role="img"
     aria-label="Pantry Tracker — scan a grocery barcode, confirm, and it's in your pantry. Fully offline, single-user, on-device.">
  <defs>
    <radialGradient id="fern" cx="0.18" cy="0.16" r="1.05" gradientUnits="objectBoundingBox">
      <stop offset="0" stop-color="#5C8A4D"/>
      <stop offset="0.46" stop-color="#4F7942"/>
      <stop offset="1" stop-color="#39592F"/>
    </radialGradient>
    <pattern id="grid" width="44" height="44" patternUnits="userSpaceOnUse">
      <path d="M44 0 H0 V44" fill="none" stroke="#FFFFFF" stroke-opacity="0.6" stroke-width="1"/>
    </pattern>
    <radialGradient id="gridfade" cx="0.88" cy="0.78" r="0.62" gradientUnits="objectBoundingBox">
      <stop offset="0" stop-color="#FFFFFF"/>
      <stop offset="1" stop-color="#000000"/>
    </radialGradient>
    <mask id="gridmask"><rect width="1280" height="560" fill="url(#gridfade)"/></mask>
    <clipPath id="tile"><rect x="64" y="188" width="184" height="184" rx="46" ry="46"/></clipPath>
    <filter id="tileshadow" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="18" stdDeviation="20" flood-color="#141E0C" flood-opacity="0.45"/>
    </filter>
    <!-- Pantry Tracker icon foreground (jars + datum shelf) -->
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
    <!-- DocGerdSoft delta family mark -->
    <symbol id="dgsMark" viewBox="0 0 100 100">
      <path d="M50 17.09 L69.87 51.5 L30.13 51.5 Z"/>
      <path d="M26.96 57 L73.04 57 L88 82.91 L12 82.91 Z"/>
    </symbol>
  </defs>

  <!-- Fern background + faded datum grid -->
  <rect width="1280" height="560" fill="url(#fern)"/>
  <rect width="1280" height="560" fill="url(#grid)" opacity="0.16" mask="url(#gridmask)"/>

  <!-- Inverted icon tile: white squircle, Fern jars -->
  <g filter="url(#tileshadow)">
    <rect x="64" y="188" width="184" height="184" rx="46" ry="46" fill="#FBFBFC"/>
  </g>
  <g clip-path="url(#tile)" color="#4F7942">
    <use href="#ptFg" x="64" y="188" width="184" height="184"/>
  </g>

  <!-- Text block -->
  <g fill="#FFFFFF"
     font-family="Geist, system-ui, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif">
    <!-- eyebrow -->
    <g color="#FFFFFF">
      <use href="#dgsMark" x="312" y="171" width="18" height="18"/>
    </g>
    <text x="340" y="185" font-family="'Geist Mono', ui-monospace, Menlo, monospace"
          font-size="13" letter-spacing="1.8" fill="#FFFFFF" fill-opacity="0.82"
          style="text-transform:uppercase">DOCGERDSOFT · OPEN TOOLS</text>
    <!-- title -->
    <text x="312" y="262" font-size="68" font-weight="600" letter-spacing="-2">Pantry Tracker</text>
    <!-- one-liner -->
    <text x="312" y="312" font-size="21" fill="#FFFFFF" fill-opacity="0.9">Scan a grocery barcode, confirm, and it's in your pantry. Fully</text>
    <text x="312" y="342" font-size="21" fill="#FFFFFF" fill-opacity="0.9">offline, single-user, on-device. No accounts, no analytics.</text>
    <!-- pills -->
    <g font-family="'Geist Mono', ui-monospace, Menlo, monospace" font-size="12.5" letter-spacing="0.4">
      <g transform="translate(312,400)">
        <rect width="116" height="30" rx="15" fill="#FFFFFF" fill-opacity="0.13" stroke="#FFFFFF" stroke-opacity="0.22"/>
        <text x="13" y="19" fill="#FFFFFF">Android 8.0+</text>
      </g>
      <g transform="translate(438,400)">
        <rect width="232" height="30" rx="15" fill="#FFFFFF" fill-opacity="0.13" stroke="#FFFFFF" stroke-opacity="0.22"/>
        <text x="13" y="19" fill="#FFFFFF">Jetpack Compose · Material 3</text>
      </g>
      <g transform="translate(680,400)">
        <rect width="128" height="30" rx="15" fill="#FFFFFF" fill-opacity="0.13" stroke="#FFFFFF" stroke-opacity="0.22"/>
        <text x="13" y="19" fill="#FFFFFF">Room · offline</text>
      </g>
      <g transform="translate(818,400)">
        <rect width="104" height="30" rx="15" fill="#FFFFFF" fill-opacity="0.13" stroke="#FFFFFF" stroke-opacity="0.22"/>
        <text x="13" y="19" fill="#FFFFFF">Apache-2.0</text>
      </g>
    </g>
  </g>
</svg>
```

- [ ] **Step 2: Insert the hero into the README**

In `README.md`, insert the following **between the description paragraph (ends line 17) and the `## Scope / Status` header (line 19)** — i.e. immediately after the blank line following "…no crash reporter." and before "## Scope / Status":

```markdown
<!--
  Hero banner (vector stopgap). The crisp 2560x1120 PNG export (render this SVG
  or the brand-doc `.readme-hero` at 2x) is DEFERRED to the maintainer; swap the
  src below to docs/brand/hero.png once it exists for a sharp social-preview card.
-->
<img src="docs/brand/hero.svg" alt="Pantry Tracker — scan a grocery barcode, confirm, and it's in your pantry. Fully offline, single-user, on-device." width="100%">

```

(Place it above the badge block if you prefer the banner first; the design intent is "first content line." Confirm the rendered order on the PR.)

- [ ] **Step 3: Add the CHANGELOG entries**

In `CHANGELOG.md`, under `## [Unreleased]`:

Append to the existing `### Added` list:

```markdown
- Monochrome / themed-icon layer (Android 13+) on the adaptive launcher icon, so
  the OS can tint the canisters-and-shelf foreground to the user's monochrome theme.
- README hero banner (`docs/brand/hero.svg`) and store-style screenshots; the
  crisp 2560×1120 PNG export of the hero is deferred to the maintainer.
```

Prepend to the existing `### Changed` list (above the coverage-gate entry):

```markdown
- Refined the adaptive launcher-icon foreground geometry: the three flat
  rectangles become rounded three-height pantry canisters with recessed lids,
  and the shelf is redrawn as the DocGerdSoft datum stroke — all white on the
  unchanged Fern `#4F7942` background.
- Expanded the Material 3 theme from the previous primary-only override into a
  full hand-built light/dark colour scheme seeded from Fern `#4F7942` (every M3
  role mapped to hex). The `AddGreen` / `RemoveRed` verb accents are retained as
  brand constants outside the scheme.
```

- [ ] **Step 4: Commit**

```bash
git add docs/brand/hero.svg README.md CHANGELOG.md
git commit -m "docs(brand): README hero banner + brand CHANGELOG entries (#236)"
```

---

## Task 4: Add the CI golden-emit step

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.gitignore`

- [ ] **Step 1: Add the upload-artifact step to the `build` job**

In `.github/workflows/ci.yml`, insert this step **after the "Build, unit-test, lint" step (its `run:` ends at line 86) and before the "Detekt (Kotlin static analysis)" step (line 88)**:

```yaml
      - name: Upload screenshot goldens on test failure
        # When a screenshot golden is missing or mismatched, ScreenshotTestBase
        # writes the freshly-rendered PNG to app/src/test/snapshots/ and then
        # fail()s. This step captures those CI-rendered (ubuntu-latest, JDK 21)
        # bytes so they can be downloaded and committed as the canonical goldens
        # — the only host whose bytes the suite will accept. Permanent
        # golden-maintenance aid; diagnostic only, never a required check.
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a  # v7.0.1
        with:
          name: screenshot-goldens-actual
          path: |
            app/src/test/snapshots/*.png
            app/src/test/snapshots/*_actual.png
          if-no-files-found: warn
```

- [ ] **Step 2: Ignore diagnostic `*_actual.png` files**

Append to `.gitignore`:

```gitignore

# Screenshot-test diagnostic renders (written on golden mismatch; never committed)
app/src/test/snapshots/*_actual.png
```

- [ ] **Step 3: Lint the workflow YAML locally**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('ci.yml OK')"`
Expected: `ci.yml OK` (no YAML parse error).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml .gitignore
git commit -m "ci: emit screenshot goldens as an artifact on test failure (#236)"
```

---

## Task 5: Regenerate the screenshot goldens from CI

This is the linchpin. The 11 goldens are host-specific Robolectric/Skia bytes; canonical bytes come only from the CI runner. `ScreenshotTestBase.compareOrWrite()` has no record flag — deleting a golden makes the test write a fresh one and fail.

**Files:**
- Delete: `app/src/test/snapshots/*.png` (all 11)
- Recreate (from CI): the same 11 PNGs

- [ ] **Step 1: Delete all 11 goldens**

```bash
git rm app/src/test/snapshots/coil_image_absent.png \
       app/src/test/snapshots/coil_image_present.png \
       app/src/test/snapshots/font_scale_large.png \
       app/src/test/snapshots/font_scale_small.png \
       app/src/test/snapshots/greyed_row_in_stock.png \
       app/src/test/snapshots/greyed_row_out_of_stock.png \
       app/src/test/snapshots/icon_circular_mask.png \
       app/src/test/snapshots/icon_full_canvas.png \
       app/src/test/snapshots/icon_square_mask.png \
       app/src/test/snapshots/theme_dark_mode.png \
       app/src/test/snapshots/theme_light_mode.png
```

Verify exactly 11 are staged for deletion:

Run: `git diff --cached --name-only --diff-filter=D | grep -c 'snapshots/.*\.png'`
Expected: `11`

- [ ] **Step 2: Commit the deletion and push the branch**

```bash
git commit -m "test(screenshot): invalidate all 11 goldens for the brand theme/icon change (#236)"
git push -u origin brand/docgerdsoft-identity
```

(Per CLAUDE.md governance, pushing to this feature branch is allowed — only merges to `develop`/`main` are human-gated.)

- [ ] **Step 3: Wait for the CI `build` job to fail and emit the goldens**

The `build` job runs `testDebugUnitTest`; all 11 goldens are missing, so each is written then `fail()`s (test execution continues across all methods, so all 11 are emitted in one run), and the new `if: failure()` step uploads them.

Run:
```bash
gh run watch "$(gh run list --branch brand/docgerdsoft-identity --workflow ci.yml -L1 --json databaseId -q '.[0].databaseId')" --exit-status 2>&1 | tail -20
```
Expected: the `build` job **fails** at the unit-test step (this is the designed path), and an artifact named `screenshot-goldens-actual` is produced.

- [ ] **Step 4: Download the CI-rendered goldens**

```bash
RUN_ID="$(gh run list --branch brand/docgerdsoft-identity --workflow ci.yml -L1 --json databaseId -q '.[0].databaseId')"
rm -rf /tmp/ci-goldens && mkdir -p /tmp/ci-goldens
gh run download "$RUN_ID" -n screenshot-goldens-actual -D /tmp/ci-goldens
ls -1 /tmp/ci-goldens
```
Expected: the 11 golden PNGs (no `*_actual.png`, since on a fresh-missing run only the goldens are written).

- [ ] **Step 5: Visually inspect every golden before committing**

Read each PNG and confirm it looks correct (this is the safety valve — never commit bytes you have not eyeballed):
- `icon_full_canvas.png`, `icon_circular_mask.png`, `icon_square_mask.png` — three white canisters + datum shelf on Fern, surviving each mask.
- `theme_light_mode.png` / `theme_dark_mode.png` — Fern-derived primary app bar; correct light (`#F8FBF1`) / dark (`#11140E`) surfaces.
- `greyed_row_in_stock.png` / `greyed_row_out_of_stock.png` — out-of-stock row dimmed.
- `coil_image_present.png` / `coil_image_absent.png`, `font_scale_small.png` / `font_scale_large.png` — render through the new scheme without artifacts.

Use the Read tool on each `/tmp/ci-goldens/*.png` (it renders images) and/or have the human eyeball them.

- [ ] **Step 6: Commit the canonical goldens and push**

```bash
cp /tmp/ci-goldens/*.png app/src/test/snapshots/
git add app/src/test/snapshots/*.png
git commit -m "test(screenshot): regenerate all 11 goldens from CI-rendered bytes (#236)"
git push
```

- [ ] **Step 7: Confirm CI goes fully green**

Run:
```bash
gh run watch "$(gh run list --branch brand/docgerdsoft-identity --workflow ci.yml -L1 --json databaseId -q '.[0].databaseId')" --exit-status 2>&1 | tail -20
```
Expected: `build` passes (goldens now match the CI bytes), which unblocks `androidTest` (`needs: build`); the 0.80 LINE coverage gate and `Fuzz regression` pass. All checks green.

---

## Task 6: Store screenshots

Capture four store-style frames in the real M3 scheme on the local emulator. See CLAUDE.md "reference_emulator_screenshot_capture" / PR #233 / #225 for the full recipe and footguns.

**Files:**
- Create: `docs/screenshots/home.png`, `docs/screenshots/scan.png`, `docs/screenshots/detail.png`, `docs/screenshots/home_dark.png`
- Modify: `README.md` (Screenshots section)

- [ ] **Step 1: Boot the emulator and install the brand-themed debug build**

```bash
./gradlew --no-daemon :app:assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global sysui_demo_allowed 1
```

- [ ] **Step 2: Apply clean SystemUI demo-mode chrome**

```bash
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4 -e datatype none
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

- [ ] **Step 3: Seed Room with sample pantry data**

Read `app/src/main/java/de/docgerdsoft/pantrytracker/data/local/AppDatabase.kt` to get the DB name and the entity/table + column names, then build an `INSERT` script for the handoff's sample content (Barilla Spaghetti ×2, Mutti Crushed Tomatoes ×4 [barcode 8005110023456, 400 g], Alnatura Whole Milk 1L ×1, Bertolli Olive Oil ×1, Sea Salt Fine ×3, Tilda Basmati Rice 1kg ×0). Seed via stdin (SQL via stdin, not as an arg — see the memory note):

```bash
adb shell run-as de.docgerdsoft.pantrytracker sqlite3 \
  /data/data/de.docgerdsoft.pantrytracker/databases/<DB_NAME> < /tmp/seed.sql
# relaunch the app so it reads the seeded rows
adb shell am force-stop de.docgerdsoft.pantrytracker
adb shell monkey -p de.docgerdsoft.pantrytracker -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 4: Capture Home (light), Detail, and Home (dark)**

```bash
adb exec-out screencap -p > docs/screenshots/home.png
# navigate to a product detail (tap a row), then:
adb exec-out screencap -p > docs/screenshots/detail.png
# enable dark mode, return Home:
adb shell "cmd uimode night yes"
adb exec-out screencap -p > docs/screenshots/home_dark.png
adb shell "cmd uimode night no"
```

- [ ] **Step 5: Capture the Scan frame via a throwaway trigger**

The camera renders **black** in `screencap` and `FakeCameraSource` is androidTest-only, so the candidate sheet must be triggered by a temporary, **uncommitted** edit. Read `ScanScreen.kt` / the scan ViewModel to find the `onBarcodeDecoded(...)` entry point; temporarily call it with the Mutti barcode `8005110023456` on screen entry and skip `CameraPreview`. Rebuild, install, capture:

```bash
./gradlew --no-daemon :app:assembleDebug && adb install -r -d app/build/outputs/apk/debug/app-debug.apk
# trigger the scan candidate sheet, then:
adb exec-out screencap -p > docs/screenshots/scan.png
```

Then **revert the throwaway edit** and confirm the tree is clean of it:

Run: `git status --porcelain app/src/main` 
Expected: empty (no uncommitted source change from the scan trigger).

- [ ] **Step 6: Exit demo mode and wire screenshots into the README**

```bash
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

Replace the README "Screenshots are a follow-up" placeholder (around line 36 — grep `git grep -n "Screenshot" README.md`) with a table:

```markdown
## Screenshots

| Home | Scan | Detail | Home (dark) |
|---|---|---|---|
| ![Home](docs/screenshots/home.png) | ![Scan](docs/screenshots/scan.png) | ![Detail](docs/screenshots/detail.png) | ![Home dark](docs/screenshots/home_dark.png) |
```

- [ ] **Step 7: Commit**

```bash
git add docs/screenshots/home.png docs/screenshots/scan.png \
        docs/screenshots/detail.png docs/screenshots/home_dark.png README.md
git commit -m "docs(brand): store-style screenshots in the real M3 scheme (#236)"
git push
```

---

## Task 7: Verify, open the PR, review, hand off

**Files:** none (process task)

- [ ] **Step 1: Final local gates**

Run:
```bash
./gradlew --no-daemon :app:testDebugUnitTest :detekt-rules:test :app:detekt :app:lintDebug 2>&1 | tail -25
```
Expected: `BUILD SUCCESSFUL` (goldens now match; ErrorTone proof test green; detekt + lint clean).

- [ ] **Step 2: Confirm CI is fully green on the pushed branch**

Run: `gh run list --branch brand/docgerdsoft-identity --workflow ci.yml -L1`
Expected: latest run `completed / success` (`build` + `androidTest` + `Fuzz regression`).

- [ ] **Step 3: Open the PR with the issue link**

```bash
gh pr create --base develop --head brand/docgerdsoft-identity \
  --title "feat(brand): apply DocGerdSoft identity (M3 scheme, refined icon, hero, screenshots)" \
  --body "$(cat <<'EOF'
Applies the DocGerdSoft product brand identity per the locked design.

- Refined adaptive launcher icon (rounded canisters + recessed lids on the datum
  shelf) with an Android-13 monochrome layer on both adaptive-icon XMLs.
- Full Material 3 light/dark scheme seeded from Fern #4F7942 (every role → hex);
  AddGreen/RemoveRed kept as literals outside the scheme.
- README hero banner (docs/brand/hero.svg, vector stopgap — crisp PNG deferred)
  and store-style screenshots in the real M3 scheme.
- Regenerated ALL 11 screenshot goldens from CI-rendered bytes (the whole suite
  stays enabled — no @Ignore shortcut) and added a permanent CI golden-emit
  artifact step.

Spec: docs/superpowers/specs/2026-06-01-236-docgerdsoft-brand-identity-design.md
Plan: docs/superpowers/plans/2026-06-01-236-docgerdsoft-brand-identity.md

Closes #236
EOF
)"
```

- [ ] **Step 4: Copy labels + milestone from the issue**

`gh pr create` does not inherit them. Copy issue #236's labels (`documentation`, `enhancement`) onto the new PR via the REST API (see CLAUDE.md "feedback_pr_labels_milestones_not_inherited"):

```bash
PR=$(gh pr view --json number -q .number)
gh api -X POST "repos/DocGerd/pantry-tracker/issues/$PR/labels" -f "labels[]=documentation" -f "labels[]=enhancement"
```

- [ ] **Step 5: Run the multi-agent PR review**

Invoke the `pr-review` skill (the repo's canonical cycle): dispatch the toolkit agents, post findings as inline threads (batched review), fix every finding on this branch, resolve each thread after its fix, re-review after fixes land. **Eyeball the hero SVG and the four screenshots on the rendered PR** — the SVG render and screenshot framing are only reliably verifiable on GitHub, not by a grammar-reasoning agent.

- [ ] **Step 6: Hand off to the human for merge**

Report ready-to-merge. **Do NOT run `gh pr merge`** — merges to `develop` are human-gated (CLAUDE.md hard governance rule). Ask the human to do the final visual sign-off (icon on device, M3 contrast/AA, hero render, screenshots) and merge.

---

## Self-Review

**Spec coverage:**
- §3 Icon → Task 2. ✓
- §4 M3 scheme → Task 1. ✓
- §5 Hero → Task 3. ✓
- §6 Goldens (CI emit + regen) → Task 4 + Task 5. ✓
- §7 Store screenshots → Task 6. ✓
- §8 Verify/review/hand-off → Task 7. ✓
- §2 decisions (upload-artifact, keep round monochrome, verb literals unchanged, single PR) → reflected in Tasks 2/3/4/7. ✓
- §9 risks (MagicNumber, host-non-portable, scan-trigger revert, hero stopgap, no @Ignore, failure()-gated upload) → Task 1 Step 4, Task 5 (whole), Task 6 Step 5, Task 3 Step 1, Task 5 Step 1, Task 4 Step 1. ✓

**Placeholder scan:** The only `<...>` tokens are `<DB_NAME>` and the scan-trigger location in Task 6 — these are inherently determined by reading repo files at execution time (the schema/entry-point are not knowable statically and the step says exactly which file to read). No "TBD/handle edge cases/write tests for the above" placeholders.

**Type consistency:** Colour role names match the M3 `lightColorScheme`/`darkColorScheme` parameter names exactly; `Fern` reused as light `primary` + dark `inversePrimary`; the `upload-artifact` SHA `043fb46d…` matches the repo's existing pins (fuzz.yml, ci.yml:201); the 11 golden filenames in Task 5 match the suite enumerated in the spec.

---

## Execution Handoff

(See the README header — use subagent-driven-development or executing-plans to run this plan.)
