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
    val resumeSaveSlot: Int? = null,
    val resumeUnavailableReason: String? = null,
    val loadGameAvailable: Boolean = false,
) {
    val resumeGameAvailable: Boolean
        get() = resumeSaveSlot != null && resumeSaveSlot >= 0
}

internal data class ScummVMLibraryState(
    val connected: Boolean = false,
    val loading: Boolean = true,
    val targets: List<ScummVMTarget> = emptyList(),
    val saveCapabilitiesReady: Boolean = false,
    val error: String? = null,
)

internal data class ScummVMGameRemovalResult(
    val targetId: String,
    val removed: Boolean,
    val error: String? = null,
)

internal class SaveCapabilityRefreshRequestGate {
    private var requested = false

    fun requestOnce(sendRequest: () -> Boolean): Boolean {
        if (requested) return false
        requested = true
        if (sendRequest()) return true
        requested = false
        return false
    }

    fun reset() {
        requested = false
    }
}

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
    private val onGameRemovalResult: (ScummVMGameRemovalResult) -> Unit = {},
) {
    private val applicationContext = context.applicationContext
    private var remote: Messenger? = null
    private var bound = false
    private var state = ScummVMLibraryState()
    private val saveCapabilityRefreshGate = SaveCapabilityRefreshRequestGate()

    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == MSG_REMOVE_GAME_RESULT) {
                val data = message.data
                onGameRemovalResult(
                    ScummVMGameRemovalResult(
                        targetId = data.getString(KEY_TARGET_ID).orEmpty(),
                        removed = data.getBoolean(KEY_REMOVED, false),
                        error = data.getString(KEY_ERROR),
                    ),
                )
                return
            }
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
                    resumeSaveSlot = bundle.getInt(KEY_RESUME_SAVE_SLOT, -1).takeIf { it >= 0 },
                    resumeUnavailableReason = bundle.getString(KEY_RESUME_UNAVAILABLE_REASON),
                    loadGameAvailable = bundle.getBoolean(KEY_LOAD_GAME_AVAILABLE, false),
                )
            }
            updateState(
                state.copy(
                    connected = true,
                    loading = false,
                    targets = normalizeScummVMTargets(targets),
                    saveCapabilitiesReady = data.getBoolean(KEY_SAVE_CAPABILITIES_READY, false),
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
            requestSaveCapabilityRefresh()
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
        saveCapabilityRefreshGate.reset()
        updateState(state.copy(connected = false, loading = false))
    }

    fun refresh(): Boolean = send(MSG_QUERY_GAME_LIBRARY)

    private fun requestSaveCapabilityRefresh(): Boolean =
        saveCapabilityRefreshGate.requestOnce { send(MSG_REFRESH_SAVE_CAPABILITIES) }

    fun launch(targetId: String): Boolean {
        if (targetId.isBlank()) return false
        return send(MSG_LAUNCH_GAME_TARGET, Bundle().apply { putString(KEY_TARGET_ID, targetId) })
    }

    fun resume(targetId: String, saveSlot: Int): Boolean {
        if (targetId.isBlank() || saveSlot < 0) return false
        return send(MSG_RESUME_GAME_TARGET, Bundle().apply {
            putString(KEY_TARGET_ID, targetId)
            putInt(KEY_RESUME_SAVE_SLOT, saveSlot)
        })
    }

    fun openLoadGame(targetId: String): Boolean {
        if (targetId.isBlank()) return false
        return send(MSG_LOAD_GAME_TARGET, Bundle().apply { putString(KEY_TARGET_ID, targetId) })
    }

    fun openAdvancedScummVM(): Boolean = send(MSG_OPEN_SCUMMVM_LIBRARY)

    fun addGame(): Boolean = send(MSG_ADD_GAME)

    fun removeGame(targetId: String): Boolean {
        if (targetId.isBlank()) return false
        return send(MSG_REMOVE_GAME_TARGET, Bundle().apply { putString(KEY_TARGET_ID, targetId) })
    }

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
        saveCapabilityRefreshGate.reset()
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
        const val MSG_RESUME_GAME_TARGET = 204
        const val MSG_LOAD_GAME_TARGET = 205
        const val MSG_REFRESH_SAVE_CAPABILITIES = 206
        const val MSG_ADD_GAME = 207
        const val MSG_REMOVE_GAME_TARGET = 208
        const val MSG_REMOVE_GAME_RESULT = 209
        const val KEY_TARGETS = "targets"
        const val KEY_TARGET_ID = "targetId"
        const val KEY_TITLE = "title"
        const val KEY_ENGINE_ID = "engineId"
        const val KEY_GAME_ID = "gameId"
        const val KEY_ERROR = "error"
        const val KEY_RESUME_SAVE_SLOT = "resumeSaveSlot"
        const val KEY_RESUME_UNAVAILABLE_REASON = "resumeUnavailableReason"
        const val KEY_LOAD_GAME_AVAILABLE = "loadGameAvailable"
        const val KEY_SAVE_CAPABILITIES_READY = "saveCapabilitiesReady"
        const val KEY_REMOVED = "removed"
    }
}
