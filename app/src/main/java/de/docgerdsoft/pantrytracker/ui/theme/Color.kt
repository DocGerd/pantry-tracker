package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.ui.graphics.Color

/** Pantry/produce-evocative primary colour. Only the `primary` slot is
 *  overridden in [PantryTrackerTheme]; the rest of the scheme (secondary,
 *  tertiary, surface, error, …) comes from M3's hardcoded Baseline palette,
 *  not from this colour. True seed-derived tonal expansion would require
 *  `dynamicLightColorScheme(context)` (Android 12+, user-wallpaper-driven)
 *  or `material-color-utilities` — neither is wired up. */
val Fern: Color = Color(0xFF4F7942)

// Used by ScanButtonsRow in HomeScreen for the two big primary actions.
// These are intentionally outside the M3-derived scheme so the "add" and
// "remove" verbs stay distinguishable across light and dark.
val AddGreen: Color = Color(0xFF2A6A2A)
val RemoveRed: Color = Color(0xFF8A2A2A)
