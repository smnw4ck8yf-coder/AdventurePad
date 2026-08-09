package com.jamesmoran.adventurepad

import java.util.concurrent.CopyOnWriteArraySet

internal data class CursorDelta(
    val dx: Float,
    val dy: Float,
)

internal fun interface CursorDeltaSubscription {
    fun cancel()
}

internal sealed interface LauncherPointerEvent {
    data class Button(val event: ScummVMButtonEvent) : LauncherPointerEvent
    data class VerticalScroll(val distance: Float) : LauncherPointerEvent
    data class Key(val action: Int, val keyCode: Int) : LauncherPointerEvent
    data class JoystickAxis(val value: TriggerAxisValue) : LauncherPointerEvent
    data object ReleaseJoystick : LauncherPointerEvent
}

/** Bounded, process-local communication between AdventurePad's two activities. */
internal object CursorDeltaCoordinator {
    private val subscribers = CopyOnWriteArraySet<(CursorDelta) -> Unit>()
    @Volatile
    private var launcherEventSink: ((LauncherPointerEvent) -> Boolean)? = null

    fun publish(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        val update = CursorDelta(dx = dx, dy = dy)
        if (launcherEventSink == null) ScummVMInputClient.sendRelativeDelta(dx, dy)
        subscribers.forEach { subscriber -> subscriber(update) }
    }

    fun publishButton(event: ScummVMButtonEvent): Boolean =
        launcherEventSink?.invoke(LauncherPointerEvent.Button(event))
            ?: ScummVMInputClient.sendButtonEvent(event)

    fun publishVerticalScroll(distance: Float): Boolean {
        if (!distance.isFinite() || distance == 0f) return false
        return launcherEventSink?.invoke(LauncherPointerEvent.VerticalScroll(distance))
            ?: ScummVMInputClient.sendVerticalScroll(distance)
    }

    fun publishGamepadKey(action: Int, keyCode: Int): Boolean =
        launcherEventSink?.invoke(LauncherPointerEvent.Key(action, keyCode))
            ?: ScummVMInputClient.sendGamepadKeyEvent(action, keyCode)

    fun publishJoystickAxis(value: TriggerAxisValue): Boolean =
        launcherEventSink?.invoke(LauncherPointerEvent.JoystickAxis(value))
            ?: ScummVMInputClient.sendJoystickAxisValueToScummVM(value)

    fun releaseJoystickAxes() {
        launcherEventSink?.invoke(LauncherPointerEvent.ReleaseJoystick)
            ?: ScummVMInputClient.releaseJoystickAxes()
    }

    fun setLauncherEventSink(sink: ((LauncherPointerEvent) -> Boolean)?) {
        launcherEventSink = sink
    }

    fun subscribe(subscriber: (CursorDelta) -> Unit): CursorDeltaSubscription {
        subscribers += subscriber
        return CursorDeltaSubscription { subscribers -= subscriber }
    }
}
