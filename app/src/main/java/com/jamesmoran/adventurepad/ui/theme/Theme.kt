package com.jamesmoran.adventurepad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    val launcherAccent: Color,
    val launcherAccentDark: Color,
    val launcherContent: Color,
    val launcherCard: Color,
    val launcherInk: Color,
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
    val isLight: Boolean = false,
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
    private val LauncherOrange = Color(0xFFE97817)
    private val LauncherOrangeDark = Color(0xFF9D430E)
    private val LauncherCream = Color(0xFFFFF0D0)
    private val LauncherCreamRaised = Color(0xFFF5D6A3)
    private val LauncherInk = Color(0xFF3B2415)

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
            launcherAccent = Color(0xFFF47A16),
            launcherAccentDark = Color(0xFFB94D08),
            launcherContent = Color(0xFFFFF3D8),
            launcherCard = Color(0xFFFFE8BE),
            launcherInk = Color(0xFF3B2819),
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
            launcherAccent = Color(0xFF13869A),
            launcherAccentDark = Color(0xFF075466),
            launcherContent = Color(0xFFEAF6F5),
            launcherCard = Color(0xFFD5ECE8),
            launcherInk = Color(0xFF102A32),
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
            launcherAccent = LauncherOrange,
            launcherAccentDark = LauncherOrangeDark,
            launcherContent = LauncherCream,
            launcherCard = LauncherCreamRaised,
            launcherInk = LauncherInk,
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

    val PurpleAdventure = nativeColourTheme(
        id = "purple-adventure",
        displayName = "Purple Adventure",
        background = Color(0xFF100A13),
        surface = Color(0xFF1D1024),
        surfaceRaised = Color(0xFF321842),
        surfacePressed = Color(0xFF542568),
        outline = Color(0xFF75458A),
        outlineStrong = Color(0xFFB260C2),
        primary = Color(0xFFCF78DC),
        onPrimary = Color(0xFF240A29),
        textPrimary = Color(0xFFFFF3DC),
        textSecondary = Color(0xFFD0B5D2),
        connected = Color(0xFF82C99B),
        disconnected = Color(0xFFD98893),
    )

    val TentacleLab = nativeColourTheme(
        id = "tentacle-lab",
        displayName = "Tentacle Lab",
        background = Color(0xFF170B2D),
        surface = Color(0xFF2A1050),
        surfaceRaised = Color(0xFF43206F),
        surfacePressed = Color(0xFF653390),
        outline = Color(0xFF44CAD3),
        outlineStrong = Color(0xFFA5E34B),
        primary = Color(0xFFA9E84F),
        onPrimary = Color(0xFF16220A),
        textPrimary = Color(0xFFF7F2FF),
        textSecondary = Color(0xFFBEEFF0),
        connected = Color(0xFFA9E84F),
        disconnected = Color(0xFFFFA34A),
        highlight = Color(0xFFFFC857),
    )

    val Atlantis = nativeColourTheme(
        id = "atlantis",
        displayName = "Atlantis",
        background = Color(0xFF071C1E),
        surface = Color(0xFF102C2D),
        surfaceRaised = Color(0xFF1C4240),
        surfacePressed = Color(0xFF2B5B56),
        outline = Color(0xFF81765D),
        outlineStrong = Color(0xFFB08D57),
        primary = Color(0xFF66BDB1),
        onPrimary = Color(0xFF071C1E),
        textPrimary = Color(0xFFE8E0CC),
        textSecondary = Color(0xFFB9B29F),
        connected = Color(0xFF72B9A7),
        disconnected = Color(0xFFC57C70),
        trackpad = Color(0xFF0C2527),
        highlight = Color(0xFF73C4B6),
    )

    val Highway = nativeColourTheme(
        id = "highway",
        displayName = "Highway",
        background = Color(0xFF0B0C0D),
        surface = Color(0xFF1B1D20),
        surfaceRaised = Color(0xFF303338),
        surfacePressed = Color(0xFF4A3A3A),
        outline = Color(0xFF5F646A),
        outlineStrong = Color(0xFF9A5550),
        primary = Color(0xFFC56A5D),
        onPrimary = Color(0xFF250C09),
        textPrimary = Color(0xFFE8E5DE),
        textSecondary = Color(0xFFB3B0AA),
        connected = Color(0xFF8CAB78),
        disconnected = Color(0xFFC56A5D),
        highlight = Color(0xFFD29A43),
    )

    val AlienWorld = nativeColourTheme(
        id = "alien-world",
        displayName = "Alien World",
        background = Color(0xFF03090F),
        surface = Color(0xFF071923),
        surfaceRaised = Color(0xFF0C2A34),
        surfacePressed = Color(0xFF12434B),
        outline = Color(0xFF28616B),
        outlineStrong = Color(0xFF44AAB5),
        primary = Color(0xFF67D8D8),
        onPrimary = Color(0xFF002123),
        textPrimary = Color(0xFFE5FBFA),
        textSecondary = Color(0xFF9EC7CB),
        connected = Color(0xFF87DB72),
        disconnected = Color(0xFFD07B7B),
        highlight = Color(0xFF8BEA75),
    )

    val Enchanted = nativeColourTheme(
        id = "enchanted",
        displayName = "Enchanted",
        background = Color(0xFF07091D),
        surface = Color(0xFF101432),
        surfaceRaised = Color(0xFF25234E),
        surfacePressed = Color(0xFF413A69),
        outline = Color(0xFF74769B),
        outlineStrong = Color(0xFFB9B4D5),
        primary = Color(0xFFB8A7E8),
        onPrimary = Color(0xFF17142D),
        textPrimary = Color(0xFFF0F1FA),
        textSecondary = Color(0xFFB9BAD0),
        connected = Color(0xFF84CEB8),
        disconnected = Color(0xFFD58A9C),
        highlight = Color(0xFFC6B8F4),
    )

    val CartoonNoir = nativeColourTheme(
        id = "cartoon-noir",
        displayName = "Cartoon Noir",
        background = Color(0xFF090909),
        surface = Color(0xFF1B1B1A),
        surfaceRaised = Color(0xFF343433),
        surfacePressed = Color(0xFF50504E),
        outline = Color(0xFFB7B4AD),
        outlineStrong = Color(0xFFE8E3D8),
        primary = Color(0xFFD8D4CB),
        onPrimary = Color(0xFF161616),
        textPrimary = Color(0xFFF3EFE5),
        textSecondary = Color(0xFFBBB7AE),
        connected = Color(0xFFD8D4CB),
        disconnected = Color(0xFF8F8C86),
        highlight = Color(0xFFE8E3D8),
    )

    val Sorcerer = nativeColourTheme(
        id = "sorcerer",
        displayName = "Sorcerer",
        background = Color(0xFF0E190F),
        surface = Color(0xFF192A1A),
        surfaceRaised = Color(0xFF2D4327),
        surfacePressed = Color(0xFF465B32),
        outline = Color(0xFF6F8250),
        outlineStrong = Color(0xFFB49855),
        primary = Color(0xFFD1B767),
        onPrimary = Color(0xFF211B08),
        textPrimary = Color(0xFFF3EACF),
        textSecondary = Color(0xFFC8C2A0),
        connected = Color(0xFF8FCC7C),
        disconnected = Color(0xFFC77B6F),
        trackpad = Color(0xFF21361E),
        highlight = Color(0xFF9A6CB2),
    )

    val Mansion = nativeColourTheme(
        id = "mansion",
        displayName = "Mansion",
        background = Color(0xFF020309),
        surface = Color(0xFF071127),
        surfaceRaised = Color(0xFF0B2540),
        surfacePressed = Color(0xFF163D58),
        outline = Color(0xFF24B8C8),
        outlineStrong = Color(0xFF55E0E5),
        primary = Color(0xFF65EC72),
        onPrimary = Color(0xFF06220B),
        textPrimary = Color(0xFFDDFEFF),
        textSecondary = Color(0xFF8DDDE2),
        connected = Color(0xFF65EC72),
        disconnected = Color(0xFFD76464),
        highlight = Color(0xFF9B62CB),
    )

    val DarkFantasy = nativeColourTheme(
        id = "dark-fantasy",
        displayName = "Dark Fantasy",
        background = Color(0xFF080609),
        surface = Color(0xFF180A10),
        surfaceRaised = Color(0xFF30101B),
        surfacePressed = Color(0xFF4A1724),
        outline = Color(0xFF67414A),
        outlineStrong = Color(0xFFA88A50),
        primary = Color(0xFFB09252),
        onPrimary = Color(0xFF171005),
        textPrimary = Color(0xFFEEE2CF),
        textSecondary = Color(0xFFC2A8AB),
        connected = Color(0xFF7FB28C),
        disconnected = Color(0xFFC57076),
        trackpad = Color(0xFF12080D),
        highlight = Color(0xFF8E3044),
    )

    val Horror = nativeColourTheme(
        id = "horror",
        displayName = "Horror",
        background = Color(0xFF070607),
        surface = Color(0xFF1B0B0E),
        surfaceRaised = Color(0xFF351218),
        surfacePressed = Color(0xFF541A22),
        outline = Color(0xFF744047),
        outlineStrong = Color(0xFFA75C58),
        primary = Color(0xFFA84848),
        onPrimary = Color(0xFFFFF1D2),
        textPrimary = Color(0xFFF1E7CF),
        textSecondary = Color(0xFFC5B9A2),
        connected = Color(0xFF829C70),
        disconnected = Color(0xFFC05A52),
        trackpad = Color(0xFF13090B),
        highlight = Color(0xFFB64D4B),
    )

    val Daylight = nativeColourTheme(
        id = "daylight",
        displayName = "Daylight",
        isLight = true,
        background = Color(0xFFF1F0EC),
        surface = Color(0xFFE2E0DA),
        surfaceRaised = Color(0xFFD5D2CA),
        surfacePressed = Color(0xFFB8B4AA),
        outline = Color(0xFF73716B),
        outlineStrong = Color(0xFF484640),
        primary = Color(0xFFC0641C),
        onPrimary = Color(0xFF090604),
        textPrimary = Color(0xFF202020),
        textSecondary = Color(0xFF555451),
        connected = Color(0xFF267044),
        disconnected = Color(0xFFA13B32),
        trackpad = Color(0xFFE7E5DF),
        highlight = Color(0xFFC9681D),
        readerPalettes = daylightReaderPalettes(),
    )

    /** Native launcher surfaces shared by the upper library and persistent lower controls. */
    val Launcher = Adventure.copy(
        id = "adventure-launcher",
        displayName = "AdventurePad Launcher",
        colors = Adventure.colors.copy(
            background = LauncherOrange,
            surface = LauncherCream,
            surfaceRaised = LauncherCreamRaised,
            surfacePressed = LauncherCreamRaised,
            outline = LauncherOrangeDark,
            outlineStrong = LauncherInk,
            primary = LauncherOrange,
            onPrimary = LauncherInk,
            textPrimary = LauncherInk,
            textSecondary = LauncherInk,
        ),
        components = Adventure.components.copy(
            trackpadBackground = LauncherCream,
            trackpadMarker = LauncherOrange,
            trackpadMarkerOutline = LauncherInk,
            trackpadOverlayTint = LauncherCreamRaised.copy(alpha = 0.62f),
            trackpadOverlaySeparator = LauncherOrangeDark.copy(alpha = 0.78f),
        ),
    )

    val BuiltIns = listOf(
        Default,
        Adventure,
        AlienWorld,
        Atlantis,
        CartoonNoir,
        DarkFantasy,
        Daylight,
        Enchanted,
        Highway,
        Horror,
        Mansion,
        Ocean,
        PurpleAdventure,
        Sorcerer,
        TentacleLab,
    )

    fun fromId(id: String?): AdventurePadThemeDefinition =
        BuiltIns.firstOrNull { theme -> theme.id == id } ?: Default
}

private fun nativeColourTheme(
    id: String,
    displayName: String,
    isLight: Boolean = false,
    background: Color,
    surface: Color,
    surfaceRaised: Color,
    surfacePressed: Color,
    outline: Color,
    outlineStrong: Color,
    primary: Color,
    onPrimary: Color,
    textPrimary: Color,
    textSecondary: Color,
    connected: Color,
    disconnected: Color,
    trackpad: Color = surface,
    highlight: Color = primary,
    readerPalettes: Map<ReadingAppearance, ReaderPalette> = nativeReaderPalettes(
        background = background,
        surfaceRaised = surfaceRaised,
        primary = primary,
        foreground = textPrimary,
        lightHeading = outlineStrong,
    ),
) = AdventurePadThemeDefinition(
    id = id,
    displayName = displayName,
    isLight = isLight,
    colors = AdventurePadColors(
        background = background,
        surface = surface,
        surfaceRaised = surfaceRaised,
        surfacePressed = surfacePressed,
        outline = outline,
        outlineStrong = outlineStrong,
        primary = primary,
        onPrimary = onPrimary,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        connected = connected,
        disconnected = disconnected,
    ),
    typography = Typography,
    shapes = adventurePadShapes(6, 10, 14),
    components = AdventurePadComponentStyles(
        subtleBorderWidth = 1.dp,
        launcherAccent = primary,
        launcherAccentDark = outlineStrong,
        launcherContent = textPrimary,
        launcherCard = surfaceRaised,
        launcherInk = onPrimary,
        trackpadBackground = trackpad,
        topDisplayBackground = background,
        topCursor = primary,
        topCursorOutline = onPrimary,
        trackpadMarker = primary,
        trackpadMarkerOutline = textPrimary,
        trackpadOverlayTint = surfaceRaised.copy(alpha = 0.62f),
        trackpadOverlaySeparator = outlineStrong.copy(alpha = 0.78f),
        mirrorBackdrop = background,
        cropOverlay = background.copy(alpha = 0.35f),
        cropHandle = onPrimary,
        searchHighlight = highlight,
        onSearchHighlight = onPrimary,
    ),
    readerPalettes = readerPalettes,
)

private fun nativeReaderPalettes(
    background: Color,
    surfaceRaised: Color,
    primary: Color,
    foreground: Color,
    lightHeading: Color,
) = mapOf(
    ReadingAppearance.DARK to ReaderPalette(background, foreground, primary),
    ReadingAppearance.BLACK to ReaderPalette(Color.Black, foreground, primary),
    ReadingAppearance.DARK_GREY to ReaderPalette(surfaceRaised, foreground, primary),
    ReadingAppearance.LIGHT to ReaderPalette(Color(0xFFF5F2EA), Color(0xFF201E1B), lightHeading),
    ReadingAppearance.BEIGE to ReaderPalette(Color(0xFFECE1C9), Color(0xFF211E18), lightHeading),
    ReadingAppearance.TAN to ReaderPalette(Color(0xFFD9C39E), Color(0xFF211B14), lightHeading),
    ReadingAppearance.WARM to ReaderPalette(Color(0xFFE9D8BD), Color(0xFF30271E), lightHeading),
)

private fun daylightReaderPalettes() = mapOf(
    ReadingAppearance.DARK to ReaderPalette(Color(0xFF171717), Color(0xFFF1F0EC), Color(0xFFD7894D)),
    ReadingAppearance.BLACK to ReaderPalette(Color.Black, Color(0xFFF1F0EC), Color(0xFFE09A64)),
    ReadingAppearance.DARK_GREY to ReaderPalette(Color(0xFF30302E), Color(0xFFF1F0EC), Color(0xFFE09A64)),
    ReadingAppearance.LIGHT to ReaderPalette(Color(0xFFF1F0EC), Color(0xFF202020), Color(0xFF8C430F)),
    ReadingAppearance.BEIGE to ReaderPalette(Color(0xFFE9E0CE), Color(0xFF24211C), Color(0xFF85400F)),
    ReadingAppearance.TAN to ReaderPalette(Color(0xFFDCC7A6), Color(0xFF251E17), Color(0xFF75380D)),
    ReadingAppearance.WARM to ReaderPalette(Color(0xFFE8D8BE), Color(0xFF30271E), Color(0xFF85400F)),
)

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
    val colorScheme = if (theme.isLight) {
        lightColorScheme(
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
    } else {
        darkColorScheme(
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
    }
    CompositionLocalProvider(LocalAdventurePadTheme provides theme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = theme.typography,
            shapes = theme.shapes,
            content = content,
        )
    }
}
