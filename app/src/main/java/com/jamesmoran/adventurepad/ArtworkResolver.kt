package com.jamesmoran.adventurepad

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves launcher box art from target-specific and canonical game asset paths. */
internal class ArtworkResolver private constructor(
    context: Context,
    private val requestedWidthPx: Int,
) {
    private val assets = context.applicationContext.assets
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_SIZE_KIB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width.toLong() * value.height * BYTES_PER_PIXEL / 1024)
                .coerceIn(1, Int.MAX_VALUE.toLong())
                .toInt()
    }
    private val missingArtwork = mutableSetOf<String>()

    suspend fun resolve(targetId: String, gameId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        artworkPaths(targetId, gameId).firstNotNullOfOrNull { path -> resolvePath(path) }
    }

    private fun resolvePath(path: String): ImageBitmap? {
        synchronized(cache) {
            cache.get(path)?.let { return it }
            if (path in missingArtwork) return null
        }

        val artwork = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val sampleSize = calculateSampleSize(bounds.outWidth, requestedWidthPx)
            assets.open(path).use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize },
                )?.asImageBitmap()
            }
        }.getOrNull()

        synchronized(cache) {
            if (artwork == null) missingArtwork.add(path) else cache.put(path, artwork)
        }
        return artwork
    }

    internal companion object {
        private const val CACHE_SIZE_KIB = 24 * 1024
        private const val BYTES_PER_PIXEL = 4

        fun create(context: Context, requestedWidthPx: Int): ArtworkResolver =
            ArtworkResolver(context, requestedWidthPx.coerceAtLeast(1))

        fun artworkPath(targetId: String): String = "artwork/$targetId/box.png"

        fun artworkPaths(targetId: String, gameId: String): List<String> =
            listOf(targetId, gameId)
                .filter(String::isNotBlank)
                .distinct()
                .map(::artworkPath)

        fun calculateSampleSize(sourceWidth: Int, requestedWidth: Int): Int {
            var sampleSize = 1
            while (sourceWidth / (sampleSize * 2) >= requestedWidth) sampleSize *= 2
            return sampleSize
        }
    }
}
