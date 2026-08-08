package com.jamesmoran.adventurepad

import androidx.compose.ui.text.input.ImeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughReaderTest {
    private val raw = "# Tikal\nPick up the kerosene Lamp near the door.\n\n# Temple\nUse the lamp on the spiral."
    private val document = WalkthroughParser.parse(raw, 1L, WalkthroughSourceType.MARKDOWN_FILE)

    @Test fun compactToolbarUsesSingleLineTitleAndShortAccessibleControls() {
        assertEquals(1, WALKTHROUGH_TITLE_MAX_LINES)
        assertEquals("←", WALKTHROUGH_BACK_LABEL)
        assertEquals("X", WALKTHROUGH_CLOSE_LABEL)
        assertEquals(28f, COMPANION_BACK_ARROW_SIZE.value, 0f)
        assertEquals(24f, WALKTHROUGH_SEARCH_ARROW_SIZE.value, 0f)
    }

    @Test fun readerActionReturnsEveryToolbarViewToReaderWithoutAScrollRequest() {
        WalkthroughView.entries.forEach { view ->
            assertEquals(WalkthroughView.READER, readerViewAfterReaderAction(view))
        }
    }

    @Test fun settingsIsExposedThroughOverflowAndPanelOpensAndCloses() {
        assertEquals("Settings", WALKTHROUGH_SETTINGS_LABEL)
        val opened = reduceReaderSettings(ReaderSettingsState(), ReaderSettingsAction.Open)
        val closed = reduceReaderSettings(opened, ReaderSettingsAction.Close)

        assertTrue(opened.isOpen)
        assertTrue(!closed.isOpen)
    }

    @Test fun readerSettingsUpdateFontSizeAndBackgroundWithoutChangingReaderData() {
        val originalResults = searchWalkthrough(document, "lamp")
        val originalSections = document.sections
        var state = ReaderSettingsState(preferences = document.preferences)

        state = reduceReaderSettings(state, ReaderSettingsAction.SelectFont(ReaderFont.SERIF))
        state = reduceReaderSettings(state, ReaderSettingsAction.SelectTextSize(ReaderTextSize.LARGE))
        state = reduceReaderSettings(state, ReaderSettingsAction.SelectBackground(ReadingAppearance.BEIGE))

        assertEquals(ReaderFont.SERIF, state.preferences.font)
        assertEquals(ReaderTextSize.LARGE.scale, state.preferences.textScale)
        assertEquals(ReadingAppearance.BEIGE, state.preferences.appearance)
        assertEquals(raw, document.rawText)
        assertEquals(originalSections, document.sections)
        assertEquals(originalResults, searchWalkthrough(document, "lamp"))
    }

    @Test fun readerTextSizesAreBoundedAndHaveAnObviousDefault() {
        assertEquals(ReaderTextSize.DEFAULT, ReaderTextSize.closestTo(1f))
        assertTrue(ReaderTextSize.entries.all { it.scale in 0.875f..1.1875f })
    }

    @Test fun importAndPastePrimaryActionsPrecedeLongContent() {
        assertEquals(listOf("SUMMARY", "ACTIONS", "PREVIEW"), WALKTHROUGH_IMPORT_PREVIEW_ORDER)
        assertEquals(listOf("TITLE", "ANALYSE", "TEXT"), WALKTHROUGH_PASTE_ORDER)
    }

    @Test fun pasteEditorUsesDoneAndDoneOnlyDismissesInput() {
        var dismissals = 0
        val pastedText = "Keep this text exactly as pasted."

        val completedText = completeWalkthroughPasteEdit(pastedText) { dismissals++ }

        assertEquals(ImeAction.Done, WALKTHROUGH_PASTE_IME_ACTION)
        assertEquals(1, dismissals)
        assertEquals("Keep this text exactly as pasted.", completedText)
    }

    @Test fun dominantSectionWinsWhenPreviousSectionHasOnlyATrailingSliverVisible() {
        val previous = WalkthroughSection("a", "Section A", 0, 100, 1)
        val current = WalkthroughSection("b", "Section B", 100, 220, 1)

        val selected = dominantWalkthroughSection(
            listOf(
                WalkthroughSectionBounds(previous, 0f, 120f),
                WalkthroughSectionBounds(current, 120f, 400f),
            ),
            viewportTop = 100f,
            viewportBottom = 300f,
        )

        assertEquals(current, selected)
    }

    @Test fun equalOverlapFavoursSectionInActiveReadingAreaThenLaterSection() {
        val previous = WalkthroughSection("a", "Section A", 0, 100, 1)
        val current = WalkthroughSection("b", "Section B", 100, 220, 1)

        val selected = dominantWalkthroughSection(
            listOf(
                WalkthroughSectionBounds(previous, 0f, 200f),
                WalkthroughSectionBounds(current, 200f, 400f),
            ),
            viewportTop = 100f,
            viewportBottom = 300f,
        )

        assertEquals(current, selected)
    }

    @Test fun dominantSelectionSupportsNestedSections() {
        val parent = WalkthroughSection("root", "Guide", 0, 80, 1)
        val nested = WalkthroughSection("child", "Cave", 80, 180, 2, "root")

        val selected = dominantWalkthroughSection(
            listOf(
                WalkthroughSectionBounds(parent, 0f, 90f),
                WalkthroughSectionBounds(nested, 90f, 300f),
            ),
            viewportTop = 70f,
            viewportBottom = 230f,
        )

        assertEquals(nested, selected)
        assertEquals("root", selected?.parentId)
    }

    @Test fun searchIsCaseInsensitiveReturnsAllSnippetsAndExactOffsets() {
        val results = searchWalkthrough(document, "lamp")
        assertEquals(2, results.size)
        assertEquals(raw.indexOf("Lamp"), results[0].offset)
        assertEquals(4, results[0].length)
        assertEquals(raw.indexOf("lamp", results[0].offset + 1), results[1].offset)
        assertEquals("Tikal", results[0].sectionTitle)
        assertEquals("Temple", results[1].sectionTitle)
        assertTrue(results.all { it.snippet.lowercase().contains("lamp") })
    }

    @Test fun searchWorksWithoutSections() {
        val plain = document.copy(sections = emptyList())
        val result = searchWalkthrough(plain, "SPIRAL").single()
        assertEquals(raw.indexOf("spiral"), result.offset)
        assertNull(result.sectionTitle)
    }

    @Test fun nextAndPreviousSearchResultsResolveToDifferentStableTargets() {
        val results = searchWalkthrough(document, "lamp")
        val display = walkthroughDisplayText(raw, document.sections.mapTo(mutableSetOf()) { it.title })
        val nextIndex = moveWalkthroughSearchResult(0, results.size, 1)
        val previousIndex = moveWalkthroughSearchResult(nextIndex, results.size, -1)

        assertEquals(1, nextIndex)
        assertEquals(0, previousIndex)
        assertTrue(
            display.targetForRawOffset(results[0].offset).displayOffset !=
                display.targetForRawOffset(results[nextIndex].offset).displayOffset,
        )
    }

    @Test fun sectionRelativeReadingPositionRestoresAfterTextBeforeSectionChanges() {
        val offset = raw.indexOf("kerosene")
        val position = document.positionForOffset(offset)
        assertEquals(document.sections.first().id, position.sectionId)
        assertEquals(offset, document.resolvePosition(position))
    }

    @Test fun readerPreferencesCanBePersistedOnDocument() {
        val preferences = WalkthroughReaderPreferences(1.1875f, 1.4f, ReadingAppearance.WARM, ReaderFont.MONOSPACE)
        val updated = document.copy(preferences = preferences)
        assertEquals(preferences, updated.preferences)
    }

    @Test fun collapsedContentsHideDescendantsButKeepSiblingBranches() {
        val root = WalkthroughSection("root", "Guide", 0, 100, 1)
        val child = WalkthroughSection("child", "Easy", 10, 50, 2, "root")
        val grandchild = WalkthroughSection("grandchild", "Chapter 1", 20, 50, 3, "child")
        val sibling = WalkthroughSection("sibling", "Appendix", 100, 120, 1)
        val sections = listOf(root, child, grandchild, sibling)

        assertEquals(listOf(root, sibling), visibleWalkthroughSections(sections, setOf("root")))
        assertEquals(listOf(root, child, sibling), visibleWalkthroughSections(sections, setOf("child")))
        assertEquals(sections, visibleWalkthroughSections(sections, emptySet()))
    }

    @Test fun unstructuredPositionUsesStableTextOffset() {
        val plain = document.copy(sections = emptyList())
        val position = plain.positionForOffset(37)
        assertNull(position.sectionId)
        assertEquals(37, plain.resolvePosition(position))
    }

    @Test fun saveToNotesAppendsAndPreservesExistingTextAndContext() {
        val once = appendWalkthroughToNotes("Remember the key", "Use the lamp.", "Temple")
        val twice = appendWalkthroughToNotes(once, "Open the door.", "Temple")
        assertTrue(twice.startsWith("Remember the key"))
        assertTrue(twice.contains("--- Walkthrough: Temple ---"))
        assertTrue(twice.contains("Use the lamp."))
        assertTrue(twice.endsWith("Open the door."))
    }

    @Test fun legacyHardWrappedProseIsReflowedForDisplayOnly() {
        val source = "This deliberately long prose line was wrapped for an old eighty-column text file\n" +
            "and should now be allowed to wrap naturally at the width of the Android reader.\n\nNext paragraph."

        val displayed = reflowWalkthroughForDisplay(source)

        assertTrue(displayed.contains("text file and should"))
        assertTrue(displayed.contains("reader.\n\nNext paragraph."))
        assertTrue(source.contains("text file\nand should"))
    }

    @Test fun displayReflowPreservesListsHeadingsIndentationAndAsciiFormatting() {
        val source = "CHAPTER ONE\n" +
            "A deliberately long introductory prose line that reaches the legacy wrapping column\n" +
            "and continues naturally on the following source line.\n" +
            "1. Take the key\n" +
            "  NORTH\n" +
            "-----\n" +
            "+---+\n" +
            "| X |\n" +
            "+---+"

        val displayed = reflowWalkthroughForDisplay(source, setOf("CHAPTER ONE"))

        assertTrue(displayed.startsWith("CHAPTER ONE\n"))
        assertTrue(displayed.contains("wrapping column and continues"))
        assertTrue(displayed.contains("\n1. Take the key\n  NORTH\n-----\n+---+\n| X |\n+---+"))
    }

    @Test fun shortIntentionalLinesAreNotFlattened() {
        val source = "Open the door.\nPull the lever.\nGo north."
        assertEquals(source, reflowWalkthroughForDisplay(source))
    }

    @Test fun reflowMapsSearchToTheExactVisibleOccurrenceAndHighlightRange() {
        val source = "This deliberately long prose line reaches an old wrapping boundary in the source\n" +
            "and continues until Iceland is finally visible in the rendered walkthrough."
        val display = walkthroughDisplayText(source)
        val rawOffset = source.indexOf("Iceland")
        val target = display.targetForRawOffset(rawOffset)
        val range = display.displayRange(rawOffset, "Iceland".length)

        assertEquals("Iceland", display.text.substring(range.first, range.last + 1))
        assertEquals(display.text.indexOf("Iceland"), target.displayOffset)
        assertEquals(rawOffset, display.rawOffsetForDisplayOffset(target.displayOffset))
    }

    @Test fun topLevelAndNestedContentsResolveToExactRenderedTargets() {
        val source = "# Guide\nIntro\n## Cave\nNested instructions"
        val parsed = WalkthroughParser.parse(source, 4L, WalkthroughSourceType.MARKDOWN_FILE)
        val display = walkthroughDisplayText(source, parsed.sections.mapTo(mutableSetOf()) { it.title })

        parsed.sections.forEach { section ->
            val target = display.targetForRawOffset(section.startOffset)
            assertEquals(section.startOffset, target.rawOffset)
            assertEquals(section.startOffset, display.rawOffsetForDisplayOffset(target.displayOffset))
        }
        assertTrue(parsed.sections.any { it.parentId != null })
    }

    @Test fun clearingSearchLeavesTheStoredWalkthroughUntouched() {
        searchWalkthrough(document, "lamp")
        searchWalkthrough(document, "")
        assertEquals(raw, document.rawText)
    }
}
