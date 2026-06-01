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
