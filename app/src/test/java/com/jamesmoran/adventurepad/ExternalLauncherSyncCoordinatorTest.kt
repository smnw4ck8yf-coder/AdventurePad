package com.jamesmoran.adventurepad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExternalLauncherSyncCoordinatorTest {
    @Test
    fun ninthGameAddsOnlyTheDigAndSecondSyncWritesNothing() {
        val directory = SyncFakeDirectory()
        val ownership = SyncFakeOwnershipStore()
        val platformOwnership = SyncFakePlatformOwnershipStore()
        val journal = SyncFakeJournal()
        val preparer = syncPreparer(ownership, platformOwnership)
        val originalTargets = originalEightTargets()

        val initial = preparer.apply(originalTargets, directory)
        assertEquals(9, directory.writeCount)
        assertTrue(initial.games.failures.isEmpty())
        val originalHashes = directory.files
            .filterKeys { it.endsWith(COCOON_DPT_EXTENSION) }
            .mapValues { cocoonContentSha256(it.value) }
        val originalPlatform = directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME)

        val first = preparer.applyAdditions(originalTargets + target("dig", "The Dig"), directory, journal)

        assertTrue(first.successful)
        assertEquals(listOf("The Dig.dpt"), first.written.map(CocoonExportEntry::fileName))
        assertEquals("[scummvm_id] dig\n", directory.files.getValue("The Dig.dpt"))
        assertEquals(10, directory.writeCount)
        assertEquals(originalPlatform, directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME))
        originalHashes.forEach { (fileName, hash) ->
            assertEquals(hash, cocoonContentSha256(directory.files.getValue(fileName)))
        }
        assertEquals("dig", ownership.ownership.entries.single { it.fileName == "The Dig.dpt" }.targetId)
        assertNull(journal.pending)

        val second = preparer.applyAdditions(originalTargets + target("dig", "The Dig"), directory, journal)

        assertTrue(second.successful)
        assertTrue(second.written.isEmpty())
        assertEquals(10, directory.writeCount)
        assertEquals(originalPlatform, directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME))
    }

    @Test
    fun existingUnownedCollisionIsNeverClaimedOrOverwritten() {
        val directory = SyncFakeDirectory().apply {
            files[ADVENTUREPAD_PLATFORM_FILE_NAME] = cocoonPlatformJson()
            files["The Dig.dpt"] = "[scummvm_id] user-target\n"
        }
        val platformOwnership = SyncFakePlatformOwnershipStore().apply {
            ownership = PreparedFileOwnership(
                listOf(
                    OwnedPreparedFile(
                        ADVENTUREPAD_PLATFORM_FILE_NAME,
                        COCOON_LAUNCHER_ID,
                        preparedFileSha256(cocoonPlatformJson()),
                    ),
                ),
            )
        }
        val ownership = SyncFakeOwnershipStore()
        val journal = SyncFakeJournal()

        val result = syncPreparer(ownership, platformOwnership)
            .applyAdditions(listOf(target("dig", "The Dig")), directory, journal)

        assertFalse(result.successful)
        assertEquals(1, result.conflicts)
        assertTrue(result.written.isEmpty())
        assertEquals("[scummvm_id] user-target\n", directory.files.getValue("The Dig.dpt"))
        assertTrue(ownership.ownership.entries.isEmpty())
        assertNull(journal.pending)
    }

    @Test
    fun automaticSyncNeverOverwritesManagedEntryThatNeedsUpdate() {
        val fixture = preparedPlatformFixture()
        fixture.preparer.apply(listOf(target("dig-old", "The Dig")), fixture.directory)
        val oldContent = fixture.directory.files.getValue("The Dig.dpt")
        val writesBeforeSync = fixture.directory.writeCount

        val result = fixture.preparer.applyAdditions(
            listOf(target("dig", "The Dig")),
            fixture.directory,
            fixture.journal,
        )

        assertFalse(result.successful)
        assertEquals(1, result.updatesRequired)
        assertTrue(result.written.isEmpty())
        assertEquals(writesBeforeSync, fixture.directory.writeCount)
        assertEquals(oldContent, fixture.directory.files.getValue("The Dig.dpt"))
        assertEquals("dig-old", fixture.ownership.ownership.entries.single().targetId)
    }

    @Test
    fun automaticSyncNeverCreatesOrOverwritesPlatformJson() {
        val directory = SyncFakeDirectory()
        val ownership = SyncFakeOwnershipStore()
        val platformOwnership = SyncFakePlatformOwnershipStore()
        val journal = SyncFakeJournal()
        val preparer = syncPreparer(ownership, platformOwnership)

        val missing = preparer.applyAdditions(listOf(target("dig", "The Dig")), directory, journal)
        assertFalse(missing.platformReady)
        assertEquals(0, directory.writeCount)
        assertFalse(ADVENTUREPAD_PLATFORM_FILE_NAME in directory.files)

        directory.files[ADVENTUREPAD_PLATFORM_FILE_NAME] = "{\"user\":true}\n"
        val existing = preparer.applyAdditions(listOf(target("dig", "The Dig")), directory, journal)
        assertFalse(existing.platformReady)
        assertEquals(0, directory.writeCount)
        assertEquals("{\"user\":true}\n", directory.files.getValue(ADVENTUREPAD_PLATFORM_FILE_NAME))
    }

    @Test
    fun exactJournaledWriteIsRecoveredWithoutASecondWrite() {
        val fixture = preparedPlatformFixture()
        fixture.directory.failAfterWritingFileName = "The Dig.dpt"

        val interrupted = fixture.preparer.applyAdditions(
            listOf(target("dig", "The Dig")),
            fixture.directory,
            fixture.journal,
        )

        assertEquals(1, interrupted.failures.size)
        assertEquals("[scummvm_id] dig\n", fixture.directory.files.getValue("The Dig.dpt"))
        assertTrue(fixture.ownership.ownership.entries.isEmpty())
        assertEquals("The Dig.dpt", fixture.journal.pending?.fileName)
        val writesAfterInterruption = fixture.directory.writeCount
        fixture.directory.failAfterWritingFileName = null

        val recovered = fixture.preparer.applyAdditions(
            listOf(target("dig", "The Dig")),
            fixture.directory,
            fixture.journal,
        )

        assertTrue(recovered.successful)
        assertTrue(recovered.written.isEmpty())
        assertEquals(writesAfterInterruption, fixture.directory.writeCount)
        assertEquals("dig", fixture.ownership.ownership.entries.single().targetId)
        assertNull(fixture.journal.pending)
    }

    @Test
    fun partialJournaledWriteBecomesConflictAndIsNeverOverwritten() {
        val fixture = preparedPlatformFixture()
        fixture.directory.partialFailureFileName = "The Dig.dpt"

        val interrupted = fixture.preparer.applyAdditions(
            listOf(target("dig", "The Dig")),
            fixture.directory,
            fixture.journal,
        )

        assertEquals(1, interrupted.failures.size)
        assertEquals("[scummvm_id]", fixture.directory.files.getValue("The Dig.dpt"))
        fixture.directory.partialFailureFileName = null
        val writesAfterInterruption = fixture.directory.writeCount

        val retried = fixture.preparer.applyAdditions(
            listOf(target("dig", "The Dig")),
            fixture.directory,
            fixture.journal,
        )

        assertFalse(retried.successful)
        assertEquals(1, retried.conflicts)
        assertEquals(writesAfterInterruption, fixture.directory.writeCount)
        assertTrue(fixture.ownership.ownership.entries.isEmpty())
        assertNull(fixture.journal.pending)
    }

    @Test
    fun missingSetupPermissionFolderOrLauncherDisablesAutomaticSync() {
        assertFalse(ExternalLauncherSyncEligibility(false, true, true, true).eligible)
        assertFalse(ExternalLauncherSyncEligibility(true, false, true, true).eligible)
        assertFalse(ExternalLauncherSyncEligibility(true, true, false, true).eligible)
        assertFalse(ExternalLauncherSyncEligibility(true, true, true, false).eligible)
        assertTrue(ExternalLauncherSyncEligibility(true, true, true, true).eligible)
    }

    @Test
    fun pendingJournalRoundTripsExactValues() {
        val pending = PendingGameExport("The \"Dig\".dpt", "dig\\cd", "abc123")
        assertEquals(pending, decodePendingGameExport(encodePendingGameExport(pending)))
    }

    @Test
    fun coordinatorCoalescesRepeatedSnapshotsAndDropsQueuedStaleSnapshot() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val releaseDispatcher = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val observed = mutableListOf<List<String>>()
        dispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext) { releaseDispatcher.await() }
        try {
            val coordinator = ExternalLauncherSyncCoordinator(
                CoroutineScope(SupervisorJob()),
                AutomaticExternalLauncherSynchronizer { targets, _ ->
                    synchronized(observed) { observed += targets.map(ScummVMTarget::targetId) }
                    completed.countDown()
                    true
                },
                dispatcher,
            )

            coordinator.submit(listOf(target("old", "Old")))
            coordinator.submit(listOf(target("dig", "The Dig")))
            coordinator.submit(listOf(target("dig", "The Dig")))
            releaseDispatcher.countDown()

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(listOf("dig")), synchronized(observed) { observed.toList() })
        } finally {
            releaseDispatcher.countDown()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private fun preparedPlatformFixture(): SyncFixture {
        val directory = SyncFakeDirectory()
        val ownership = SyncFakeOwnershipStore()
        val platformOwnership = SyncFakePlatformOwnershipStore()
        val preparer = syncPreparer(ownership, platformOwnership)
        preparer.apply(emptyList(), directory)
        directory.writeCount = 0
        return SyncFixture(directory, ownership, SyncFakeJournal(), preparer)
    }

    private fun syncPreparer(
        ownership: SyncFakeOwnershipStore,
        platformOwnership: SyncFakePlatformOwnershipStore,
    ) = ExternalLauncherPreparer(
        CocoonDptExporter(ownership),
        platformOwnership,
        CocoonExternalLauncherAdapter,
    )

    private fun originalEightTargets() = listOf(
        target("sky", "Beneath a Steel Sky (v0.0368 CD-DOS)"),
        target("tentacle", "Day of the Tentacle (CD-English)"),
        target("gk1-cd", "Gabriel Knight: Sins of the Fathers (CD-DOS-English)"),
        target("atlantis", "Indiana Jones and the Fate of Atlantis (CD-DOS-English)"),
        target("indy3-fm", "Indiana Jones and the Last Crusade (FM-TOWNS-English)"),
        target("loom-pce", "Loom (PC-Engine-English)"),
        target("samnmax", "Sam & Max Hit the Road (CD-English)"),
        target("monkey-1", "The Secret of Monkey Island (Unofficial SE Talkie v1.02-DOS-English)"),
    )

    private fun target(targetId: String, title: String) = ScummVMTarget(
        targetId = targetId,
        title = title,
        engineId = "scumm",
        gameId = targetId,
    )
}

private data class SyncFixture(
    val directory: SyncFakeDirectory,
    val ownership: SyncFakeOwnershipStore,
    val journal: SyncFakeJournal,
    val preparer: ExternalLauncherPreparer,
)

private class SyncFakeOwnershipStore : CocoonOwnershipStore {
    var ownership = CocoonOwnershipManifest()
    override fun load() = ownership
    override fun save(manifest: CocoonOwnershipManifest) {
        ownership = manifest
    }
}

private class SyncFakePlatformOwnershipStore : PreparedFileOwnershipStore {
    var ownership = PreparedFileOwnership()
    override fun load() = ownership
    override fun save(ownership: PreparedFileOwnership) {
        this.ownership = ownership
    }
}

private class SyncFakeJournal : PendingGameExportStore {
    var pending: PendingGameExport? = null
    override fun load() = pending
    override fun save(pending: PendingGameExport) {
        this.pending = pending
    }
    override fun clear() {
        pending = null
    }
}

private class SyncFakeDirectory : ExternalLauncherPreparationDirectory {
    val files = linkedMapOf<String, String>()
    var writeCount = 0
    var failAfterWritingFileName: String? = null
    var partialFailureFileName: String? = null

    override fun readDptFiles() = files
        .filterKeys { it.endsWith(COCOON_DPT_EXTENSION, ignoreCase = true) }
        .map { CocoonExistingDpt(it.key, it.value) }

    override fun writeDpt(fileName: String, content: String, expectedExistingSha256: String?) {
        write(fileName, content, expectedExistingSha256)
    }

    override fun readFilesNamed(fileName: String) = files
        .filterKeys { it.equals(fileName, ignoreCase = true) }
        .map { ExistingPreparedFile(it.key, it.value) }

    override fun writePreparedFile(fileName: String, content: String, expectedExistingSha256: String?) {
        write(fileName, content, expectedExistingSha256)
    }

    private fun write(fileName: String, content: String, expectedExistingSha256: String?) {
        val existingName = files.keys.singleOrNull { it.equals(fileName, ignoreCase = true) }
        if (expectedExistingSha256 == null) check(existingName == null) { "Late collision" }
        else {
            check(existingName != null) { "Missing managed file" }
            check(preparedFileSha256(files.getValue(existingName)) == expectedExistingSha256)
        }
        writeCount++
        if (partialFailureFileName == fileName) {
            files[existingName ?: fileName] = content.substringBefore(' ')
            error("Interrupted partial write")
        }
        files[existingName ?: fileName] = content
        if (failAfterWritingFileName == fileName) error("Interrupted after write")
    }
}
