package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import java.nio.file.Files
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
    fun launcherAlwaysResolvesToBuiltInAdventurePadOrange() {
        assertEquals(
            BUILTIN_ADVENTURE_SKIN_ID,
            resolveSkinIdForContext(
                SkinContext.LAUNCHER,
                "monkey",
                mapOf("monkey" to "org.example.custom-game-skin"),
            ),
        )
    }

    @Test
    fun gameplayWithoutAssignmentNeverFallsBackToLauncherSkin() {
        assertEquals(
            BUILTIN_DEFAULT_SKIN_ID,
            resolveSkinIdForContext(SkinContext.GAMEPLAY, "loom", emptyMap()),
        )
    }

    @Test
    fun advancedManagementUsesLauncherSkin() {
        assertEquals(
            BUILTIN_ADVENTURE_SKIN_ID,
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
    fun launcherUsesDedicatedOrangeCreamNativeThemeWithoutMakingItSelectable() {
        val launcherSkin = builtInSkins().single {
            it.manifest.id == BUILTIN_ADVENTURE_SKIN_ID
        }

        assertEquals(AdventurePadThemes.Launcher, launcherSkin.builtInTheme)
        assertFalse(AdventurePadThemes.Launcher in AdventurePadThemes.BuiltIns)
        assertEquals(
            AdventurePadThemes.Launcher.components.launcherAccent,
            AdventurePadThemes.Launcher.colors.background,
        )
        assertEquals(
            AdventurePadThemes.Launcher.components.launcherContent,
            AdventurePadThemes.Launcher.components.trackpadBackground,
        )
        assertEquals(
            AdventurePadThemes.Launcher.components.launcherInk,
            AdventurePadThemes.Launcher.colors.textPrimary,
        )
    }

    @Test
    fun liveLowerActivityFollowsGeometryBetweenLauncherAndGameplay() {
        assertEquals(
            SkinContext.GAMEPLAY,
            lowerSkinContextForTarget("monkey"),
        )
        assertEquals(
            SkinContext.LAUNCHER,
            lowerSkinContextForTarget(""),
        )
    }

    @Test
    fun legacyAdvancedManagementContextUsesLauncherPresentation() {
        assertEquals(SkinContext.LAUNCHER, SkinContext.ADVANCED_SCUMMVM.asLowerScreenContext())
        assertEquals(
            BUILTIN_ADVENTURE_SKIN_ID,
            resolveSkinIdForContext(SkinContext.ADVANCED_SCUMMVM, null, emptyMap()),
        )
        val resolvedSkin = ResolvedSkin(
            builtInSkins().single { it.manifest.id == BUILTIN_ADVENTURE_SKIN_ID },
            AdventurePadThemes.Launcher,
        )
        assertEquals(
            AdventurePadThemes.Launcher,
            nativeThemeForSkinContext(
                SkinContext.ADVANCED_SCUMMVM,
                AdventurePadThemes.Ocean,
                resolvedSkin,
            ),
        )
    }

    @Test
    fun managementIntentIgnoresTargetCachedFromPreviousGame() {
        assertEquals(
            SkinContext.LAUNCHER,
            lowerSkinContextForIntent(SkinContext.LAUNCHER, "monkey"),
        )
        assertEquals(
            SkinContext.LAUNCHER,
            lowerSkinContextForIntent(SkinContext.ADVANCED_SCUMMVM, "monkey"),
        )
    }

    @Test
    fun gameplayIntentStillWaitsForAnActiveTarget() {
        assertEquals(
            SkinContext.LAUNCHER,
            lowerSkinContextForIntent(SkinContext.GAMEPLAY, ""),
        )
        assertEquals(
            SkinContext.GAMEPLAY,
            lowerSkinContextForIntent(SkinContext.GAMEPLAY, "monkey"),
        )
    }

    @Test
    fun launcherAlwaysUsesCompleteNativeControlsRegardlessOfSavedInterfaceStyle() {
        assertEquals(
            InterfaceStyle.STANDARD,
            effectiveInterfaceStyle(SkinContext.LAUNCHER, InterfaceStyle.IMMERSIVE, true),
        )
    }

    @Test
    fun unskinnedGameplayCannotUseSavedImmersiveStyle() {
        assertEquals(
            InterfaceStyle.STANDARD,
            effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, false),
        )
    }

    @Test
    fun skinnedGameplayAllowsRequestedImmersiveAndStandard() {
        assertEquals(
            InterfaceStyle.IMMERSIVE,
            effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, true),
        )
        assertEquals(
            InterfaceStyle.STANDARD,
            effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.STANDARD, true),
        )
    }

    @Test
    fun removingSkinFromCurrentGameFallsBackImmediately() {
        assertEquals(
            InterfaceStyle.IMMERSIVE,
            effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, true),
        )
        assertEquals(
            InterfaceStyle.STANDARD,
            effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, false),
        )
    }

    @Test
    fun movingFromSkinnedGameAToUnskinnedGameBIsSafe() {
        val gameA = effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, true)
        val gameB = effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, false)

        assertEquals(InterfaceStyle.IMMERSIVE, gameA)
        assertEquals(InterfaceStyle.STANDARD, gameB)
    }

    @Test
    fun movingBackFromUnskinnedGameBToSkinnedGameAAllowsImmersive() {
        val gameB = effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, false)
        val gameA = effectiveInterfaceStyle(SkinContext.GAMEPLAY, InterfaceStyle.IMMERSIVE, true)

        assertEquals(InterfaceStyle.STANDARD, gameB)
        assertEquals(InterfaceStyle.IMMERSIVE, gameA)
    }

    @Test
    fun advancedScummVmNeverUsesGameSkinImmersivePresentation() {
        assertEquals(
            InterfaceStyle.STANDARD,
            effectiveInterfaceStyle(SkinContext.ADVANCED_SCUMMVM, InterfaceStyle.IMMERSIVE, true),
        )
    }

    @Test
    fun applyingAndReapplyingSkinDefaultsRequestedStyleToImmersive() {
        assertEquals(InterfaceStyle.IMMERSIVE, defaultRequestedStyleForSkinSelection("skin.a"))
        assertEquals(null, defaultRequestedStyleForSkinSelection(null))
        assertEquals(InterfaceStyle.IMMERSIVE, defaultRequestedStyleForSkinSelection("skin.a"))
    }

    @Test
    fun immersiveCapabilityRequiresEveryHiddenControlReplacement() {
        val root = Files.createTempDirectory("adventurepad-immersive-skin").toFile()
        try {
            val requiredSlots = listOf(
                SkinSlots.BUTTON_LMB_NORMAL,
                SkinSlots.BUTTON_RMB_NORMAL,
                SkinSlots.BUTTON_COMPANION_NORMAL,
                SkinSlots.BUTTON_SETTINGS_NORMAL,
            )
            requiredSlots.forEach { slot -> root.resolve("$slot.png").writeBytes(byteArrayOf(1)) }
            val assets = requiredSlots.associateWith { slot ->
                SkinAsset(
                    slot = slot,
                    path = "$slot.png",
                    scale = SkinScaleMode.FILL,
                    sha256 = "unused-in-resolved-skin-test",
                )
            }
            val capable = resolvedExternalSkin(root, assets)
            val missingSettingsArtwork = resolvedExternalSkin(
                root,
                assets - SkinSlots.BUTTON_SETTINGS_NORMAL,
            )

            assertTrue(capable.supportsImmersiveGameplayArtwork())
            assertFalse(missingSettingsArtwork.supportsImmersiveGameplayArtwork())
            assertFalse(
                ResolvedSkin(
                    builtInSkins().first { it.manifest.id == BUILTIN_DEFAULT_SKIN_ID },
                    AdventurePadThemes.Default,
                ).supportsImmersiveGameplayArtwork(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun resolvedExternalSkin(
        root: java.io.File,
        assets: Map<String, SkinAsset>,
    ): ResolvedSkin = ResolvedSkin(
        skin = InstalledSkin(
            manifest = SkinManifest(
                formatVersion = SKIN_FORMAT_VERSION,
                id = "org.example.test",
                name = "Test",
                author = "Test",
                packageVersion = "1",
                minimumAdventurePadVersionCode = 1,
                features = setOf("lower-controls"),
                assets = assets,
                colors = emptyMap(),
                metrics = emptyMap(),
                extensions = emptyMap(),
            ),
            root = root,
        ),
        theme = AdventurePadThemes.Default,
    )
}
