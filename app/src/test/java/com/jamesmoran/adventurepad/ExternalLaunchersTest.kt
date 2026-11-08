package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLaunchersTest {
    @Test
    fun onlyDetectedSupportedLauncherIsPreselected() {
        val selection = selectExternalLauncher(
            supported = listOf(CocoonExternalLauncherAdapter.definition),
            installedPackages = setOf(COCOON_PACKAGE_NAME),
            rememberedId = null,
        )

        assertEquals(COCOON_LAUNCHER_ID, selection.selectedId)
        assertEquals(setOf(COCOON_LAUNCHER_ID), selection.detectedIds)
        assertFalse(selection.needsManualSelection)
    }

    @Test
    fun rememberedSupportedLauncherIsKeptEvenWhenNotDetected() {
        val selection = selectExternalLauncher(
            supported = listOf(CocoonExternalLauncherAdapter.definition),
            installedPackages = emptySet(),
            rememberedId = COCOON_LAUNCHER_ID,
        )

        assertEquals(COCOON_LAUNCHER_ID, selection.selectedId)
        assertTrue(selection.detectedIds.isEmpty())
        assertFalse(selection.needsManualSelection)
    }

    @Test
    fun missingOrAmbiguousDetectionRequiresSupportedManualChoice() {
        val second = ExternalLauncherDefinition("future", "Future Launcher", setOf("example.future"))
        val none = selectExternalLauncher(
            supported = listOf(CocoonExternalLauncherAdapter.definition),
            installedPackages = emptySet(),
            rememberedId = null,
        )
        val multiple = selectExternalLauncher(
            supported = listOf(CocoonExternalLauncherAdapter.definition, second),
            installedPackages = setOf(COCOON_PACKAGE_NAME, "example.future"),
            rememberedId = null,
        )

        assertNull(none.selectedId)
        assertTrue(none.needsManualSelection)
        assertNull(multiple.selectedId)
        assertTrue(multiple.needsManualSelection)
    }

    @Test
    fun cocoonPlatformJsonUsesStableIdAndProvenPlayerDefinition() {
        val root = SimpleJson.parse(cocoonPlatformJson()) as Map<*, *>
        val platform = root["platform"] as Map<*, *>
        val player = (root["playerList"] as List<*>).single() as Map<*, *>

        assertEquals(14L, root["databaseVersion"])
        assertEquals(ADVENTUREPAD_PLATFORM_ID, platform["uniqueId"])
        assertEquals(ADVENTUREPAD_PLATFORM_ID, platform["shortname"])
        assertEquals("^(.*)\\.(?:dpt)$", platform["acceptedFilenameRegex"])
        assertEquals(ADVENTUREPAD_PLAYER_ID, player["uniqueId"])
        assertEquals("^(.*)\\.(?:dpt)$", player["acceptedFilenameRegex"])
        assertEquals(
            "-n com.jamesmoran.adventurepad/com.jamesmoran.adventurepad.ExternalGameLaunchActivity\n" +
                " -a com.jamesmoran.adventurepad.action.LAUNCH_SCUMMVM_TARGET\n" +
                " -e com.jamesmoran.adventurepad.extra.TARGET_ID {tags.scummvm_id}",
            player["amStartArguments"],
        )
        assertEquals(false, player["killPackageProcesses"])
        assertEquals(false, player["killPackageProcessesWarning"])
        assertEquals(ADVENTUREPAD_PLATFORM_FILE_NAME, CocoonExternalLauncherAdapter.platformFileName)
    }

    @Test
    fun preparationCreatesPlatformAndExactTargetShortcutsThenBecomesIdempotent() {
        val directory = FakePreparationDirectory()
        val gameOwnership = FakeGameOwnershipStore()
        val platformOwnership = FakePlatformOwnershipStore()
        val preparer = ExternalLauncherPreparer(
            CocoonDptExporter(gameOwnership),
            platformOwnership,
            CocoonExternalLauncherAdapter,
        )
        val targets = listOf(target("monkey-1", "Monkey Island", gameId = "monkey"))

        val firstPreview = preparer.preview(targets, directory)
        assertEquals(CocoonExportAction.ADD, firstPreview.games.entries.single().action)
        assertEquals(PreparedFileAction.ADD, firstPreview.platformFile.action)
        preparer.apply(targets, directory)

        assertEquals("[scummvm_id] monkey-1\n", directory.files.getValue("Monkey Island.dpt"))
        assertEquals(cocoonPlatformJson(), directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME))
        assertEquals(2, directory.writeCount)

        val secondPreview = preparer.preview(targets, directory)
        assertEquals(CocoonExportAction.UNCHANGED, secondPreview.games.entries.single().action)
        assertEquals(PreparedFileAction.UNCHANGED, secondPreview.platformFile.action)
        preparer.apply(targets, directory)
        assertEquals(2, directory.writeCount)
    }

    @Test
    fun userCreatedPlatformFileIsNeverOverwritten() {
        val directory = FakePreparationDirectory().apply {
            files[ADVENTUREPAD_PLATFORM_FILE_NAME] = "{\"user\":true}\n"
        }
        val platformOwnership = FakePlatformOwnershipStore()
        val preparer = ExternalLauncherPreparer(
            CocoonDptExporter(FakeGameOwnershipStore()),
            platformOwnership,
            CocoonExternalLauncherAdapter,
        )

        val preview = preparer.preview(emptyList(), directory)
        assertEquals(PreparedFileAction.CONFLICT, preview.platformFile.action)
        val result = preparer.apply(emptyList(), directory)

        assertEquals("{\"user\":true}\n", directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME))
        assertFalse(result.platformFileWritten)
        assertTrue(result.platformFileFailure!!.contains("not owned"))
        assertTrue(platformOwnership.ownership.entries.isEmpty())
    }

    @Test
    fun latePlatformCollisionIsRevalidatedBeforeWrite() {
        val directory = FakePreparationDirectory()
        val platformOwnership = FakePlatformOwnershipStore()
        val preparer = ExternalLauncherPreparer(
            CocoonDptExporter(FakeGameOwnershipStore()),
            platformOwnership,
            CocoonExternalLauncherAdapter,
        )
        val stale = preparer.preview(emptyList(), directory)
        assertEquals(PreparedFileAction.ADD, stale.platformFile.action)
        directory.files[ADVENTUREPAD_PLATFORM_FILE_NAME] = "user file"

        val result = preparer.apply(emptyList(), directory)

        assertFalse(result.platformFileWritten)
        assertEquals("user file", directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME))
        assertTrue(platformOwnership.ownership.entries.isEmpty())
    }

    @Test
    fun preparedFileOwnershipRoundTripsEscapedValues() {
        val ownership = PreparedFileOwnership(
            listOf(OwnedPreparedFile("A \"File\".json", "launcher\\id", "abc123")),
        )

        assertEquals(ownership, decodePreparedFileOwnership(encodePreparedFileOwnership(ownership)))
    }

    private fun target(targetId: String, title: String, gameId: String = targetId) = ScummVMTarget(
        targetId = targetId,
        title = title,
        engineId = "scumm",
        gameId = gameId,
    )
}

private class FakeGameOwnershipStore : CocoonOwnershipStore {
    var ownership = CocoonOwnershipManifest()
    override fun load(): CocoonOwnershipManifest = ownership
    override fun save(manifest: CocoonOwnershipManifest) {
        ownership = manifest
    }
}

private class FakePlatformOwnershipStore : PreparedFileOwnershipStore {
    var ownership = PreparedFileOwnership()
    override fun load(): PreparedFileOwnership = ownership
    override fun save(ownership: PreparedFileOwnership) {
        this.ownership = ownership
    }
}

private class FakePreparationDirectory : ExternalLauncherPreparationDirectory {
    val files = linkedMapOf<String, String>()
    var writeCount = 0

    override fun readDptFiles(): List<CocoonExistingDpt> = files
        .filterKeys { it.endsWith(COCOON_DPT_EXTENSION, ignoreCase = true) }
        .map { CocoonExistingDpt(it.key, it.value) }

    override fun writeDpt(fileName: String, content: String, expectedExistingSha256: String?) {
        write(fileName, content, expectedExistingSha256)
    }

    override fun readFilesNamed(fileName: String): List<ExistingPreparedFile> = files
        .filterKeys { it.equals(fileName, ignoreCase = true) }
        .map { ExistingPreparedFile(it.key, it.value) }

    override fun writePreparedFile(fileName: String, content: String, expectedExistingSha256: String?) {
        write(fileName, content, expectedExistingSha256)
    }

    private fun write(fileName: String, content: String, expectedExistingSha256: String?) {
        val existingName = files.keys.singleOrNull { it.equals(fileName, ignoreCase = true) }
        if (expectedExistingSha256 == null) {
            check(existingName == null) { "Late collision" }
        } else {
            check(existingName != null) { "Missing managed file" }
            assertEquals(expectedExistingSha256, preparedFileSha256(files.getValue(existingName)))
        }
        files[existingName ?: fileName] = content
        writeCount++
    }
}
