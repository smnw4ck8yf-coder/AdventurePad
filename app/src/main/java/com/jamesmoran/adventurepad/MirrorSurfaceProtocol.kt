package com.jamesmoran.adventurepad

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

internal object MirrorSurfaceProtocol {
    const val MSG_ATTACH_SURFACE = 100
    const val MSG_DETACH_SURFACE = 101
    const val MSG_STATUS = 102
    const val MSG_QUERY_GEOMETRY = 103
    const val MSG_GEOMETRY = 104
    const val MSG_APPLY_CROP = 105
    const val MSG_CROP_ACK = 106
    const val MSG_APPLY_DISPLAY_MODE = 107
    const val MSG_DISPLAY_MODE_ACK = 108
    const val MSG_ABSOLUTE_SOURCE_POINTER = 109
    const val MSG_CURSOR_POSITION = 110

    const val KEY_SURFACE = "mirrorSurface"
    const val KEY_GENERATION = "surfaceGeneration"
    const val KEY_WIDTH = "width"
    const val KEY_HEIGHT = "height"
    const val KEY_DISPLAY_ID = "displayId"
    const val KEY_STATUS = "status"
    const val KEY_DIAGNOSTIC = "diagnostic"
    const val KEY_SOURCE_WIDTH = "sourceWidth"
    const val KEY_SOURCE_HEIGHT = "sourceHeight"
    const val KEY_RENDERER_CAPABILITY = "rendererCapability"
    const val KEY_GEOMETRY_GENERATION = "geometryGeneration"
    const val KEY_GAME_ID = "gameId"
    const val KEY_CROP_GENERATION = "cropGeneration"
    const val KEY_EXPECTED_GEOMETRY_GENERATION = "expectedGeometryGeneration"
    const val KEY_LEFT = "cropLeft"
    const val KEY_TOP = "cropTop"
    const val KEY_RIGHT = "cropRight"
    const val KEY_BOTTOM = "cropBottom"
    const val KEY_CROP_RESULT = "cropResult"
    const val KEY_DISPLAY_MODE = "displayMode"
    const val KEY_MODE_GENERATION = "modeGeneration"
    const val KEY_MODE_RESULT = "modeResult"
    const val KEY_ORIENTATION = "sourceOrientation"
    const val KEY_SOURCE_X = "sourceX"
    const val KEY_SOURCE_Y = "sourceY"
    const val KEY_POINTER_ACTION = "pointerAction"
    const val KEY_POINTER_ID = "pointerId"
    const val KEY_POINTER_SEQUENCE_ID = "pointerSequenceId"
    const val KEY_CURSOR_VISIBLE = "cursorVisible"

    const val STATUS_SUPPORTED = 1
    const val STATUS_UNSUPPORTED_NO_TEXTURE = 2
    const val STATUS_ATTACHED = 3
    const val STATUS_DETACHED = 4
    const val STATUS_FAILED = 5
}

internal enum class MirrorOutputState(val wireValue: Int, val label: String) {
    WAITING(0, "WAITING FOR SCUMMVM"),
    SUPPORTED(MirrorSurfaceProtocol.STATUS_SUPPORTED, "LIVE"),
    UNSUPPORTED_NO_TEXTURE(
        MirrorSurfaceProtocol.STATUS_UNSUPPORTED_NO_TEXTURE,
        "UNAVAILABLE FOR CURRENT RENDERER",
    ),
    ATTACHED(MirrorSurfaceProtocol.STATUS_ATTACHED, "SURFACE ATTACHED"),
    DETACHED(MirrorSurfaceProtocol.STATUS_DETACHED, "DETACHED"),
    FAILED(MirrorSurfaceProtocol.STATUS_FAILED, "MIRROR FAILED"),
    ;

    companion object {
        fun fromWireValue(value: Int): MirrorOutputState =
            entries.firstOrNull { it.wireValue == value } ?: FAILED
    }
}

internal val MirrorOutputState.shouldShowLiveSurface: Boolean
    get() = this == MirrorOutputState.SUPPORTED

internal data class MirrorOutputStatus(
    val state: MirrorOutputState = MirrorOutputState.WAITING,
    val generation: Long = 0,
    val diagnostic: String = "Waiting for mirror capability.",
)

internal data class MirrorCursorState(
    val point: SourcePoint = SourcePoint(0, 0),
    val visible: Boolean = false,
    val geometryGeneration: Long = 0,
)

internal data class MirrorAttachmentEligibility(
    val mirrorRequired: Boolean,
    val lifecycleActive: Boolean,
    val messengerConnected: Boolean,
    val surfaceAvailable: Boolean,
    val surfaceValid: Boolean,
    val width: Int,
    val height: Int,
    val surfaceEpoch: Long,
) {
    val blockingReason: String
        get() = when {
            !mirrorRequired -> "mirror not required"
            !lifecycleActive -> "lifecycle inactive"
            !messengerConnected -> "Messenger disconnected"
            !surfaceAvailable -> "surface unavailable"
            !surfaceValid -> "surface invalid"
            width <= 0 || height <= 0 -> "surface size ${width}x$height"
            surfaceEpoch <= 0 -> "surface epoch unavailable"
            else -> "eligible"
        }

    val canAttach: Boolean
        get() = blockingReason == "eligible"
}

/** Single-flight ownership for one Android Surface lifecycle epoch. */
internal class MirrorAttachmentGate {
    var requestedSurfaceEpoch: Long? = null
        private set

    fun shouldAttach(eligibility: MirrorAttachmentEligibility): Boolean =
        eligibility.canAttach && requestedSurfaceEpoch != eligibility.surfaceEpoch

    fun markRequested(surfaceEpoch: Long) {
        check(surfaceEpoch > 0) { "Surface epochs must be positive" }
        requestedSurfaceEpoch = surfaceEpoch
    }

    fun invalidate() {
        requestedSurfaceEpoch = null
    }
}

internal class MirrorSurfaceGenerationState(
    private val nextGeneration: () -> Long,
) {
    var activeGeneration: Long? = null
        private set

    fun beginAttachment(): Long = nextGeneration().also { generation ->
        check(activeGeneration == null) { "A mirror surface generation is already active" }
        check(generation > 0) { "Mirror surface generations must be positive" }
        activeGeneration = generation
    }

    fun consumeMatchingDetach(generation: Long): Boolean {
        if (activeGeneration != generation) return false
        activeGeneration = null
        return true
    }

    fun invalidate(): Long? = activeGeneration.also { activeGeneration = null }
}

internal object MirrorSurfaceGenerations {
    // Android's monotonic clock keeps generations increasing across Activity and process recreation.
    private val nextGeneration = AtomicLong(SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L))

    fun next(): Long = nextGeneration.incrementAndGet()
}

internal object MirrorCropGenerations {
    private val nextGeneration = AtomicLong(SystemClock.elapsedRealtime().coerceAtLeast(1L))

    fun next(): Long = nextGeneration.incrementAndGet()
}

internal object DisplayModeGenerations {
    // This generation crosses JNI in a double array, so keep it exactly representable.
    private val nextGeneration = AtomicLong(SystemClock.elapsedRealtime().coerceAtLeast(1L))

    fun next(): Long = nextGeneration.incrementAndGet()
}
