package com.jamesmoran.adventurepad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPanelFramePolicyTest {
    @Test
    fun frameRequiresSplitViewImmersiveStyleAndAnAsset() {
        assertTrue(shouldShowSplitPanelFrame(true, InterfaceStyle.IMMERSIVE, true))
        assertFalse(shouldShowSplitPanelFrame(false, InterfaceStyle.IMMERSIVE, true))
        assertFalse(shouldShowSplitPanelFrame(true, InterfaceStyle.STANDARD, true))
        assertFalse(shouldShowSplitPanelFrame(true, InterfaceStyle.IMMERSIVE, false))
    }
}
