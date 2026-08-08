package com.jamesmoran.adventurepad

internal object WalkthroughParser {
    private data class Line(val text: String, val start: Int, val end: Int, val index: Int)
    private data class Candidate(val title: String, val offset: Int, val level: Int, val lineIndex: Int)

    private val markdown = Regex("^\\s*(#{1,6})\\s+(.+?)\\s*#*\\s*$")
    private val htmlHeading = Regex("^\\s*<h([1-6])(?:\\s+[^>]*)?>(.*?)</h\\1>\\s*$", RegexOption.IGNORE_CASE)
    private val numbered = Regex("^\\s*((?:\\d+[.-])+\\d*[a-z]?|\\d+[a-z]?[.)])\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
    private val explicit = Regex(
        "^\\s*(?:(section|part|chapter)\\s+(?:[A-Z]|\\d+|[IVXLCDM]+|one|two|three|four|five|six|seven|eight|nine|ten)\\b.*|walkthrough|introduction|controls|item list|credits)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val separator = Regex("^\\s*([=\\-*+])\\1{4,}\\s*$")
    private val actionCommand = Regex("^(PICK UP|USE|TALK TO|OPEN|CLOSE|GO TO|WALK TO|LOOK AT|GIVE|TAKE)\\b")

    fun parse(rawText: String, importedAt: Long, sourceType: WalkthroughSourceType): WalkthroughDocument {
        val source = rawText.take(MAX_WALKTHROUGH_LENGTH)
        val lines = lines(source)
        val candidates = mutableListOf<Candidate>()
        val consumed = mutableSetOf<Int>()

        lines.forEach { line ->
            htmlHeading.matchEntire(line.text)?.let { match ->
                val title = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                if (title.isNotEmpty()) candidates += Candidate(title, line.start, match.groupValues[1].toInt(), line.index)
                consumed += line.index
                return@forEach
            }
            markdown.matchEntire(line.text)?.let { match ->
                candidates += Candidate(match.groupValues[2].trim(), line.start, match.groupValues[1].length, line.index)
                consumed += line.index
                return@forEach
            }
            numbered.matchEntire(line.text)?.let { match ->
                val marker = match.groupValues[1].trimEnd('.', ')')
                val level = marker.count { it == '.' || it == '-' }.coerceAtLeast(0) + 1
                candidates += Candidate(match.groupValues[2].trim(), line.start, level, line.index)
                consumed += line.index
                return@forEach
            }
            explicit.matchEntire(line.text)?.let {
                val prefix = it.groupValues[1].lowercase()
                val level = if (prefix == "chapter") 2 else 1
                candidates += Candidate(line.text.trim(), line.start, level, line.index)
                consumed += line.index
            }
        }

        // A short title directly adjacent to a repeated punctuation rule is strong visual structure.
        lines.forEach { line ->
            if (line.index in consumed || separator.matches(line.text)) return@forEach
            val before = lines.getOrNull(line.index - 1)?.text?.let(separator::matches) == true
            val after = lines.getOrNull(line.index + 1)?.text?.let(separator::matches) == true
            if ((before || after) && isSafeShortTitle(line.text)) {
                candidates += Candidate(line.text.trim().trim('+').trim(), line.start, 1, line.index)
                consumed += line.index
            }
        }

        // Preserve an unambiguous indented contents tree: every nonblank line is short,
        // indentation changes consistently, and at least one child is present. Prefer
        // the complete tree over the explicit "Chapter" leaves it contains.
        val indentedContents = parseIndentedContents(lines)
        if (indentedContents.size > candidates.size) {
            candidates.clear()
            candidates += indentedContents
        }

        val ordered = candidates
            .distinctBy { it.offset }
            .sortedBy { it.offset }
        val stack = mutableListOf<Pair<Int, String>>()
        val sections = ordered.mapIndexed { index, candidate ->
            val level = candidate.level.coerceIn(1, 6)
            while (stack.isNotEmpty() && stack.last().first >= level) stack.removeAt(stack.lastIndex)
            val id = "section-${candidate.offset}"
            val section = WalkthroughSection(
                id = id,
                title = candidate.title,
                startOffset = candidate.offset,
                endOffset = ordered.getOrNull(index + 1)?.offset ?: source.length,
                level = level,
                parentId = stack.lastOrNull()?.second,
            )
            stack += level to id
            section
        }
        return WalkthroughDocument(source, sections, importedAt, sourceType)
    }

    private fun lines(text: String): List<Line> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<Line>()
        var start = 0
        var index = 0
        while (start <= text.length) {
            val newline = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            result += Line(text.substring(start, newline).trimEnd('\r'), start, newline, index++)
            if (newline == text.length) break
            start = newline + 1
        }
        return result
    }

    private fun isSafeShortTitle(text: String): Boolean {
        val title = text.trim().trim('+').trim()
        if (title.isEmpty() || title.length > 80 || title.split(Regex("\\s+")).size > 10) return false
        return !actionCommand.containsMatchIn(title.uppercase())
    }

    private fun parseIndentedContents(lines: List<Line>): List<Candidate> {
        val nonblank = lines.filter { it.text.isNotBlank() }
        if (nonblank.size !in 3..80 || nonblank.any { it.text.trim().length > 60 }) return emptyList()
        val indents = nonblank.map { it.text.indexOfFirst { char -> !char.isWhitespace() }.coerceAtLeast(0) }
        if (indents.none { it > 0 } || nonblank.any { actionCommand.containsMatchIn(it.text.trim().uppercase()) }) return emptyList()
        val distinct = indents.distinct().sorted()
        if (distinct.size < 2 || distinct.zipWithNext().any { (a, b) -> b - a !in 1..8 }) return emptyList()
        return nonblank.mapIndexed { index, line ->
            Candidate(line.text.trim(), line.start, distinct.indexOf(indents[index]) + 1, line.index)
        }
    }
}
