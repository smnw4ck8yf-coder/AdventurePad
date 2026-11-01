package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CocoonDptExportTest {
    @Test
    fun dptDocumentsUseMimeTypeThatPreservesExactExtension() {
        assertEquals("application/octet-stream", COCOON_DPT_MIME_TYPE)
    }

    @Test
    fun formattingUsesExactConfiguredTargetIdNotEngineGameId() {
        val target = target("monkey-1", "The Secret of Monkey Island", gameId = "monkey")
        val plan = planCocoonDptExport(listOf(target), emptyList(), CocoonOwnershipManifest())

        assertEquals("[scummvm_id] monkey-1\n", plan.entries.single().content)
        assertFalse(plan.entries.single().content.contains("[scummvm_id] monkey\n"))
        assertEquals("The Secret of Monkey Island.dpt", plan.entries.single().fileName)
    }

    @Test
    fun duplicateTitlesAndInvalidFilenameCharactersAreMadeSafeAndDeterministic() {
        val targets = listOf(
            target("monkey-cd", "Monkey: Island?"),
            target("monkey-floppy", "Monkey: Island?"),
            target("sky", "  /<>|  "),
        )
        val names = allocateCocoonDptFileNames(targets)

        assertEquals("Monkey- Island- (monkey-cd).dpt", names["monkey-cd"])
        assertEquals("Monkey- Island- (monkey-floppy).dpt", names["monkey-floppy"])
        assertEquals("----.dpt", names["sky"])
        assertEquals(names, allocateCocoonDptFileNames(targets.reversed()))
    }

    @Test
    fun userCreatedFileIsAlwaysAConflictEvenWhenItsContentMatches() {
        val target = target("atlantis", "Fate of Atlantis")
        val plan = planCocoonDptExport(
            targets = listOf(target),
            existingFiles = listOf(CocoonExistingDpt("Fate of Atlantis.dpt", cocoonDptContent("atlantis"))),
            ownership = CocoonOwnershipManifest(),
        )

        assertEquals(CocoonExportAction.CONFLICT, plan.entries.single().action)
        assertTrue(plan.writableEntries.isEmpty())
    }

    @Test
    fun managedFileUpdatesOnlyWhenUserHasNotChangedLastWrittenContent() {
        val oldContent = cocoonDptContent("atlantis-old")
        val ownership = CocoonOwnershipManifest(
            listOf(CocoonOwnedDpt("Fate of Atlantis.dpt", "atlantis-old", cocoonContentSha256(oldContent))),
        )
        val renamedTarget = target("atlantis", "Fate of Atlantis")

        val update = planCocoonDptExport(
            listOf(renamedTarget),
            listOf(CocoonExistingDpt("Fate of Atlantis.dpt", oldContent)),
            ownership,
        ).entries.single()
        assertEquals(CocoonExportAction.UPDATE, update.action)
        assertEquals(cocoonContentSha256(oldContent), update.expectedExistingSha256)

        val changedByUser = planCocoonDptExport(
            listOf(renamedTarget),
            listOf(CocoonExistingDpt("Fate of Atlantis.dpt", "[scummvm_id] custom\n")),
            ownership,
        ).entries.single()
        assertEquals(CocoonExportAction.CONFLICT, changedByUser.action)
    }

    @Test
    fun repeatedExportConvergesToUnchangedWithoutAdditionalWrites() {
        val directory = FakeDirectory()
        val store = FakeOwnershipStore()
        val exporter = CocoonDptExporter(store)
        val targets = listOf(target("monkey-1", "Monkey Island"), target("atlantis", "Atlantis"))

        val first = exporter.apply(exporter.preview(targets, directory), directory)
        assertEquals(2, first.written.size)
        assertEquals(2, directory.writeCount)

        val secondPlan = exporter.preview(targets, directory)
        assertEquals(2, secondPlan.count(CocoonExportAction.UNCHANGED))
        val second = exporter.apply(secondPlan, directory)
        assertTrue(second.written.isEmpty())
        assertEquals(2, directory.writeCount)
    }

    @Test
    fun titleRenameAddsNewFileAndNeverDeletesOldManagedExport() {
        val directory = FakeDirectory()
        val store = FakeOwnershipStore()
        val exporter = CocoonDptExporter(store)
        val original = listOf(target("loom-pce", "Loom"))
        exporter.apply(exporter.preview(original, directory), directory)

        val renamed = listOf(target("loom-pce", "Loom PC Engine"))
        val plan = exporter.preview(renamed, directory)
        assertEquals(CocoonExportAction.ADD, plan.entries.single().action)
        exporter.apply(plan, directory)

        assertTrue("Loom.dpt" in directory.files)
        assertTrue("Loom PC Engine.dpt" in directory.files)
        assertEquals(2, store.manifest.entries.size)
    }

    @Test
    fun targetRemovedFromLibraryDoesNotDeleteItsExport() {
        val directory = FakeDirectory()
        val store = FakeOwnershipStore()
        val exporter = CocoonDptExporter(store)
        exporter.apply(exporter.preview(listOf(target("sky", "Beneath a Steel Sky")), directory), directory)

        val emptyPlan = exporter.preview(emptyList(), directory)
        exporter.apply(emptyPlan, directory)

        assertTrue("Beneath a Steel Sky.dpt" in directory.files)
        assertEquals(1, store.manifest.entries.size)
    }

    @Test
    fun ownershipManifestRoundTripsEscapedValues() {
        val manifest = CocoonOwnershipManifest(
            listOf(CocoonOwnedDpt("A \"Game\".dpt", "target\\id", "abc123")),
        )
        assertEquals(manifest, decodeCocoonOwnershipManifest(encodeCocoonOwnershipManifest(manifest)))
    }

    @Test
    fun applyRevalidatesDirectoryStateAndWillNotOverwriteLateCollision() {
        val directory = FakeDirectory()
        val store = FakeOwnershipStore()
        val exporter = CocoonDptExporter(store)
        val plan = exporter.preview(listOf(target("samnmax", "Sam and Max")), directory)
        directory.files["Sam and Max.dpt"] = "[scummvm_id] user-file\n"

        val result = exporter.apply(plan, directory)

        assertTrue(result.written.isEmpty())
        assertEquals(1, result.failures.size)
        assertEquals("[scummvm_id] user-file\n", directory.files["Sam and Max.dpt"])
        assertTrue(store.manifest.entries.isEmpty())
    }

    private fun target(targetId: String, title: String, gameId: String = targetId) = ScummVMTarget(
        targetId = targetId,
        title = title,
        engineId = "scumm",
        gameId = gameId,
    )
}

private class FakeOwnershipStore : CocoonOwnershipStore {
    var manifest = CocoonOwnershipManifest()
    override fun load(): CocoonOwnershipManifest = manifest
    override fun save(manifest: CocoonOwnershipManifest) {
        this.manifest = manifest
    }
}

private class FakeDirectory : CocoonExportDirectory {
    val files = linkedMapOf<String, String>()
    var writeCount = 0

    override fun readDptFiles(): List<CocoonExistingDpt> = files.map { CocoonExistingDpt(it.key, it.value) }

    override fun writeDpt(fileName: String, content: String, expectedExistingSha256: String?) {
        val existingName = files.keys.singleOrNull { it.equals(fileName, ignoreCase = true) }
        if (expectedExistingSha256 == null) {
            check(existingName == null) { "Late collision" }
        } else {
            check(existingName != null) { "Missing managed file" }
            assertEquals(expectedExistingSha256, cocoonContentSha256(files.getValue(existingName)))
        }
        files[existingName ?: fileName] = content
        writeCount++
    }
}
