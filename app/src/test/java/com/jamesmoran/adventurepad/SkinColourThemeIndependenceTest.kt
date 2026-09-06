package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import org.junit.Assert.assertSame
import org.junit.Test

class SkinColourThemeIndependenceTest {
    @Test
    fun selectedColourThemeRemainsNativeThemeWithExternalGameSkin() {
        val externalSkin = InstalledSkin(
            manifest = SkinManifest(
                formatVersion = SKIN_FORMAT_VERSION,
                id = "org.adventurepad.test.rainbow",
                name = "Rainbow",
                author = "Test",
                packageVersion = "1",
                minimumAdventurePadVersionCode = 1,
                features = emptySet(),
                assets = emptyMap(),
                colors = mapOf("background" to "#111417"),
                metrics = emptyMap(),
                extensions = emptyMap(),
            ),
            root = null,
        )
        val resolvedSkin = ResolvedSkin(externalSkin, AdventurePadThemes.Default)

        assertSame(
            AdventurePadThemes.Adventure,
            nativeThemeForSkinContext(
                SkinContext.GAMEPLAY,
                AdventurePadThemes.Adventure,
                resolvedSkin,
            ),
        )
    }

    @Test
    fun launcherResolvedSkinOwnsItsNativeTheme() {
        val launcherSkin = ResolvedSkin(
            builtInSkins().single { it.manifest.id == BUILTIN_ADVENTURE_SKIN_ID },
            AdventurePadThemes.Launcher,
        )

        assertSame(
            AdventurePadThemes.Launcher,
            nativeThemeForSkinContext(
                SkinContext.LAUNCHER,
                AdventurePadThemes.Default,
                launcherSkin,
            ),
        )
    }

    @Test
    fun unskinnedGameplayDoesNotUseLauncherNativeTheme() {
        val defaultSkin = ResolvedSkin(
            builtInSkins().single { it.manifest.id == BUILTIN_DEFAULT_SKIN_ID },
            AdventurePadThemes.Default,
        )

        assertSame(
            AdventurePadThemes.Adventure,
            nativeThemeForSkinContext(
                SkinContext.GAMEPLAY,
                AdventurePadThemes.Adventure,
                defaultSkin,
            ),
        )
    }
}
