package com.jamesmoran.adventurepad

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorCropRepositoryTest {
    private val geometry = MirrorSourceGeometry(
        width = 320,
        height = 200,
        rendererCapability = 1,
        generation = 3,
    )
    private val launcherProfile = compatibleProfile(0.7f)

    @Test fun anonymousToRealTargetMigratesCompatibleLauncherProfile() = runRepositoryTest {
        val store = FakeMirrorCropStore(mapOf("" to launcherProfile))
        val repository = repository(store)
        awaitSelection(repository, "")

        repository.handoffLauncherProfile("atlantis", geometry)
        awaitSelection(repository, "atlantis")

        assertEquals(launcherProfile, store.profileValue("atlantis"))
        assertEquals(launcherProfile, repository.selection.value.profile)
        assertEquals(listOf("atlantis"), store.savedGameIds)
    }

    @Test fun existingRealTargetProfileWinsWithoutBeingOverwritten() = runRepositoryTest {
        val targetProfile = compatibleProfile(0.8f)
        val store = FakeMirrorCropStore(mapOf("" to launcherProfile, "atlantis" to targetProfile))
        val repository = repository(store)

        repository.handoffLauncherProfile("atlantis", geometry)
        awaitSelection(repository, "atlantis")

        assertEquals(targetProfile, repository.selection.value.profile)
        assertTrue(store.savedGameIds.isEmpty())
    }

    @Test fun incompatibleLauncherProfileIsNotMigrated() = runRepositoryTest {
        val incompatible = launcherProfile.copy(sourceWidth = 640)
        val store = FakeMirrorCropStore(mapOf("" to incompatible))
        val repository = repository(store)

        repository.handoffLauncherProfile("atlantis", geometry)
        awaitSelection(repository, "atlantis")

        assertEquals(MirrorCropProfile.Empty, repository.selection.value.profile)
        assertTrue(store.savedGameIds.isEmpty())
    }

    @Test fun launcherMigrationHappensOnlyOnce() = runRepositoryTest {
        val store = FakeMirrorCropStore(mapOf("" to launcherProfile))
        val repository = repository(store)

        repository.handoffLauncherProfile("atlantis", geometry)
        awaitSelection(repository, "atlantis")
        repository.selectGame("")
        awaitSelection(repository, "")
        repository.handoffLauncherProfile("atlantis", geometry)
        awaitSelection(repository, "atlantis")

        assertEquals(listOf("atlantis"), store.savedGameIds)
    }

    @Test fun anonymousProfileKeepsSplitViewEligibleDuringHandoff() = runRepositoryTest {
        val saveStarted = CompletableDeferred<Unit>()
        val allowSave = CompletableDeferred<Unit>()
        val store = FakeMirrorCropStore(
            initial = mapOf("" to launcherProfile),
            saveStarted = saveStarted,
            allowSave = allowSave,
        )
        val repository = repository(store)
        awaitSelection(repository, "")

        coroutineScope {
            val handoff = launch {
                repository.handoffLauncherProfile("atlantis", geometry)
            }
            saveStarted.await()

            assertEquals("", repository.selection.value.gameId)
            val savedSplit = compatibleCropSplit(
                selection = repository.selection.value,
                geometry = geometry.copy(gameId = "atlantis"),
                pendingTargetId = "atlantis",
            )
            val target = resolvePresentationTarget(
                preferredMode = DisplayMode.INTERFACE,
                savedSplit = savedSplit,
                editorSplit = null,
                connected = true,
                activeSurfaceReady = true,
            )

            assertEquals(PresentationOwner.SPLIT_VIEW, target.owner)
            assertEquals(DisplayMode.INTERFACE, target.mode)
            assertTrue(target.runtimePanelRequested)

            allowSave.complete(Unit)
            handoff.join()
            assertEquals("atlantis", repository.selection.value.gameId)
        }
    }

    @Test fun knownGamesKeepIndependentProfilesWhenSwitching() = runRepositoryTest {
        val atlantis = compatibleProfile(0.7f)
        val monkey2 = compatibleProfile(0.8f)
        val store = FakeMirrorCropStore(mapOf("atlantis" to atlantis, "monkey2" to monkey2))
        val repository = repository(store)

        repository.selectGame("atlantis")
        awaitSelection(repository, "atlantis")
        assertEquals(atlantis, repository.selection.value.profile)
        repository.selectGame("monkey2")
        awaitSelection(repository, "monkey2")
        assertEquals(monkey2, repository.selection.value.profile)
        repository.selectGame("atlantis")
        awaitSelection(repository, "atlantis")
        assertEquals(atlantis, repository.selection.value.profile)
        assertTrue(store.savedGameIds.isEmpty())
    }

    private fun compatibleProfile(ratio: Float) = MirrorCropProfile(
        split = InterfaceSplit(ratio),
        sourceWidth = geometry.width,
        sourceHeight = geometry.height,
        sourceAspectRatio = geometry.aspectRatio,
        confirmed = true,
        requiresReview = false,
    )

    private suspend fun awaitSelection(repository: MirrorCropRepository, gameId: String) {
        repeat(100) {
            if (repository.selection.value.gameId == gameId) return
            yield()
        }
        assertEquals(gameId, repository.selection.value.gameId)
    }

    private fun runRepositoryTest(block: suspend RepositoryTestScope.() -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            RepositoryTestScope(scope).block()
        } finally {
            scope.cancel()
        }
    }

    private class RepositoryTestScope(private val scope: CoroutineScope) {
        fun repository(store: MirrorCropStore) = MirrorCropRepository(store, scope)
    }
}

private class FakeMirrorCropStore(
    initial: Map<String, MirrorCropProfile>,
    private val saveStarted: CompletableDeferred<Unit>? = null,
    private val allowSave: CompletableDeferred<Unit>? = null,
) : MirrorCropStore {
    private val profiles = initial.mapValues { MutableStateFlow(it.value) }.toMutableMap()
    val savedGameIds = mutableListOf<String>()

    override fun profile(gameId: String): Flow<MirrorCropProfile> =
        profiles.getOrPut(gameId) { MutableStateFlow(MirrorCropProfile.Empty) }

    override suspend fun save(gameId: String, profile: MirrorCropProfile) {
        saveStarted?.complete(Unit)
        allowSave?.await()
        savedGameIds += gameId
        profiles.getOrPut(gameId) { MutableStateFlow(MirrorCropProfile.Empty) }.value = profile
    }

    fun profileValue(gameId: String): MirrorCropProfile =
        profiles[gameId]?.value ?: MirrorCropProfile.Empty
}
