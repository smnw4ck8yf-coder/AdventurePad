package com.jamesmoran.adventurepad

import androidx.compose.ui.input.pointer.PointerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PointerSpeedSettingsTest {
    @Test
    fun defaultSensitivityIsNormal() {
        assertSame(PointerSpeed.NORMAL, PointerSpeed.Default)
        assertEquals(1f, PointerSpeed.Default.multiplier)
        assertSame(PointerSpeed.Default, PointerSpeed.fromStoredValue(null))
    }

    @Test
    fun everySupportedSensitivityIsAccepted() {
        val expected = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

        assertEquals(expected, PointerSpeed.entries.map(PointerSpeed::multiplier))
        PointerSpeed.entries.forEach { speed ->
            assertSame(speed, PointerSpeed.fromStoredValue(speed.storedValue))
        }
    }

    @Test
    fun unsupportedOrCorruptedStoredValuesFallBackToNormal() {
        listOf("", "0", "0.7", "1", "NaN", "Infinity", "corrupted").forEach { value ->
            assertSame(PointerSpeed.Default, PointerSpeed.fromStoredValue(value))
        }
    }

    @Test
    fun positiveDeltasScaleAtEverySupportedSensitivity() {
        val expected = mapOf(
            PointerSpeed.HALF to 5f,
            PointerSpeed.THREE_QUARTERS to 7.5f,
            PointerSpeed.NORMAL to 10f,
            PointerSpeed.ONE_AND_QUARTER to 12.5f,
            PointerSpeed.ONE_AND_HALF to 15f,
            PointerSpeed.DOUBLE to 20f,
        )

        expected.forEach { (speed, scaledValue) ->
            val scaled = scaleRelativeDelta(10f, 10f, speed)
            assertEquals(scaledValue, scaled.dx)
            assertEquals(scaledValue, scaled.dy)
        }
    }

    @Test
    fun negativeDeltasScaleCorrectly() {
        val scaled = scaleRelativeDelta(-10f, -4f, PointerSpeed.ONE_AND_HALF)

        assertEquals(-15f, scaled.dx)
        assertEquals(-6f, scaled.dy)
    }

    @Test
    fun zeroDeltasRemainZero() {
        PointerSpeed.entries.forEach { speed ->
            assertEquals(RelativePointerDelta(0f, 0f), scaleRelativeDelta(0f, 0f, speed))
        }
    }

    @Test
    fun scalingDoesNotAlterGestureOrButtonRelatedTouchState() {
        val activeButtons = setOf(ScummVMMouseButton.LEFT)
        val gesture = TrackpadGesture.DOUBLE_TAP_HOLD_START
        val touchState = TouchState(
            deltaX = 8f,
            deltaY = -4f,
            pointerCount = 1,
            trackedPointerId = PointerId(7L),
            action = TouchAction.MOVE,
        )

        val scaled = scaleRelativeDelta(
            rawDx = touchState.deltaX,
            rawDy = touchState.deltaY,
            pointerSpeed = PointerSpeed.DOUBLE,
        )

        assertEquals(RelativePointerDelta(16f, -8f), scaled)
        assertEquals(1, touchState.pointerCount)
        assertEquals(PointerId(7L), touchState.trackedPointerId)
        assertEquals(TouchAction.MOVE, touchState.action)
        assertEquals(setOf(ScummVMMouseButton.LEFT), activeButtons)
        assertEquals(TrackpadGesture.DOUBLE_TAP_HOLD_START, gesture)
    }

    @Test
    fun selectedValueIsRestoredFromPersistentStore() = runBlocking {
        val store = FakePersistentPointerSpeedStore()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val firstRepository = PointerSpeedRepository(store, firstScope)
        firstRepository.setPointerSpeed(PointerSpeed.ONE_AND_QUARTER)
        firstScope.cancel()

        val restoredScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val restoredRepository = PointerSpeedRepository(store, restoredScope)

        assertSame(PointerSpeed.ONE_AND_QUARTER, restoredRepository.pointerSpeed.value)
        restoredScope.cancel()
    }
}

private class FakePersistentPointerSpeedStore : PointerSpeedStore {
    private val persistedValue = MutableStateFlow(PointerSpeed.Default)

    override val pointerSpeed: Flow<PointerSpeed> = persistedValue

    override suspend fun setPointerSpeed(pointerSpeed: PointerSpeed) {
        persistedValue.value = pointerSpeed
    }
}
