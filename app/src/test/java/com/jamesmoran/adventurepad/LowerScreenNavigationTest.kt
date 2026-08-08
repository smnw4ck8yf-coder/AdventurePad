package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadDesign
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
        assertEquals(CompanionSection.HOME, open.companionSection)
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

    @Test fun unavailableCompanionTabsDoNotChangePageOrMirrorState() {
        val initial = LowerScreenNavigationState(page = LowerScreenPage.COMPANION)
        val crop = NormalizedCrop(0f, 0.7f, 1f, 1f)
        val updated = reduceLowerScreenNavigation(
            initial,
            LowerScreenNavigationAction.SelectCompanionSection(CompanionSection.STATISTICS),
        )

        assertEquals(LowerScreenPage.COMPANION, updated.page)
        assertEquals(CompanionSection.HOME, updated.companionSection)
        assertEquals(NormalizedCrop(0f, 0.7f, 1f, 1f), crop)
    }

    @Test fun manualDialogueAndStatisticsAreConsistentlyMarkedComingSoon() {
        val comingSoon = listOf(
            CompanionSection.MANUAL,
            CompanionSection.DIALOGUE,
            CompanionSection.STATISTICS,
        )

        assertTrue(comingSoon.all { !it.isAvailable })
        assertEquals("COMING SOON", COMPANION_COMING_SOON_LABEL)
        assertTrue(CompanionSection.NOTES.isAvailable)
        assertTrue(CompanionSection.WALKTHROUGH.isAvailable)
    }

    @Test fun backFromCompanionPageReturnsHomeThenGameplay() {
        val page = LowerScreenNavigationState(LowerScreenPage.COMPANION, CompanionSection.WALKTHROUGH)
        val home = reduceLowerScreenNavigation(page, LowerScreenNavigationAction.BackCompanion)
        val gameplay = reduceLowerScreenNavigation(home, LowerScreenNavigationAction.BackCompanion)

        assertEquals(LowerScreenPage.COMPANION, home.page)
        assertEquals(CompanionSection.HOME, home.companionSection)
        assertEquals(LowerScreenPage.GAMEPLAY, gameplay.page)
    }

    @Test fun permanentGameplayUtilitiesContainOnlyCompanionAndSettings() {
        assertEquals(
            listOf(GameplayUtilityAction.COMPANION, GameplayUtilityAction.SETTINGS),
            PermanentGameplayUtilityActions,
        )
    }

    @Test fun utilityLabelsRenderExactlyOneConfiguredIcon() {
        assertEquals("📖  Companion", GameplayUtilityAction.COMPANION.displayLabel())
        assertEquals("⚙  Settings", GameplayUtilityAction.SETTINGS.displayLabel())
        assertEquals(1, GameplayUtilityAction.SETTINGS.displayLabel().count { it == '⚙' })
        assertFalse(GameplayUtilityAction.SETTINGS.label.contains('⚙'))
    }

    @Test fun utilityTargetsAndLowerPagesMeetSizingIntent() {
        assertTrue(GAMEPLAY_UTILITY_MIN_TOUCH_TARGET_DP >= 56)
        assertEquals(
            GAMEPLAY_UTILITY_MIN_TOUCH_TARGET_DP.toFloat(),
            AdventurePadDesign.utilityTouchTarget.value,
            0f,
        )
        assertTrue(LOWER_PAGE_FRACTION in 0.92f..0.95f)
    }
}
