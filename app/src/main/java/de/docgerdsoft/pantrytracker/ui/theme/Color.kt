package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.ui.graphics.Color

/** Pantry/produce-evocative seed colour — the single Fern accent of the
 *  DocGerdSoft "one accent per product" identity. Used as the light-mode
 *  `primary` and as the dark-mode `inversePrimary`. The full light/dark M3
 *  tonal expansion seeded from this value lives inline in [PantryTrackerTheme]
 *  (`lightColorScheme(...)` / `darkColorScheme(...)` in Theme.kt) — Fern is no
 *  longer just a primary-only override over the M3 Baseline. */
val Fern: Color = Color(0xFF4F7942)

// Used by ScanButtonsRow in HomeScreen for the two big primary actions.
// These are intentionally outside the M3-derived scheme so the "add" and
// "remove" verbs stay distinguishable across light and dark.
val AddGreen: Color = Color(0xFF2A6A2A)
val RemoveRed: Color = Color(0xFF8A2A2A)

/** Foreground (label + icon) for the [AddGreen] / [RemoveRed] verb-accent fills.
 *  Fixed near-white and mode-independent. The verb containers are themselves
 *  mode-independent, but the M3 default content colour is theme-derived and
 *  reads low-contrast on them: filled buttons default to `onPrimary` (near-black
 *  in DARK mode → ~2:1), and the Scan top app bar defaults to `onSurface`
 *  (near-black in LIGHT mode → ~2:1 on RemoveRed) — both failing AA in at least
 *  one mode. Pure white reads on both fills in light AND dark — ~6.6:1 on
 *  AddGreen, ~8.6:1 on RemoveRed (computed via WCAG relative luminance),
 *  comfortably clearing WCAG 2.1 AA (4.5:1 text, 3:1 icons). See #241. */
val OnVerb: Color = Color.White
