package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTargetTest {
    @Test fun nonblankReportedTargetBecomesCanonical() {
        assertEquals("atlantis", canonicalCompanionTargetId("", "atlantis"))
    }

    @Test fun transientBlankDoesNotClearConfirmedTarget() {
        assertEquals("atlantis", canonicalCompanionTargetId("atlantis", ""))
        assertEquals("atlantis", canonicalCompanionTargetId("atlantis", "   "))
    }

    @Test fun validThenTransientBlankKeepsNotesAndWalkthroughAvailableOnTheValidTarget() {
        var retained = ""
        var notesTarget = ""
        var walkthroughTarget = ""
        fun report(targetId: String) {
            retained = retainAndRouteCompanionTargetId(
                currentTargetId = retained,
                reportedTargetId = targetId,
                selectNotesTarget = { notesTarget = it },
                selectWalkthroughTarget = { walkthroughTarget = it },
            )
        }

        report("atlantis")
        report("")

        assertEquals("atlantis", retained)
        assertEquals("atlantis", notesTarget)
        assertEquals("atlantis", walkthroughTarget)
        assertTrue(isCompanionTargetAvailable(retained))
    }

    @Test fun laterValidTargetReplacesTheRetainedTargetForNotesAndWalkthrough() {
        var retained = ""
        val notesTargets = mutableListOf<String>()
        val walkthroughTargets = mutableListOf<String>()
        fun report(targetId: String) {
            retained = retainAndRouteCompanionTargetId(
                currentTargetId = retained,
                reportedTargetId = targetId,
                selectNotesTarget = notesTargets::add,
                selectWalkthroughTarget = walkthroughTargets::add,
            )
        }

        report("game-a")
        report("game-b")

        assertEquals("game-b", retained)
        assertEquals(listOf("game-a", "game-b"), notesTargets)
        assertEquals(notesTargets, walkthroughTargets)
    }

    @Test fun notesAndWalkthroughReceiveIdenticalCanonicalTarget() {
        var notesTarget = ""
        var walkthroughTarget = ""
        routeCompanionTargetId(
            targetId = "atlantis",
            selectNotesTarget = { notesTarget = it },
            selectWalkthroughTarget = { walkthroughTarget = it },
        )
        assertEquals("atlantis", notesTarget)
        assertEquals(notesTarget, walkthroughTarget)
    }

    @Test fun freshNoTargetStateRemainsUnavailableAndIsNotRouted() {
        val routed = mutableListOf<String>()
        val canonical = canonicalCompanionTargetId("", "")
        routeCompanionTargetId(canonical, routed::add, routed::add)

        assertTrue(canonical.isBlank())
        assertTrue(routed.isEmpty())
    }
}
