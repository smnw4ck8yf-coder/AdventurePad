package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LowerDisplayHandoffCoordinatorTest {
    @Test
    fun launcherTrackpadIsMovedToBackground() {
        val registry = LowerDisplayHandoffRegistry()
        val owner = FakeLowerDisplayOwner(LowerDisplayOwnershipState(SkinContext.LAUNCHER, null))
        registry.register(owner)

        assertSame(LowerDisplayHandoffResult.Backgrounded, registry.backgroundLauncherTrackpad())
        assertTrue(owner.moveRequested)
    }

    @Test
    fun gameplayContextIsNeverMovedToBackground() {
        val registry = LowerDisplayHandoffRegistry()
        val owner = FakeLowerDisplayOwner(LowerDisplayOwnershipState(SkinContext.GAMEPLAY, "monkey-1"))
        registry.register(owner)

        assertEquals(
            LowerDisplayHandoffResult.RefusedGameplay("monkey-1"),
            registry.backgroundLauncherTrackpad(),
        )
        assertFalse(owner.moveRequested)
    }

    @Test
    fun reportedTargetBlocksHandoffEvenIfPresentationStillLooksLikeLauncher() {
        val registry = LowerDisplayHandoffRegistry()
        val owner = FakeLowerDisplayOwner(LowerDisplayOwnershipState(SkinContext.LAUNCHER, "atlantis"))
        registry.register(owner)

        assertEquals(
            LowerDisplayHandoffResult.RefusedGameplay("atlantis"),
            registry.backgroundLauncherTrackpad(),
        )
        assertFalse(owner.moveRequested)
    }

    @Test
    fun failedMoveIsReportedAndUnregisteredOwnerIsIgnored() {
        val registry = LowerDisplayHandoffRegistry()
        val owner = FakeLowerDisplayOwner(
            LowerDisplayOwnershipState(SkinContext.LAUNCHER, null),
            moveResult = false,
        )
        registry.register(owner)

        assertSame(LowerDisplayHandoffResult.Failed, registry.backgroundLauncherTrackpad())
        registry.unregister(owner)
        assertSame(LowerDisplayHandoffResult.NoActiveTrackpad, registry.backgroundLauncherTrackpad())
    }
}

private class FakeLowerDisplayOwner(
    private val state: LowerDisplayOwnershipState,
    private val moveResult: Boolean = true,
) : LowerDisplayTaskOwner {
    var moveRequested = false

    override fun ownershipState(): LowerDisplayOwnershipState = state

    override fun moveTaskToBackground(): Boolean {
        moveRequested = true
        return moveResult
    }
}
