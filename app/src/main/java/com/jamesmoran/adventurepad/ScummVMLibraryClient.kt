package com.jamesmoran.adventurepad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log

internal data class ScummVMTarget(
    val targetId: String,
    val title: String,
    val engineId: String,
    val gameId: String,
    val artworkUri: String? = null,
)

internal data class ScummVMLibraryState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val targets: List<ScummVMTarget> = emptyList(),
    val error: String? = null,
)

internal fun normalizeScummVMTargets(targets: List<ScummVMTarget>): List<ScummVMTarget> = targets
    .asSequence()
    .filter { it.targetId.isNotBlank() && it.gameId.isNotBlank() }
    .distinctBy { it.targetId }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    .toList()

/** Read-only library and launch bridge to the signature-matched ScummVM build. */
internal class ScummVMLibraryClient(
    context: Context,
    private val onStateChanged: (ScummVMLibraryState) -> Unit,
) {
    private val applicationContext = context.applicationContext
    private var remote: Messenger? = null
    private var bound = false
    private var state = ScummVMLibraryState()

    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != MSG_GAME_LIBRARY) return
            val data = message.data
            data.classLoader = Bundle::class.java.classLoader
            val targets = data.getParcelableArrayList(KEY_TARGETS, Bundle::class.java).orEmpty().map { bundle ->
                ScummVMTarget(
                    targetId = bundle.getString(KEY_TARGET_ID).orEmpty(),
                    title = bundle.getString(KEY_TITLE).orEmpty()
                        .ifBlank { bundle.getString(KEY_TARGET_ID).orEmpty() },
                    engineId = bundle.getString(KEY_ENGINE_ID).orEmpty(),
                    gameId = bundle.getString(KEY_GAME_ID).orEmpty(),
                )
            }
            updateState(
                state.copy(
                    connected = true,
                    loading = false,
                    targets = normalizeScummVMTargets(targets),
                    error = data.getString(KEY_ERROR),
                ),
            )
        }
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remote = Messenger(service)
            updateState(state.copy(connected = true, loading = true, error = null))
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) = disconnect("ScummVM disconnected")
        override fun onBindingDied(name: ComponentName) = disconnect("ScummVM bridge stopped")
        override fun onNullBinding(name: ComponentName) = disconnect("ScummVM bridge unavailable")
    }

    fun connect() {
        if (bound) return
        updateState(state.copy(loading = true, error = null))
        val intent = Intent().setComponent(ComponentName(SCUMMVM_PACKAGE, SCUMMVM_SERVICE))
        bound = try {
            applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "ScummVM library bind failed", exception)
            false
        }
        if (!bound) disconnect("ScummVM is unavailable. Install the matching AdventurePad build.")
    }

    fun disconnect() {
        if (bound) {
            try {
                applicationContext.unbindService(connection)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "ScummVM library unbind failed", exception)
            }
        }
        bound = false
        remote = null
        updateState(state.copy(connected = false, loading = false))
    }

    fun refresh(): Boolean = send(MSG_QUERY_GAME_LIBRARY)

    fun launch(targetId: String): Boolean {
        if (targetId.isBlank()) return false
        return send(MSG_LAUNCH_GAME_TARGET, Bundle().apply { putString(KEY_TARGET_ID, targetId) })
    }

    fun openAdvancedScummVM(): Boolean = send(MSG_OPEN_SCUMMVM_LIBRARY)

    private fun send(what: Int, data: Bundle = Bundle.EMPTY): Boolean {
        val recipient = remote ?: return false
        val message = Message.obtain(null, what).apply {
            this.data = data
            replyTo = replyMessenger
        }
        return try {
            recipient.send(message)
            true
        } catch (exception: RemoteException) {
            Log.w(TAG, "ScummVM library command failed", exception)
            disconnect("ScummVM stopped responding")
            false
        }
    }

    private fun disconnect(error: String) {
        remote = null
        bound = false
        updateState(state.copy(connected = false, loading = false, error = error))
    }

    private fun updateState(updated: ScummVMLibraryState) {
        state = updated
        onStateChanged(updated)
    }

    private companion object {
        const val TAG = "AdventurePadLibrary"
        const val SCUMMVM_PACKAGE = "org.scummvm.scummvm.debug"
        const val SCUMMVM_SERVICE = "org.scummvm.scummvm.RelativeInputService"
        const val MSG_QUERY_GAME_LIBRARY = 200
        const val MSG_LAUNCH_GAME_TARGET = 201
        const val MSG_OPEN_SCUMMVM_LIBRARY = 202
        const val MSG_GAME_LIBRARY = 203
        const val KEY_TARGETS = "targets"
        const val KEY_TARGET_ID = "targetId"
        const val KEY_TITLE = "title"
        const val KEY_ENGINE_ID = "engineId"
        const val KEY_GAME_ID = "gameId"
        const val KEY_ERROR = "error"
    }
}
