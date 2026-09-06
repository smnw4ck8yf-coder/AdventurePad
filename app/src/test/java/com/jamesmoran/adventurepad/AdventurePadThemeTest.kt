package com.jamesmoran.adventurepad.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.jamesmoran.adventurepad.ReadingAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventurePadThemeTest {
    private val expectedThemeNames = listOf(
        "Default",
        "Adventure",
        "Alien World",
        "Atlantis",
        "Cartoon Noir",
        "Dark Fantasy",
        "Daylight",
        "Enchanted",
        "Highway",
        "Horror",
        "Mansion",
        "Ocean",
        "Purple Adventure",
        "Sorcerer",
        "Tentacle Lab",
    )

    @Test
    fun defaultThemeIsComplete() = assertThemeIsComplete(AdventurePadThemes.Default)

    @Test
    fun secondBuiltInThemeIsComplete() = assertThemeIsComplete(AdventurePadThemes.Ocean)

    @Test
    fun adventureThemeIsComplete() = assertThemeIsComplete(AdventurePadThemes.Adventure)

    @Test
    fun adventureHasStableIdentityAndResolvesFromId() {
        assertEquals("adventure", AdventurePadThemes.Adventure.id)
        assertEquals("Adventure", AdventurePadThemes.Adventure.displayName)
        assertEquals(AdventurePadThemes.Adventure, AdventurePadThemes.fromId("adventure"))
        assertEquals(AdventurePadThemes.Default, AdventurePadThemes.fromId("unknown"))
    }

    @Test
    fun builtInThemeNamesAndOrderingAreStable() {
        assertEquals(expectedThemeNames, AdventurePadThemes.BuiltIns.map { it.displayName })
        assertEquals(15, AdventurePadThemes.BuiltIns.size)
        assertEquals(
            AdventurePadThemes.BuiltIns,
            AdventurePadThemes.BuiltIns.map { AdventurePadThemes.fromId(it.id) },
        )
        assertEquals(
            AdventurePadThemes.BuiltIns.size,
            AdventurePadThemes.BuiltIns.map { it.id }.toSet().size,
        )
    }

    @Test
    fun everyBuiltInThemeProvidesReadableCoreColourPairs() {
        AdventurePadThemes.BuiltIns.forEach { theme ->
            with(theme.colors) {
                assertContrast(theme, "text/background", textPrimary, background, 4.5f)
                assertContrast(theme, "text/surface", textPrimary, surface, 4.5f)
                assertContrast(theme, "text/raised surface", textPrimary, surfaceRaised, 4.5f)
                assertContrast(theme, "secondary text/background", textSecondary, background, 4.5f)
                assertContrast(theme, "secondary text/surface", textSecondary, surface, 4.5f)
                assertContrast(theme, "button text/button", onPrimary, primary, 4.5f)
                assertNotEquals(surface, surfacePressed)
                assertNotEquals(surface, outline)
            }
        }
    }

    @Test
    fun daylightIsTheOnlyLightBuiltInAndKeepsItsStableId() {
        assertEquals("daylight", AdventurePadThemes.Daylight.id)
        assertEquals("Daylight", AdventurePadThemes.Daylight.displayName)
        assertTrue(AdventurePadThemes.Daylight.isLight)
        assertEquals(
            listOf(AdventurePadThemes.Daylight),
            AdventurePadThemes.BuiltIns.filter { it.isLight },
        )
        assertEquals(AdventurePadThemes.Daylight, AdventurePadThemes.fromId("daylight"))
    }

    @Test
    fun everyBuiltInThemeHasEveryReaderAppearancePalette() {
        AdventurePadThemes.BuiltIns.forEach { theme ->
            assertEquals(ReadingAppearance.entries.toSet(), theme.readerPalettes.keys)
            ReadingAppearance.entries.forEach { appearance ->
                with(theme.readerPalette(appearance)) {
                    assertSpecified(listOf(background, foreground, heading))
                }
            }
        }
    }

    @Test
    fun switchingBuiltInDefinitionsChangesVisualValues() {
        assertNotEquals(AdventurePadThemes.Default.colors, AdventurePadThemes.Ocean.colors)
        assertNotEquals(AdventurePadThemes.Default.components, AdventurePadThemes.Ocean.components)
        assertNotEquals(
            AdventurePadThemes.Default.readerPalette(ReadingAppearance.DARK),
            AdventurePadThemes.Ocean.readerPalette(ReadingAppearance.DARK),
        )
        assertNotEquals(AdventurePadThemes.Default.shapes.medium, AdventurePadThemes.Ocean.shapes.medium)

        listOf(AdventurePadThemes.Default, AdventurePadThemes.Ocean).forEach { existingTheme ->
            assertNotEquals(existingTheme.colors, AdventurePadThemes.Adventure.colors)
            assertNotEquals(existingTheme.components, AdventurePadThemes.Adventure.components)
            ReadingAppearance.entries.forEach { appearance ->
                assertNotEquals(
                    existingTheme.readerPalette(appearance),
                    AdventurePadThemes.Adventure.readerPalette(appearance),
                )
            }
            assertNotEquals(existingTheme.shapes.medium, AdventurePadThemes.Adventure.shapes.medium)
        }
    }

    private fun assertThemeIsComplete(theme: AdventurePadThemeDefinition) {
        assertTrue(theme.id.isNotBlank())
        assertTrue(theme.displayName.isNotBlank())
        with(theme.colors) {
            assertSpecified(listOf(
                background, surface, surfaceRaised, surfacePressed, outline, outlineStrong,
                primary, onPrimary, textPrimary, textSecondary, connected, disconnected,
            ))
        }
        with(theme.components) {
            assertTrue(subtleBorderWidth.value > 0f)
            assertSpecified(listOf(
                launcherAccent, launcherAccentDark, launcherContent, launcherCard, launcherInk,
                trackpadBackground, topDisplayBackground, topCursor, topCursorOutline, trackpadMarker,
                trackpadMarkerOutline, trackpadOverlayTint, trackpadOverlaySeparator,
                mirrorBackdrop, cropOverlay, cropHandle,
                searchHighlight, onSearchHighlight,
            ))
        }
        assertEquals(ReadingAppearance.entries.size, theme.readerPalettes.size)
    }

    private fun assertSpecified(colors: List<Color>) {
        colors.forEach { color -> assertNotEquals(Color.Unspecified, color) }
    }

    private fun assertContrast(
        theme: AdventurePadThemeDefinition,
        role: String,
        foreground: Color,
        background: Color,
        minimum: Float,
    ) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("${theme.displayName} $role contrast was $ratio", ratio >= minimum)
    }
}
