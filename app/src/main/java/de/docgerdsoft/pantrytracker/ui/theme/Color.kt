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
