package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinSurfaceContractTest {
    @Test
    fun gameplayBackgroundTracksActualSplitVisibility() {
        assertArrayEquals(
            arrayOf(SkinSlots.BOTTOM_TRACKPAD_BACKGROUND, SkinSlots.LEGACY_BOTTOM_BACKGROUND),
            gameplayBackgroundCandidates(splitViewVisible = false),
        )
        assertArrayEquals(
            arrayOf(SkinSlots.BOTTOM_SPLIT_BACKGROUND, SkinSlots.LEGACY_BOTTOM_BACKGROUND),
            gameplayBackgroundCandidates(splitViewVisible = true),
        )
    }

    @Test
    fun companionFamilySelectsOnlyItsCurrentScreenSurface() {
        assertArrayEquals(
            arrayOf(SkinSlots.COMPANION_BACKGROUND),
            companionBackgroundCandidates(CompanionSection.HOME),
        )
        assertArrayEquals(
            arrayOf(SkinSlots.NOTES_BACKGROUND),
            companionBackgroundCandidates(CompanionSection.NOTES),
        )
        assertArrayEquals(
            arrayOf(SkinSlots.WALKTHROUGH_BACKGROUND),
            companionBackgroundCandidates(CompanionSection.WALKTHROUGH),
        )
    }

    @Test
    fun settingsHasNoGameSkinSurface() {
        assertArrayEquals(
            emptyArray<String>(),
            lowerPageBackgroundCandidates(
                LowerScreenPage.SETTINGS,
                CompanionSection.HOME,
                splitViewVisible = false,
            ),
        )
    }

    @Test
    fun exactCompanionNotesAndWalkthroughArtworkCanBecomeImmersive() {
        assertEquals(
            FunctionalPaneTreatment.IMMERSIVE,
            functionalPaneTreatment(
                InterfaceStyle.IMMERSIVE, LowerScreenPage.COMPANION, CompanionSection.HOME, true,
            ),
        )
        assertEquals(
            FunctionalPaneTreatment.IMMERSIVE,
            functionalPaneTreatment(
                InterfaceStyle.IMMERSIVE, LowerScreenPage.COMPANION, CompanionSection.NOTES, true,
            ),
        )
        assertEquals(
            FunctionalPaneTreatment.IMMERSIVE,
            functionalPaneTreatment(
                InterfaceStyle.IMMERSIVE, LowerScreenPage.COMPANION, CompanionSection.WALKTHROUGH, true,
            ),
        )
        listOf(
            functionalPaneTreatment(InterfaceStyle.STANDARD, LowerScreenPage.COMPANION, CompanionSection.NOTES, true),
            functionalPaneTreatment(InterfaceStyle.IMMERSIVE, LowerScreenPage.COMPANION, CompanionSection.NOTES, false),
            functionalPaneTreatment(InterfaceStyle.IMMERSIVE, LowerScreenPage.SETTINGS, CompanionSection.NOTES, true),
        ).forEach { assertEquals(FunctionalPaneTreatment.OPAQUE, it) }
    }

    @Test
    fun immersiveSurfacePolicyKeepsSettingsNative() {
        assertTrue(isImmersiveGameSurface(LowerScreenPage.GAMEPLAY, CompanionSection.HOME))
        assertTrue(isImmersiveGameSurface(LowerScreenPage.COMPANION, CompanionSection.HOME))
        assertTrue(isImmersiveGameSurface(LowerScreenPage.COMPANION, CompanionSection.NOTES))
        assertTrue(isImmersiveGameSurface(LowerScreenPage.COMPANION, CompanionSection.WALKTHROUGH))
        assertFalse(isImmersiveGameSurface(LowerScreenPage.SETTINGS, CompanionSection.HOME))
    }

    @Test
    fun companionFamilyUsesAReferenceSizedSafeInset() {
        assertEquals(16, COMPANION_SAFE_CONTENT_INSET_DP)
        // The documented 2 px/dp, 1240x1080 reference display leaves 32 px per edge.
        assertEquals(1176, 1240 - (COMPANION_SAFE_CONTENT_INSET_DP * 2 * 2))
        assertEquals(1016, 1080 - (COMPANION_SAFE_CONTENT_INSET_DP * 2 * 2))
    }

    @Test
    fun preActionAssetSkinKeepsItsBackgroundWithoutInventingNewButtonArtwork() {
        val root = kotlin.io.path.createTempDirectory("skin-surface-contract").toFile()
        root.resolve("companion.png").writeBytes(byteArrayOf(1))
        val companionAsset = SkinAsset(
            slot = SkinSlots.COMPANION_BACKGROUND,
            path = "companion.png",
            scale = SkinScaleMode.COVER,
            sha256 = "unused",
        )
        val installed = InstalledSkin(
            manifest = SkinManifest(
                formatVersion = SKIN_FORMAT_VERSION,
                id = "test.missing-notes",
                name = "Missing notes",
                author = "Test",
                packageVersion = "1",
                minimumAdventurePadVersionCode = 1,
                features = emptySet(),
                assets = mapOf(SkinSlots.COMPANION_BACKGROUND to companionAsset),
                colors = emptyMap(),
                metrics = emptyMap(),
                extensions = emptyMap(),
            ),
            root = root,
        )
        val resolved = ResolvedSkin(installed, AdventurePadThemes.Default)

        assertEquals(
            SkinSlots.COMPANION_BACKGROUND,
            resolved.resolveAssetSlot(*companionBackgroundCandidates(CompanionSection.HOME)),
        )
        assertNull(resolved.resolveAssetSlot(*companionBackgroundCandidates(CompanionSection.NOTES)))
        assertNull(resolved.resolveAssetSlot(*SkinnableButton.NOTES.artworkCandidates(pressed = false)))
        assertNull(resolved.resolveAssetSlot(*SkinnableButton.NOTES.artworkCandidates(pressed = true)))
        assertNull(resolved.resolveAssetSlot(*SkinnableButton.WALKTHROUGH.artworkCandidates(pressed = false)))
        assertNull(resolved.resolveAssetSlot(*SkinnableButton.WALKTHROUGH.artworkCandidates(pressed = true)))
        root.deleteRecursively()
    }

    @Test
    fun pressedButtonsPreferPressedArtThenNormalAndLegacyFallbacks() {
        assertArrayEquals(
            arrayOf(
                SkinSlots.BUTTON_LMB_PRESSED,
                SkinSlots.BUTTON_LMB_NORMAL,
                SkinSlots.LEGACY_TRACKPAD_BUTTON_LEFT,
            ),
            SkinnableButton.LMB.artworkCandidates(pressed = true),
        )
        assertArrayEquals(
            arrayOf(SkinSlots.BUTTON_COMPANION_PRESSED, SkinSlots.BUTTON_COMPANION_NORMAL),
            SkinnableButton.COMPANION.artworkCandidates(pressed = true),
        )
        assertArrayEquals(
            arrayOf(SkinSlots.BUTTON_NOTES_PRESSED, SkinSlots.BUTTON_NOTES_NORMAL),
            SkinnableButton.NOTES.artworkCandidates(pressed = true),
        )
        assertArrayEquals(
            arrayOf(SkinSlots.BUTTON_WALKTHROUGH_PRESSED, SkinSlots.BUTTON_WALKTHROUGH_NORMAL),
            SkinnableButton.WALKTHROUGH.artworkCandidates(pressed = true),
        )
    }

    @Test
    fun authoritativeV1SlotsContainNewContractButNotLegacyAliases() {
        assertTrue(SkinSlots.BOTTOM_SPLIT_BACKGROUND in SkinSlots.supportedV1)
        assertTrue(SkinSlots.NOTES_BACKGROUND in SkinSlots.supportedV1)
        assertTrue(SkinSlots.BUTTON_SETTINGS_PRESSED in SkinSlots.supportedV1)
        assertTrue(SkinSlots.BUTTON_NOTES_NORMAL in SkinSlots.supportedV1)
        assertTrue(SkinSlots.BUTTON_NOTES_PRESSED in SkinSlots.supportedV1)
        assertTrue(SkinSlots.BUTTON_WALKTHROUGH_NORMAL in SkinSlots.supportedV1)
        assertTrue(SkinSlots.BUTTON_WALKTHROUGH_PRESSED in SkinSlots.supportedV1)
        assertFalse(SkinSlots.LEGACY_BOTTOM_BACKGROUND in SkinSlots.supportedV1)
        assertFalse(SkinSlots.LEGACY_TRACKPAD_BUTTON_LEFT in SkinSlots.supportedV1)
    }
}
