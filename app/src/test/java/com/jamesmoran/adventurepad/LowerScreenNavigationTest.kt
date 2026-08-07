package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowerScreenNavigationTest {
    @Test fun companionOpensFromGameplayAndBlocksGameplayTouch() {
        val open = reduceLowerScreenNavigation(
            LowerScreenNavigationState(),
            LowerScreenNavigationAction.OpenCompanion,
        )

        assertEquals(LowerScreenPage.COMPANION, open.page)
        assertTrue(shouldBlockGameplayTouch(open))
    }

    @Test fun companionClosesWithoutOwningOrChangingDisplayState() {
        val displayMode = DisplayMode.INTERFACE
        val split = InterfaceSplit(0.72f)
        val open = reduceLowerScreenNavigation(
            LowerScreenNavigationState(),
            LowerScreenNavigationAction.OpenCompanion,
        )
        val closed = reduceLowerScreenNavigation(open, LowerScreenNavigationAction.ClosePage)

        assertEquals(LowerScreenPage.GAMEPLAY, closed.page)
        assertEquals(DisplayMode.INTERFACE, displayMode)
        assertEquals(InterfaceSplit(0.72f), split)
        assertFalse(shouldBlockGameplayTouch(closed))
    }

    @Test fun settingsOpensAndClosesWithoutChangingCompanionOrGameplayState() {
        val initial = LowerScreenNavigationState(companionSection = CompanionSection.MANUAL)
        val open = reduceLowerScreenNavigation(initial, LowerScreenNavigationAction.OpenSettings)
        val closed = reduceLowerScreenNavigation(open, LowerScreenNavigationAction.ClosePage)

        assertEquals(LowerScreenPage.SETTINGS, open.page)
        assertTrue(shouldBlockGameplayTouch(open))
        assertEquals(initial, closed)
    }

    @Test fun selectingCompanionTabsDoesNotChangePageOrMirrorState() {
        val initial = LowerScreenNavigationState(page = LowerScreenPage.COMPANION)
        val crop = NormalizedCrop(0f, 0.7f, 1f, 1f)
        val updated = reduceLowerScreenNavigation(
            initial,
            LowerScreenNavigationAction.SelectCompanionSection(CompanionSection.STATISTICS),
        )

        assertEquals(LowerScreenPage.COMPANION, updated.page)
        assertEquals(CompanionSection.STATISTICS, updated.companionSection)
        assertEquals(NormalizedCrop(0f, 0.7f, 1f, 1f), crop)
    }

    @Test fun permanentGameplayUtilitiesContainOnlyCompanionAndSettings() {
        assertEquals(
            listOf(GameplayUtilityAction.COMPANION, GameplayUtilityAction.SETTINGS),
            PermanentGameplayUtilityActions,
        )
    }
}
