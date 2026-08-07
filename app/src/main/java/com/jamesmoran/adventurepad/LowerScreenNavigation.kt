package com.jamesmoran.adventurepad

internal enum class LowerScreenPage {
    GAMEPLAY,
    COMPANION,
    SETTINGS,
}

internal enum class CompanionSection(val label: String) {
    NOTES("Notes"),
    WALKTHROUGH("Walkthrough"),
    MANUAL("Manual"),
    DIALOGUE("Dialogue"),
    STATISTICS("Statistics"),
}

internal data class LowerScreenNavigationState(
    val page: LowerScreenPage = LowerScreenPage.GAMEPLAY,
    val companionSection: CompanionSection = CompanionSection.NOTES,
)

internal sealed interface LowerScreenNavigationAction {
    data object OpenCompanion : LowerScreenNavigationAction
    data object OpenSettings : LowerScreenNavigationAction
    data object ClosePage : LowerScreenNavigationAction
    data class SelectCompanionSection(val section: CompanionSection) : LowerScreenNavigationAction
}

internal fun reduceLowerScreenNavigation(
    state: LowerScreenNavigationState,
    action: LowerScreenNavigationAction,
): LowerScreenNavigationState = when (action) {
    LowerScreenNavigationAction.OpenCompanion -> state.copy(page = LowerScreenPage.COMPANION)
    LowerScreenNavigationAction.OpenSettings -> state.copy(page = LowerScreenPage.SETTINGS)
    LowerScreenNavigationAction.ClosePage -> state.copy(page = LowerScreenPage.GAMEPLAY)
    is LowerScreenNavigationAction.SelectCompanionSection ->
        state.copy(companionSection = action.section)
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
internal const val LOWER_PAGE_FRACTION = 0.94f

internal val PermanentGameplayUtilityActions = GameplayUtilityAction.entries.toList()

internal fun shouldBlockGameplayTouch(state: LowerScreenNavigationState): Boolean =
    state.page != LowerScreenPage.GAMEPLAY
