package com.jamesmoran.adventurepad

internal enum class LowerScreenPage {
    GAMEPLAY,
    COMPANION,
    SETTINGS,
}

internal enum class CompanionSection(val label: String, val isAvailable: Boolean = true) {
    HOME("Companion"),
    NOTES("Notes"),
    WALKTHROUGH("Walkthrough"),
    MANUAL("Manual", isAvailable = false),
    DIALOGUE("Dialogue", isAvailable = false),
    STATISTICS("Statistics", isAvailable = false),
}

internal const val COMPANION_COMING_SOON_LABEL = "COMING SOON"

internal data class LowerScreenNavigationState(
    val page: LowerScreenPage = LowerScreenPage.GAMEPLAY,
    val companionSection: CompanionSection = CompanionSection.HOME,
)

internal sealed interface LowerScreenNavigationAction {
    data object OpenCompanion : LowerScreenNavigationAction
    data object OpenSettings : LowerScreenNavigationAction
    data object ClosePage : LowerScreenNavigationAction
    data object BackCompanion : LowerScreenNavigationAction
    data class SelectCompanionSection(val section: CompanionSection) : LowerScreenNavigationAction
}

internal fun reduceLowerScreenNavigation(
    state: LowerScreenNavigationState,
    action: LowerScreenNavigationAction,
): LowerScreenNavigationState = when (action) {
    LowerScreenNavigationAction.OpenCompanion -> state.copy(
        page = LowerScreenPage.COMPANION,
        companionSection = CompanionSection.HOME,
    )
    LowerScreenNavigationAction.OpenSettings -> state.copy(page = LowerScreenPage.SETTINGS)
    LowerScreenNavigationAction.ClosePage -> state.copy(page = LowerScreenPage.GAMEPLAY)
    LowerScreenNavigationAction.BackCompanion -> if (state.companionSection == CompanionSection.HOME) {
        state.copy(page = LowerScreenPage.GAMEPLAY)
    } else {
        state.copy(companionSection = CompanionSection.HOME)
    }
    is LowerScreenNavigationAction.SelectCompanionSection -> if (action.section.isAvailable) {
        state.copy(companionSection = action.section)
    } else {
        state
    }
}

internal enum class GameplayUtilityAction(val label: String) {
    COMPANION("Companion"),
    SETTINGS("Settings"),
}

internal fun GameplayUtilityAction.displayLabel(): String = when (this) {
    GameplayUtilityAction.COMPANION -> "📖  $label"
    GameplayUtilityAction.SETTINGS -> "⚙  $label"
}

internal const val GAMEPLAY_UTILITY_MIN_TOUCH_TARGET_DP = 56
internal const val APPLICATION_PAGE_FRACTION = 0.94f
internal const val COMPANION_SAFE_CONTENT_INSET_DP = 16
internal const val COMPANION_HEADER_HEIGHT_DP = 72
internal const val COMPANION_ACTION_TOP_OFFSET_DP = 48
internal const val COMPANION_ACTION_ROW_HEIGHT_DP = 96
internal const val COMPANION_ACTION_HORIZONTAL_PADDING_DP = 16
internal const val COMPANION_ACTION_GAP_DP = 16

internal val PermanentGameplayUtilityActions = GameplayUtilityAction.entries.toList()

internal fun shouldBlockGameplayTouch(state: LowerScreenNavigationState): Boolean =
    state.page != LowerScreenPage.GAMEPLAY
