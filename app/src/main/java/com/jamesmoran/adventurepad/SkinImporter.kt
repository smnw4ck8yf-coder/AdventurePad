package com.jamesmoran.adventurepad

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

internal data class SkinImportResult(val installedSkin: InstalledSkin)

internal enum class SkinImportFailure(val userMessage: String) {
    PACKAGE_UNREADABLE("Package could not be read"),
    INVALID_PACKAGE("Invalid AdventurePad skin"),
    MISSING_MANIFEST("Missing skin.json"),
    MALFORMED_MANIFEST("Invalid AdventurePad skin"),
    UNSUPPORTED_VERSION("Unsupported skin version"),
    MISSING_ASSET("Missing required asset"),
    INVALID_IMAGE("Invalid image"),
    DUPLICATE_ID("Skin ID already installed"),
    RESERVED_ID("This skin ID is reserved by AdventurePad"),
    STORAGE_ERROR("Skin could not be installed"),
}

internal class SkinImportException(
    val failure: SkinImportFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal data class DecodedImageBounds(val width: Int, val height: Int)

internal class SkinImporter(
    private val skinsRoot: File,
    private val versionCode: Int,
    private val imageDecoder: (File) -> DecodedImageBounds? = ::decodeImageSafely,
) {
    fun import(contentResolver: ContentResolver, uri: Uri, replaceExisting: Boolean = false): SkinImportResult {
        ensureRoot()
        val stagingRoot = File(skinsRoot, ".staging-${UUID.randomUUID()}")
        val packageFile = File(stagingRoot, "package.apskin")
        try {
            if (!stagingRoot.mkdir()) storageError("Could not create skin staging directory")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(packageFile).use { output ->
                    copyPackageWithLimit(input, output)
                }
            } ?: throw SkinImportException(
                SkinImportFailure.PACKAGE_UNREADABLE,
                "The selected skin could not be opened",
            )
            return installZip(packageFile, stagingRoot, replaceExisting)
        } catch (exception: SkinImportException) {
            throw exception
        } catch (exception: Exception) {
            throw SkinImportException(
                SkinImportFailure.PACKAGE_UNREADABLE,
                exception.message ?: "Skin import failed",
                exception,
            )
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    @Synchronized
    internal fun installZip(packageFile: File, stagingRoot: File, replaceExisting: Boolean): SkinImportResult {
        val extractionRoot = File(stagingRoot, "contents")
        if (!extractionRoot.mkdir()) storageError("Could not create extraction directory")
        val rootPath = extractionRoot.canonicalPath + File.separator
        var expandedBytes = 0L
        var entryCount = 0
        val caseFoldedPaths = hashSetOf<String>()
        try {
            ZipFile(packageFile).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entryCount > MAX_ENTRIES) invalidPackage("Skin package contains too many files")
                    val path = entry.name
                    try {
                        SkinManifestParser.validateRelativePath(path.removeSuffix("/"))
                    } catch (exception: SkinManifestException) {
                        throw SkinImportException(SkinImportFailure.INVALID_PACKAGE, exception.message.orEmpty(), exception)
                    }
                    val folded = path.lowercase(Locale.ROOT)
                    if (!caseFoldedPaths.add(folded)) invalidPackage("Duplicate or case-colliding path '$path'")
                    if (!entry.isDirectory && !isAllowedPackageFile(path)) invalidPackage("Unsupported file type in '$path'")
                    val destination = File(extractionRoot, path)
                    if (!destination.canonicalPath.startsWith(rootPath)) invalidPackage("Path escapes the package root")
                    if (entry.isDirectory) {
                        if (!destination.mkdirs() && !destination.isDirectory) storageError("Could not create '$path'")
                        continue
                    }
                    val parent = requireNotNull(destination.parentFile)
                    if (!parent.mkdirs() && !parent.isDirectory) {
                        storageError("Could not create parent for '$path'")
                    }
                    archive.getInputStream(entry).use { input ->
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var fileBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                fileBytes += read
                                expandedBytes += read
                                if (fileBytes > MAX_ENTRY_BYTES || expandedBytes > MAX_EXPANDED_BYTES) {
                                    invalidPackage("Skin package exceeds extraction limits")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        } catch (exception: SkinImportException) {
            throw exception
        } catch (exception: Exception) {
            throw SkinImportException(SkinImportFailure.INVALID_PACKAGE, "Malformed ZIP archive", exception)
        }
        val manifestFile = File(extractionRoot, "skin.json")
        val previewFile = File(extractionRoot, "preview.png")
        if (!manifestFile.isFile) {
            throw SkinImportException(SkinImportFailure.MISSING_MANIFEST, "skin.json is required")
        }
        if (!previewFile.isFile) {
            throw SkinImportException(SkinImportFailure.MISSING_ASSET, "preview.png is required")
        }
        val manifest = try {
            SkinManifestParser.parse(readManifest(manifestFile))
        } catch (exception: SkinManifestException) {
            val failure = when (exception.failure) {
                SkinManifestFailure.UNSUPPORTED_VERSION -> SkinImportFailure.UNSUPPORTED_VERSION
                SkinManifestFailure.RESERVED_ID -> SkinImportFailure.RESERVED_ID
                SkinManifestFailure.INVALID -> SkinImportFailure.MALFORMED_MANIFEST
            }
            throw SkinImportException(failure, "Invalid skin.json: ${exception.message}", exception)
        } catch (exception: RuntimeException) {
            throw SkinImportException(SkinImportFailure.MALFORMED_MANIFEST, "Invalid skin.json", exception)
        }
        if (manifest.minimumAdventurePadVersionCode > versionCode) {
            throw SkinImportException(
                SkinImportFailure.UNSUPPORTED_VERSION,
                "This skin requires a newer AdventurePad version",
            )
        }
        val idRoot = File(skinsRoot, manifest.id)
        if (idRoot.exists() && !replaceExisting) {
            throw SkinImportException(SkinImportFailure.DUPLICATE_ID, "Skin ID '${manifest.id}' is already installed")
        }
        validateInstalledContents(extractionRoot, manifest)
        val destination = File(idRoot, manifest.packageVersion)
        if (destination.exists() && !replaceExisting) {
            throw SkinImportException(SkinImportFailure.DUPLICATE_ID, "Skin ID '${manifest.id}' is already installed")
        }
        if (!replaceExisting) {
            val preparedIdRoot = File(stagingRoot, ".install-${UUID.randomUUID()}")
            if (!preparedIdRoot.mkdir()) storageError("Could not prepare the skin installation")
            val preparedVersion = File(preparedIdRoot, manifest.packageVersion)
            if (!extractionRoot.renameTo(preparedVersion)) storageError("Could not prepare the skin installation")
            if (!preparedIdRoot.renameTo(idRoot)) {
                if (idRoot.exists()) {
                    throw SkinImportException(SkinImportFailure.DUPLICATE_ID, "Skin ID '${manifest.id}' is already installed")
                }
                storageError("Could not finalize the skin installation")
            }
            return SkinImportResult(InstalledSkin(manifest, destination))
        }
        val idRootExisted = idRoot.exists()
        if (!idRoot.mkdirs() && !idRoot.isDirectory) storageError("Could not create the skin library")
        val backup = File(idRoot, ".backup-${manifest.packageVersion}-${UUID.randomUUID()}")
        if (destination.exists() && !destination.renameTo(backup)) storageError("Could not stage the installed version")
        if (!extractionRoot.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            if (!idRootExisted && idRoot.listFiles().isNullOrEmpty()) idRoot.delete()
            storageError("Could not finalize the skin installation")
        }
        if (backup.exists()) backup.deleteRecursively()
        return SkinImportResult(InstalledSkin(manifest, destination))
    }

    internal fun validateInstalled(versionRoot: File): InstalledSkin {
        val manifestFile = File(versionRoot, "skin.json")
        if (!manifestFile.isFile) throw SkinImportException(SkinImportFailure.MISSING_MANIFEST, "skin.json is required")
        val manifest = try {
            SkinManifestParser.parse(readManifest(manifestFile))
        } catch (exception: RuntimeException) {
            throw SkinImportException(SkinImportFailure.MALFORMED_MANIFEST, "Invalid installed skin manifest", exception)
        }
        if (versionRoot.parentFile?.name != manifest.id || versionRoot.name != manifest.packageVersion) {
            throw SkinImportException(SkinImportFailure.MALFORMED_MANIFEST, "Installed skin path does not match its manifest")
        }
        validateInstalledContents(versionRoot, manifest)
        return InstalledSkin(manifest, versionRoot)
    }

    private fun validateInstalledContents(root: File, manifest: SkinManifest) {
        val rootPath = root.canonicalPath + File.separator
        validateImage(File(root, "preview.png"), "preview.png")
        manifest.assets.values.forEach { asset ->
            if (!asset.path.endsWith(".png", ignoreCase = true) && !asset.path.endsWith(".webp", ignoreCase = true)) {
                throw SkinImportException(SkinImportFailure.INVALID_PACKAGE, "Unsupported asset '${asset.path}'")
            }
            val file = File(root, asset.path)
            if (!file.isFile || !file.canonicalPath.startsWith(rootPath)) {
                throw SkinImportException(SkinImportFailure.MISSING_ASSET, "Missing asset '${asset.path}'")
            }
            if (!file.sha256().equals(asset.sha256, ignoreCase = true)) {
                throw SkinImportException(SkinImportFailure.INVALID_PACKAGE, "Checksum mismatch for '${asset.path}'")
            }
            val bounds = validateImage(file, asset.path)
            asset.sliceInsets?.let { inset ->
                if (inset.left + inset.right >= bounds.width || inset.top + inset.bottom >= bounds.height) {
                    throw SkinImportException(SkinImportFailure.INVALID_IMAGE, "Nine-slice insets consume '${asset.path}'")
                }
            }
        }
    }

    private fun ensureRoot() {
        if (!skinsRoot.mkdirs() && !skinsRoot.isDirectory) storageError("Could not create the skin library")
    }

    private fun validateImage(file: File, label: String): DecodedImageBounds {
        if (!file.isFile) throw SkinImportException(SkinImportFailure.MISSING_ASSET, "Missing asset '$label'")
        val bounds = try {
            imageDecoder(file)
        } catch (error: OutOfMemoryError) {
            throw SkinImportException(SkinImportFailure.INVALID_IMAGE, "Image '$label' exhausted decoder memory", error)
        } catch (exception: RuntimeException) {
            throw SkinImportException(SkinImportFailure.INVALID_IMAGE, "Image '$label' could not be decoded", exception)
        }
        if (bounds == null || bounds.width !in 1..MAX_IMAGE_DIMENSION || bounds.height !in 1..MAX_IMAGE_DIMENSION) {
            throw SkinImportException(SkinImportFailure.INVALID_IMAGE, "'$label' is not a supported image or exceeds 8192×8192")
        }
        return bounds
    }

    private fun isAllowedPackageFile(path: String): Boolean {
        if (path == "skin.json" || path == "preview.png") return true
        if (!path.startsWith("assets/")) return false
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension == "png" || extension == "webp"
    }

    private fun copyPackageWithLimit(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            copied += read
            if (copied > MAX_COMPRESSED_BYTES) invalidPackage("Skin package exceeds 64 MiB")
            output.write(buffer, 0, read)
        }
    }

    private fun readManifest(file: File): String {
        if (file.length() !in 1..MAX_MANIFEST_BYTES) {
            throw SkinImportException(SkinImportFailure.MALFORMED_MANIFEST, "skin.json exceeds its size limit")
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            InputStreamReader(FileInputStream(file), decoder).use { it.readText() }
        } catch (exception: java.nio.charset.CharacterCodingException) {
            throw SkinImportException(SkinImportFailure.MALFORMED_MANIFEST, "skin.json is not valid UTF-8", exception)
        }
    }

    private fun invalidPackage(message: String): Nothing =
        throw SkinImportException(SkinImportFailure.INVALID_PACKAGE, message)

    private fun storageError(message: String): Nothing =
        throw SkinImportException(SkinImportFailure.STORAGE_ERROR, message)

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(this)).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        const val MAX_COMPRESSED_BYTES = 64L * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 128L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
        const val MAX_ENTRIES = 256
        const val MAX_IMAGE_DIMENSION = 8192
        const val MAX_MANIFEST_BYTES = 1024L * 1024
    }
}

private fun decodeImageSafely(file: File): DecodedImageBounds? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    if (bounds.outWidth > 8192 || bounds.outHeight > 8192) {
        return DecodedImageBounds(bounds.outWidth, bounds.outHeight)
    }
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1024 || bounds.outHeight / sampleSize > 1024) sampleSize *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?: return null
    decoded.recycle()
    return DecodedImageBounds(bounds.outWidth, bounds.outHeight)
}
