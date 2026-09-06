package com.jamesmoran.adventurepad

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Top-display AdventurePad facade backed directly by ScummVM's configured targets. */
class MainActivity : ComponentActivity() {
    private lateinit var libraryClient: ScummVMLibraryClient
    private lateinit var skinRepository: SkinRepository
    private lateinit var launcherLibraryMetadataRepository: LauncherLibraryMetadataRepository
    private lateinit var launcherPointerInput: LauncherPointerInput
    private var libraryState by mutableStateOf(ScummVMLibraryState())
    private var ownsTopScreenCursor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        skinRepository = SkinRepository.get(this)
        launcherLibraryMetadataRepository = LauncherLibraryMetadataRepository.create(this, lifecycleScope)
        launcherPointerInput = LauncherPointerInput(this)
        libraryClient = ScummVMLibraryClient(
            context = this,
            onStateChanged = { libraryState = it },
            onGameRemovalResult = { result ->
                if (!result.removed) {
                    Toast.makeText(
                        this,
                        result.error ?: "ScummVM could not remove the configured game.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )

        setContent {
            val launcherMetadata by launcherLibraryMetadataRepository.metadata.collectAsState()
            LaunchedEffect(
                libraryState.targets,
                libraryState.loading,
                libraryState.error,
                libraryState.connected,
                launcherMetadata.loaded,
            ) {
                if (launcherMetadata.loaded && libraryState.connected &&
                    !libraryState.loading && libraryState.error == null
                ) {
                    launcherLibraryMetadataRepository.reconcileTargets(
                        libraryState.targets.map(ScummVMTarget::targetId),
                    )
                }
            }
            val launcherSkin = skinRepository.resolve(SkinContext.LAUNCHER)
            AdventurePadSkinTheme(skin = launcherSkin) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AdventurePadLauncherScreen(
                        state = libraryState,
                        metadata = launcherMetadata,
                        onRefresh = libraryClient::refresh,
                        onLaunchTarget = ::launchTarget,
                        onResumeTarget = ::resumeTarget,
                        onLoadTarget = ::loadTarget,
                        onAddGame = ::addGame,
                        onRemoveTarget = ::removeTarget,
                        onOpenAdvancedScummVM = ::openAdvancedScummVM,
                        onManualOrderChanged = { order ->
                            lifecycleScope.launch {
                                launcherLibraryMetadataRepository.setManualOrder(order)
                            }
                        },
                    )
                    AdventurePadLauncherCursor(
                        ownsTopScreen = ownsTopScreenCursor,
                        onStateChanged = launcherPointerInput::updateCursor,
                    )
                }
            }
        }

        DualDisplayCoordinator.launchTrackpad(
            activity = this,
            reason = "AdventurePad launcher opened",
        )
    }

    override fun onStart() {
        super.onStart()
        libraryClient.connect()
    }

    override fun onResume() {
        super.onResume()
        ownsTopScreenCursor = true
        launcherPointerInput.activate()
        if (libraryState.connected) libraryClient.refresh()
    }

    override fun onPause() {
        launcherPointerInput.deactivate()
        ownsTopScreenCursor = false
        super.onPause()
    }

    override fun onStop() {
        libraryClient.disconnect()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        libraryClient.refresh()
    }

    private fun launchTarget(target: ScummVMTarget) {
        if (libraryClient.launch(target.targetId)) {
            lifecycleScope.launch {
                launcherLibraryMetadataRepository.recordLaunch(target.targetId)
            }
        } else {
            Toast.makeText(this, "ScummVM is not connected yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeTarget(target: ScummVMTarget) {
        val saveSlot = target.resumeSaveSlot
        if (!target.resumeGameAvailable || saveSlot == null) {
            Toast.makeText(
                this,
                target.resumeUnavailableReason ?: "No resumable save is available.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (libraryClient.resume(target.targetId, saveSlot)) {
            lifecycleScope.launch {
                launcherLibraryMetadataRepository.recordLaunch(target.targetId)
            }
        } else {
            Toast.makeText(this, "ScummVM is not connected yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTarget(target: ScummVMTarget) {
        if (!target.loadGameAvailable) {
            Toast.makeText(
                this,
                "This game does not support loading from the ScummVM launcher.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (!libraryClient.openLoadGame(target.targetId)) {
            Toast.makeText(this, "ScummVM is not connected yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAdvancedScummVM() {
        if (!libraryClient.openAdvancedScummVM()) {
            Toast.makeText(this, "ScummVM is not connected yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addGame() {
        if (!libraryClient.addGame()) {
            Toast.makeText(this, "ScummVM is not connected yet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeTarget(target: ScummVMTarget) {
        if (!libraryClient.removeGame(target.targetId)) {
            Toast.makeText(this, "ScummVM is not connected yet.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Shared lifecycle diagnostic model still used by the lower-screen diagnostics panel. */
internal data class ActivityRuntimeDiagnostics(
    val displayId: Int,
    val taskId: Int,
    val isTaskRoot: Boolean,
    val lifecycleEvent: String,
    val intentFlags: Int,
    val lastResult: String,
)
