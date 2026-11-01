package com.jamesmoran.adventurepad

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal const val COCOON_DPT_EXTENSION = ".dpt"

internal data class CocoonExistingDpt(
    val fileName: String,
    val content: String,
)

internal data class CocoonOwnedDpt(
    val fileName: String,
    val targetId: String,
    val lastWrittenSha256: String,
)

internal data class CocoonOwnershipManifest(
    val entries: List<CocoonOwnedDpt> = emptyList(),
)

internal enum class CocoonExportAction {
    ADD,
    UPDATE,
    CONFLICT,
    UNCHANGED,
}

internal data class CocoonExportEntry(
    val targetId: String,
    val title: String,
    val fileName: String,
    val content: String,
    val action: CocoonExportAction,
    val reason: String? = null,
    val expectedExistingSha256: String? = null,
)

internal data class CocoonExportPlan(
    val entries: List<CocoonExportEntry>,
) {
    fun count(action: CocoonExportAction): Int = entries.count { it.action == action }
    val writableEntries: List<CocoonExportEntry>
        get() = entries.filter { it.action == CocoonExportAction.ADD || it.action == CocoonExportAction.UPDATE }
}

internal data class CocoonExportApplyResult(
    val written: List<CocoonExportEntry>,
    val failures: List<Pair<CocoonExportEntry, String>>,
    val manifest: CocoonOwnershipManifest,
)

internal interface CocoonExportDirectory {
    fun readDptFiles(): List<CocoonExistingDpt>
    fun writeDpt(fileName: String, content: String, expectedExistingSha256: String?)
}

internal interface CocoonOwnershipStore {
    fun load(): CocoonOwnershipManifest
    fun save(manifest: CocoonOwnershipManifest)
}

internal class CocoonDptExporter(
    private val ownershipStore: CocoonOwnershipStore,
) {
    fun preview(
        targets: List<ScummVMTarget>,
        directory: CocoonExportDirectory,
    ): CocoonExportPlan = planCocoonDptExport(
        targets = targets,
        existingFiles = directory.readDptFiles(),
        ownership = ownershipStore.load(),
    )

    fun apply(
        plan: CocoonExportPlan,
        directory: CocoonExportDirectory,
    ): CocoonExportApplyResult {
        val manifestEntries = ownershipStore.load().entries.associateByTo(linkedMapOf()) {
            it.fileName.lowercase(Locale.ROOT)
        }
        val written = mutableListOf<CocoonExportEntry>()
        val failures = mutableListOf<Pair<CocoonExportEntry, String>>()
        plan.writableEntries.forEach { entry ->
            runCatching {
                directory.writeDpt(entry.fileName, entry.content, entry.expectedExistingSha256)
            }
                .onSuccess {
                    written += entry
                    manifestEntries[entry.fileName.lowercase(Locale.ROOT)] = CocoonOwnedDpt(
                        fileName = entry.fileName,
                        targetId = entry.targetId,
                        lastWrittenSha256 = cocoonContentSha256(entry.content),
                    )
                }
                .onFailure { error ->
                    failures += entry to (error.message ?: "The document provider rejected the write.")
                }
        }
        val updated = CocoonOwnershipManifest(
            manifestEntries.values.sortedWith(
                compareBy<CocoonOwnedDpt, String>(String.CASE_INSENSITIVE_ORDER) { it.fileName },
            ),
        )
        ownershipStore.save(updated)
        return CocoonExportApplyResult(written, failures, updated)
    }
}

internal fun cocoonDptContent(targetId: String): String = "[scummvm_id] $targetId\n"

internal fun planCocoonDptExport(
    targets: List<ScummVMTarget>,
    existingFiles: List<CocoonExistingDpt>,
    ownership: CocoonOwnershipManifest,
): CocoonExportPlan {
    val existingByName = existingFiles.groupBy { it.fileName.lowercase(Locale.ROOT) }
    val ownedByName = ownership.entries.associateBy { it.fileName.lowercase(Locale.ROOT) }
    val fileNames = allocateCocoonDptFileNames(targets)
    val entries = targets
        .distinctBy(ScummVMTarget::targetId)
        .sortedWith(
            compareBy<ScummVMTarget, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.targetId },
        )
        .map { target ->
            val fileName = checkNotNull(fileNames[target.targetId])
            val content = cocoonDptContent(target.targetId)
            val invalidReason = validateCocoonTargetId(target.targetId)
            val matches = existingByName[fileName.lowercase(Locale.ROOT)].orEmpty()
            val existing = matches.singleOrNull()
            val owned = ownedByName[fileName.lowercase(Locale.ROOT)]
            when {
                invalidReason != null -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    fileName,
                    content,
                    CocoonExportAction.CONFLICT,
                    invalidReason,
                )
                matches.size > 1 -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    fileName,
                    content,
                    CocoonExportAction.CONFLICT,
                    "Multiple files differ only by letter case.",
                )
                existing == null -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    fileName,
                    content,
                    CocoonExportAction.ADD,
                )
                owned == null -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    existing.fileName,
                    content,
                    CocoonExportAction.CONFLICT,
                    "A file with this name exists but is not owned by AdventurePad.",
                )
                existing.content == content -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    existing.fileName,
                    content,
                    CocoonExportAction.UNCHANGED,
                )
                cocoonContentSha256(existing.content) == owned.lastWrittenSha256 -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    existing.fileName,
                    content,
                    CocoonExportAction.UPDATE,
                    "AdventurePad's managed target ID changed.",
                    cocoonContentSha256(existing.content),
                )
                else -> CocoonExportEntry(
                    target.targetId,
                    target.title,
                    existing.fileName,
                    content,
                    CocoonExportAction.CONFLICT,
                    "This AdventurePad export was changed outside AdventurePad.",
                )
            }
        }
    return CocoonExportPlan(entries)
}

internal fun allocateCocoonDptFileNames(targets: List<ScummVMTarget>): Map<String, String> {
    val uniqueTargets = targets.distinctBy(ScummVMTarget::targetId)
    val baseNames = uniqueTargets.associate { target ->
        target.targetId to sanitizeCocoonFileName(target.title.ifBlank { target.targetId })
    }
    val duplicateBases = baseNames.values
        .groupingBy { it.lowercase(Locale.ROOT) }
        .eachCount()
    val allocated = linkedMapOf<String, String>()
    val used = mutableSetOf<String>()
    uniqueTargets.sortedBy { it.targetId.lowercase(Locale.ROOT) }.forEach { target ->
        val base = checkNotNull(baseNames[target.targetId])
        val withTarget = if (duplicateBases[base.lowercase(Locale.ROOT)]!! > 1) {
            "$base (${sanitizeCocoonFileName(target.targetId)})"
        } else {
            base
        }
        var candidate = trimCocoonBaseName(withTarget)
        var suffix = 2
        while (!used.add("${candidate.lowercase(Locale.ROOT)}$COCOON_DPT_EXTENSION")) {
            candidate = trimCocoonBaseName("$withTarget ($suffix)")
            suffix++
        }
        allocated[target.targetId] = "$candidate$COCOON_DPT_EXTENSION"
    }
    return allocated
}

internal fun sanitizeCocoonFileName(value: String): String {
    val sanitized = buildString {
        value.forEach { char ->
            append(
                when {
                    char.isISOControl() -> ' '
                    char in InvalidFileNameCharacters -> '-'
                    else -> char
                },
            )
        }
    }.replace(WhitespaceRegex, " ").trim().trim('.', ' ')
    return trimCocoonBaseName(sanitized.ifBlank { "ScummVM Game" })
}

private fun trimCocoonBaseName(value: String): String = value
    .take(MAX_COCOON_DPT_BASE_LENGTH)
    .trim()
    .trimEnd('.', ' ')
    .ifBlank { "ScummVM Game" }

private fun validateCocoonTargetId(targetId: String): String? = when {
    targetId.isBlank() -> "The configured target ID is blank."
    targetId != targetId.trim() -> "The configured target ID has surrounding whitespace."
    targetId.any(Char::isISOControl) -> "The configured target ID contains control characters."
    else -> null
}

internal fun cocoonContentSha256(content: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(content.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private const val MAX_COCOON_DPT_BASE_LENGTH = 120
private val InvalidFileNameCharacters = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
private val WhitespaceRegex = Regex("\\s+")
