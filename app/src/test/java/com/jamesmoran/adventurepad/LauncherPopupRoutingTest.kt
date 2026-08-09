package com.jamesmoran.adventurepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherPopupRoutingTest {
    private val sortBounds = Rect(80f, 0f, 120f, 40f)
    private val cards = linkedMapOf(
        "first" to Rect(0f, 50f, 40f, 100f),
        "second" to Rect(50f, 50f, 90f, 100f),
    )

    @Test fun primaryPressOnSortReplacesCurrentPopup() {
        assertEquals(
            LauncherPopupTarget.Sort,
            launcherPopupTargetForPress(false, Offset(100f, 20f), sortBounds, cards),
        )
    }

    @Test fun secondaryPressOnAnotherCardReplacesContextPopup() {
        assertEquals(
            LauncherPopupTarget.Context("second"),
            launcherPopupTargetForPress(true, Offset(70f, 70f), sortBounds, cards),
        )
    }

    @Test fun primaryOrSecondaryPressElsewhereDismissesWithoutPassThroughTarget() {
        assertNull(launcherPopupTargetForPress(false, Offset(20f, 20f), sortBounds, cards))
        assertNull(launcherPopupTargetForPress(true, Offset(140f, 140f), sortBounds, cards))
    }
}
