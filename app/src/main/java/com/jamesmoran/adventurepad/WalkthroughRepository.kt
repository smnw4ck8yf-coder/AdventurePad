package com.jamesmoran.adventurepad

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface WalkthroughStore {
    suspend fun load(gameId: String): WalkthroughDocument?
    suspend fun save(gameId: String, document: WalkthroughDocument)
    suspend fun remove(gameId: String)
}

internal class FileWalkthroughStore(private val directory: File) : WalkthroughStore {
    override suspend fun load(gameId: String): WalkthroughDocument? = withContext(Dispatchers.IO) {
        val file = fileFor(gameId)
        if (!file.isFile) return@withContext null
        runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                val fileVersion = input.readInt()
                check(fileVersion in 1..FILE_FORMAT_VERSION)
                val parserVersion = input.readInt()
                val importedAt = input.readLong()
                val sourceType = WalkthroughSourceType.valueOf(input.readUTF())
                val position = WalkthroughPosition(input.readInt(), input.readUTF().ifBlank { null }, input.readFloat())
                val preferences = WalkthroughReaderPreferences(
                    input.readFloat(),
                    input.readFloat(),
                    ReadingAppearance.valueOf(input.readUTF()),
                    if (fileVersion >= 2) ReaderFont.valueOf(input.readUTF()) else ReaderFont.SANS_SERIF,
                )
                val rawText = input.readUTF8String(MAX_WALKTHROUGH_LENGTH)
                val count = input.readInt().coerceIn(0, 20_000)
                val sections = List(count) {
                    WalkthroughSection(
                        id = input.readUTF(),
                        title = input.readUTF(),
                        startOffset = input.readInt(),
                        endOffset = input.readInt(),
                        level = input.readInt(),
                        parentId = input.readUTF().ifBlank { null },
                    )
                }
                WalkthroughDocument(rawText, sections, importedAt, sourceType, parserVersion, position, preferences)
            }
        }.getOrNull()
    }

    override suspend fun save(gameId: String, document: WalkthroughDocument) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val destination = fileFor(gameId)
        val temporary = File(directory, "${destination.name}.tmp")
        DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
            output.writeInt(FILE_FORMAT_VERSION)
            output.writeInt(document.parserSchemaVersion)
            output.writeLong(document.importedAt)
            output.writeUTF(document.sourceType.name)
            output.writeInt(document.position.textOffset)
            output.writeUTF(document.position.sectionId.orEmpty())
            output.writeFloat(document.position.relativeInSection)
            output.writeFloat(document.preferences.textScale)
            output.writeFloat(document.preferences.lineSpacingScale)
            output.writeUTF(document.preferences.appearance.name)
            output.writeUTF(document.preferences.font.name)
            output.writeUTF8String(document.rawText.take(MAX_WALKTHROUGH_LENGTH))
            output.writeInt(document.sections.size)
            document.sections.forEach { section ->
                output.writeUTF(section.id)
                output.writeUTF(section.title.take(2_000))
                output.writeInt(section.startOffset)
                output.writeInt(section.endOffset)
                output.writeInt(section.level)
                output.writeUTF(section.parentId.orEmpty())
            }
        }
        check(temporary.renameTo(destination)) { "Unable to replace walkthrough document" }
    }

    override suspend fun remove(gameId: String) = withContext(Dispatchers.IO) {
        val file = fileFor(gameId)
        if (file.exists()) check(file.delete()) { "Unable to remove walkthrough document" }
    }

    private fun fileFor(gameId: String) = File(directory, "${gameStorageKey(gameId)}.apw")

    private fun DataOutputStream.writeUTF8String(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readUTF8String(maxCharacters: Int): String {
        val size = readInt()
        require(size in 0..MAX_FILE_BYTES)
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8).take(maxCharacters)
    }

    private companion object {
        const val FILE_FORMAT_VERSION = 2
        const val MAX_FILE_BYTES = 20_000_000
    }
}

internal class WalkthroughRepository(
    private val store: WalkthroughStore,
    private val scope: CoroutineScope,
) {
    private val mutableSelection = MutableStateFlow(WalkthroughSelection())
    val selection: StateFlow<WalkthroughSelection> = mutableSelection
    private var activeGameId = ""

    fun selectGame(gameId: String) {
        if (activeGameId == gameId) return
        activeGameId = gameId
        mutableSelection.value = WalkthroughSelection(gameId)
        if (gameId.isBlank()) return
        scope.launch {
            val loaded = store.load(gameId)
            if (activeGameId == gameId) mutableSelection.value = WalkthroughSelection(gameId, loaded)
        }
    }

    suspend fun save(gameId: String, document: WalkthroughDocument) {
        require(gameId.isNotBlank())
        store.save(gameId, document)
        if (activeGameId == gameId) mutableSelection.value = WalkthroughSelection(gameId, document)
    }

    suspend fun updatePosition(gameId: String, position: WalkthroughPosition) {
        val document = currentDocument(gameId) ?: return
        save(gameId, document.copy(position = position))
    }

    suspend fun updatePreferences(gameId: String, preferences: WalkthroughReaderPreferences) {
        val document = currentDocument(gameId) ?: return
        save(gameId, document.copy(preferences = preferences))
    }

    suspend fun remove(gameId: String) {
        require(gameId.isNotBlank())
        store.remove(gameId)
        if (activeGameId == gameId) mutableSelection.value = WalkthroughSelection(gameId)
    }

    private suspend fun currentDocument(gameId: String): WalkthroughDocument? =
        mutableSelection.value.takeIf { it.gameId == gameId }?.document ?: store.load(gameId)

    companion object {
        fun create(context: Context, scope: CoroutineScope) = WalkthroughRepository(
            FileWalkthroughStore(File(context.applicationContext.filesDir, "walkthroughs")),
            scope,
        )
    }
}
