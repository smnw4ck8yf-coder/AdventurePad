package com.jamesmoran.adventurepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal const val TRACKPAD_OVERLAY_WIDTH_FRACTION = 0.34f
internal const val TRACKPAD_OVERLAY_HEIGHT_FRACTION = 0.22f

internal data class TrackpadOverlayGeometry(
    val trackpadBounds: Rect,
    val left: Rect,
    val right: Rect,
)

internal fun calculateTrackpadOverlayGeometry(
    width: Float,
    height: Float,
    minimumHeight: Float,
    maximumHeight: Float,
): TrackpadOverlayGeometry? {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
        !minimumHeight.isFinite() || !maximumHeight.isFinite() ||
        minimumHeight < 0f || maximumHeight < minimumHeight
    ) return null

    val overlayHeight = (height * TRACKPAD_OVERLAY_HEIGHT_FRACTION)
        .coerceIn(minimumHeight, maximumHeight)
        .coerceAtMost(height)
    val overlayWidth = width * TRACKPAD_OVERLAY_WIDTH_FRACTION
    val overlayTop = height - overlayHeight
    val bounds = Rect(0f, 0f, width, height)
    return TrackpadOverlayGeometry(
        trackpadBounds = bounds,
        left = Rect(0f, overlayTop, overlayWidth, height),
        right = Rect(width - overlayWidth, overlayTop, width, height),
    )
}

internal enum class TrackpadInputOwner {
    TRACKPAD,
    LEFT_OVERLAY,
    RIGHT_OVERLAY,
    IGNORED,
}

internal enum class TrackpadOverlayButton {
    LEFT,
    RIGHT,
}

internal data class TrackpadOverlayButtonTransition(
    val button: TrackpadOverlayButton,
    val isDown: Boolean,
)

/** Locks each physical pointer to its initial region for the life of that pointer. */
internal class TrackpadInputOwnership {
    private val owners = mutableMapOf<Long, TrackpadInputOwner>()

    fun begin(
        pointerId: Long,
        position: Offset,
        geometry: TrackpadOverlayGeometry,
    ): TrackpadOverlayButtonTransition? {
        if (pointerId in owners) return null
        val requestedOwner = when {
            position in geometry.left -> TrackpadInputOwner.LEFT_OVERLAY
            position in geometry.right -> TrackpadInputOwner.RIGHT_OVERLAY
            else -> TrackpadInputOwner.TRACKPAD
        }
        val owner = when {
            owners.isEmpty() -> requestedOwner
            requestedOwner == TrackpadInputOwner.TRACKPAD &&
                owners.values.singleOrNull() == TrackpadInputOwner.LEFT_OVERLAY ->
                TrackpadInputOwner.TRACKPAD
            owners.values.all { it == TrackpadInputOwner.TRACKPAD } &&
                requestedOwner == TrackpadInputOwner.TRACKPAD -> TrackpadInputOwner.TRACKPAD
            else -> TrackpadInputOwner.IGNORED
        }
        owners[pointerId] = owner
        return owner.buttonTransition(isDown = true)
    }

    fun finish(pointerId: Long): TrackpadOverlayButtonTransition? =
        owners.remove(pointerId)?.buttonTransition(isDown = false)

    fun finishAll(): List<TrackpadOverlayButtonTransition> =
        owners.keys.toList().mapNotNull(::finish)

    fun ownerOf(pointerId: Long): TrackpadInputOwner? = owners[pointerId]

    fun trackpadPointerIds(): Set<Long> = owners
        .filterValues { it == TrackpadInputOwner.TRACKPAD }
        .keys

    fun isLeftHeld(): Boolean = owners.values.any { it == TrackpadInputOwner.LEFT_OVERLAY }

    fun routesHeldLeftTrackpadMovement(): Boolean =
        isLeftHeld() && trackpadPointerIds().size == 1

    fun isEmpty(): Boolean = owners.isEmpty()

    fun isButtonOwnedSequence(): Boolean = owners.values.any {
        it == TrackpadInputOwner.LEFT_OVERLAY ||
            it == TrackpadInputOwner.RIGHT_OVERLAY
    }

    private fun TrackpadInputOwner.buttonTransition(
        isDown: Boolean,
    ): TrackpadOverlayButtonTransition? = when (this) {
        TrackpadInputOwner.LEFT_OVERLAY -> TrackpadOverlayButtonTransition(
            TrackpadOverlayButton.LEFT,
            isDown,
        )
        TrackpadInputOwner.RIGHT_OVERLAY -> TrackpadOverlayButtonTransition(
            TrackpadOverlayButton.RIGHT,
            isDown,
        )
        TrackpadInputOwner.TRACKPAD,
        TrackpadInputOwner.IGNORED,
        -> null
    }
}
