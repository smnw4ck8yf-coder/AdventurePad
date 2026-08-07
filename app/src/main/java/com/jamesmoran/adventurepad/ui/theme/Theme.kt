package com.jamesmoran.adventurepad.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AdventurePadDesign.primary,
    onPrimary = AdventurePadDesign.onPrimary,
    secondary = AdventurePadDesign.textSecondary,
    background = AdventurePadDesign.background,
    onBackground = AdventurePadDesign.textPrimary,
    surface = AdventurePadDesign.surface,
    onSurface = AdventurePadDesign.textPrimary,
    surfaceVariant = AdventurePadDesign.surfaceRaised,
    onSurfaceVariant = AdventurePadDesign.textSecondary,
    outline = AdventurePadDesign.outline,
)

@Composable
fun AdventurePadTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = AdventurePadShapes,
        content = content
    )
}
