# Screenshot golden files

This directory contains the golden PNG files used by the Robolectric Native
Graphics (RNG) screenshot tests under `app/src/test/.../screenshot/`.

## How golden comparison works

- **First run (no golden):** the test captures a PNG and writes it here, then
  **fails** with a message like "Golden `foo.png` did not exist — wrote it to
  ...".  Review the image visually.  If it looks correct, commit the PNG and
  re-run — the test will pass.
- **Subsequent runs:** the test captures a PNG and compares it byte-for-byte to
  the committed golden.  Any pixel difference causes the test to fail and write
  a `_actual.png` file next to the golden so you can compare them side-by-side.

## Regenerating goldens after an intentional design change

1. Delete the stale golden PNG(s) from this directory.
2. Run the affected test(s):

   ```bash
   ./gradlew :app:test --tests '*screenshot*'
   ```

3. Each deleted golden is re-written and the test fails once.
4. Open the new PNG(s) and verify the render looks correct.
5. Commit the new file(s) and re-run — the tests will pass.

## Regenerating all goldens at once

```bash
# Delete all goldens and re-generate in one pass.
rm app/src/test/snapshots/*.png
./gradlew :app:test --tests '*screenshot*'
# Tests will all fail (first-run write). Review each PNG, then:
git add app/src/test/snapshots/*.png
./gradlew :app:test --tests '*screenshot*'
# All tests pass.
```

## Golden inventory

| File | Test class | What it covers |
|------|------------|----------------|
| `icon_full_canvas.png` | `AppIconScreenshotTest` | Launcher icon on 108×108 dp fern background |
| `icon_circular_mask.png` | `AppIconScreenshotTest` | Icon clipped to circle (circular-icon launchers) |
| `icon_square_mask.png` | `AppIconScreenshotTest` | Icon clipped to rounded square (legacy masks) |
| `theme_light_mode.png` | `ThemeScreenshotTest` | App bar + product row in light colour scheme |
| `theme_dark_mode.png` | `ThemeScreenshotTest` | App bar + product row in dark colour scheme |
| `font_scale_small.png` | `FontScaleScreenshotTest` | Text hierarchy at font scale 0.85× |
| `font_scale_large.png` | `FontScaleScreenshotTest` | Text hierarchy at font scale 1.30× |
| `greyed_row_in_stock.png` | `GreyedRowScreenshotTest` | Product row at full opacity (quantity > 0) |
| `greyed_row_out_of_stock.png` | `GreyedRowScreenshotTest` | Product row at 45% opacity (quantity == 0) |
| `coil_image_present.png` | `CoilImageScreenshotTest` | Detail image slot with a fixed in-memory bitmap |
| `coil_image_absent.png` | `CoilImageScreenshotTest` | Detail without an image URL (no slot rendered) |

## Notes

- All tests use `@Config(sdk = [34])` and `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.
  Goldens generated on a different SDK level or without NATIVE mode will not match.
- The `xxhdpi` density qualifier is fixed so file sizes remain stable across
  development machines with different screen densities.
- Do not commit `*_actual.png` files — these are written on mismatch as a
  diagnostic aid and are excluded by `.gitignore` if you add `*_actual.png`.
