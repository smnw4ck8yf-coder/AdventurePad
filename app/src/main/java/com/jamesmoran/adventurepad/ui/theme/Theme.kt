package com.jamesmoran.adventurepad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jamesmoran.adventurepad.ReadingAppearance

internal data class AdventurePadColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfacePressed: Color,
    val outline: Color,
    val outlineStrong: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val connected: Color,
    val disconnected: Color,
)

internal data class AdventurePadComponentStyles(
    val subtleBorderWidth: Dp,
    val trackpadBackground: Color,
    val topDisplayBackground: Color,
    val topCursor: Color,
    val topCursorOutline: Color,
    val trackpadMarker: Color,
    val trackpadMarkerOutline: Color,
    val trackpadOverlayTint: Color,
    val trackpadOverlaySeparator: Color,
    val mirrorBackdrop: Color,
    val cropOverlay: Color,
    val cropHandle: Color,
    val searchHighlight: Color,
    val onSearchHighlight: Color,
)

internal data class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val heading: Color,
)

internal data class AdventurePadThemeDefinition(
    val id: String,
    val displayName: String,
    val colors: AdventurePadColors,
    val typography: Typography,
    val shapes: Shapes,
    val components: AdventurePadComponentStyles,
    val readerPalettes: Map<ReadingAppearance, ReaderPalette>,
) {
    fun readerPalette(appearance: ReadingAppearance): ReaderPalette =
        checkNotNull(readerPalettes[appearance]) {
            "Theme '$id' has no reader palette for $appearance"
        }
}

private fun adventurePadShapes(
    smallRadius: Int,
    mediumRadius: Int,
    largeRadius: Int,
) = Shapes(
    extraSmall = RoundedCornerShape(smallRadius.dp),
    small = RoundedCornerShape(smallRadius.dp),
    medium = RoundedCornerShape(mediumRadius.dp),
    large = RoundedCornerShape(largeRadius.dp),
    extraLarge = RoundedCornerShape(largeRadius.dp),
)

internal object AdventurePadThemes {
    val Default = AdventurePadThemeDefinition(
        id = "default",
        displayName = "Default",
        colors = AdventurePadColors(
            background = Color(0xFF111417),
            surface = Color(0xFF1A1F24),
            surfaceRaised = Color(0xFF22282E),
            surfacePressed = Color(0xFF303841),
            outline = Color(0xFF46515B),
            outlineStrong = Color(0xFF64717D),
            primary = Color(0xFFD8B86A),
            onPrimary = Color(0xFF211B0D),
            textPrimary = Color(0xFFF2F3F5),
            textSecondary = Color(0xFFADB5BD),
            connected = Color(0xFF72C08A),
            disconnected = Color(0xFFD08080),
        ),
        typography = Typography,
        shapes = adventurePadShapes(8, 12, 16),
        components = AdventurePadComponentStyles(
            subtleBorderWidth = 1.dp,
            trackpadBackground = Color(0xFF1A1F24),
            topDisplayBackground = Color(0xFF102A43),
            topCursor = Color(0xFFFFD166),
            topCursorOutline = Color.Black,
            trackpadMarker = Color(0xFFD9DDE2),
            trackpadMarkerOutline = Color.White,
            trackpadOverlayTint = Color(0xFF22282E).copy(alpha = 0.58f),
            trackpadOverlaySeparator = Color(0xFF46515B).copy(alpha = 0.72f),
            mirrorBackdrop = Color.Black,
            cropOverlay = Color.Black.copy(alpha = 0.25f),
            cropHandle = Color.Black,
            searchHighlight = Color(0xFF71D7E5),
            onSearchHighlight = Color(0xFF102126),
        ),
        readerPalettes = defaultReaderPalettes(),
    )

    val Ocean = AdventurePadThemeDefinition(
        id = "ocean",
        displayName = "Ocean",
        colors = AdventurePadColors(
            background = Color(0xFF071A24),
            surface = Color(0xFF0C2835),
            surfaceRaised = Color(0xFF123848),
            surfacePressed = Color(0xFF1A4D60),
            outline = Color(0xFF367083),
            outlineStrong = Color(0xFF5C94A5),
            primary = Color(0xFF62D6D1),
            onPrimary = Color(0xFF00201F),
            textPrimary = Color(0xFFE8F7F8),
            textSecondary = Color(0xFFA5C8CE),
            connected = Color(0xFF72D6A0),
            disconnected = Color(0xFFFF9B91),
        ),
        typography = Typography,
        shapes = adventurePadShapes(4, 8, 12),
        components = AdventurePadComponentStyles(
            subtleBorderWidth = 1.dp,
            trackpadBackground = Color(0xFF0C2835),
            topDisplayBackground = Color(0xFF073642),
            topCursor = Color(0xFF62D6D1),
            topCursorOutline = Color(0xFF001419),
            trackpadMarker = Color(0xFF62D6D1),
            trackpadMarkerOutline = Color(0xFFE8F7F8),
            trackpadOverlayTint = Color(0xFF123848).copy(alpha = 0.62f),
            trackpadOverlaySeparator = Color(0xFF5C94A5).copy(alpha = 0.78f),
            mirrorBackdrop = Color(0xFF02090D),
            cropOverlay = Color(0xFF001419).copy(alpha = 0.35f),
            cropHandle = Color(0xFF001419),
            searchHighlight = Color(0xFF62D6D1),
            onSearchHighlight = Color(0xFF00201F),
        ),
        readerPalettes = oceanReaderPalettes(),
    )

    val Adventure = AdventurePadThemeDefinition(
        id = "adventure",
        displayName = "Adventure",
        colors = AdventurePadColors(
            background = Color(0xFF17110D),
            surface = Color(0xFF2A211A),
            surfaceRaised = Color(0xFF4A3828),
            surfacePressed = Color(0xFF79562E),
            outline = Color(0xFFD6B98A),
            outlineStrong = Color(0xFFD6B98A),
            primary = Color(0xFFC99548),
            onPrimary = Color(0xFF17110D),
            textPrimary = Color(0xFFFFF7E8),
            textSecondary = Color(0xFFCDBA9C),
            connected = Color(0xFF72C08A),
            disconnected = Color(0xFFD08080),
        ),
        typography = Typography,
        shapes = adventurePadShapes(6, 10, 14),
        components = AdventurePadComponentStyles(
            subtleBorderWidth = 1.dp,
            trackpadBackground = Color(0xFF3A3532),
            topDisplayBackground = Color(0xFF2A211A),
            topCursor = Color(0xFFC99548),
            topCursorOutline = Color(0xFF17110D),
            trackpadMarker = Color(0xFFC99548),
            trackpadMarkerOutline = Color(0xFFFFF7E8),
            trackpadOverlayTint = Color(0xFF4A3828).copy(alpha = 0.62f),
            trackpadOverlaySeparator = Color(0xFFD6B98A).copy(alpha = 0.78f),
            mirrorBackdrop = Color(0xFF17110D),
            cropOverlay = Color(0xFF17110D).copy(alpha = 0.35f),
            cropHandle = Color(0xFF17110D),
            searchHighlight = Color(0xFFC99548),
            onSearchHighlight = Color(0xFF17110D),
        ),
        readerPalettes = adventureReaderPalettes(),
    )

    val BuiltIns = listOf(Default, Ocean, Adventure)

    fun fromId(id: String?): AdventurePadThemeDefinition =
        BuiltIns.firstOrNull { theme -> theme.id == id } ?: Default
}

private fun defaultReaderPalettes() = mapOf(
    ReadingAppearance.DARK to ReaderPalette(Color(0xFF111417), Color(0xFFF2F3F5), Color(0xFFD8B86A)),
    ReadingAppearance.BLACK to ReaderPalette(Color.Black, Color(0xFFF5F5F5), Color(0xFFE5C873)),
    ReadingAppearance.DARK_GREY to ReaderPalette(Color(0xFF282B2E), Color(0xFFF4F4F2), Color(0xFFE5C873)),
    ReadingAppearance.LIGHT to ReaderPalette(Color(0xFFF5F3EE), Color(0xFF1E1E1B), Color(0xFF684D08)),
    ReadingAppearance.BEIGE to ReaderPalette(Color(0xFFF2E5C4), Color(0xFF181613), Color(0xFF604407)),
    ReadingAppearance.TAN to ReaderPalette(Color(0xFFE6D0A9), Color(0xFF17130F), Color(0xFF563B0B)),
    ReadingAppearance.WARM to ReaderPalette(Color(0xFFF1E4C9), Color(0xFF3C3024), Color(0xFF604407)),
)

private fun oceanReaderPalettes() = mapOf(
    ReadingAppearance.DARK to ReaderPalette(Color(0xFF071A24), Color(0xFFE8F7F8), Color(0xFF62D6D1)),
    ReadingAppearance.BLACK to ReaderPalette(Color(0xFF010608), Color(0xFFE8F7F8), Color(0xFF62D6D1)),
    ReadingAppearance.DARK_GREY to ReaderPalette(Color(0xFF17303A), Color(0xFFE8F7F8), Color(0xFF75DED9)),
    ReadingAppearance.LIGHT to ReaderPalette(Color(0xFFEAF6F5), Color(0xFF102A32), Color(0xFF006C70)),
    ReadingAppearance.BEIGE to ReaderPalette(Color(0xFFE3EFE7), Color(0xFF142822), Color(0xFF176B64)),
    ReadingAppearance.TAN to ReaderPalette(Color(0xFFCFE3DA), Color(0xFF102923), Color(0xFF12645F)),
    ReadingAppearance.WARM to ReaderPalette(Color(0xFFE6EEE2), Color(0xFF25332D), Color(0xFF176B64)),
)

private fun adventureReaderPalettes() = mapOf(
    ReadingAppearance.DARK to ReaderPalette(Color(0xFF241810), Color(0xFFFFF1DC), Color(0xFFC99548)),
    ReadingAppearance.BLACK to ReaderPalette(Color(0xFF120C08), Color(0xFFFFF7E8), Color(0xFFD6A75B)),
    ReadingAppearance.DARK_GREY to ReaderPalette(Color(0xFF352A24), Color(0xFFFFF1DC), Color(0xFFD6A75B)),
    ReadingAppearance.LIGHT to ReaderPalette(Color(0xFFFFF4D6), Color(0xFF2B1B11), Color(0xFF79562E)),
    ReadingAppearance.BEIGE to ReaderPalette(Color(0xFFF1D9AC), Color(0xFF2A190F), Color(0xFF70491F)),
    ReadingAppearance.TAN to ReaderPalette(Color(0xFFDDBA82), Color(0xFF24150D), Color(0xFF654019)),
    ReadingAppearance.WARM to ReaderPalette(Color(0xFFF3C98B), Color(0xFF352015), Color(0xFF79562E)),
)

private val LocalAdventurePadTheme = staticCompositionLocalOf { AdventurePadThemes.Default }

internal object AdventurePadThemeTokens {
    val current: AdventurePadThemeDefinition
        @Composable get() = LocalAdventurePadTheme.current
    val colors: AdventurePadColors
        @Composable get() = current.colors
    val shapes: Shapes
        @Composable get() = current.shapes
    val components: AdventurePadComponentStyles
        @Composable get() = current.components
}

@Composable
internal fun AdventurePadTheme(
    theme: AdventurePadThemeDefinition = AdventurePadThemes.Default,
    content: @Composable () -> Unit,
) {
    val colors = theme.colors
    val colorScheme = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        secondary = colors.textSecondary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.outline,
    )
    CompositionLocalProvider(LocalAdventurePadTheme provides theme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = theme.typography,
            shapes = theme.shapes,
            content = content,
        )
    }
}
