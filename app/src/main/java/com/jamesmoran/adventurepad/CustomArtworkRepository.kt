package com.jamesmoran.adventurepad

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns user-imported launcher card artwork in durable, app-private storage. */
internal class CustomArtworkRepository private constructor(
    private val directory: File,
) {
    fun artworkFile(targetId: String): File = File(directory, artworkFileName(targetId))

    fun hasArtwork(targetId: String): Boolean =
        targetId.isNotBlank() && artworkFile(targetId).isFile

    suspend fun import(targetId: String, input: InputStream) = withContext(Dispatchers.IO) {
        require(targetId.isNotBlank())
        if (!directory.exists() && !directory.mkdirs()) {
            throw CustomArtworkException("AdventurePad could not create its custom artwork folder.")
        }

        val destination = artworkFile(targetId)
        val temporary = File.createTempFile("artwork-", ".pending", directory)
        try {
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_ARTWORK_BYTES) {
                            throw CustomArtworkException("That image is too large. Choose an image under 32 MB.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            validate(temporary)
            replaceAtomically(temporary, destination)
        } catch (exception: CustomArtworkException) {
            throw exception
        } catch (exception: Exception) {
            throw CustomArtworkException("AdventurePad could not read that image.", exception)
        } finally {
            temporary.delete()
        }
    }

    suspend fun remove(targetId: String): Boolean = withContext(Dispatchers.IO) {
        require(targetId.isNotBlank())
        val file = artworkFile(targetId)
        !file.exists() || file.delete()
    }

    private fun validate(file: File) {
        if (file.length() == 0L) throw CustomArtworkException("That file is empty.")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outMimeType !in SUPPORTED_MIME_TYPES) {
            throw CustomArtworkException("Choose a valid PNG, JPEG, or WEBP image.")
        }
        var sampleSize = 1
        while (options.outWidth / sampleSize > VALIDATION_MAX_DIMENSION ||
            options.outHeight / sampleSize > VALIDATION_MAX_DIMENSION
        ) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw CustomArtworkException("That image is corrupt or unreadable.")
        decoded.recycle()
    }

    private fun replaceAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    internal companion object {
        const val DIRECTORY_NAME = "custom_artwork"
        private const val MAX_ARTWORK_BYTES = 32L * 1024 * 1024
        private const val VALIDATION_MAX_DIMENSION = 2048
        private val SUPPORTED_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")

        fun create(context: Context): CustomArtworkRepository =
            CustomArtworkRepository(File(context.applicationContext.filesDir, DIRECTORY_NAME))

        fun create(directory: File): CustomArtworkRepository = CustomArtworkRepository(directory)

        fun artworkFileName(targetId: String): String {
            require(targetId.isNotBlank())
            val key = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(targetId.toByteArray(StandardCharsets.UTF_8))
            return "$key.artwork"
        }
    }
}

internal class CustomArtworkException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
