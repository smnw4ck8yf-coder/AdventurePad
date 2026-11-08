package com.jamesmoran.adventurepad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeGeometryResponseGateTest {
    @Test fun rejectsSupersededQueryAndOlderState() {
        val gate = BridgeGeometryResponseGate()
        gate.beginQuery(10)
        assertTrue(gate.accept(10, 4))
        assertFalse(gate.accept(9, 5))
        assertFalse(gate.accept(10, 3))
        assertTrue(gate.accept(10, 5))
    }

    @Test fun reconnectQueryAcceptsAuthoritativeSnapshotAndRejectsOldConnectionReply() {
        val gate = BridgeGeometryResponseGate()
        gate.beginQuery(10)
        assertTrue(gate.accept(10, 8))

        gate.invalidate()
        gate.beginQuery(20)
        assertFalse(gate.accept(10, 9))
        assertTrue(gate.accept(20, 1))
    }
}
