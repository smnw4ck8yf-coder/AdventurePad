package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalGameLaunchContractTest {
    @Test
    fun acceptsConfiguredTargetForExactExternalAction() {
        assertEquals(
            ExternalGameLaunchRequestResult.Accepted("monkey"),
            parseExternalGameLaunchRequest(
                ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET,
                "monkey",
            ),
        )
    }

    @Test
    fun rejectsOtherActionsAndMalformedIdentifiers() {
        val rejected = listOf(
            parseExternalGameLaunchRequest(IntentActionMain, "monkey"),
            parseExternalGameLaunchRequest(ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET, null),
            parseExternalGameLaunchRequest(ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET, " "),
            parseExternalGameLaunchRequest(ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET, " monkey"),
            parseExternalGameLaunchRequest(ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET, "a/b"),
            parseExternalGameLaunchRequest(ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET, "a\\b"),
            parseExternalGameLaunchRequest(ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET, "a\nb"),
            parseExternalGameLaunchRequest(
                ExternalGameLaunchContract.ACTION_LAUNCH_SCUMMVM_TARGET,
                "a".repeat(ExternalGameLaunchContract.MAX_TARGET_ID_LENGTH + 1),
            ),
        )
        assertTrue(rejected.all { it is ExternalGameLaunchRequestResult.Rejected })
    }

    @Test
    fun resolvesOnlyExactConfiguredTargetId() {
        val targets = listOf(
            target(targetId = "indy3-fm", gameId = "indy3"),
            target(targetId = "Monkey", gameId = "monkey"),
        )

        assertEquals("indy3-fm", resolveExternalLaunchTarget("indy3-fm", targets)?.targetId)
        assertNull(resolveExternalLaunchTarget("indy3", targets))
        assertNull(resolveExternalLaunchTarget("monkey", targets))
    }

    @Test
    fun newestOverlappingRequestWinsAndCanOnlyBeConsumedOnce() {
        val coordinator = ExternalGameLaunchRequestCoordinator()
        val first = coordinator.replace("monkey")
        val second = coordinator.replace("atlantis")

        assertNull(coordinator.consume(first.generation))
        assertEquals("atlantis", coordinator.consume(second.generation))
        assertNull(coordinator.consume(second.generation))
    }

    @Test
    fun repeatedAlreadyActiveTargetCreatesAValidNewGeneration() {
        val coordinator = ExternalGameLaunchRequestCoordinator()
        val first = coordinator.replace("monkey")
        assertEquals("monkey", coordinator.consume(first.generation))

        val repeated = coordinator.replace("monkey")
        assertTrue(repeated.generation > first.generation)
        assertEquals("monkey", coordinator.consume(repeated.generation))
    }

    @Test
    fun launchAttemptsAreBoundedAndConfirmationRequiresExactActiveTarget() {
        val coordinator = ExternalGameLaunchRequestCoordinator()
        val request = coordinator.replace("atlantis")

        assertEquals("atlantis", coordinator.nextAttempt(request.generation, maxAttempts = 2))
        assertNull(coordinator.confirm("monkey-1"))
        assertEquals("atlantis", coordinator.nextAttempt(request.generation, maxAttempts = 2))
        assertNull(coordinator.nextAttempt(request.generation, maxAttempts = 2))
        assertEquals("atlantis", coordinator.confirm("atlantis"))
        assertNull(coordinator.confirm("atlantis"))
    }

    private fun target(targetId: String, gameId: String) = ScummVMTarget(
        targetId = targetId,
        title = targetId,
        engineId = "scumm",
        gameId = gameId,
    )

    private companion object {
        const val IntentActionMain = "android.intent.action.MAIN"
    }
}
