package com.jamesmoran.adventurepad

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal enum class BorderPatch {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT,
}

internal data class NineSlicePatch(
    val patch: BorderPatch,
    val source: PixelRect,
    val destination: PixelRect,
)

internal data class PixelPoint(val x: Float, val y: Float)

internal data class PanelContentTransform(
    val content: PixelRect,
    val interfaceWidth: Int = content.width,
    val interfaceHeight: Int = content.height,
) {
    fun localToOuter(point: PixelPoint): PixelPoint =
        PixelPoint(point.x + content.left, point.y + content.top)

    fun outerToLocal(point: PixelPoint): PixelPoint =
        PixelPoint(point.x - content.left, point.y - content.top)

    fun interfaceToOuter(point: PixelPoint): PixelPoint = PixelPoint(
        x = content.left + point.x * content.width / interfaceWidth.coerceAtLeast(1),
        y = content.top + point.y * content.height / interfaceHeight.coerceAtLeast(1),
    )

    fun outerToInterface(point: PixelPoint): PixelPoint = PixelPoint(
        x = (point.x - content.left) * interfaceWidth / content.width.coerceAtLeast(1),
        y = (point.y - content.top) * interfaceHeight / content.height.coerceAtLeast(1),
    )
}

/**
 * Maps a border-only nine-slice onto the complete destination rectangle.
 *
 * [sourceOuterBounds] removes only transparent padding outside the visible authored border. Slice
 * insets remain source cut positions; they are never interpreted as destination padding.
 */
internal fun calculateBorderNineSlicePatches(
    destinationWidth: Int,
    destinationHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    insets: SkinInsets,
    sourceOuterBounds: PixelRect = PixelRect(0, 0, sourceWidth, sourceHeight),
): List<NineSlicePatch> {
    val destinationRight = destinationWidth.coerceAtLeast(0)
    val destinationBottom = destinationHeight.coerceAtLeast(0)
    val outer = PixelRect(
        left = sourceOuterBounds.left.coerceIn(0, sourceWidth),
        top = sourceOuterBounds.top.coerceIn(0, sourceHeight),
        right = sourceOuterBounds.right.coerceIn(0, sourceWidth),
        bottom = sourceOuterBounds.bottom.coerceIn(0, sourceHeight),
    )
    val sourceLeftCut = insets.left.coerceIn(outer.left, outer.right)
    val sourceRightCut = (sourceWidth - insets.right).coerceIn(sourceLeftCut, outer.right)
    val sourceTopCut = insets.top.coerceIn(outer.top, outer.bottom)
    val sourceBottomCut = (sourceHeight - insets.bottom).coerceIn(sourceTopCut, outer.bottom)

    val authoredLeft = sourceLeftCut - outer.left
    val authoredRight = outer.right - sourceRightCut
    val authoredTop = sourceTopCut - outer.top
    val authoredBottom = outer.bottom - sourceBottomCut
    val horizontalScale = if (authoredLeft + authoredRight > 0) {
        destinationRight.toFloat() / (authoredLeft + authoredRight)
    } else 1f
    val verticalScale = if (authoredTop + authoredBottom > 0) {
        destinationBottom.toFloat() / (authoredTop + authoredBottom)
    } else 1f
    val cornerScale = min(1f, min(horizontalScale, verticalScale))
    fun scaled(value: Int): Int = floor(value * cornerScale).toInt()

    val destinationLeftCut = scaled(authoredLeft)
    val destinationRightCut = destinationRight - scaled(authoredRight)
    val destinationTopCut = scaled(authoredTop)
    val destinationBottomCut = destinationBottom - scaled(authoredBottom)

    fun patch(
        name: BorderPatch,
        sourceLeft: Int,
        sourceTop: Int,
        sourceRight: Int,
        sourceBottom: Int,
        destinationLeft: Int,
        destinationTop: Int,
        destinationPatchRight: Int,
        destinationPatchBottom: Int,
    ) = NineSlicePatch(
        patch = name,
        source = PixelRect(sourceLeft, sourceTop, sourceRight, sourceBottom),
        destination = PixelRect(
            destinationLeft,
            destinationTop,
            destinationPatchRight,
            destinationPatchBottom,
        ),
    )

    return listOf(
        patch(BorderPatch.TOP_LEFT, outer.left, outer.top, sourceLeftCut, sourceTopCut, 0, 0, destinationLeftCut, destinationTopCut),
        patch(BorderPatch.TOP, sourceLeftCut, outer.top, sourceRightCut, sourceTopCut, destinationLeftCut, 0, destinationRightCut, destinationTopCut),
        patch(BorderPatch.TOP_RIGHT, sourceRightCut, outer.top, outer.right, sourceTopCut, destinationRightCut, 0, destinationRight, destinationTopCut),
        patch(BorderPatch.LEFT, outer.left, sourceTopCut, sourceLeftCut, sourceBottomCut, 0, destinationTopCut, destinationLeftCut, destinationBottomCut),
        patch(BorderPatch.RIGHT, sourceRightCut, sourceTopCut, outer.right, sourceBottomCut, destinationRightCut, destinationTopCut, destinationRight, destinationBottomCut),
        patch(BorderPatch.BOTTOM_LEFT, outer.left, sourceBottomCut, sourceLeftCut, outer.bottom, 0, destinationBottomCut, destinationLeftCut, destinationBottom),
        patch(BorderPatch.BOTTOM, sourceLeftCut, sourceBottomCut, sourceRightCut, outer.bottom, destinationLeftCut, destinationBottomCut, destinationRightCut, destinationBottom),
        patch(BorderPatch.BOTTOM_RIGHT, sourceRightCut, sourceBottomCut, outer.right, outer.bottom, destinationRightCut, destinationBottomCut, destinationRight, destinationBottom),
    )
}

internal fun calculateBorderContentRect(
    destinationWidth: Int,
    destinationHeight: Int,
    patches: List<NineSlicePatch>,
    sourceContentBounds: PixelRect? = null,
): PixelRect {
    val byName = patches.associateBy { it.patch }
    if (sourceContentBounds != null) {
        fun mapEdge(value: Int, patch: NineSlicePatch, horizontal: Boolean): Int {
            val sourceStart = if (horizontal) patch.source.left else patch.source.top
            val sourceEnd = if (horizontal) patch.source.right else patch.source.bottom
            val destinationStart = if (horizontal) patch.destination.left else patch.destination.top
            val destinationEnd = if (horizontal) patch.destination.right else patch.destination.bottom
            if (sourceEnd <= sourceStart) return destinationStart
            val fraction = (value.coerceIn(sourceStart, sourceEnd) - sourceStart).toFloat() /
                (sourceEnd - sourceStart)
            return (destinationStart + fraction * (destinationEnd - destinationStart)).roundToInt()
        }

        val left = mapEdge(sourceContentBounds.left, byName.getValue(BorderPatch.LEFT), true)
            .coerceIn(0, destinationWidth)
        val top = mapEdge(sourceContentBounds.top, byName.getValue(BorderPatch.TOP), false)
            .coerceIn(0, destinationHeight)
        val right = mapEdge(sourceContentBounds.right, byName.getValue(BorderPatch.RIGHT), true)
            .coerceIn(left, destinationWidth)
        val bottom = mapEdge(sourceContentBounds.bottom, byName.getValue(BorderPatch.BOTTOM), false)
            .coerceIn(top, destinationHeight)
        return PixelRect(left, top, right, bottom)
    }
    val left = byName.getValue(BorderPatch.LEFT).destination.right.coerceIn(0, destinationWidth)
    val top = byName.getValue(BorderPatch.TOP).destination.bottom.coerceIn(0, destinationHeight)
    val right = byName.getValue(BorderPatch.RIGHT).destination.left.coerceIn(left, destinationWidth)
    val bottom = byName.getValue(BorderPatch.BOTTOM).destination.top.coerceIn(top, destinationHeight)
    return PixelRect(left, top, right, bottom)
}

/**
 * Finds the transparent opening between the actually painted frame rails.
 *
 * Nine-slice insets are stretch boundaries, not necessarily the inner edge of visible artwork.
 * The centre line of each straight rail is sampled so corner ornaments can overlap a few edge
 * pixels without turning their inward protrusions into permanent empty content strips.
 */
internal fun calculateFrameSourceContentBounds(
    sourceWidth: Int,
    sourceHeight: Int,
    insets: SkinInsets,
    visibleBounds: PixelRect,
    isVisible: (x: Int, y: Int) -> Boolean,
): PixelRect {
    val outer = PixelRect(
        left = visibleBounds.left.coerceIn(0, sourceWidth),
        top = visibleBounds.top.coerceIn(0, sourceHeight),
        right = visibleBounds.right.coerceIn(0, sourceWidth),
        bottom = visibleBounds.bottom.coerceIn(0, sourceHeight),
    )
    val leftCut = insets.left.coerceIn(outer.left, outer.right)
    val rightCut = (sourceWidth - insets.right).coerceIn(leftCut, outer.right)
    val topCut = insets.top.coerceIn(outer.top, outer.bottom)
    val bottomCut = (sourceHeight - insets.bottom).coerceIn(topCut, outer.bottom)

    val centreY = topCut + (bottomCut - topCut) / 2
    val centreX = leftCut + (rightCut - leftCut) / 2
    var left = outer.left
    var right = outer.right
    var top = outer.top
    var bottom = outer.bottom

    if (centreY in 0 until sourceHeight) {
        for (x in outer.left until leftCut) if (isVisible(x, centreY)) left = x + 1
        for (x in rightCut until outer.right) if (isVisible(x, centreY)) {
            right = x
            break
        }
    }
    if (centreX in 0 until sourceWidth) {
        for (y in outer.top until topCut) if (isVisible(centreX, y)) top = y + 1
        for (y in bottomCut until outer.bottom) if (isVisible(centreX, y)) {
            bottom = y
            break
        }
    }

    return PixelRect(
        left = left.coerceAtMost(right),
        top = top.coerceAtMost(bottom),
        right = right.coerceAtLeast(left),
        bottom = bottom.coerceAtLeast(top),
    )
}
