package com.jamesmoran.adventurepad

import kotlin.math.floor

/** Global presentation-only stretch for the lower interface; source crop coordinates stay unchanged. */
internal const val LOWER_PANEL_VERTICAL_SCALE = 1.35f

internal data class FloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x >= left && x < right && y >= top && y < bottom
}

internal data class SourcePoint(val x: Int, val y: Int)
internal data class PanelPoint(val x: Float, val y: Float)

/** Authoritative stretched lower-panel destination and panel-to-virtual-source transform. */
internal data class LowerPanelGeometry(
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val destination: FloatRect,
    val crop: NormalizedCrop,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val orientation: SourceOrientation,
) {
    fun mapTouch(x: Float, y: Float): SourcePoint? {
        if (!destination.contains(x, y)) return null
        val outputU = ((x - destination.left) / destination.width).coerceIn(0f, 1f)
        val outputV = ((y - destination.top) / destination.height).coerceIn(0f, 1f)
        val (cropU, cropV) = when (orientation) {
            SourceOrientation.NORMAL -> outputU to outputV
            SourceOrientation.ROTATE_90 -> outputV to (1f - outputU)
            SourceOrientation.ROTATE_180 -> (1f - outputU) to (1f - outputV)
            SourceOrientation.ROTATE_270 -> (1f - outputV) to outputU
        }
        val sourceU = crop.left + cropU * crop.width
        val sourceV = crop.top + cropV * crop.height
        return SourcePoint(
            x = floor(sourceU * sourceWidth).toInt().coerceIn(0, sourceWidth - 1),
            y = floor(sourceV * sourceHeight).toInt().coerceIn(0, sourceHeight - 1),
        )
    }

    fun mapSource(point: SourcePoint): PanelPoint? {
        if (point.x !in 0 until sourceWidth || point.y !in 0 until sourceHeight) return null
        val sourceU = point.x.toFloat() / sourceWidth
        val sourceV = point.y.toFloat() / sourceHeight
        if (sourceU < crop.left || sourceU >= crop.right ||
            sourceV < crop.top || sourceV >= crop.bottom
        ) return null
        val cropU = (sourceU - crop.left) / crop.width
        val cropV = (sourceV - crop.top) / crop.height
        val (outputU, outputV) = when (orientation) {
            SourceOrientation.NORMAL -> cropU to cropV
            SourceOrientation.ROTATE_90 -> (1f - cropV) to cropU
            SourceOrientation.ROTATE_180 -> (1f - cropU) to (1f - cropV)
            SourceOrientation.ROTATE_270 -> cropV to (1f - cropU)
        }
        return PanelPoint(
            x = destination.left + outputU * destination.width,
            y = destination.top + outputV * destination.height,
        )
    }
}

internal fun lowerPanelGeometry(
    surfaceWidth: Int,
    surfaceHeight: Int,
    crop: NormalizedCrop,
    sourceWidth: Int,
    sourceHeight: Int,
    orientation: SourceOrientation,
): LowerPanelGeometry? {
    if (surfaceWidth <= 0 || surfaceHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0 ||
        !crop.isValid()
    ) return null
    val cropWidth = crop.width * sourceWidth
    val cropHeight = crop.height * sourceHeight
    if (!cropWidth.isFinite() || !cropHeight.isFinite() || cropWidth <= 0f || cropHeight <= 0f) return null
    // The lower mirror intentionally maps the crop across the entire supplied surface.
    // Its X and Y scales are independent; there is no aspect fit or padding rectangle.
    val destinationWidth = surfaceWidth.toFloat()
    val destinationHeight = surfaceHeight.toFloat()
    val left = 0f
    val top = 0f
    return LowerPanelGeometry(
        surfaceWidth = surfaceWidth,
        surfaceHeight = surfaceHeight,
        destination = FloatRect(left, top, left + destinationWidth, top + destinationHeight),
        crop = crop,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        orientation = orientation,
    )
}

internal enum class AbsoluteSourcePointerAction(val wireValue: Int) {
    MOVE(0), DOWN(1), UP(2), CANCEL(3),
}

internal data class AbsoluteSourcePointerCommand(
    val point: SourcePoint,
    val action: AbsoluteSourcePointerAction,
    val cropGeneration: Long,
    val geometryGeneration: Long,
    val pointerSequenceId: Long,
)

internal class LowerPanelTouchTracker(private val touchSlop: Float) {
    private var pointerId: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var cancelled = false

    fun begin(id: Int, x: Float, y: Float, point: SourcePoint?): AbsoluteSourcePointerAction? {
        reset()
        if (point == null || id < 0 || !x.isFinite() || !y.isFinite()) return null
        pointerId = id
        downX = x
        downY = y
        return AbsoluteSourcePointerAction.DOWN
    }

    fun move(id: Int, x: Float, y: Float, point: SourcePoint?): AbsoluteSourcePointerAction? {
        if (id != pointerId || cancelled || point == null) return cancel()
        val dx = x - downX
        val dy = y - downY
        if (dx * dx + dy * dy > touchSlop * touchSlop) cancelled = true
        return AbsoluteSourcePointerAction.MOVE
    }

    fun end(id: Int, point: SourcePoint?): AbsoluteSourcePointerAction? {
        if (id != pointerId || cancelled || point == null) return cancel()
        reset()
        return AbsoluteSourcePointerAction.UP
    }

    fun additionalPointer(): AbsoluteSourcePointerAction? = cancel()

    fun cancel(): AbsoluteSourcePointerAction? {
        val wasActive = pointerId != null
        reset()
        return if (wasActive) AbsoluteSourcePointerAction.CANCEL else null
    }

    private fun reset() {
        pointerId = null
        downX = 0f
        downY = 0f
        cancelled = false
    }
}
