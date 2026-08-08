package com.jamesmoran.adventurepad

internal enum class WalkthroughSourceType { PASTED_TEXT, TEXT_FILE, MARKDOWN_FILE }

internal enum class ReaderFont(val label: String) {
    SANS_SERIF("Sans"),
    SERIF("Serif"),
    MONOSPACE("Mono"),
}

internal enum class ReaderTextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.875f),
    DEFAULT("Default", 1f),
    LARGE("Large", 1.1875f),
    ;

    companion object {
        fun closestTo(scale: Float): ReaderTextSize = entries.minBy { kotlin.math.abs(it.scale - scale) }
    }
}

internal enum class ReadingAppearance(val label: String) {
    DARK("Default"),
    BLACK("Black"),
    DARK_GREY("Dark grey"),
    LIGHT("Light"),
    BEIGE("Beige"),
    TAN("Tan"),
    WARM("Warm"),
}

internal data class WalkthroughSection(
    val id: String,
    val title: String,
    val startOffset: Int,
    val endOffset: Int,
    val level: Int,
    val parentId: String? = null,
)

internal data class WalkthroughPosition(
    val textOffset: Int = 0,
    val sectionId: String? = null,
    val relativeInSection: Float = 0f,
)

internal data class WalkthroughReaderPreferences(
    val textScale: Float = 1f,
    val lineSpacingScale: Float = 1f,
    val appearance: ReadingAppearance = ReadingAppearance.DARK,
    val font: ReaderFont = ReaderFont.SANS_SERIF,
)

internal data class ReaderSettingsState(
    val isOpen: Boolean = false,
    val preferences: WalkthroughReaderPreferences = WalkthroughReaderPreferences(),
)

internal sealed interface ReaderSettingsAction {
    data object Open : ReaderSettingsAction
    data object Close : ReaderSettingsAction
    data class SelectFont(val font: ReaderFont) : ReaderSettingsAction
    data class SelectTextSize(val size: ReaderTextSize) : ReaderSettingsAction
    data class SelectBackground(val appearance: ReadingAppearance) : ReaderSettingsAction
}

internal fun reduceReaderSettings(
    state: ReaderSettingsState,
    action: ReaderSettingsAction,
): ReaderSettingsState = when (action) {
    ReaderSettingsAction.Open -> state.copy(isOpen = true)
    ReaderSettingsAction.Close -> state.copy(isOpen = false)
    is ReaderSettingsAction.SelectFont -> state.copy(preferences = state.preferences.copy(font = action.font))
    is ReaderSettingsAction.SelectTextSize -> state.copy(
        preferences = state.preferences.copy(textScale = action.size.scale),
    )
    is ReaderSettingsAction.SelectBackground -> state.copy(
        preferences = state.preferences.copy(appearance = action.appearance),
    )
}

internal data class WalkthroughDocument(
    val rawText: String,
    val sections: List<WalkthroughSection>,
    val importedAt: Long,
    val sourceType: WalkthroughSourceType,
    val parserSchemaVersion: Int = WALKTHROUGH_PARSER_SCHEMA_VERSION,
    val position: WalkthroughPosition = WalkthroughPosition(),
    val preferences: WalkthroughReaderPreferences = WalkthroughReaderPreferences(),
)

internal data class WalkthroughSelection(
    val gameId: String = "",
    val document: WalkthroughDocument? = null,
)

internal data class WalkthroughSearchResult(
    val offset: Int,
    val length: Int,
    val snippet: String,
    val sectionTitle: String?,
)

internal data class WalkthroughReaderTarget(
    val rawOffset: Int,
    val displayOffset: Int,
)

internal data class WalkthroughDisplayText(
    val text: String,
    private val rawToDisplayOffsets: IntArray,
) {
    fun targetForRawOffset(rawOffset: Int): WalkthroughReaderTarget {
        val safe = rawOffset.coerceIn(0, rawToDisplayOffsets.lastIndex)
        return WalkthroughReaderTarget(safe, rawToDisplayOffsets[safe])
    }

    fun displayRange(rawOffset: Int, rawLength: Int): IntRange {
        val start = targetForRawOffset(rawOffset).displayOffset
        val end = targetForRawOffset((rawOffset + rawLength).coerceAtMost(rawToDisplayOffsets.lastIndex)).displayOffset
        return start until end.coerceAtLeast(start)
    }

    fun rawOffsetForDisplayOffset(displayOffset: Int): Int {
        val target = displayOffset.coerceIn(0, text.length)
        var low = 0
        var high = rawToDisplayOffsets.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (rawToDisplayOffsets[middle] <= target) low = middle + 1 else high = middle - 1
        }
        return high.coerceIn(0, rawToDisplayOffsets.lastIndex)
    }
}

internal const val WALKTHROUGH_PARSER_SCHEMA_VERSION = 1
internal const val MAX_WALKTHROUGH_LENGTH = 5_000_000

internal fun moveWalkthroughSearchResult(current: Int, resultCount: Int, step: Int): Int {
    if (resultCount <= 0) return 0
    return ((current + step) % resultCount + resultCount) % resultCount
}

internal fun WalkthroughDocument.positionForOffset(offset: Int): WalkthroughPosition {
    val safeOffset = offset.coerceIn(0, rawText.length)
    val section = sections.lastOrNull { it.startOffset <= safeOffset }
    val relative = section?.let {
        val length = (it.endOffset - it.startOffset).coerceAtLeast(1)
        ((safeOffset - it.startOffset).toFloat() / length).coerceIn(0f, 1f)
    } ?: 0f
    return WalkthroughPosition(safeOffset, section?.id, relative)
}

internal fun WalkthroughDocument.resolvePosition(position: WalkthroughPosition): Int {
    val section = position.sectionId?.let { id -> sections.firstOrNull { it.id == id } }
    return if (section == null) {
        position.textOffset.coerceIn(0, rawText.length)
    } else {
        (section.startOffset + (section.endOffset - section.startOffset) * position.relativeInSection)
            .toInt()
            .coerceIn(section.startOffset, section.endOffset)
    }
}

internal fun searchWalkthrough(
    document: WalkthroughDocument,
    query: String,
    contextCharacters: Int = 42,
): List<WalkthroughSearchResult> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val results = mutableListOf<WalkthroughSearchResult>()
    var from = 0
    while (from <= document.rawText.length - needle.length) {
        val found = document.rawText.indexOf(needle, from, ignoreCase = true)
        if (found < 0) break
        val start = (found - contextCharacters).coerceAtLeast(0)
        val end = (found + needle.length + contextCharacters).coerceAtMost(document.rawText.length)
        val snippet = document.rawText.substring(start, end)
            .replace(Regex("\\s+"), " ")
            .trim()
        val section = document.sections.lastOrNull { it.startOffset <= found && found < it.endOffset }
        results += WalkthroughSearchResult(
            offset = found,
            length = needle.length,
            snippet = "${if (start > 0) "…" else ""}$snippet${if (end < document.rawText.length) "…" else ""}",
            sectionTitle = section?.title,
        )
        from = found + needle.length.coerceAtLeast(1)
    }
    return results
}

internal fun appendWalkthroughToNotes(
    existingNotes: String,
    passage: String,
    sectionTitle: String?,
): String {
    val cleanPassage = passage.trim()
    if (cleanPassage.isEmpty()) return existingNotes
    val heading = sectionTitle?.takeIf(String::isNotBlank)?.let { "--- Walkthrough: $it ---" }
        ?: "--- Walkthrough ---"
    return buildString {
        append(existingNotes.trimEnd())
        if (isNotEmpty()) append("\n\n")
        append(heading)
        append("\n\n")
        append(cleanPassage)
    }.take(MAX_NOTES_LENGTH)
}

/**
 * Joins only lines that look like legacy column-wrapped prose. The imported source remains the
 * canonical text for search, navigation, notes, and persistence; this result is display-only.
 */
internal fun reflowWalkthroughForDisplay(
    rawText: String,
    sectionTitles: Set<String> = emptySet(),
): String = walkthroughDisplayText(rawText, sectionTitles).text

internal fun walkthroughDisplayText(
    rawText: String,
    sectionTitles: Set<String> = emptySet(),
): WalkthroughDisplayText {
    val rawToNormalized = IntArray(rawText.length + 1)
    val normalized = StringBuilder(rawText.length)
    var rawIndex = 0
    while (rawIndex < rawText.length) {
        rawToNormalized[rawIndex] = normalized.length
        if (rawText[rawIndex] == '\r') {
            if (rawIndex + 1 < rawText.length && rawText[rawIndex + 1] == '\n') {
                rawToNormalized[rawIndex + 1] = normalized.length
                rawIndex++
            }
            normalized.append('\n')
        } else {
            normalized.append(rawText[rawIndex])
        }
        rawIndex++
    }
    rawToNormalized[rawText.length] = normalized.length
    val displaySource = normalized.toString()
    if ('\n' !in displaySource) return displaySource
        .let { WalkthroughDisplayText(it, rawToNormalized.copyOf()) }

    val normalizedToDisplay = IntArray(displaySource.length + 1)
    val lines = displaySource.split('\n')
    val displayed = buildString(displaySource.length) {
        var normalizedLineStart = 0
        lines.forEachIndexed { index, line ->
            val join = shouldJoinWalkthroughLines(line, lines.getOrNull(index + 1), sectionTitles)
            val charactersToAppend = if (join) line.indexOfLast { !it.isWhitespace() } + 1 else line.length
            line.indices.forEach { lineIndex ->
                normalizedToDisplay[normalizedLineStart + lineIndex] = length
                if (lineIndex < charactersToAppend) append(line[lineIndex])
            }
            if (index < lines.lastIndex) {
                val newlineOffset = normalizedLineStart + line.length
                normalizedToDisplay[newlineOffset] = length
                append(if (join) ' ' else '\n')
                normalizedLineStart = newlineOffset + 1
            }
        }
        normalizedToDisplay[displaySource.length] = length
    }
    val rawToDisplay = IntArray(rawText.length + 1) { index ->
        normalizedToDisplay[rawToNormalized[index]]
    }
    return WalkthroughDisplayText(displayed, rawToDisplay)
}

private fun shouldJoinWalkthroughLines(
    line: String,
    next: String?,
    sectionTitles: Set<String>,
): Boolean {
    if (next == null || !line.isWalkthroughProse(sectionTitles) || !next.isWalkthroughProse(sectionTitles)) return false
    val current = line.trim()
    val following = next.trim()
    val currentLooksWrapped = current.length >= LEGACY_WRAP_MINIMUM
    val nextContinuesSentence = following.length >= LEGACY_WRAP_MINIMUM &&
        current.lastOrNull() !in setOf('.', '!', '?', ':')
    return currentLooksWrapped || nextContinuesSentence
}

private fun String.isWalkthroughProse(sectionTitles: Set<String>): Boolean {
    val trimmed = trim()
    if (trimmed.isEmpty() || trimmed in sectionTitles) return false
    if (length - trimStart().length >= 2 || startsWith('\t')) return false
    if (MARKDOWN_HEADING.matches(trimmed) || LIST_ITEM.matches(trimmed) || ASCII_SEPARATOR.matches(trimmed)) return false
    if (COMMAND_OR_QUOTE.matches(trimmed) || REFERENCE_LINE.matches(trimmed)) return false
    if (ASCII_STRUCTURE.containsMatchIn(trimmed)) return false
    if (trimmed.length <= 80 && trimmed.any(Char::isLetter) && trimmed.filter(Char::isLetter).all(Char::isUpperCase)) return false
    return true
}

private const val LEGACY_WRAP_MINIMUM = 45
private val MARKDOWN_HEADING = Regex("^#{1,6}\\s+.+$")
private val LIST_ITEM = Regex("^(?:[-*+•]|\\d+[.)]|[A-Za-z][.)])\\s+.+$")
private val ASCII_SEPARATOR = Regex("^[*=_-]{3,}\\s*$")
private val COMMAND_OR_QUOTE = Regex("^(?:[>$]|::|```|~~~).*$")
private val REFERENCE_LINE = Regex("^[A-Za-z][A-Za-z0-9 _/-]{0,24}:\\s+\\S.*$")
private val ASCII_STRUCTURE = Regex("(?:\\|.*\\||\\+-{2,}|-{2,}\\+|[_/\\\\]{3,})")
