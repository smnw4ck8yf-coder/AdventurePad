package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorSurfaceGenerationStateTest {
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
