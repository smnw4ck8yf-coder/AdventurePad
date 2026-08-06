package com.jamesmoran.adventurepad

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme

class TrackpadActivity : ComponentActivity() {
    private var lifecycleEvent by mutableStateOf("INITIALIZING")
    private var lastLaunchResult by mutableStateOf("Waiting for launch details.")
    private var receivedIntentFlags by mutableStateOf(0)
    private var currentDisplayId by mutableStateOf(Display.INVALID_DISPLAY)
    private var mouseDiagnostics by mutableStateOf(MouseDiagnostics())
    private var gestureResetGeneration by mutableStateOf(0)
    private val mouseButtonSources = ScummVMMouseButton.entries.associateWith {
        mutableSetOf<MouseButtonSource>()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val forwardedGamepadKeysDown = mutableSetOf<Int>()
    private val tapLeftButtonRelease = Runnable {
        releaseMouseButton(ScummVMMouseButton.LEFT, MouseButtonSource.TRACKPAD_TAP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receivedIntentFlags = intent.flags
        currentDisplayId = display?.displayId ?: Display.INVALID_DISPLAY
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Launched because: $it" }
            ?: "TrackpadActivity created without a launch reason."
        recordLifecycle("CREATED")
        enableEdgeToEdge()

        setContent {
            AdventurePadTheme {
                TrackpadTouchTestScreen(
                    mouseDiagnostics = mouseDiagnostics,
                    gestureResetGeneration = gestureResetGeneration,
                    onGesture = ::handleTrackpadGesture,
                    onButtonDown = ::pressDedicatedButton,
                    onButtonUp = ::releaseDedicatedButton,
                    onRestoreBothScreens = ::restoreBothScreens,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ScummVMInputClient.bind(this)
        recordLifecycle("STARTED")
    }

    override fun onResume() {
        super.onResume()
        recordLifecycle("RESUMED")
    }

    override fun onPause() {
        releaseAllMouseButtons("PAUSE")
        recordLifecycle("PAUSED")
        super.onPause()
    }

    override fun onStop() {
        recordLifecycle("STOPPED")
        releaseForwardedGamepadKeys()
        ScummVMInputClient.releaseJoystickAxes()
        ScummVMInputClient.unbind()
        super.onStop()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            event.actionMasked == MotionEvent.ACTION_MOVE &&
            ScummVMInputClient.sendJoystickMotion(event)
        ) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (forwardControllerKeyEvent(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (forwardControllerKeyEvent(event)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun forwardControllerKeyEvent(event: KeyEvent): Boolean {
        val mouseButton = when {
            event.keyCode == KeyEvent.KEYCODE_BUTTON_A -> ScummVMMouseButton.LEFT
            event.keyCode == KeyEvent.KEYCODE_BUTTON_B -> ScummVMMouseButton.RIGHT
            else -> null
        }

        if (mouseButton != null && forwardMouseButton(event, mouseButton)) {
            return true
        }
        if (event.isFromGameController() && forwardGamepadKey(event)) {
            return true
        }
        return false
    }

    override fun onDestroy() {
        releaseAllMouseButtons("DESTROY")
        recordLifecycle("DESTROYED")
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) releaseAllMouseButtons("FOCUS LOSS")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivedIntentFlags = intent.flags
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Received launch request: $it" }
            ?: "Received a new intent without a launch reason."
        recordLifecycle("NEW_INTENT")
    }

    private fun restoreBothScreens() {
        releaseAllMouseButtons("RESTORE")
        lastLaunchResult = "Restore in progress…"
        lastLaunchResult = DualDisplayCoordinator.restoreBoth(this).message
    }

    private fun forwardMouseButton(event: KeyEvent, button: ScummVMMouseButton): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (isMouseButtonSourceDown(button, MouseButtonSource.CONTROLLER)) {
                    true
                } else if (event.repeatCount == 0) {
                    pressMouseButton(button, MouseButtonSource.CONTROLLER)
                } else {
                    false
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!isMouseButtonSourceDown(button, MouseButtonSource.CONTROLLER)) {
                    false
                } else {
                    releaseMouseButton(button, MouseButtonSource.CONTROLLER)
                    true
                }
            }
            else -> false
        }
    }

    private fun forwardGamepadKey(event: KeyEvent): Boolean {
        if (!ScummVMInputClient.isForwardedGamepadKey(event.keyCode)) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.keyCode in forwardedGamepadKeysDown) {
                    true
                } else if (event.repeatCount == 0 &&
                    ScummVMInputClient.sendGamepadKeyEvent(event.action, event.keyCode)
                ) {
                    forwardedGamepadKeysDown += event.keyCode
                    true
                } else {
                    false
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!forwardedGamepadKeysDown.remove(event.keyCode)) {
                    false
                } else {
                    ScummVMInputClient.sendGamepadKeyEvent(event.action, event.keyCode)
                    true
                }
            }
            else -> false
        }
    }

    private fun releaseForwardedGamepadKeys() {
        forwardedGamepadKeysDown.forEach { keyCode ->
            ScummVMInputClient.sendGamepadKeyEvent(KeyEvent.ACTION_UP, keyCode)
        }
        forwardedGamepadKeysDown.clear()
    }

    private fun pressDedicatedButton(button: ScummVMMouseButton) {
        pressMouseButton(button, MouseButtonSource.DEDICATED_BUTTON)
    }

    private fun releaseDedicatedButton(button: ScummVMMouseButton) {
        releaseMouseButton(button, MouseButtonSource.DEDICATED_BUTTON)
    }

    private fun sendTapLeftClick() {
        val activeLeftSources = mouseButtonSources.getValue(ScummVMMouseButton.LEFT)
        if (activeLeftSources.any { it != MouseButtonSource.TRACKPAD_TAP }) return

        mainHandler.removeCallbacks(tapLeftButtonRelease)
        releaseMouseButton(ScummVMMouseButton.LEFT, MouseButtonSource.TRACKPAD_TAP)
        if (pressMouseButton(ScummVMMouseButton.LEFT, MouseButtonSource.TRACKPAD_TAP)) {
            mainHandler.postDelayed(tapLeftButtonRelease, TapClickDurationMillis)
        }
    }

    private fun handleTrackpadGesture(gesture: TrackpadGesture) {
        mouseDiagnostics = mouseDiagnostics.copy(lastGesture = gesture.label)
        when (gesture) {
            TrackpadGesture.SINGLE_TAP -> sendTapLeftClick()
            TrackpadGesture.TWO_FINGER_RIGHT_CLICK -> sendTwoFingerRightClick()
            TrackpadGesture.CANCELLED -> Unit
        }
    }

    private fun sendTwoFingerRightClick() {
        val button = ScummVMMouseButton.RIGHT
        val source = MouseButtonSource.TRACKPAD_TWO_FINGER_TAP
        if (pressMouseButton(button, source)) {
            releaseMouseButton(button, source)
        }
    }

    private fun pressMouseButton(
        button: ScummVMMouseButton,
        source: MouseButtonSource,
    ): Boolean {
        val sources = mouseButtonSources.getValue(button)
        if (!sources.add(source)) return true
        if (sources.size > 1) return true

        if (!ScummVMInputClient.sendButtonEvent(button.downEvent)) {
            sources.remove(source)
            return false
        }
        updateMouseDiagnostics(button, isDown = true)
        return true
    }

    private fun releaseMouseButton(
        button: ScummVMMouseButton,
        source: MouseButtonSource,
    ): Boolean {
        val sources = mouseButtonSources.getValue(button)
        if (!sources.remove(source)) return false
        if (sources.isNotEmpty()) return true

        ScummVMInputClient.sendButtonEvent(button.upEvent)
        updateMouseDiagnostics(button, isDown = false)
        return true
    }

    private fun releaseAllMouseButtons(reason: String) {
        gestureResetGeneration++
        mouseDiagnostics = mouseDiagnostics.copy(lastGesture = TrackpadGesture.CANCELLED.label)
        mainHandler.removeCallbacks(tapLeftButtonRelease)
        ScummVMMouseButton.entries.forEach { button ->
            val sources = mouseButtonSources.getValue(button)
            if (sources.isNotEmpty()) {
                sources.clear()
                ScummVMInputClient.sendButtonEvent(button.upEvent)
                updateMouseDiagnostics(button, isDown = false, reason = reason)
            }
        }
    }

    private fun isMouseButtonSourceDown(
        button: ScummVMMouseButton,
        source: MouseButtonSource,
    ): Boolean = source in mouseButtonSources.getValue(button)

    private fun updateMouseDiagnostics(
        button: ScummVMMouseButton,
        isDown: Boolean,
        reason: String? = null,
    ) {
        val state = if (isDown) "DOWN" else "UP"
        mouseDiagnostics = when (button) {
            ScummVMMouseButton.LEFT -> mouseDiagnostics.copy(
                leftButtonDown = isDown,
                lastButtonAction = "LEFT $state" + reason?.let { " ($it)" }.orEmpty(),
            )
            ScummVMMouseButton.RIGHT -> mouseDiagnostics.copy(
                rightButtonDown = isDown,
                lastButtonAction = "RIGHT $state" + reason?.let { " ($it)" }.orEmpty(),
            )
        }
    }

    private fun runtimeDiagnostics() = ActivityRuntimeDiagnostics(
        displayId = currentDisplayId,
        taskId = taskId,
        isTaskRoot = isTaskRoot,
        lifecycleEvent = lifecycleEvent,
        intentFlags = receivedIntentFlags,
        lastResult = lastLaunchResult,
    )

    private fun recordLifecycle(event: String) {
        currentDisplayId = display?.displayId ?: currentDisplayId
        lifecycleEvent = event
        Log.i(
            TAG,
            "TrackpadActivity $event displayId=$currentDisplayId " +
                "taskId=$taskId isTaskRoot=$isTaskRoot flags=${receivedIntentFlags.toHexFlags()}",
        )
    }

    private companion object {
        const val TAG = "AdventurePadLifecycle"
    }
}

@Composable
private fun TrackpadTouchTestScreen(
    mouseDiagnostics: MouseDiagnostics,
    gestureResetGeneration: Int,
    onGesture: (TrackpadGesture) -> Unit,
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
    onRestoreBothScreens: () -> Unit,
) {
    val touchState = remember { mutableStateOf(TouchState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackpadBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        CompactActivityHeader(
            onRestoreBothScreens = onRestoreBothScreens,
        )

        TouchSurface(
            touchState = touchState,
            gestureResetGeneration = gestureResetGeneration,
            onGesture = onGesture,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        )

        MouseButtonControls(
            diagnostics = mouseDiagnostics,
            onButtonDown = onButtonDown,
            onButtonUp = onButtonUp,
        )
        MouseDiagnosticsPanel(mouseDiagnostics)
    }
}

@Composable
private fun CompactActivityHeader(
    onRestoreBothScreens: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "TRACKPAD RELATIVE MOVEMENT TEST",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onRestoreBothScreens,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = "RESTORE BOTH SCREENS",
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TouchSurface(
    touchState: MutableState<TouchState>,
    gestureResetGeneration: Int,
    onGesture: (TrackpadGesture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by touchState
    val viewConfiguration = LocalViewConfiguration.current

    Box(
        modifier = modifier
            .background(TouchSurfaceBackground)
            .border(2.dp, TouchSurfaceBorder)
            .onSizeChanged { surfaceSize ->
                touchState.value = touchState.value.withSurfaceSize(
                    surfaceWidth = surfaceSize.width.toFloat(),
                    surfaceHeight = surfaceSize.height.toFloat(),
                )
            }
            .pointerInput(viewConfiguration.touchSlop, gestureResetGeneration) {
                val gestureTracker = TrackpadGestureTracker(
                    singleTapMaximumDurationMillis = ViewConfiguration.getTapTimeout().toLong(),
                    twoFingerTapMaximumDurationMillis = TwoFingerTapMaximumDurationMillis,
                    maximumDistance = viewConfiguration.touchSlop,
                )
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val gestureUpdate = gestureTracker.handle(event)
                        gestureUpdate.gesture?.let(onGesture)
                        val nextState = handlePointerEvent(
                            event = event,
                            previousState = touchState.value,
                            surfaceWidth = size.width.toFloat(),
                            surfaceHeight = size.height.toFloat(),
                            allowMovement = gestureUpdate.allowMovement,
                        )
                        touchState.value = nextState
                        event.changes.forEach(PointerInputChange::consume)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = MarkerRadius.toPx()
            val unconstrainedCenter = if (currentState.cursorInitialized) {
                Offset(currentState.cursorX, currentState.cursorY)
            } else {
                center
            }
            val markerCenter = Offset(
                x = unconstrainedCenter.x.coerceIn(
                    radius,
                    (size.width - radius).coerceAtLeast(radius),
                ),
                y = unconstrainedCenter.y.coerceIn(
                    radius,
                    (size.height - radius).coerceAtLeast(radius),
                ),
            )
            drawCircle(
                Color.White,
                radius = radius + MarkerOutlineWidth.toPx(),
                center = markerCenter,
            )
            drawCircle(MarkerColor, radius = radius, center = markerCenter)
        }
        Text(
            text = "1:1 relative movement",
            color = TouchSurfaceBorder,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
    }
}

private class TrackpadGestureTracker(
    private val singleTapMaximumDurationMillis: Long,
    private val twoFingerTapMaximumDurationMillis: Long,
    maximumDistance: Float,
) {
    private val maximumDistanceSquared = maximumDistance * maximumDistance
    private val initialPositions = mutableMapOf<PointerId, Offset>()
    private var downUptimeMillis = 0L
    private var state = GestureTrackingState.IDLE
    private var cancellationReported = false

    fun handle(event: PointerEvent): GestureUpdate {
        val pressedCount = event.changes.count { it.pressed }
        when (event.type) {
            PointerEventType.Press -> {
                if (state == GestureTrackingState.IDLE && pressedCount == 1) {
                    val pointer = event.changes.first { it.pressed }
                    initialPositions[pointer.id] = pointer.position
                    downUptimeMillis = pointer.uptimeMillis
                    state = GestureTrackingState.SINGLE_PENDING
                } else if (state == GestureTrackingState.SINGLE_PENDING && pressedCount == 2) {
                    event.changes.filter { it.pressed }.forEach { pointer ->
                        initialPositions.putIfAbsent(pointer.id, pointer.position)
                    }
                    state = GestureTrackingState.TWO_FINGER_PENDING
                } else {
                    return cancel()
                }
            }

            PointerEventType.Move -> {
                when (state) {
                    GestureTrackingState.SINGLE_PENDING -> {
                        val pointer = event.changes.singleOrNull { it.pressed }
                        val initialPosition = pointer?.let { initialPositions[it.id] }
                        if (pointer == null || initialPosition == null) return cancel()
                        if (pointer.distanceSquaredFrom(initialPosition) > maximumDistanceSquared) {
                            state = GestureTrackingState.SINGLE_MOVING
                            return GestureUpdate(
                                gesture = reportCancellationOnce(),
                                allowMovement = true,
                            )
                        }
                    }
                    GestureTrackingState.SINGLE_MOVING -> {
                        if (pressedCount == 1) return GestureUpdate(allowMovement = true)
                        return cancel()
                    }
                    GestureTrackingState.TWO_FINGER_PENDING -> {
                        if (pressedCount > 2 || hasPointerExceededTolerance(event)) return cancel()
                        val elapsed = event.changes.maxOfOrNull { it.uptimeMillis }
                            ?.minus(downUptimeMillis) ?: 0L
                        if (elapsed > twoFingerTapMaximumDurationMillis) return cancel()
                    }
                    GestureTrackingState.CANCELLED,
                    GestureTrackingState.IDLE,
                    -> Unit
                }
            }

            PointerEventType.Release -> {
                if (state == GestureTrackingState.TWO_FINGER_PENDING &&
                    (hasPointerExceededTolerance(event) || event.changes.any {
                        it.id !in initialPositions && (it.pressed || it.previousPressed)
                    })
                ) {
                    return cancel()
                }
                if (pressedCount == 0) {
                    val finalUptimeMillis = event.changes.maxOfOrNull { it.uptimeMillis }
                        ?: downUptimeMillis
                    val duration = finalUptimeMillis - downUptimeMillis
                    val gesture = when (state) {
                        GestureTrackingState.SINGLE_PENDING -> {
                            if (!hasPointerExceededTolerance(event) &&
                                duration in 0..singleTapMaximumDurationMillis
                            ) {
                                TrackpadGesture.SINGLE_TAP
                            } else {
                                TrackpadGesture.CANCELLED
                            }
                        }
                        GestureTrackingState.TWO_FINGER_PENDING -> {
                            if (initialPositions.size == 2 &&
                                duration in 0..twoFingerTapMaximumDurationMillis
                            ) {
                                TrackpadGesture.TWO_FINGER_RIGHT_CLICK
                            } else {
                                TrackpadGesture.CANCELLED
                            }
                        }
                        GestureTrackingState.SINGLE_MOVING -> null
                        GestureTrackingState.CANCELLED -> reportCancellationOnce()
                        GestureTrackingState.IDLE -> null
                    }
                    reset()
                    return GestureUpdate(gesture = gesture)
                }
            }

            PointerEventType.Unknown -> if (state != GestureTrackingState.IDLE) {
                val gesture = reportCancellationOnce()
                reset()
                return GestureUpdate(gesture = gesture)
            }
        }
        return GestureUpdate()
    }

    private fun hasPointerExceededTolerance(event: PointerEvent): Boolean =
        event.changes.any { pointer ->
            val initialPosition = initialPositions[pointer.id]
            initialPosition == null ||
                pointer.distanceSquaredFrom(initialPosition) > maximumDistanceSquared
        }

    private fun PointerInputChange.distanceSquaredFrom(position: Offset): Float =
        (this.position - position).getDistanceSquared()

    private fun cancel(): GestureUpdate {
        state = GestureTrackingState.CANCELLED
        return GestureUpdate(gesture = reportCancellationOnce())
    }

    private fun reportCancellationOnce(): TrackpadGesture? {
        if (cancellationReported) return null
        cancellationReported = true
        return TrackpadGesture.CANCELLED
    }

    private fun reset() {
        initialPositions.clear()
        downUptimeMillis = 0L
        state = GestureTrackingState.IDLE
        cancellationReported = false
    }
}

private data class GestureUpdate(
    val gesture: TrackpadGesture? = null,
    val allowMovement: Boolean = false,
)

private enum class GestureTrackingState {
    IDLE,
    SINGLE_PENDING,
    SINGLE_MOVING,
    TWO_FINGER_PENDING,
    CANCELLED,
}

private fun KeyEvent.isFromGameController(): Boolean =
    isFromSource(InputDevice.SOURCE_GAMEPAD) || isFromSource(InputDevice.SOURCE_JOYSTICK)

private fun handlePointerEvent(
    event: PointerEvent,
    previousState: TouchState,
    surfaceWidth: Float,
    surfaceHeight: Float,
    allowMovement: Boolean,
): TouchState {
    val state = previousState.withSurfaceSize(surfaceWidth, surfaceHeight)
    val activePointerCount = event.changes.count { it.pressed }
    val action = when (event.type) {
        PointerEventType.Press -> if (state.pointerCount == 0) {
            TouchAction.DOWN
        } else {
            TouchAction.POINTER_DOWN
        }
        PointerEventType.Move -> TouchAction.MOVE
        PointerEventType.Release -> if (activePointerCount == 0) {
            TouchAction.UP
        } else {
            TouchAction.POINTER_UP
        }
        PointerEventType.Unknown -> if (
            state.trackedPointerId != null &&
            event.changes.isNotEmpty() &&
            event.changes.none { it.pressed }
        ) {
            TouchAction.CANCEL
        } else {
            null
        }
        else -> null
    }

    return when (action) {
        TouchAction.DOWN -> {
            val initialPointer = event.changes.firstOrNull { it.pressed } ?: return state
            val position = initialPointer.position.constrainTo(surfaceWidth, surfaceHeight)

            // DOWN establishes only the finger baseline; it never relocates the persistent cursor.
            state.copy(
                fingerX = position.x,
                fingerY = position.y,
                deltaX = 0f,
                deltaY = 0f,
                pointerCount = activePointerCount,
                trackedPointerId = initialPointer.id,
                action = TouchAction.DOWN,
                moveEventCount = 0,
            )
        }

        TouchAction.MOVE -> {
            if (!allowMovement) {
                return state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                )
            }
            val trackedPointer = event.findPressedPointer(state.trackedPointerId)
            if (trackedPointer == null) {
                state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                )
            } else if (trackedPointer.id != state.trackedPointerId) {
                state.withResetBaseline(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            } else {
                val updatedState = state.withRelativeMovement(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    moveEventCount = (state.moveEventCount + 1)
                        .coerceAtMost(MaxMoveEventCount),
                )
                CursorDeltaCoordinator.publish(
                    dx = updatedState.deltaX,
                    dy = updatedState.deltaY,
                )
                updatedState
            }
        }

        TouchAction.POINTER_DOWN -> {
            val trackedPointer = event.findPointer(state.trackedPointerId)
            if (trackedPointer == null) {
                state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = activePointerCount,
                    action = TouchAction.POINTER_DOWN,
                )
            } else {
                // Additional fingers never replace the pointer selected at ACTION_DOWN.
                state.withResetBaseline(
                    pointer = trackedPointer,
                    pointerCount = activePointerCount,
                    action = TouchAction.POINTER_DOWN,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                )
            }
        }

        TouchAction.POINTER_UP -> {
            val liftedPointerId = event.changes
                .firstOrNull { it.previousPressed && !it.pressed }
                ?.id
            if (liftedPointerId == state.trackedPointerId) {
                val replacement = event.changes.firstOrNull { it.pressed }
                if (replacement == null) {
                    state.copy(
                        deltaX = 0f,
                        deltaY = 0f,
                        pointerCount = activePointerCount,
                        trackedPointerId = null,
                        action = TouchAction.POINTER_UP,
                    )
                } else {
                    state.withResetBaseline(
                        pointer = replacement,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                    )
                }
            } else {
                val trackedPointer = event.findPointer(state.trackedPointerId)
                if (trackedPointer == null) {
                    state.copy(
                        deltaX = 0f,
                        deltaY = 0f,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                    )
                } else {
                    state.withResetBaseline(
                        pointer = trackedPointer,
                        pointerCount = activePointerCount,
                        action = TouchAction.POINTER_UP,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                    )
                }
            }
        }

        TouchAction.UP -> {
            val finalPointer = event.findPointer(state.trackedPointerId)
            if (finalPointer == null) {
                state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = 0,
                    trackedPointerId = null,
                    action = TouchAction.UP,
                )
            } else {
                val position = finalPointer.position.constrainTo(surfaceWidth, surfaceHeight)
                // UP clears the active baseline but leaves the accumulated cursor untouched.
                state.copy(
                    fingerX = position.x,
                    fingerY = position.y,
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = 0,
                    trackedPointerId = null,
                    action = TouchAction.UP,
                )
            }
        }

        TouchAction.CANCEL -> state.copy(
            deltaX = 0f,
            deltaY = 0f,
            pointerCount = 0,
            trackedPointerId = null,
            action = TouchAction.CANCEL,
            moveEventCount = 0,
        )

        null -> state.copy(pointerCount = activePointerCount)
    }
}

private fun TouchState.withRelativeMovement(
    pointer: PointerInputChange,
    pointerCount: Int,
    action: TouchAction,
    surfaceWidth: Float,
    surfaceHeight: Float,
    moveEventCount: Int = this.moveEventCount,
): TouchState {
    val position = pointer.position.constrainTo(surfaceWidth, surfaceHeight)
    val perEventDeltaX = position.x - fingerX
    val perEventDeltaY = position.y - fingerY
    return copy(
        fingerX = position.x,
        fingerY = position.y,
        deltaX = perEventDeltaX,
        deltaY = perEventDeltaY,
        // Finger coordinates are absolute; cursor coordinates accumulate only 1:1 MOVE deltas.
        cursorX = (cursorX + perEventDeltaX).coerceIn(0f, surfaceWidth.coerceAtLeast(0f)),
        cursorY = (cursorY + perEventDeltaY).coerceIn(0f, surfaceHeight.coerceAtLeast(0f)),
        pointerCount = pointerCount,
        trackedPointerId = pointer.id,
        action = action,
        moveEventCount = moveEventCount,
    )
}

private fun TouchState.withResetBaseline(
    pointer: PointerInputChange,
    pointerCount: Int,
    action: TouchAction,
    surfaceWidth: Float,
    surfaceHeight: Float,
): TouchState {
    val position = pointer.position.constrainTo(surfaceWidth, surfaceHeight)
    // Resetting the baseline on new gestures and pointer handoff prevents cursor jumps.
    return copy(
        fingerX = position.x,
        fingerY = position.y,
        deltaX = 0f,
        deltaY = 0f,
        pointerCount = pointerCount,
        trackedPointerId = pointer.id,
        action = action,
    )
}

private fun TouchState.withSurfaceSize(
    surfaceWidth: Float,
    surfaceHeight: Float,
): TouchState {
    val boundedWidth = surfaceWidth.coerceAtLeast(0f)
    val boundedHeight = surfaceHeight.coerceAtLeast(0f)
    return if (!cursorInitialized && boundedWidth > 0f && boundedHeight > 0f) {
        copy(
            cursorX = boundedWidth / 2f,
            cursorY = boundedHeight / 2f,
            cursorInitialized = true,
        )
    } else if (cursorInitialized) {
        copy(
            cursorX = cursorX.coerceIn(0f, boundedWidth),
            cursorY = cursorY.coerceIn(0f, boundedHeight),
        )
    } else {
        this
    }
}

private fun PointerEvent.findPointer(pointerId: PointerId?): PointerInputChange? =
    pointerId?.let { id -> changes.firstOrNull { it.id == id } }

private fun PointerEvent.findPressedPointer(pointerId: PointerId?): PointerInputChange? =
    findPointer(pointerId)?.takeIf { it.pressed } ?: changes.firstOrNull { it.pressed }

private fun Offset.constrainTo(surfaceWidth: Float, surfaceHeight: Float) = Offset(
    x = x.coerceIn(0f, surfaceWidth.coerceAtLeast(0f)),
    y = y.coerceIn(0f, surfaceHeight.coerceAtLeast(0f)),
)

@Composable
private fun MouseButtonControls(
    diagnostics: MouseDiagnostics,
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MouseButtonControl(
            label = "LEFT",
            button = ScummVMMouseButton.LEFT,
            isDown = diagnostics.leftButtonDown,
            onButtonDown = onButtonDown,
            onButtonUp = onButtonUp,
            modifier = Modifier.weight(1f),
        )
        MouseButtonControl(
            label = "RIGHT",
            button = ScummVMMouseButton.RIGHT,
            isDown = diagnostics.rightButtonDown,
            onButtonDown = onButtonDown,
            onButtonUp = onButtonUp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MouseButtonControl(
    label: String,
    button: ScummVMMouseButton,
    isDown: Boolean,
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (isDown) MarkerColor else TouchSurfaceBackground)
            .border(2.dp, TouchSurfaceBorder)
            .pointerInput(button) {
                var pointerDown = false
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.any { it.pressed }
                            if (pressed && !pointerDown) {
                                pointerDown = true
                                onButtonDown(button)
                            } else if (!pressed && pointerDown) {
                                pointerDown = false
                                onButtonUp(button)
                            }
                            event.changes.forEach(PointerInputChange::consume)
                        }
                    }
                } finally {
                    if (pointerDown) onButtonUp(button)
                }
            }
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MouseDiagnosticsPanel(diagnostics: MouseDiagnostics) {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            "Left button: ${if (diagnostics.leftButtonDown) "DOWN" else "UP"}",
            "Right button: ${if (diagnostics.rightButtonDown) "DOWN" else "UP"}",
            "Drag: ${if (diagnostics.leftButtonDown) "ACTIVE" else "INACTIVE"}",
            "Last button action: ${diagnostics.lastButtonAction}",
            "Last gesture: ${diagnostics.lastGesture}",
        ).forEach { value ->
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
        }
    }
}

private data class MouseDiagnostics(
    val leftButtonDown: Boolean = false,
    val rightButtonDown: Boolean = false,
    val lastButtonAction: String = "NONE",
    val lastGesture: String = TrackpadGesture.NONE_LABEL,
)

private enum class MouseButtonSource {
    DEDICATED_BUTTON,
    CONTROLLER,
    TRACKPAD_TAP,
    TRACKPAD_TWO_FINGER_TAP,
}

private const val TapClickDurationMillis = 50L
private const val TwoFingerTapMaximumDurationMillis = 250L

private enum class TrackpadGesture(val label: String) {
    SINGLE_TAP("SINGLE TAP"),
    TWO_FINGER_RIGHT_CLICK("TWO-FINGER RIGHT CLICK"),
    CANCELLED("CANCELLED");

    companion object {
        const val NONE_LABEL = "NONE"
    }
}

private data class TouchState(
    val fingerX: Float = 0f,
    val fingerY: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val cursorX: Float = 0f,
    val cursorY: Float = 0f,
    val cursorInitialized: Boolean = false,
    val pointerCount: Int = 0,
    val trackedPointerId: PointerId? = null,
    val action: TouchAction = TouchAction.CANCEL,
    val moveEventCount: Int = 0,
)

private enum class TouchAction(val label: String) {
    DOWN("DOWN"),
    MOVE("MOVE"),
    UP("UP"),
    CANCEL("CANCEL"),
    POINTER_DOWN("POINTER_DOWN"),
    POINTER_UP("POINTER_UP"),
}

private val TrackpadBackground = Color(0xFF6B1D2A)
private val TouchSurfaceBackground = Color(0xFF2D0B13)
private val TouchSurfaceBorder = Color(0xFFFFB3C1)
private val MarkerColor = Color(0xFF00E5FF)
private val MarkerRadius = 16.dp
private val MarkerOutlineWidth = 3.dp
private const val MaxMoveEventCount = 999_999
private const val AdventurePadBridgeTag = "AdventurePadBridge"
