package com.jamesmoran.adventurepad

import android.content.Context
import android.util.Log
import android.view.View

/** Internal A/B switch for the lower mirror's Android presentation path. */
internal enum class MirrorHostMode {
    SURFACE_VIEW,
    TEXTURE_VIEW,
}

internal const val USE_TEXTURE_VIEW_MIRROR = true

internal val activeMirrorHostMode: MirrorHostMode
    get() = if (USE_TEXTURE_VIEW_MIRROR) MirrorHostMode.TEXTURE_VIEW else MirrorHostMode.SURFACE_VIEW

internal data class MirrorHostAttachmentMetadata(
    val width: Int,
    val height: Int,
    val displayId: Int,
)

/** Both host variants publish the same non-surface ATTACH fields. */
internal fun MirrorHostMode.attachmentMetadata(
    width: Int,
    height: Int,
    displayId: Int,
): MirrorHostAttachmentMetadata = MirrorHostAttachmentMetadata(width, height, displayId)

/** Host-neutral contract used by the Activity and Compose layout. */
internal interface MirrorHost {
    val view: View

    fun configureDirectTouch(
        crop: NormalizedCrop?,
        geometry: MirrorSourceGeometry?,
        cropGeneration: Long,
        enabled: Boolean,
    )

    fun activate(displayId: Int)
    fun refreshAttachment(displayId: Int)
    fun ownsGeneration(generation: Long): Boolean
    fun deactivate()
    fun dispose()
}

internal fun createMirrorHost(context: Context): MirrorHost = when (activeMirrorHostMode) {
    MirrorHostMode.SURFACE_VIEW -> SurfaceViewMirrorHost(context)
    MirrorHostMode.TEXTURE_VIEW -> MirrorTextureView(context)
}.also { host ->
    Log.i(MIRROR_HOST_LOG_TAG, "host mode=$activeMirrorHostMode host created view=${host.view.javaClass.simpleName}")
}

/** Delegates to the existing SurfaceView implementation without changing the control path. */
private class SurfaceViewMirrorHost(context: Context) : MirrorHost {
    private val surfaceView = MirrorSurfaceView(context)

    override val view: View = surfaceView

    override fun configureDirectTouch(
        crop: NormalizedCrop?,
        geometry: MirrorSourceGeometry?,
        cropGeneration: Long,
        enabled: Boolean,
    ) = surfaceView.configureDirectTouch(crop, geometry, cropGeneration, enabled)

    override fun activate(displayId: Int) = surfaceView.activate(displayId)
    override fun refreshAttachment(displayId: Int) = surfaceView.refreshAttachment(displayId)
    override fun ownsGeneration(generation: Long): Boolean = surfaceView.ownsGeneration(generation)
    override fun deactivate() = surfaceView.deactivate()
    override fun dispose() = surfaceView.dispose()
}

internal const val MIRROR_HOST_LOG_TAG = "AdventurePadMirrorHost"
