package com.jamesmoran.adventurepad

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Read-only producer surface for the ScummVM dual-surface rendering proof. */
internal class MirrorSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private val generations = MirrorSurfaceGenerationState(MirrorSurfaceGenerations::next)
    private var lifecycleActive = false
    private var currentDisplayId = Display.INVALID_DISPLAY
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var attachmentSent = false
    private var disposed = false

    init {
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        holder.setFormat(PixelFormat.OPAQUE)
        holder.addCallback(this)
    }

    fun activate(displayId: Int) {
        if (disposed) return
        lifecycleActive = true
        currentDisplayId = displayId
        publishFreshAttachment()
    }

    fun refreshAttachment(displayId: Int) {
        if (!lifecycleActive || disposed) return
        currentDisplayId = displayId
        detachCurrentGeneration()
        publishFreshAttachment()
    }

    fun deactivate() {
        lifecycleActive = false
        detachCurrentGeneration()
    }

    fun dispose() {
        if (disposed) return
        deactivate()
        disposed = true
        holder.removeCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        publishFreshAttachment()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        detachCurrentGeneration()
        publishFreshAttachment()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        detachCurrentGeneration()
        surfaceWidth = 0
        surfaceHeight = 0
    }

    private fun publishFreshAttachment() {
        val surface = holder.surface
        if (!lifecycleActive || disposed || !surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) {
            return
        }
        if (generations.activeGeneration != null) return

        val generation = generations.beginAttachment()
        attachmentSent = ScummVMInputClient.attachMirrorSurface(
            surface = surface,
            generation = generation,
            width = surfaceWidth,
            height = surfaceHeight,
            displayId = currentDisplayId,
        )
    }

    private fun detachCurrentGeneration() {
        val generation = generations.invalidate() ?: return
        if (attachmentSent) {
            ScummVMInputClient.detachMirrorSurface(generation)
        }
        attachmentSent = false
    }
}
