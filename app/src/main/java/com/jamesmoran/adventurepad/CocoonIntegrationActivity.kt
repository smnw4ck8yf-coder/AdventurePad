package com.jamesmoran.adventurepad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Frontend-neutral setup backed by non-privileged SAF access and launcher-specific adapters. */
class CocoonIntegrationActivity : ComponentActivity() {
    private lateinit var libraryClient: ScummVMLibraryClient
    private lateinit var integrationPreferences: CocoonIntegrationPreferences
    private lateinit var launcherPointerInput: LauncherPointerInput

    private var libraryState by mutableStateOf(ScummVMLibraryState())
    private var selectedTreeUri by mutableStateOf<Uri?>(null)
    private var folderPermissionValid by mutableStateOf(false)
    private var folderName by mutableStateOf<String?>(null)
    private var selectedLauncherId by mutableStateOf<String?>(null)
    private var detectedLauncherIds by mutableStateOf<Set<String>>(emptySet())
    private var preview by mutableStateOf<ExternalLauncherPreparationPlan?>(null)
    private var busy by mutableStateOf(false)
    private var statusMessage by mutableStateOf<String?>(null)
    private var setupComplete by mutableStateOf(false)
    private var preparedGameCount by mutableStateOf(0)
    private var page by mutableStateOf(ExternalLaunchersPage.CHOOSE_LAUNCHER)
    private var reviewingUpdate by mutableStateOf(false)
    private var showDetails by mutableStateOf(false)
    private var ownsTopScreenCursor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launcherPointerInput = LauncherPointerInput(this)
        integrationPreferences = CocoonIntegrationPreferences(this)
        selectedTreeUri = integrationPreferences.treeUri
        folderPermissionValid = integrationPreferences.hasPersistedReadWritePermission(contentResolver)
        folderName = selectedTreeUri?.let(::treeDisplayName)
        setupComplete = integrationPreferences.setupComplete
        preparedGameCount = integrationPreferences.preparedGameCount

        val installedPackages = SupportedExternalLaunchers
            .flatMap { it.definition.packageNames }
            .filterTo(linkedSetOf()) { packageManager.getLaunchIntentForPackage(it) != null }
        val selection = selectExternalLauncher(
            supported = SupportedExternalLaunchers.map(ExternalLauncherAdapter::definition),
            installedPackages = installedPackages,
            rememberedId = integrationPreferences.selectedLauncherId,
        )
        detectedLauncherIds = selection.detectedIds
        selectedLauncherId = selection.selectedId
        page = if (
            setupComplete && folderPermissionValid &&
            findExternalLauncher(selectedLauncherId) != null
        ) {
            ExternalLaunchersPage.READY
        } else {
            ExternalLaunchersPage.CHOOSE_LAUNCHER
        }

        libraryClient = ScummVMLibraryClient(
            context = this,
            onStateChanged = { libraryState = it },
            refreshSaveCapabilitiesOnConnect = false,
        )

        setContent {
            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) selectFolder(uri)
            }
            LaunchedEffect(
                selectedLauncherId,
                selectedTreeUri,
                folderPermissionValid,
                libraryState.targets,
                libraryState.loading,
                libraryState.error,
            ) {
                refreshPreview()
            }
            val skin = SkinRepository.get(this).resolve(SkinContext.LAUNCHER)
            AdventurePadSkinTheme(skin = skin) {
                Box(Modifier.fillMaxSize()) {
                    ExternalLaunchersScreen(
                        launchers = SupportedExternalLaunchers,
                        selectedLauncherId = selectedLauncherId,
                        detectedLauncherIds = detectedLauncherIds,
                        page = page,
                        libraryState = libraryState,
                        folderName = folderName,
                        folderPermissionValid = folderPermissionValid,
                        preview = preview,
                        busy = busy,
                        statusMessage = statusMessage,
                        reviewingUpdate = reviewingUpdate,
                        preparedGameCount = preparedGameCount,
                        showDetails = showDetails,
                        onBack = ::finish,
                        onSelectLauncher = ::selectLauncher,
                        onConfirmLauncher = ::confirmLauncher,
                        onLauncherBack = ::finish,
                        onFolderBack = { page = ExternalLaunchersPage.CHOOSE_LAUNCHER },
                        onFolderNext = ::confirmFolder,
                        onFolderSkip = ::confirmFolder,
                        onReviewBack = ::leaveReview,
                        onChooseFolder = { folderPicker.launch(selectedTreeUri) },
                        onRefreshPreview = ::refreshPreview,
                        onPrepare = ::prepareGames,
                        onReviewUpdate = ::reviewUpdate,
                        onRunSetupAgain = ::runSetupAgain,
                        onShowInstructions = { page = ExternalLaunchersPage.SUCCESS },
                        onFinishSuccess = { page = ExternalLaunchersPage.READY },
                        onToggleDetails = { showDetails = !showDetails },
                        onOpenLauncher = ::openSelectedLauncher,
                    )
                    AdventurePadLauncherCursor(
                        ownsTopScreen = ownsTopScreenCursor,
                        onStateChanged = launcherPointerInput::updateCursor,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        libraryClient.connect()
    }

    override fun onResume() {
        super.onResume()
        ownsTopScreenCursor = true
        launcherPointerInput.activate()
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

    private fun selectLauncher(id: String) {
        val adapter = findExternalLauncher(id) ?: return
        selectedLauncherId = adapter.definition.id
        statusMessage = null
    }

    private fun confirmLauncher() {
        val adapter = findExternalLauncher(selectedLauncherId) ?: return
        val changed = integrationPreferences.selectedLauncherId != adapter.definition.id
        integrationPreferences.selectedLauncherId = adapter.definition.id
        reviewingUpdate = false
        showDetails = false
        if (changed) clearCompletion()
        page = ExternalLaunchersPage.CHOOSE_FOLDER
        refreshPreview()
    }

    private fun confirmFolder() {
        if (!folderPermissionValid || selectedTreeUri == null || folderName == null) return
        page = ExternalLaunchersPage.REVIEW
        showDetails = false
        refreshPreview()
    }

    private fun leaveReview() {
        page = if (reviewingUpdate && setupComplete) {
            ExternalLaunchersPage.READY
        } else {
            ExternalLaunchersPage.CHOOSE_FOLDER
        }
        reviewingUpdate = false
        showDetails = false
    }

    private fun reviewUpdate() {
        reviewingUpdate = true
        showDetails = false
        statusMessage = null
        page = ExternalLaunchersPage.REVIEW
        refreshPreview()
    }

    private fun runSetupAgain() {
        reviewingUpdate = false
        showDetails = false
        statusMessage = null
        page = ExternalLaunchersPage.CHOOSE_LAUNCHER
    }

    private fun selectFolder(uri: Uri) {
        runCatching { persistCocoonTreePermission(this, uri) }
            .onSuccess {
                val changed = selectedTreeUri != uri
                selectedTreeUri = uri
                folderPermissionValid = true
                folderName = treeDisplayName(uri)
                preview = null
                reviewingUpdate = false
                if (changed) clearCompletion()
                statusMessage = null
            }
            .onFailure { error ->
                folderPermissionValid = false
                statusMessage = error.message ?: "AdventurePad could not retain access to that folder."
            }
    }

    private fun refreshPreview() {
        val uri = selectedTreeUri ?: run {
            preview = null
            return
        }
        val adapter = findExternalLauncher(selectedLauncherId) ?: run {
            preview = null
            return
        }
        if (!folderPermissionValid || libraryState.loading || libraryState.error != null || busy) {
            preview = null
            return
        }
        busy = true
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    preparerFor(uri, adapter).preview(
                        libraryState.targets,
                        SafCocoonExportDirectory(contentResolver, uri),
                    )
                }
            }.onSuccess {
                preview = it
                folderPermissionValid = true
            }.onFailure { error ->
                preview = null
                updateFolderPermissionState()
                statusMessage = if (folderPermissionValid) {
                    error.message ?: "AdventurePad could not read the selected folder."
                } else {
                    "Folder access was revoked. Select the prepared-games folder again."
                }
            }
            busy = false
        }
    }

    private fun prepareGames() {
        val uri = selectedTreeUri ?: return
        val adapter = findExternalLauncher(selectedLauncherId) ?: return
        if (!folderPermissionValid || busy) return
        busy = true
        lifecycleScope.launch {
            var finalPlan: ExternalLauncherPreparationPlan? = null
            runCatching {
                withContext(Dispatchers.IO) {
                    ExternalLauncherPreparationSerialiser.run {
                        val directory = SafCocoonExportDirectory(contentResolver, uri)
                        val preparer = preparerFor(uri, adapter)
                        val result = preparer.apply(
                            libraryState.targets,
                            directory,
                            FilePendingGameExportStore(this@CocoonIntegrationActivity, uri),
                        )
                        val refreshed = preparer.preview(libraryState.targets, directory)
                        result to refreshed
                    }
                }
            }.onSuccess { (result, refreshed) ->
                finalPlan = refreshed
                preview = refreshed
                folderPermissionValid = true
                val gameFailures = result.games.failures.size
                val gameConflicts = refreshed.games.count(CocoonExportAction.CONFLICT)
                val ready = refreshed.games.writableEntries.isEmpty() &&
                    refreshed.platformFile.action == PreparedFileAction.UNCHANGED &&
                    gameFailures == 0 && result.platformFileFailure == null
                if (ready) {
                    setupComplete = true
                    reviewingUpdate = false
                    preparedGameCount = refreshed.preparedGameCount
                    integrationPreferences.setupComplete = true
                    integrationPreferences.preparedGameCount = preparedGameCount
                    page = ExternalLaunchersPage.SUCCESS
                    statusMessage = if (gameConflicts == 0) {
                        "Prepared $preparedGameCount game(s). No game files were copied or changed."
                    } else {
                        "Prepared $preparedGameCount game(s). $gameConflicts conflicting shortcut(s) were safely skipped."
                    }
                } else {
                    setupComplete = false
                    integrationPreferences.setupComplete = false
                    statusMessage = buildString {
                        append("Preparation needs attention")
                        if (refreshed.conflicts > 0) append(": ${refreshed.conflicts} conflict(s)")
                        if (gameFailures > 0) append(": $gameFailures game shortcut write failure(s)")
                        result.platformFileFailure?.let { append(": $it") }
                    }
                }
            }.onFailure { error ->
                updateFolderPermissionState()
                statusMessage = if (folderPermissionValid) {
                    error.message ?: "AdventurePad could not prepare the launcher files."
                } else {
                    "Folder access was revoked. No further files were written."
                }
            }
            busy = false
            if (finalPlan == null) refreshPreview()
        }
    }

    private fun clearCompletion() {
        setupComplete = false
        preparedGameCount = 0
        integrationPreferences.setupComplete = false
        integrationPreferences.preparedGameCount = 0
    }

    private fun updateFolderPermissionState() {
        folderPermissionValid = integrationPreferences.hasPersistedReadWritePermission(contentResolver)
    }

    private fun openSelectedLauncher() {
        val adapter = findExternalLauncher(selectedLauncherId) ?: return
        val launchIntent = adapter.definition.packageNames
            .firstNotNullOfOrNull(packageManager::getLaunchIntentForPackage)
        if (launchIntent == null) {
            statusMessage = "${adapter.definition.displayName} is not installed or is not visible to AdventurePad."
            return
        }
        when (val handoff = LowerDisplayHandoffCoordinator.backgroundLauncherTrackpad()) {
            is LowerDisplayHandoffResult.RefusedGameplay -> {
                statusMessage = "AdventurePad kept the lower-screen gameplay companion active for " +
                    "'${handoff.targetId}'. Return from the game before opening the launcher."
                return
            }
            LowerDisplayHandoffResult.Failed -> {
                statusMessage = "AdventurePad could not safely release the lower display. The launcher was not opened."
                return
            }
            LowerDisplayHandoffResult.Backgrounded,
            LowerDisplayHandoffResult.NoActiveTrackpad,
            -> Unit
        }
        runCatching {
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { error ->
            DualDisplayCoordinator.launchTrackpad(
                activity = this,
                reason = "External launcher failed to open; restoring AdventurePad launcher controls",
            )
            statusMessage = error.message ?: "AdventurePad could not open ${adapter.definition.displayName}."
        }
    }

    private fun preparerFor(uri: Uri, adapter: ExternalLauncherAdapter) = ExternalLauncherPreparer(
        gameExporter = CocoonDptExporter(FileCocoonOwnershipStore(this, uri)),
        platformOwnershipStore = FilePreparedLauncherOwnershipStore(this, uri),
        adapter = adapter,
    )

    private fun treeDisplayName(uri: Uri): String = runCatching {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/')
    }.getOrDefault("Selected folder")
}

internal enum class ExternalLaunchersPage {
    CHOOSE_LAUNCHER,
    CHOOSE_FOLDER,
    REVIEW,
    SUCCESS,
    READY,
}

@Composable
internal fun ExternalLaunchersScreen(
    launchers: List<ExternalLauncherAdapter>,
    selectedLauncherId: String?,
    detectedLauncherIds: Set<String>,
    page: ExternalLaunchersPage,
    libraryState: ScummVMLibraryState,
    folderName: String?,
    folderPermissionValid: Boolean,
    preview: ExternalLauncherPreparationPlan?,
    busy: Boolean,
    statusMessage: String?,
    reviewingUpdate: Boolean,
    preparedGameCount: Int,
    showDetails: Boolean,
    onBack: () -> Unit,
    onSelectLauncher: (String) -> Unit,
    onConfirmLauncher: () -> Unit,
    onLauncherBack: () -> Unit,
    onFolderBack: () -> Unit,
    onFolderNext: () -> Unit,
    onFolderSkip: () -> Unit,
    onReviewBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onRefreshPreview: () -> Unit,
    onPrepare: () -> Unit,
    onReviewUpdate: () -> Unit,
    onRunSetupAgain: () -> Unit,
    onShowInstructions: () -> Unit,
    onFinishSuccess: () -> Unit,
    onToggleDetails: () -> Unit,
    onOpenLauncher: () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    val selectedLauncher = findExternalLauncher(selectedLauncherId, launchers)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.launcherAccent)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 1120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "External Launchers",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = palette.launcherInk,
                    )
                    Text("Prepare your ScummVM games for a supported launcher.", color = palette.launcherInk)
                }
                OutlinedButton(onClick = onBack) { Text("Close") }
            }

            when (page) {
                ExternalLaunchersPage.CHOOSE_LAUNCHER -> LauncherChoice(
                    launchers = launchers,
                    selectedLauncherId = selectedLauncherId,
                    detectedLauncherIds = detectedLauncherIds,
                    onSelectLauncher = onSelectLauncher,
                    onBack = onLauncherBack,
                    onNext = onConfirmLauncher,
                )
                ExternalLaunchersPage.CHOOSE_FOLDER -> FolderChoice(
                    folderName = folderName,
                    folderPermissionValid = folderPermissionValid,
                    busy = busy,
                    onChooseFolder = onChooseFolder,
                    onBack = onFolderBack,
                    onSkip = onFolderSkip,
                    onNext = onFolderNext,
                )
                ExternalLaunchersPage.REVIEW -> PreparationReview(
                    launcher = selectedLauncher,
                    folderName = folderName,
                    libraryState = libraryState,
                    preview = preview,
                    busy = busy,
                    reviewingUpdate = reviewingUpdate,
                    onRefreshPreview = onRefreshPreview,
                    onPrepare = onPrepare,
                    onBack = onReviewBack,
                    onToggleDetails = onToggleDetails,
                )
                ExternalLaunchersPage.SUCCESS -> PreparationSuccess(
                    launcher = selectedLauncher,
                    launcherInstalled = selectedLauncherId in detectedLauncherIds,
                    folderName = folderName,
                    preparedGameCount = preparedGameCount,
                    gameConflicts = preview?.games?.count(CocoonExportAction.CONFLICT) ?: 0,
                    onOpenLauncher = onOpenLauncher,
                    onDone = onFinishSuccess,
                )
                ExternalLaunchersPage.READY -> LauncherStatus(
                    launcher = selectedLauncher,
                    launcherInstalled = selectedLauncherId in detectedLauncherIds,
                    folderName = folderName,
                    preparedGameCount = preparedGameCount,
                    busy = busy,
                    onReviewUpdate = onReviewUpdate,
                    onRunSetupAgain = onRunSetupAgain,
                    onShowInstructions = onShowInstructions,
                    onOpenLauncher = onOpenLauncher,
                )
            }

            statusMessage?.takeIf { page != ExternalLaunchersPage.SUCCESS }?.let {
                Surface(
                    color = palette.launcherContent,
                    contentColor = palette.launcherInk,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(it, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (page == ExternalLaunchersPage.REVIEW && showDetails && preview != null) {
            PreparationDetailsOverlay(
                plan = preview,
                onClose = onToggleDetails,
            )
        }
    }
}

@Composable
private fun LauncherChoice(
    launchers: List<ExternalLauncherAdapter>,
    selectedLauncherId: String?,
    detectedLauncherIds: Set<String>,
    onSelectLauncher: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    WizardStep(
        number = 1,
        question = "Which launcher do you use?",
        body = "Choose a supported launcher. AdventurePad detected one for you when possible.",
        modifier = Modifier.testTag("external-launchers-page-launcher"),
    ) {
        launchers.forEach { launcher ->
            val id = launcher.definition.id
            val selected = id == selectedLauncherId
            Surface(
                color = AdventurePadThemeTokens.components.launcherContent,
                contentColor = AdventurePadThemeTokens.components.launcherInk,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    if (selected) 3.dp else 1.dp,
                    AdventurePadThemeTokens.components.launcherInk.copy(alpha = if (selected) 0.8f else 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { onSelectLauncher(id) },
                    modifier = Modifier.fillMaxWidth().testTag("launcher-option-$id"),
                ) {
                    val installed = if (id in detectedLauncherIds) "Installed" else "Not installed"
                    Text(
                        "${launcher.definition.displayName}  •  $installed${if (selected) "  •  Selected" else ""}",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        WizardNavigation(
            onBack = onBack,
            onNext = onNext,
            nextEnabled = selectedLauncherId != null,
        )
    }
}

@Composable
private fun FolderChoice(
    folderName: String?,
    folderPermissionValid: Boolean,
    busy: Boolean,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val validFolder = folderName != null && folderPermissionValid
    WizardStep(
        number = 2,
        question = "Where should AdventurePad prepare launcher files?",
        body = "AdventurePad puts small shortcut and setup files in this folder. Your actual games are not copied, moved or changed.",
        modifier = Modifier.testTag("external-launchers-page-folder"),
    ) {
        Surface(
            color = AdventurePadThemeTokens.components.launcherContent,
            contentColor = AdventurePadThemeTokens.components.launcherInk,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AdventurePadThemeTokens.components.launcherInk.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Prepared-games folder", style = MaterialTheme.typography.labelLarge)
                Text(
                    when {
                        folderName == null -> "No folder selected"
                        folderPermissionValid -> folderName
                        else -> "$folderName — access needs renewing"
                    },
                    fontWeight = FontWeight.Black,
                )
            }
        }
        OutlinedButton(onClick = onChooseFolder, enabled = !busy) {
            Text(if (folderName == null) "Choose Folder" else "Choose a Different Folder")
        }
        WizardNavigation(
            onBack = onBack,
            onSkip = onSkip,
            skipEnabled = validFolder && !busy,
            onNext = onNext,
            nextEnabled = validFolder && !busy,
        )
    }
}

@Composable
private fun PreparationReview(
    launcher: ExternalLauncherAdapter?,
    folderName: String?,
    libraryState: ScummVMLibraryState,
    preview: ExternalLauncherPreparationPlan?,
    busy: Boolean,
    reviewingUpdate: Boolean,
    onRefreshPreview: () -> Unit,
    onPrepare: () -> Unit,
    onBack: () -> Unit,
    onToggleDetails: () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    val platformConflict = preview?.platformFile?.action == PreparedFileAction.CONFLICT
    val gameConflicts = preview?.games?.count(CocoonExportAction.CONFLICT) ?: 0
    WizardStep(
        number = 3,
        question = if (reviewingUpdate) "Ready to update your prepared games?" else "Ready to prepare your games?",
        body = "Check the summary, then let AdventurePad prepare the launcher files.",
        modifier = Modifier.testTag("external-launchers-page-review"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            SummaryValue("Launcher", launcher?.definition?.displayName ?: "Not selected", Modifier.weight(1f))
            SummaryValue("Folder", folderName ?: "Not selected", Modifier.weight(1f))
            SummaryValue("Configured games", libraryState.targets.size.toString(), Modifier.weight(1f))
        }
        when {
            libraryState.loading -> Text("Loading configured ScummVM games…")
            libraryState.error != null -> Text(libraryState.error)
            preview == null -> {
                Text(if (busy) "Checking the prepared folder…" else "The preparation preview is unavailable.")
                if (!busy) OutlinedButton(onClick = onRefreshPreview) { Text("Try Again") }
            }
            else -> {
                PreparationSummary(preview)
                if (platformConflict) {
                    ConflictWarning(
                        "AdventurePad cannot safely update the launcher setup file. Resolve this conflict before preparing games.",
                    )
                } else if (gameConflicts > 0) {
                    ConflictWarning(
                        "$gameConflicts game shortcut conflict(s) will be skipped. All unaffected games can still be prepared.",
                    )
                }
                OutlinedButton(
                    onClick = onToggleDetails,
                    modifier = Modifier.testTag("view-preparation-details"),
                ) { Text("View Details") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.testTag("wizard-back")) {
                Text("Back")
            }
            Button(
                onClick = onPrepare,
                enabled = !busy && preview != null && !platformConflict && libraryState.error == null,
                colors = ButtonDefaults.buttonColors(containerColor = palette.launcherAccentDark),
                modifier = Modifier.testTag("prepare-games"),
            ) { Text(if (busy) "Preparing…" else "Prepare Games") }
        }
    }
}

@Composable
private fun PreparationDetailsOverlay(
    plan: ExternalLauncherPreparationPlan,
    onClose: () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.launcherInk.copy(alpha = 0.48f))
            .clickable(onClick = {})
            .testTag("preparation-details-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = palette.launcherContent,
            contentColor = palette.launcherInk,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, palette.launcherInk.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.84f)
                .widthIn(max = 920.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Preparation details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(
                            "Technical filenames and exact ScummVM target aliases",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close-preparation-details"),
                    ) { Text("Close") }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("preparation-details"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { PreparedPlatformFileRow(plan.platformFile) }
                    items(plan.games.entries, key = { it.targetId }) { entry ->
                        PreparedGameRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherStatus(
    launcher: ExternalLauncherAdapter?,
    launcherInstalled: Boolean,
    folderName: String?,
    preparedGameCount: Int,
    busy: Boolean,
    onReviewUpdate: () -> Unit,
    onRunSetupAgain: () -> Unit,
    onShowInstructions: () -> Unit,
    onOpenLauncher: () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    ContentCard(
        modifier = Modifier.testTag("external-launchers-page-ready"),
        title = "Ready",
        body = "Your prepared games can be updated whenever your ScummVM library changes.",
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            SummaryValue("Launcher", launcher?.definition?.displayName ?: "Not selected", Modifier.weight(1f))
            SummaryValue("Prepared games", preparedGameCount.toString(), Modifier.weight(1f))
            SummaryValue("Folder", folderName ?: "Not selected", Modifier.weight(1f))
        }
        Button(
            onClick = onReviewUpdate,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = palette.launcherAccentDark),
            modifier = Modifier.testTag("update-prepared-games"),
        ) { Text("Update Prepared Games") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRunSetupAgain,
                enabled = !busy,
                modifier = Modifier.testTag("run-setup-again"),
            ) { Text("Run Setup Again") }
            OutlinedButton(onClick = onShowInstructions, enabled = !busy) { Text("Setup Instructions") }
            Button(onClick = onOpenLauncher, enabled = launcherInstalled && !busy) {
                Text("Open Launcher")
            }
        }
    }
}

@Composable
private fun PreparationSuccess(
    launcher: ExternalLauncherAdapter?,
    launcherInstalled: Boolean,
    folderName: String?,
    preparedGameCount: Int,
    gameConflicts: Int,
    onOpenLauncher: () -> Unit,
    onDone: () -> Unit,
) {
    val displayName = launcher?.definition?.displayName ?: "your launcher"
    ContentCard(
        modifier = Modifier.testTag("external-launchers-page-success"),
        title = "Games prepared successfully",
        body = "AdventurePad prepared $preparedGameCount game(s) for $displayName.",
    ) {
        if (gameConflicts > 0) {
            ConflictWarning("$gameConflicts conflicting shortcut(s) were left untouched.")
        }
        Text("Finish setup in $displayName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        launcher?.setupInstructions(folderName ?: "the selected folder")?.forEachIndexed { index, instruction ->
            Text("${index + 1}. $instruction")
        }
        Text(
            "This is a community compatibility feature and is not affiliated with or endorsed by $displayName.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (launcher?.definition?.id == COCOON_LAUNCHER_ID) Text(
            "Known issue: Cocoon may not attribute playtime or sessions when AdventurePad launches ScummVM.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onDone) { Text("Done") }
            Button(onClick = onOpenLauncher, enabled = launcherInstalled) { Text("Open Launcher") }
        }
    }
}

@Composable
private fun WizardStep(
    number: Int,
    question: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ContentCard(
        modifier = modifier,
        title = "Step $number of 3  •  $question",
        body = body,
        content = content,
    )
}

@Composable
private fun ContentCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    Surface(
        color = palette.launcherContent,
        contentColor = palette.launcherInk,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            content()
        }
    }
}

@Composable
private fun WizardNavigation(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    onSkip: (() -> Unit)? = null,
    skipEnabled: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = onBack, modifier = Modifier.testTag("wizard-back")) { Text("Back") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onSkip != null) {
                OutlinedButton(
                    onClick = onSkip,
                    enabled = skipEnabled,
                    modifier = Modifier.testTag("wizard-skip"),
                ) { Text("Skip") }
            }
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier.testTag("wizard-next"),
            ) { Text("Next") }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, fontWeight = FontWeight.Black, maxLines = 2)
    }
}

@Composable
private fun ConflictWarning(message: String) {
    val palette = AdventurePadThemeTokens.components
    Surface(
        color = palette.launcherAccent,
        contentColor = palette.launcherInk,
        border = BorderStroke(2.dp, palette.launcherInk.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("preparation-conflict-warning"),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PreparationSummary(plan: ExternalLauncherPreparationPlan) {
    Text(
        "${plan.games.count(CocoonExportAction.ADD)} additions • " +
            "${plan.games.count(CocoonExportAction.UPDATE)} updates • " +
            "${plan.games.count(CocoonExportAction.CONFLICT)} conflicts • " +
            "${plan.games.count(CocoonExportAction.UNCHANGED)} unchanged",
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun PreparedPlatformFileRow(plan: PreparedFilePlan) {
    val label = plan.action.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label — ${plan.fileName}", fontWeight = FontWeight.Bold)
        Text("Launcher setup file", style = MaterialTheme.typography.bodySmall)
        plan.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PreparedGameRow(entry: CocoonExportEntry) {
    val label = entry.action.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label — ${entry.title}", fontWeight = FontWeight.Bold)
        Text("Target alias: ${entry.targetId} • ${entry.fileName}", style = MaterialTheme.typography.bodySmall)
        entry.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
