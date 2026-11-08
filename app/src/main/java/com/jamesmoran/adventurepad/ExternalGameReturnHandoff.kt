package com.jamesmoran.adventurepad

internal data class ExternalGameReturnToken(
    val sessionGeneration: Long,
    val connectionEpoch: Long,
    val confirmationGeneration: Long,
)

internal sealed interface ExternalGameReturnAction {
    data object None : ExternalGameReturnAction
    data object CancelPendingConfirmation : ExternalGameReturnAction
    data class ScheduleConfirmation(val token: ExternalGameReturnToken) : ExternalGameReturnAction
    data class QueryFreshGeometry(val token: ExternalGameReturnToken) : ExternalGameReturnAction
    data class BackgroundLowerDisplay(val token: ExternalGameReturnToken) : ExternalGameReturnAction
}

/**
 * Tracks only games confirmed through the external-launch entry point. A blank target can return
 * the lower display only after the expected target was observed on the same uninterrupted bridge
 * connection and a delayed, fresh geometry query confirmed that it remained blank.
 */
internal class ExternalGameReturnHandoffStateMachine {
    private data class Session(
        val expectedTargetId: String,
        val generation: Long,
        var observedTargetEpoch: Long? = null,
        var pending: Pending? = null,
    )

    private sealed interface Pending {
        val token: ExternalGameReturnToken

        data class Delay(override val token: ExternalGameReturnToken) : Pending
        data class FreshGeometry(override val token: ExternalGameReturnToken) : Pending
        data class Handoff(override val token: ExternalGameReturnToken) : Pending
    }

    private var session: Session? = null
    private var connected = false
    private var connectionEpoch = 0L
    private var nextSessionGeneration = 0L
    private var nextConfirmationGeneration = 0L

    fun replaceSession(expectedTargetId: String): ExternalGameReturnAction {
        require(expectedTargetId.isNotBlank())
        val cancelPending = session?.pending != null
        session = Session(
            expectedTargetId = expectedTargetId.trim(),
            generation = ++nextSessionGeneration,
        )
        return if (cancelPending) {
            ExternalGameReturnAction.CancelPendingConfirmation
        } else {
            ExternalGameReturnAction.None
        }
    }

    fun clearSession(): ExternalGameReturnAction {
        val cancelPending = session?.pending != null
        session = null
        return if (cancelPending) {
            ExternalGameReturnAction.CancelPendingConfirmation
        } else {
            ExternalGameReturnAction.None
        }
    }

    fun currentExpectedTargetId(): String? = session?.expectedTargetId

    fun onConnectionChanged(isConnected: Boolean): ExternalGameReturnAction {
        if (connected == isConnected) return ExternalGameReturnAction.None
        connected = isConnected
        connectionEpoch++
        val current = session ?: return ExternalGameReturnAction.None
        val cancelPending = current.pending != null
        current.observedTargetEpoch = null
        current.pending = null
        return if (cancelPending) {
            ExternalGameReturnAction.CancelPendingConfirmation
        } else {
            ExternalGameReturnAction.None
        }
    }

    fun onTargetObserved(targetId: String): ExternalGameReturnAction {
        val current = session ?: return ExternalGameReturnAction.None
        if (!connected) return ExternalGameReturnAction.None
        val normalizedTarget = targetId.trim()
        if (normalizedTarget.isNotEmpty()) {
            val cancelPending = current.pending != null
            current.pending = null
            current.observedTargetEpoch = if (normalizedTarget == current.expectedTargetId) {
                connectionEpoch
            } else {
                null
            }
            return if (cancelPending) {
                ExternalGameReturnAction.CancelPendingConfirmation
            } else {
                ExternalGameReturnAction.None
            }
        }
        if (current.observedTargetEpoch != connectionEpoch) return ExternalGameReturnAction.None
        return when (val pending = current.pending) {
            null -> {
                val token = ExternalGameReturnToken(
                    sessionGeneration = current.generation,
                    connectionEpoch = connectionEpoch,
                    confirmationGeneration = ++nextConfirmationGeneration,
                )
                current.pending = Pending.Delay(token)
                ExternalGameReturnAction.ScheduleConfirmation(token)
            }
            is Pending.Delay -> ExternalGameReturnAction.None
            is Pending.FreshGeometry -> {
                current.pending = Pending.Handoff(pending.token)
                ExternalGameReturnAction.BackgroundLowerDisplay(pending.token)
            }
            is Pending.Handoff -> ExternalGameReturnAction.None
        }
    }

    fun onConfirmationDelayElapsed(token: ExternalGameReturnToken): ExternalGameReturnAction {
        val current = session ?: return ExternalGameReturnAction.None
        if (!connected || current.observedTargetEpoch != connectionEpoch) {
            return ExternalGameReturnAction.None
        }
        val pending = current.pending as? Pending.Delay ?: return ExternalGameReturnAction.None
        if (pending.token != token || !token.matches(current)) return ExternalGameReturnAction.None
        current.pending = Pending.FreshGeometry(token)
        return ExternalGameReturnAction.QueryFreshGeometry(token)
    }

    fun onFreshGeometryQueryFailed(token: ExternalGameReturnToken) {
        val current = session ?: return
        val pending = current.pending as? Pending.FreshGeometry ?: return
        if (pending.token == token && token.matches(current)) current.pending = null
    }

    fun onHandoffResult(token: ExternalGameReturnToken, succeeded: Boolean) {
        val current = session ?: return
        val pending = current.pending as? Pending.Handoff ?: return
        if (pending.token != token || !token.matches(current)) return
        if (succeeded) {
            session = null
        } else {
            current.pending = null
        }
    }

    private fun ExternalGameReturnToken.matches(session: Session): Boolean =
        sessionGeneration == session.generation && connectionEpoch == this@ExternalGameReturnHandoffStateMachine.connectionEpoch
}
