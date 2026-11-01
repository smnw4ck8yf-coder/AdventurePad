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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Guided, non-privileged Cocoon setup and managed .dpt export. */
class CocoonIntegrationActivity : ComponentActivity() {
    private lateinit var libraryClient: ScummVMLibraryClient
    private lateinit var integrationPreferences: CocoonIntegrationPreferences

    private var libraryState by mutableStateOf(ScummVMLibraryState())
    private var selectedTreeUri by mutableStateOf<Uri?>(null)
    private var folderPermissionValid by mutableStateOf(false)
    private var folderName by mutableStateOf<String?>(null)
    private var preview by mutableStateOf<CocoonExportPlan?>(null)
    private var busy by mutableStateOf(false)
    private var statusMessage by mutableStateOf<String?>(null)
    private var cocoonInstalled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        integrationPreferences = CocoonIntegrationPreferences(this)
        selectedTreeUri = integrationPreferences.treeUri
        folderPermissionValid = integrationPreferences.hasPersistedReadWritePermission(contentResolver)
        folderName = selectedTreeUri?.let(::cocoonTreeDisplayName)
        cocoonInstalled = packageManager.getLaunchIntentForPackage(COCOON_PACKAGE) != null
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
                CocoonIntegrationScreen(
                    cocoonInstalled = cocoonInstalled,
                    libraryState = libraryState,
                    folderName = folderName,
                    folderPermissionValid = folderPermissionValid,
                    preview = preview,
                    busy = busy,
                    statusMessage = statusMessage,
                    onBack = ::finish,
                    onChooseFolder = { folderPicker.launch(selectedTreeUri) },
                    onRefreshPreview = ::refreshPreview,
                    onExport = ::exportPlan,
                    onOpenCocoon = ::openCocoon,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        libraryClient.connect()
    }

    override fun onStop() {
        libraryClient.disconnect()
        super.onStop()
    }

    private fun selectFolder(uri: Uri) {
        runCatching { persistCocoonTreePermission(this, uri) }
            .onSuccess {
                selectedTreeUri = uri
                folderPermissionValid = true
                folderName = cocoonTreeDisplayName(uri)
                preview = null
                statusMessage = "Folder access saved. Review the export preview below."
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
        if (!folderPermissionValid || libraryState.loading || libraryState.error != null) {
            preview = null
            return
        }
        val targets = libraryState.targets
        busy = true
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    exporterFor(uri).preview(targets, SafCocoonExportDirectory(contentResolver, uri))
                }
            }.onSuccess {
                preview = it
                folderPermissionValid = true
            }.onFailure { error ->
                preview = null
                folderPermissionValid = integrationPreferences
                    .hasPersistedReadWritePermission(contentResolver)
                statusMessage = if (folderPermissionValid) {
                    error.message ?: "AdventurePad could not read the selected folder."
                } else {
                    "Folder access was revoked. Select Cocoon's ScummVM folder again."
                }
            }
            busy = false
        }
    }

    private fun exportPlan() {
        val uri = selectedTreeUri ?: return
        if (!folderPermissionValid || busy) return
        busy = true
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val directory = SafCocoonExportDirectory(contentResolver, uri)
                    val exporter = exporterFor(uri)
                    // Re-plan immediately before applying so the preview can never authorize a stale overwrite.
                    val current = exporter.preview(libraryState.targets, directory)
                    exporter.apply(current, directory)
                }
            }.onSuccess { result ->
                val summary = "Exported ${result.written.size} file(s)"
                statusMessage = if (result.failures.isEmpty()) {
                    "$summary. AdventurePad did not delete any older exports."
                } else {
                    "$summary; ${result.failures.size} file(s) were skipped because they changed or failed."
                }
                folderPermissionValid = true
            }.onFailure { error ->
                folderPermissionValid = integrationPreferences
                    .hasPersistedReadWritePermission(contentResolver)
                statusMessage = if (folderPermissionValid) {
                    error.message ?: "AdventurePad could not complete the export."
                } else {
                    "Folder access was revoked. No further files were written."
                }
            }
            busy = false
            refreshPreview()
        }
    }

    private fun openCocoon() {
        val launchIntent = packageManager.getLaunchIntentForPackage(COCOON_PACKAGE)
        if (launchIntent == null) {
            statusMessage = "Cocoon is not installed or is not visible to AdventurePad."
            return
        }
        startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun exporterFor(uri: Uri): CocoonDptExporter = CocoonDptExporter(
        FileCocoonOwnershipStore(this, uri),
    )

    private fun cocoonTreeDisplayName(uri: Uri): String = runCatching {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/')
    }.getOrDefault("Selected folder")

    private companion object {
        const val COCOON_PACKAGE = "rip.moth.cocoonshell"
    }
}

@Composable
private fun CocoonIntegrationScreen(
    cocoonInstalled: Boolean,
    libraryState: ScummVMLibraryState,
    folderName: String?,
    folderPermissionValid: Boolean,
    preview: CocoonExportPlan?,
    busy: Boolean,
    statusMessage: String?,
    onBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onRefreshPreview: () -> Unit,
    onExport: () -> Unit,
    onOpenCocoon: () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.launcherAccent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "Cocoon Integration",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = palette.launcherInk,
                )
                Text(
                    "Export AdventurePad's configured targets as safe Cocoon tag files.",
                    color = palette.launcherInk,
                )
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        SetupStep(
            number = 1,
            title = "Select Cocoon's ScummVM folder",
            body = "Choose the folder Cocoon scans for ScummVM .dpt files. AdventurePad receives access only " +
                "to this folder and never requests broad storage access.",
        ) {
            Text(
                when {
                    folderName == null -> "No folder selected"
                    folderPermissionValid -> "Selected: $folderName"
                    else -> "Access revoked: $folderName"
                },
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = onChooseFolder, enabled = !busy) {
                Text(if (folderName == null) "Choose folder" else "Choose again")
            }
        }

        SetupStep(
            number = 2,
            title = "Review and export configured games",
            body = "Target aliases come directly from the signature-protected ScummVM library. Conflicts are " +
                "skipped, user-created files are never overwritten, and old exports are not deleted.",
        ) {
            when {
                libraryState.loading -> Text("Loading configured ScummVM targets…")
                libraryState.error != null -> Text(libraryState.error)
                preview == null -> Text("Select an accessible folder to build the preview.")
                else -> {
                    ExportSummary(preview)
                    preview.entries.forEach { ExportPreviewRow(it) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRefreshPreview,
                    enabled = folderPermissionValid && !busy && !libraryState.loading,
                ) { Text("Refresh preview") }
                Button(
                    onClick = onExport,
                    enabled = folderPermissionValid && !busy && preview?.writableEntries?.isNotEmpty() == true,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.launcherAccentDark),
                ) { Text(if (busy) "Working…" else "Export changes") }
            }
        }

        SetupStep(
            number = 3,
            title = "Finish setup in Cocoon",
            body = "In Cocoon open Settings → Library & Data, refresh the platform list, choose AdventurePad " +
                "as the ScummVM default player, then rescan games. The catalogue option depends on Cocoon " +
                "accepting the prepared upstream player definition.",
        ) {
            Text(
                if (cocoonInstalled) "Cocoon is installed." else "Cocoon is not installed.",
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onOpenCocoon, enabled = cocoonInstalled) { Text("Open Cocoon") }
        }

        statusMessage?.let {
            Surface(
                color = palette.launcherContent,
                contentColor = palette.launcherInk,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(it, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    Surface(
        color = palette.launcherContent,
        contentColor = palette.launcherInk,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$number. $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            content()
        }
    }
}

@Composable
private fun ExportSummary(plan: CocoonExportPlan) {
    Text(
        "${plan.count(CocoonExportAction.ADD)} additions • " +
            "${plan.count(CocoonExportAction.UPDATE)} updates • " +
            "${plan.count(CocoonExportAction.CONFLICT)} conflicts • " +
            "${plan.count(CocoonExportAction.UNCHANGED)} unchanged",
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun ExportPreviewRow(entry: CocoonExportEntry) {
    val label = entry.action.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label — ${entry.fileName}", fontWeight = FontWeight.Bold)
        Text("Target: ${entry.targetId}", style = MaterialTheme.typography.bodySmall)
        entry.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
