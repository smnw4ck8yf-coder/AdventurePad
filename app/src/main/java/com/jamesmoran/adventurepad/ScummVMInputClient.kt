package com.jamesmoran.adventurepad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/** Explicit, lifecycle-bound Messenger connection to the custom ScummVM debug app. */
internal object ScummVMInputClient {
    private const val TAG = "AdventurePadIPC"
    private const val SCUMMVM_PACKAGE = "org.scummvm.scummvm.debug"
    private const val SCUMMVM_SERVICE = "org.scummvm.scummvm.RelativeInputService"
    private const val MSG_RELATIVE_MOVE = 1
    private const val MSG_JOYSTICK_AXIS = 6
    private const val MSG_GAMEPAD_KEY = 7
    private const val JOYSTICK_AXIS_MAX = 32767
    private const val ANDROID_JOYSTICK_DEAD_ZONE = 0.209f
    private const val JOYSTICK_HAT_SCALE = 0.66f
    private const val BRIDGE_TAG = "AdventurePadBridge"

    private var applicationContext: Context? = null
    private var bindingRequested = false
    private var remoteMessenger: Messenger? = null
    private val lastJoystickPositions = mutableMapOf<JoystickAxisKey, Int>()
    private val loggedGamepadDevices = mutableSetOf<Int>()
    private val loggedJoystickAxes = mutableSetOf<JoystickAxisKey>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remoteMessenger = Messenger(service)
            Log.i(TAG, "Messenger connected to $name")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remoteMessenger = null
            clearGamepadState()
            Log.w(TAG, "Messenger disconnected from $name")
        }

        override fun onBindingDied(name: ComponentName) {
            remoteMessenger = null
            clearGamepadState()
            Log.e(TAG, "Messenger binding died for $name")
        }

        override fun onNullBinding(name: ComponentName) {
            remoteMessenger = null
            clearGamepadState()
            Log.e(TAG, "ScummVM returned a null Messenger binding for $name")
        }
    }

    @Synchronized
    fun bind(context: Context) {
        if (bindingRequested) return

        val appContext = context.applicationContext
        val intent = Intent().setComponent(ComponentName(SCUMMVM_PACKAGE, SCUMMVM_SERVICE))
        applicationContext = appContext
        bindingRequested = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE).also { bound ->
                if (bound) {
                    Log.i(TAG, "Messenger bind requested for ${intent.component}")
                } else {
                    Log.w(TAG, "Messenger bind was rejected for ${intent.component}")
                    applicationContext = null
                }
            }
        } catch (exception: RuntimeException) {
            applicationContext = null
            Log.w(TAG, "Messenger bind failed; standalone mode remains available", exception)
            false
        }
    }

    @Synchronized
    fun unbind() {
        val context = applicationContext
        if (bindingRequested && context != null) {
            try {
                context.unbindService(connection)
                Log.i(TAG, "Messenger unbound")
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Messenger unbind failed", exception)
            }
        }
        remoteMessenger = null
        clearGamepadState()
        bindingRequested = false
        applicationContext = null
    }

    fun sendRelativeDelta(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) {
            Log.w(TAG, "Rejected non-finite relative delta dx=$dx dy=$dy")
            return
        }

        val messenger = remoteMessenger ?: return
        val message = Message.obtain(null, MSG_RELATIVE_MOVE).apply {
            arg1 = java.lang.Float.floatToIntBits(dx)
            arg2 = java.lang.Float.floatToIntBits(dy)
        }
        try {
            messenger.send(message)
        } catch (exception: RemoteException) {
            remoteMessenger = null
            Log.w(TAG, "Messenger send failed; waiting for a future lifecycle rebind", exception)
        }
    }

    fun sendButtonEvent(event: ScummVMButtonEvent): Boolean {
        val messenger = remoteMessenger ?: return false
        val message = Message.obtain(null, event.messageWhat)
        return try {
            messenger.send(message)
            Log.i(BRIDGE_TAG, "Button event forwarded: ${event.name}")
            true
        } catch (exception: RemoteException) {
            remoteMessenger = null
            Log.w(TAG, "Button event forwarding failed", exception)
            false
        }
    }

    fun sendJoystickMotion(event: MotionEvent): Boolean {
        val messenger = remoteMessenger ?: return false
        val device = event.device ?: return false
        val mappings = joystickMappingsFor(device)
        if (mappings.isEmpty()) return false

        if (loggedGamepadDevices.add(event.deviceId)) {
            Log.i(
                BRIDGE_TAG,
                "Gamepad motion accepted: deviceId=${event.deviceId} name=${device.name} " +
                    "source=0x${event.source.toString(16)}",
            )
        }

        for (mapping in mappings) {
            val key = JoystickAxisKey(event.deviceId, mapping.scummVMBitFlag)
            val value = centeredAxisValue(event, device, mapping)
            val position = (value * JOYSTICK_AXIS_MAX).roundToInt()
            if (lastJoystickPositions[key] == position) continue

            val message = Message.obtain(null, MSG_JOYSTICK_AXIS).apply {
                arg1 = mapping.scummVMBitFlag
                arg2 = position
            }
            try {
                messenger.send(message)
                lastJoystickPositions[key] = position
                if (loggedJoystickAxes.add(key)) {
                    Log.i(
                        BRIDGE_TAG,
                        "Axis mapped: deviceId=${event.deviceId} " +
                            "${MotionEvent.axisToString(mapping.androidAxis)}=${event.getAxisValue(mapping.androidAxis)} " +
                            "-> JE_JOYSTICK flag=0x${mapping.scummVMBitFlag.toString(16)}",
                    )
                }
            } catch (exception: RemoteException) {
                remoteMessenger = null
                clearGamepadState()
                Log.w(TAG, "Joystick forwarding failed", exception)
                return false
            }
        }
        return true
    }

    fun releaseJoystickAxes() {
        val messenger = remoteMessenger
        if (messenger != null) {
            lastJoystickPositions.filterValues { it != 0 }.forEach { (key, _) ->
                val message = Message.obtain(null, MSG_JOYSTICK_AXIS).apply {
                    arg1 = key.scummVMBitFlag
                    arg2 = 0
                }
                try {
                    messenger.send(message)
                } catch (exception: RemoteException) {
                    remoteMessenger = null
                    Log.w(TAG, "Joystick release forwarding failed", exception)
                    return@forEach
                }
            }
        }
        lastJoystickPositions.clear()
    }

    fun sendGamepadKeyEvent(action: Int, keyCode: Int): Boolean {
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        if (!isForwardedGamepadKey(keyCode)) return false

        val messenger = remoteMessenger ?: return false
        val message = Message.obtain(null, MSG_GAMEPAD_KEY).apply {
            arg1 = action
            arg2 = keyCode
        }
        return try {
            messenger.send(message)
            Log.i(
                BRIDGE_TAG,
                "Gamepad key forwarded: ${KeyEvent.keyCodeToString(keyCode)} " +
                    if (action == KeyEvent.ACTION_DOWN) "DOWN" else "UP",
            )
            true
        } catch (exception: RemoteException) {
            remoteMessenger = null
            clearGamepadState()
            Log.w(TAG, "Gamepad key forwarding failed", exception)
            false
        }
    }

    fun isForwardedGamepadKey(keyCode: Int): Boolean = keyCode in ForwardedGamepadKeyCodes

    private fun joystickMappingsFor(device: InputDevice): List<JoystickAxisMapping> {
        val mappings = listOfNotNull(
            mappingIfPresent(device, MotionEvent.AXIS_X, 0x01),
            mappingIfPresent(device, MotionEvent.AXIS_Y, 0x02),
            mappingIfPresent(device, MotionEvent.AXIS_HAT_X, 0x04, JOYSTICK_HAT_SCALE),
            mappingIfPresent(device, MotionEvent.AXIS_HAT_Y, 0x08, JOYSTICK_HAT_SCALE),
            mappingIfPresent(device, MotionEvent.AXIS_Z, 0x10),
            mappingIfPresent(device, MotionEvent.AXIS_RZ, 0x20),
        ).toMutableList()

        // Android gamepads may expose triggers either as BRAKE/GAS or LTRIGGER/RTRIGGER.
        (mappingIfPresent(device, MotionEvent.AXIS_BRAKE, 0x40)
            ?: mappingIfPresent(device, MotionEvent.AXIS_LTRIGGER, 0x40))
            ?.let(mappings::add)
        (mappingIfPresent(device, MotionEvent.AXIS_GAS, 0x80)
            ?: mappingIfPresent(device, MotionEvent.AXIS_RTRIGGER, 0x80))
            ?.let(mappings::add)
        return mappings
    }

    private fun mappingIfPresent(
        device: InputDevice,
        androidAxis: Int,
        scummVMBitFlag: Int,
        scale: Float = 1f,
    ): JoystickAxisMapping? = device.getMotionRange(androidAxis, InputDevice.SOURCE_JOYSTICK)
        ?.let { JoystickAxisMapping(androidAxis, scummVMBitFlag, scale) }

    private fun centeredAxisValue(
        event: MotionEvent,
        device: InputDevice,
        mapping: JoystickAxisMapping,
    ): Float {
        val range = device.getMotionRange(mapping.androidAxis, InputDevice.SOURCE_JOYSTICK)
        val rawValue = event.getAxisValue(mapping.androidAxis)
        val deadZone = maxOf(range?.flat ?: 0f, ANDROID_JOYSTICK_DEAD_ZONE)
        val centeredValue = if (abs(rawValue) < deadZone) 0f else rawValue
        return (centeredValue * mapping.scale).coerceIn(-1f, 1f)
    }

    private fun clearGamepadState() {
        lastJoystickPositions.clear()
    }
}

private data class JoystickAxisKey(
    val deviceId: Int,
    val scummVMBitFlag: Int,
)

private data class JoystickAxisMapping(
    val androidAxis: Int,
    val scummVMBitFlag: Int,
    val scale: Float,
)

private val ForwardedGamepadKeyCodes = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_THUMBL,
    KeyEvent.KEYCODE_BUTTON_THUMBR,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    KeyEvent.KEYCODE_BUTTON_MODE,
)

internal enum class ScummVMButtonEvent(internal val messageWhat: Int) {
    LEFT_BUTTON_DOWN(2),
    LEFT_BUTTON_UP(3),
    RIGHT_BUTTON_DOWN(4),
    RIGHT_BUTTON_UP(5),
}

internal enum class ScummVMMouseButton(
    internal val downEvent: ScummVMButtonEvent,
    internal val upEvent: ScummVMButtonEvent,
) {
    LEFT(ScummVMButtonEvent.LEFT_BUTTON_DOWN, ScummVMButtonEvent.LEFT_BUTTON_UP),
    RIGHT(ScummVMButtonEvent.RIGHT_BUTTON_DOWN, ScummVMButtonEvent.RIGHT_BUTTON_UP),
}
