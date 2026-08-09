package com.jamesmoran.adventurepad

/** Pure slot selection rules shared by rendering and contract tests. */
internal fun gameplayBackgroundCandidates(splitViewVisible: Boolean): Array<String> =
    if (splitViewVisible) {
        arrayOf(SkinSlots.BOTTOM_SPLIT_BACKGROUND, SkinSlots.LEGACY_BOTTOM_BACKGROUND)
    } else {
        arrayOf(SkinSlots.BOTTOM_TRACKPAD_BACKGROUND, SkinSlots.LEGACY_BOTTOM_BACKGROUND)
    }

internal fun companionBackgroundCandidates(section: CompanionSection): Array<String> = when (section) {
    CompanionSection.NOTES -> arrayOf(SkinSlots.NOTES_BACKGROUND)
    CompanionSection.WALKTHROUGH -> arrayOf(SkinSlots.WALKTHROUGH_BACKGROUND)
    CompanionSection.HOME,
    CompanionSection.MANUAL,
    CompanionSection.DIALOGUE,
    CompanionSection.STATISTICS,
    -> arrayOf(SkinSlots.COMPANION_BACKGROUND)
}

/** Settings deliberately has no game-skin surface; it remains opaque application UI. */
internal fun lowerPageBackgroundCandidates(
    page: LowerScreenPage,
    companionSection: CompanionSection,
    splitViewVisible: Boolean,
): Array<String> = when (page) {
    LowerScreenPage.GAMEPLAY -> gameplayBackgroundCandidates(splitViewVisible)
    LowerScreenPage.COMPANION -> companionBackgroundCandidates(companionSection)
    LowerScreenPage.SETTINGS -> emptyArray()
}

internal enum class FunctionalPaneTreatment { OPAQUE, IMMERSIVE }

/** Only the two proof surfaces may surrender their native opaque pane to exact skin artwork. */
internal fun functionalPaneTreatment(
    interfaceStyle: InterfaceStyle,
    page: LowerScreenPage,
    companionSection: CompanionSection,
    hasExactArtwork: Boolean,
): FunctionalPaneTreatment = if (
    interfaceStyle == InterfaceStyle.IMMERSIVE &&
    page == LowerScreenPage.COMPANION &&
    companionSection in setOf(CompanionSection.NOTES, CompanionSection.WALKTHROUGH) &&
    hasExactArtwork
) {
    FunctionalPaneTreatment.IMMERSIVE
} else {
    FunctionalPaneTreatment.OPAQUE
}
