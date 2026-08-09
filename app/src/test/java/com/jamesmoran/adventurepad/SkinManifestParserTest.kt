package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkinManifestParserTest {
    @Test
    fun parsesMinimalV1Manifest() {
        val manifest = SkinManifestParser.parse(validManifest())
        assertEquals("org.example.test", manifest.id)
        assertEquals(
            SkinScaleMode.COVER,
            manifest.assets.getValue(SkinSlots.BOTTOM_TRACKPAD_BACKGROUND).scale,
        )
    }

    @Test
    fun rejectsExternalBuiltinNamespace() {
        assertThrows(SkinManifestException::class.java) {
            SkinManifestParser.parse(validManifest().replace("org.example.test", "builtin.fake"))
        }
    }

    @Test
    fun rejectsTraversalAndDuplicateJsonKeys() {
        assertThrows(SkinManifestException::class.java) {
            SkinManifestParser.parse(validManifest().replace("assets/bottom.png", "../bottom.png"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SimpleJson.parse("{\"id\":1,\"id\":2}")
        }
    }

    @Test
    fun panelFrameRequiresNineSlice() {
        val invalid = validManifest().replace(
            "\"${SkinSlots.BOTTOM_TRACKPAD_BACKGROUND}\"",
            "\"${SkinSlots.PANEL_FRAME}\"",
        )
        assertThrows(SkinManifestException::class.java) {
            SkinManifestParser.parse(invalid)
        }
    }

    @Test
    fun acceptsDocumentedCamelCaseNineSlice() {
        val valid = validManifest()
            .replace("\"bottom.trackpad.background\"", "\"panel.frame\"")
            .replace("\"scale\": \"cover\"", "\"scale\": \"nineSlice\"")
            .replace(
                "\"sha256\":",
                "\"sliceInsets\": {\"left\": 1, \"top\": 1, \"right\": 1, \"bottom\": 1}, \"sha256\":",
            )
        assertEquals(SkinScaleMode.NINE_SLICE, SkinManifestParser.parse(valid).assets.getValue(SkinSlots.PANEL_FRAME).scale)
    }

    @Test
    fun rejectsUnsupportedAssetDeclarationFields() {
        val invalid = validManifest().replace(
            "\"path\": \"assets/bottom.png\"",
            "\"path\": \"assets/bottom.png\", \"script\": \"payload.js\"",
        )

        assertThrows(SkinManifestException::class.java) {
            SkinManifestParser.parse(invalid)
        }
    }

    private fun validManifest() = """
        {
          "formatVersion": 1,
          "id": "org.example.test",
          "name": "Test Skin",
          "author": "Test Artist",
          "packageVersion": "1.0.0",
          "minimumAdventurePadVersionCode": 1,
          "features": ["lower-controls"],
          "assets": {
            "bottom.trackpad.background": {
              "path": "assets/bottom.png",
              "scale": "cover",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
          },
          "colors": {"background": "#112233"},
          "metrics": {"spacingScale": 1.0},
          "extensions": {}
        }
    """.trimIndent()
}
