package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinContextResolverTest {
    @Test
    fun launcherAndGameplaySelectionsAreIndependent() {
        val assignments = mapOf("monkey" to "org.example.monkey")
        assertEquals(
            BUILTIN_ADVENTURE_SKIN_ID,
            resolveSkinIdForContext(SkinContext.LAUNCHER, "monkey", assignments),
        )
        assertEquals(
            "org.example.monkey",
            resolveSkinIdForContext(SkinContext.GAMEPLAY, "monkey", assignments),
        )
    }

    @Test
    fun gameplayWithoutAssignmentNeverFallsBackToLauncherSkin() {
        assertEquals(
            BUILTIN_DEFAULT_SKIN_ID,
            resolveSkinIdForContext(SkinContext.GAMEPLAY, "loom", emptyMap()),
        )
        assertEquals(
            BUILTIN_DEFAULT_SKIN_ID,
            resolveSkinIdForContext(SkinContext.ADVANCED_SCUMMVM, null, emptyMap()),
        )
    }

    @Test
    fun oceanResolvesAsAnAssignedGameplaySkin() {
        assertEquals(
            BUILTIN_OCEAN_SKIN_ID,
            resolveSkinIdForContext(
                SkinContext.GAMEPLAY,
                "monkey",
                mapOf("monkey" to BUILTIN_OCEAN_SKIN_ID),
            ),
        )
    }

    @Test
    fun adventureLauncherSkinCannotLeakThroughALegacyGameplayAssignment() {
        assertEquals(
            BUILTIN_DEFAULT_SKIN_ID,
            resolveSkinIdForContext(
                SkinContext.GAMEPLAY,
                "monkey",
                mapOf("monkey" to BUILTIN_ADVENTURE_SKIN_ID),
            ),
        )
    }

    @Test
    fun colourThemesAndGameSkinsRemainSeparateChoices() {
        val choices = gameplaySkinChoices(builtInSkins()).map { it.manifest.id }

        assertFalse(BUILTIN_DEFAULT_SKIN_ID in choices)
        assertFalse(BUILTIN_OCEAN_SKIN_ID in choices)
        assertFalse(BUILTIN_ADVENTURE_SKIN_ID in choices)
        assertTrue(AdventurePadThemes.Adventure in AdventurePadThemes.BuiltIns)
        assertEquals("adventure", AdventurePadThemes.Adventure.id)
        assertEquals(BUILTIN_ADVENTURE_SKIN_ID, builtInSkinIdForTheme(AdventurePadThemes.Adventure))
    }

    @Test
    fun liveLowerActivityFollowsGeometryBetweenLauncherAndGameplay() {
        assertEquals(
            SkinContext.GAMEPLAY,
            lowerSkinContextForTarget(SkinContext.LAUNCHER, "monkey"),
        )
        assertEquals(
            SkinContext.LAUNCHER,
            lowerSkinContextForTarget(SkinContext.GAMEPLAY, ""),
        )
        assertEquals(
            SkinContext.ADVANCED_SCUMMVM,
            lowerSkinContextForTarget(SkinContext.ADVANCED_SCUMMVM, "monkey"),
        )
    }
}
