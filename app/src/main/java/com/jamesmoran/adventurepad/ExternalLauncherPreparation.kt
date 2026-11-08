package com.jamesmoran.adventurepad

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal data class ExistingPreparedFile(
    val fileName: String,
    val content: String,
)

internal data class OwnedPreparedFile(
    val fileName: String,
    val launcherId: String,
    val lastWrittenSha256: String,
)

internal data class PreparedFileOwnership(
    val entries: List<OwnedPreparedFile> = emptyList(),
)

internal interface PreparedFileOwnershipStore {
    fun load(): PreparedFileOwnership
    fun save(ownership: PreparedFileOwnership)
}

internal interface ExternalLauncherPreparationDirectory : CocoonExportDirectory {
    fun readFilesNamed(fileName: String): List<ExistingPreparedFile>
    fun writePreparedFile(fileName: String, content: String, expectedExistingSha256: String?)
}

internal enum class PreparedFileAction {
    ADD,
    UPDATE,
    CONFLICT,
    UNCHANGED,
}

internal data class PreparedFilePlan(
    val launcherId: String,
    val fileName: String,
    val content: String,
    val action: PreparedFileAction,
    val reason: String? = null,
    val expectedExistingSha256: String? = null,
) {
    val writable: Boolean
        get() = action == PreparedFileAction.ADD || action == PreparedFileAction.UPDATE
}

internal data class ExternalLauncherPreparationPlan(
    val games: CocoonExportPlan,
    val platformFile: PreparedFilePlan,
) {
    val conflicts: Int
        get() = games.count(CocoonExportAction.CONFLICT) +
            if (platformFile.action == PreparedFileAction.CONFLICT) 1 else 0

    val preparedGameCount: Int
        get() = games.entries.count { it.action != CocoonExportAction.CONFLICT }
}

internal data class ExternalLauncherPreparationResult(
    val games: CocoonExportApplyResult,
    val platformFileWritten: Boolean,
    val platformFileFailure: String?,
)

internal data class AutomaticExternalLauncherPreparationResult(
    val written: List<CocoonExportEntry>,
    val failures: List<Pair<CocoonExportEntry, String>>,
    val conflicts: Int,
    val updatesRequired: Int,
    val platformReady: Boolean,
) {
    val successful: Boolean
        get() = failures.isEmpty() && conflicts == 0 && updatesRequired == 0 && platformReady
}

internal class ExternalLauncherPreparer(
    private val gameExporter: CocoonDptExporter,
    private val platformOwnershipStore: PreparedFileOwnershipStore,
    private val adapter: ExternalLauncherAdapter,
) {
    fun preview(
        targets: List<ScummVMTarget>,
        directory: ExternalLauncherPreparationDirectory,
    ): ExternalLauncherPreparationPlan = ExternalLauncherPreparationPlan(
        games = gameExporter.preview(targets, directory),
        platformFile = planPreparedFile(
            launcherId = adapter.definition.id,
            fileName = adapter.platformFileName,
            content = adapter.platformFileContent(),
            existingFiles = directory.readFilesNamed(adapter.platformFileName),
            ownership = platformOwnershipStore.load(),
        ),
    )

    /** Re-plans immediately before every write so a stale preview can never authorize an overwrite. */
    fun apply(
        targets: List<ScummVMTarget>,
        directory: ExternalLauncherPreparationDirectory,
        pendingGameExportStore: PendingGameExportStore? = null,
    ): ExternalLauncherPreparationResult {
        pendingGameExportStore?.let { gameExporter.recoverPendingExport(directory, it) }
        val current = preview(targets, directory)
        val gameResult = gameExporter.apply(current.games, directory)
        var platformWritten = false
        var platformFailure: String? = null
        if (current.platformFile.writable) {
            runCatching {
                directory.writePreparedFile(
                    current.platformFile.fileName,
                    current.platformFile.content,
                    current.platformFile.expectedExistingSha256,
                )
            }.onSuccess {
                platformWritten = true
                val byName = platformOwnershipStore.load().entries.associateByTo(linkedMapOf()) {
                    it.fileName.lowercase(Locale.ROOT)
                }
                byName[current.platformFile.fileName.lowercase(Locale.ROOT)] = OwnedPreparedFile(
                    fileName = current.platformFile.fileName,
                    launcherId = current.platformFile.launcherId,
                    lastWrittenSha256 = preparedFileSha256(current.platformFile.content),
                )
                platformOwnershipStore.save(
                    PreparedFileOwnership(
                        byName.values.sortedWith(
                            compareBy<OwnedPreparedFile, String>(String.CASE_INSENSITIVE_ORDER) { it.fileName },
                        ),
                    ),
                )
            }.onFailure {
                platformFailure = it.message ?: "The document provider rejected the launcher file."
            }
        } else if (current.platformFile.action == PreparedFileAction.CONFLICT) {
            platformFailure = current.platformFile.reason ?: "The launcher file has a conflict."
        }
        return ExternalLauncherPreparationResult(gameResult, platformWritten, platformFailure)
    }

    /**
     * Automatic synchronization is deliberately narrower than the manual repair path: it can
     * create missing game entries only when the existing platform definition is already current.
     */
    fun applyAdditions(
        targets: List<ScummVMTarget>,
        directory: ExternalLauncherPreparationDirectory,
        journal: PendingGameExportStore,
        shouldContinue: () -> Boolean = { true },
    ): AutomaticExternalLauncherPreparationResult {
        gameExporter.recoverPendingExport(directory, journal)
        val current = preview(targets, directory)
        if (current.platformFile.action != PreparedFileAction.UNCHANGED) {
            return AutomaticExternalLauncherPreparationResult(
                written = emptyList(),
                failures = emptyList(),
                conflicts = current.conflicts,
                updatesRequired = current.games.count(CocoonExportAction.UPDATE),
                platformReady = false,
            )
        }
        val applied = gameExporter.applyAdditions(current.games, directory, journal, shouldContinue)
        return AutomaticExternalLauncherPreparationResult(
            written = applied.written,
            failures = applied.failures,
            conflicts = current.games.count(CocoonExportAction.CONFLICT),
            updatesRequired = current.games.count(CocoonExportAction.UPDATE),
            platformReady = true,
        )
    }
}

internal fun planPreparedFile(
    launcherId: String,
    fileName: String,
    content: String,
    existingFiles: List<ExistingPreparedFile>,
    ownership: PreparedFileOwnership,
): PreparedFilePlan {
    val matches = existingFiles.filter { it.fileName.equals(fileName, ignoreCase = true) }
    val existing = matches.singleOrNull()
    val owned = ownership.entries.singleOrNull { it.fileName.equals(fileName, ignoreCase = true) }
    return when {
        matches.size > 1 -> PreparedFilePlan(
            launcherId,
            fileName,
            content,
            PreparedFileAction.CONFLICT,
            "Multiple launcher files differ only by letter case.",
        )
        existing == null -> PreparedFilePlan(
            launcherId,
            fileName,
            content,
            PreparedFileAction.ADD,
        )
        owned == null -> PreparedFilePlan(
            launcherId,
            existing.fileName,
            content,
            PreparedFileAction.CONFLICT,
            "A file with this name exists but is not owned by AdventurePad.",
        )
        owned.launcherId != launcherId -> PreparedFilePlan(
            launcherId,
            existing.fileName,
            content,
            PreparedFileAction.CONFLICT,
            "This file belongs to a different AdventurePad launcher setup.",
        )
        existing.content == content -> PreparedFilePlan(
            launcherId,
            existing.fileName,
            content,
            PreparedFileAction.UNCHANGED,
        )
        preparedFileSha256(existing.content) == owned.lastWrittenSha256 -> PreparedFilePlan(
            launcherId,
            existing.fileName,
            content,
            PreparedFileAction.UPDATE,
            "AdventurePad's launcher definition changed.",
            preparedFileSha256(existing.content),
        )
        else -> PreparedFilePlan(
            launcherId,
            existing.fileName,
            content,
            PreparedFileAction.CONFLICT,
            "This AdventurePad launcher file was changed outside AdventurePad.",
        )
    }
}

internal fun preparedFileSha256(content: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(content.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
