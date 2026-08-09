package com.jamesmoran.adventurepad

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

internal data class SkinImportResult(val installedSkin: InstalledSkin)

internal class SkinImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class SkinImporter(
    private val skinsRoot: File,
    private val versionCode: Int,
) {
    fun import(contentResolver: ContentResolver, uri: Uri, replaceExisting: Boolean = false): SkinImportResult {
        ensureRoot()
        val stagingRoot = File(skinsRoot, ".staging-${UUID.randomUUID()}")
        val packageFile = File(stagingRoot, "package.apskin")
        try {
            check(stagingRoot.mkdir()) { "Could not create skin staging directory" }
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(packageFile).use { output ->
                    val copied = input.copyTo(output, BUFFER_SIZE)
                    if (copied > MAX_COMPRESSED_BYTES) throw SkinImportException("Skin package exceeds 64 MiB")
                }
            } ?: throw SkinImportException("The selected skin could not be opened")
            return installZip(packageFile, stagingRoot, replaceExisting)
        } catch (exception: SkinImportException) {
            throw exception
        } catch (exception: Exception) {
            throw SkinImportException(exception.message ?: "Skin import failed", exception)
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    internal fun installZip(packageFile: File, stagingRoot: File, replaceExisting: Boolean): SkinImportResult {
        val extractionRoot = File(stagingRoot, "contents")
        check(extractionRoot.mkdir()) { "Could not create extraction directory" }
        val rootPath = extractionRoot.canonicalPath + File.separator
        var expandedBytes = 0L
        var entryCount = 0
        val caseFoldedPaths = hashSetOf<String>()
        ZipFile(packageFile).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ENTRIES) throw SkinImportException("Skin package contains too many files")
                val path = entry.name
                SkinManifestParser.validateRelativePath(path.removeSuffix("/"))
                val folded = path.lowercase(Locale.ROOT)
                if (!caseFoldedPaths.add(folded)) throw SkinImportException("Duplicate or case-colliding path '$path'")
                if (isForbiddenFile(path)) throw SkinImportException("Forbidden file type in '$path'")
                val destination = File(extractionRoot, path)
                if (!destination.canonicalPath.startsWith(rootPath)) throw SkinImportException("Path escapes the package root")
                if (entry.isDirectory) {
                    if (!destination.mkdirs() && !destination.isDirectory) throw SkinImportException("Could not create '$path'")
                    continue
                }
                val parent = requireNotNull(destination.parentFile)
                if (!parent.mkdirs() && !parent.isDirectory) {
                    throw SkinImportException("Could not create parent for '$path'")
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
                                throw SkinImportException("Skin package exceeds extraction limits")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
        val manifestFile = File(extractionRoot, "skin.json")
        val previewFile = File(extractionRoot, "preview.png")
        if (!manifestFile.isFile || !previewFile.isFile) throw SkinImportException("skin.json and preview.png are required")
        val manifest = try {
            SkinManifestParser.parse(manifestFile.readText(Charsets.UTF_8))
        } catch (exception: RuntimeException) {
            throw SkinImportException("Invalid skin.json: ${exception.message}", exception)
        }
        if (manifest.minimumAdventurePadVersionCode > versionCode) {
            throw SkinImportException("This skin requires a newer AdventurePad version")
        }
        validateImage(previewFile, "preview.png")
        manifest.assets.values.forEach { asset ->
            val file = File(extractionRoot, asset.path)
            if (!file.isFile || !file.canonicalPath.startsWith(rootPath)) {
                throw SkinImportException("Missing asset '${asset.path}'")
            }
            if (!file.sha256().equals(asset.sha256, ignoreCase = true)) {
                throw SkinImportException("Checksum mismatch for '${asset.path}'")
            }
            if (asset.path.endsWith(".png", ignoreCase = true) || asset.path.endsWith(".webp", ignoreCase = true)) {
                validateImage(file, asset.path)
                asset.sliceInsets?.let { inset ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    if (inset.left + inset.right >= bounds.outWidth || inset.top + inset.bottom >= bounds.outHeight) {
                        throw SkinImportException("Nine-slice insets consume '${asset.path}'")
                    }
                }
            }
        }
        val idRoot = File(skinsRoot, manifest.id)
        val destination = File(idRoot, manifest.packageVersion)
        if (destination.exists() && !replaceExisting) throw SkinImportException("This skin version is already installed")
        if (!idRoot.mkdirs() && !idRoot.isDirectory) throw SkinImportException("Could not create the skin library")
        val backup = File(idRoot, ".backup-${manifest.packageVersion}-${UUID.randomUUID()}")
        if (destination.exists() && !destination.renameTo(backup)) throw SkinImportException("Could not stage the installed version for replacement")
        if (!extractionRoot.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            throw SkinImportException("Could not finalize the skin installation")
        }
        if (backup.exists()) backup.deleteRecursively()
        return SkinImportResult(InstalledSkin(manifest, destination))
    }

    private fun ensureRoot() {
        if (!skinsRoot.mkdirs() && !skinsRoot.isDirectory) throw SkinImportException("Could not create the skin library")
    }

    private fun validateImage(file: File, label: String) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth !in 1..MAX_IMAGE_DIMENSION || options.outHeight !in 1..MAX_IMAGE_DIMENSION) {
            throw SkinImportException("'$label' is not a supported image or exceeds 8192×8192")
        }
    }

    private fun isForbiddenFile(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in setOf("apk", "dex", "jar", "so", "zip", "apskin", "sh", "js", "html", "exe")
    }

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
    }
}
