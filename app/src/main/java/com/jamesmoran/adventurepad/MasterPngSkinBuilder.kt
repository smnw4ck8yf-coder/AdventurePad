package com.jamesmoran.adventurepad

import android.content.ContentResolver
import android.content.res.AssetManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import android.util.JsonWriter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class MasterPngBuildResult(val installedSkin: InstalledSkin)

internal class MasterPngBuildException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Builds, validates, installs, and registers a v1 skin from one authoring-template PNG. */
internal class MasterPngSkinBuilder(
    private val assets: AssetManager,
    private val contentResolver: ContentResolver,
    private val importer: SkinImporter,
    private val cacheRoot: File,
) {
    fun buildAndInstall(uri: Uri): MasterPngBuildResult {
        val staging = File(cacheRoot, "skin-authoring-${UUID.randomUUID()}")
        try {
            if (!staging.mkdir()) throw MasterPngBuildException("AdventurePad could not prepare the skin builder")
            requirePng(uri)
            val specJson = runCatching {
                assets.open(SPEC_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse { throw MasterPngBuildException("The bundled authoring template is unavailable", it) }
            val authoringSpec = try {
                SkinAuthoringSpecParser.parse(specJson)
            } catch (exception: RuntimeException) {
                throw MasterPngBuildException("The bundled authoring template is invalid", exception)
            }
            val sourceHash = contentResolver.openInputStream(uri)?.use(::sha256)
                ?: throw MasterPngBuildException("The selected PNG could not be opened")
            val metadata = metadata(displayName(uri), sourceHash)
            val packageRoot = File(staging, "package-root").apply {
                if (!mkdir()) throw MasterPngBuildException("AdventurePad could not prepare the generated skin")
            }
            exportRegions(uri, authoringSpec, packageRoot)
            val hashes = authoringSpec.regions.associate { region ->
                region.slotId to sha256(BufferedInputStream(FileInputStream(File(packageRoot, assetPath(region.slotId)))))
            }
            writeManifest(File(packageRoot, "skin.json"), authoringSpec, metadata, hashes)
            val packageFile = File(staging, "generated.apskin")
            zipPackage(packageRoot, packageFile)
            val installed = importer.import(
                contentResolver = contentResolver,
                uri = Uri.fromFile(packageFile),
                replaceExisting = false,
            ).installedSkin
            return MasterPngBuildResult(installed)
        } catch (exception: MasterPngBuildException) {
            throw exception
        } catch (exception: SkinImportException) {
            val message = if (exception.failure == SkinImportFailure.DUPLICATE_ID) {
                "This skin is already installed. AdventurePad did not overwrite it."
            } else {
                "AdventurePad could not validate the generated skin."
            }
            throw MasterPngBuildException(message, exception)
        } catch (exception: Exception) {
            throw MasterPngBuildException(exception.message ?: "Could not build a skin from this PNG", exception)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun exportRegions(uri: Uri, spec: SkinAuthoringSpec, packageRoot: File) {
        val input = contentResolver.openInputStream(uri)
            ?: throw MasterPngBuildException("The selected PNG could not be opened")
        val decoder = runCatching { BitmapRegionDecoder.newInstance(input, false) }
            .getOrElse {
                input.close()
                throw MasterPngBuildException("This PNG is unreadable or corrupt.", it)
            }
            ?: run {
                input.close()
                throw MasterPngBuildException("This PNG is unreadable or corrupt.")
            }
        try {
            if (decoder.width != spec.masterCanvas.width || decoder.height != spec.masterCanvas.height) {
                throw MasterPngBuildException(
                    "This PNG is ${decoder.width}×${decoder.height}; the supported template is " +
                        "${spec.masterCanvas.width}×${spec.masterCanvas.height}.",
                )
            }
            spec.regions.forEach { region ->
                val decoded = decoder.decodeRegion(
                    Rect(region.x, region.y, region.x + region.width, region.y + region.height),
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                ) ?: throw MasterPngBuildException("This PNG is corrupt or contains an unreadable artwork region.")
                var output = if (decoded.width == region.outputWidth && decoded.height == region.outputHeight) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, region.outputWidth, region.outputHeight, false).also {
                        decoded.recycle()
                    }
                }
                if (region.transparentCenter != null) {
                    if (!output.isMutable) {
                        val previous = output
                        output = previous.copy(Bitmap.Config.ARGB_8888, true)
                        previous.recycle()
                    }
                    clearCenter(output, region.transparentCenter)
                }
                if (region.alphaMode == "opaque" && !isOpaque(output)) {
                    output.recycle()
                    throw MasterPngBuildException(
                        "This is not a valid AdventurePad template: the catalog image area must be fully opaque.",
                    )
                }
                val destination = File(packageRoot, assetPath(region.slotId))
                destination.parentFile?.let { parent ->
                    if (!parent.mkdirs() && !parent.isDirectory) throw MasterPngBuildException("Could not create asset folders")
                }
                FileOutputStream(destination).use { stream ->
                    if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw MasterPngBuildException("AdventurePad could not export an artwork region")
                    }
                }
                output.recycle()
            }
        } finally {
            decoder.recycle()
            input.close()
        }
    }

    private fun clearCenter(bitmap: Bitmap, center: SkinTransparentCenter) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawRect(
            center.x.toFloat(), center.y.toFloat(),
            (center.x + center.width).toFloat(), (center.y + center.height).toFloat(),
            paint,
        )
        paint.xfermode = null
    }

    private fun isOpaque(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return true
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { pixel -> pixel ushr 24 != 0xFF }) return false
        }
        return true
    }

    private fun requirePng(uri: Uri) {
        val header = contentResolver.openInputStream(uri)?.use { input -> ByteArray(8).also { input.read(it) } }
            ?: throw MasterPngBuildException("The selected file could not be opened")
        if (!header.contentEquals(PNG_SIGNATURE)) {
            throw MasterPngBuildException("Unsupported file. Select a PNG exported from the AdventurePad template.")
        }
    }

    private fun writeManifest(
        destination: File,
        spec: SkinAuthoringSpec,
        metadata: GeneratedSkinMetadata,
        hashes: Map<String, String>,
    ) {
        JsonWriter(OutputStreamWriter(FileOutputStream(destination), Charsets.UTF_8)).use { json ->
            json.setIndent("  ")
            json.beginObject()
            json.name("formatVersion").value(1)
            json.name("id").value(metadata.id)
            json.name("name").value(metadata.name)
            json.name("author").value("AdventurePad Skin Builder")
            json.name("packageVersion").value("1.0.0")
            json.name("minimumAdventurePadVersionCode").value(1)
            json.name("features").beginArray()
            listOf("lower-controls", "upper-surround", "companion-surfaces", "button-states").forEach(json::value)
            json.endArray()
            json.name("assets").beginObject()
            spec.regions.forEach { region ->
                json.name(region.slotId).beginObject()
                json.name("path").value(assetPath(region.slotId))
                json.name("scale").value(region.scaleMode.manifestValue())
                json.name("canvas").beginObject()
                json.name("width").value(region.outputWidth)
                json.name("height").value(region.outputHeight)
                json.endObject()
                region.sliceInsets?.let { inset ->
                    json.name("sliceInsets").beginObject()
                    json.name("left").value(inset.left)
                    json.name("top").value(inset.top)
                    json.name("right").value(inset.right)
                    json.name("bottom").value(inset.bottom)
                    json.endObject()
                }
                json.name("sha256").value(hashes.getValue(region.slotId))
                json.endObject()
            }
            json.endObject()
            json.name("colors").beginObject()
            DEFAULT_COLORS.forEach { (name, value) -> json.name(name).value(value) }
            json.endObject()
            json.name("metrics").beginObject()
            json.name("spacingScale").value(1.0)
            json.name("cornerScale").value(1.0)
            json.name("borderScale").value(1.0)
            json.endObject()
            json.name("extensions").beginObject()
            json.name("authoringTemplateVersion").value(spec.templateVersion)
            json.endObject()
            json.endObject()
        }
    }

    private fun zipPackage(root: File, destination: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { archive ->
            root.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                archive.putNextEntry(ZipEntry(path))
                FileInputStream(file).use { it.copyTo(archive) }
                archive.closeEntry()
            }
        }
    }

    private fun displayName(uri: Uri): String? = contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
    )?.use { cursor: Cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private data class GeneratedSkinMetadata(val id: String, val name: String)

    private fun metadata(fileName: String?, sourceHash: String): GeneratedSkinMetadata {
        val original = fileName?.substringBeforeLast('.')?.takeIf(String::isNotBlank) ?: "Created Skin"
        val withoutSource = original.replace(Regex("(?i)[ _-]*source$"), "")
        val words = withoutSource
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("(?i)[ _-]*skin$"), "")
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Created" }
        val name = (if (words.startsWith("AdventurePad", ignoreCase = true)) words else "AdventurePad $words")
            .take(128)
        return GeneratedSkinMetadata("org.adventurepad.created.${sourceHash.take(24)}", name)
    }

    private fun assetPath(slot: String) = if (slot == SkinSlots.PREVIEW) {
        "preview.png"
    } else {
        "assets/${slot.replace('.', '/')}.png"
    }

    private fun SkinScaleMode.manifestValue(): String = when (this) {
        SkinScaleMode.NINE_SLICE -> "nineSlice"
        else -> name.lowercase(Locale.ROOT)
    }

    private fun sha256(input: java.io.InputStream): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val SPEC_ASSET_PATH = "skin-authoring/AUTHORING_TEMPLATE_SPEC.json"
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val DEFAULT_COLORS = linkedMapOf(
            "background" to "#111417", "surface" to "#1A1F24", "surfaceRaised" to "#22282E",
            "surfacePressed" to "#303841", "outline" to "#46515B", "outlineStrong" to "#64717D",
            "primary" to "#D8B86A", "onPrimary" to "#211B0D", "textPrimary" to "#F2F3F5",
            "textSecondary" to "#ADB5BD",
        )
    }
}
