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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.jamesmoran.adventurepad.ui.theme.AdventurePadTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

class TrackpadActivity : ComponentActivity() {
    private var lifecycleEvent by mutableStateOf("INITIALIZING")
    private var lastLaunchResult by mutableStateOf("Waiting for launch details.")
    private var receivedIntentFlags by mutableStateOf(0)
    private var currentDisplayId by mutableStateOf(Display.INVALID_DISPLAY)
    private var mouseDiagnostics by mutableStateOf(MouseDiagnostics())
    private var connectionDiagnostics by mutableStateOf(ScummVMConnectionDiagnostics())
    private var mirrorOutputStatus by mutableStateOf(MirrorOutputStatus())
    private var gestureResetGeneration by mutableStateOf(0)
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
    private lateinit var mirrorCropRepository: MirrorCropRepository
    private lateinit var displayModePreferencesRepository: DisplayModePreferencesRepository
    private var mirrorSourceGeometry by mutableStateOf<MirrorSourceGeometry?>(null)
    private var mirrorCursorState by mutableStateOf(MirrorCursorState())
    private var cropEditorModel by mutableStateOf<CropEditorModel?>(null)
    private var cropEditorVisible by mutableStateOf(false)
    private var splitBeforeEditing = InterfaceSplit.Default
    private var cropEditTransaction: CropEditTransaction? = null
    private var lastCropAcknowledgement by mutableStateOf<CropAcknowledgement?>(null)
    private var cropSavePending by mutableStateOf(false)
    private var activeCropGeneration by mutableStateOf(0L)
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
        receivedIntentFlags = intent.flags
        currentDisplayId = display?.displayId ?: Display.INVALID_DISPLAY
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Launched because: $it" }
            ?: "TrackpadActivity created without a launch reason."
        recordLifecycle("CREATED")
        touchProvenance.reset(gestureResetGeneration)
        rawTouchDiagnostics.reset(gestureResetGeneration, "CREATE")
        pointerSpeedRepository = PointerSpeedRepository.create(this, lifecycleScope)
        mirrorCropRepository = MirrorCropRepository.create(this, lifecycleScope)
        displayModePreferencesRepository = DisplayModePreferencesRepository.create(this, lifecycleScope)
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
            val displayModePreferences by displayModePreferencesRepository.preferences.collectAsState()
            val cropSelection by mirrorCropRepository.selection.collectAsState()
            val cropProfile = cropSelection.profile
            AdventurePadTheme {
                AdventurePadScreen(
                    mouseDiagnostics = mouseDiagnostics,
                    displayId = currentDisplayId,
                    connectionDiagnostics = connectionDiagnostics,
                    mirrorOutputStatus = mirrorOutputStatus,
                    mirrorSourceGeometry = mirrorSourceGeometry,
                    mirrorCursorState = mirrorCursorState,
                    cropEditorModel = cropEditorModel.takeIf { cropEditorVisible },
                    cropProfile = cropProfile,
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
                    onPointerSpeedSelected = { selectedSpeed ->
                        lifecycleScope.launch {
                            pointerSpeedRepository.setPointerSpeed(selectedSpeed)
                        }
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
            }
        }
    }

    override fun onStart() {
        super.onStart()
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
        ScummVMInputClient.setMirrorGeometryListener(::handleMirrorGeometry)
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
        ScummVMInputClient.releaseJoystickAxes()
        recordLifecycle("PAUSED")
        super.onPause()
    }

    override fun onStop() {
        releaseAllMouseButtons("STOP")
        recordLifecycle("STOPPED")
        releaseForwardedGamepadKeys()
        ScummVMInputClient.releaseJoystickAxes()
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
        val previous = mirrorSourceGeometry
        val changed = previous != null &&
            (previous.generation != geometry.generation || previous.gameId != geometry.gameId)
        mirrorSourceGeometry = geometry
        mirrorCropRepository.selectGame(geometry.gameId)
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
        val profile = selection.profile
        val savedSplit = geometry?.let {
            profile.takeIf { candidate -> selection.gameId == it.gameId && candidate.isCompatibleWith(it) }
                ?.split
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
        ScummVMInputClient.releaseJoystickAxes()
        setIntent(intent)
        receivedIntentFlags = intent.flags
        lastLaunchResult = intent.getStringExtra(DualDisplayCoordinator.EXTRA_LAUNCH_REASON)
            ?.let { "Received launch request: $it" }
            ?: "Received a new intent without a launch reason."
        recordLifecycle("NEW_INTENT")
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

        if (!ScummVMInputClient.sendButtonEvent(button.downEvent)) {
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

        ScummVMInputClient.sendButtonEvent(button.upEvent)
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
                ScummVMInputClient.sendButtonEvent(button.upEvent)
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
    onPointerSpeedSelected: (PointerSpeed) -> Unit,
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
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = settingsVisible || cropEditorModel != null) {
        if (cropEditorModel != null) onCancelCrop() else settingsVisible = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackpadBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
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
        } else if (settingsVisible) {
            PointerSpeedSettings(
                pointerSpeed = pointerSpeed,
                onPointerSpeedSelected = onPointerSpeedSelected,
                displayModePreferences = displayModePreferences,
                displayMode = displayMode,
                upperExpansionSupported = upperExpansionSupported,
                onPreferredDisplayModeChanged = onPreferredDisplayModeChanged,
                cropConfigurationEnabled = mirrorSourceGeometry?.isSupported == true,
                onConfigureCrop = {
                    settingsVisible = false
                    onOpenCropEditor()
                },
                onClose = { settingsVisible = false },
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
                    ?.let { maxWidth / it }
                    ?: 0.dp
                val maximumInterfaceHeight = (maxHeight - NormalLayoutReservedHeight).coerceAtLeast(0.dp)
                val interfaceHeight = minOf(desiredInterfaceHeight, maximumInterfaceHeight)
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
                            onViewAvailable = onMirrorViewAvailable,
                            onViewDisposed = onMirrorViewDisposed,
                        )
                    }

                    TouchSurface(
                        touchState = touchState,
                        gestureResetGeneration = gestureResetGeneration,
                        touchProvenance = touchProvenance,
                        pointerSpeed = pointerSpeed,
                        onGesture = onGesture,
                        onGestureDiagnostic = onGestureDiagnostic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = MinimumNormalTrackpadHeight)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )

                    if (diagnosticsVisible) {
                        MouseDiagnosticsPanel(
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
                            onRestoreBothScreens = onRestoreBothScreens,
                        )
                    }

                    MouseButtonControls(
                        diagnostics = mouseDiagnostics,
                        onButtonDown = onButtonDown,
                        onButtonUp = onButtonUp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    CompactActivityHeader(
                        isScummVMConnected = connectionDiagnostics.isConnected,
                        diagnosticsVisible = diagnosticsVisible,
                        onOpenSettings = {
                            diagnosticsVisible = false
                            settingsVisible = true
                        },
                        onToggleDiagnostics = { diagnosticsVisible = !diagnosticsVisible },
                        onRestoreTrackpad = onRestoreTrackpad,
                    )
                }
            }
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
    onViewAvailable: (MirrorHost) -> Unit,
    onViewDisposed: (MirrorHost) -> Unit,
) {
    val context = LocalContext.current
    val mirrorHost = remember(context) { createMirrorHost(context) }
    var panelWidth by remember { mutableStateOf(0) }
    var panelHeightPixels by remember { mutableStateOf(0) }

    DisposableEffect(mirrorHost) {
        onViewAvailable(mirrorHost)
        onDispose { onViewDisposed(mirrorHost) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(Color.Black)
            .onSizeChanged {
                panelWidth = it.width
                panelHeightPixels = it.height
            },
    ) {
        val panelGeometry = geometry?.let {
            lowerPanelGeometry(panelWidth, panelHeightPixels, crop, it.width, it.height, it.orientation)
        }
        AndroidView(
            factory = { mirrorHost.view },
            update = {
                mirrorHost.configureDirectTouch(
                    crop = crop,
                    geometry = geometry,
                    cropGeneration = cropGeneration,
                    enabled = displayMode == DisplayMode.INTERFACE &&
                        status.state == MirrorOutputState.SUPPORTED,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
        val cursorPoint = cursorState
            .takeIf { it.visible && it.geometryGeneration == geometry?.generation }
            ?.point
            ?.let { panelGeometry?.mapSource(it) }
        if (cursorPoint != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(cursorPoint.x, cursorPoint.y)
                val radius = 7.dp.toPx()
                val gap = 2.dp.toPx()
                val stroke = 1.dp.toPx()
                drawLine(Color.Black, center.copy(x = center.x - radius), center.copy(x = center.x - gap), stroke + 2f)
                drawLine(Color.Black, center.copy(x = center.x + gap), center.copy(x = center.x + radius), stroke + 2f)
                drawLine(Color.Black, center.copy(y = center.y - radius), center.copy(y = center.y - gap), stroke + 2f)
                drawLine(Color.Black, center.copy(y = center.y + gap), center.copy(y = center.y + radius), stroke + 2f)
                drawLine(PrimaryText, center.copy(x = center.x - radius), center.copy(x = center.x - gap), stroke)
                drawLine(PrimaryText, center.copy(x = center.x + gap), center.copy(x = center.x + radius), stroke)
                drawLine(PrimaryText, center.copy(y = center.y - radius), center.copy(y = center.y - gap), stroke)
                drawLine(PrimaryText, center.copy(y = center.y + gap), center.copy(y = center.y + radius), stroke)
            }
        }
    }
}

@Composable
private fun PointerSpeedSettings(
    pointerSpeed: PointerSpeed,
    onPointerSpeedSelected: (PointerSpeed) -> Unit,
    displayModePreferences: DisplayModePreferences,
    displayMode: DisplayMode,
    upperExpansionSupported: Boolean,
    onPreferredDisplayModeChanged: (DisplayMode) -> Unit,
    cropConfigurationEnabled: Boolean,
    onConfigureCrop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdventurePadScrollablePage(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(TouchSurfaceBackground)
            .border(2.dp, TouchSurfaceBorder),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SETTINGS",
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
            ) {
                Text("CLOSE")
            }
        }
        Text(
            text = "Current: ${pointerSpeed.label}",
            color = PrimaryText,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PointerSpeed.entries.forEach { option ->
                OutlinedButton(
                    onClick = { onPointerSpeedSelected(option) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (option == pointerSpeed) {
                            ButtonPressedBackground
                        } else {
                            Color.Transparent
                        },
                        contentColor = PrimaryText,
                    ),
                    border = BorderStroke(
                        width = if (option == pointerSpeed) 2.dp else 1.dp,
                        color = if (option == pointerSpeed) PrimaryText else TouchSurfaceBorder,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = option.label,
                        fontWeight = if (option == pointerSpeed) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = "Default Mode",
            color = PrimaryText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayMode.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { onPreferredDisplayModeChanged(mode) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (displayModePreferences.preferredMode == mode) {
                            ButtonPressedBackground
                        } else {
                            Color.Transparent
                        },
                        contentColor = PrimaryText,
                    ),
                    border = BorderStroke(1.dp, TouchSurfaceBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (mode == DisplayMode.INTERFACE) "SPLIT VIEW" else "TRACKPAD")
                }
            }
        }
        Text(
            text = "Current mode: ${if (displayMode == DisplayMode.INTERFACE) "SPLIT VIEW" else "TRACKPAD"}",
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Shortcut: double-tap with two fingers on the trackpad",
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Shortcut: L2 + R2",
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!upperExpansionSupported) {
            Text(
                text = "Set a valid split to enable Split View Mode.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = onConfigureCrop,
            enabled = cropConfigurationEnabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
            border = BorderStroke(1.dp, TouchSurfaceBorder),
        ) {
            Text("CONFIGURE SPLIT VIEW")
        }
    }
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
    var viewportHeight by remember { mutableStateOf(1) }
    val latestModel by rememberUpdatedState(model)

    DisposableEffect(mirrorHost) {
        onViewAvailable(mirrorHost)
        onDispose { onViewDisposed(mirrorHost) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(TouchSurfaceBackground)
            .border(2.dp, TouchSurfaceBorder)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "CONFIGURE SPLIT VIEW",
                color = PrimaryText,
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
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(model.sourceAspectRatio)
                .background(Color.Black)
                .border(3.dp, PrimaryText)
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
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(0f, splitY),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - splitY),
                )
                drawLine(
                    color = PrimaryText,
                    start = Offset(0f, splitY),
                    end = Offset(size.width, splitY),
                    strokeWidth = 6f,
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, splitY + 7f),
                    end = Offset(size.width, splitY + 7f),
                    strokeWidth = 2f,
                )
            }
            Text(
                text = "GAME",
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            Text(
                text = "SPLIT VIEW",
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        if (cropNeedsReview) {
            Text("Crop needs review", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Split: ${(model.split.ratio * 100f).formatOneDecimal()}% • " +
                "Split View: ${((1f - model.split.ratio) * 100f).formatOneDecimal()}%",
            color = SecondaryText,
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
        border = BorderStroke(1.dp, TouchSurfaceBorder),
        modifier = modifier,
    ) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Float.formatOneDecimal(): String = String.format(Locale.ROOT, "%.1f", this)

@Composable
private fun CompactActivityHeader(
    isScummVMConnected: Boolean,
    diagnosticsVisible: Boolean,
    onOpenSettings: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onRestoreTrackpad: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionIndicator(isConnected = isScummVMConnected)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onOpenSettings,
            colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text(text = "SETTINGS", maxLines = 1)
        }
        TextButton(
            onClick = onToggleDiagnostics,
            colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text(
                text = if (diagnosticsVisible) "HIDE DIAGNOSTICS" else "DIAGNOSTICS",
                maxLines = 1,
            )
        }
        OutlinedButton(
            onClick = onRestoreTrackpad,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText),
            border = BorderStroke(1.dp, TouchSurfaceBorder),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text(
                text = "RESTORE TRACKPAD",
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(isConnected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(StatusBackground)
            .border(1.dp, TouchSurfaceBorder)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (isConnected) "CONNECTED" else "DISCONNECTED",
            color = if (isConnected) PrimaryText else SecondaryText,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
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
    modifier: Modifier = Modifier,
) {
    val currentState by touchState
    val currentPointerSpeed by rememberUpdatedState(pointerSpeed)
    val viewConfiguration = LocalViewConfiguration.current
    val androidViewConfiguration = ViewConfiguration.get(LocalContext.current)

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
                    val pointerInputScope = this
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                composeTouchDiagnostics.record(event)
                                val motionEvent = event.motionEvent
                                val pressedCount = event.changes.count { it.pressed }
                                if (event.type == PointerEventType.Press && pressedCount == 1) {
                                    val pointer = event.changes.first { it.pressed }
                                    activeSequenceToken = touchProvenance.claimComposeDown(
                                        generation = gestureResetGeneration,
                                        downTimeMillis = motionEvent?.downTime,
                                        pointerId = motionEvent?.let {
                                            it.getPointerId(it.actionIndex)
                                        } ?: pointer.id.value.toInt(),
                                    )
                                }
                                val invalidSequenceDown =
                                    event.type == PointerEventType.Press &&
                                        pressedCount == 1 &&
                                        activeSequenceToken == null
                                val terminalRelease = event.type == PointerEventType.Release &&
                                    pressedCount == 0
                                val platformCancel =
                                    motionEvent?.actionMasked == MotionEvent.ACTION_CANCEL
                                val provenanceTerminal = terminalRelease || platformCancel
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
                                    gestureTracker.handle(event, releaseVerdict)
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
                                if (provenanceTerminal) {
                                    touchProvenance.complete(
                                        activeSequenceToken,
                                        gestureResetGeneration,
                                    )
                                    activeSequenceToken = null
                                }
                                val nextState = handlePointerEvent(
                                    event = event,
                                    previousState = touchState.value,
                                    surfaceWidth = size.width.toFloat(),
                                    surfaceHeight = size.height.toFloat(),
                                    allowMovement = gestureUpdate.allowMovement,
                                    updateMovementBaseline = gestureUpdate.updateMovementBaseline,
                                    resetMovementBaseline = gestureUpdate.resetMovementBaseline,
                                    pointerSpeed = currentPointerSpeed,
                                )
                                touchState.value = nextState
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
    }
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

    fun handle(
        event: PointerEvent,
        releaseVerdict: TouchReleaseVerdict? = null,
    ): GestureUpdate {
        val pressedCount = event.changes.count { it.pressed }
        when (event.type) {
            PointerEventType.Press -> {
                if (state == GestureTrackingState.IDLE && pressedCount == 1) {
                    val pointer = event.changes.first { it.pressed }
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
                    event.changes.filter { it.pressed }.forEach { pointer ->
                        initialPositions.putIfAbsent(pointer.id, pointer.position)
                    }
                    clearPreviousTap()
                    state = GestureTrackingState.TWO_FINGER_PENDING
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
                        val pointer = event.changes.singleOrNull { it.pressed }
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
                        val pointer = event.changes.singleOrNull { it.pressed }
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
                    if (releaseVerdict?.accepted != true) {
                        return rejectRelease(
                            releaseVerdict ?: TouchReleaseVerdict.rejected(
                                sequenceId = null,
                                generation = null,
                                reason = TapRejectionReason.NO_GENUINE_PLATFORM_ACTION_UP,
                            ),
                        )
                    }
                    val finalUptimeMillis = event.changes.maxOfOrNull { it.uptimeMillis }
                        ?: downUptimeMillis
                    val duration = finalUptimeMillis - downUptimeMillis
                    val completedState = state
                    val displacement = maximumDisplacement(event)
                    val gesture = when (completedState) {
                        GestureTrackingState.SINGLE_PENDING -> {
                            if (!hasPointerExceededTolerance(event) &&
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
                            if (!hasPointerExceededTolerance(event) &&
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
        state == GestureTrackingState.TWO_FINGER_PENDING

    fun invalidateFromProvenance(verdict: TouchReleaseVerdict): GestureUpdate =
        rejectRelease(verdict)

    private fun hasPointerExceededTolerance(event: PointerEvent): Boolean =
        event.changes.any { pointer ->
            val initialPosition = initialPositions[pointer.id]
            initialPosition == null ||
                pointer.distanceSquaredFrom(initialPosition) > touchSlopSquared
        }

    private fun PointerInputChange.distanceSquaredFrom(position: Offset): Float =
        (this.position - position).getDistanceSquared()

    private fun maximumDisplacement(event: PointerEvent): Float = event.changes.maxOfOrNull {
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
    val diagnostics: List<String> = emptyList(),
)

private enum class GestureTrackingState {
    IDLE,
    SINGLE_PENDING,
    SECOND_TAP_HOLD_PENDING,
    DOUBLE_TAP_HOLD_ACTIVE,
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
    updateMovementBaseline: Boolean,
    resetMovementBaseline: Boolean,
    pointerSpeed: PointerSpeed,
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
            if (updateMovementBaseline || resetMovementBaseline) {
                val trackedPointer = event.findPressedPointer(state.trackedPointerId)
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
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
            .heightIn(min = 68.dp)
            .background(if (isDown) ButtonPressedBackground else ButtonBackground)
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
            .padding(vertical = 18.dp),
    ) {
        Text(
            text = label,
            color = PrimaryText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MouseDiagnosticsPanel(
    diagnostics: MouseDiagnostics,
    displayId: Int,
    connectionDiagnostics: ScummVMConnectionDiagnostics,
    mirrorCropDiagnostics: MirrorCropDiagnostics,
    onRestoreBothScreens: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(StatusBackground)
            .border(1.dp, TouchSurfaceBorder)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Developer action: can replace the app on the upper display.",
                color = SecondaryText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onRestoreBothScreens,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText),
                border = BorderStroke(1.dp, TouchSurfaceBorder),
            ) {
                Text(text = "RESTORE BOTH SCREENS", maxLines = 1)
            }
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
        color = SecondaryText,
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

private val TrackpadBackground = Color(0xFF181A1D)
private val TouchSurfaceBackground = Color(0xFF24272B)
private val TouchSurfaceBorder = Color(0xFF5A6068)
private val ButtonBackground = Color(0xFF2D3136)
private val ButtonPressedBackground = Color(0xFF4B5158)
private val StatusBackground = Color(0xFF202328)
private val PrimaryText = Color(0xFFF2F3F5)
private val SecondaryText = Color(0xFFB8BDC4)
private val MarkerColor = Color(0xFFD9DDE2)
private val MarkerRadius = 16.dp
private val MarkerOutlineWidth = 3.dp
private val MinimumNormalTrackpadHeight = 120.dp
private val NormalLayoutReservedHeight = 252.dp
private const val MaxMoveEventCount = 999_999
private const val AdventurePadBridgeTag = "AdventurePadBridge"
private const val RAW_TOUCH_TAG = "AdventurePadRawTouch"
private const val RAW_SEQUENCE_LIMIT = 2
