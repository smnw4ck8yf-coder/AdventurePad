package com.jamesmoran.adventurepad

import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughRepositoryTest {
    @Test fun supportedFileNamesMapToTextAndMarkdownOnly() {
        assertEquals(WalkthroughSourceType.TEXT_FILE, walkthroughSourceTypeForFileName("guide.TXT"))
        assertEquals(WalkthroughSourceType.MARKDOWN_FILE, walkthroughSourceTypeForFileName("guide.md"))
        assertEquals(WalkthroughSourceType.MARKDOWN_FILE, walkthroughSourceTypeForFileName("guide.markdown"))
        assertNull(walkthroughSourceTypeForFileName("guide.rtf"))
        assertNull(walkthroughSourceTypeForFileName("guide.pdf"))
    }

    @Test fun fileStorePersistsStructuredAndPlainImportsAndReaderState() = runBlocking {
        val directory = Files.createTempDirectory("adventurepad-walkthrough-test").toFile()
        try {
            val store = FileWalkthroughStore(directory)
            val structured = WalkthroughParser.parse("# Guide\nText", 10L, WalkthroughSourceType.MARKDOWN_FILE)
                .copy(
                    position = WalkthroughPosition(4, "section-0", .25f),
                    preferences = WalkthroughReaderPreferences(
                        1.1875f,
                        1.3f,
                        ReadingAppearance.TAN,
                        ReaderFont.SERIF,
                    ),
                )
            val plain = WalkthroughParser.parse("Unstructured prose", 11L, WalkthroughSourceType.TEXT_FILE)
                .copy(sections = emptyList())

            store.save("game-a", structured)
            store.save("game-b", plain)

            assertEquals(structured, store.load("game-a"))
            assertEquals(plain, store.load("game-b"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun replacementAndRemovalOnlyAffectSelectedTarget() = runBlocking {
        val store = FakeWalkthroughStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = WalkthroughRepository(store, scope)
        val first = WalkthroughParser.parse("# First", 1L, WalkthroughSourceType.PASTED_TEXT)
        val replacement = WalkthroughParser.parse("# Replacement", 2L, WalkthroughSourceType.PASTED_TEXT)
        val other = WalkthroughParser.parse("# Other", 3L, WalkthroughSourceType.TEXT_FILE)

        repository.save("game-a", first)
        repository.save("game-b", other)
        repository.save("game-a", replacement)
        assertEquals(replacement, store.load("game-a"))
        assertEquals(other, store.load("game-b"))

        repository.remove("game-a")
        assertNull(store.load("game-a"))
        assertEquals(other, store.load("game-b"))
        scope.cancel()
    }

    @Test fun preferenceUpdatesPreserveStoredSourceAndNavigationData() = runBlocking {
        val store = FakeWalkthroughStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = WalkthroughRepository(store, scope)
        val original = WalkthroughParser.parse("# Guide\nFind the key", 12L, WalkthroughSourceType.MARKDOWN_FILE)
        val preferences = WalkthroughReaderPreferences(
            textScale = ReaderTextSize.SMALL.scale,
            appearance = ReadingAppearance.BLACK,
            font = ReaderFont.MONOSPACE,
        )

        repository.save("game-a", original)
        repository.updatePreferences("game-a", preferences)
        val updated = store.load("game-a")!!

        assertEquals(original.rawText, updated.rawText)
        assertEquals(original.sections, updated.sections)
        assertEquals(original.position, updated.position)
        assertEquals(preferences, updated.preferences)
        scope.cancel()
    }
}

private class FakeWalkthroughStore : WalkthroughStore {
    private val values = mutableMapOf<String, WalkthroughDocument>()
    override suspend fun load(gameId: String) = values[gameId]
    override suspend fun save(gameId: String, document: WalkthroughDocument) { values[gameId] = document }
    override suspend fun remove(gameId: String) { values.remove(gameId) }
}
