package com.jamesmoran.adventurepad

import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MIRROR_CROP_SCHEMA_VERSION = 2
internal const val LEGACY_MIRROR_CROP_SCHEMA_VERSION = 1
internal const val MIN_SPLIT_RATIO = 0.05f
internal const val MAX_SPLIT_RATIO = 0.95f
internal const val DEFAULT_SPLIT_RATIO = 0.75f
internal const val SOURCE_ASPECT_TOLERANCE = 0.01f

/** The one authoritative boundary between the upper game and lower interface regions. */
internal data class InterfaceSplit(val ratio: Float) {
    fun isValid(): Boolean = ratio.isFinite() && ratio in MIN_SPLIT_RATIO..MAX_SPLIT_RATIO

    fun snappedTo(sourceHeight: Int): InterfaceSplit {
        if (sourceHeight <= 1 || !ratio.isFinite()) return Default
        val minimumPixel = (sourceHeight * MIN_SPLIT_RATIO).roundToInt().coerceAtLeast(1)
        val maximumPixel = (sourceHeight * MAX_SPLIT_RATIO).roundToInt().coerceAtMost(sourceHeight - 1)
        val pixel = (ratio * sourceHeight).roundToInt().coerceIn(minimumPixel, maximumPixel)
        return InterfaceSplit(pixel.toFloat() / sourceHeight)
    }

    val upperCrop: NormalizedCrop get() = NormalizedCrop(0f, 0f, 1f, ratio)
    val interfaceCrop: NormalizedCrop get() = NormalizedCrop(0f, ratio, 1f, 1f)

    companion object {
        val Default = InterfaceSplit(DEFAULT_SPLIT_RATIO)

        fun fromLegacyCrop(crop: NormalizedCrop): InterfaceSplit =
            InterfaceSplit(crop.top.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO))
    }
}

/** Wire-format rectangle. App state must derive this from [InterfaceSplit], never store it independently. */
internal data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun isValid(minimumDimension: Float = MIN_SPLIT_RATIO): Boolean =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
            left < right && top < bottom &&
            width + 1e-6f >= minimumDimension && height + 1e-6f >= minimumDimension

    fun toPixels(sourceWidth: Int, sourceHeight: Int): PixelCrop? {
        if (!isValid() || sourceWidth <= 0 || sourceHeight <= 0) return null
        val pixelLeft = (left * sourceWidth).roundToInt().coerceIn(0, sourceWidth - 1)
        val pixelTop = (top * sourceHeight).roundToInt().coerceIn(0, sourceHeight - 1)
        val pixelRight = (right * sourceWidth).roundToInt().coerceIn(pixelLeft + 1, sourceWidth)
        val pixelBottom = (bottom * sourceHeight).roundToInt().coerceIn(pixelTop + 1, sourceHeight)
        return PixelCrop(pixelLeft, pixelTop, pixelRight, pixelBottom)
    }

    companion object {
        val FullFrame = NormalizedCrop(0f, 0f, 1f, 1f)
    }
}

internal data class PixelCrop(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal data class MirrorSourceGeometry(
    val width: Int,
    val height: Int,
    val rendererCapability: Int,
    val generation: Long,
    val gameId: String = "",
    val orientation: SourceOrientation = SourceOrientation.NORMAL,
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 0f
    val isSupported: Boolean get() = width > 0 && height > 0 && generation > 0L && rendererCapability > 0
}

internal enum class SourceOrientation(val wireValue: Int) {
    NORMAL(0),
    ROTATE_90(1),
    ROTATE_180(2),
    ROTATE_270(3),
    ;

    val swapsDimensions: Boolean get() = this == ROTATE_90 || this == ROTATE_270

    companion object {
        fun fromWireValue(value: Int): SourceOrientation =
            entries.firstOrNull { it.wireValue == value } ?: NORMAL
    }
}

internal data class MirrorCropProfile(
    val split: InterfaceSplit = InterfaceSplit.Default,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceAspectRatio: Float = 0f,
    val schemaVersion: Int = MIRROR_CROP_SCHEMA_VERSION,
    val confirmed: Boolean = false,
    val requiresReview: Boolean = true,
) {
    val crop: NormalizedCrop get() = split.interfaceCrop

    fun isCompatibleWith(geometry: MirrorSourceGeometry): Boolean =
        schemaVersion == MIRROR_CROP_SCHEMA_VERSION && confirmed && !requiresReview &&
            split.isValid() && geometry.isSupported && sourceWidth == geometry.width &&
            sourceHeight == geometry.height && sourceAspectRatio.isFinite() &&
            abs(sourceAspectRatio - geometry.aspectRatio) <= SOURCE_ASPECT_TOLERANCE

    companion object {
        val Empty = MirrorCropProfile()
    }
}

internal data class AspectFit(val width: Int, val height: Int, val x: Int, val y: Int)

internal fun aspectFit(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): AspectFit? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
    var width = targetWidth
    var height = (targetWidth.toLong() * sourceHeight / sourceWidth).toInt().coerceAtLeast(1)
    if (height > targetHeight) {
        height = targetHeight
        width = (targetHeight.toLong() * sourceWidth / sourceHeight).toInt().coerceAtLeast(1)
    }
    return AspectFit(width, height, (targetWidth - width) / 2, (targetHeight - height) / 2)
}

internal data class CropEditorModel(
    val split: InterfaceSplit,
    val sourceHeight: Int,
    val sourceAspectRatio: Float,
) {
    val crop: NormalizedCrop get() = split.interfaceCrop

    fun reset(): CropEditorModel = copy(split = InterfaceSplit.Default.snappedTo(sourceHeight))

    fun withSplitRatio(ratio: Float): CropEditorModel =
        copy(split = InterfaceSplit(ratio.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)).snappedTo(sourceHeight))

    fun nudgeUp(step: Float = FINE_ADJUSTMENT_STEP): CropEditorModel = withSplitRatio(split.ratio - step)
    fun nudgeDown(step: Float = FINE_ADJUSTMENT_STEP): CropEditorModel = withSplitRatio(split.ratio + step)

    companion object {
        fun create(split: InterfaceSplit, geometry: MirrorSourceGeometry): CropEditorModel = CropEditorModel(
            split = split.takeIf { it.isValid() }?.snappedTo(geometry.height)
                ?: InterfaceSplit.Default.snappedTo(geometry.height),
            sourceHeight = geometry.height,
            sourceAspectRatio = geometry.aspectRatio,
        )
    }
}

internal const val FINE_ADJUSTMENT_STEP = 0.0075f

internal enum class CropAcknowledgementResult(val wireValue: Int) {
    APPLIED(1), REJECTED(2), INCOMPATIBLE_GEOMETRY(3), INVALID_RECTANGLE(4),
    UNSUPPORTED_SOURCE(5), STALE_GENERATION(6),
    ;

    companion object {
        fun fromWireValue(value: Int) = entries.firstOrNull { it.wireValue == value } ?: REJECTED
    }
}

internal data class CropAcknowledgement(
    val result: CropAcknowledgementResult,
    val cropGeneration: Long,
    val geometryGeneration: Long,
    val diagnostic: String,
)

internal data class MirrorCropRequest(
    val crop: NormalizedCrop,
    val geometryGeneration: Long,
)

/** Suppresses identical reconciliation requests without delaying a changed editor preview. */
internal class MirrorCropApplicationGate {
    private var lastRequested: MirrorCropRequest? = null

    fun begin(request: MirrorCropRequest): Boolean {
        if (!request.crop.isValid() || request.geometryGeneration <= 0) return false
        if (request == lastRequested) return false
        lastRequested = request
        return true
    }

    fun invalidate() {
        lastRequested = null
    }
}

internal class CropSaveGate {
    var pendingGeneration: Long? = null
        private set
    private var pendingSplit: InterfaceSplit? = null

    fun begin(generation: Long, split: InterfaceSplit): Boolean {
        if (generation <= 0 || !split.isValid()) return false
        pendingGeneration = generation
        pendingSplit = split
        return true
    }

    fun acknowledge(acknowledgement: CropAcknowledgement): InterfaceSplit? {
        if (acknowledgement.cropGeneration != pendingGeneration) return null
        if (acknowledgement.result != CropAcknowledgementResult.APPLIED) {
            cancel()
            return null
        }
        return pendingSplit.also { cancel() }
    }

    fun cancel() {
        pendingGeneration = null
        pendingSplit = null
    }
}

internal class CropEditTransaction(savedSplit: InterfaceSplit) {
    val savedSplit: InterfaceSplit = savedSplit.takeIf { it.isValid() } ?: InterfaceSplit.Default
    var currentSplit: InterfaceSplit = this.savedSplit
        private set

    fun update(split: InterfaceSplit) {
        if (split.isValid()) currentSplit = split
    }

    fun cancel(): InterfaceSplit = savedSplit
}

internal data class SplitEditorCompletion(
    val split: InterfaceSplit,
    val shouldPersist: Boolean,
    val editorVisible: Boolean = false,
)

internal fun cancelSplitEditor(transaction: CropEditTransaction?, fallback: InterfaceSplit) =
    SplitEditorCompletion(
        split = transaction?.cancel() ?: fallback,
        shouldPersist = false,
    )

internal fun saveSplitEditor(split: InterfaceSplit) = SplitEditorCompletion(
    split = split,
    shouldPersist = true,
)

internal fun shouldShowSplitEditorControls(editorModel: CropEditorModel?): Boolean = editorModel != null
