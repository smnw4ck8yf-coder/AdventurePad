package com.jamesmoran.adventurepad

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadOverlayTest {
    @Test fun leftOverlayTapProducesExactlyOneDownAndOneUp() {
        val ownership = TrackpadInputOwnership()
        val events = listOfNotNull(
            ownership.begin(0L, Offset(20f, 380f), geometry()),
            ownership.finish(0L),
            ownership.finish(0L),
        )

        assertEquals(
            listOf(
                TrackpadOverlayButtonTransition(TrackpadOverlayButton.LEFT, true),
                TrackpadOverlayButtonTransition(TrackpadOverlayButton.LEFT, false),
            ),
            events,
        )
    }

    @Test fun rightOverlayTapProducesExactlyOneDownAndOneUp() {
        val ownership = TrackpadInputOwnership()
        assertEquals(
            TrackpadOverlayButtonTransition(TrackpadOverlayButton.RIGHT, true),
            ownership.begin(0L, Offset(780f, 380f), geometry()),
        )
        assertEquals(
            TrackpadOverlayButtonTransition(TrackpadOverlayButton.RIGHT, false),
            ownership.finish(0L),
        )
        assertNull(ownership.finish(0L))
    }

    @Test fun centralTrackpadGestureRemainsTrackpadOwnedWhenCrossingOverlay() {
        val ownership = TrackpadInputOwnership()
        assertNull(ownership.begin(0L, Offset(400f, 200f), geometry()))
        assertEquals(TrackpadInputOwner.TRACKPAD, ownership.ownerOf(0L))

        // Movement does not re-hit-test; only the initial contact establishes ownership.
        assertEquals(TrackpadInputOwner.TRACKPAD, ownership.ownerOf(0L))
        assertNull(ownership.finish(0L))
        assertNull(ownership.ownerOf(0L))
    }

    @Test fun gesturesStartingInOverlaysRemainOverlayOwned() {
        val left = TrackpadInputOwnership()
        left.begin(0L, Offset(20f, 380f), geometry())
        assertEquals(TrackpadInputOwner.LEFT_OVERLAY, left.ownerOf(0L))
        assertNull(left.begin(0L, Offset(400f, 100f), geometry()))
        assertEquals(TrackpadInputOwner.LEFT_OVERLAY, left.ownerOf(0L))

        val right = TrackpadInputOwnership()
        right.begin(0L, Offset(780f, 380f), geometry())
        assertEquals(TrackpadInputOwner.RIGHT_OVERLAY, right.ownerOf(0L))
        assertNull(right.begin(0L, Offset(400f, 100f), geometry()))
        assertEquals(TrackpadInputOwner.RIGHT_OVERLAY, right.ownerOf(0L))
    }

    @Test fun heldLeftAllowsASecondTrackpadPointerWithoutChangingEitherOwner() {
        val ownership = TrackpadInputOwnership()
        assertEquals(
            TrackpadOverlayButtonTransition(TrackpadOverlayButton.LEFT, true),
            ownership.begin(10L, Offset(20f, 380f), geometry()),
        )
        assertNull(ownership.begin(20L, Offset(400f, 100f), geometry()))

        assertTrue(ownership.isLeftHeld())
        assertEquals(TrackpadInputOwner.LEFT_OVERLAY, ownership.ownerOf(10L))
        assertEquals(TrackpadInputOwner.TRACKPAD, ownership.ownerOf(20L))
        assertEquals(setOf(20L), ownership.trackpadPointerIds())
        assertTrue(ownership.routesHeldLeftTrackpadMovement())

        // Crossing regions never reassigns either physical pointer.
        assertEquals(TrackpadInputOwner.LEFT_OVERLAY, ownership.ownerOf(10L))
        assertEquals(TrackpadInputOwner.TRACKPAD, ownership.ownerOf(20L))
    }

    @Test fun heldLeftReleaseIsExactlyOnceAndDoesNotReleaseTrackpadOwnership() {
        val ownership = TrackpadInputOwnership()
        ownership.begin(10L, Offset(20f, 380f), geometry())
        ownership.begin(20L, Offset(400f, 100f), geometry())

        assertEquals(
            TrackpadOverlayButtonTransition(TrackpadOverlayButton.LEFT, false),
            ownership.finish(10L),
        )
        assertNull(ownership.finish(10L))
        assertEquals(TrackpadInputOwner.TRACKPAD, ownership.ownerOf(20L))
        assertNull(ownership.finish(20L))
        assertTrue(ownership.isEmpty())
    }

    @Test fun leftHeldTrackpadPointerCannotBecomeTwoFingerTrackpadGesture() {
        val ownership = TrackpadInputOwnership()
        ownership.begin(10L, Offset(20f, 380f), geometry())
        ownership.begin(20L, Offset(400f, 100f), geometry())

        assertEquals(1, ownership.trackpadPointerIds().size)
        assertTrue(ownership.isButtonOwnedSequence())
    }

    @Test fun lifecycleCancellationReleasesHeldLeftOnce() {
        val ownership = TrackpadInputOwnership()
        ownership.begin(10L, Offset(20f, 380f), geometry())
        ownership.begin(20L, Offset(400f, 100f), geometry())

        assertEquals(
            listOf(TrackpadOverlayButtonTransition(TrackpadOverlayButton.LEFT, false)),
            ownership.finishAll(),
        )
        assertTrue(ownership.finishAll().isEmpty())
    }

    @Test fun overlayPointerAddedToOrdinaryTrackpadSequenceIsIgnored() {
        val ownership = TrackpadInputOwnership()
        ownership.begin(10L, Offset(400f, 100f), geometry())
        assertNull(ownership.begin(20L, Offset(20f, 380f), geometry()))

        assertEquals(TrackpadInputOwner.TRACKPAD, ownership.ownerOf(10L))
        assertEquals(TrackpadInputOwner.IGNORED, ownership.ownerOf(20L))
    }

    @Test fun overlayGeometryScalesAndStaysInsideTrackpadBounds() {
        val compact = calculateTrackpadOverlayGeometry(800f, 300f, 56f, 88f)!!
        val tall = calculateTrackpadOverlayGeometry(800f, 600f, 56f, 160f)!!

        assertTrue(tall.left.height > compact.left.height)
        listOf(compact, tall).forEach { geometry ->
            assertTrue(geometry.trackpadBounds.contains(geometry.left.topLeft))
            assertTrue(geometry.trackpadBounds.contains(geometry.right.bottomRight - Offset(0.01f, 0.01f)))
            assertTrue(geometry.left.right < geometry.right.left)
            assertEquals(geometry.trackpadBounds.bottom, geometry.left.bottom, 0f)
            assertEquals(geometry.trackpadBounds.bottom, geometry.right.bottom, 0f)
        }
    }

    @Test fun dynamicSplitHeightKeepsOverlaysAttachedToTrackpadBottom() {
        val shallow = calculateTrackpadOverlayGeometry(1920f, 220f, 56f, 88f)!!
        val deep = calculateTrackpadOverlayGeometry(1920f, 700f, 56f, 88f)!!

        assertEquals(220f, shallow.left.bottom, 0f)
        assertEquals(700f, deep.left.bottom, 0f)
        assertEquals(220f, shallow.right.bottom, 0f)
        assertEquals(700f, deep.right.bottom, 0f)
    }

    private fun geometry() = calculateTrackpadOverlayGeometry(
        width = 800f,
        height = 400f,
        minimumHeight = 56f,
        maximumHeight = 88f,
    )!!
}
