package de.docgerdsoft.pantrytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Md3Primary,
    onPrimary = Md3OnPrimary,
    surface = Md3Surface,
    onSurface = Md3OnSurface,
)

private val DarkColors = darkColorScheme(
    primary = Md3Primary,
    onPrimary = Md3OnPrimary,
    surface = Md3SurfaceDark,
    onSurface = Md3OnSurfaceDark,
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
