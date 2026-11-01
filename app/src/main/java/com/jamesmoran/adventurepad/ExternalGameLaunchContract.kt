package com.jamesmoran.adventurepad

internal object ExternalGameLaunchContract {
    const val ACTION_LAUNCH_SCUMMVM_TARGET =
        "com.jamesmoran.adventurepad.action.LAUNCH_SCUMMVM_TARGET"
    const val EXTRA_TARGET_ID = "com.jamesmoran.adventurepad.extra.TARGET_ID"
    const val MAX_TARGET_ID_LENGTH = 128
}

internal sealed interface ExternalGameLaunchRequestResult {
    data class Accepted(val targetId: String) : ExternalGameLaunchRequestResult
    data class Rejected(val reason: String) : ExternalGameLaunchRequestResult
}

/** Parses only the public, non-privileged launch contract. */
internal fun parseExternalGameLaunchRequest(
    action: String?,
    targetId: String?,
): ExternalGameLaunchRequestResult {
    if (action != ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET) {
        return ExternalGameLaunchRequestResult.Rejected("Unsupported external launch action.")
    }
    if (targetId == null || targetId.isBlank()) {
        return ExternalGameLaunchRequestResult.Rejected("No configured ScummVM target was supplied.")
    }
    if (targetId != targetId.trim()) {
        return ExternalGameLaunchRequestResult.Rejected("The configured target contains surrounding whitespace.")
    }
    if (targetId.length > ExternalGameLaunchContract.MAX_TARGET_ID_LENGTH) {
        return ExternalGameLaunchRequestResult.Rejected("The configured target is too long.")
    }
    if (targetId.any(Char::isISOControl)) {
        return ExternalGameLaunchRequestResult.Rejected("The configured target contains control characters.")
    }
    if ('/' in targetId || '\\' in targetId) {
        return ExternalGameLaunchRequestResult.Rejected("Filesystem paths are not accepted.")
    }
    return ExternalGameLaunchRequestResult.Accepted(targetId)
}

/** Cocoon's scummvm_id is a configured target alias, never an engine game ID. */
internal fun resolveExternalLaunchTarget(
    targetId: String,
    targets: List<ScummVMTarget>,
): ScummVMTarget? = targets.firstOrNull { it.targetId == targetId }

internal data class PendingExternalLaunch(
    val generation: Long,
    val targetId: String,
    val attempts: Int = 0,
)

/** Latest-request-wins gate for service replies racing with new external intents. */
internal class ExternalGameLaunchRequestCoordinator {
    private var nextGeneration = 0L
    private var pending: PendingExternalLaunch? = null

    fun replace(targetId: String): PendingExternalLaunch {
        val request = PendingExternalLaunch(++nextGeneration, targetId)
        pending = request
        return request
    }

    fun current(): PendingExternalLaunch? = pending

    fun nextAttempt(generation: Long, maxAttempts: Int): String? {
        require(maxAttempts > 0)
        val request = pending?.takeIf {
            it.generation == generation && it.attempts < maxAttempts
        } ?: return null
        pending = request.copy(attempts = request.attempts + 1)
        return request.targetId
    }

    fun confirm(activeTargetId: String): String? {
        val request = pending?.takeIf { it.targetId == activeTargetId } ?: return null
        pending = null
        return request.targetId
    }

    fun consume(generation: Long): String? {
        val request = pending?.takeIf { it.generation == generation } ?: return null
        pending = null
        return request.targetId
    }
}
