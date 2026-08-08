package com.jamesmoran.adventurepad.ui.theme

import androidx.compose.ui.graphics.Color
import com.jamesmoran.adventurepad.ReadingAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventurePadThemeTest {
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
}
