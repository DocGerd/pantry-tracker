# v1.0 User Acceptance Test — sign-off checklist

This is the human gate before v1.0 ships. Walk through every section on
a real device using a **release-signed APK** (not debug — see
[SHIPPING.md](../release/SHIPPING.md)). Tick each box; if any fails, fix
or document as a v1.1 deferral.

The automated `HappyPathUatTest` covers the happy-path regressions in CI;
this checklist covers the things automation can't verify (visual
correctness, device-specific permission behaviour, actual barcode
scanning against real products).

**Tester:** ________________
**Device:** ________________ (model + Android version)
**APK version:** ________________ (versionName + versionCode)
**Date:** ________________

---

## 0. Install

- [ ] APK installs without errors
- [ ] Launcher shows the **three-jars-on-a-shelf icon** on a fern (green) background
- [ ] Icon renders correctly in **both** the home-screen grid and the app drawer
- [ ] On a launcher with circular icons, the icon is **centered and not clipped**

## 1. First launch — empty pantry

- [ ] App opens to Home screen within ~2 seconds
- [ ] Top bar reads "Pantry Tracker"
- [ ] Two big primary buttons visible: **"Scan to Add" (green)** + **"Scan to Remove" (red)**
- [ ] Search field below them is empty + shows "Search" placeholder
- [ ] Empty-pantry state shows:
  - [ ] **"Your pantry is empty"** headline
  - [ ] Subtitle: "Tap Scan to Add or + to start tracking"
  - [ ] **"Scan to Add"** button (filled)
  - [ ] **"Add manually"** button (outlined)
- [ ] Floating action button (+) visible in the bottom-right
- [ ] FAB content description (TalkBack) reads "Add manually"

## 2. Theme + visual sanity

- [ ] Switch device to **dark mode** → app re-renders in dark scheme without restart
- [ ] Primary colour (visible on buttons, top app bar) is fern green in both modes
- [ ] Status bar / nav bar contrast is acceptable (no white-on-white)
- [ ] No clipped text on the smallest font size, no overflow on the largest

## 3. Manual entry path (no camera needed)

- [ ] Tap "Add manually" (the OutlinedButton in EmptyState) → bottom sheet opens
- [ ] Sheet has a **Name** field, **initial quantity** stepper, **Add** + **Cancel** buttons
- [ ] Type "Test Product" → tap Add → sheet closes
- [ ] Home now shows "Test Product ×1" instead of the empty state
- [ ] Search field still says "Search" (placeholder, not the value)

## 4. Camera-permission rationale gate — first scan

(Reset permission first if you've already granted: Settings → Apps → Pantry Tracker → Permissions → Camera → Don't allow.)

- [ ] Tap "Scan to Add" → **rationale dialog** appears titled "Camera access"
- [ ] Body text: "We scan barcodes to find products. Nothing leaves your device."
- [ ] Buttons: **Continue** (filled) + **Cancel** (text)
- [ ] Tap Cancel → returns to Home without opening the system prompt
- [ ] Tap "Scan to Add" again → rationale dialog reappears
- [ ] Tap Continue → **system permission prompt** appears

## 5. Permission flow — SoftDenied

- [ ] At the system prompt, tap **Deny** (NOT "Don't ask again")
- [ ] App shows **"Camera access needed"** screen with body "Pantry Tracker uses the camera to scan barcodes. Nothing leaves your device."
- [ ] **"Try again"** button (filled) + **"Go back"** button (outlined)
- [ ] Tap "Go back" → returns to Home
- [ ] Tap "Scan to Add" → returns directly to **"Camera access needed"** (no rationale dialog this round)
- [ ] Tap **Try again** → system permission prompt reappears
- [ ] Tap **Allow** → camera preview renders

## 6. Permission flow — HardDenied recovery

- [ ] Settings → Apps → Pantry Tracker → Permissions → Camera → **Don't allow** (or revoke + re-deny with "don't ask again" if Android 11+)
- [ ] Return to app → tap "Scan to Add"
- [ ] App shows **"Camera access blocked"** screen with body "Open Settings and allow camera access for Pantry Tracker, then come back."
- [ ] **"Open settings"** (filled) + **"Go back"** (outlined)
- [ ] Tap "Open settings" → **system Settings app opens directly to the Pantry Tracker permission page**
  - [ ] **OEM-fail fallback path** (Xiaomi/MIUI, some Huawei builds may land on a generic settings screen, or the Settings activity may be disabled entirely): if Open settings does NOT reach the permission page, the app must surface a "Couldn't open settings on this device" Toast — not silently fail
- [ ] Toggle Camera to Allow → press device Back to return to the app
- [ ] App **automatically transitions to the camera preview** (no extra tap needed) — this is the M6-caught regression

## 7. Scan to Add — OFF hit

(Need an actual product with a barcode. A bottle of mineral water, a packaged snack, anything with an EAN-13 barcode. The product must already be in Open Food Facts — if you scan something obscure you'll fall through to manual entry, which is also a valid test path.)

- [ ] Tap "Scan to Add" → camera preview visible
- [ ] Top app bar is green ("Scan to Add" mode)
- [ ] Point at the barcode — within ~1 second it auto-detects
- [ ] Bottom sheet appears showing:
  - [ ] Product **name** (from OFF)
  - [ ] **Brand** (if OFF has it)
  - [ ] Product **image** (if OFF has it; falls back gracefully if not)
  - [ ] Quantity stepper showing **1**
  - [ ] **Confirm** + **Cancel** buttons
- [ ] Increase quantity to 3 → tap Confirm
- [ ] Sheet dismisses → camera preview returns to live state
- [ ] Press device Back → Home shows the product with **×3**

## 8. Scan to Add — OFF miss (manual entry fallback)

(Pick an unusual product not in OFF, or temporarily put the phone in airplane mode.)

- [ ] Tap "Scan to Add" → grant if prompted → camera preview
- [ ] Scan the barcode
- [ ] After ~8 seconds (timeout), bottom sheet opens with **manual entry** layout:
  - [ ] Pre-filled with the barcode
  - [ ] Name field is **empty + focused**
  - [ ] Quantity stepper at 1
  - [ ] **Add** + **Cancel** buttons
- [ ] Type "Local Brand Cookies" → tap Add → product is saved
- [ ] Home shows "Local Brand Cookies ×1"

## 9. Search

- [ ] In Home, tap the Search field and type "Test"
- [ ] List filters to only matching rows
- [ ] Type a string that matches nothing (e.g. "zzz")
- [ ] List shows **"No matches for "zzz""** hint
- [ ] **Empty-pantry CTAs do NOT appear** (no "Your pantry is empty", no "Scan to Add" button — those are only for blank-query empty)
- [ ] Clear the search → list returns to full

## 10. Detail screen — rename + stepper

- [ ] Tap any row in Home → Detail screen opens
- [ ] Top app bar shows the product name + a Back arrow
- [ ] Image (if any), name, brand (if any), current quantity, "Updated <relative time>" line all visible
- [ ] Name field is editable; type a new name → tap **Save**
- [ ] Snackbar confirms (or no error appears)
- [ ] Quantity stepper: tap **+** → quantity increases by 1; **−** → decreases; below 0 it clamps at 0
- [ ] Press Back → Home shows the renamed product + new quantity

## 11. Scan to Remove — in-inventory path

- [ ] Tap "Scan to Remove" (red top bar) → permission flow if needed → camera preview
- [ ] Scan a barcode of a product currently in your inventory at quantity > 0
- [ ] Bottom sheet shows the product + quantity stepper clamped to **max = current quantity**
- [ ] Decrease (or accept default 1) → tap Confirm
- [ ] Home shows the row with the **new lower quantity**
- [ ] Repeat until the row hits **0** — the row stays in the list but **greyed at 45% opacity**

## 12. Scan to Remove — not-in-inventory path

- [ ] Scan a barcode for a product NOT in your inventory
- [ ] Bottom sheet shows **"Not in inventory"** message with the barcode and a **"Switch to Add"** button
- [ ] Tap "Switch to Add" → top bar flips to green ("Scan to Add" mode), the same barcode re-resolves through the Add flow (OFF lookup if needed)

## 13. Delete

- [ ] **Long-press** any row in Home → delete confirmation dialog
- [ ] Dialog title: "Delete <product name>?"
- [ ] Body mentions "This removes it from your inventory. Cannot be undone in v1."
- [ ] Tap Cancel → dialog dismisses, row remains
- [ ] Long-press again → tap Delete → row disappears from Home
- [ ] **Long-press a quantity=0 (greyed) row** → confirm dialog appears the same way → tap Delete → row disappears. Verifies the long-press click area + dialog still work on out-of-stock rows (which render at ~45% alpha so the gesture surface could theoretically be confused with the dim).

## 14. Persistence

- [ ] Force-stop the app (Settings → Apps → Pantry Tracker → Force stop)
- [ ] Re-launch → all your products are still there, with the right quantities
- [ ] Reboot the device → re-launch → still there
- [ ] **Configuration change** — open the Detail screen of any product, edit the Name field but do NOT tap Save / lose focus yet. Rotate the device (or change dark/light mode, or system font scale): the app must rebuild the Activity cleanly, the row still exists with its committed state, the nav backstack is preserved. (The typed-but-uncommitted edit is currently NOT preserved — that's a known v1 limitation; this step verifies nothing CRASHES during the rebuild.)
- [ ] (Bonus, only on a dev install you don't mind losing data on:) `adb shell pm clear de.docgerdsoft.pantrytracker` → re-launch → pantry is empty (clean slate)

## 15. Error tone audit

- [ ] During any of the above, if an error appeared (failed scan, failed save, settings deep-link unavailable on this device), the user-facing message starts with **"Couldn't <verb>: ..."** and not a raw stack trace or "java.lang.Exception" string

---

## Sign-off

- [ ] All required boxes above are checked
- [ ] All visual checks (icon, theme, empty state) match the v1.0 spec
- [ ] All permission flows recover correctly without app restart
- [ ] No silent failures observed (every error has a Snackbar / sheet / Toast)

**Signed off by:** ________________
**Date:** ________________
**Decision:** ☐ ready to tag v1.0 ☐ block; deferrals: ________________

---

## Notes for testers

- **"Bonus"** boxes are nice-to-have, not required.
- If a permission flow looks different from what's described above, it might
  be an OEM-specific permission UI (Xiaomi, Huawei, Samsung have their own
  permission flows). Test the *outcome* (permission granted, app recovers
  on resume) rather than the exact dialog wording.
- Tests 8 (OFF miss) and 11 (in-inventory remove) require some pre-existing
  inventory state. The cleanest order is: 0 → 1 → 3 (add one manual) → 4–6
  (permission) → 7 (scan-add OFF hit) → rest in any order.
- If `Test Product` (from test 3) is still in your test pantry after the
  full walkthrough, clear it with `adb shell pm clear ...` before signing
  off the v1.0 release APK.
