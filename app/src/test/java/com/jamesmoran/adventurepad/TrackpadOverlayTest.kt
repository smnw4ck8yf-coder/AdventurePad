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

    @Test fun standardSurfaceHeightReductionPreservesMouseButtonSize() {
        val previousHeight = 919f
        val reducedHeight = previousHeight * 0.90f
        val previous = calculateTrackpadOverlayGeometry(1184f, previousHeight, 129.15f, 202.95f)!!
        val reduced = calculateTrackpadOverlayGeometry(
            width = 1184f,
            height = reducedHeight,
            minimumHeight = 129.15f,
            maximumHeight = 202.95f,
            heightScale = 1f / 0.90f,
        )!!

        assertEquals(previous.left.size, reduced.left.size)
        assertEquals(previous.right.size, reduced.right.size)
        assertEquals(previous.trackpadBounds.width, reduced.trackpadBounds.width, 0f)
        assertEquals(previous.trackpadBounds.height * 0.90f, reduced.trackpadBounds.height, 0.001f)
    }

    @Test fun immersiveOverlayGeometryUniformlyFitsAuthoredButtonRatio() {
        val authoredRatio = 528f / 224f
        val shallow = calculateTrackpadOverlayGeometry(
            width = 1000f,
            height = 220f,
            minimumHeight = 56f,
            maximumHeight = 88f,
            leftAspectRatio = authoredRatio,
            rightAspectRatio = authoredRatio,
        )!!
        val deep = calculateTrackpadOverlayGeometry(
            width = 1000f,
            height = 700f,
            minimumHeight = 56f,
            maximumHeight = 88f,
            leftAspectRatio = authoredRatio,
            rightAspectRatio = authoredRatio,
        )!!

        listOf(shallow, deep).forEach { geometry ->
            assertEquals(authoredRatio, geometry.left.width / geometry.left.height, 0.0001f)
            assertEquals(authoredRatio, geometry.right.width / geometry.right.height, 0.0001f)
            assertEquals(geometry.trackpadBounds.bottom, geometry.left.bottom, 0f)
            assertEquals(geometry.trackpadBounds.bottom, geometry.right.bottom, 0f)
        }
    }

    @Test fun immersiveOverlayScaleGrowsUniformlyAndRemainsSeparated() {
        val authoredRatio = 528f / 224f
        listOf(781f, 572f).forEach { height ->
            val previous = calculateTrackpadOverlayGeometry(
                width = 1006f,
                height = height,
                minimumHeight = 129.15f,
                maximumHeight = 202.95f,
                leftAspectRatio = authoredRatio,
                rightAspectRatio = authoredRatio,
            )!!
            val enlarged = calculateTrackpadOverlayGeometry(
                width = 1006f,
                height = height,
                minimumHeight = 129.15f,
                maximumHeight = 202.95f,
                leftAspectRatio = authoredRatio,
                rightAspectRatio = authoredRatio,
                sizeScale = IMMERSIVE_MOUSE_BUTTON_SCALE,
            )!!

            assertEquals(previous.left.width * 1.2f, enlarged.left.width, 0.001f)
            assertEquals(previous.left.height * 1.2f, enlarged.left.height, 0.001f)
            assertEquals(authoredRatio, enlarged.left.width / enlarged.left.height, 0.0001f)
            assertTrue(enlarged.left.right <= enlarged.right.left)
            assertTrue(enlarged.trackpadBounds.contains(enlarged.left.topLeft))
            assertTrue(enlarged.trackpadBounds.contains(enlarged.right.bottomRight - Offset(0.01f, 0.01f)))
        }
    }

    @Test fun immersiveTrackpadAndCloseGeometryUseMilestoneDimensions() {
        assertEquals(0.85f, IMMERSIVE_TRACKPAD_SCALE, 0f)
        assertEquals(20, IMMERSIVE_SPLIT_TRACKPAD_EXTRA_HEIGHT_DP)
        assertEquals(8, IMMERSIVE_TRACKPAD_BOTTOM_GAP_DP)
        assertEquals(8, IMMERSIVE_UTILITY_UPWARD_OFFSET_DP)
        assertEquals(1.20f, IMMERSIVE_MOUSE_BUTTON_SCALE, 0f)
        assertEquals(72, IMMERSIVE_CLOSE_TOUCH_TARGET_DP)
        assertEquals(-13, IMMERSIVE_CLOSE_FLAG_CENTER_OFFSET_X_DP)
        assertEquals(18, IMMERSIVE_CLOSE_GLYPH_SIZE_DP)
        assertEquals(3, IMMERSIVE_CLOSE_OUTLINE_WIDTH_DP)
        assertEquals(2, IMMERSIVE_CLOSE_FOREGROUND_WIDTH_DP)
        assertEquals(0.5625f, IMMERSIVE_CLOSE_GLYPH_SIZE_DP / 32f, 0f)
        assertTrue(IMMERSIVE_CLOSE_TOUCH_TARGET_DP > IMMERSIVE_CLOSE_GLYPH_SIZE_DP)
    }

    @Test fun splitPanelExpansionAddsHeightAboveWithoutMovingBottomButtons() {
        val currentTop = 345f
        val currentHeight = 599f
        val addedHeight = IMMERSIVE_SPLIT_TRACKPAD_EXTRA_HEIGHT_DP * 2f
        val expandedTop = currentTop - addedHeight
        val expandedHeight = currentHeight + addedHeight
        val authoredRatio = 528f / 224f
        val current = calculateTrackpadOverlayGeometry(
            width = 1006f,
            height = currentHeight,
            minimumHeight = 112f,
            maximumHeight = 176f,
            leftAspectRatio = authoredRatio,
            rightAspectRatio = authoredRatio,
            sizeScale = IMMERSIVE_MOUSE_BUTTON_SCALE,
        )!!
        val expanded = calculateTrackpadOverlayGeometry(
            width = 1006f,
            height = expandedHeight,
            sizingHeight = currentHeight,
            minimumHeight = 112f,
            maximumHeight = 176f,
            leftAspectRatio = authoredRatio,
            rightAspectRatio = authoredRatio,
            sizeScale = IMMERSIVE_MOUSE_BUTTON_SCALE,
        )!!

        assertEquals(305f, expandedTop, 0f)
        assertEquals(currentTop + current.left.top, expandedTop + expanded.left.top, 0.001f)
        assertEquals(currentTop + current.left.bottom, expandedTop + expanded.left.bottom, 0.001f)
        assertEquals(current.left.size, expanded.left.size)
        assertEquals(current.right.size, expanded.right.size)
    }

    private fun geometry() = calculateTrackpadOverlayGeometry(
        width = 800f,
        height = 400f,
        minimumHeight = 56f,
        maximumHeight = 88f,
    )!!
}
