package com.jamesmoran.adventurepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalGameReturnHandoffTest {
    @Test
    fun confirmedExternalGameExitRequiresDelayAndFreshBlankGeometry() {
        val machine = armedConnectedMachine("atlantis")
        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved("atlantis"))

        val schedule = machine.onTargetObserved("") as ExternalGameReturnAction.ScheduleConfirmation
        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved(""))
        assertEquals(
            ExternalGameReturnAction.QueryFreshGeometry(schedule.token),
            machine.onConfirmationDelayElapsed(schedule.token),
        )
        assertEquals(
            ExternalGameReturnAction.BackgroundLowerDisplay(schedule.token),
            machine.onTargetObserved(""),
        )

        machine.onHandoffResult(schedule.token, succeeded = true)
        assertNull(machine.currentExpectedTargetId())
    }

    @Test
    fun transientBlankIsCancelledWhenExpectedTargetReturns() {
        val machine = armedConnectedMachine("atlantis")
        machine.onTargetObserved("atlantis")
        val schedule = machine.onTargetObserved("") as ExternalGameReturnAction.ScheduleConfirmation

        assertEquals(
            ExternalGameReturnAction.CancelPendingConfirmation,
            machine.onTargetObserved("atlantis"),
        )
        assertEquals(
            ExternalGameReturnAction.None,
            machine.onConfirmationDelayElapsed(schedule.token),
        )
    }

    @Test
    fun connectionLossCancelsPendingAndNewEpochMustObserveTargetAgain() {
        val machine = armedConnectedMachine("atlantis")
        machine.onTargetObserved("atlantis")
        val oldSchedule = machine.onTargetObserved("") as ExternalGameReturnAction.ScheduleConfirmation

        assertEquals(
            ExternalGameReturnAction.CancelPendingConfirmation,
            machine.onConnectionChanged(false),
        )
        assertEquals(ExternalGameReturnAction.None, machine.onConfirmationDelayElapsed(oldSchedule.token))
        assertEquals(ExternalGameReturnAction.None, machine.onConnectionChanged(true))
        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved(""))

        machine.onTargetObserved("atlantis")
        assertTrue(machine.onTargetObserved("") is ExternalGameReturnAction.ScheduleConfirmation)
    }

    @Test
    fun staleCallbackCannotAffectReplacementSession() {
        val machine = armedConnectedMachine("atlantis")
        machine.onTargetObserved("atlantis")
        val oldSchedule = machine.onTargetObserved("") as ExternalGameReturnAction.ScheduleConfirmation

        assertEquals(
            ExternalGameReturnAction.CancelPendingConfirmation,
            machine.replaceSession("monkey-1"),
        )
        assertEquals(ExternalGameReturnAction.None, machine.onConfirmationDelayElapsed(oldSchedule.token))
        assertEquals("monkey-1", machine.currentExpectedTargetId())
    }

    @Test
    fun wrongOrInitialBlankTargetCannotStartExitConfirmation() {
        val machine = armedConnectedMachine("atlantis")

        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved(""))
        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved("monkey-1"))
        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved(""))
    }

    @Test
    fun directLaunchWithoutExternalSessionNeverHandsOff() {
        val machine = ExternalGameReturnHandoffStateMachine()
        machine.onConnectionChanged(true)

        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved("atlantis"))
        assertEquals(ExternalGameReturnAction.None, machine.onTargetObserved(""))
    }

    @Test
    fun repeatedExternalSessionsCanEachComplete() {
        val machine = armedConnectedMachine("atlantis")
        completeSession(machine, "atlantis")
        assertNull(machine.currentExpectedTargetId())

        machine.replaceSession("monkey-1")
        completeSession(machine, "monkey-1")
        assertNull(machine.currentExpectedTargetId())
    }

    @Test
    fun failedHandoffKeepsSessionArmedForRetry() {
        val machine = armedConnectedMachine("atlantis")
        machine.onTargetObserved("atlantis")
        val schedule = machine.onTargetObserved("") as ExternalGameReturnAction.ScheduleConfirmation
        machine.onConfirmationDelayElapsed(schedule.token)
        machine.onTargetObserved("")

        machine.onHandoffResult(schedule.token, succeeded = false)

        assertEquals("atlantis", machine.currentExpectedTargetId())
        assertTrue(machine.onTargetObserved("") is ExternalGameReturnAction.ScheduleConfirmation)
    }

    private fun armedConnectedMachine(targetId: String) = ExternalGameReturnHandoffStateMachine().apply {
        replaceSession(targetId)
        onConnectionChanged(true)
    }

    private fun completeSession(machine: ExternalGameReturnHandoffStateMachine, targetId: String) {
        machine.onTargetObserved(targetId)
        val schedule = machine.onTargetObserved("") as ExternalGameReturnAction.ScheduleConfirmation
        machine.onConfirmationDelayElapsed(schedule.token)
        machine.onTargetObserved("")
        machine.onHandoffResult(schedule.token, succeeded = true)
    }
}
