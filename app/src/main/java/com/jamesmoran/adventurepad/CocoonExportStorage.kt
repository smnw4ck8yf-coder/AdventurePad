package com.jamesmoran.adventurepad

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.util.Locale

internal const val COCOON_DPT_MIME_TYPE = "application/octet-stream"

internal class FileCocoonOwnershipStore(context: Context, treeUri: Uri) : CocoonOwnershipStore {
    private val treeKey = cocoonContentSha256(treeUri.toString()).take(20)
    private val atomicFile = AtomicFile(
        File(context.filesDir, "cocoon-integration/export-ownership-$treeKey.json").apply {
            parentFile?.mkdirs()
        },
    )

    override fun load(): CocoonOwnershipManifest = synchronized(atomicFile) {
        if (!atomicFile.baseFile.isFile) return CocoonOwnershipManifest()
        runCatching {
            atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                decodeCocoonOwnershipManifest(reader.readText())
            }
        }.getOrDefault(CocoonOwnershipManifest())
    }

    override fun save(manifest: CocoonOwnershipManifest) = synchronized(atomicFile) {
        val stream = atomicFile.startWrite()
        try {
            val writer = stream.writer(StandardCharsets.UTF_8)
            writer.write(encodeCocoonOwnershipManifest(manifest))
            writer.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }
}

internal class CocoonIntegrationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var treeUri: Uri?
        get() = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_TREE_URI) else putString(KEY_TREE_URI, value.toString())
            }.apply()
        }

    fun hasPersistedReadWritePermission(contentResolver: ContentResolver): Boolean {
        val selected = treeUri ?: return false
        return contentResolver.persistedUriPermissions.any {
            it.uri == selected && it.isReadPermission && it.isWritePermission
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "cocoon_integration"
        const val KEY_TREE_URI = "scummvm_tree_uri"
    }
}

internal class SafCocoonExportDirectory(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
) : CocoonExportDirectory {
    private val rootDocumentUri: Uri
        get() = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

    private val childrenUri: Uri
        get() = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

    override fun readDptFiles(): List<CocoonExistingDpt> = queryChildren()
        .filter { it.displayName.endsWith(COCOON_DPT_EXTENSION, ignoreCase = true) }
        .map { child ->
            val bytes = contentResolver.openInputStream(child.uri)?.use {
                it.readNBytes(MAX_DPT_READ_BYTES + 1)
            } ?: throw FileNotFoundException("Could not read ${child.displayName}.")
            val content = if (bytes.size > MAX_DPT_READ_BYTES) {
                String(bytes.copyOf(MAX_DPT_READ_BYTES), StandardCharsets.UTF_8) + "\u0000TRUNCATED"
            } else {
                String(bytes, StandardCharsets.UTF_8)
            }
            CocoonExistingDpt(child.displayName, content)
        }

    override fun writeDpt(
        fileName: String,
        content: String,
        expectedExistingSha256: String?,
    ) {
        require(fileName.endsWith(COCOON_DPT_EXTENSION, ignoreCase = true))
        val matches = queryChildren().filter { it.displayName.equals(fileName, ignoreCase = true) }
        val documentUri = when {
            expectedExistingSha256 == null && matches.isNotEmpty() -> {
                error("A file named '$fileName' appeared after the preview. Refresh before exporting.")
            }
            expectedExistingSha256 != null && matches.size == 1 -> {
                val existing = contentResolver.openInputStream(matches.single().uri)?.use {
                    String(it.readNBytes(MAX_DPT_READ_BYTES + 1), StandardCharsets.UTF_8)
                } ?: error("AdventurePad could not re-read '$fileName'.")
                check(cocoonContentSha256(existing) == expectedExistingSha256) {
                    "'$fileName' changed after the preview. AdventurePad did not overwrite it."
                }
                matches.single().uri
            }
            expectedExistingSha256 != null -> {
                error("The managed file '$fileName' is missing or ambiguous. Refresh before exporting.")
            }
            else -> DocumentsContract.createDocument(
                contentResolver,
                rootDocumentUri,
                // A text MIME type makes Android's external-storage provider append
                // ".txt" to otherwise unknown extensions (yielding ".dpt.txt").
                // Keep the exact Cocoon filename while still writing UTF-8 text.
                COCOON_DPT_MIME_TYPE,
                fileName,
            ) ?: error("The document provider could not create '$fileName'.")
        }
        contentResolver.openOutputStream(documentUri, "wt")?.use {
            it.write(content.toByteArray(StandardCharsets.UTF_8))
        } ?: error("The document provider could not write '$fileName'.")
    }

    fun displayName(): String = runCatching {
        DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').substringAfterLast('/')
    }.getOrDefault("Selected folder")

    private fun queryChildren(): List<SafChildDocument> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        return contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) add(cursor.toSafChild(idIndex, nameIndex))
            }
        } ?: error("The selected folder is no longer available.")
    }

    private fun Cursor.toSafChild(idIndex: Int, nameIndex: Int): SafChildDocument {
        val documentId = getString(idIndex)
        return SafChildDocument(
            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
            displayName = getString(nameIndex),
        )
    }

    private data class SafChildDocument(val uri: Uri, val displayName: String)

    private companion object {
        const val MAX_DPT_READ_BYTES = 64 * 1024
    }
}

internal fun persistCocoonTreePermission(context: Context, uri: Uri) {
    val preferences = CocoonIntegrationPreferences(context)
    val previousUri = preferences.treeUri
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    preferences.treeUri = uri
    if (previousUri != null && previousUri != uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                previousUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
}

internal fun encodeCocoonOwnershipManifest(manifest: CocoonOwnershipManifest): String = buildString {
    append("{\n  \"version\": 1,\n  \"entries\": [")
    manifest.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
        .forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append("\n    {\"fileName\": \"")
            append(jsonEscape(entry.fileName))
            append("\", \"targetId\": \"")
            append(jsonEscape(entry.targetId))
            append("\", \"lastWrittenSha256\": \"")
            append(jsonEscape(entry.lastWrittenSha256))
            append("\"}")
        }
    append("\n  ]\n}\n")
}

internal fun decodeCocoonOwnershipManifest(source: String): CocoonOwnershipManifest {
    val root = SimpleJson.parse(source) as? Map<*, *> ?: error("Ownership manifest must be an object.")
    require((root["version"] as? Number)?.toInt() == 1) { "Unsupported ownership manifest version." }
    val entries = (root["entries"] as? List<*>).orEmpty().map { value ->
        val entry = value as? Map<*, *> ?: error("Ownership entry must be an object.")
        CocoonOwnedDpt(
            fileName = entry["fileName"] as? String ?: error("Missing owned filename."),
            targetId = entry["targetId"] as? String ?: error("Missing owned target ID."),
            lastWrittenSha256 = entry["lastWrittenSha256"] as? String
                ?: error("Missing owned content hash."),
        )
    }
    require(entries.distinctBy { it.fileName.lowercase(Locale.ROOT) }.size == entries.size) {
        "Duplicate owned filenames."
    }
    return CocoonOwnershipManifest(entries)
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
}
