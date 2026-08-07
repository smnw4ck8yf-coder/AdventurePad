package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorSurfaceGenerationStateTest {
    private fun eligibility(
        required: Boolean = true,
        active: Boolean = true,
        connected: Boolean = true,
        available: Boolean = true,
        valid: Boolean = true,
        width: Int = 1240,
        height: Int = 182,
        epoch: Long = 1,
    ) = MirrorAttachmentEligibility(
        mirrorRequired = required,
        lifecycleActive = active,
        messengerConnected = connected,
        surfaceAvailable = available,
        surfaceValid = valid,
        width = width,
        height = height,
        surfaceEpoch = epoch,
    )

    @Test fun validSurfaceWhileSplitViewRequestedAttachesImmediately() {
        assertTrue(MirrorAttachmentGate().shouldAttach(eligibility()))
    }

    @Test fun splitViewRequestedAfterSurfaceReadyAttachesImmediately() {
        val gate = MirrorAttachmentGate()
        assertFalse(gate.shouldAttach(eligibility(required = false)))
        assertTrue(gate.shouldAttach(eligibility(required = true)))
    }

    @Test fun messengerConnectingLastAttachesImmediately() {
        val gate = MirrorAttachmentGate()
        assertFalse(gate.shouldAttach(eligibility(connected = false)))
        assertTrue(gate.shouldAttach(eligibility(connected = true)))
    }

    @Test fun dimensionsBecomingAvailableLastAttachImmediately() {
        val gate = MirrorAttachmentGate()
        assertFalse(gate.shouldAttach(eligibility(width = 0, height = 0)))
        assertTrue(gate.shouldAttach(eligibility()))
    }

    @Test fun cursorIndependentInputsCannotChangeAttachmentEligibility() {
        val gate = MirrorAttachmentGate()
        val ready = eligibility()
        assertTrue(gate.shouldAttach(ready))
        gate.markRequested(ready.surfaceEpoch)
        assertFalse(gate.shouldAttach(ready))
    }

    @Test fun recompositionCannotDuplicateAttachmentForUnchangedSurface() {
        val gate = MirrorAttachmentGate()
        val ready = eligibility()
        gate.markRequested(ready.surfaceEpoch)
        repeat(10) { assertFalse(gate.shouldAttach(ready)) }
    }

    @Test fun surfaceResizeUpdatesMetadataWithoutDuplicatingAttachment() {
        val gate = MirrorAttachmentGate()
        gate.markRequested(1)

        assertFalse(gate.shouldAttach(eligibility(width = 1600, height = 240, epoch = 1)))
    }

    @Test fun destroyedSurfaceIsIneligibleAndInvalidationAllowsReplacement() {
        val gate = MirrorAttachmentGate()
        gate.markRequested(1)
        gate.invalidate()
        assertFalse(gate.shouldAttach(eligibility(available = false, valid = false, epoch = 1)))
        assertTrue(gate.shouldAttach(eligibility(epoch = 2)))
    }

    @Test fun newSurfaceEpochAttachesExactlyOnce() {
        val gate = MirrorAttachmentGate()
        gate.markRequested(1)
        assertTrue(gate.shouldAttach(eligibility(epoch = 2)))
        gate.markRequested(2)
        assertFalse(gate.shouldAttach(eligibility(epoch = 2)))
    }

    @Test fun trackpadModeDoesNotAttachMirror() {
        assertFalse(MirrorAttachmentGate().shouldAttach(eligibility(required = false)))
    }

    @Test fun inactiveLifecycleDoesNotAttachMirror() {
        assertFalse(MirrorAttachmentGate().shouldAttach(eligibility(active = false)))
    }

    @Test fun reconnectReattachesCurrentValidSurfaceOnce() {
        val gate = MirrorAttachmentGate()
        gate.markRequested(1)
        gate.invalidate()
        assertTrue(gate.shouldAttach(eligibility(epoch = 1)))
        gate.markRequested(1)
        assertFalse(gate.shouldAttach(eligibility(epoch = 1)))
    }

    @Test fun surfaceViewAndTextureViewProduceEquivalentAttachmentMetadata() {
        val surfaceView = MirrorHostMode.SURFACE_VIEW.attachmentMetadata(1240, 182, 7)
        val textureView = MirrorHostMode.TEXTURE_VIEW.attachmentMetadata(1240, 182, 7)

        assertEquals(surfaceView, textureView)
    }

    @Test fun lowerCrosshairStateIsIndependentOfMirrorHostAttachmentState() {
        val gate = MirrorAttachmentGate()
        val ready = eligibility()
        gate.markRequested(ready.surfaceEpoch)
        val upstairs = MirrorCursorState(point = SourcePoint(10, 20), visible = false, geometryGeneration = 3)
        val downstairs = upstairs.copy(visible = true)

        assertFalse(upstairs.visible)
        assertTrue(downstairs.visible)
        assertFalse(gate.shouldAttach(ready))
    }

    @Test fun stationaryTrackpadSplitTrackpadSplitTransitionsRemainDeterministic() {
        val gate = MirrorAttachmentGate()
        assertFalse(gate.shouldAttach(eligibility(required = false)))
        assertTrue(gate.shouldAttach(eligibility(required = true)))
        gate.markRequested(1)
        gate.invalidate()
        assertFalse(gate.shouldAttach(eligibility(required = false)))
        assertTrue(gate.shouldAttach(eligibility(required = true)))
    }

    @Test
    fun attachmentsUseMonotonicallyIncreasingGenerations() {
        var next = 0L
        val state = MirrorSurfaceGenerationState { ++next }

        val first = state.beginAttachment()
        assertTrue(state.consumeMatchingDetach(first))
        val second = state.beginAttachment()

        assertTrue(second > first)
        assertEquals(second, state.activeGeneration)
    }

    @Test
    fun staleDetachCannotInvalidateCurrentSurface() {
        var next = 40L
        val state = MirrorSurfaceGenerationState { ++next }
        val active = state.beginAttachment()

        assertFalse(state.consumeMatchingDetach(active - 1))
        assertEquals(active, state.activeGeneration)
    }

    @Test
    fun lifecycleInvalidationClearsTheSingleActiveSurface() {
        var next = 100L
        val state = MirrorSurfaceGenerationState { ++next }
        val active = state.beginAttachment()

        assertEquals(active, state.invalidate())
        assertNull(state.activeGeneration)
        assertNull(state.invalidate())
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateActiveSurfaceIsRejected() {
        var next = 200L
        val state = MirrorSurfaceGenerationState { ++next }

        state.beginAttachment()
        state.beginAttachment()
    }
}
