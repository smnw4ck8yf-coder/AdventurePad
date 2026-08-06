package com.jamesmoran.adventurepad

import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BindingRequestTrackerTest {
    @Test
    fun activeBindingPreventsDuplicateRequests() {
        val tracker = BindingRequestTracker()

        assertTrue(tracker.start())
        assertTrue(tracker.canRequestBinding())
        tracker.recordRequestResult(accepted = true)

        assertFalse(tracker.start())
        assertFalse(tracker.canRequestBinding())
        assertFalse(tracker.beginReconnectAttempt())
        assertEquals(0, tracker.reconnectAttemptCount)
    }

    @Test
    fun rejectedAndDiscardedBindingsAllowOneReconnectAtATime() {
        val tracker = BindingRequestTracker()

        tracker.start()
        tracker.recordRequestResult(accepted = false)
        assertTrue(tracker.beginReconnectAttempt())
        tracker.recordRequestResult(accepted = true)
        assertFalse(tracker.beginReconnectAttempt())

        tracker.discardBinding()
        assertTrue(tracker.beginReconnectAttempt())
        assertEquals(2, tracker.reconnectAttemptCount)
    }

    @Test
    fun stopCancelsFutureReconnectOpportunities() {
        val tracker = BindingRequestTracker()

        tracker.start()
        tracker.recordRequestResult(accepted = false)
        tracker.stop()

        assertFalse(tracker.bindingDesired)
        assertFalse(tracker.bindingRequested)
        assertFalse(tracker.canRequestBinding())
        assertFalse(tracker.beginReconnectAttempt())
    }
}

class TouchStateResetTest {
    @Test
    fun lifecycleResetClearsTransientInputButPreservesCursor() {
        val reset = TouchState(
            fingerX = 120f,
            fingerY = 240f,
            deltaX = 8f,
            deltaY = -4f,
            cursorX = 500f,
            cursorY = 300f,
            cursorInitialized = true,
            pointerCount = 1,
            trackedPointerId = PointerId(42L),
            action = TouchAction.MOVE,
            moveEventCount = 17,
        ).withTransientInputCleared()

        assertEquals(0f, reset.fingerX)
        assertEquals(0f, reset.fingerY)
        assertEquals(0f, reset.deltaX)
        assertEquals(0f, reset.deltaY)
        assertEquals(0, reset.pointerCount)
        assertNull(reset.trackedPointerId)
        assertEquals(TouchAction.CANCEL, reset.action)
        assertEquals(0, reset.moveEventCount)
        assertEquals(500f, reset.cursorX)
        assertEquals(300f, reset.cursorY)
        assertTrue(reset.cursorInitialized)
    }
}

class TrackpadGestureProvenanceTest {
    @Test
    fun genuineDownAndUpAcceptExactlyOneSingleTap() {
        val tracker = tracker()
        val provenance = TrackpadTouchProvenance().apply { reset(0) }
        val token = provenance.recordPlatformDown(0, 100L, 1)
        assertEquals(token, provenance.claimComposeDown(0, 100L, 1))
        tracker.handle(press(100L))
        provenance.recordPlatformUp(0, 100L, 1)
        val verdict = provenance.validateComposeRelease(
            token = token,
            generation = 0,
            composeBackedByPlatformUp = true,
            composeDownTimeMillis = 100L,
            composePointerId = 1,
            allowAdditionalPointers = false,
        )

        val update = tracker.handle(release(140L), verdict)

        assertTrue(verdict.accepted)
        assertEquals(TrackpadGesture.SINGLE_TAP, update.gesture)
        assertEquals(1, update.diagnostics.count { it == "FIRST TAP RECORDED" })
    }

    @Test
    fun platformCancelAndFabricatedReleaseCannotCreateTapHistoryOrButtonGesture() {
        val tracker = tracker()
        val provenance = TrackpadTouchProvenance().apply { reset(0) }
        val token = provenance.recordPlatformDown(0, 100L, 1)
        provenance.claimComposeDown(0, 100L, 1)
        tracker.handle(press(100L))
        provenance.recordPlatformCancel(0, 100L)
        val verdict = provenance.validateComposeRelease(
            token = token,
            generation = 0,
            composeBackedByPlatformUp = false,
            composeDownTimeMillis = null,
            composePointerId = null,
            allowAdditionalPointers = false,
        )

        val cancelled = tracker.handle(release(120L), verdict)
        val followingDown = tracker.handle(press(200L))

        assertEquals(TrackpadGesture.CANCELLED, cancelled.gesture)
        assertEquals(TapRejectionReason.PLATFORM_ACTION_CANCEL, verdict.reason)
        assertFalse(cancelled.diagnostics.contains("FIRST TAP RECORDED"))
        assertFalse(followingDown.scheduleHold)
        assertFalse(followingDown.diagnostics.contains("VALID SECOND TAP DETECTED"))
    }

    @Test
    fun resetBetweenDownAndFabricatedReleaseRejectsTap() {
        val provenance = TrackpadTouchProvenance()
        provenance.reset(4)
        val token = provenance.recordPlatformDown(4, 1_000L, 7)
        provenance.reset(5)

        val verdict = provenance.validateComposeRelease(
            token = token,
            generation = 5,
            composeBackedByPlatformUp = false,
            composeDownTimeMillis = null,
            composePointerId = null,
            allowAdditionalPointers = false,
        )

        assertFalse(verdict.accepted)
        assertEquals(TapRejectionReason.RESET_GENERATION_CHANGED, verdict.reason)
    }

    @Test
    fun movementBeyondSlopCannotBecomeTapOnGenuineUp() {
        val tracker = tracker()
        tracker.handle(press(100L))

        val movement = tracker.handle(move(120L, Offset(20f, 0f)))
        val release = tracker.handle(
            release(140L, Offset(20f, 0f)),
            acceptedRelease(sequenceId = 1),
        )

        assertEquals(TrackpadGesture.CANCELLED, movement.gesture)
        assertNull(release.gesture)
        assertFalse(release.diagnostics.contains("FIRST TAP RECORDED"))
    }

    @Test
    fun twoGenuineTapsStillCreateDoubleTapCandidate() {
        val tracker = tracker()
        tracker.handle(press(100L))
        assertEquals(
            TrackpadGesture.SINGLE_TAP,
            tracker.handle(release(130L), acceptedRelease(sequenceId = 1)).gesture,
        )

        val secondDown = tracker.handle(press(200L))

        assertTrue(secondDown.scheduleHold)
        assertTrue(secondDown.diagnostics.contains("VALID SECOND TAP DETECTED"))
    }

    @Test
    fun cancelledFirstSequenceLeavesFollowingMovementOrdinary() {
        val tracker = tracker()
        tracker.handle(press(100L))
        tracker.handle(
            release(120L),
            rejectedRelease(TapRejectionReason.PLATFORM_ACTION_CANCEL),
        )

        val followingDown = tracker.handle(press(200L))
        val followingMove = tracker.handle(move(230L, Offset(20f, 0f)))

        assertFalse(followingDown.scheduleHold)
        assertEquals(TrackpadGesture.CANCELLED, followingMove.gesture)
        assertTrue(followingMove.allowMovement)
    }

    @Test
    fun cancelReleasesActiveGestureOwnedDragExactlyOnce() {
        val tracker = tracker()
        tracker.handle(press(100L))
        tracker.handle(release(130L), acceptedRelease(sequenceId = 1))
        tracker.handle(press(200L))
        assertEquals(
            TrackpadGesture.DOUBLE_TAP_HOLD_START,
            tracker.handle(move(230L, Offset(20f, 0f))).gesture,
        )

        val firstCancel = tracker.invalidateFromProvenance(
            rejectedRelease(TapRejectionReason.PLATFORM_ACTION_CANCEL),
        )
        val repeatedCancel = tracker.invalidateFromProvenance(
            rejectedRelease(TapRejectionReason.PLATFORM_ACTION_CANCEL),
        )

        assertEquals(TrackpadGesture.DOUBLE_TAP_HOLD_END, firstCancel.gesture)
        assertFalse(repeatedCancel.gesture == TrackpadGesture.DOUBLE_TAP_HOLD_END)
    }

    private fun tracker() = TrackpadGestureTracker(
        singleTapMaximumDurationMillis = 180L,
        twoFingerTapMaximumDurationMillis = 250L,
        doubleTapTimeoutMillis = 300L,
        holdTimeoutMillis = 500L,
        touchSlop = 10f,
        doubleTapSlop = 100f,
    )

    private fun press(time: Long, position: Offset = Offset.Zero) = pointerEvent(
        time = time,
        position = position,
        pressed = true,
        previousPressed = false,
    )

    private fun move(time: Long, position: Offset) = pointerEvent(
        time = time,
        position = position,
        pressed = true,
        previousPressed = true,
    )

    private fun release(time: Long, position: Offset = Offset.Zero) = pointerEvent(
        time = time,
        position = position,
        pressed = false,
        previousPressed = true,
    )

    private fun pointerEvent(
        time: Long,
        position: Offset,
        pressed: Boolean,
        previousPressed: Boolean,
    ) = PointerEvent(
        listOf(
            PointerInputChange(
                id = PointerId(1L),
                uptimeMillis = time,
                position = position,
                pressed = pressed,
                previousUptimeMillis = time - 10L,
                previousPosition = if (previousPressed) Offset.Zero else position,
                previousPressed = previousPressed,
                isInitiallyConsumed = false,
            ),
        ),
    )

    private fun acceptedRelease(sequenceId: Int) = TouchReleaseVerdict(
        accepted = true,
        sequenceId = sequenceId,
        generation = 0,
        genuinePlatformUp = true,
        platformCancelled = false,
        coroutineInvalidated = false,
        reason = null,
    )

    private fun rejectedRelease(reason: TapRejectionReason) =
        TouchReleaseVerdict.rejected(
            sequenceId = 1,
            generation = 0,
            reason = reason,
            platformCancelled = reason == TapRejectionReason.PLATFORM_ACTION_CANCEL,
        )
}
