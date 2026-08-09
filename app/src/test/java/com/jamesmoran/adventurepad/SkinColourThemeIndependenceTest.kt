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
            nativeThemeForGameSkin(AdventurePadThemes.Adventure, resolvedSkin),
        )
    }
}
