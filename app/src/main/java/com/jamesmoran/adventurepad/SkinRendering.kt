package com.jamesmoran.adventurepad

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LocalResolvedSkin = staticCompositionLocalOf<ResolvedSkin?> { null }

internal object AdventurePadSkinTokens {
    val current: ResolvedSkin?
        @Composable get() = LocalResolvedSkin.current
}

@Composable
internal fun AdventurePadSkinTheme(
    skin: ResolvedSkin,
    nativeTheme: AdventurePadThemeDefinition = skin.theme,
    content: @Composable () -> Unit,
) {
    AdventurePadTheme(theme = nativeTheme) {
        CompositionLocalProvider(LocalResolvedSkin provides skin, content = content)
    }
}

@Composable
internal fun SkinArtwork(slot: String, modifier: Modifier = Modifier) {
    SkinArtwork(listOf(slot), modifier, preserveIntrinsicAspectRatio = false)
}

@Composable
internal fun SkinArtwork(
    slots: Array<String>,
    modifier: Modifier = Modifier,
    preserveIntrinsicAspectRatio: Boolean = false,
) {
    SkinArtwork(slots.asList(), modifier, preserveIntrinsicAspectRatio)
}

@Composable
private fun SkinArtwork(
    slots: List<String>,
    modifier: Modifier,
    preserveIntrinsicAspectRatio: Boolean,
) {
    val skin = AdventurePadSkinTokens.current ?: return
    val slot = skin.resolveAssetSlot(*slots.toTypedArray()) ?: return
    val asset = skin.skin.manifest.assets.getValue(slot)
    val file = skin.assetFile(slot) ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    }
    bitmap?.let {
        if (!preserveIntrinsicAspectRatio &&
            asset.scale == SkinScaleMode.NINE_SLICE && asset.sliceInsets != null
        ) {
            NineSliceImage(it, asset.sliceInsets, modifier)
        } else {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = if (preserveIntrinsicAspectRatio) ContentScale.Fit else when (asset.scale) {
                    SkinScaleMode.COVER -> ContentScale.Crop
                    SkinScaleMode.CONTAIN, SkinScaleMode.NONE -> ContentScale.Fit
                    SkinScaleMode.FILL, SkinScaleMode.NINE_SLICE -> ContentScale.FillBounds
                },
                modifier = modifier,
            )
        }
    }
}

/** Draws only the eight nine-slice border patches, so page content can never be covered by the center. */
@Composable
internal fun SkinFrameArtwork(slot: String, modifier: Modifier = Modifier) {
    val artwork = rememberSkinFrameArtwork(slot) ?: return
    SkinFrameArtwork(artwork, modifier)
}

internal data class ResolvedSkinFrameArtwork(
    val bitmap: ImageBitmap,
    val insets: SkinInsets,
    val visibleBounds: PixelRect,
    val contentBounds: PixelRect,
) {
    fun patches(destinationWidth: Int, destinationHeight: Int): List<NineSlicePatch> =
        calculateBorderNineSlicePatches(
            destinationWidth = destinationWidth,
            destinationHeight = destinationHeight,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            insets = insets,
            sourceOuterBounds = visibleBounds,
        )

    fun contentRect(
        destinationWidth: Int,
        destinationHeight: Int,
        patches: List<NineSlicePatch>,
    ): PixelRect = calculateBorderContentRect(
        destinationWidth = destinationWidth,
        destinationHeight = destinationHeight,
        patches = patches,
        sourceContentBounds = contentBounds,
    )
}

@Composable
internal fun rememberSkinFrameArtwork(slot: String): ResolvedSkinFrameArtwork? {
    val skin = AdventurePadSkinTokens.current ?: return null
    val resolvedSlot = skin.resolveAssetSlot(slot) ?: return null
    val asset = skin.skin.manifest.assets.getValue(resolvedSlot)
    val insets = asset.sliceInsets ?: return null
    val file = skin.assetFile(resolvedSlot) ?: return null
    val artwork by produceState<ResolvedSkinFrameArtwork?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.path)?.let { bitmap ->
                val visibleBounds = bitmap.visiblePixelBounds()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ResolvedSkinFrameArtwork(
                    bitmap = bitmap.asImageBitmap(),
                    insets = insets,
                    visibleBounds = visibleBounds,
                    contentBounds = calculateFrameSourceContentBounds(
                        sourceWidth = bitmap.width,
                        sourceHeight = bitmap.height,
                        insets = insets,
                        visibleBounds = visibleBounds,
                        isVisible = { x, y -> pixels[y * bitmap.width + x] ushr 24 != 0 },
                    ),
                )
            }
        }
    }
    return artwork
}

private fun Bitmap.visiblePixelBounds(): PixelRect {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width
    var top = height
    var right = 0
    var bottom = 0
    pixels.forEachIndexed { index, pixel ->
        if (pixel ushr 24 != 0) {
            val x = index % width
            val y = index / width
            if (x < left) left = x
            if (x + 1 > right) right = x + 1
            if (y < top) top = y
            if (y + 1 > bottom) bottom = y + 1
        }
    }
    return if (right > left && bottom > top) {
        PixelRect(left, top, right, bottom)
    } else {
        PixelRect(0, 0, width, height)
    }
}

@Composable
internal fun SkinFrameArtwork(
    artwork: ResolvedSkinFrameArtwork,
    modifier: Modifier,
) {
    Canvas(modifier) {
        artwork.patches(size.width.toInt(), size.height.toInt()).forEach { patch ->
            if (patch.source.width > 0 && patch.source.height > 0 &&
                patch.destination.width > 0 && patch.destination.height > 0
            ) {
                drawImage(
                    image = artwork.bitmap,
                    srcOffset = IntOffset(patch.source.left, patch.source.top),
                    srcSize = IntSize(patch.source.width, patch.source.height),
                    dstOffset = IntOffset(patch.destination.left, patch.destination.top),
                    dstSize = IntSize(patch.destination.width, patch.destination.height),
                )
            }
        }
    }
}

@Composable
private fun NineSliceImage(
    bitmap: ImageBitmap,
    insets: SkinInsets,
    modifier: Modifier,
    drawCenter: Boolean = true,
) {
    Canvas(modifier) {
        val targetWidth = size.width.toInt()
        val targetHeight = size.height.toInt()
        val targetLeft = minOf(insets.left, targetWidth / 2)
        val targetRight = minOf(insets.right, (targetWidth - targetLeft).coerceAtLeast(0))
        val targetTop = minOf(insets.top, targetHeight / 2)
        val targetBottom = minOf(insets.bottom, (targetHeight - targetTop).coerceAtLeast(0))
        val sourceX = intArrayOf(0, insets.left, bitmap.width - insets.right, bitmap.width)
        val sourceY = intArrayOf(0, insets.top, bitmap.height - insets.bottom, bitmap.height)
        val targetX = intArrayOf(0, targetLeft, targetWidth - targetRight, targetWidth)
        val targetY = intArrayOf(0, targetTop, targetHeight - targetBottom, targetHeight)
        for (row in 0..2) for (column in 0..2) {
            if (!drawCenter && row == 1 && column == 1) continue
            val sourceWidth = sourceX[column + 1] - sourceX[column]
            val sourceHeight = sourceY[row + 1] - sourceY[row]
            val destinationWidth = targetX[column + 1] - targetX[column]
            val destinationHeight = targetY[row + 1] - targetY[row]
            if (sourceWidth > 0 && sourceHeight > 0 && destinationWidth > 0 && destinationHeight > 0) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(sourceX[column], sourceY[row]),
                    srcSize = IntSize(sourceWidth, sourceHeight),
                    dstOffset = IntOffset(targetX[column], targetY[row]),
                    dstSize = IntSize(destinationWidth, destinationHeight),
                )
            }
        }
    }
}

@Composable
internal fun SkinFileArtwork(file: java.io.File?, modifier: Modifier = Modifier) {
    if (file?.isFile != true) return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}
