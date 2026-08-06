package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySelectionTest {
    @Test
    fun selectsFirstEligibleNonDefaultPhysicalPresentationDisplay() {
        val candidates = listOf(
            eligibleCandidate(displayId = 0),
            eligibleCandidate(displayId = 7),
            eligibleCandidate(displayId = 9),
        )

        assertEquals(7, selectEligibleSecondaryDisplayId(candidates))
    }

    @Test
    fun rejectsVirtualPrivateUnavailableAndNonPresentationDisplays() {
        val candidates = listOf(
            eligibleCandidate(displayId = 1, name = "screen record virtual display"),
            eligibleCandidate(displayId = 2, isPrivate = true),
            eligibleCandidate(displayId = 3, isAvailable = false),
            eligibleCandidate(displayId = 4, isPresentationCategory = false),
            eligibleCandidate(displayId = 5, supportsPresentation = false),
            eligibleCandidate(displayId = 6, physicalWidth = 0),
        )

        assertNull(selectEligibleSecondaryDisplayId(candidates))
    }

    private fun eligibleCandidate(
        displayId: Int,
        name: String = "Built-in secondary display",
        isPresentationCategory: Boolean = true,
        isPrivate: Boolean = false,
        isAvailable: Boolean = true,
        supportsPresentation: Boolean = true,
        physicalWidth: Int = 1920,
    ) = DisplayCandidate(
        displayId = displayId,
        name = name,
        isPresentationCategory = isPresentationCategory,
        isValid = true,
        supportsPresentation = supportsPresentation,
        isPrivate = isPrivate,
        isAvailable = isAvailable,
        physicalWidth = physicalWidth,
        physicalHeight = 1080,
    )
}
