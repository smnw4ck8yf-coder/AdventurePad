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
    private val customArtworkRepository: CustomArtworkRepository,
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
        resolveCustomArtwork(targetId)
            ?: artworkPaths(targetId, gameId).firstNotNullOfOrNull { path -> resolveAssetPath(path) }
    }

    fun hasCustomArtwork(targetId: String): Boolean = customArtworkRepository.hasArtwork(targetId)

    fun invalidateCustomArtwork(targetId: String) {
        val path = customArtworkRepository.artworkFile(targetId).path
        synchronized(cache) {
            cache.snapshot().keys.filter { it.startsWith("custom:$path:") }.forEach(cache::remove)
            missingArtwork.removeIf { it.startsWith("custom:$path:") }
        }
    }

    private fun resolveCustomArtwork(targetId: String): ImageBitmap? {
        if (targetId.isBlank()) return null
        val file = customArtworkRepository.artworkFile(targetId)
        if (!file.isFile) return null
        val key = "custom:${file.path}:${file.lastModified()}:${file.length()}"
        return resolvePath(key) {
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = imageFileSampleSize(file, requestedWidthPx)
                },
            )?.asImageBitmap()
        }
    }

    private fun resolveAssetPath(path: String): ImageBitmap? = resolvePath("asset:$path") {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        val sampleSize = calculateSampleSize(bounds.outWidth, requestedWidthPx)
        assets.open(path).use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )?.asImageBitmap()
        }
    }

    private fun resolvePath(key: String, decode: () -> ImageBitmap?): ImageBitmap? {
        synchronized(cache) {
            cache.get(key)?.let { return it }
            if (key in missingArtwork) return null
        }

        val artwork = runCatching(decode).getOrNull()

        synchronized(cache) {
            if (artwork == null) missingArtwork.add(key) else cache.put(key, artwork)
        }
        return artwork
    }

    internal companion object {
        private const val CACHE_SIZE_KIB = 24 * 1024
        private const val BYTES_PER_PIXEL = 4

        fun create(
            context: Context,
            requestedWidthPx: Int,
            customArtworkRepository: CustomArtworkRepository = CustomArtworkRepository.create(context),
        ): ArtworkResolver = ArtworkResolver(
            context,
            requestedWidthPx.coerceAtLeast(1),
            customArtworkRepository,
        )

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

        private fun imageFileSampleSize(file: java.io.File, requestedWidth: Int): Int {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            return calculateSampleSize(bounds.outWidth, requestedWidth)
        }
    }
}
