package com.jamesmoran.adventurepad

/** Retains a confirmed ScummVM target when geometry briefly reports no target. */
internal fun canonicalCompanionTargetId(currentTargetId: String, reportedTargetId: String): String =
    reportedTargetId.trim().takeIf(String::isNotEmpty) ?: currentTargetId

/** Keeps all per-game Companion repositories on the same validated target. */
internal fun routeCompanionTargetId(
    targetId: String,
    selectNotesTarget: (String) -> Unit,
    selectWalkthroughTarget: (String) -> Unit,
) {
    if (targetId.isBlank()) return
    selectNotesTarget(targetId)
    selectWalkthroughTarget(targetId)
}

/** Applies a target report without allowing a transient blank report to clear established content. */
internal fun retainAndRouteCompanionTargetId(
    currentTargetId: String,
    reportedTargetId: String,
    selectNotesTarget: (String) -> Unit,
    selectWalkthroughTarget: (String) -> Unit,
): String {
    val retainedTargetId = canonicalCompanionTargetId(currentTargetId, reportedTargetId)
    if (retainedTargetId != currentTargetId) {
        routeCompanionTargetId(retainedTargetId, selectNotesTarget, selectWalkthroughTarget)
    }
    return retainedTargetId
}

internal fun isCompanionTargetAvailable(targetId: String): Boolean = targetId.isNotBlank()
