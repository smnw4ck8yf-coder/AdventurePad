package com.jamesmoran.adventurepad

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkinAuthoringSpecParserTest {
    @Test
    fun parsesBundledAuthoritativeV1Regions() {
        val spec = SkinAuthoringSpecParser.parse(authoritativeSpec())
        assertEquals(SkinCanvas(4720, 4040), spec.masterCanvas)
        assertEquals(17, spec.regions.size)
        assertEquals("top.surround", spec.regions.first().slotId)
        assertEquals(SkinTransparentCenter(64, 64, 1112, 232, 0), spec.regions.first { it.slotId == SkinSlots.PANEL_FRAME }.transparentCenter)
    }

    @Test
    fun rejectsDuplicateAuthoritativeSlot() {
        val invalid = authoritativeSpec().replaceFirst("\"bottom.trackpad.background\"", "\"top.surround\"")
        assertThrows(SkinAuthoringSpecException::class.java) {
            SkinAuthoringSpecParser.parse(invalid)
        }
    }

    private fun authoritativeSpec(): String {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(workingDirectory, "skin-authoring/AUTHORING_TEMPLATE_SPEC.json"),
            File(workingDirectory, "../skin-authoring/AUTHORING_TEMPLATE_SPEC.json"),
        )
        val specFile = candidates.firstOrNull(File::isFile)
            ?: error("Could not locate AUTHORING_TEMPLATE_SPEC.json from $workingDirectory")
        return specFile.readText()
    }
}
