package com.jamesmoran.adventurepad

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCursorStateTest {
    @Test fun initializesAtCenterAndIsImmediatelyVisibleForActiveLauncher() {
        val state = LauncherCursorState().withBounds(width = 100f, height = 60f, radius = 8f)

        assertEquals(50f, state.x)
        assertEquals(30f, state.y)
        assertTrue(state.isVisible(ownsTopScreen = true))
    }

    @Test fun cursorIsHiddenWhenLauncherDoesNotOwnTopScreenAndRestoredOnResume() {
        val state = LauncherCursorState().withBounds(width = 100f, height = 60f, radius = 8f)

        assertFalse(state.isVisible(ownsTopScreen = false))
        assertTrue(state.isVisible(ownsTopScreen = true))
    }

    @Test fun movementAndResizeRemainInsideVisibleLauncherBounds() {
        val moved = LauncherCursorState()
            .withBounds(width = 100f, height = 60f, radius = 8f)
            .moveBy(dx = 1_000f, dy = -1_000f)

        assertEquals(92f, moved.x)
        assertEquals(8f, moved.y)

        val resized = moved.withBounds(width = 40f, height = 30f, radius = 8f)
        assertEquals(32f, resized.x)
        assertEquals(8f, resized.y)
    }

    @Test fun pausedMovementStateIsRetainedWithoutReinitialization() {
        val state = LauncherCursorState()
            .withBounds(width = 100f, height = 60f, radius = 8f)
            .moveBy(dx = 12f, dy = 7f)

        assertFalse(state.isVisible(ownsTopScreen = false))
        assertEquals(62f, state.x)
        assertEquals(37f, state.y)
        assertTrue(state.isVisible(ownsTopScreen = true))
    }

    @Test fun invalidDeltaCannotCorruptCursorCoordinates() {
        val state = LauncherCursorState().withBounds(width = 100f, height = 60f, radius = 8f)

        assertSame(state, state.moveBy(Float.NaN, 1f))
        assertSame(state, state.moveBy(1f, Float.POSITIVE_INFINITY))
    }

    @Test fun heldPrimaryButtonUsesMoveEventsWhileCursorCoordinatesKeepUpdating() {
        val initial = LauncherCursorState().withBounds(width = 100f, height = 60f, radius = 8f)

        assertEquals(MotionEvent.ACTION_MOVE, launcherPointerMotionAction(MotionEvent.BUTTON_PRIMARY))
        assertEquals(OffsetExpectation(57f, 34f), initial.moveBy(7f, 4f).let { OffsetExpectation(it.x, it.y) })
        assertEquals(MotionEvent.ACTION_HOVER_MOVE, launcherPointerMotionAction(0))
    }
}

private data class OffsetExpectation(val x: Float, val y: Float)
