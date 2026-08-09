package com.jamesmoran.adventurepad

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Converts the existing lower-display pointer stream into genuine Android mouse events for the
 * Compose launcher. ScummVM remains the fallback sink whenever the launcher is not resumed.
 */
internal class LauncherPointerInput(private val activity: Activity) {
    private var cursor = LauncherCursorState()
    private var active = false
    private var buttonState = 0
    private var downTimeMillis = 0L
    private val directKeysDown = mutableSetOf<Int>()
    private val joystickAxisPositions = mutableMapOf<Int, Int>()
    private var joystickKeysDown = emptySet<Int>()

    fun activate() {
        if (active) return
        active = true
        // Do not leave an axis held in the now-background ScummVM owner.
        ScummVMInputClient.releaseJoystickAxes()
        CursorDeltaCoordinator.setLauncherEventSink(::handle)
        dispatchPointerMotion(MotionEvent.ACTION_HOVER_MOVE)
    }

    fun deactivate() {
        if (!active) return
        if (buttonState != 0) {
            dispatchPointerMotion(MotionEvent.ACTION_CANCEL)
            buttonState = 0
        }
        releaseNavigationKeys()
        CursorDeltaCoordinator.setLauncherEventSink(null)
        active = false
    }

    fun updateCursor(state: LauncherCursorState, moved: Boolean) {
        cursor = state
        if (!active || !moved || !state.initialized) return
        dispatchPointerMotion(
            launcherPointerMotionAction(buttonState),
        )
    }

    private fun handle(event: LauncherPointerEvent): Boolean {
        if (!active) return false
        return when (event) {
            is LauncherPointerEvent.Button -> cursor.initialized && dispatchButton(event.event)
            is LauncherPointerEvent.VerticalScroll -> cursor.initialized && dispatchScroll(event.distance)
            is LauncherPointerEvent.Key -> dispatchKey(event.action, event.keyCode)
            is LauncherPointerEvent.JoystickAxis -> dispatchJoystickAxis(event.value)
            LauncherPointerEvent.ReleaseJoystick -> {
                updateJoystickKeys(emptySet())
                joystickAxisPositions.clear()
                true
            }
        }
    }

    private fun dispatchKey(action: Int, keyCode: Int): Boolean {
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        val wasEffectivelyDown = keyCode in directKeysDown || keyCode in joystickKeysDown
        if (action == KeyEvent.ACTION_DOWN) directKeysDown += keyCode else directKeysDown -= keyCode
        val isEffectivelyDown = keyCode in directKeysDown || keyCode in joystickKeysDown
        return if (wasEffectivelyDown == isEffectivelyDown) true else dispatchAndroidKey(
            if (isEffectivelyDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            keyCode,
        )
    }

    private fun dispatchJoystickAxis(value: TriggerAxisValue): Boolean {
        if (value.axisFlag !in LauncherNavigationAxisFlags) return true
        joystickAxisPositions[value.axisFlag] = value.position
        val desired = buildSet {
            addAxisDirection(
                joystickAxisPositions[JoystickXAxis].orZero() +
                    joystickAxisPositions[JoystickHatXAxis].orZero(),
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
            )
            addAxisDirection(
                joystickAxisPositions[JoystickYAxis].orZero() +
                    joystickAxisPositions[JoystickHatYAxis].orZero(),
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
            )
        }
        updateJoystickKeys(desired)
        return true
    }

    private fun MutableSet<Int>.addAxisDirection(position: Int, negative: Int, positive: Int) {
        when {
            position <= -JoystickNavigationThreshold -> add(negative)
            position >= JoystickNavigationThreshold -> add(positive)
        }
    }

    private fun updateJoystickKeys(desired: Set<Int>) {
        val previous = joystickKeysDown
        joystickKeysDown = desired
        (previous - desired).forEach { keyCode ->
            if (keyCode !in directKeysDown) dispatchAndroidKey(KeyEvent.ACTION_UP, keyCode)
        }
        (desired - previous).forEach { keyCode ->
            if (keyCode !in directKeysDown) dispatchAndroidKey(KeyEvent.ACTION_DOWN, keyCode)
        }
    }

    private fun releaseNavigationKeys() {
        val keys = directKeysDown + joystickKeysDown
        directKeysDown.clear()
        joystickKeysDown = emptySet()
        joystickAxisPositions.clear()
        keys.forEach { dispatchAndroidKey(KeyEvent.ACTION_UP, it) }
    }

    private fun dispatchAndroidKey(action: Int, keyCode: Int): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        return activity.dispatchKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                action,
                keyCode,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_GAMEPAD,
            ),
        )
    }

    private fun dispatchButton(event: ScummVMButtonEvent): Boolean {
        val button = when (event) {
            ScummVMButtonEvent.LEFT_BUTTON_DOWN,
            ScummVMButtonEvent.LEFT_BUTTON_UP,
            -> MotionEvent.BUTTON_PRIMARY
            ScummVMButtonEvent.RIGHT_BUTTON_DOWN,
            ScummVMButtonEvent.RIGHT_BUTTON_UP,
            -> MotionEvent.BUTTON_SECONDARY
        }
        val pressed = event == ScummVMButtonEvent.LEFT_BUTTON_DOWN ||
            event == ScummVMButtonEvent.RIGHT_BUTTON_DOWN
        if (pressed == ((buttonState and button) != 0)) return true

        val eventTime = SystemClock.uptimeMillis()
        if (pressed) {
            if (buttonState == 0) downTimeMillis = eventTime
            buttonState = buttonState or button
        } else {
            buttonState = buttonState and button.inv()
        }
        val action = if (pressed) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP
        val handled = obtainMouseEvent(action, eventTime).useEvent {
            activity.dispatchTouchEvent(it)
        }
        if (!pressed && buttonState == 0) downTimeMillis = 0L
        return handled
    }

    private fun dispatchScroll(distance: Float): Boolean {
        if (!distance.isFinite() || distance == 0f) return false
        val eventTime = SystemClock.uptimeMillis()
        return obtainMouseEvent(
            action = MotionEvent.ACTION_SCROLL,
            eventTime = eventTime,
            verticalScroll = distance / LauncherScrollPixelsPerAxisUnit,
        ).useEvent { activity.dispatchGenericMotionEvent(it) }
    }

    private fun dispatchPointerMotion(action: Int): Boolean {
        if (!cursor.initialized) return false
        val eventTime = SystemClock.uptimeMillis()
        return obtainMouseEvent(action, eventTime).useEvent {
            if (action == MotionEvent.ACTION_HOVER_MOVE) {
                activity.dispatchGenericMotionEvent(it)
            } else {
                activity.dispatchTouchEvent(it)
            }
        }
    }

    private fun obtainMouseEvent(
        action: Int,
        eventTime: Long,
        verticalScroll: Float = 0f,
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = cursor.x
            y = cursor.y
            pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) 0f else 1f
            size = 1f
            if (verticalScroll != 0f) setAxisValue(MotionEvent.AXIS_VSCROLL, verticalScroll)
        })
        return MotionEvent.obtain(
            if (downTimeMillis == 0L) eventTime else downTimeMillis,
            eventTime,
            action,
            1,
            properties,
            coordinates,
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
    }

    private inline fun <T> MotionEvent.useEvent(block: (MotionEvent) -> T): T =
        try {
            block(this)
        } finally {
            recycle()
        }

    private companion object {
        const val LauncherScrollPixelsPerAxisUnit = 48f
        const val JoystickXAxis = 0x01
        const val JoystickYAxis = 0x02
        const val JoystickHatXAxis = 0x04
        const val JoystickHatYAxis = 0x08
        const val JoystickNavigationThreshold = 16_384
        val LauncherNavigationAxisFlags = setOf(
            JoystickXAxis,
            JoystickYAxis,
            JoystickHatXAxis,
            JoystickHatYAxis,
        )
    }
}

internal fun launcherPointerMotionAction(buttonState: Int): Int =
    if (buttonState == 0) MotionEvent.ACTION_HOVER_MOVE else MotionEvent.ACTION_MOVE

private fun Int?.orZero(): Int = this ?: 0
