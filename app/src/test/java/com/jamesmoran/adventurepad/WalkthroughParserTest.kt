package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughParserTest {
    @Test fun numberedAndAsciiHeadingsProduceMappedHierarchy() {
        val raw = """SECTION 2: WALKTHROUGH
            |======================
            |
            |2.1 New York
            |------------
            |
            |Walk to the theatre.
            |
            |2.2 Iceland
            |-----------
            |
            |Talk to the professor.
        """.trimMargin()
        val document = parse(raw)

        assertEquals(listOf("SECTION 2: WALKTHROUGH", "New York", "Iceland"), document.sections.map { it.title })
        assertEquals(document.sections.first().id, document.sections[1].parentId)
        assertEquals("2.1 New York", raw.substring(document.sections[1].startOffset, document.sections[1].startOffset + 12))
        assertEquals(raw, document.rawText)
    }

    @Test fun partChapterAndSectionWordsAreDetectedAsStructure() {
        val raw = "Part One - Introduction\n\nText.\n\nPart Two - Carnival\n\nText.\n\nChapter 4\nText.\n\nSECTION A\nText."
        val document = parse(raw)
        assertEquals(4, document.sections.size)
        assertEquals(listOf("Part One - Introduction", "Part Two - Carnival", "Chapter 4", "SECTION A"), document.sections.map { it.title })
    }

    @Test fun markdownLevelsAndAsciiTitleAreDetected() {
        val raw = "# Guide\n\n## Route\n\nText\n\n********************\nBONUS\n********************"
        val document = parse(raw)
        assertEquals(listOf("Guide", "Route", "BONUS"), document.sections.map { it.title })
        assertEquals(listOf(1, 2, 1), document.sections.map { it.level })
    }

    @Test fun htmlHeadingsAreRecognisedWithoutChangingSource() {
        val raw = "<h1>Guide</h1>\nText\n<h2 class=\"route\">Temple <em>Route</em></h2>"
        val document = parse(raw)
        assertEquals(listOf("Guide", "Temple Route"), document.sections.map { it.title })
        assertEquals(listOf(1, 2), document.sections.map { it.level })
        assertEquals(raw, document.rawText)
    }

    @Test fun unambiguousIndentedContentsPreservesParents() {
        val raw = "Guide\n  Easy Mode\n    Chapter 1\n    Chapter 2\n  Hard Mode\n    Chapter 1\n    Chapter 2"
        val document = parse(raw)
        assertEquals(7, document.sections.size)
        assertEquals(document.sections[0].id, document.sections[1].parentId)
        assertEquals(document.sections[1].id, document.sections[2].parentId)
        assertEquals(document.sections[4].id, document.sections[5].parentId)
    }

    @Test fun flatProseAndCapitalActionCommandsCreateNoFakeHierarchy() {
        val prose = "This walkthrough has no useful headings.\nIt is simply a long sequence of prose describing the game.\nThere are no reliable structural markers."
        val actions = "PICK UP THE KEY\nUSE THE KEY ON THE DOOR\nTALK TO THE GUARD"
        assertTrue(parse(prose).sections.isEmpty())
        assertTrue(parse(actions).sections.isEmpty())
    }

    @Test fun mixedNumberingStylesRemainStableAndReparseFromRaw() {
        val raw = "1-0 Introduction\n1-1 Controls\n\n2.0 Walkthrough\n2.1a Chapter One\n2.1b Chapter Two"
        val first = parse(raw)
        val reparsed = WalkthroughParser.parse(first.rawText, 2L, first.sourceType)
        assertEquals(5, first.sections.size)
        assertEquals(first.sections.map { it.title }, reparsed.sections.map { it.title })
        assertTrue(first.sections.zipWithNext().all { (a, b) -> a.endOffset == b.startOffset })
        assertEquals(raw.length, first.sections.last().endOffset)
    }

    @Test fun plainTextFallbackKeepsAuthoritativeRawSource() {
        val raw = "# A heading\n\nCopyright Example Author"
        val plain = parse(raw).copy(sections = emptyList())
        assertEquals(raw, plain.rawText)
        assertTrue(plain.sections.isEmpty())
        assertTrue(plain.rawText.contains("Copyright"))
    }

    private fun parse(raw: String) = WalkthroughParser.parse(raw, 1L, WalkthroughSourceType.PASTED_TEXT)
}
