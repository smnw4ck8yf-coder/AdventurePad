package com.jamesmoran.adventurepad

import java.util.concurrent.CopyOnWriteArraySet

internal data class CursorDelta(
    val dx: Float,
    val dy: Float,
)

internal fun interface CursorDeltaSubscription {
    fun cancel()
}

/** Bounded, process-local communication between AdventurePad's two activities. */
internal object CursorDeltaCoordinator {
    private val subscribers = CopyOnWriteArraySet<(CursorDelta) -> Unit>()

    fun publish(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        val update = CursorDelta(dx = dx, dy = dy)
        ScummVMInputClient.sendRelativeDelta(dx, dy)
        subscribers.forEach { subscriber -> subscriber(update) }
    }

    fun subscribe(subscriber: (CursorDelta) -> Unit): CursorDeltaSubscription {
        subscribers += subscriber
        return CursorDeltaSubscription { subscribers -= subscriber }
    }
}
