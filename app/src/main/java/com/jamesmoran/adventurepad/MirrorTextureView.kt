package com.jamesmoran.adventurepad

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewConfiguration
import java.util.concurrent.atomic.AtomicLong

/** TextureView-backed producer surface for the lower mirror A/B experiment. */
internal class MirrorTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener, MirrorHost {
    private val generations = MirrorSurfaceGenerationState(MirrorSurfaceGenerations::next)
    private val attachmentGate = MirrorAttachmentGate()
    private var lifecycleActive = false
    private var mirrorRequired = false
    private var messengerConnected = false
    private var currentDisplayId = Display.INVALID_DISPLAY
    private var mirrorSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceAvailable = false
    private var surfaceEpoch = 0L
    private var attachmentSent = false
    private var disposed = false
    private var lastEligibilityDiagnostic: String? = null
    private var directTouchGeometry: LowerPanelGeometry? = null
    private var directTouchCrop: NormalizedCrop? = null
    private var directTouchSourceGeometry: MirrorSourceGeometry? = null
    private var directTouchCropGeneration = 0L
    private var directTouchGeometryGeneration = 0L
    private var directTouchEnabled = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeSequenceId = 0L
    private var lastSourcePoint: SourcePoint? = null
    private val touchTracker = LowerPanelTouchTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat())

    override val view get() = this

    init {
        isOpaque = true
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        surfaceTextureListener = this
        log("host created isAvailable=$isAvailable")
    }

    override fun configureDirectTouch(
        crop: NormalizedCrop?,
        geometry: MirrorSourceGeometry?,
        cropGeneration: Long,
        enabled: Boolean,
    ) {
        val nextEnabled = enabled && crop != null && geometry?.isSupported == true && cropGeneration > 0
        val configurationChanged = directTouchCrop != crop ||
            directTouchSourceGeometry != geometry || directTouchCropGeneration != cropGeneration ||
            directTouchEnabled != nextEnabled
        if (configurationChanged) cancelDirectTouch(sendCancel = true)
        directTouchEnabled = nextEnabled
        directTouchCrop = crop
        directTouchSourceGeometry = geometry
        directTouchCropGeneration = cropGeneration
        directTouchGeometryGeneration = geometry?.generation ?: 0L
        rebuildDirectTouchGeometry()
        if (!directTouchEnabled) cancelDirectTouch()
    }

    override fun activate(displayId: Int) {
        if (disposed) return
        mirrorRequired = true
        lifecycleActive = true
        messengerConnected = ScummVMInputClient.isConnected()
        currentDisplayId = displayId
        log("activate isAvailable=$isAvailable surfaceValid=${mirrorSurface?.isValid == true}")
        reconcileAttachment("activate")
    }

    override fun refreshAttachment(displayId: Int) {
        if (!lifecycleActive || disposed) return
        currentDisplayId = displayId
        messengerConnected = ScummVMInputClient.isConnected()
        detachCurrentGeneration("reconnect")
        reconcileAttachment("reconnect attach")
    }

    override fun ownsGeneration(generation: Long): Boolean =
        generation > 0 && generations.activeGeneration == generation

    override fun deactivate() {
        mirrorRequired = false
        lifecycleActive = false
        detachCurrentGeneration("deactivate")
        logEligibility("deactivate")
    }

    override fun dispose() {
        if (disposed) return
        deactivate()
        disposed = true
        surfaceTextureListener = null
        releaseJavaSurface("dispose")
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (disposed) return
        detachCurrentGeneration("replacement surface")
        releaseJavaSurface("replacement surface")
        mirrorSurface = Surface(surfaceTexture)
        surfaceAvailable = true
        surfaceEpoch += 1
        updateSurfaceSize(width, height)
        rebuildDirectTouchGeometry()
        log(
            "surface available dimensions=${surfaceWidth}x$surfaceHeight epoch=$surfaceEpoch " +
                "isAvailable=$isAvailable surfaceValid=${mirrorSurface?.isValid == true}",
        )
        reconcileAttachment("surface available")
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        updateSurfaceSize(width, height)
        rebuildDirectTouchGeometry()
        log(
            "surface size changed dimensions=${surfaceWidth}x$surfaceHeight epoch=$surfaceEpoch " +
                "surfaceValid=${mirrorSurface?.isValid == true}",
        )
        // The Surface remains backed by the same SurfaceTexture; the existing generation stays attached.
        reconcileAttachment("surface size changed")
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        cancelDirectTouch(sendCancel = true)
        log("surface destroyed epoch=$surfaceEpoch generation=${generations.activeGeneration}")
        detachCurrentGeneration("surface destroyed")
        surfaceAvailable = false
        surfaceWidth = 0
        surfaceHeight = 0
        releaseJavaSurface("surface destroyed")
        logEligibility("surface destroyed")
        // TextureView retains ownership and may release the SurfaceTexture after this callback.
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildDirectTouchGeometry()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!directTouchEnabled || disposed) return true
        val geometry = directTouchGeometry ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val id = event.getPointerId(event.actionIndex)
                val point = geometry.mapTouch(event.x, event.y)
                if (touchTracker.begin(id, event.x, event.y, point) == null) return true
                activePointerId = id
                activeSequenceId = nextPointerSequence.incrementAndGet()
                lastSourcePoint = point
                sendPointer(checkNotNull(point), AbsoluteSourcePointerAction.DOWN)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1 || activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    cancelDirectTouch(sendCancel = true)
                    return true
                }
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    cancelDirectTouch(sendCancel = true)
                    return true
                }
                val x = event.getX(index)
                val y = event.getY(index)
                val point = geometry.mapTouch(x, y)
                when (touchTracker.move(activePointerId, x, y, point)) {
                    AbsoluteSourcePointerAction.MOVE -> {
                        lastSourcePoint = point
                        if (point != null) sendPointer(point, AbsoluteSourcePointerAction.MOVE)
                    }
                    AbsoluteSourcePointerAction.CANCEL -> cancelDirectTouch(sendCancel = true)
                    else -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> cancelDirectTouch(sendCancel = true)
            MotionEvent.ACTION_UP -> {
                val id = event.getPointerId(event.actionIndex)
                val point = geometry.mapTouch(event.x, event.y)
                if (touchTracker.end(id, point) == AbsoluteSourcePointerAction.UP && point != null) {
                    lastSourcePoint = point
                    sendPointer(point, AbsoluteSourcePointerAction.UP)
                    clearDirectTouchState()
                    performClick()
                } else {
                    cancelDirectTouch(sendCancel = true)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelDirectTouch(sendCancel = true)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun reconcileAttachment(reason: String) {
        val surface = mirrorSurface
        val eligibility = MirrorAttachmentEligibility(
            mirrorRequired = mirrorRequired,
            lifecycleActive = lifecycleActive && !disposed,
            messengerConnected = messengerConnected,
            surfaceAvailable = surfaceAvailable,
            surfaceValid = surface?.isValid == true,
            width = surfaceWidth,
            height = surfaceHeight,
            surfaceEpoch = surfaceEpoch,
        )
        logEligibility(reason, eligibility)
        if (surface == null || !attachmentGate.shouldAttach(eligibility)) return

        val generation = generations.beginAttachment()
        val sent = ScummVMInputClient.attachMirrorSurface(
            surface = surface,
            generation = generation,
            width = surfaceWidth,
            height = surfaceHeight,
            displayId = currentDisplayId,
        )
        if (sent) {
            attachmentSent = true
            attachmentGate.markRequested(surfaceEpoch)
            log("ATTACH sent dimensions=${surfaceWidth}x$surfaceHeight generation=$generation epoch=$surfaceEpoch reason=$reason")
        } else {
            generations.invalidate()
            attachmentSent = false
            log("ATTACH not sent generation=$generation epoch=$surfaceEpoch reason=$reason")
        }
    }

    private fun detachCurrentGeneration(reason: String) {
        cancelDirectTouch(sendCancel = true)
        val generation = generations.invalidate()
        if (generation != null && attachmentSent) {
            ScummVMInputClient.detachMirrorSurface(generation)
            log("DETACH sent generation=$generation epoch=$surfaceEpoch reason=$reason")
        }
        attachmentSent = false
        attachmentGate.invalidate()
    }

    private fun updateSurfaceSize(callbackWidth: Int, callbackHeight: Int) {
        surfaceWidth = callbackWidth.takeIf { it > 0 } ?: width.coerceAtLeast(0)
        surfaceHeight = callbackHeight.takeIf { it > 0 } ?: height.coerceAtLeast(0)
    }

    private fun rebuildDirectTouchGeometry() {
        val crop = directTouchCrop
        val geometry = directTouchSourceGeometry
        directTouchGeometry = if (directTouchEnabled && crop != null && geometry != null) {
            lowerPanelGeometry(width, height, crop, geometry.width, geometry.height, geometry.orientation)
        } else null
    }

    private fun sendPointer(point: SourcePoint, action: AbsoluteSourcePointerAction) {
        ScummVMInputClient.sendAbsoluteSourcePointer(
            AbsoluteSourcePointerCommand(
                point = point,
                action = action,
                cropGeneration = directTouchCropGeneration,
                geometryGeneration = directTouchGeometryGeneration,
                pointerSequenceId = activeSequenceId,
            ),
            activePointerId,
        )
    }

    private fun cancelDirectTouch(sendCancel: Boolean = false) {
        if (sendCancel && activePointerId != MotionEvent.INVALID_POINTER_ID && activeSequenceId > 0) {
            lastSourcePoint?.let { sendPointer(it, AbsoluteSourcePointerAction.CANCEL) }
        }
        touchTracker.cancel()
        clearDirectTouchState()
    }

    private fun clearDirectTouchState() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeSequenceId = 0L
        lastSourcePoint = null
    }

    private fun releaseJavaSurface(reason: String) {
        val surface = mirrorSurface ?: return
        val wasValid = surface.isValid
        surface.release()
        mirrorSurface = null
        log("Surface released epoch=$surfaceEpoch wasValid=$wasValid reason=$reason")
    }

    private fun logEligibility(
        reason: String,
        eligibility: MirrorAttachmentEligibility = MirrorAttachmentEligibility(
            mirrorRequired = mirrorRequired,
            lifecycleActive = lifecycleActive && !disposed,
            messengerConnected = messengerConnected,
            surfaceAvailable = surfaceAvailable,
            surfaceValid = mirrorSurface?.isValid == true,
            width = surfaceWidth,
            height = surfaceHeight,
            surfaceEpoch = surfaceEpoch,
        ),
    ) {
        val diagnostic = "required=${eligibility.mirrorRequired} lifecycle=${eligibility.lifecycleActive} " +
            "connected=${eligibility.messengerConnected} available=${eligibility.surfaceAvailable} " +
            "valid=${eligibility.surfaceValid} dimensions=${eligibility.width}x${eligibility.height} " +
            "epoch=${eligibility.surfaceEpoch} eligibility=${eligibility.blockingReason}"
        if (diagnostic == lastEligibilityDiagnostic) return
        lastEligibilityDiagnostic = diagnostic
        log("attachment eligibility reason=$reason $diagnostic")
    }

    private fun log(message: String) {
        Log.i(MIRROR_HOST_LOG_TAG, "mode=TEXTURE_VIEW $message")
    }

    private companion object {
        val nextPointerSequence = AtomicLong(android.os.SystemClock.elapsedRealtime().coerceAtLeast(1L))
    }
}
