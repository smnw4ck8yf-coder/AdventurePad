package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourThemeSelectorTest {
    @Test
    fun immersiveIsAlwaysTheFinalOption() {
        val options = colourThemeSelectorOptions(immersiveAvailable = true)

        assertEquals(
            AdventurePadThemes.BuiltIns.map { it.displayName } + "Immersive",
            options.map { it.displayName },
        )
        assertTrue(options.last().immersive)
        assertNull(options.last().theme)
    }

    @Test
    fun immersiveAvailabilityRemainsCapabilityGated() {
        val unavailable = colourThemeSelectorOptions(immersiveAvailable = false)
        val available = colourThemeSelectorOptions(immersiveAvailable = true)

        assertFalse(unavailable.last().enabled)
        assertTrue(available.last().enabled)
        assertTrue(unavailable.dropLast(1).all { it.enabled && !it.immersive })
    }
}
