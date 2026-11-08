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

internal class FilePreparedLauncherOwnershipStore(context: Context, treeUri: Uri) : PreparedFileOwnershipStore {
    private val treeKey = cocoonContentSha256(treeUri.toString()).take(20)
    private val atomicFile = AtomicFile(
        File(context.filesDir, "cocoon-integration/launcher-file-ownership-$treeKey.json").apply {
            parentFile?.mkdirs()
        },
    )

    override fun load(): PreparedFileOwnership = synchronized(atomicFile) {
        if (!atomicFile.baseFile.isFile) return PreparedFileOwnership()
        runCatching {
            atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                decodePreparedFileOwnership(reader.readText())
            }
        }.getOrDefault(PreparedFileOwnership())
    }

    override fun save(ownership: PreparedFileOwnership) = synchronized(atomicFile) {
        val stream = atomicFile.startWrite()
        try {
            val writer = stream.writer(StandardCharsets.UTF_8)
            writer.write(encodePreparedFileOwnership(ownership))
            writer.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }
}

internal class FilePendingGameExportStore(context: Context, treeUri: Uri) : PendingGameExportStore {
    private val treeKey = cocoonContentSha256(treeUri.toString()).take(20)
    private val atomicFile = AtomicFile(
        File(context.filesDir, "cocoon-integration/pending-game-export-$treeKey.json").apply {
            parentFile?.mkdirs()
        },
    )

    override fun load(): PendingGameExport? = synchronized(atomicFile) {
        if (!atomicFile.baseFile.isFile) return null
        runCatching {
            atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                decodePendingGameExport(reader.readText())
            }
        }.getOrNull()
    }

    override fun save(pending: PendingGameExport) = synchronized(atomicFile) {
        val stream = atomicFile.startWrite()
        try {
            val writer = stream.writer(StandardCharsets.UTF_8)
            writer.write(encodePendingGameExport(pending))
            writer.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override fun clear() = synchronized(atomicFile) {
        atomicFile.delete()
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

    var selectedLauncherId: String?
        get() = preferences.getString(KEY_SELECTED_LAUNCHER_ID, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_SELECTED_LAUNCHER_ID) else putString(KEY_SELECTED_LAUNCHER_ID, value)
            }.apply()
        }

    var setupComplete: Boolean
        get() = preferences.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = preferences.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    var preparedGameCount: Int
        get() = preferences.getInt(KEY_PREPARED_GAME_COUNT, 0)
        set(value) = preferences.edit().putInt(KEY_PREPARED_GAME_COUNT, value.coerceAtLeast(0)).apply()

    fun hasPersistedReadWritePermission(contentResolver: ContentResolver): Boolean {
        val selected = treeUri ?: return false
        return contentResolver.persistedUriPermissions.any {
            it.uri == selected && it.isReadPermission && it.isWritePermission
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "cocoon_integration"
        const val KEY_TREE_URI = "scummvm_tree_uri"
        const val KEY_SELECTED_LAUNCHER_ID = "selected_external_launcher_id"
        const val KEY_SETUP_COMPLETE = "external_launcher_setup_complete"
        const val KEY_PREPARED_GAME_COUNT = "prepared_game_count"
    }
}

internal class SafCocoonExportDirectory(
    private val contentResolver: ContentResolver,
    private val treeUri: Uri,
) : ExternalLauncherPreparationDirectory {
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
        writeManagedFile(fileName, content, expectedExistingSha256, COCOON_DPT_MIME_TYPE, MAX_DPT_READ_BYTES)
    }

    override fun readFilesNamed(fileName: String): List<ExistingPreparedFile> = queryChildren()
        .filter { it.displayName.equals(fileName, ignoreCase = true) }
        .map { child ->
            val content = contentResolver.openInputStream(child.uri)?.use {
                String(it.readNBytes(MAX_PREPARED_FILE_READ_BYTES + 1), StandardCharsets.UTF_8)
            } ?: throw FileNotFoundException("Could not read ${child.displayName}.")
            check(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_PREPARED_FILE_READ_BYTES) {
                "${child.displayName} is too large to manage safely."
            }
            ExistingPreparedFile(child.displayName, content)
        }

    override fun writePreparedFile(
        fileName: String,
        content: String,
        expectedExistingSha256: String?,
    ) {
        require(fileName == ADVENTUREPAD_PLATFORM_FILE_NAME)
        writeManagedFile(fileName, content, expectedExistingSha256, "application/json", MAX_PREPARED_FILE_READ_BYTES)
    }

    private fun writeManagedFile(
        fileName: String,
        content: String,
        expectedExistingSha256: String?,
        mimeType: String,
        maxReadBytes: Int,
    ) {
        val matches = queryChildren().filter { it.displayName.equals(fileName, ignoreCase = true) }
        val documentUri = when {
            expectedExistingSha256 == null && matches.isNotEmpty() -> {
                error("A file named '$fileName' appeared after the preview. Refresh before exporting.")
            }
            expectedExistingSha256 != null && matches.size == 1 -> {
                val existing = contentResolver.openInputStream(matches.single().uri)?.use {
                    String(it.readNBytes(maxReadBytes + 1), StandardCharsets.UTF_8)
                } ?: error("AdventurePad could not re-read '$fileName'.")
                check(preparedFileSha256(existing) == expectedExistingSha256) {
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
                mimeType,
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
        const val MAX_PREPARED_FILE_READ_BYTES = 256 * 1024
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

internal fun encodePreparedFileOwnership(ownership: PreparedFileOwnership): String = buildString {
    append("{\n  \"version\": 1,\n  \"entries\": [")
    ownership.entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
        .forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append("\n    {\"fileName\": \"")
            append(jsonEscape(entry.fileName))
            append("\", \"launcherId\": \"")
            append(jsonEscape(entry.launcherId))
            append("\", \"lastWrittenSha256\": \"")
            append(jsonEscape(entry.lastWrittenSha256))
            append("\"}")
        }
    append("\n  ]\n}\n")
}

internal fun decodePreparedFileOwnership(source: String): PreparedFileOwnership {
    val root = SimpleJson.parse(source) as? Map<*, *> ?: error("Prepared file ownership must be an object.")
    require((root["version"] as? Number)?.toInt() == 1) { "Unsupported ownership version." }
    val entries = (root["entries"] as? List<*>).orEmpty().map { value ->
        val entry = value as? Map<*, *> ?: error("Prepared file ownership entry must be an object.")
        OwnedPreparedFile(
            fileName = entry["fileName"] as? String ?: error("Missing prepared filename."),
            launcherId = entry["launcherId"] as? String ?: error("Missing launcher ID."),
            lastWrittenSha256 = entry["lastWrittenSha256"] as? String
                ?: error("Missing prepared file content hash."),
        )
    }
    require(entries.distinctBy { it.fileName.lowercase(Locale.ROOT) }.size == entries.size) {
        "Duplicate prepared filenames."
    }
    return PreparedFileOwnership(entries)
}

internal fun encodePendingGameExport(pending: PendingGameExport): String = buildString {
    append("{\n  \"version\": 1,\n  \"fileName\": \"")
    append(jsonEscape(pending.fileName))
    append("\",\n  \"targetId\": \"")
    append(jsonEscape(pending.targetId))
    append("\",\n  \"contentSha256\": \"")
    append(jsonEscape(pending.contentSha256))
    append("\"\n}\n")
}

internal fun decodePendingGameExport(source: String): PendingGameExport {
    val root = SimpleJson.parse(source) as? Map<*, *> ?: error("Pending export must be an object.")
    require((root["version"] as? Number)?.toInt() == 1) { "Unsupported pending export version." }
    return PendingGameExport(
        fileName = root["fileName"] as? String ?: error("Missing pending export filename."),
        targetId = root["targetId"] as? String ?: error("Missing pending export target ID."),
        contentSha256 = root["contentSha256"] as? String
            ?: error("Missing pending export content hash."),
    )
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
