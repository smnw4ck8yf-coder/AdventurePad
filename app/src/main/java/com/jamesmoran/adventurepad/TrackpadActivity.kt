package com.jamesmoran.adventurepad

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.jamesmoran.adventurepad.ui.theme.AdventurePadDesign
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeDefinition
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.sqrt

class TrackpadActivity : ComponentActivity() {
    private var lifecycleEvent by mutableStateOf("INITIALIZING")
    private var lastLaunchResult by mutableStateOf("Waiting for launch details.")
    private var receivedIntentFlags by mutableIntStateOf(0)
    private var currentDisplayId by mutableIntStateOf(Display.INVALID_DISPLAY)
    private var mouseDiagnostics by mutableStateOf(MouseDiagnostics())
    private var connectionDiagnostics by mutableStateOf(ScummVMConnectionDiagnostics())
    private var mirrorOutputStatus by mutableStateOf(MirrorOutputStatus())
    private var gestureResetGeneration by mutableIntStateOf(0)
    private var mirrorHost: MirrorHost? = null
    private var mirrorLifecycleActive = false
    private val mouseButtonSources = ScummVMMouseButton.entries.associateWith {
        mutableSetOf<MouseButtonSource>()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val forwardedGamepadKeysDown = mutableSetOf<Int>()
    private val controllerModeChordResolver = ControllerTriggerChordResolver(TRIGGER_CHORD_WINDOW_MILLIS)
    private var triggerChordDeadline: Long? = null
    private val triggerChordTimeoutRunnable = Runnable {
        val deadline = triggerChordDeadline ?: return@Runnable
        triggerChordDeadline = null
        applyTriggerChordResolution(controllerModeChordResolver.onTimeout(deadline))
    }
    private val rawTouchDiagnostics = RawTouchDiagnostics()
    private val touchProvenance = TrackpadTouchProvenance()
    private lateinit var pointerSpeedRepository: PointerSpeedRepository
    private lateinit var themePreferencesRepository: ThemePreferencesRepository
    private lateinit var interfaceStylePreferencesRepository: InterfaceStylePreferencesRepository
    private lateinit var skinRepository: SkinRepository
    private lateinit var mirrorCropRepository: MirrorCropRepository
    private lateinit var displayModePreferencesRepository: DisplayModePreferencesRepository
    private lateinit var companionNotesRepository: CompanionNotesRepository
    private lateinit var walkthroughRepository: WalkthroughRepository
    private var mirrorSourceGeometry by mutableStateOf<MirrorSourceGeometry?>(null)
    private var pendingCropTargetId: String? = null
    private var cropTargetHandoffJob: Job? = null
    private var companionTargetId by mutableStateOf("")
    private var lowerSkinContext by mutableStateOf(SkinContext.GAMEPLAY)
    private var mirrorCursorState by mutableStateOf(MirrorCursorState())
    private var cropEditorModel by mutableStateOf<CropEditorModel?>(null)
    private var cropEditorVisible by mutableStateOf(false)
    private var splitBeforeEditing = InterfaceSplit.Default
    private var cropEditTransaction: CropEditTransaction? = null
    private var lastCropAcknowledgement by mutableStateOf<CropAcknowledgement?>(null)
    private var cropSavePending by mutableStateOf(false)
    private var activeCropGeneration by mutableLongStateOf(0L)
    private val cropSaveGate = CropSaveGate()
    private val cropApplicationGate = MirrorCropApplicationGate()
    private var displayMode by mutableStateOf(DisplayMode.TRACKPAD)
    private var interfacePanelRequested by mutableStateOf(false)
    private var requestedPreferredDisplayMode: DisplayMode? = null
    private var lastUpperPresentationAcknowledgement by mutableStateOf<UpperPresentationAcknowledgement?>(null)
    private val upperPresentationGate = UpperPresentationGate()
    private var lastCompositionDiagnostic: String? = null
    private var compositionDiagnosticCount = 0
    private lateinit var twoFingerDoubleTapResolver: TwoFingerDoubleTapResolver
    private var pendingTwoFingerTapUptimeMillis: Long? = null
    private val twoFingerTapTimeoutRunnable = Runnable {
        val expected = pendingTwoFingerTapUptimeMillis ?: return@Runnable
        pendingTwoFingerTapUptimeMillis = null
        if (twoFingerDoubleTapResolver.resolveTimeout(expected)) {
            recordGestureDiagnostic("TWO-FINGER DOUBLE-TAP TIMEOUT: CONVERTED TO RIGHT CLICK")
            sendTwoFingerRightClick()
        }
    }
    private var pendingSplitPreview: InterfaceSplit? = null
    private var cropPreviewScheduled = false
    private val cropPreviewRunnable = Runnable {
        cropPreviewScheduled = false
        pendingSplitPreview?.let(::previewSplitImmediately)
        pendingSplitPreview = null
    }
    private val tapLeftButtonRelease = Runnable {
        releaseMouseButton(ScummVMMouseButton.LEFT, MouseButtonSource.TRACKPAD_TAP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("AdventurePadTarget", "TrackpadActivity onCreate")

        receivedIntentFlags = intent.flags
        lowerSkinContext = lowerSkinContextForIntent(
            requestedContext = intent.skinContextOr(SkinContext.GAMEPLAY),
            cachedTargetId = null,
        )
        Log.i(
            THEME_DIAGNOSTIC_TAG,
            "onCreate requestedContext=${intent.skinContextOr(SkinContext.GAMEPLAY)} " +
                "resolvedContext=$lowerSkinContext",
        )
        currentDisplayId = display?.displayId ?: Display.INVALID_DISPLAY
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Launched because: $it" }
            ?: "TrackpadActivity created without a launch reason."
        recordLifecycle("CREATED")
        touchProvenance.reset(gestureResetGeneration)
        rawTouchDiagnostics.reset(gestureResetGeneration, "CREATE")
        pointerSpeedRepository = PointerSpeedRepository.create(this, lifecycleScope)
        themePreferencesRepository = ThemePreferencesRepository.create(this, lifecycleScope)
        interfaceStylePreferencesRepository = InterfaceStylePreferencesRepository.create(this, lifecycleScope)
        skinRepository = SkinRepository.get(this)
        mirrorCropRepository = MirrorCropRepository.create(this, lifecycleScope)
        displayModePreferencesRepository = DisplayModePreferencesRepository.create(this, lifecycleScope)
        companionNotesRepository = CompanionNotesRepository.create(this, lifecycleScope)
        walkthroughRepository = WalkthroughRepository.create(this, lifecycleScope)
        twoFingerDoubleTapResolver = TwoFingerDoubleTapResolver(
            ViewConfiguration.getDoubleTapTimeout().toLong(),
        )
        lifecycleScope.launch {
            mirrorCropRepository.selection.collect { selection ->
                val geometry = mirrorSourceGeometry
                val profile = selection.profile
                if (!cropEditorVisible && geometry != null && selection.gameId == geometry.gameId &&
                    profile.isCompatibleWith(geometry)
                ) {
                    sendSplit(profile.split, saveAfterAcknowledgement = false)
                }
                reconcileDisplayComposition()
            }
        }
        lifecycleScope.launch {
            displayModePreferencesRepository.preferences.collect {
                reconcileDisplayComposition()
            }
        }
        enableEdgeToEdge()

        setContent {
            val pointerSpeed by pointerSpeedRepository.pointerSpeed.collectAsState()
            val activeColourTheme by themePreferencesRepository.activeTheme.collectAsState()
            val skinCatalog by skinRepository.catalog.collectAsState()
            val skinSelectionRevision by skinRepository.selectionRevision.collectAsState()
            val displayModePreferences by displayModePreferencesRepository.preferences.collectAsState()
            val cropSelection by mirrorCropRepository.selection.collectAsState()
            val notesSelection by companionNotesRepository.selection.collectAsState()
            val walkthroughSelection by walkthroughRepository.selection.collectAsState()
            val cropProfile = cropSelection.profile
            val currentGameId = companionTargetId
            val requestedInterfaceStyleFlow = remember(currentGameId) {
                interfaceStylePreferencesRepository.requestedStyle(currentGameId)
            }
            val requestedInterfaceStyle by requestedInterfaceStyleFlow.collectAsState(
                // A newly selected flow may not have replayed its persisted value yet. Standard
                // avoids even a single frame of hidden native controls during a game transition.
                initial = InterfaceStyle.STANDARD,
            )
            val activeSkin = remember(
                lowerSkinContext,
                currentGameId,
                skinCatalog,
                skinSelectionRevision,
                activeColourTheme,
            ) {
                skinRepository.resolve(lowerSkinContext, currentGameId, activeColourTheme)
            }
            val nativeTheme = remember(lowerSkinContext, activeColourTheme, activeSkin) {
                nativeThemeForSkinContext(lowerSkinContext, activeColourTheme, activeSkin)
            }
            LaunchedEffect(lowerSkinContext, currentGameId, activeSkin, nativeTheme) {
                Log.i(
                    THEME_DIAGNOSTIC_TAG,
                    "resolved context=$lowerSkinContext target='$currentGameId' " +
                        "skin=${activeSkin.id} skinTheme=${activeSkin.theme.id} " +
                        "nativeTheme=${nativeTheme.id} " +
                        "outer=${nativeTheme.colors.background.diagnosticHex()} " +
                        "trackpad=${nativeTheme.components.trackpadBackground.diagnosticHex()}",
                )
            }
            var pendingCreatedSkin by remember { mutableStateOf<InstalledSkin?>(null) }
            var skinBuildError by remember { mutableStateOf<String?>(null) }
            val addSkinLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            skinRepository.buildFromMasterPng(uri).installedSkin
                        }
                    }.onSuccess {
                        pendingCreatedSkin = it
                    }.onFailure { error ->
                        Log.e("AdventurePadSkins", "PNG skin creation failed", error)
                        skinBuildError = (error as? MasterPngBuildException)?.message
                            ?: "AdventurePad could not create a skin from this PNG."
                    }
                }
            }
            val currentNotes = notesSelection.notes.takeIf { notesSelection.gameId == currentGameId }.orEmpty()
            val currentWalkthrough = walkthroughSelection.document
                .takeIf { walkthroughSelection.gameId == currentGameId }
            AdventurePadSkinTheme(
                skin = activeSkin,
                nativeTheme = nativeTheme,
            ) {
                AdventurePadScreen(
                    mouseDiagnostics = mouseDiagnostics,
                    displayId = currentDisplayId,
                    connectionDiagnostics = connectionDiagnostics,
                    mirrorOutputStatus = mirrorOutputStatus,
                    mirrorSourceGeometry = mirrorSourceGeometry,
                    mirrorCursorState = mirrorCursorState,
                    cropEditorModel = cropEditorModel.takeIf { cropEditorVisible },
                    cropProfile = cropProfile,
                    currentGameId = currentGameId,
                    persistedNotes = currentNotes,
                    walkthrough = currentWalkthrough,
                    lastCropAcknowledgement = lastCropAcknowledgement,
                    lastUpperPresentationAcknowledgement = lastUpperPresentationAcknowledgement,
                    cropSavePending = cropSavePending,
                    activeCropGeneration = activeCropGeneration,
                    displayModePreferences = displayModePreferences,
                    displayMode = displayMode,
                    interfacePanelVisible = interfacePanelRequested,
                    upperExpansionSupported = cropProfile
                        .takeIf { profile -> mirrorSourceGeometry?.let(profile::isCompatibleWith) == true }
                        ?.crop
                        ?.let(::deriveUpperGameplayRegion) != null,
                    gestureResetGeneration = gestureResetGeneration,
                    touchProvenance = touchProvenance,
                    pointerSpeed = pointerSpeed,
                    activeColourTheme = activeColourTheme,
                    requestedInterfaceStyle = requestedInterfaceStyle,
                    skinContext = lowerSkinContext,
                    activeSkin = activeSkin,
                    installedSkins = skinCatalog,
                    onPointerSpeedSelected = { selectedSpeed ->
                        lifecycleScope.launch {
                            pointerSpeedRepository.setPointerSpeed(selectedSpeed)
                        }
                    },
                    onColourThemeSelected = { selectedTheme ->
                        lifecycleScope.launch { themePreferencesRepository.selectTheme(selectedTheme) }
                    },
                    onInterfaceStyleSelected = { selectedStyle ->
                        if (currentGameId.isNotBlank()) lifecycleScope.launch {
                            interfaceStylePreferencesRepository.selectStyle(currentGameId, selectedStyle)
                        }
                    },
                    onSkinSelected = { skinId ->
                        skinRepository.assignGameplaySkin(currentGameId, skinId)
                        defaultRequestedStyleForSkinSelection(skinId)?.let { defaultStyle ->
                            lifecycleScope.launch {
                                interfaceStylePreferencesRepository.selectStyle(
                                    currentGameId,
                                    defaultStyle,
                                )
                            }
                        }
                    },
                    onAddSkin = {
                        addSkinLauncher.launch(arrayOf("image/png"))
                    },
                    onRemoveSkin = { skinRepository.remove(it) },
                    onNotesChanged = { notes ->
                        lifecycleScope.launch { companionNotesRepository.save(currentGameId, notes) }
                    },
                    onSaveWalkthroughToNotes = { passage, sectionTitle ->
                        lifecycleScope.launch {
                            companionNotesRepository.appendWalkthrough(currentGameId, passage, sectionTitle)
                        }
                    },
                    onWalkthroughImported = { document ->
                        lifecycleScope.launch { walkthroughRepository.save(currentGameId, document) }
                    },
                    onWalkthroughRemoved = {
                        lifecycleScope.launch { walkthroughRepository.remove(currentGameId) }
                    },
                    onWalkthroughPositionChanged = { position ->
                        lifecycleScope.launch { walkthroughRepository.updatePosition(currentGameId, position) }
                    },
                    onWalkthroughPreferencesChanged = { preferences ->
                        lifecycleScope.launch { walkthroughRepository.updatePreferences(currentGameId, preferences) }
                    },
                    onPreferredDisplayModeChanged = { preferredMode ->
                        requestPreferredDisplayMode(preferredMode)
                    },
                    onGesture = ::handleTrackpadGesture,
                    onGestureDiagnostic = ::recordGestureDiagnostic,
                    onButtonDown = ::pressDedicatedButton,
                    onButtonUp = ::releaseDedicatedButton,
                    onMirrorViewAvailable = ::onMirrorViewAvailable,
                    onMirrorViewDisposed = ::onMirrorViewDisposed,
                    onOpenCropEditor = ::openCropEditor,
                    onCropEditorChanged = { updatedModel ->
                        cropSaveGate.cancel()
                        cropSavePending = false
                        cropEditTransaction?.update(updatedModel.split)
                        cropEditorModel = updatedModel
                        queueSplitPreview(updatedModel.split)
                    },
                    onSaveCrop = {
                        cancelPendingCropPreview()
                        sendSplit(cropEditorModel?.split, saveAfterAcknowledgement = true)
                    },
                    onCancelCrop = ::cancelCropEditor,
                    onRestoreTrackpad = ::restoreTrackpad,
                    onRestoreBothScreens = ::restoreBothScreens,
                )
                pendingCreatedSkin?.let { imported ->
                    AlertDialog(
                        onDismissRequest = { pendingCreatedSkin = null },
                        title = { Text("Skin added") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkinFileArtwork(
                                    imported.root?.resolve("preview.png"),
                                    Modifier.fillMaxWidth().height(160.dp),
                                )
                                Text("${imported.manifest.name} by ${imported.manifest.author}")
                                Text(if (currentGameId.isBlank()) "Start a game to assign this skin." else "Apply to $currentGameId?")
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = currentGameId.isNotBlank(),
                                onClick = {
                                    skinRepository.assignGameplaySkin(currentGameId, imported.manifest.id)
                                    lifecycleScope.launch {
                                        interfaceStylePreferencesRepository.selectStyle(
                                            currentGameId,
                                            InterfaceStyle.IMMERSIVE,
                                        )
                                    }
                                    pendingCreatedSkin = null
                                },
                            ) { Text("APPLY") }
                        },
                        dismissButton = { TextButton(onClick = { pendingCreatedSkin = null }) { Text("KEEP INSTALLED") } },
                    )
                }
                skinBuildError?.let { error ->
                    AlertDialog(
                        onDismissRequest = { skinBuildError = null },
                        title = { Text("Skin could not be added") },
                        text = { Text(error) },
                        confirmButton = { TextButton(onClick = { skinBuildError = null }) { Text("OK") } },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register first: the listeners below synchronously replay cached state and may request geometry.
        ScummVMInputClient.setMirrorGeometryListener(::handleMirrorGeometry)
        ScummVMInputClient.setConnectionStateListener { updatedDiagnostics ->
            val connectionWasLost = connectionDiagnostics.isConnected &&
                !updatedDiagnostics.isConnected
            val connectionWasEstablished = !connectionDiagnostics.isConnected &&
                updatedDiagnostics.isConnected
            connectionDiagnostics = updatedDiagnostics
            if (connectionWasLost) {
                cropApplicationGate.invalidate()
                upperPresentationGate.cancel(invalidateRequest = true)
                discardLocalInputState("CONNECTION LOSS")
                if (cropEditorVisible) cancelCropEditor()
                enterSafeTrackpadMode()
            }
            if (connectionWasEstablished) {
                cropApplicationGate.invalidate()
                upperPresentationGate.cancel(invalidateRequest = true)
                mirrorHost?.refreshAttachment(currentDisplayId)
                ScummVMInputClient.queryMirrorGeometry()
                reconcileDisplayComposition()
            }
        }
        ScummVMInputClient.setMirrorStatusListener { status ->
            mirrorOutputStatus = status
            val appliesToActiveSurface = mirrorHost?.ownsGeneration(status.generation) == true
            if (status.state == MirrorOutputState.SUPPORTED && appliesToActiveSurface) {
                ScummVMInputClient.queryMirrorGeometry()
                reconcileDisplayComposition()
            } else if (appliesToActiveSurface && (status.state == MirrorOutputState.FAILED ||
                status.state == MirrorOutputState.UNSUPPORTED_NO_TEXTURE ||
                status.state == MirrorOutputState.DETACHED)
            ) {
                enterSafeTrackpadMode()
            }
        }
        ScummVMInputClient.setCropAcknowledgementListener(::handleCropAcknowledgement)
        ScummVMInputClient.setUpperPresentationAcknowledgementListener(::handleUpperPresentationAcknowledgement)
        ScummVMInputClient.setMirrorCursorListener { mirrorCursorState = it }
        ScummVMInputClient.bind(this)
        recordLifecycle("STARTED")
    }

    override fun onResume() {
        super.onResume()
        mirrorLifecycleActive = true
        mirrorHost?.activate(currentDisplayId)
        reconcileDisplayComposition()
        recordLifecycle("RESUMED")
    }

    override fun onPause() {
        if (cropEditorVisible) cancelCropEditor()
        enterSafeTrackpadMode()
        mirrorLifecycleActive = false
        mirrorHost?.deactivate()
        releaseAllMouseButtons("PAUSE")
        releaseForwardedGamepadKeys()
        CursorDeltaCoordinator.releaseJoystickAxes()
        recordLifecycle("PAUSED")
        super.onPause()
    }

    override fun onStop() {
        releaseAllMouseButtons("STOP")
        recordLifecycle("STOPPED")
        releaseForwardedGamepadKeys()
        CursorDeltaCoordinator.releaseJoystickAxes()
        ScummVMInputClient.unbind()
        ScummVMInputClient.setConnectionStateListener(null)
        ScummVMInputClient.setMirrorStatusListener(null)
        ScummVMInputClient.setMirrorGeometryListener(null)
        ScummVMInputClient.setCropAcknowledgementListener(null)
        ScummVMInputClient.setUpperPresentationAcknowledgementListener(null)
        ScummVMInputClient.setMirrorCursorListener(null)
        super.onStop()
    }

    private fun openCropEditor() {
        val geometry = mirrorSourceGeometry ?: return
        if (!geometry.isSupported) return
        val saved = mirrorCropRepository.selection.value.profile
        splitBeforeEditing = saved.split.takeIf { saved.isCompatibleWith(geometry) }
            ?: InterfaceSplit.Default.snappedTo(geometry.height)
        cropEditTransaction = CropEditTransaction(splitBeforeEditing)
        cropEditorModel = CropEditorModel.create(splitBeforeEditing, geometry)
        cropEditorVisible = true
        lastCropAcknowledgement = null
        cropSavePending = false
        reconcileDisplayComposition()
    }

    private fun cancelCropEditor() {
        if (!cropEditorVisible) return
        cancelPendingCropPreview()
        cropSaveGate.cancel()
        cropSavePending = false
        val completion = cancelSplitEditor(cropEditTransaction, splitBeforeEditing)
        finishCropEditor(completion)
        reconcileDisplayComposition()
    }

    private fun finishCropEditor(completion: SplitEditorCompletion) {
        splitBeforeEditing = completion.split
        cropEditorVisible = completion.editorVisible
        cropEditorModel = null
        cropEditTransaction = null
    }

    private fun sendSplit(split: InterfaceSplit?, saveAfterAcknowledgement: Boolean) {
        val geometry = mirrorSourceGeometry ?: return
        if (split == null || !split.isValid() || !geometry.isSupported) return
        val snappedSplit = split.snappedTo(geometry.height)
        val crop = snappedSplit.interfaceCrop
        if (!saveAfterAcknowledgement && !cropApplicationGate.begin(
                MirrorCropRequest(crop, geometry.generation),
            )
        ) return
        val generation = MirrorCropGenerations.next()
        activeCropGeneration = 0L
        if (saveAfterAcknowledgement && !cropSaveGate.begin(generation, snappedSplit)) return
        if (saveAfterAcknowledgement) cropSavePending = true
        if (!ScummVMInputClient.applyMirrorCrop(crop, generation, geometry.generation)) {
            if (!saveAfterAcknowledgement) cropApplicationGate.invalidate()
            if (saveAfterAcknowledgement) {
                cropSaveGate.cancel()
                cropSavePending = false
            }
        }
    }

    private fun sendCropImmediately(crop: NormalizedCrop) {
        val geometry = mirrorSourceGeometry ?: return
        if (!crop.isValid() || !geometry.isSupported) return
        if (!cropApplicationGate.begin(MirrorCropRequest(crop, geometry.generation))) return
        activeCropGeneration = 0L
        if (!ScummVMInputClient.applyMirrorCrop(
                crop,
                MirrorCropGenerations.next(),
                geometry.generation,
            )
        ) cropApplicationGate.invalidate()
    }

    private fun previewSplitImmediately(split: InterfaceSplit) {
        if (cropEditorVisible && cropEditorModel?.split == split) reconcileDisplayComposition()
    }

    private fun queueSplitPreview(split: InterfaceSplit) {
        pendingSplitPreview = split
        if (cropPreviewScheduled) return
        cropPreviewScheduled = true
        mainHandler.postDelayed(cropPreviewRunnable, CROP_PREVIEW_INTERVAL_MILLIS)
    }

    private fun cancelPendingCropPreview() {
        mainHandler.removeCallbacks(cropPreviewRunnable)
        cropPreviewScheduled = false
        pendingSplitPreview = null
    }

    private fun handleMirrorGeometry(geometry: MirrorSourceGeometry) {
        Log.i(
        "AdventurePadTarget",
        "handleMirrorGeometry gameId='${geometry.gameId}' generation=${geometry.generation}"
    )
        val previous = mirrorSourceGeometry
        val previousSkinContext = lowerSkinContext
        lowerSkinContext = lowerSkinContextForTarget(geometry.gameId)
        Log.i(
            THEME_DIAGNOSTIC_TAG,
            "geometry target='${geometry.gameId}' context=$previousSkinContext->$lowerSkinContext",
        )
        val changed = previous != null &&
            (previous.generation != geometry.generation || previous.gameId != geometry.gameId)
        mirrorSourceGeometry = geometry
        when {
            pendingCropTargetId == geometry.gameId -> Unit
            mirrorCropRepository.selection.value.gameId.isBlank() && geometry.gameId.isNotBlank() -> {
                cropTargetHandoffJob?.cancel()
                pendingCropTargetId = geometry.gameId
                cropTargetHandoffJob = lifecycleScope.launch {
                    try {
                        mirrorCropRepository.handoffLauncherProfile(geometry.gameId, geometry)
                    } finally {
                        if (pendingCropTargetId == geometry.gameId) {
                            pendingCropTargetId = null
                            reconcileDisplayComposition()
                        }
                    }
                }
            }
            else -> mirrorCropRepository.selectGame(geometry.gameId)
        }
        companionTargetId = retainAndRouteCompanionTargetId(
            currentTargetId = companionTargetId,
            reportedTargetId = geometry.gameId,
            selectNotesTarget = companionNotesRepository::selectGame,
            selectWalkthroughTarget = walkthroughRepository::selectGame,
        )
        Log.i(
            "AdventurePadTarget",
            "companionTargetId='$companionTargetId'"
        )
        if (!geometry.isSupported) {
            if (cropEditorVisible) cancelCropEditor()
            return
        }
        if (previous?.gameId != geometry.gameId) {
            sendCropImmediately(NormalizedCrop.FullFrame)
            return
        }
        if (changed && cropEditorVisible) {
            cancelPendingCropPreview()
            cropSaveGate.cancel()
            cropSavePending = false
            cropEditorVisible = false
            cropEditorModel = null
            cropEditTransaction = null
        }
        val saved = mirrorCropRepository.selection.value.profile
        saved.split.takeIf { saved.isCompatibleWith(geometry) }
            ?.let { sendSplit(it, saveAfterAcknowledgement = false) }
            ?: sendCropImmediately(NormalizedCrop.FullFrame)
        reconcileDisplayComposition()
    }

    private fun handleCropAcknowledgement(acknowledgement: CropAcknowledgement) {
        lastCropAcknowledgement = acknowledgement
        val geometry = mirrorSourceGeometry
        if (acknowledgement.result == CropAcknowledgementResult.APPLIED && geometry != null &&
            acknowledgement.geometryGeneration == geometry.generation
        ) {
            activeCropGeneration = acknowledgement.cropGeneration
        }
        val matchedPendingSave = cropSaveGate.pendingGeneration == acknowledgement.cropGeneration
        if (matchedPendingSave) cropSavePending = false
        val split = cropSaveGate.acknowledge(acknowledgement) ?: return
        val activeGeometry = geometry ?: return
        if (acknowledgement.geometryGeneration != activeGeometry.generation) return
        val completion = saveSplitEditor(split)
        if (!completion.shouldPersist) return
        lifecycleScope.launch {
            mirrorCropRepository.save(
                MirrorCropProfile(
                    split = completion.split,
                    sourceWidth = activeGeometry.width,
                    sourceHeight = activeGeometry.height,
                    sourceAspectRatio = activeGeometry.aspectRatio,
                    confirmed = true,
                    requiresReview = false,
                ),
            )
            finishCropEditor(completion)
            reconcileDisplayComposition()
        }
    }

    private fun reconcileDisplayComposition() {
        if (!::displayModePreferencesRepository.isInitialized ||
            !::mirrorCropRepository.isInitialized
        ) return
        val preferences = displayModePreferencesRepository.preferences.value
        val geometry = mirrorSourceGeometry
        val selection = mirrorCropRepository.selection.value
        val savedSplit = geometry?.let {
            compatibleCropSplit(selection, it, pendingCropTargetId)
        }
        val activeView = mirrorHost
        val activeSurfaceReady = mirrorOutputStatus.state == MirrorOutputState.SUPPORTED &&
            activeView?.ownsGeneration(mirrorOutputStatus.generation) == true
        val target = resolvePresentationTarget(
            preferredMode = requestedPreferredDisplayMode ?: preferences.preferredMode,
            savedSplit = savedSplit,
            editorSplit = cropEditorModel?.split.takeIf { cropEditorVisible },
            connected = connectionDiagnostics.isConnected,
            activeSurfaceReady = activeSurfaceReady,
        )
        logCompositionTransition(
            requestedMode = requestedPreferredDisplayMode ?: preferences.preferredMode,
            effectiveMode = target.mode,
            mirrorRequired = target.runtimePanelRequested || target.owner == PresentationOwner.EDITOR,
            connected = connectionDiagnostics.isConnected,
            activeSurfaceReady = activeSurfaceReady,
            owner = target.owner,
        )
        interfacePanelRequested = target.runtimePanelRequested
        displayMode = target.mode
        if (target.mode == DisplayMode.INTERFACE) {
            if (target.owner == PresentationOwner.EDITOR) {
                sendCropImmediately(target.lowerCrop)
            } else {
                sendSplit(savedSplit, saveAfterAcknowledgement = false)
            }
        }
        requestUpperPresentation(target.mode, target.upperCrop)
    }

    private fun enterSafeTrackpadMode() {
        val crop = mirrorCropRepository.selection.value.profile.crop.takeIf { it.isValid() }
            ?: NormalizedCrop.FullFrame
        displayMode = DisplayMode.TRACKPAD
        interfacePanelRequested = false
        requestUpperPresentation(DisplayMode.TRACKPAD, crop)
    }

    private fun requestUpperPresentation(mode: DisplayMode, crop: NormalizedCrop) {
        val geometry = mirrorSourceGeometry ?: return
        if (!geometry.isSupported || !connectionDiagnostics.isConnected) return
        val generation = DisplayModeGenerations.next()
        val request = UpperPresentationRequest(mode, crop, geometry.generation)
        if (!upperPresentationGate.begin(generation, request)) return
        if (!ScummVMInputClient.applyDisplayMode(mode, crop, generation, geometry.generation)) {
            upperPresentationGate.cancel(invalidateRequest = true)
            displayMode = DisplayMode.TRACKPAD
            interfacePanelRequested = false
        }
    }

    private fun logCompositionTransition(
        requestedMode: DisplayMode,
        effectiveMode: DisplayMode,
        mirrorRequired: Boolean,
        connected: Boolean,
        activeSurfaceReady: Boolean,
        owner: PresentationOwner,
    ) {
        val diagnostic = "composition requested=${requestedMode.name} effective=${effectiveMode.name} " +
            "owner=${owner.name} mirrorRequired=$mirrorRequired connected=$connected " +
            "activeSurfaceReady=$activeSurfaceReady"
        if (diagnostic == lastCompositionDiagnostic || compositionDiagnosticCount >= 32) return
        lastCompositionDiagnostic = diagnostic
        compositionDiagnosticCount += 1
        Log.i("AdventurePadMirrorInit", "compositionEvent=$compositionDiagnosticCount/32 $diagnostic")
    }

    private fun handleUpperPresentationAcknowledgement(
        acknowledgement: UpperPresentationAcknowledgement,
    ) {
        if (!upperPresentationGate.acknowledge(acknowledgement)) return
        lastUpperPresentationAcknowledgement = acknowledgement
        when (acknowledgement.result) {
            UpperPresentationResult.FULL_FRAME_APPLIED,
            UpperPresentationResult.EXPANDED_APPLIED,
            UpperPresentationResult.EXPANDED_UNSUPPORTED_SHAPE,
            -> Unit
            UpperPresentationResult.INVALID_CROP,
            UpperPresentationResult.STALE_GENERATION,
            UpperPresentationResult.UNSUPPORTED_RENDERER,
            -> {
                upperPresentationGate.cancel(invalidateRequest = true)
                enterSafeTrackpadMode()
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
            event.actionMasked == MotionEvent.ACTION_MOVE &&
            ScummVMInputClient.sendJoystickMotion(event) { trigger ->
                applyTriggerChordResolution(
                    controllerModeChordResolver.onAxis(trigger, event.eventTime),
                )
            }
        ) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchProvenance.recordPlatformDown(
                generation = gestureResetGeneration,
                downTimeMillis = event.downTime,
                pointerId = event.getPointerId(event.actionIndex),
            )
            MotionEvent.ACTION_POINTER_DOWN -> touchProvenance.recordAdditionalPointer(
                generation = gestureResetGeneration,
                downTimeMillis = event.downTime,
            )
            MotionEvent.ACTION_UP -> touchProvenance.recordPlatformUp(
                generation = gestureResetGeneration,
                downTimeMillis = event.downTime,
                pointerId = event.getPointerId(event.actionIndex),
            )
            MotionEvent.ACTION_CANCEL -> touchProvenance.recordPlatformCancel(
                generation = gestureResetGeneration,
                downTimeMillis = event.downTime,
            )
        }
        rawTouchDiagnostics.recordPlatformEvent(
            event = event,
            resetGeneration = gestureResetGeneration,
            touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat(),
        )
        return super.dispatchTouchEvent(event)
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
        enterSafeTrackpadMode()
        mirrorLifecycleActive = false
        mirrorHost?.dispose()
        mirrorHost = null
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
        mirrorHost?.deactivate()
        releaseAllMouseButtons("NEW INTENT")
        releaseForwardedGamepadKeys()
        CursorDeltaCoordinator.releaseJoystickAxes()
        setIntent(intent)
        val requestedSkinContext = intent.skinContextOr(lowerSkinContext)
        lowerSkinContext = lowerSkinContextForIntent(
            requestedContext = requestedSkinContext,
            cachedTargetId = mirrorSourceGeometry?.gameId,
        )
        Log.i(
            THEME_DIAGNOSTIC_TAG,
            "onNewIntent requestedContext=$requestedSkinContext resolvedContext=$lowerSkinContext " +
                "geometryTarget='${mirrorSourceGeometry?.gameId.orEmpty()}'",
        )
        receivedIntentFlags = intent.flags
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Received launch request: $it" }
            ?: "Received a new intent without a launch reason."
        recordLifecycle("NEW_INTENT")
        ScummVMInputClient.queryMirrorGeometry()
        if (mirrorLifecycleActive) mirrorHost?.activate(currentDisplayId)
    }

    private fun onMirrorViewAvailable(host: MirrorHost) {
        if (mirrorHost === host) return
        mirrorHost?.dispose()
        invalidateAppliedCropForMirrorHostChange()
        mirrorHost = host
        if (mirrorLifecycleActive) host.activate(currentDisplayId)
        reconcileDisplayComposition()
    }

    private fun onMirrorViewDisposed(host: MirrorHost) {
        if (mirrorHost !== host) return
        host.dispose()
        invalidateAppliedCropForMirrorHostChange()
        mirrorHost = null
        if (displayMode == DisplayMode.INTERFACE) {
            displayMode = DisplayMode.TRACKPAD
            val crop = mirrorCropRepository.selection.value.profile.crop.takeIf { it.isValid() }
                ?: NormalizedCrop.FullFrame
            requestUpperPresentation(DisplayMode.TRACKPAD, crop)
        }
    }

    private fun invalidateAppliedCropForMirrorHostChange() {
        cropApplicationGate.invalidate()
        activeCropGeneration = 0L
    }

    private fun restoreTrackpad() {
        mirrorHost?.deactivate()
        releaseAllMouseButtons("RESTORE TRACKPAD")
        lastLaunchResult = "Trackpad restore in progress…"
        lastLaunchResult = DualDisplayCoordinator.launchTrackpad(
            activity = this,
            reason = "Trackpad restore requested",
        ).message
    }

    private fun restoreBothScreens() {
        mirrorHost?.deactivate()
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
                    CursorDeltaCoordinator.publishGamepadKey(event.action, event.keyCode)
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
                    CursorDeltaCoordinator.publishGamepadKey(event.action, event.keyCode)
                    true
                }
            }
            else -> false
        }
    }

    private fun releaseForwardedGamepadKeys() {
        forwardedGamepadKeysDown.forEach { keyCode ->
            CursorDeltaCoordinator.publishGamepadKey(KeyEvent.ACTION_UP, keyCode)
        }
        forwardedGamepadKeysDown.clear()
        resetTriggerChord()
    }

    private fun applyTriggerChordResolution(resolution: TriggerChordResolution) {
        resolution.forward.forEach(ScummVMInputClient::sendJoystickAxisValue)
        resolution.scheduleTimeoutAt?.let { deadline ->
            triggerChordDeadline = deadline
            mainHandler.removeCallbacks(triggerChordTimeoutRunnable)
            mainHandler.postDelayed(
                triggerChordTimeoutRunnable,
                (deadline - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L),
            )
        }
        if (resolution.toggle) {
            triggerChordDeadline = null
            mainHandler.removeCallbacks(triggerChordTimeoutRunnable)
            recordGestureDiagnostic("L2 + R2 TRIGGER-AXIS CHORD RECOGNIZED")
            togglePreferredDisplayMode()
        }
    }

    private fun resetTriggerChord() {
        triggerChordDeadline = null
        mainHandler.removeCallbacks(triggerChordTimeoutRunnable)
        controllerModeChordResolver.reset().forEach(ScummVMInputClient::sendJoystickAxisValue)
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
        recordGestureDiagnostic(gesture.label)
        when (gesture) {
            TrackpadGesture.SINGLE_TAP -> {
                resolvePendingTwoFingerTapAsRightClick()
                sendTapLeftClick()
            }
            TrackpadGesture.TWO_FINGER_RIGHT_CLICK -> handleTwoFingerTap()
            TrackpadGesture.DOUBLE_TAP_HOLD_START -> startDoubleTapHoldDrag()
            TrackpadGesture.DOUBLE_TAP_HOLD_END -> releaseMouseButton(
                ScummVMMouseButton.LEFT,
                MouseButtonSource.DOUBLE_TAP_HOLD,
            )
            TrackpadGesture.CANCELLED -> resolvePendingTwoFingerTapAsRightClick()
        }
    }

    private fun handleTwoFingerTap() {
        val now = android.os.SystemClock.uptimeMillis()
        when (twoFingerDoubleTapResolver.onTwoFingerTap(now)) {
            TwoFingerTapResolution.WAIT_FOR_SECOND_TAP -> {
                recordGestureDiagnostic("FIRST TWO-FINGER TAP RECOGNIZED: WAITING FOR SECOND TAP")
                pendingTwoFingerTapUptimeMillis = now
                mainHandler.removeCallbacks(twoFingerTapTimeoutRunnable)
                mainHandler.postDelayed(
                    twoFingerTapTimeoutRunnable,
                    ViewConfiguration.getDoubleTapTimeout().toLong(),
                )
            }
            TwoFingerTapResolution.TOGGLE_MODE -> {
                recordGestureDiagnostic("SECOND TWO-FINGER TAP RECOGNIZED: TOGGLING MODE")
                pendingTwoFingerTapUptimeMillis = null
                mainHandler.removeCallbacks(twoFingerTapTimeoutRunnable)
                togglePreferredDisplayMode()
            }
            TwoFingerTapResolution.NONE -> sendTwoFingerRightClick()
        }
    }

    private fun resolvePendingTwoFingerTapAsRightClick() {
        mainHandler.removeCallbacks(twoFingerTapTimeoutRunnable)
        pendingTwoFingerTapUptimeMillis = null
        if (twoFingerDoubleTapResolver.cancelAndResolveSingleTap()) sendTwoFingerRightClick()
    }

    private fun togglePreferredDisplayMode() {
        val preferences = displayModePreferencesRepository.preferences.value
        val currentMode = requestedPreferredDisplayMode ?: preferences.preferredMode
        val nextMode = if (currentMode == DisplayMode.INTERFACE) DisplayMode.TRACKPAD else DisplayMode.INTERFACE
        Toast.makeText(
            this,
            if (nextMode == DisplayMode.INTERFACE) "SPLIT VIEW ON" else "TRACKPAD MODE",
            Toast.LENGTH_SHORT,
        ).show()
        requestPreferredDisplayMode(nextMode)
    }

    private fun requestPreferredDisplayMode(preferredMode: DisplayMode) {
        requestedPreferredDisplayMode = preferredMode
        if (preferredMode == DisplayMode.TRACKPAD) enterSafeTrackpadMode() else reconcileDisplayComposition()
        lifecycleScope.launch {
            try {
                val current = displayModePreferencesRepository.preferences.value
                displayModePreferencesRepository.save(current.copy(preferredMode = preferredMode))
            } finally {
                if (requestedPreferredDisplayMode == preferredMode) {
                    requestedPreferredDisplayMode = null
                    reconcileDisplayComposition()
                }
            }
        }
    }

    private fun startDoubleTapHoldDrag() {
        val activeLeftSources = mouseButtonSources.getValue(ScummVMMouseButton.LEFT)
        if (MouseButtonSource.DEDICATED_BUTTON in activeLeftSources ||
            MouseButtonSource.CONTROLLER in activeLeftSources ||
            MouseButtonSource.DOUBLE_TAP_HOLD in activeLeftSources
        ) {
            recordGestureDiagnostic("DRAG ACTIVATION BLOCKED: LEFT ALREADY HELD")
            return
        }
        mainHandler.removeCallbacks(tapLeftButtonRelease)
        releaseMouseButton(ScummVMMouseButton.LEFT, MouseButtonSource.TRACKPAD_TAP)
        pressMouseButton(ScummVMMouseButton.LEFT, MouseButtonSource.DOUBLE_TAP_HOLD)
    }

    private fun recordGestureDiagnostic(message: String) {
        mouseDiagnostics = mouseDiagnostics.copy(lastGesture = message)
        Log.i(GESTURE_TAG, message)
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
        updateDragDiagnostics()
        if (sources.size > 1) return true

        if (!CursorDeltaCoordinator.publishButton(button.downEvent)) {
            sources.remove(source)
            updateDragDiagnostics()
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
        updateDragDiagnostics()
        if (sources.isNotEmpty()) return true

        CursorDeltaCoordinator.publishButton(button.upEvent)
        updateMouseDiagnostics(button, isDown = false)
        return true
    }

    private fun releaseAllMouseButtons(reason: String) {
        resetGestureRecognition(reason)
        mainHandler.removeCallbacks(tapLeftButtonRelease)
        ScummVMMouseButton.entries.forEach { button ->
            val sources = mouseButtonSources.getValue(button)
            if (sources.isNotEmpty()) {
                sources.clear()
                CursorDeltaCoordinator.publishButton(button.upEvent)
                updateMouseDiagnostics(button, isDown = false, reason = reason)
            }
        }
        updateDragDiagnostics()
    }

    private fun discardLocalInputState(reason: String) {
        resetGestureRecognition(reason)
        mainHandler.removeCallbacks(tapLeftButtonRelease)
        forwardedGamepadKeysDown.clear()
        resetTriggerChord()
        ScummVMMouseButton.entries.forEach { button ->
            val sources = mouseButtonSources.getValue(button)
            if (sources.isNotEmpty()) {
                sources.clear()
                updateMouseDiagnostics(button, isDown = false, reason = reason)
            }
        }
        updateDragDiagnostics()
    }

    private fun resetGestureRecognition(reason: String) {
        mainHandler.removeCallbacks(twoFingerTapTimeoutRunnable)
        pendingTwoFingerTapUptimeMillis = null
        if (::twoFingerDoubleTapResolver.isInitialized) twoFingerDoubleTapResolver.reset()
        gestureResetGeneration++
        touchProvenance.reset(gestureResetGeneration)
        rawTouchDiagnostics.reset(gestureResetGeneration, reason)
        recordGestureDiagnostic("GESTURE CANCELLED: $reason")
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

    private fun updateDragDiagnostics() {
        val leftSources = mouseButtonSources.getValue(ScummVMMouseButton.LEFT)
        val dragSource = when {
            MouseButtonSource.DEDICATED_BUTTON in leftSources -> DragSource.DEDICATED_LEFT
            MouseButtonSource.DOUBLE_TAP_HOLD in leftSources -> DragSource.DOUBLE_TAP_HOLD
            else -> DragSource.NONE
        }
        mouseDiagnostics = mouseDiagnostics.copy(
            dragSource = dragSource,
            dragActive = dragSource != DragSource.NONE,
            activeMouseButtonSources = ScummVMMouseButton.entries.joinToString("; ") { button ->
                val sources = mouseButtonSources.getValue(button)
                    .joinToString("+") { it.diagnosticLabel }
                    .ifEmpty { "NONE" }
                "${button.name}: $sources"
            },
        )
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
        const val GESTURE_TAG = "AdventurePadGesture"
        const val CROP_PREVIEW_INTERVAL_MILLIS = 16L
        const val TRIGGER_CHORD_WINDOW_MILLIS = 120L
    }
}

@Composable
private fun AdventurePadScreen(
    mouseDiagnostics: MouseDiagnostics,
    displayId: Int,
    connectionDiagnostics: ScummVMConnectionDiagnostics,
    mirrorOutputStatus: MirrorOutputStatus,
    mirrorSourceGeometry: MirrorSourceGeometry?,
    mirrorCursorState: MirrorCursorState,
    cropEditorModel: CropEditorModel?,
    cropProfile: MirrorCropProfile,
    currentGameId: String,
    persistedNotes: String,
    walkthrough: WalkthroughDocument?,
    lastCropAcknowledgement: CropAcknowledgement?,
    lastUpperPresentationAcknowledgement: UpperPresentationAcknowledgement?,
    cropSavePending: Boolean,
    activeCropGeneration: Long,
    displayModePreferences: DisplayModePreferences,
    displayMode: DisplayMode,
    interfacePanelVisible: Boolean,
    upperExpansionSupported: Boolean,
    gestureResetGeneration: Int,
    touchProvenance: TrackpadTouchProvenance,
    pointerSpeed: PointerSpeed,
    activeColourTheme: AdventurePadThemeDefinition,
    requestedInterfaceStyle: InterfaceStyle,
    skinContext: SkinContext,
    activeSkin: ResolvedSkin,
    installedSkins: List<InstalledSkin>,
    onPointerSpeedSelected: (PointerSpeed) -> Unit,
    onColourThemeSelected: (AdventurePadThemeDefinition) -> Unit,
    onInterfaceStyleSelected: (InterfaceStyle) -> Unit,
    onSkinSelected: (String?) -> Unit,
    onAddSkin: () -> Unit,
    onRemoveSkin: (String) -> Boolean,
    onNotesChanged: (String) -> Unit,
    onSaveWalkthroughToNotes: (String, String?) -> Unit,
    onWalkthroughImported: (WalkthroughDocument) -> Unit,
    onWalkthroughRemoved: () -> Unit,
    onWalkthroughPositionChanged: (WalkthroughPosition) -> Unit,
    onWalkthroughPreferencesChanged: (WalkthroughReaderPreferences) -> Unit,
    onPreferredDisplayModeChanged: (DisplayMode) -> Unit,
    onGesture: (TrackpadGesture) -> Unit,
    onGestureDiagnostic: (String) -> Unit,
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
    onMirrorViewAvailable: (MirrorHost) -> Unit,
    onMirrorViewDisposed: (MirrorHost) -> Unit,
    onOpenCropEditor: () -> Unit,
    onCropEditorChanged: (CropEditorModel) -> Unit,
    onSaveCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onRestoreTrackpad: () -> Unit,
    onRestoreBothScreens: () -> Unit,
) {
    val touchState = remember { mutableStateOf(TouchState()) }
    val launcherPresentation = skinContext == SkinContext.LAUNCHER
    val immersiveAvailable = activeSkin.supportsImmersiveGameplayArtwork()
    val interfaceStyle = effectiveInterfaceStyle(
        context = skinContext,
        requestedStyle = requestedInterfaceStyle,
        supportsImmersiveArtwork = immersiveAvailable,
    )
    val immersivePresentation = interfaceStyle == InterfaceStyle.IMMERSIVE
    LaunchedEffect(
        requestedInterfaceStyle,
        interfaceStyle,
        immersivePresentation,
        immersiveAvailable,
    ) {
        Log.i(
            THEME_DIAGNOSTIC_TAG,
            "interface requested=$requestedInterfaceStyle resolved=$interfaceStyle " +
                "immersive=$immersivePresentation " +
                "standardTrackpadChrome=${!immersivePresentation} " +
                "immersiveTrackpadArtwork=${immersivePresentation && immersiveAvailable}",
        )
    }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var activePage by rememberSaveable { mutableStateOf(LowerScreenPage.GAMEPLAY) }
    var companionSection by rememberSaveable { mutableStateOf(CompanionSection.HOME) }
    fun navigate(action: LowerScreenNavigationAction) {
        val updated = reduceLowerScreenNavigation(
            LowerScreenNavigationState(activePage, companionSection),
            action,
        )
        activePage = updated.page
        companionSection = updated.companionSection
    }

    BackHandler(enabled = activePage != LowerScreenPage.GAMEPLAY || cropEditorModel != null) {
        if (cropEditorModel != null) onCancelCrop()
        else if (activePage == LowerScreenPage.COMPANION) navigate(LowerScreenNavigationAction.BackCompanion)
        else navigate(LowerScreenNavigationAction.ClosePage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AdventurePadThemeTokens.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SkinArtwork(
            gameplayBackgroundCandidates(splitViewVisible = interfacePanelVisible),
            Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            if (shouldShowSplitEditorControls(cropEditorModel) && mirrorSourceGeometry != null) {
                MirrorCropEditor(
                    model = checkNotNull(cropEditorModel),
                    cropNeedsReview = cropProfile.requiresReview,
                    savePending = cropSavePending,
                    onModelChanged = onCropEditorChanged,
                    onSave = onSaveCrop,
                    onCancel = onCancelCrop,
                    onViewAvailable = onMirrorViewAvailable,
                    onViewDisposed = onMirrorViewDisposed,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .weight(1f),
                )
            } else {
                val interfaceAspectRatio = mirrorSourceGeometry?.let { geometry ->
                    cropProfile.takeIf { it.isCompatibleWith(geometry) }
                        ?.split
                        ?.let { split ->
                            val cropAspect = geometry.aspectRatio / (1f - split.ratio)
                            if (geometry.orientation.swapsDimensions) 1f / cropAspect else cropAspect
                        }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val desiredInterfaceHeight = interfaceAspectRatio
                        ?.takeIf { interfacePanelVisible && it.isFinite() && it > 0f }
                        ?.let { maxWidth / it * LOWER_PANEL_VERTICAL_SCALE }
                        ?: 0.dp
                    val maximumInterfaceHeight = (maxHeight - NormalLayoutReservedHeight).coerceAtLeast(0.dp)
                    val interfaceHeight = minOf(desiredInterfaceHeight, maximumInterfaceHeight)
                    val showSplitPanelFrame = shouldShowSplitPanelFrame(
                        splitViewActive = interfacePanelVisible,
                        interfaceStyle = interfaceStyle,
                        hasPanelFrameAsset = activeSkin.resolveAssetSlot(SkinSlots.PANEL_FRAME) != null,
                    )
                    Column(Modifier.fillMaxSize()) {
                        if (interfacePanelVisible) {
                            MirrorPrototypePanel(
                                status = mirrorOutputStatus,
                                panelHeight = interfaceHeight,
                                crop = cropProfile.crop,
                                geometry = mirrorSourceGeometry,
                                cursorState = mirrorCursorState,
                                cropGeneration = activeCropGeneration,
                                displayMode = displayMode,
                                showPanelFrame = showSplitPanelFrame,
                                onViewAvailable = onMirrorViewAvailable,
                                onViewDisposed = onMirrorViewDisposed,
                            )
                        }
                        if (launcherPresentation) {
                            LauncherTrackpadSurface(
                                touchState = touchState,
                                gestureResetGeneration = gestureResetGeneration,
                                touchProvenance = touchProvenance,
                                pointerSpeed = pointerSpeed,
                                onGesture = onGesture,
                                onGestureDiagnostic = onGestureDiagnostic,
                                onButtonDown = onButtonDown,
                                onButtonUp = onButtonUp,
                            )
                        } else {
                            val trackpadRegionModifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = MinimumNormalTrackpadHeight)
                            if (immersivePresentation) {
                                BoxWithConstraints(
                                    modifier = trackpadRegionModifier
                                        .padding(
                                            horizontal = AdventurePadDesign.spacingMd,
                                            vertical = IMMERSIVE_TRACKPAD_REGION_VERTICAL_PADDING_DP.dp,
                                        )
                                        .onSizeChanged { size ->
                                            Log.i(
                                                LAYOUT_POLISH_DIAGNOSTIC_TAG,
                                                "immersive trackpad region=${size.width}x${size.height} " +
                                                    "widthScale=$IMMERSIVE_TRACKPAD_SCALE " +
                                                    "splitExtraHeightDp=$IMMERSIVE_SPLIT_TRACKPAD_EXTRA_HEIGHT_DP " +
                                                    "split=$interfacePanelVisible",
                                            )
                                        },
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    val baseSurfaceHeight = maxHeight * IMMERSIVE_TRACKPAD_SCALE
                                    val surfaceHeight = if (interfacePanelVisible) {
                                        (baseSurfaceHeight + IMMERSIVE_SPLIT_TRACKPAD_EXTRA_HEIGHT_DP.dp)
                                            .coerceAtMost(maxHeight)
                                    } else {
                                        baseSurfaceHeight
                                    }
                                    val surfaceHeightExpansion = surfaceHeight - baseSurfaceHeight
                                    TouchSurface(
                                        touchState = touchState,
                                        gestureResetGeneration = gestureResetGeneration,
                                        touchProvenance = touchProvenance,
                                        pointerSpeed = pointerSpeed,
                                        onGesture = onGesture,
                                        onGestureDiagnostic = onGestureDiagnostic,
                                        onButtonDown = onButtonDown,
                                        onButtonUp = onButtonUp,
                                        immersive = true,
                                        launcherPresentation = false,
                                        overlaySizingHeightReduction = surfaceHeightExpansion,
                                        modifier = Modifier
                                            .fillMaxWidth(IMMERSIVE_TRACKPAD_SCALE)
                                            .height(surfaceHeight)
                                            .offset(
                                                y = -(
                                                    IMMERSIVE_TRACKPAD_BOTTOM_GAP_DP -
                                                        IMMERSIVE_TRACKPAD_REGION_VERTICAL_PADDING_DP
                                                    ).dp,
                                            )
                                            .onGloballyPositioned { coordinates ->
                                                val bounds = coordinates.boundsInRoot()
                                                Log.i(
                                                    LAYOUT_POLISH_DIAGNOSTIC_TAG,
                                                    "immersive trackpad boundsInRoot=$bounds " +
                                                        "bottomGapDp=$IMMERSIVE_TRACKPAD_BOTTOM_GAP_DP " +
                                                        "split=$interfacePanelVisible",
                                                )
                                            },
                                    )
                                }
                            } else {
                                Box(
                                    modifier = trackpadRegionModifier.padding(
                                        horizontal = AdventurePadDesign.spacingMd,
                                        vertical = 3.dp,
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TouchSurface(
                                        touchState = touchState,
                                        gestureResetGeneration = gestureResetGeneration,
                                        touchProvenance = touchProvenance,
                                        pointerSpeed = pointerSpeed,
                                        onGesture = onGesture,
                                        onGestureDiagnostic = onGestureDiagnostic,
                                        onButtonDown = onButtonDown,
                                        onButtonUp = onButtonUp,
                                        immersive = false,
                                        launcherPresentation = false,
                                        overlayHeightScale =
                                            1f / AdventurePadDesign.standardGameplayTrackpadHeightScale,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(
                                                AdventurePadDesign.standardGameplayTrackpadHeightScale,
                                            ),
                                    )
                                }
                            }
                            GameplayUtilityBar(
                                immersive = immersivePresentation,
                                launcherPresentation = false,
                                onOpenCompanion = { navigate(LowerScreenNavigationAction.OpenCompanion) },
                                onOpenSettings = { navigate(LowerScreenNavigationAction.OpenSettings) },
                                modifier = if (immersivePresentation) {
                                    Modifier.onGloballyPositioned { coordinates ->
                                        Log.i(
                                            LAYOUT_POLISH_DIAGNOSTIC_TAG,
                                            "immersive utility boundsInRoot=${coordinates.boundsInRoot()} " +
                                                "split=$interfacePanelVisible",
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }

        val navigationState = LowerScreenNavigationState(activePage, companionSection)
        if (cropEditorModel == null && shouldBlockGameplayTouch(navigationState)) {
            val blockingModifier = Modifier
                .fillMaxSize()
                .background(AdventurePadThemeTokens.colors.background)
                .pointerInput(activePage) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final).changes.forEach(PointerInputChange::consume)
                        }
                    }
                }
            Box(blockingModifier) {
                val exactPageArtwork = activeSkin.resolveAssetSlot(
                    *lowerPageBackgroundCandidates(activePage, companionSection, interfacePanelVisible),
                ) != null
                val paneTreatment = functionalPaneTreatment(
                    interfaceStyle = interfaceStyle,
                    page = activePage,
                    companionSection = companionSection,
                    hasExactArtwork = exactPageArtwork,
                )
                SkinArtwork(
                    lowerPageBackgroundCandidates(activePage, companionSection, interfacePanelVisible),
                    Modifier.fillMaxSize(),
                )
                when (activePage) {
                    LowerScreenPage.COMPANION -> Box(
                        Modifier.then(
                            if (paneTreatment == FunctionalPaneTreatment.IMMERSIVE) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .fillMaxSize(APPLICATION_PAGE_FRACTION)
                                    .align(Alignment.Center)
                                    .clip(AdventurePadThemeTokens.shapes.large)
                                    .border(
                                    1.dp,
                                    AdventurePadThemeTokens.colors.outline,
                                    AdventurePadThemeTokens.shapes.large,
                                    )
                            },
                        ),
                    ) {
                        CompanionScreen(
                            gameId = currentGameId,
                            persistedNotes = persistedNotes,
                            walkthrough = walkthrough,
                            selectedSection = companionSection,
                            immersive = paneTreatment == FunctionalPaneTreatment.IMMERSIVE,
                            statistics = CompanionStatistics(
                                targetId = currentGameId,
                                displayMode = displayMode,
                                splitProfileConfigured = mirrorSourceGeometry
                                    ?.let(cropProfile::isCompatibleWith) == true,
                                notesPresent = persistedNotes.isNotBlank(),
                            ),
                            onNotesChanged = onNotesChanged,
                            onSaveWalkthroughToNotes = onSaveWalkthroughToNotes,
                            onWalkthroughImported = onWalkthroughImported,
                            onWalkthroughRemoved = onWalkthroughRemoved,
                            onWalkthroughPositionChanged = onWalkthroughPositionChanged,
                            onWalkthroughPreferencesChanged = onWalkthroughPreferencesChanged,
                            onSectionSelected = {
                                navigate(LowerScreenNavigationAction.SelectCompanionSection(it))
                            },
                            onBack = { navigate(LowerScreenNavigationAction.BackCompanion) },
                            onClose = { navigate(LowerScreenNavigationAction.ClosePage) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    LowerScreenPage.SETTINGS -> {
                        Box(
                            Modifier.fillMaxSize()
                                .background(AdventurePadThemeTokens.colors.background),
                        ) {
                            PointerSpeedSettings(
                                pointerSpeed = pointerSpeed,
                                onPointerSpeedSelected = onPointerSpeedSelected,
                                activeColourTheme = activeColourTheme,
                                onColourThemeSelected = onColourThemeSelected,
                                interfaceStyle = interfaceStyle,
                                immersiveAvailable = immersiveAvailable,
                                onInterfaceStyleSelected = onInterfaceStyleSelected,
                                activeSkin = activeSkin,
                                installedSkins = installedSkins,
                                onSkinSelected = onSkinSelected,
                                onAddSkin = onAddSkin,
                                onRemoveSkin = onRemoveSkin,
                                displayModePreferences = displayModePreferences,
                                displayMode = displayMode,
                                upperExpansionSupported = upperExpansionSupported,
                                onPreferredDisplayModeChanged = onPreferredDisplayModeChanged,
                                cropConfigurationEnabled = mirrorSourceGeometry?.isSupported == true,
                                onConfigureCrop = {
                                    navigate(LowerScreenNavigationAction.ClosePage)
                                    onOpenCropEditor()
                                },
                                diagnosticsVisible = diagnosticsVisible,
                                onDiagnosticsVisibleChanged = { diagnosticsVisible = it },
                                diagnostics = mouseDiagnostics,
                                displayId = displayId,
                                connectionDiagnostics = connectionDiagnostics,
                                mirrorCropDiagnostics = MirrorCropDiagnostics(
                                    geometry = mirrorSourceGeometry,
                                    profile = cropProfile,
                                    acknowledgement = lastCropAcknowledgement,
                                    displayMode = displayMode,
                                    displayModePreferences = displayModePreferences,
                                    upperAcknowledgement = lastUpperPresentationAcknowledgement,
                                ),
                                onRestoreTrackpad = onRestoreTrackpad,
                                onRestoreBothScreens = onRestoreBothScreens,
                                onClose = { navigate(LowerScreenNavigationAction.ClosePage) },
                                modifier = Modifier
                                    .fillMaxSize(APPLICATION_PAGE_FRACTION)
                                    .align(Alignment.Center)
                                    .clip(AdventurePadThemeTokens.shapes.large)
                                    .border(
                                        1.dp,
                                        AdventurePadThemeTokens.colors.outline,
                                        AdventurePadThemeTokens.shapes.large,
                                    ),
                            )
                        }
                    }
                    LowerScreenPage.GAMEPLAY -> Unit
                }
            }
        }
    }
}

@Composable
private fun LauncherTrackpadSurface(
    touchState: MutableState<TouchState>,
    gestureResetGeneration: Int,
    touchProvenance: TrackpadTouchProvenance,
    pointerSpeed: PointerSpeed,
    onGesture: (TrackpadGesture) -> Unit,
    onGestureDiagnostic: (String) -> Unit,
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val previousVisibleWidth = (
            maxWidth - AdventurePadDesign.launcherTrackpadHorizontalPadding * 2
        ).coerceAtLeast(0.dp)
        val previousVisibleHeight = (
            maxHeight - AdventurePadDesign.launcherPreviousUtilityBarHeight -
                AdventurePadDesign.launcherTrackpadTopPadding -
                AdventurePadDesign.launcherTrackpadBottomPadding
        ).coerceAtLeast(0.dp)
        val visibleWidth = previousVisibleWidth * AdventurePadDesign.launcherTrackpadLinearScale
        val visibleHeight = previousVisibleHeight * AdventurePadDesign.launcherTrackpadLinearScale
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.layout.Spacer(
                Modifier.height(AdventurePadDesign.launcherAssemblyTopOffset),
            )
            TouchSurface(
                touchState = touchState,
                gestureResetGeneration = gestureResetGeneration,
                touchProvenance = touchProvenance,
                pointerSpeed = pointerSpeed,
                onGesture = onGesture,
                onGestureDiagnostic = onGestureDiagnostic,
                onButtonDown = onButtonDown,
                onButtonUp = onButtonUp,
                immersive = false,
                launcherPresentation = true,
                modifier = Modifier
                    .width(
                        visibleWidth + AdventurePadDesign.launcherTrackpadHorizontalPadding * 2,
                    )
                    .height(
                        visibleHeight + AdventurePadDesign.launcherTrackpadTopPadding +
                            AdventurePadDesign.launcherTrackpadBottomPadding,
                    )
                    .padding(
                        start = AdventurePadDesign.launcherTrackpadHorizontalPadding,
                        top = AdventurePadDesign.launcherTrackpadTopPadding,
                        end = AdventurePadDesign.launcherTrackpadHorizontalPadding,
                        bottom = AdventurePadDesign.launcherTrackpadBottomPadding,
                    ),
            )
            Image(
                painter = painterResource(R.drawable.adventurepad_logo),
                contentDescription = "AdventurePad",
                modifier = Modifier
                    .padding(top = AdventurePadDesign.launcherLogoTopSpacing)
                    .fillMaxWidth(0.45f)
                    .widthIn(max = AdventurePadDesign.launcherLogoWidth)
                    .aspectRatio(AdventurePadDesign.launcherLogoAspectRatio),
            )
            androidx.compose.foundation.layout.Spacer(
                Modifier.height(AdventurePadDesign.launcherLogoBottomSpacing),
            )
        }
    }
}

@Composable
private fun MirrorPrototypePanel(
    status: MirrorOutputStatus,
    panelHeight: androidx.compose.ui.unit.Dp,
    crop: NormalizedCrop,
    geometry: MirrorSourceGeometry?,
    cursorState: MirrorCursorState,
    cropGeneration: Long,
    displayMode: DisplayMode,
    showPanelFrame: Boolean,
    onViewAvailable: (MirrorHost) -> Unit,
    onViewDisposed: (MirrorHost) -> Unit,
) {
    val context = LocalContext.current
    val mirrorHost = remember(context) { createMirrorHost(context) }
    val panelFrameArtwork = if (showPanelFrame) {
        rememberSkinFrameArtwork(SkinSlots.PANEL_FRAME)
    } else null
    val themeColors = AdventurePadThemeTokens.colors
    val componentStyles = AdventurePadThemeTokens.components
    var panelWidth by remember { mutableIntStateOf(0) }
    var panelHeightPixels by remember { mutableIntStateOf(0) }

    DisposableEffect(mirrorHost) {
        onViewAvailable(mirrorHost)
        onDispose { onViewDisposed(mirrorHost) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(AdventurePadThemeTokens.components.mirrorBackdrop)
            .onSizeChanged {
                panelWidth = it.width
                panelHeightPixels = it.height
            }
            .onGloballyPositioned { coordinates ->
                if (showPanelFrame) {
                    val bounds = coordinates.boundsInRoot()
                    Log.i(
                        LAYOUT_POLISH_DIAGNOSTIC_TAG,
                        "split interface boundsInRoot left=${bounds.left} top=${bounds.top} " +
                            "right=${bounds.right} bottom=${bounds.bottom} " +
                            "width=${bounds.width} height=${bounds.height}",
                    )
                }
            },
    ) {
        val outerRect = PixelRect(0, 0, panelWidth, panelHeightPixels)
        val framePatches = panelFrameArtwork?.patches(panelWidth, panelHeightPixels)
        val frameOpening = framePatches?.let {
            panelFrameArtwork.contentRect(panelWidth, panelHeightPixels, it)
        }
        val interfaceRect = frameOpening
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?: outerRect
        val contentTransform = PanelContentTransform(
            content = interfaceRect,
            interfaceWidth = panelWidth,
            interfaceHeight = panelHeightPixels,
        )
        LaunchedEffect(panelFrameArtwork, panelWidth, panelHeightPixels, interfaceRect) {
            if (panelFrameArtwork != null && panelWidth > 0 && panelHeightPixels > 0) {
                Log.i(
                    LAYOUT_POLISH_DIAGNOSTIC_TAG,
                    "panel frame sourceVisible=${panelFrameArtwork.visibleBounds} " +
                        "sourceOpening=${panelFrameArtwork.contentBounds} " +
                        "displayOpening=$interfaceRect surface=${panelWidth}x$panelHeightPixels " +
                        "scale=${interfaceRect.width.toFloat() / panelWidth}," +
                        "${interfaceRect.height.toFloat() / panelHeightPixels}",
                )
            }
        }
        val panelGeometry = geometry?.let {
            lowerPanelGeometry(
                panelWidth,
                panelHeightPixels,
                crop,
                it.width,
                it.height,
                it.orientation,
            )
        }
        AndroidView(
            factory = { mirrorHost.view },
            update = { view ->
                mirrorHost.configureDirectTouch(
                    crop = crop,
                    geometry = geometry,
                    cropGeneration = cropGeneration,
                    enabled = displayMode == DisplayMode.INTERFACE &&
                        status.state == MirrorOutputState.SUPPORTED,
                )
                view.pivotX = 0f
                view.pivotY = 0f
                view.scaleX = if (panelWidth > 0) interfaceRect.width.toFloat() / panelWidth else 1f
                view.scaleY = if (panelHeightPixels > 0) {
                    interfaceRect.height.toFloat() / panelHeightPixels
                } else 1f
                view.translationX = interfaceRect.left.toFloat()
                view.translationY = interfaceRect.top.toFloat()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (panelFrameArtwork != null) {
            SkinFrameArtwork(
                artwork = panelFrameArtwork,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val cursorPoint = cursorState
            .takeIf { it.visible && it.geometryGeneration == geometry?.generation }
            ?.point
            ?.let { panelGeometry?.mapSource(it) }
            ?.let { contentTransform.interfaceToOuter(PixelPoint(it.x, it.y)) }
        if (cursorPoint != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(cursorPoint.x, cursorPoint.y)
                val radius = 7.dp.toPx()
                val gap = 2.dp.toPx()
                val stroke = 1.dp.toPx()
                drawLine(componentStyles.cropHandle, center.copy(x = center.x - radius), center.copy(x = center.x - gap), stroke + 2f)
                drawLine(componentStyles.cropHandle, center.copy(x = center.x + gap), center.copy(x = center.x + radius), stroke + 2f)
                drawLine(componentStyles.cropHandle, center.copy(y = center.y - radius), center.copy(y = center.y - gap), stroke + 2f)
                drawLine(componentStyles.cropHandle, center.copy(y = center.y + gap), center.copy(y = center.y + radius), stroke + 2f)
                drawLine(themeColors.textPrimary, center.copy(x = center.x - radius), center.copy(x = center.x - gap), stroke)
                drawLine(themeColors.textPrimary, center.copy(x = center.x + gap), center.copy(x = center.x + radius), stroke)
                drawLine(themeColors.textPrimary, center.copy(y = center.y - radius), center.copy(y = center.y - gap), stroke)
                drawLine(themeColors.textPrimary, center.copy(y = center.y + gap), center.copy(y = center.y + radius), stroke)
            }
        }
    }
}

internal fun shouldShowSplitPanelFrame(
    splitViewActive: Boolean,
    interfaceStyle: InterfaceStyle,
    hasPanelFrameAsset: Boolean,
): Boolean = splitViewActive &&
    interfaceStyle == InterfaceStyle.IMMERSIVE &&
    hasPanelFrameAsset

@Composable
private fun PointerSpeedSettings(
    pointerSpeed: PointerSpeed,
    onPointerSpeedSelected: (PointerSpeed) -> Unit,
    activeColourTheme: AdventurePadThemeDefinition,
    onColourThemeSelected: (AdventurePadThemeDefinition) -> Unit,
    interfaceStyle: InterfaceStyle,
    immersiveAvailable: Boolean,
    onInterfaceStyleSelected: (InterfaceStyle) -> Unit,
    activeSkin: ResolvedSkin,
    installedSkins: List<InstalledSkin>,
    onSkinSelected: (String?) -> Unit,
    onAddSkin: () -> Unit,
    onRemoveSkin: (String) -> Boolean,
    displayModePreferences: DisplayModePreferences,
    displayMode: DisplayMode,
    upperExpansionSupported: Boolean,
    onPreferredDisplayModeChanged: (DisplayMode) -> Unit,
    cropConfigurationEnabled: Boolean,
    onConfigureCrop: () -> Unit,
    diagnosticsVisible: Boolean,
    onDiagnosticsVisibleChanged: (Boolean) -> Unit,
    diagnostics: MouseDiagnostics,
    displayId: Int,
    connectionDiagnostics: ScummVMConnectionDiagnostics,
    mirrorCropDiagnostics: MirrorCropDiagnostics,
    onRestoreTrackpad: () -> Unit,
    onRestoreBothScreens: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeDialogVisible by rememberSaveable { mutableStateOf(false) }
    var skinDialogVisible by rememberSaveable { mutableStateOf(false) }

    AdventurePadScrollablePage(
        modifier = modifier
            .fillMaxWidth()
            .background(AdventurePadThemeTokens.colors.surface)
            .border(1.dp, AdventurePadThemeTokens.colors.outline, AdventurePadThemeTokens.shapes.large),
        contentPadding = PaddingValues(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingMd),
    ) {
        PageHeader(title = "SETTINGS", onClose = onClose)
        SettingsSectionTitle("GENERAL")
        Text(
            text = "Pointer speed · ${pointerSpeed.label}",
            color = AdventurePadThemeTokens.colors.textPrimary,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
        ) {
            PointerSpeed.entries.forEach { option ->
                SettingsOptionButton(
                    label = option.label,
                    selected = option == pointerSpeed,
                    onClick = { onPointerSpeedSelected(option) },
                    modifier = Modifier.weight(1f),
                    emphasizeSelection = true,
                    singleLine = true,
                )
            }
        }
        Text(
            text = "Default Mode",
            color = AdventurePadThemeTokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm)) {
            DisplayMode.entries.forEach { mode ->
                SettingsOptionButton(
                    label = if (mode == DisplayMode.INTERFACE) "SPLIT VIEW" else "TRACKPAD",
                    selected = displayModePreferences.preferredMode == mode,
                    onClick = { onPreferredDisplayModeChanged(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "Current mode: ${if (displayMode == DisplayMode.INTERFACE) "SPLIT VIEW" else "TRACKPAD"}",
            color = AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSectionTitle("THEMES")
        OutlinedButton(
            onClick = { themeDialogVisible = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textPrimary),
            border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Colour theme", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (interfaceStyle == InterfaceStyle.IMMERSIVE) {
                        InterfaceStyle.IMMERSIVE.displayName
                    } else {
                        activeColourTheme.displayName
                    },
                    color = AdventurePadThemeTokens.colors.textSecondary,
                )
            }
        }
        if (!immersiveAvailable) {
            Text(
                text = "Immersive is available when a game skin with immersive control artwork is applied.",
                color = AdventurePadThemeTokens.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = { skinDialogVisible = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textPrimary),
            border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Game skin", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    activeSkin.skin.takeUnless(InstalledSkin::isBuiltIn)?.manifest?.name ?: "None",
                    color = AdventurePadThemeTokens.colors.textSecondary,
                )
            }
        }
        SettingsSectionTitle("SPLIT VIEW")
        if (!upperExpansionSupported) {
            Text(
                text = "Set a valid split to enable Split View Mode.",
                color = AdventurePadThemeTokens.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = onConfigureCrop,
            enabled = cropConfigurationEnabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textPrimary),
            border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
        ) {
            Text("CONFIGURE SPLIT VIEW")
        }
        SettingsSectionTitle("INPUT")
        Text(
            text = "Tap for left click · two-finger tap for right click · double-tap and hold to drag.",
            color = AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Mode shortcuts: two-finger double tap, or L2 + R2.",
            color = AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Known issue: L2 + R2 is focus-dependent on the AYN Thor and only works while the lower display owns input focus.",
            color = AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        SettingsSectionTitle("RECOVERY")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
        ) {
            OutlinedButton(
                onClick = onRestoreTrackpad,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textPrimary),
                border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
                modifier = Modifier.weight(1f),
            ) {
                Text("RESTORE TRACKPAD")
            }
            OutlinedButton(
                onClick = onRestoreBothScreens,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textPrimary),
                border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
                modifier = Modifier.weight(1f),
            ) {
                Text("RESTORE BOTH SCREENS")
            }
        }
        SettingsSectionTitle("ADVANCED / DIAGNOSTICS")
        OutlinedButton(
            onClick = { onDiagnosticsVisibleChanged(!diagnosticsVisible) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textSecondary),
            border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
        ) {
            Text(if (diagnosticsVisible) "HIDE TECHNICAL INFORMATION" else "SHOW TECHNICAL INFORMATION")
        }
        if (diagnosticsVisible) {
            MouseDiagnosticsPanel(
                diagnostics = diagnostics,
                displayId = displayId,
                connectionDiagnostics = connectionDiagnostics,
                mirrorCropDiagnostics = mirrorCropDiagnostics,
            )
        }
    }

    if (themeDialogVisible) {
        ThemeSelectionDialog(
            activeTheme = activeColourTheme,
            interfaceStyle = interfaceStyle,
            immersiveAvailable = immersiveAvailable,
            onOptionSelected = { option ->
                if (option.immersive) {
                    onInterfaceStyleSelected(InterfaceStyle.IMMERSIVE)
                } else {
                    option.theme?.let(onColourThemeSelected)
                    onInterfaceStyleSelected(InterfaceStyle.STANDARD)
                }
                themeDialogVisible = false
            },
            onDismiss = { themeDialogVisible = false },
        )
    }

    if (skinDialogVisible) {
        SkinSelectionDialog(
            activeSkin = activeSkin,
            installedSkins = gameplaySkinChoices(installedSkins),
            onSkinSelected = {
                onSkinSelected(it)
                skinDialogVisible = false
            },
            onAddSkin = {
                skinDialogVisible = false
                onAddSkin()
            },
            onRemoveSkin = onRemoveSkin,
            onDismiss = { skinDialogVisible = false },
        )
    }
}

@Composable
private fun SkinSelectionDialog(
    activeSkin: ResolvedSkin,
    installedSkins: List<InstalledSkin>,
    onSkinSelected: (String?) -> Unit,
    onAddSkin: () -> Unit,
    onRemoveSkin: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<InstalledSkin?>(null) }
    var removalFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game skins") },
        text = {
            Column {
                Text(
                    "DEFAULT",
                    color = AdventurePadThemeTokens.colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedButton(
                    onClick = { onSkinSelected(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(selected = activeSkin.skin.isBuiltIn, onClick = null)
                    Text("No game skin", modifier = Modifier.weight(1f))
                }
                if (installedSkins.isNotEmpty()) {
                    Text(
                        "INSTALLED CUSTOM SKINS",
                        color = AdventurePadThemeTokens.colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = AdventurePadDesign.spacingSm),
                    )
                }
                installedSkins.forEach { skin ->
                    OutlinedButton(
                        onClick = { onSkinSelected(skin.manifest.id) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (skin.manifest.id == activeSkin.id) {
                                AdventurePadThemeTokens.colors.surfacePressed
                            } else {
                                Color.Transparent
                            },
                            contentColor = AdventurePadThemeTokens.colors.textPrimary,
                        ),
                        border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = skin.manifest.id == activeSkin.id,
                            onClick = null,
                        )
                        Text(skin.manifest.name, modifier = Modifier.weight(1f))
                        if (!skin.isBuiltIn) {
                            TextButton(onClick = { pendingRemoval = skin }) { Text("REMOVE") }
                        }
                    }
                }
                OutlinedButton(onClick = onAddSkin, modifier = Modifier.fillMaxWidth()) {
                    Text("ADD SKIN")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
    )
    pendingRemoval?.let { skin ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove game skin?") },
            text = { Text("Remove ${skin.manifest.name} from AdventurePad?") },
            confirmButton = {
                TextButton(onClick = {
                    if (!onRemoveSkin(skin.manifest.id)) removalFailed = true
                    pendingRemoval = null
                }) { Text("REMOVE") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("CANCEL") }
            },
        )
    }
    if (removalFailed) {
        AlertDialog(
            onDismissRequest = { removalFailed = false },
            title = { Text("Skin could not be removed") },
            text = { Text("AdventurePad could not remove this installed skin.") },
            confirmButton = {
                TextButton(onClick = { removalFailed = false }) { Text("OK") }
            },
        )
    }
}

internal data class ColourThemeSelectorOption(
    val displayName: String,
    val theme: AdventurePadThemeDefinition?,
    val immersive: Boolean,
    val enabled: Boolean,
)

internal fun colourThemeSelectorOptions(immersiveAvailable: Boolean): List<ColourThemeSelectorOption> =
    AdventurePadThemes.BuiltIns.map { theme ->
        ColourThemeSelectorOption(
            displayName = theme.displayName,
            theme = theme,
            immersive = false,
            enabled = true,
        )
    } + ColourThemeSelectorOption(
        displayName = InterfaceStyle.IMMERSIVE.displayName,
        theme = null,
        immersive = true,
        enabled = immersiveAvailable,
    )

@Composable
private fun ThemeSelectionDialog(
    activeTheme: AdventurePadThemeDefinition,
    interfaceStyle: InterfaceStyle,
    immersiveAvailable: Boolean,
    onOptionSelected: (ColourThemeSelectorOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour theme") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                colourThemeSelectorOptions(immersiveAvailable).forEach { option ->
                    OutlinedButton(
                        onClick = { onOptionSelected(option) },
                        enabled = option.enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = if (option.immersive) {
                                interfaceStyle == InterfaceStyle.IMMERSIVE
                            } else {
                                interfaceStyle != InterfaceStyle.IMMERSIVE &&
                                    option.theme?.id == activeTheme.id
                            },
                            enabled = option.enabled,
                            onClick = null,
                        )
                        Text(option.displayName, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun SettingsOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasizeSelection: Boolean = false,
    singleLine: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                AdventurePadThemeTokens.colors.surfacePressed
            } else {
                Color.Transparent
            },
            contentColor = AdventurePadThemeTokens.colors.textPrimary,
        ),
        border = BorderStroke(
            width = if (selected && emphasizeSelection) 2.dp else 1.dp,
            color = if (selected && emphasizeSelection) {
                AdventurePadThemeTokens.colors.textPrimary
            } else {
                AdventurePadThemeTokens.colors.outline
            },
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontWeight = if (emphasizeSelection) {
                if (selected) FontWeight.Bold else FontWeight.Normal
            } else {
                null
            },
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = AdventurePadThemeTokens.colors.primary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = AdventurePadDesign.spacingSm),
    )
}

@Composable
private fun MirrorCropEditor(
    model: CropEditorModel,
    cropNeedsReview: Boolean,
    savePending: Boolean,
    onModelChanged: (CropEditorModel) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onViewAvailable: (MirrorHost) -> Unit,
    onViewDisposed: (MirrorHost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mirrorHost = remember(context) { createMirrorHost(context) }
    val themeColors = AdventurePadThemeTokens.colors
    val componentStyles = AdventurePadThemeTokens.components
    var viewportHeight by remember { mutableIntStateOf(1) }
    val latestModel by rememberUpdatedState(model)

    DisposableEffect(mirrorHost) {
        onViewAvailable(mirrorHost)
        onDispose { onViewDisposed(mirrorHost) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(AdventurePadThemeTokens.colors.surface)
            .border(2.dp, AdventurePadThemeTokens.colors.outline)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "CONFIGURE SPLIT VIEW",
                color = AdventurePadThemeTokens.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            CropEditorButton("Cancel", onCancel)
            CropEditorButton(
                text = if (savePending) "Saving…" else "Save",
                onClick = onSave,
                enabled = !savePending,
            )
        }
        Text(
            "Drag the horizontal line to choose where the game ends and Split View begins.",
            color = AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(model.sourceAspectRatio)
                .background(AdventurePadThemeTokens.components.mirrorBackdrop)
                .border(3.dp, AdventurePadThemeTokens.colors.textPrimary)
                .onSizeChanged { size ->
                    viewportHeight = size.height.coerceAtLeast(1)
                }
                .pointerInput(viewportHeight) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        onModelChanged(latestModel.withSplitRatio(
                            latestModel.split.ratio + dragAmount / viewportHeight,
                        ))
                    }
                },
        ) {
            AndroidView(
                factory = { mirrorHost.view },
                update = {},
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                val splitY = size.height * model.split.ratio
                drawRect(
                    color = componentStyles.cropOverlay,
                    topLeft = Offset(0f, splitY),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - splitY),
                )
                drawLine(
                    color = themeColors.textPrimary,
                    start = Offset(0f, splitY),
                    end = Offset(size.width, splitY),
                    strokeWidth = 6f,
                )
                drawLine(
                    color = componentStyles.cropHandle,
                    start = Offset(0f, splitY + 7f),
                    end = Offset(size.width, splitY + 7f),
                    strokeWidth = 2f,
                )
            }
            Text(
                text = "GAME",
                color = AdventurePadThemeTokens.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(AdventurePadDesign.spacingSm),
            )
            Text(
                text = "SPLIT VIEW",
                color = AdventurePadThemeTokens.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(AdventurePadDesign.spacingSm),
            )
        }
        if (cropNeedsReview) {
            Text("Crop needs review", color = AdventurePadThemeTokens.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Split: ${(model.split.ratio * 100f).formatOneDecimal()}% • " +
                "Split View: ${((1f - model.split.ratio) * 100f).formatOneDecimal()}%",
            color = AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CropEditorButton("RESET", { onModelChanged(model.reset()) }, Modifier.weight(1f))
            CropEditorButton("↑", { onModelChanged(model.nudgeUp()) }, Modifier.weight(1f))
            CropEditorButton("↓", { onModelChanged(model.nudgeDown()) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CropEditorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdventurePadThemeTokens.colors.textPrimary),
        border = BorderStroke(1.dp, AdventurePadThemeTokens.colors.outline),
        modifier = modifier,
    ) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Float.formatOneDecimal(): String = String.format(Locale.ROOT, "%.1f", this)

@Composable
private fun GameplayUtilityBar(
    immersive: Boolean,
    launcherPresentation: Boolean,
    onOpenCompanion: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (launcherPresentation) {
                    AdventurePadDesign.launcherUtilityBarHorizontalPadding
                } else AdventurePadDesign.spacingMd,
                top = if (launcherPresentation) {
                    AdventurePadDesign.launcherUtilityBarTopPadding
                } else AdventurePadDesign.spacingXs,
                end = if (launcherPresentation) {
                    AdventurePadDesign.launcherUtilityBarHorizontalPadding
                } else AdventurePadDesign.spacingMd,
                bottom = if (launcherPresentation) {
                    AdventurePadDesign.launcherUtilityBarBottomPadding
                } else AdventurePadDesign.spacingXs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatefulSkinUtilityButton(
            button = SkinnableButton.COMPANION,
            onClick = onOpenCompanion,
            label = GameplayUtilityAction.COMPANION.displayLabel(),
            immersive = immersive,
            launcherPresentation = launcherPresentation,
            modifier = if (immersive) {
                Modifier.offset(y = -IMMERSIVE_UTILITY_UPWARD_OFFSET_DP.dp)
            } else {
                Modifier
            },
        )
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        StatefulSkinUtilityButton(
            button = SkinnableButton.SETTINGS,
            onClick = onOpenSettings,
            label = GameplayUtilityAction.SETTINGS.displayLabel(),
            immersive = immersive,
            launcherPresentation = launcherPresentation,
            modifier = if (immersive) {
                Modifier.offset(y = -IMMERSIVE_UTILITY_UPWARD_OFFSET_DP.dp)
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun StatefulSkinUtilityButton(
    button: SkinnableButton,
    label: String,
    immersive: Boolean,
    launcherPresentation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    var measuredSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val shape = AdventurePadThemeTokens.shapes.medium
    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                if (immersive) {
                    Log.i(
                        LAYOUT_POLISH_DIAGNOSTIC_TAG,
                        "immersive utility control=$label boundsInRoot=${coordinates.boundsInRoot()} " +
                            "upwardOffsetDp=$IMMERSIVE_UTILITY_UPWARD_OFFSET_DP",
                    )
                }
            }
            .then(
            when {
                immersive -> Modifier
                launcherPresentation -> Modifier
                    .background(
                        if (pressed) AdventurePadThemeTokens.components.launcherAccentDark
                        else AdventurePadThemeTokens.components.launcherContent,
                        shape,
                    )
                    .border(
                        AdventurePadDesign.launcherControlBorderWidth,
                        AdventurePadThemeTokens.components.launcherAccentDark,
                        shape,
                    )
                else -> Modifier
                    .background(AdventurePadThemeTokens.colors.surfaceRaised, shape)
                    .border(1.dp, AdventurePadThemeTokens.colors.outline, shape)
            },
        ),
    ) {
        if (measuredSize != androidx.compose.ui.unit.IntSize.Zero) {
            val artworkSize = with(density) {
                androidx.compose.ui.unit.DpSize(measuredSize.width.toDp(), measuredSize.height.toDp())
            }
            SkinArtwork(
                button.artworkCandidates(pressed),
                Modifier.size(artworkSize.width, artworkSize.height),
            )
        }
        val buttonModifier = Modifier
            .heightIn(
                min = if (launcherPresentation) {
                    AdventurePadDesign.launcherControlMinimumHeight
                } else AdventurePadDesign.utilityTouchTarget,
            )
            .widthIn(
                min = if (launcherPresentation) {
                    AdventurePadDesign.launcherControlMinimumWidth
                } else 132.dp,
            )
            .onSizeChanged { measuredSize = it }
            .semantics { contentDescription = label }
        if (immersive) {
            Box(
                buttonModifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            )
        } else {
            TextButton(
                onClick = onClick,
                interactionSource = interactionSource,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (launcherPresentation) {
                        if (pressed) AdventurePadThemeTokens.components.launcherContent
                        else AdventurePadThemeTokens.components.launcherInk
                    } else AdventurePadThemeTokens.colors.textSecondary,
                ),
                modifier = buttonModifier,
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    fontWeight = if (launcherPresentation) FontWeight.Bold else null,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TouchSurface(
    touchState: MutableState<TouchState>,
    gestureResetGeneration: Int,
    touchProvenance: TrackpadTouchProvenance,
    pointerSpeed: PointerSpeed,
    onGesture: (TrackpadGesture) -> Unit,
    onGestureDiagnostic: (String) -> Unit,
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
    immersive: Boolean,
    launcherPresentation: Boolean,
    overlaySizingHeightReduction: Dp = 0.dp,
    overlayHeightScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val currentState by touchState
    val currentPointerSpeed by rememberUpdatedState(pointerSpeed)
    val viewConfiguration = LocalViewConfiguration.current
    val androidViewConfiguration = ViewConfiguration.get(LocalContext.current)
    val density = LocalDensity.current
    val overlaySizingHeightReductionPx = with(density) {
        overlaySizingHeightReduction.toPx()
    }
    val currentButtonDown by rememberUpdatedState(onButtonDown)
    val currentButtonUp by rememberUpdatedState(onButtonUp)
    val themeColors = AdventurePadThemeTokens.colors
    val componentStyles = AdventurePadThemeTokens.components
    val activeSkin = AdventurePadSkinTokens.current
    val leftButtonAspectRatio = if (immersive) {
        activeSkin?.assetAspectRatio(*SkinnableButton.LMB.artworkCandidates(pressed = false))
    } else null
    val rightButtonAspectRatio = if (immersive) {
        activeSkin?.assetAspectRatio(*SkinnableButton.RMB.artworkCandidates(pressed = false))
    } else null
    var leftButtonPressed by remember { mutableStateOf(false) }
    var rightButtonPressed by remember { mutableStateOf(false) }

    LaunchedEffect(themeColors.background, componentStyles.trackpadBackground) {
        Log.i(
            THEME_DIAGNOSTIC_TAG,
            "TouchSurface consumed outer=${themeColors.background.diagnosticHex()} " +
                "trackpad=${componentStyles.trackpadBackground.diagnosticHex()}",
        )
    }

    fun dispatchOverlayTransition(transition: TrackpadOverlayButtonTransition?) {
        transition ?: return
        when (transition.button) {
            TrackpadOverlayButton.LEFT -> leftButtonPressed = transition.isDown
            TrackpadOverlayButton.RIGHT -> rightButtonPressed = transition.isDown
        }
        transition.dispatch(currentButtonDown, currentButtonUp)
    }

    Box(
        modifier = modifier
            .clip(AdventurePadThemeTokens.shapes.large)
            .background(
                if (immersive) Color.Transparent
                else AdventurePadThemeTokens.components.trackpadBackground,
            )
            .then(
                if (immersive) {
                    Modifier
                } else {
                    Modifier.border(
                        width = if (launcherPresentation) {
                            AdventurePadDesign.launcherControlBorderWidth
                        } else 1.dp,
                        color = if (launcherPresentation) {
                            AdventurePadThemeTokens.components.launcherAccentDark
                        } else AdventurePadThemeTokens.colors.outline,
                        shape = AdventurePadThemeTokens.shapes.large,
                    )
                },
            )
            .onSizeChanged { surfaceSize ->
                touchState.value = touchState.value.withSurfaceSize(
                    surfaceWidth = surfaceSize.width.toFloat(),
                    surfaceHeight = surfaceSize.height.toFloat(),
                )
            }
            .pointerInput(
                viewConfiguration.touchSlop,
                androidViewConfiguration.scaledDoubleTapSlop,
                gestureResetGeneration,
            ) {
                touchState.value = touchState.value.withTransientInputCleared()
                onGestureDiagnostic(
                    "LIFECYCLE GESTURE RESET APPLIED: GENERATION $gestureResetGeneration",
                )
                Log.i(
                    RAW_TOUCH_TAG,
                    "COMPOSE POINTER INPUT STARTED generation=$gestureResetGeneration",
                )
                coroutineScope {
                    val holdTimeoutMillis = ViewConfiguration.getLongPressTimeout().toLong()
                    val gestureTracker = TrackpadGestureTracker(
                        singleTapMaximumDurationMillis = ViewConfiguration.getTapTimeout().toLong(),
                        twoFingerTapMaximumDurationMillis = TwoFingerTapMaximumDurationMillis,
                        doubleTapTimeoutMillis = ViewConfiguration.getDoubleTapTimeout().toLong(),
                        holdTimeoutMillis = holdTimeoutMillis,
                        touchSlop = viewConfiguration.touchSlop,
                        doubleTapSlop = androidViewConfiguration.scaledDoubleTapSlop.toFloat(),
                    )
                    val composeTouchDiagnostics = ComposeTouchDiagnostics(
                        resetGeneration = gestureResetGeneration,
                        touchSlop = viewConfiguration.touchSlop,
                    )
                    var holdJob: Job? = null
                    var activeSequenceToken: TouchSequenceToken? = null
                    val inputOwnership = TrackpadInputOwnership()
                    var buttonChordActive = false
                    val pointerInputScope = this
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                composeTouchDiagnostics.record(event)
                                val motionEvent = event.motionEvent
                                val overlayGeometry = calculateTrackpadOverlayGeometry(
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                    sizingHeight = (size.height - overlaySizingHeightReductionPx)
                                        .coerceAtLeast(0f),
                                    minimumHeight = with(density) {
                                        AdventurePadDesign.trackpadOverlayMinimumHeight.toPx()
                                    },
                                    maximumHeight = with(density) {
                                        AdventurePadDesign.trackpadOverlayMaximumHeight.toPx()
                                    },
                                    leftAspectRatio = leftButtonAspectRatio,
                                    rightAspectRatio = rightButtonAspectRatio,
                                    sizeScale = if (immersive) IMMERSIVE_MOUSE_BUTTON_SCALE else 1f,
                                    heightScale = overlayHeightScale,
                                )
                                event.changes
                                    .filter { it.pressed && !it.previousPressed }
                                    .forEach { pointer ->
                                        overlayGeometry?.let { geometry ->
                                            dispatchOverlayTransition(inputOwnership.begin(
                                                pointerId = pointer.id.value,
                                                position = pointer.position,
                                                geometry = geometry,
                                            ))
                                        }
                                    }
                                val trackpadPointerIds = inputOwnership.trackpadPointerIds()
                                val trackpadChanges = event.changes.filter {
                                    it.id.value in trackpadPointerIds
                                }
                                val trackpadPressedCount = trackpadChanges.count { it.pressed }
                                val trackpadEventType = when {
                                    trackpadChanges.any { it.pressed && !it.previousPressed } ->
                                        PointerEventType.Press
                                    trackpadChanges.any { !it.pressed && it.previousPressed } ->
                                        PointerEventType.Release
                                    event.type == PointerEventType.Move -> PointerEventType.Move
                                    event.type == PointerEventType.Unknown -> PointerEventType.Unknown
                                    else -> null
                                }
                                if (inputOwnership.routesHeldLeftTrackpadMovement() &&
                                    trackpadPressedCount > 0
                                ) {
                                    buttonChordActive = true
                                }
                                if (buttonChordActive) {
                                    if (trackpadEventType != null && trackpadChanges.isNotEmpty()) {
                                        touchState.value = handlePointerEvent(
                                            eventType = trackpadEventType,
                                            changes = trackpadChanges,
                                            previousState = touchState.value,
                                            surfaceWidth = size.width.toFloat(),
                                            surfaceHeight = size.height.toFloat(),
                                            allowMovement = true,
                                            updateMovementBaseline = false,
                                            resetMovementBaseline = false,
                                            pointerSpeed = currentPointerSpeed,
                                        )
                                    }
                                    event.changes
                                        .filter { !it.pressed && it.previousPressed }
                                        .forEach { pointer ->
                                            dispatchOverlayTransition(inputOwnership.finish(pointer.id.value))
                                        }
                                    if (inputOwnership.isEmpty()) buttonChordActive = false
                                    event.changes.forEach(PointerInputChange::consume)
                                    continue
                                }
                                val pressedCount = trackpadPressedCount
                                if (trackpadEventType == PointerEventType.Press && pressedCount == 1 &&
                                    activeSequenceToken == null &&
                                    !inputOwnership.isButtonOwnedSequence()
                                ) {
                                    val pointer = trackpadChanges.first { it.pressed }
                                    if (motionEvent?.actionMasked == MotionEvent.ACTION_DOWN) {
                                        activeSequenceToken = touchProvenance.claimComposeDown(
                                            generation = gestureResetGeneration,
                                            downTimeMillis = motionEvent?.downTime,
                                            pointerId = motionEvent?.let {
                                                it.getPointerId(it.actionIndex)
                                            } ?: pointer.id.value.toInt(),
                                        )
                                    }
                                }
                                val invalidSequenceDown =
                                    trackpadEventType == PointerEventType.Press &&
                                        pressedCount == 1 &&
                                        activeSequenceToken == null &&
                                        !inputOwnership.isButtonOwnedSequence()
                                val terminalRelease = trackpadEventType == PointerEventType.Release &&
                                    pressedCount == 0
                                val platformCancel =
                                    motionEvent?.actionMasked == MotionEvent.ACTION_CANCEL
                                val provenanceTerminal = terminalRelease || platformCancel
                                if (inputOwnership.isButtonOwnedSequence()) {
                                    event.changes
                                        .filter { !it.pressed && it.previousPressed }
                                        .forEach { pointer ->
                                            dispatchOverlayTransition(inputOwnership.finish(pointer.id.value))
                                        }
                                    event.changes.forEach(PointerInputChange::consume)
                                    continue
                                }
                                if (trackpadEventType == null) {
                                    event.changes
                                        .filter { !it.pressed && it.previousPressed }
                                        .forEach { dispatchOverlayTransition(inputOwnership.finish(it.id.value)) }
                                    event.changes.forEach(PointerInputChange::consume)
                                    continue
                                }
                                val releaseVerdict = if (provenanceTerminal) {
                                    touchProvenance.validateComposeRelease(
                                        token = activeSequenceToken,
                                        generation = gestureResetGeneration,
                                        composeBackedByPlatformUp =
                                            motionEvent?.actionMasked == MotionEvent.ACTION_UP,
                                        composeDownTimeMillis = motionEvent?.downTime,
                                        composePointerId = motionEvent?.takeIf {
                                            it.actionMasked == MotionEvent.ACTION_UP
                                        }?.let { it.getPointerId(it.actionIndex) },
                                        allowAdditionalPointers =
                                            gestureTracker.isTwoFingerGesturePending(),
                                    )
                                } else {
                                    null
                                }
                                val gestureUpdate = if (invalidSequenceDown) {
                                    gestureTracker.invalidateFromProvenance(
                                        TouchReleaseVerdict.rejected(
                                            sequenceId = null,
                                            generation = gestureResetGeneration,
                                            reason = TapRejectionReason
                                                .NO_GENUINE_PLATFORM_ACTION_DOWN,
                                        ),
                                    )
                                } else if (platformCancel) {
                                    gestureTracker.invalidateFromProvenance(
                                        checkNotNull(releaseVerdict),
                                    )
                                } else {
                                    gestureTracker.handle(event, releaseVerdict, trackpadChanges)
                                }
                                if (gestureUpdate.cancelHold) {
                                    val timerWasPending = holdJob != null
                                    holdJob?.cancel()
                                    holdJob = null
                                    if (timerWasPending) {
                                        onGestureDiagnostic("PENDING HOLD TIMER CANCELLED")
                                    }
                                }
                                if (gestureUpdate.scheduleHold) {
                                    holdJob?.cancel()
                                    val scheduledToken = activeSequenceToken
                                    holdJob = pointerInputScope.launch {
                                        delay(holdTimeoutMillis)
                                        holdJob = null
                                        if (touchProvenance.isLive(
                                                scheduledToken,
                                                gestureResetGeneration,
                                            )
                                        ) {
                                            gestureTracker.handleHoldTimeout()?.let(onGesture)
                                        }
                                    }
                                }
                                gestureUpdate.diagnostics.forEach(onGestureDiagnostic)
                                gestureUpdate.gesture?.let(onGesture)
                                gestureUpdate.scrollDeltaY?.let {
                                    CursorDeltaCoordinator.publishVerticalScroll(it)
                                }
                                if (provenanceTerminal) {
                                    touchProvenance.complete(
                                        activeSequenceToken,
                                        gestureResetGeneration,
                                    )
                                    activeSequenceToken = null
                                }
                                val nextState = handlePointerEvent(
                                    eventType = trackpadEventType ?: event.type,
                                    changes = trackpadChanges,
                                    previousState = touchState.value,
                                    surfaceWidth = size.width.toFloat(),
                                    surfaceHeight = size.height.toFloat(),
                                    allowMovement = gestureUpdate.allowMovement,
                                    updateMovementBaseline = gestureUpdate.updateMovementBaseline,
                                    resetMovementBaseline = gestureUpdate.resetMovementBaseline,
                                    pointerSpeed = currentPointerSpeed,
                                )
                                touchState.value = nextState
                                event.changes
                                    .filter { !it.pressed && it.previousPressed }
                                    .forEach { dispatchOverlayTransition(inputOwnership.finish(it.id.value)) }
                                event.changes.forEach(PointerInputChange::consume)
                            }
                        }
                    } finally {
                        touchProvenance.invalidateCoroutine(
                            activeSequenceToken,
                            gestureResetGeneration,
                        )
                        val coroutineCancellationUpdate = activeSequenceToken?.let { token ->
                            val verdict = touchProvenance.validateComposeRelease(
                                token = token,
                                generation = gestureResetGeneration,
                                composeBackedByPlatformUp = false,
                                composeDownTimeMillis = null,
                                composePointerId = null,
                                allowAdditionalPointers = false,
                            )
                            gestureTracker.invalidateFromProvenance(verdict)
                        }
                        composeTouchDiagnostics.recordCoroutineCancellation()
                        Log.i(
                            RAW_TOUCH_TAG,
                            "COMPOSE POINTER INPUT CANCELLED/RESTARTED " +
                                "generation=$gestureResetGeneration",
                        )
                        val timerWasPending = holdJob != null
                        holdJob?.cancel()
                        holdJob = null
                        inputOwnership.finishAll().forEach(::dispatchOverlayTransition)
                        if (timerWasPending) {
                            onGestureDiagnostic("PENDING HOLD TIMER CANCELLED: LIFECYCLE RESET")
                        }
                        coroutineCancellationUpdate?.diagnostics?.forEach(onGestureDiagnostic)
                        if (coroutineCancellationUpdate != null) {
                            coroutineCancellationUpdate.gesture?.let(onGesture)
                            touchProvenance.complete(
                                activeSequenceToken,
                                gestureResetGeneration,
                            )
                        } else {
                            gestureTracker.cancelActiveGesture()?.let(onGesture)
                        }
                    }
                }
            },
    ) {
        SkinArtwork(SkinSlots.TRACKPAD_SURFACE, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val overlayGeometry = calculateTrackpadOverlayGeometry(
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat(),
                sizingHeight = (constraints.maxHeight - overlaySizingHeightReductionPx)
                    .coerceAtLeast(0f),
                minimumHeight = with(density) {
                    AdventurePadDesign.trackpadOverlayMinimumHeight.toPx()
                },
                maximumHeight = with(density) {
                    AdventurePadDesign.trackpadOverlayMaximumHeight.toPx()
                },
                leftAspectRatio = leftButtonAspectRatio,
                rightAspectRatio = rightButtonAspectRatio,
                sizeScale = if (immersive) IMMERSIVE_MOUSE_BUTTON_SCALE else 1f,
                heightScale = overlayHeightScale,
            )
            val leftButtonSize = overlayGeometry?.left?.let { bounds ->
                with(density) { androidx.compose.ui.unit.DpSize(bounds.width.toDp(), bounds.height.toDp()) }
            }
            val rightButtonSize = overlayGeometry?.right?.let { bounds ->
                with(density) { androidx.compose.ui.unit.DpSize(bounds.width.toDp(), bounds.height.toDp()) }
            }
            LaunchedEffect(
                constraints.maxWidth,
                constraints.maxHeight,
                immersive,
                leftButtonAspectRatio,
                rightButtonAspectRatio,
            ) {
                Log.i(
                    LAYOUT_POLISH_DIAGNOSTIC_TAG,
                    "TouchSurface=${constraints.maxWidth}x${constraints.maxHeight} " +
                        "immersive=$immersive intrinsicButtons=${immersive && leftButtonAspectRatio != null && rightButtonAspectRatio != null} " +
                        "leftAspect=$leftButtonAspectRatio rightAspect=$rightButtonAspectRatio " +
                        "left=${overlayGeometry?.left} right=${overlayGeometry?.right}",
                )
            }
            SkinArtwork(
                SkinnableButton.LMB.artworkCandidates(leftButtonPressed),
                Modifier.align(Alignment.BottomStart)
                    .then(leftButtonSize?.let { Modifier.size(it) } ?: Modifier),
                preserveIntrinsicAspectRatio = immersive,
            )
            SkinArtwork(
                SkinnableButton.RMB.artworkCandidates(rightButtonPressed),
                Modifier.align(Alignment.BottomEnd)
                    .then(rightButtonSize?.let { Modifier.size(it) } ?: Modifier),
                preserveIntrinsicAspectRatio = immersive,
            )
        }
        if (!immersive) Canvas(modifier = Modifier.fillMaxSize()) {
            val overlayGeometry = calculateTrackpadOverlayGeometry(
                width = size.width,
                height = size.height,
                minimumHeight = AdventurePadDesign.trackpadOverlayMinimumHeight.toPx(),
                maximumHeight = AdventurePadDesign.trackpadOverlayMaximumHeight.toPx(),
                heightScale = overlayHeightScale,
            )
            overlayGeometry?.let { geometry ->
                val leftOverlayTint = when {
                    launcherPresentation && leftButtonPressed -> componentStyles.launcherAccentDark
                    leftButtonPressed -> themeColors.surfacePressed
                    else -> componentStyles.trackpadOverlayTint
                }
                val rightOverlayTint = when {
                    launcherPresentation && rightButtonPressed -> componentStyles.launcherAccentDark
                    rightButtonPressed -> themeColors.surfacePressed
                    else -> componentStyles.trackpadOverlayTint
                }
                val separator = componentStyles.trackpadOverlaySeparator
                val separatorWidth = if (launcherPresentation) {
                    AdventurePadDesign.launcherTrackpadSeparatorWidth.toPx()
                } else 1.dp.toPx()
                val cornerRadius = CornerRadius(
                    AdventurePadDesign.launcherMouseButtonCornerRadius.toPx(),
                )
                drawRoundRect(
                    color = leftOverlayTint,
                    topLeft = geometry.left.topLeft,
                    size = geometry.left.size,
                    cornerRadius = cornerRadius,
                )
                drawRoundRect(
                    color = rightOverlayTint,
                    topLeft = geometry.right.topLeft,
                    size = geometry.right.size,
                    cornerRadius = cornerRadius,
                )
                drawRoundRect(
                    color = separator,
                    topLeft = geometry.left.topLeft,
                    size = geometry.left.size,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = separatorWidth),
                )
                drawRoundRect(
                    color = separator,
                    topLeft = geometry.right.topLeft,
                    size = geometry.right.size,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = separatorWidth),
                )
            }
            if (!launcherPresentation) {
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
                    componentStyles.trackpadMarkerOutline,
                    radius = radius + MarkerOutlineWidth.toPx(),
                    center = markerCenter,
                )
                drawCircle(componentStyles.trackpadMarker, radius = radius, center = markerCenter)
            }
        }
        if (!immersive) Text(
            text = if (launcherPresentation) "LMB" else "Left",
            color = if (launcherPresentation) componentStyles.launcherAccent
            else AdventurePadThemeTokens.colors.textSecondary,
            fontWeight = if (launcherPresentation) FontWeight.Bold else FontWeight.Medium,
            style = if (launcherPresentation) {
                MaterialTheme.typography.titleMedium
            } else MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = AdventurePadDesign.spacingLg,
                    bottom = AdventurePadDesign.spacingMd,
                ),
        )
        if (!immersive) Text(
            text = if (launcherPresentation) "RMB" else "Right",
            color = if (launcherPresentation) componentStyles.launcherAccent
            else AdventurePadThemeTokens.colors.textSecondary,
            fontWeight = if (launcherPresentation) FontWeight.Bold else FontWeight.Medium,
            style = if (launcherPresentation) {
                MaterialTheme.typography.titleMedium
            } else MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = AdventurePadDesign.spacingLg,
                    bottom = AdventurePadDesign.spacingMd,
                ),
        )
    }
}

private fun TrackpadOverlayButtonTransition.dispatch(
    onButtonDown: (ScummVMMouseButton) -> Unit,
    onButtonUp: (ScummVMMouseButton) -> Unit,
) {
    val mouseButton = when (button) {
        TrackpadOverlayButton.LEFT -> ScummVMMouseButton.LEFT
        TrackpadOverlayButton.RIGHT -> ScummVMMouseButton.RIGHT
    }
    if (isDown) onButtonDown(mouseButton) else onButtonUp(mouseButton)
}

private class RawTouchDiagnostics {
    private var sequenceIndex = 0
    private var activeSequence: Int? = null
    private var trackedPointerId = MotionEvent.INVALID_POINTER_ID
    private var initialX = 0f
    private var initialY = 0f
    private var maximumDisplacement = 0f
    private var firstMoveLogged = false
    private var slopCrossingLogged = false
    private var startedWithoutDown = false

    fun reset(resetGeneration: Int, reason: String) {
        sequenceIndex = 0
        activeSequence = null
        trackedPointerId = MotionEvent.INVALID_POINTER_ID
        maximumDisplacement = 0f
        firstMoveLogged = false
        slopCrossingLogged = false
        startedWithoutDown = false
        Log.i(
            RAW_TOUCH_TAG,
            "PLATFORM DIAGNOSTICS RESET generation=$resetGeneration reason=$reason",
        )
    }

    fun recordPlatformEvent(event: MotionEvent, resetGeneration: Int, touchSlop: Float) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            sequenceIndex++
            activeSequence = sequenceIndex
            trackedPointerId = event.getPointerId(event.actionIndex)
            initialX = event.getX(event.actionIndex)
            initialY = event.getY(event.actionIndex)
            maximumDisplacement = 0f
            firstMoveLogged = false
            slopCrossingLogged = false
            startedWithoutDown = false
        } else if (activeSequence == null && sequenceIndex < RAW_SEQUENCE_LIMIT) {
            sequenceIndex++
            activeSequence = sequenceIndex
            val fallbackIndex = event.actionIndex.takeIf { it in 0 until event.pointerCount } ?: 0
            trackedPointerId = if (event.pointerCount > 0) {
                event.getPointerId(fallbackIndex)
            } else {
                MotionEvent.INVALID_POINTER_ID
            }
            initialX = if (event.pointerCount > 0) event.getX(fallbackIndex) else 0f
            initialY = if (event.pointerCount > 0) event.getY(fallbackIndex) else 0f
            maximumDisplacement = 0f
            firstMoveLogged = false
            slopCrossingLogged = false
            startedWithoutDown = true
        }

        val sequence = activeSequence ?: return
        if (sequence > RAW_SEQUENCE_LIMIT) return

        val pointerIndex = event.findPointerIndex(trackedPointerId)
        val trackedX = pointerIndex.takeIf { it >= 0 }?.let(event::getX)
        val trackedY = pointerIndex.takeIf { it >= 0 }?.let(event::getY)
        val displacement = if (trackedX != null && trackedY != null) {
            sqrt(
                (trackedX - initialX) * (trackedX - initialX) +
                    (trackedY - initialY) * (trackedY - initialY),
            )
        } else {
            maximumDisplacement
        }
        maximumDisplacement = maxOf(maximumDisplacement, displacement)
        val crossedSlop = maximumDisplacement > touchSlop
        val shouldLog = event.actionMasked != MotionEvent.ACTION_MOVE ||
            !firstMoveLogged ||
            (crossedSlop && !slopCrossingLogged)
        if (shouldLog) {
            Log.i(
                RAW_TOUCH_TAG,
                "PLATFORM sequence=$sequence generation=$resetGeneration " +
                    "startedWithoutDown=$startedWithoutDown " +
                    event.describeRawTouch(
                        trackedPointerId = trackedPointerId,
                        displacement = maximumDisplacement,
                        origin = "GENUINE_PLATFORM_DISPATCH",
                    ),
            )
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) firstMoveLogged = true
        if (crossedSlop) slopCrossingLogged = true

        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.i(
                RAW_TOUCH_TAG,
                "PLATFORM sequence=$sequence terminal=${MotionEvent.actionToString(event.action)} " +
                    "duration=${event.eventTime - event.downTime}ms " +
                    "totalDisplacement=${String.format(Locale.US, "%.2f", maximumDisplacement)} " +
                    "genuinePlatformEvent=true generation=$resetGeneration",
            )
            activeSequence = null
        }
    }
}

private class ComposeTouchDiagnostics(
    private val resetGeneration: Int,
    private val touchSlop: Float,
) {
    private var sequenceIndex = 0
    private var activeSequence: Int? = null
    private var trackedPointerId: PointerId? = null
    private var initialPosition: Offset? = null
    private var maximumDisplacement = 0f
    private var firstMoveLogged = false
    private var slopCrossingLogged = false
    private var platformUpSeen = false
    private var startedWithoutDown = false

    fun record(event: PointerEvent) {
        val motionEvent = event.motionEvent
        val isSequenceDown = motionEvent?.actionMasked == MotionEvent.ACTION_DOWN ||
            (motionEvent == null && event.type == PointerEventType.Press && activeSequence == null)
        if (isSequenceDown) {
            sequenceIndex++
            activeSequence = sequenceIndex
            val change = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
            trackedPointerId = change?.id
            initialPosition = change?.position
            maximumDisplacement = 0f
            firstMoveLogged = false
            slopCrossingLogged = false
            platformUpSeen = false
            startedWithoutDown = false
        } else if (activeSequence == null && sequenceIndex < RAW_SEQUENCE_LIMIT) {
            sequenceIndex++
            activeSequence = sequenceIndex
            val change = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
            trackedPointerId = change?.id
            initialPosition = change?.position
            maximumDisplacement = 0f
            firstMoveLogged = false
            slopCrossingLogged = false
            platformUpSeen = false
            startedWithoutDown = true
        }

        val sequence = activeSequence ?: return
        if (sequence > RAW_SEQUENCE_LIMIT) return
        val trackedChange = trackedPointerId?.let { id ->
            event.changes.firstOrNull { it.id == id }
        }
        val start = initialPosition
        if (trackedChange != null && start != null) {
            maximumDisplacement = maxOf(
                maximumDisplacement,
                sqrt((trackedChange.position - start).getDistanceSquared()),
            )
        }
        val crossedSlop = maximumDisplacement > touchSlop
        val isMove = motionEvent?.actionMasked == MotionEvent.ACTION_MOVE ||
            (motionEvent == null && event.type == PointerEventType.Move)
        val shouldLog = !isMove || !firstMoveLogged || (crossedSlop && !slopCrossingLogged)
        if (shouldLog) {
            val origin = if (motionEvent == null) "COMPOSE_FABRICATED" else "PLATFORM_BACKED"
            val details = motionEvent?.describeRawTouch(
                trackedPointerId = trackedPointerId?.value?.toInt()
                    ?: MotionEvent.INVALID_POINTER_ID,
                displacement = maximumDisplacement,
                origin = origin,
            ) ?: "action=${event.type} actionIndex=NA pointerCount=${event.changes.size} " +
                "pointerIds=${event.changes.joinToString(prefix = "[", postfix = "]") { it.id.value.toString() }} " +
                "downTime=NA eventTime=${event.changes.maxOfOrNull { it.uptimeMillis } ?: -1L} " +
                "elapsed=NA trackedPointerId=${trackedPointerId?.value ?: "NONE"} " +
                "x=${trackedChange?.position?.x ?: "NA"} y=${trackedChange?.position?.y ?: "NA"} " +
                "deviceId=NA source=NA toolType=${trackedChange?.type ?: "NA"} " +
                "displacement=${String.format(Locale.US, "%.2f", maximumDisplacement)} " +
                "origin=$origin"
            Log.i(
                RAW_TOUCH_TAG,
                "COMPOSE sequence=$sequence generation=$resetGeneration " +
                    "startedWithoutDown=$startedWithoutDown $details",
            )
        }
        if (isMove) firstMoveLogged = true
        if (crossedSlop) slopCrossingLogged = true

        val isTerminal = motionEvent?.actionMasked == MotionEvent.ACTION_UP ||
            motionEvent?.actionMasked == MotionEvent.ACTION_CANCEL ||
            (motionEvent == null && event.type == PointerEventType.Release &&
                event.changes.none { it.pressed })
        if (isTerminal) {
            platformUpSeen = motionEvent?.actionMasked == MotionEvent.ACTION_UP
            Log.i(
                RAW_TOUCH_TAG,
                "COMPOSE sequence=$sequence terminal=${motionEvent?.let {
                    MotionEvent.actionToString(it.action)
                } ?: event.type.toString()} " +
                    "duration=${motionEvent?.let { it.eventTime - it.downTime } ?: "NA"}ms " +
                    "totalDisplacement=${String.format(Locale.US, "%.2f", maximumDisplacement)} " +
                    "genuinePlatformUp=$platformUpSeen generation=$resetGeneration",
            )
            activeSequence = null
        }
    }

    fun recordCoroutineCancellation() {
        Log.i(
            RAW_TOUCH_TAG,
            "COMPOSE COROUTINE TERMINATED generation=$resetGeneration " +
                "activeSequence=${activeSequence ?: "NONE"} " +
                "platformUpSeen=$platformUpSeen syntheticTapOrUpEmitted=false",
        )
    }
}

private fun MotionEvent.describeRawTouch(
    trackedPointerId: Int,
    displacement: Float,
    origin: String,
): String {
    val trackedIndex = findPointerIndex(trackedPointerId)
    val x = trackedIndex.takeIf { it >= 0 }?.let(::getX)
    val y = trackedIndex.takeIf { it >= 0 }?.let(::getY)
    val toolType = trackedIndex.takeIf { it >= 0 }
        ?.let(::getToolType)
        ?.let(::motionEventToolTypeLabel)
        ?: "UNAVAILABLE"
    return "action=${MotionEvent.actionToString(action)} actionIndex=$actionIndex " +
        "pointerCount=$pointerCount " +
        "pointerIds=${(0 until pointerCount).joinToString(prefix = "[", postfix = "]") {
            getPointerId(it).toString()
        }} downTime=$downTime eventTime=$eventTime elapsed=${eventTime - downTime}ms " +
        "trackedPointerId=$trackedPointerId x=${x ?: "NA"} y=${y ?: "NA"} " +
        "deviceId=$deviceId source=0x${source.toString(16)} toolType=$toolType " +
        "displacement=${String.format(Locale.US, "%.2f", displacement)} origin=$origin"
}

private fun motionEventToolTypeLabel(toolType: Int): String = when (toolType) {
    MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
    MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
    MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
    MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
    MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN"
    else -> toolType.toString()
}

internal data class TouchSequenceToken(
    val sequenceId: Int,
    val generation: Int,
    val downTimeMillis: Long,
    val pointerId: Int,
)

internal enum class TapRejectionReason(val label: String) {
    NO_GENUINE_PLATFORM_ACTION_DOWN("NO GENUINE PLATFORM ACTION_DOWN"),
    NO_GENUINE_PLATFORM_ACTION_UP("NO GENUINE PLATFORM ACTION_UP"),
    PLATFORM_ACTION_CANCEL("PLATFORM ACTION_CANCEL"),
    COMPOSE_FABRICATED_RELEASE("COMPOSE-FABRICATED RELEASE"),
    RESET_GENERATION_CHANGED("RESET GENERATION CHANGED"),
    POINTER_SEQUENCE_MISMATCH("POINTER SEQUENCE MISMATCH"),
    POINTER_INPUT_COROUTINE_INVALIDATED("POINTER-INPUT COROUTINE INVALIDATED"),
    ADDITIONAL_POINTER_PARTICIPATED("ADDITIONAL POINTER PARTICIPATED"),
}

internal data class TouchReleaseVerdict(
    val accepted: Boolean,
    val sequenceId: Int?,
    val generation: Int?,
    val genuinePlatformUp: Boolean,
    val platformCancelled: Boolean,
    val coroutineInvalidated: Boolean,
    val reason: TapRejectionReason?,
) {
    fun diagnosticMessage(): String {
        val outcome = if (accepted) "TAP ACCEPTED" else "TAP REJECTED: ${reason?.label}"
        return "$outcome sequence=${sequenceId ?: "NONE"} " +
            "generation=${generation ?: "NONE"} genuinePlatformUp=$genuinePlatformUp " +
            "platformCancelled=$platformCancelled " +
            "coroutineInvalidated=$coroutineInvalidated"
    }

    companion object {
        fun rejected(
            sequenceId: Int?,
            generation: Int?,
            reason: TapRejectionReason,
            genuinePlatformUp: Boolean = false,
            platformCancelled: Boolean = false,
            coroutineInvalidated: Boolean = false,
        ) = TouchReleaseVerdict(
            accepted = false,
            sequenceId = sequenceId,
            generation = generation,
            genuinePlatformUp = genuinePlatformUp,
            platformCancelled = platformCancelled,
            coroutineInvalidated = coroutineInvalidated,
            reason = reason,
        )
    }
}

internal class TrackpadTouchProvenance {
    private data class LiveSequence(
        val token: TouchSequenceToken,
        var platformUpSeen: Boolean = false,
        var platformCancelled: Boolean = false,
        var coroutineInvalidated: Boolean = false,
        var additionalPointerParticipated: Boolean = false,
    )

    private var currentGeneration = 0
    private var nextSequenceId = 0
    private var liveSequence: LiveSequence? = null

    @Synchronized
    fun reset(generation: Int) {
        currentGeneration = generation
        liveSequence = null
    }

    @Synchronized
    fun recordPlatformDown(
        generation: Int,
        downTimeMillis: Long,
        pointerId: Int,
    ): TouchSequenceToken {
        if (generation != currentGeneration) reset(generation)
        nextSequenceId = if (nextSequenceId == MAX_SEQUENCE_ID) 1 else nextSequenceId + 1
        return TouchSequenceToken(
            sequenceId = nextSequenceId,
            generation = generation,
            downTimeMillis = downTimeMillis,
            pointerId = pointerId,
        ).also { token -> liveSequence = LiveSequence(token) }
    }

    @Synchronized
    fun recordAdditionalPointer(generation: Int, downTimeMillis: Long) {
        matchingSequence(generation, downTimeMillis)?.additionalPointerParticipated = true
    }

    @Synchronized
    fun recordPlatformUp(generation: Int, downTimeMillis: Long, pointerId: Int) {
        val sequence = matchingSequence(generation, downTimeMillis) ?: return
        if (sequence.token.pointerId == pointerId) sequence.platformUpSeen = true
    }

    @Synchronized
    fun recordPlatformCancel(generation: Int, downTimeMillis: Long) {
        matchingSequence(generation, downTimeMillis)?.platformCancelled = true
    }

    @Synchronized
    fun claimComposeDown(
        generation: Int,
        downTimeMillis: Long?,
        pointerId: Int,
    ): TouchSequenceToken? {
        val sequence = liveSequence ?: return null
        return sequence.token.takeIf {
            downTimeMillis != null &&
                it.generation == generation &&
                generation == currentGeneration &&
                it.downTimeMillis == downTimeMillis &&
                it.pointerId == pointerId &&
                !sequence.platformCancelled &&
                !sequence.coroutineInvalidated
        }
    }

    @Synchronized
    fun validateComposeRelease(
        token: TouchSequenceToken?,
        generation: Int,
        composeBackedByPlatformUp: Boolean,
        composeDownTimeMillis: Long?,
        composePointerId: Int?,
        allowAdditionalPointers: Boolean,
    ): TouchReleaseVerdict {
        val sequence = liveSequence
        val reason = when {
            generation != currentGeneration ||
                (token != null && token.generation != generation) ->
                TapRejectionReason.RESET_GENERATION_CHANGED
            token == null || sequence == null ->
                TapRejectionReason.NO_GENUINE_PLATFORM_ACTION_DOWN
            sequence.token != token -> TapRejectionReason.POINTER_SEQUENCE_MISMATCH
            sequence.platformCancelled -> TapRejectionReason.PLATFORM_ACTION_CANCEL
            sequence.coroutineInvalidated ->
                TapRejectionReason.POINTER_INPUT_COROUTINE_INVALIDATED
            composeBackedByPlatformUp &&
                (composeDownTimeMillis != token?.downTimeMillis ||
                    composePointerId != token?.pointerId) ->
                TapRejectionReason.POINTER_SEQUENCE_MISMATCH
            sequence.additionalPointerParticipated && !allowAdditionalPointers ->
                TapRejectionReason.ADDITIONAL_POINTER_PARTICIPATED
            !sequence.platformUpSeen -> TapRejectionReason.NO_GENUINE_PLATFORM_ACTION_UP
            !composeBackedByPlatformUp -> TapRejectionReason.COMPOSE_FABRICATED_RELEASE
            else -> null
        }
        return if (reason == null && sequence != null) {
            TouchReleaseVerdict(
                accepted = true,
                sequenceId = sequence.token.sequenceId,
                generation = sequence.token.generation,
                genuinePlatformUp = true,
                platformCancelled = false,
                coroutineInvalidated = false,
                reason = null,
            )
        } else {
            TouchReleaseVerdict.rejected(
                sequenceId = token?.sequenceId ?: sequence?.token?.sequenceId,
                generation = token?.generation ?: sequence?.token?.generation,
                reason = reason ?: TapRejectionReason.POINTER_SEQUENCE_MISMATCH,
                genuinePlatformUp = sequence?.platformUpSeen == true,
                platformCancelled = sequence?.platformCancelled == true,
                coroutineInvalidated = sequence?.coroutineInvalidated == true,
            )
        }
    }

    @Synchronized
    fun isLive(token: TouchSequenceToken?, generation: Int): Boolean {
        val sequence = liveSequence ?: return false
        return token != null && sequence.token == token &&
            generation == currentGeneration && token.generation == generation &&
            !sequence.platformCancelled && !sequence.coroutineInvalidated
    }

    @Synchronized
    fun invalidateCoroutine(token: TouchSequenceToken?, generation: Int) {
        val sequence = liveSequence ?: return
        if (token != null && sequence.token == token && generation == currentGeneration) {
            sequence.coroutineInvalidated = true
        }
    }

    @Synchronized
    fun complete(token: TouchSequenceToken?, generation: Int) {
        val sequence = liveSequence ?: return
        if (token != null && sequence.token == token && generation == currentGeneration) {
            liveSequence = null
        }
    }

    private fun matchingSequence(generation: Int, downTimeMillis: Long): LiveSequence? =
        liveSequence?.takeIf {
            generation == currentGeneration &&
                it.token.generation == generation &&
                it.token.downTimeMillis == downTimeMillis
        }

    private companion object {
        const val MAX_SEQUENCE_ID = 1_000_000
    }
}

internal class TrackpadGestureTracker(
    private val singleTapMaximumDurationMillis: Long,
    private val twoFingerTapMaximumDurationMillis: Long,
    private val doubleTapTimeoutMillis: Long,
    private val holdTimeoutMillis: Long,
    touchSlop: Float,
    doubleTapSlop: Float,
) {
    private val touchSlopSquared = touchSlop * touchSlop
    private val touchSlop = touchSlop
    private val doubleTapSlopSquared = doubleTapSlop * doubleTapSlop
    private val initialPositions = mutableMapOf<PointerId, Offset>()
    private var downUptimeMillis = 0L
    private var lastTapUpUptimeMillis = Long.MIN_VALUE
    private var lastTapPosition: Offset? = null
    private var state = GestureTrackingState.IDLE
    private var cancellationReported = false
    private var pendingMovementReported = false
    private var awaitingFirstDownAfterReset = true
    private var previousTwoFingerCentroid: Offset? = null

    fun handle(
        event: PointerEvent,
        releaseVerdict: TouchReleaseVerdict? = null,
        changes: List<PointerInputChange> = event.changes,
    ): GestureUpdate {
        val pressedCount = changes.count { it.pressed }
        when (event.type) {
            PointerEventType.Press -> {
                if (state == GestureTrackingState.IDLE && pressedCount == 1) {
                    val pointer = changes.first { it.pressed }
                    initialPositions[pointer.id] = pointer.position
                    downUptimeMillis = pointer.uptimeMillis
                    val previousTapPosition = lastTapPosition
                    val sincePreviousTap = pointer.uptimeMillis - lastTapUpUptimeMillis
                    val hasPreviousTap = previousTapPosition != null
                    val withinTime = sincePreviousTap in 0..doubleTapTimeoutMillis
                    val withinDistance = previousTapPosition != null &&
                        (pointer.position - previousTapPosition).getDistanceSquared() <=
                        doubleTapSlopSquared
                    val isSecondTap = hasPreviousTap && withinTime && withinDistance
                    val diagnostics = buildList {
                        if (awaitingFirstDownAfterReset) {
                            add("FIRST ACTION_DOWN AFTER GESTURE RESET")
                            awaitingFirstDownAfterReset = false
                        }
                        addAll(
                            when {
                                isSecondTap -> listOf(
                                    "VALID SECOND TAP DETECTED",
                                    "HOLD TIMER STARTED",
                                )
                                hasPreviousTap && !withinTime ->
                                    listOf("SECOND TAP REJECTED: TIME")
                                hasPreviousTap && !withinDistance ->
                                    listOf("SECOND TAP REJECTED: DISTANCE")
                                else -> emptyList()
                            },
                        )
                    }
                    state = if (isSecondTap) {
                        clearPreviousTap()
                        GestureTrackingState.SECOND_TAP_HOLD_PENDING
                    } else {
                        GestureTrackingState.SINGLE_PENDING
                    }
                    return GestureUpdate(
                        scheduleHold = isSecondTap,
                        diagnostics = diagnostics,
                    )
                } else if ((state == GestureTrackingState.SINGLE_PENDING ||
                        state == GestureTrackingState.SECOND_TAP_HOLD_PENDING) &&
                    pressedCount == 2
                ) {
                    changes.filter { it.pressed }.forEach { pointer ->
                        initialPositions.putIfAbsent(pointer.id, pointer.position)
                    }
                    clearPreviousTap()
                    state = GestureTrackingState.TWO_FINGER_PENDING
                    previousTwoFingerCentroid = pressedCentroid(changes)
                    return GestureUpdate(cancelHold = true)
                } else if (state == GestureTrackingState.SECOND_TAP_HOLD_PENDING ||
                    state == GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE
                ) {
                    return cancel(endActiveDrag = true)
                } else {
                    return cancel()
                }
            }

            PointerEventType.Move -> {
                when (state) {
                    GestureTrackingState.SINGLE_PENDING -> {
                        val pointer = changes.singleOrNull { it.pressed }
                        val initialPosition = pointer?.let { initialPositions[it.id] }
                        if (pointer == null || initialPosition == null) return cancel()
                        if (pointer.distanceSquaredFrom(initialPosition) > touchSlopSquared) {
                            state = GestureTrackingState.SINGLE_MOVING
                            clearPreviousTap()
                            return GestureUpdate(
                                gesture = reportCancellationOnce(),
                                allowMovement = true,
                                diagnostics = listOf(
                                    "TOUCH SLOP EXCEEDED",
                                    "ORDINARY MOVEMENT STARTED",
                                ),
                            )
                        }
                    }
                    GestureTrackingState.SECOND_TAP_HOLD_PENDING -> {
                        val pointer = changes.singleOrNull { it.pressed }
                        val initialPosition = pointer?.let { initialPositions[it.id] }
                        if (pointer == null || initialPosition == null) return cancel()
                        val diagnostics = if (pendingMovementReported) {
                            emptyList()
                        } else {
                            pendingMovementReported = true
                            listOf("MOVEMENT SEEN WHILE PENDING")
                        }
                        if (pointer.distanceSquaredFrom(initialPosition) > touchSlopSquared) {
                            state = GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE
                            return GestureUpdate(
                                gesture = TrackpadGesture.DOUBLE_TAP_HOLD_START,
                                allowMovement = true,
                                cancelHold = true,
                                resetMovementBaseline = true,
                                diagnostics = diagnostics,
                            )
                        }
                        return GestureUpdate(
                            updateMovementBaseline = true,
                            diagnostics = diagnostics,
                        )
                    }
                    GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE -> {
                        if (pressedCount == 1) return GestureUpdate(allowMovement = true)
                        return cancel(endActiveDrag = true)
                    }
                    GestureTrackingState.SINGLE_MOVING -> {
                        if (pressedCount == 1) return GestureUpdate(allowMovement = true)
                        return cancel()
                    }
                    GestureTrackingState.TWO_FINGER_PENDING -> {
                        if (pressedCount > 2) return cancel()
                        val elapsed = changes.maxOfOrNull { it.uptimeMillis }
                            ?.minus(downUptimeMillis) ?: 0L
                        val centroid = pressedCentroid(changes) ?: return cancel()
                        val initialCentroid = initialPositions.values.centroid() ?: return cancel()
                        val displacement = centroid - initialCentroid
                        if (displacement.getDistanceSquared() > touchSlopSquared) {
                            if (kotlin.math.abs(displacement.y) < kotlin.math.abs(displacement.x)) {
                                return cancel()
                            }
                            val previous = previousTwoFingerCentroid ?: initialCentroid
                            previousTwoFingerCentroid = centroid
                            state = GestureTrackingState.TWO_FINGER_SCROLLING
                            return GestureUpdate(
                                scrollDeltaY = centroid.y - previous.y,
                                cancelHold = true,
                                diagnostics = listOf("TWO-FINGER VERTICAL SCROLL STARTED"),
                            )
                        }
                        if (elapsed > twoFingerTapMaximumDurationMillis) return cancel()
                    }
                    GestureTrackingState.TWO_FINGER_SCROLLING -> {
                        if (pressedCount != 2) return GestureUpdate()
                        val centroid = pressedCentroid(changes) ?: return GestureUpdate()
                        val previous = previousTwoFingerCentroid ?: centroid
                        previousTwoFingerCentroid = centroid
                        val deltaY = centroid.y - previous.y
                        return GestureUpdate(scrollDeltaY = deltaY.takeUnless { it == 0f })
                    }
                    GestureTrackingState.CANCELLED,
                    GestureTrackingState.IDLE,
                    -> Unit
                }
            }

            PointerEventType.Release -> {
                if (state == GestureTrackingState.TWO_FINGER_PENDING &&
                    (hasPointerExceededTolerance(changes) || changes.any {
                        it.id !in initialPositions && (it.pressed || it.previousPressed)
                    })
                ) {
                    return cancel()
                }
                if (pressedCount == 0) {
                    if (releaseVerdict?.accepted != true) {
                        return rejectRelease(
                            releaseVerdict ?: TouchReleaseVerdict.rejected(
                                sequenceId = null,
                                generation = null,
                                reason = TapRejectionReason.NO_GENUINE_PLATFORM_ACTION_UP,
                            ),
                        )
                    }
                    val finalUptimeMillis = changes.maxOfOrNull { it.uptimeMillis }
                        ?: downUptimeMillis
                    val duration = finalUptimeMillis - downUptimeMillis
                    val completedState = state
                    val displacement = maximumDisplacement(changes)
                    val gesture = when (completedState) {
                        GestureTrackingState.SINGLE_PENDING -> {
                            if (!hasPointerExceededTolerance(changes) &&
                                duration in 0..singleTapMaximumDurationMillis
                            ) {
                                lastTapUpUptimeMillis = finalUptimeMillis
                                lastTapPosition = initialPositions.values.singleOrNull()
                                TrackpadGesture.SINGLE_TAP
                            } else {
                                clearPreviousTap()
                                TrackpadGesture.CANCELLED
                            }
                        }
                        GestureTrackingState.SECOND_TAP_HOLD_PENDING -> {
                            if (!hasPointerExceededTolerance(changes) &&
                                duration in 0..singleTapMaximumDurationMillis
                            ) {
                                TrackpadGesture.SINGLE_TAP
                            } else {
                                TrackpadGesture.CANCELLED
                            }
                        }
                        GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE ->
                            TrackpadGesture.DOUBLE_TAP_HOLD_END
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
                        GestureTrackingState.TWO_FINGER_SCROLLING -> null
                        GestureTrackingState.CANCELLED -> reportCancellationOnce()
                        GestureTrackingState.IDLE -> null
                    }
                    resetCurrentGesture()
                    return GestureUpdate(
                        gesture = gesture,
                        cancelHold = true,
                        diagnostics = if (completedState == GestureTrackingState.SINGLE_PENDING &&
                            gesture == TrackpadGesture.SINGLE_TAP
                        ) {
                            listOf(
                                "FIRST TAP RECORDED",
                                tapEligibilityDiagnostic(
                                    releaseVerdict,
                                    duration,
                                    displacement,
                                ),
                            )
                        } else if (
                            completedState == GestureTrackingState.SECOND_TAP_HOLD_PENDING &&
                            gesture == TrackpadGesture.SINGLE_TAP
                        ) {
                            listOf(
                                tapEligibilityDiagnostic(
                                    releaseVerdict,
                                    duration,
                                    displacement,
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    )
                }
            }

            PointerEventType.Unknown -> if (state != GestureTrackingState.IDLE) {
                val gesture = if (state == GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE) {
                    TrackpadGesture.DOUBLE_TAP_HOLD_END
                } else {
                    reportCancellationOnce()
                }
                clearPreviousTap()
                resetCurrentGesture()
                return GestureUpdate(
                    gesture = gesture,
                    cancelHold = true,
                    diagnostics = listOf("GESTURE CANCELLED"),
                )
            }
        }
        return GestureUpdate()
    }

    fun handleHoldTimeout(): TrackpadGesture? {
        if (state != GestureTrackingState.SECOND_TAP_HOLD_PENDING) return null
        val elapsed = android.os.SystemClock.uptimeMillis() - downUptimeMillis
        if (elapsed < holdTimeoutMillis) return null
        state = GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE
        return TrackpadGesture.DOUBLE_TAP_HOLD_START
    }

    fun cancelActiveGesture(): TrackpadGesture? {
        val gesture = if (state == GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE) {
            TrackpadGesture.DOUBLE_TAP_HOLD_END
        } else {
            null
        }
        clearPreviousTap()
        resetCurrentGesture()
        return gesture
    }

    fun isTwoFingerGesturePending(): Boolean =
        state == GestureTrackingState.TWO_FINGER_PENDING ||
            state == GestureTrackingState.TWO_FINGER_SCROLLING

    fun invalidateFromProvenance(verdict: TouchReleaseVerdict): GestureUpdate =
        rejectRelease(verdict)

    private fun hasPointerExceededTolerance(changes: List<PointerInputChange>): Boolean =
        changes.any { pointer ->
            val initialPosition = initialPositions[pointer.id]
            initialPosition == null ||
                pointer.distanceSquaredFrom(initialPosition) > touchSlopSquared
        }

    private fun pressedCentroid(changes: List<PointerInputChange>): Offset? =
        changes.asSequence().filter { it.pressed }.map { it.position }.toList().centroid()

    private fun Collection<Offset>.centroid(): Offset? {
        if (isEmpty()) return null
        return Offset(sumOf { it.x.toDouble() }.toFloat() / size, sumOf { it.y.toDouble() }.toFloat() / size)
    }

    private fun PointerInputChange.distanceSquaredFrom(position: Offset): Float =
        (this.position - position).getDistanceSquared()

    private fun maximumDisplacement(changes: List<PointerInputChange>): Float = changes.maxOfOrNull {
        val initialPosition = initialPositions[it.id] ?: return@maxOfOrNull Float.POSITIVE_INFINITY
        sqrt(it.distanceSquaredFrom(initialPosition))
    } ?: 0f

    private fun tapEligibilityDiagnostic(
        releaseVerdict: TouchReleaseVerdict,
        duration: Long,
        displacement: Float,
    ): String {
        return "TAP ELIGIBLE: duration=${duration}ms<=${singleTapMaximumDurationMillis}ms " +
            "displacement=${String.format(Locale.US, "%.2f", displacement)}<=" +
            "${String.format(Locale.US, "%.2f", touchSlop)} " +
            "releaseOrigin=PLATFORM_ACTION_UP sequence=${releaseVerdict.sequenceId} " +
            "generation=${releaseVerdict.generation}"
    }

    private fun rejectRelease(verdict: TouchReleaseVerdict): GestureUpdate {
        val gesture = if (state == GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE) {
            TrackpadGesture.DOUBLE_TAP_HOLD_END
        } else {
            reportCancellationOnce()
        }
        clearPreviousTap()
        resetCurrentGesture()
        return GestureUpdate(
            gesture = gesture,
            cancelHold = true,
            diagnostics = listOf(verdict.diagnosticMessage()),
        )
    }

    private fun cancel(endActiveDrag: Boolean = false): GestureUpdate {
        val gesture = if (endActiveDrag && state == GestureTrackingState.DOUBLE_TAP_HOLD_ACTIVE) {
            TrackpadGesture.DOUBLE_TAP_HOLD_END
        } else {
            reportCancellationOnce()
        }
        state = GestureTrackingState.CANCELLED
        clearPreviousTap()
        return GestureUpdate(
            gesture = gesture,
            cancelHold = true,
            diagnostics = listOf("GESTURE CANCELLED"),
        )
    }

    private fun reportCancellationOnce(): TrackpadGesture? {
        if (cancellationReported) return null
        cancellationReported = true
        return TrackpadGesture.CANCELLED
    }

    private fun resetCurrentGesture() {
        initialPositions.clear()
        downUptimeMillis = 0L
        state = GestureTrackingState.IDLE
        cancellationReported = false
        pendingMovementReported = false
        previousTwoFingerCentroid = null
    }

    private fun clearPreviousTap() {
        lastTapUpUptimeMillis = Long.MIN_VALUE
        lastTapPosition = null
    }
}

internal data class GestureUpdate(
    val gesture: TrackpadGesture? = null,
    val allowMovement: Boolean = false,
    val scheduleHold: Boolean = false,
    val cancelHold: Boolean = false,
    val updateMovementBaseline: Boolean = false,
    val resetMovementBaseline: Boolean = false,
    val scrollDeltaY: Float? = null,
    val diagnostics: List<String> = emptyList(),
)

private enum class GestureTrackingState {
    IDLE,
    SINGLE_PENDING,
    SECOND_TAP_HOLD_PENDING,
    DOUBLE_TAP_HOLD_ACTIVE,
    SINGLE_MOVING,
    TWO_FINGER_PENDING,
    TWO_FINGER_SCROLLING,
    CANCELLED,
}

private fun KeyEvent.isFromGameController(): Boolean =
    isFromSource(InputDevice.SOURCE_GAMEPAD) || isFromSource(InputDevice.SOURCE_JOYSTICK)

private fun handlePointerEvent(
    eventType: PointerEventType,
    changes: List<PointerInputChange>,
    previousState: TouchState,
    surfaceWidth: Float,
    surfaceHeight: Float,
    allowMovement: Boolean,
    updateMovementBaseline: Boolean,
    resetMovementBaseline: Boolean,
    pointerSpeed: PointerSpeed,
): TouchState {
    val state = previousState.withSurfaceSize(surfaceWidth, surfaceHeight)
    val activePointerCount = changes.count { it.pressed }
    val action = when (eventType) {
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
            changes.isNotEmpty() &&
            changes.none { it.pressed }
        ) {
            TouchAction.CANCEL
        } else {
            null
        }
        else -> null
    }

    return when (action) {
        TouchAction.DOWN -> {
            val initialPointer = changes.firstOrNull { it.pressed } ?: return state
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
            if (updateMovementBaseline || resetMovementBaseline) {
                val trackedPointer = changes.findPressedPointer(state.trackedPointerId)
                return if (trackedPointer == null) {
                    state.copy(
                        deltaX = 0f,
                        deltaY = 0f,
                        pointerCount = activePointerCount,
                        action = TouchAction.MOVE,
                    )
                } else {
                    state.withResetBaseline(
                        pointer = trackedPointer,
                        pointerCount = activePointerCount,
                        action = TouchAction.MOVE,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight,
                    )
                }
            }
            if (!allowMovement) {
                return state.copy(
                    deltaX = 0f,
                    deltaY = 0f,
                    pointerCount = activePointerCount,
                    action = TouchAction.MOVE,
                )
            }
            val trackedPointer = changes.findPressedPointer(state.trackedPointerId)
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
                val scaledDelta = scaleRelativeDelta(
                    rawDx = updatedState.deltaX,
                    rawDy = updatedState.deltaY,
                    pointerSpeed = pointerSpeed,
                )
                CursorDeltaCoordinator.publish(dx = scaledDelta.dx, dy = scaledDelta.dy)
                updatedState
            }
        }

        TouchAction.POINTER_DOWN -> {
            val trackedPointer = changes.findPointer(state.trackedPointerId)
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
            val liftedPointerId = changes
                .firstOrNull { it.previousPressed && !it.pressed }
                ?.id
            if (liftedPointerId == state.trackedPointerId) {
                val replacement = changes.firstOrNull { it.pressed }
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
                val trackedPointer = changes.findPointer(state.trackedPointerId)
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
            val finalPointer = changes.findPointer(state.trackedPointerId)
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

internal fun TouchState.withTransientInputCleared(): TouchState = copy(
    fingerX = 0f,
    fingerY = 0f,
    deltaX = 0f,
    deltaY = 0f,
    pointerCount = 0,
    trackedPointerId = null,
    action = TouchAction.CANCEL,
    moveEventCount = 0,
)

private fun List<PointerInputChange>.findPointer(pointerId: PointerId?): PointerInputChange? =
    pointerId?.let { id -> firstOrNull { it.id == id } }

private fun List<PointerInputChange>.findPressedPointer(pointerId: PointerId?): PointerInputChange? =
    findPointer(pointerId)?.takeIf { it.pressed } ?: firstOrNull { it.pressed }

private fun Offset.constrainTo(surfaceWidth: Float, surfaceHeight: Float) = Offset(
    x = x.coerceIn(0f, surfaceWidth.coerceAtLeast(0f)),
    y = y.coerceIn(0f, surfaceHeight.coerceAtLeast(0f)),
)

@Composable
private fun MouseDiagnosticsPanel(
    diagnostics: MouseDiagnostics,
    displayId: Int,
    connectionDiagnostics: ScummVMConnectionDiagnostics,
    mirrorCropDiagnostics: MirrorCropDiagnostics,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(AdventurePadThemeTokens.colors.surface)
            .border(1.dp, AdventurePadThemeTokens.colors.outline)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DiagnosticValue(
                value = "LEFT: ${if (diagnostics.leftButtonDown) "DOWN" else "UP"}",
                modifier = Modifier.weight(1f),
            )
            DiagnosticValue(
                value = "RIGHT: ${if (diagnostics.rightButtonDown) "DOWN" else "UP"}",
                modifier = Modifier.weight(1f),
            )
            val dragState = if (diagnostics.dragActive) {
                "ACTIVE (${diagnostics.dragSource.label})"
            } else {
                "INACTIVE"
            }
            DiagnosticValue(
                value = "DRAG: $dragState",
                modifier = Modifier.weight(2f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DiagnosticValue(
                value = "LAST GESTURE: ${diagnostics.lastGesture}",
                modifier = Modifier.weight(1f),
            )
            DiagnosticValue(value = "DISPLAY: $displayId")
            DiagnosticValue(
                value = if (connectionDiagnostics.isConnected) "CONNECTED" else "DISCONNECTED",
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DiagnosticValue(
                value = if (connectionDiagnostics.bindingRequested) {
                    "BINDING: REQUESTED"
                } else {
                    "BINDING: IDLE"
                },
                modifier = Modifier.weight(1f),
            )
            DiagnosticValue(
                value = "RECONNECTS: ${connectionDiagnostics.reconnectAttemptCount}",
                modifier = Modifier.weight(1f),
            )
            DiagnosticValue(
                value = "LAST CONNECTION: ${connectionDiagnostics.lastConnectionEvent}",
                modifier = Modifier.weight(2f),
            )
        }
        DiagnosticValue(value = "BUTTON SOURCES: ${diagnostics.activeMouseButtonSources}")
        val geometry = mirrorCropDiagnostics.geometry
        val profile = mirrorCropDiagnostics.profile
        val pixelCrop = geometry?.let { profile.crop.toPixels(it.width, it.height) }
        DiagnosticValue(
            value = "MIRROR SOURCE: ${geometry?.let { "${it.width}×${it.height}" } ?: "UNKNOWN"} • " +
                "CAPABILITY: ${geometry?.rendererCapability ?: 0} • GEOMETRY GEN: ${geometry?.generation ?: 0}",
        )
        DiagnosticValue(
            value = "CROP: ${profile.crop.toDiagnosticString()} • " +
                "PIXELS: ${pixelCrop?.toDiagnosticString() ?: "UNKNOWN"} • " +
                "SCHEMA: ${profile.schemaVersion} • CONFIRMED: ${profile.confirmed} • " +
                "REVIEW REQUIRED: ${profile.requiresReview}",
        )
        mirrorCropDiagnostics.acknowledgement?.let { acknowledgement ->
            DiagnosticValue(
                value = "LAST CROP ACK: ${acknowledgement.result.name} • " +
                    "CROP GEN: ${acknowledgement.cropGeneration} • " +
                    "GEOMETRY GEN: ${acknowledgement.geometryGeneration} • ${acknowledgement.diagnostic}",
            )
        }
        DiagnosticValue(
            value = "DISPLAY MODE: ${mirrorCropDiagnostics.displayMode.name} • " +
                "PREFERRED: ${mirrorCropDiagnostics.displayModePreferences.preferredMode.name}",
        )
        mirrorCropDiagnostics.upperAcknowledgement?.let { acknowledgement ->
            DiagnosticValue(
                value = "LAST UPPER ACK: ${acknowledgement.result.name} • " +
                    "MODE GEN: ${acknowledgement.modeGeneration} • " +
                    "GEOMETRY GEN: ${acknowledgement.geometryGeneration} • ${acknowledgement.diagnostic}",
            )
        }
    }
}

private data class MirrorCropDiagnostics(
    val geometry: MirrorSourceGeometry?,
    val profile: MirrorCropProfile,
    val acknowledgement: CropAcknowledgement?,
    val displayMode: DisplayMode,
    val displayModePreferences: DisplayModePreferences,
    val upperAcknowledgement: UpperPresentationAcknowledgement?,
)


private fun NormalizedCrop.toDiagnosticString(): String = String.format(
    Locale.ROOT,
    "%.4f,%.4f–%.4f,%.4f",
    left,
    top,
    right,
    bottom,
)

private fun PixelCrop.toDiagnosticString(): String = "$left,$top–$right,$bottom"

@Composable
private fun DiagnosticValue(
    value: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = value,
        color = AdventurePadThemeTokens.colors.textSecondary,
        fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 8.dp),
    )
}

private data class MouseDiagnostics(
    val leftButtonDown: Boolean = false,
    val rightButtonDown: Boolean = false,
    val dragSource: DragSource = DragSource.NONE,
    val dragActive: Boolean = false,
    val lastButtonAction: String = "NONE",
    val lastGesture: String = TrackpadGesture.NONE_LABEL,
    val activeMouseButtonSources: String = "LEFT: NONE; RIGHT: NONE",
)

private enum class MouseButtonSource(val diagnosticLabel: String) {
    DEDICATED_BUTTON("DEDICATED"),
    CONTROLLER("CONTROLLER"),
    TRACKPAD_TAP("TAP"),
    TRACKPAD_TWO_FINGER_TAP("TWO-FINGER TAP"),
    DOUBLE_TAP_HOLD("DOUBLE-TAP HOLD"),
}

private enum class DragSource(val label: String) {
    NONE("NONE"),
    DEDICATED_LEFT("DEDICATED LEFT"),
    DOUBLE_TAP_HOLD("DOUBLE-TAP HOLD"),
}

private const val TapClickDurationMillis = 50L
private const val TwoFingerTapMaximumDurationMillis = 250L

internal enum class TrackpadGesture(val label: String) {
    SINGLE_TAP("SINGLE TAP"),
    TWO_FINGER_RIGHT_CLICK("TWO-FINGER RIGHT CLICK"),
    DOUBLE_TAP_HOLD_START("DRAG ACTIVATED: DOUBLE-TAP HOLD"),
    DOUBLE_TAP_HOLD_END("DRAG RELEASED: DOUBLE-TAP HOLD"),
    CANCELLED("CANCELLED");

    companion object {
        const val NONE_LABEL = "NONE"
    }
}

internal data class TouchState(
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

internal enum class TouchAction(val label: String) {
    DOWN("DOWN"),
    MOVE("MOVE"),
    UP("UP"),
    CANCEL("CANCEL"),
    POINTER_DOWN("POINTER_DOWN"),
    POINTER_UP("POINTER_UP"),
}

private val MarkerRadius = 16.dp
private val MarkerOutlineWidth = 3.dp
private val MinimumNormalTrackpadHeight = 120.dp
internal const val IMMERSIVE_TRACKPAD_SCALE = 0.85f
internal const val IMMERSIVE_SPLIT_TRACKPAD_EXTRA_HEIGHT_DP = 20
internal const val IMMERSIVE_TRACKPAD_BOTTOM_GAP_DP = 8
internal const val IMMERSIVE_UTILITY_UPWARD_OFFSET_DP = 8
private const val IMMERSIVE_TRACKPAD_REGION_VERTICAL_PADDING_DP = 3
private const val THEME_DIAGNOSTIC_TAG = "AdventurePadTheme"
private const val LAYOUT_POLISH_DIAGNOSTIC_TAG = "AdventurePadLayout87"

private fun Color.diagnosticHex(): String = String.format(Locale.ROOT, "#%08X", toArgb())
private val NormalLayoutReservedHeight = 176.dp
private const val MaxMoveEventCount = 999_999
private const val AdventurePadBridgeTag = "AdventurePadBridge"
private const val RAW_TOUCH_TAG = "AdventurePadRawTouch"
private const val RAW_SEQUENCE_LIMIT = 2

private fun Intent.skinContextOr(fallback: SkinContext): SkinContext =
    getStringExtra(DualDisplayCoordinator.EXTRA_SKIN_CONTEXT)
        ?.let { value -> SkinContext.entries.firstOrNull { it.name == value } }
        ?: fallback
