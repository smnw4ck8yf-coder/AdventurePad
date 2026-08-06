package com.jamesmoran.adventurepad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorOutputStateTest {
    @Test
    fun liveSurfaceIsShownOnlyAfterSupportedStatus() {
        assertTrue(MirrorOutputState.SUPPORTED.shouldShowLiveSurface)
        MirrorOutputState.entries
            .filterNot { it == MirrorOutputState.SUPPORTED }
            .forEach { state -> assertFalse(state.shouldShowLiveSurface) }
    }
}
