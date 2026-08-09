package com.jamesmoran.adventurepad

import android.graphics.BitmapFactory
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
    SkinArtwork(listOf(slot), modifier)
}

@Composable
internal fun SkinArtwork(slots: Array<String>, modifier: Modifier = Modifier) {
    SkinArtwork(slots.asList(), modifier)
}

@Composable
private fun SkinArtwork(slots: List<String>, modifier: Modifier) {
    val skin = AdventurePadSkinTokens.current ?: return
    val slot = skin.resolveAssetSlot(*slots.toTypedArray()) ?: return
    val asset = skin.skin.manifest.assets.getValue(slot)
    val file = skin.assetFile(slot) ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    }
    bitmap?.let {
        if (asset.scale == SkinScaleMode.NINE_SLICE && asset.sliceInsets != null) {
            NineSliceImage(it, asset.sliceInsets, modifier)
        } else {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = when (asset.scale) {
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
    val skin = AdventurePadSkinTokens.current ?: return
    val resolvedSlot = skin.resolveAssetSlot(slot) ?: return
    val asset = skin.skin.manifest.assets.getValue(resolvedSlot)
    val insets = asset.sliceInsets ?: return
    val file = skin.assetFile(resolvedSlot) ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    }
    bitmap?.let { NineSliceImage(it, insets, modifier, drawCenter = false) }
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
