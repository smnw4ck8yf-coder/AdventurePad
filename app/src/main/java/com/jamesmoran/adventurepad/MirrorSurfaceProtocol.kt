package com.jamesmoran.adventurepad

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

internal object MirrorSurfaceProtocol {
    const val MSG_ATTACH_SURFACE = 100
    const val MSG_DETACH_SURFACE = 101
    const val MSG_STATUS = 102

    const val KEY_SURFACE = "mirrorSurface"
    const val KEY_GENERATION = "surfaceGeneration"
    const val KEY_WIDTH = "width"
    const val KEY_HEIGHT = "height"
    const val KEY_DISPLAY_ID = "displayId"
    const val KEY_STATUS = "status"
    const val KEY_DIAGNOSTIC = "diagnostic"

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
