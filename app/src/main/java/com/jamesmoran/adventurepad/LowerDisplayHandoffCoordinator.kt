package com.jamesmoran.adventurepad

import java.lang.ref.WeakReference

internal data class LowerDisplayOwnershipState(
    val skinContext: SkinContext,
    val activeTargetId: String?,
)

internal interface LowerDisplayTaskOwner {
    fun ownershipState(): LowerDisplayOwnershipState
    fun moveTaskToBackground(): Boolean
}

internal sealed interface LowerDisplayHandoffResult {
    data object Backgrounded : LowerDisplayHandoffResult
    data object NoActiveTrackpad : LowerDisplayHandoffResult
    data object Failed : LowerDisplayHandoffResult
    data class RefusedGameplay(val targetId: String) : LowerDisplayHandoffResult
}

/**
 * Coordinates only the independently hosted lower-display task. It never stops the application,
 * ScummVM, services, or processes, and it refuses to move an active gameplay companion.
 */
internal class LowerDisplayHandoffRegistry {
    private var ownerReference: WeakReference<LowerDisplayTaskOwner>? = null

    fun register(owner: LowerDisplayTaskOwner) {
        ownerReference = WeakReference(owner)
    }

    fun unregister(owner: LowerDisplayTaskOwner) {
        if (ownerReference?.get() === owner) ownerReference = null
    }

    fun backgroundLauncherTrackpad(): LowerDisplayHandoffResult {
        val owner = ownerReference?.get() ?: return LowerDisplayHandoffResult.NoActiveTrackpad
        val state = owner.ownershipState()
        if (state.skinContext != SkinContext.LAUNCHER || !state.activeTargetId.isNullOrBlank()) {
            return LowerDisplayHandoffResult.RefusedGameplay(
                state.activeTargetId?.takeIf(String::isNotBlank) ?: "active game",
            )
        }
        return if (owner.moveTaskToBackground()) {
            LowerDisplayHandoffResult.Backgrounded
        } else {
            LowerDisplayHandoffResult.Failed
        }
    }
}

internal object LowerDisplayHandoffCoordinator {
    private val registry = LowerDisplayHandoffRegistry()

    fun register(owner: LowerDisplayTaskOwner) = registry.register(owner)

    fun unregister(owner: LowerDisplayTaskOwner) = registry.unregister(owner)

    fun backgroundLauncherTrackpad(): LowerDisplayHandoffResult =
        registry.backgroundLauncherTrackpad()
}
