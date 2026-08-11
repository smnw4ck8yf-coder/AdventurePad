package com.jamesmoran.adventurepad

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jamesmoran.adventurepad.ui.theme.AdventurePadDesign
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CompanionStatistics(
    val targetId: String,
    val displayMode: DisplayMode,
    val splitProfileConfigured: Boolean,
    val notesPresent: Boolean,
)

private enum class ImportStep { CHOOSE, PASTE, PREVIEW }
internal enum class WalkthroughView { READER, CONTENTS, SEARCH }
private enum class ReaderTargetAlignment { HEADING, SEARCH_MATCH }
private data class ReaderScrollRequest(val rawOffset: Int, val alignment: ReaderTargetAlignment, val id: Int)
internal data class WalkthroughSectionBounds(
    val section: WalkthroughSection,
    val top: Float,
    val bottom: Float,
)
internal const val WALKTHROUGH_BACK_LABEL = "←"
internal const val WALKTHROUGH_CLOSE_LABEL = "X"
internal const val WALKTHROUGH_TITLE_MAX_LINES = 1
internal val WALKTHROUGH_PASTE_IME_ACTION = ImeAction.Done
internal const val WALKTHROUGH_SETTINGS_LABEL = "Settings"
internal val COMPANION_BACK_ARROW_SIZE = 28.sp
internal val WALKTHROUGH_SEARCH_ARROW_SIZE = 24.sp
internal val WALKTHROUGH_IMPORT_PREVIEW_ORDER = listOf("SUMMARY", "ACTIONS", "PREVIEW")
internal val WALKTHROUGH_PASTE_ORDER = listOf("TITLE", "ANALYSE", "TEXT")
private val ImmersiveInk = Color(0xFF322317)
private val ImmersiveSecondaryInk = Color(0xFF66503A)
private val ImmersiveOutline = Color(0x995E452E)
private val ImmersiveControlScrim = Color(0x38FFF4D6)
private val ImmersiveContentEdgeFadeHeight = 24.dp

private fun Modifier.immersiveContentEdgeFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (size.height > 0f) {
                val fadeHeight = ImmersiveContentEdgeFadeHeight.toPx()
                    .coerceAtMost(size.height / 2f)
                val fadeFraction = fadeHeight / size.height
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        fadeFraction to Color.Black,
                        (1f - fadeFraction) to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

@Composable
internal fun CompanionScreen(
    gameId: String,
    persistedNotes: String,
    walkthrough: WalkthroughDocument?,
    selectedSection: CompanionSection,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    statistics: CompanionStatistics,
    onNotesChanged: (String) -> Unit,
    onSaveWalkthroughToNotes: (String, String?) -> Unit,
    onWalkthroughImported: (WalkthroughDocument) -> Unit,
    onWalkthroughRemoved: () -> Unit,
    onWalkthroughPositionChanged: (WalkthroughPosition) -> Unit,
    onWalkthroughPreferencesChanged: (WalkthroughReaderPreferences) -> Unit,
    onSectionSelected: (CompanionSection) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    var notesDraft by rememberSaveable(gameId) { mutableStateOf(persistedNotes) }
    var lastPersistedNotes by rememberSaveable(gameId) { mutableStateOf(persistedNotes) }
    LaunchedEffect(gameId, persistedNotes) {
        if (notesDraft == lastPersistedNotes) notesDraft = persistedNotes
        lastPersistedNotes = persistedNotes
    }

    Surface(
        color = if (immersive) Color.Transparent else AdventurePadThemeTokens.colors.background,
        contentColor = if (immersive) ImmersiveInk else AdventurePadThemeTokens.colors.textPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (immersive) ImmersiveInk else AdventurePadThemeTokens.colors.textPrimary,
        ) {
            Column(Modifier.fillMaxSize()) {
                if (selectedSection != CompanionSection.WALKTHROUGH) {
                    PageHeader(
                        title = if (selectedSection == CompanionSection.HOME) "COMPANION" else selectedSection.label.uppercase(),
                        showBack = selectedSection != CompanionSection.HOME,
                        immersive = immersive,
                        showVisuals = !immersive || selectedSection != CompanionSection.HOME,
                        onBack = onBack,
                        onClose = onClose,
                    )
                    if (!immersive || selectedSection != CompanionSection.HOME) {
                        HorizontalDivider(color = if (immersive) ImmersiveOutline else AdventurePadThemeTokens.colors.outline)
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    when (selectedSection) {
                        CompanionSection.HOME -> CompanionHome(
                            immersive = immersive,
                            onOpen = onSectionSelected,
                        )
                        CompanionSection.NOTES -> if (!isCompanionTargetAvailable(gameId)) {
                            PlaceholderSection(
                                "Notes",
                                "Unavailable\n\nGameId='$gameId'\nAvailable=${isCompanionTargetAvailable(gameId)}",
                            )
                        } else {
                            NotesSection(notesDraft, immersive) {
                                notesDraft = it
                                onNotesChanged(it)
                            }
                        }
                        CompanionSection.WALKTHROUGH -> WalkthroughPage(
                            gameId = gameId,
                            document = walkthrough,
                            onImported = onWalkthroughImported,
                            onRemoved = onWalkthroughRemoved,
                            onSaveToNotes = onSaveWalkthroughToNotes,
                            onPositionChanged = onWalkthroughPositionChanged,
                            onPreferencesChanged = onWalkthroughPreferencesChanged,
                            onBack = onBack,
                            onClose = onClose,
                            immersive = immersive,
                        )
                        CompanionSection.MANUAL -> PlaceholderSection(
                            "Manual",
                            "No manual added for this game.",
                            "Original manual pages, maps, and preservation material will appear here when supplied by the user.",
                        )
                        CompanionSection.DIALOGUE -> PlaceholderSection(
                            "Recent Dialogue",
                            "Recent dialogue will appear here when dialogue capture support is added.",
                        )
                        CompanionSection.STATISTICS -> StatisticsSection(statistics)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    immersive: Boolean = false,
    showVisuals: Boolean = true,
    onBack: () -> Unit = {},
    onClose: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(COMPANION_HEADER_HEIGHT_DP.dp)
            .background(
                if (immersive && showVisuals) ImmersiveControlScrim else Color.Transparent,
            )
            .padding(horizontal = AdventurePadDesign.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CompanionBackButton(
                onClick = onBack,
                visible = showVisuals,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        if (showVisuals) {
            Text(
                title,
                color = if (immersive) ImmersiveInk else AdventurePadThemeTokens.colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showVisuals) {
            TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (immersive) ImmersiveSecondaryInk else AdventurePadThemeTokens.colors.textSecondary,
                ),
                modifier = Modifier.width(48.dp).heightIn(min = AdventurePadDesign.utilityTouchTarget)
                    .semantics { contentDescription = "Close" },
            ) { Text(WALKTHROUGH_CLOSE_LABEL) }
        } else {
            InvisibleHitTarget(
                description = "Close",
                onClick = onClose,
                modifier = Modifier.width(48.dp).heightIn(min = AdventurePadDesign.utilityTouchTarget),
            )
        }
    }
}

@Composable
private fun InvisibleHitTarget(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
    )
}

@Composable
private fun CompanionBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
    visible: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
) {
    TextButton(
        onClick = onClick,
        colors = if (!visible) ButtonDefaults.textButtonColors(contentColor = Color.Transparent)
        else contentColor?.let { ButtonDefaults.textButtonColors(contentColor = it) }
            ?: ButtonDefaults.textButtonColors(),
        contentPadding = contentPadding,
        modifier = modifier
            .width(48.dp)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Back" },
    ) {
        if (visible) {
            Text(
                WALKTHROUGH_BACK_LABEL,
                fontSize = COMPANION_BACK_ARROW_SIZE,
                lineHeight = COMPANION_BACK_ARROW_SIZE,
                modifier = Modifier.offset(y = (-6).dp),
            )
        }
    }
}

@Composable
private fun CompanionHome(
    immersive: Boolean,
    onOpen: (CompanionSection) -> Unit,
) {
    Column {
        // Move the complete original-height action row down by 48 dp.
        Spacer(Modifier.height(COMPANION_ACTION_TOP_OFFSET_DP.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(COMPANION_ACTION_ROW_HEIGHT_DP.dp)
                .padding(horizontal = COMPANION_ACTION_HORIZONTAL_PADDING_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(COMPANION_ACTION_GAP_DP.dp),
        ) {
            CompanionActionButton(
                section = CompanionSection.NOTES,
                skinButton = SkinnableButton.NOTES,
                immersive = immersive,
                onClick = { onOpen(CompanionSection.NOTES) },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            CompanionActionButton(
                section = CompanionSection.WALKTHROUGH,
                skinButton = SkinnableButton.WALKTHROUGH,
                immersive = immersive,
                onClick = { onOpen(CompanionSection.WALKTHROUGH) },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CompanionActionButton(
    section: CompanionSection,
    skinButton: SkinnableButton,
    immersive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier.then(
            if (immersive) Modifier else Modifier
                .background(
                    AdventurePadThemeTokens.colors.surface,
                    AdventurePadThemeTokens.shapes.medium,
                )
                .border(
                    AdventurePadThemeTokens.components.subtleBorderWidth,
                    AdventurePadThemeTokens.colors.outline,
                    AdventurePadThemeTokens.shapes.medium,
                ),
        ),
    ) {
        SkinArtwork(
            skinButton.artworkCandidates(pressed),
            Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (immersive) null else LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { contentDescription = section.label },
            contentAlignment = Alignment.Center,
        ) {
            // Immersive v2 artwork supplies its own label. If these optional assets are
            // absent, the transparent target preserves legacy companion.background UI.
            if (!immersive) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NotesSection(notes: String, immersive: Boolean, onNotesChanged: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        Modifier.fillMaxSize().padding(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
    ) {
        Text(
            "Saved automatically for this game.",
            color = if (immersive) ImmersiveSecondaryInk else AdventurePadThemeTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { onNotesChanged(it.take(MAX_NOTES_LENGTH)) },
            placeholder = { Text("Write clues, plans, or anything you want to remember…") },
            minLines = 8,
            maxLines = Int.MAX_VALUE,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                completeNotesEdit(notes, onNotesChanged) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            }),
            colors = if (immersive) OutlinedTextFieldDefaults.colors(
                focusedTextColor = ImmersiveInk,
                unfocusedTextColor = ImmersiveInk,
                focusedBorderColor = ImmersiveOutline,
                unfocusedBorderColor = ImmersiveOutline,
                cursorColor = ImmersiveInk,
                focusedPlaceholderColor = ImmersiveSecondaryInk,
                unfocusedPlaceholderColor = ImmersiveSecondaryInk,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ) else OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(if (immersive) Modifier.immersiveContentEdgeFade() else Modifier),
        )
    }
}

@Composable
private fun WalkthroughPage(
    gameId: String,
    document: WalkthroughDocument?,
    onImported: (WalkthroughDocument) -> Unit,
    onRemoved: () -> Unit,
    onSaveToNotes: (String, String?) -> Unit,
    onPositionChanged: (WalkthroughPosition) -> Unit,
    onPreferencesChanged: (WalkthroughReaderPreferences) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    immersive: Boolean,
) {
    if (!isCompanionTargetAvailable(gameId)) {
        Column(Modifier.fillMaxSize()) {
            WalkthroughToolbar(onBack = onBack, onClose = onClose, immersive = immersive)
            PlaceholderSection(
                "Walkthrough",
                "Unavailable\n\nGameId='$gameId'\nAvailable=${isCompanionTargetAvailable(gameId)}",
            )        }
    } else if (document == null) {
        WalkthroughImporter(onImported, onBack, onClose, immersive)
    } else {
        var replacing by rememberSaveable(gameId, document.importedAt) { mutableStateOf(false) }
        if (replacing) WalkthroughImporter(
            onImported = { imported -> onImported(imported); replacing = false },
            onBack = { replacing = false },
            onClose = onClose,
            immersive = immersive,
        ) else WalkthroughReader(
            document = document,
            onReplace = { replacing = true },
            onRemoved = onRemoved,
            onSaveToNotes = onSaveToNotes,
            onPositionChanged = onPositionChanged,
            onPreferencesChanged = onPreferencesChanged,
            onBack = onBack,
            onClose = onClose,
            immersive = immersive,
        )
    }
}

@Composable
private fun WalkthroughImporter(
    onImported: (WalkthroughDocument) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    immersive: Boolean,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(ImportStep.CHOOSE) }
    var text by rememberSaveable { mutableStateOf("") }
    var sourceType by rememberSaveable { mutableStateOf(WalkthroughSourceType.PASTED_TEXT) }
    var preview by remember { mutableStateOf<WalkthroughDocument?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.displayName(uri)
                    val type = walkthroughSourceTypeForFileName(name)
                        ?: throw IllegalArgumentException("Unsupported file type")
                    val loaded = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IOException("The selected file could not be opened.")
                    loaded.take(MAX_WALKTHROUGH_LENGTH) to type
                }
            }.onSuccess { (loaded, type) ->
                text = loaded
                sourceType = type
                preview = withContext(Dispatchers.Default) { WalkthroughParser.parse(loaded, System.currentTimeMillis(), type) }
                step = ImportStep.PREVIEW
                error = null
            }.onFailure { error = "Choose a TXT or Markdown file." }
        }
    }
    Column(Modifier.fillMaxSize()) {
        WalkthroughToolbar(onBack = onBack, onClose = onClose, immersive = immersive)
        HorizontalDivider(color = if (immersive) ImmersiveOutline else AdventurePadThemeTokens.colors.outline)
        AdventurePadScrollablePage(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(AdventurePadDesign.contentPadding),
            verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingMd),
        ) {
        when (step) {
            ImportStep.CHOOSE -> {
                Text("Add Walkthrough", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Import read-only reference material for this game.", color = AdventurePadThemeTokens.colors.textSecondary)
                Button(onClick = { step = ImportStep.PASTE }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("PASTE TEXT") }
                OutlinedButton(
                    onClick = { launcher.launch(arrayOf("text/plain", "text/markdown")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("IMPORT FILE") }
                Text("Supported files: TXT and Markdown", style = MaterialTheme.typography.bodySmall, color = AdventurePadThemeTokens.colors.textSecondary)
            }
            ImportStep.PASTE -> {
                Text("Paste walkthrough text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    enabled = text.isNotBlank(),
                    onClick = {
                        sourceType = WalkthroughSourceType.PASTED_TEXT
                        scope.launch {
                            preview = withContext(Dispatchers.Default) {
                                WalkthroughParser.parse(text, System.currentTimeMillis(), sourceType)
                            }
                            step = ImportStep.PREVIEW
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("ANALYSE") }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_WALKTHROUGH_LENGTH) },
                    minLines = 10,
                    maxLines = Int.MAX_VALUE,
                    keyboardOptions = KeyboardOptions(imeAction = WALKTHROUGH_PASTE_IME_ACTION),
                    keyboardActions = KeyboardActions(onDone = {
                        text = completeWalkthroughPasteEdit(text) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }),
                    colors = if (immersive) OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ImmersiveInk,
                        unfocusedTextColor = ImmersiveInk,
                        focusedBorderColor = ImmersiveOutline,
                        unfocusedBorderColor = ImmersiveOutline,
                        cursorColor = ImmersiveInk,
                        focusedContainerColor = ImmersiveControlScrim,
                        unfocusedContainerColor = ImmersiveControlScrim,
                    ) else OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { step = ImportStep.CHOOSE }) { Text("CANCEL") }
            }
            ImportStep.PREVIEW -> preview?.let { analysed ->
                Text("Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val nested = analysed.sections.any { it.parentId != null }
                Text(
                    when {
                        analysed.sections.isEmpty() -> "No clear sections found"
                        nested -> "${analysed.sections.size} sections found · Nested sections detected"
                        else -> "${analysed.sections.size} sections found"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    enabled = analysed.sections.isNotEmpty(),
                    onClick = { onImported(analysed) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("IMPORT WITH STRUCTURE") }
                OutlinedButton(
                    onClick = { onImported(analysed.copy(sections = emptyList())) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("IMPORT AS PLAIN TEXT") }
                TextButton(onClick = { step = ImportStep.CHOOSE; preview = null }) { Text("START AGAIN") }
                HorizontalDivider(color = AdventurePadThemeTokens.colors.outline)
                Text("Document preview", style = MaterialTheme.typography.labelLarge, color = AdventurePadThemeTokens.colors.textSecondary)
                analysed.sections.take(12).forEach { section ->
                    Text("  ".repeat((section.level - 1).coerceAtLeast(0)) + section.title)
                }
                if (analysed.sections.size > 12) Text("…and ${analysed.sections.size - 12} more")
            }
        }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun WalkthroughReader(
    document: WalkthroughDocument,
    onReplace: () -> Unit,
    onRemoved: () -> Unit,
    onSaveToNotes: (String, String?) -> Unit,
    onPositionChanged: (WalkthroughPosition) -> Unit,
    onPreferencesChanged: (WalkthroughReaderPreferences) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    immersive: Boolean,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by rememberSaveable(document.importedAt) { mutableStateOf("") }
    var view by rememberSaveable(document.importedAt) { mutableStateOf(WalkthroughView.READER) }
    var showMore by rememberSaveable(document.importedAt) { mutableStateOf(false) }
    var settingsState by remember(document.importedAt) {
        mutableStateOf(ReaderSettingsState(preferences = document.preferences))
    }
    var collapsedIds by rememberSaveable(document.importedAt) { mutableStateOf(setOf<String>()) }
    var currentResult by rememberSaveable(document.importedAt) { mutableIntStateOf(0) }
    var scrollRequest by remember(document.importedAt) {
        mutableStateOf(ReaderScrollRequest(document.resolvePosition(document.position), ReaderTargetAlignment.HEADING, 0))
    }
    var textLayout by remember(document.importedAt) { mutableStateOf<TextLayoutResult?>(null) }
    var readerViewportHeight by remember(document.importedAt) { mutableIntStateOf(0) }
    val results = remember(document.rawText, query) { searchWalkthrough(document, query) }
    val preferences = settingsState.preferences
    val display = remember(document.rawText, document.sections) {
        walkthroughDisplayText(document.rawText, document.sections.mapTo(mutableSetOf()) { it.title })
    }

    fun updateSettings(action: ReaderSettingsAction) {
        val updated = reduceReaderSettings(settingsState, action)
        settingsState = updated
        if (updated.preferences != preferences) onPreferencesChanged(updated.preferences)
    }
    LaunchedEffect(document.preferences) {
        if (document.preferences != settingsState.preferences) {
            settingsState = settingsState.copy(preferences = document.preferences)
        }
    }

    fun jumpTo(offset: Int, alignment: ReaderTargetAlignment) {
        scrollRequest = ReaderScrollRequest(offset, alignment, scrollRequest.id + 1)
    }
    LaunchedEffect(scrollRequest, textLayout, readerViewportHeight) {
        val layout = textLayout ?: return@LaunchedEffect
        if (readerViewportHeight <= 0) return@LaunchedEffect
        val displayOffset = display.targetForRawOffset(scrollRequest.rawOffset).displayOffset
            .coerceIn(0, (display.text.length - 1).coerceAtLeast(0))
        val lineTop = layout.getLineTop(layout.getLineForOffset(displayOffset))
        val inset = when (scrollRequest.alignment) {
            ReaderTargetAlignment.HEADING -> with(density) { AdventurePadDesign.spacingMd.toPx() }
            ReaderTargetAlignment.SEARCH_MATCH -> readerViewportHeight * 0.35f
        }
        scrollState.scrollTo((lineTop - inset).toInt().coerceIn(0, scrollState.maxValue))
    }
    LaunchedEffect(view, currentResult, results) {
        if (view == WalkthroughView.SEARCH) {
            results.getOrNull(currentResult)?.let { jumpTo(it.offset, ReaderTargetAlignment.SEARCH_MATCH) }
        }
    }
    DisposableEffect(document.importedAt) {
        onDispose {
            val layout = textLayout
            val displayOffset = if (layout == null || display.text.isEmpty()) 0 else {
                val line = layout.getLineForVerticalPosition(scrollState.value.toFloat())
                layout.getLineStart(line)
            }
            val offset = display.rawOffsetForDisplayOffset(displayOffset)
            onPositionChanged(document.positionForOffset(offset))
        }
    }

    val palette = AdventurePadThemeTokens.current.readerPalette(preferences.appearance)
    val readerForeground = if (immersive) ImmersiveInk else palette.foreground
    val readerHeading = if (immersive) Color(0xFF71461F) else palette.heading
    Column(
        Modifier.fillMaxSize().background(
            if (immersive) Color.Transparent else AdventurePadThemeTokens.colors.background,
        ),
    ) {
        val currentOffset by remember(document.rawText, scrollState) {
            derivedStateOf {
                val layout = textLayout
                if (layout == null || display.text.isEmpty()) 0 else {
                    val line = layout.getLineForVerticalPosition(scrollState.value.toFloat())
                    display.rawOffsetForDisplayOffset(layout.getLineStart(line))
                }
            }
        }
        val currentSection by remember(
            document.sections,
            display,
            textLayout,
            scrollState,
            readerViewportHeight,
            density,
        ) {
            derivedStateOf {
                val layout = textLayout
                if (layout == null || readerViewportHeight <= 0 || display.text.isEmpty()) {
                    document.sections.lastOrNull { it.startOffset <= currentOffset }
                } else {
                    val contentInset = with(density) { AdventurePadDesign.spacingMd.toPx() }
                    val bounds = document.sections.map { section ->
                        val startOffset = display.targetForRawOffset(section.startOffset).displayOffset
                            .coerceIn(0, display.text.lastIndex)
                        val endOffset = (display.targetForRawOffset(section.endOffset).displayOffset - 1)
                            .coerceIn(startOffset, display.text.lastIndex)
                        WalkthroughSectionBounds(
                            section = section,
                            top = layout.getLineTop(layout.getLineForOffset(startOffset)) + contentInset,
                            bottom = layout.getLineBottom(layout.getLineForOffset(endOffset)) + contentInset,
                        )
                    }
                    dominantWalkthroughSection(
                        bounds = bounds,
                        viewportTop = scrollState.value.toFloat(),
                        viewportBottom = scrollState.value + readerViewportHeight.toFloat(),
                    ) ?: document.sections.lastOrNull { it.startOffset <= currentOffset }
                }
            }
        }
        WalkthroughToolbar(
            onBack = onBack,
            onReader = {
                view = readerViewAfterReaderAction(view)
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            onSearch = { view = WalkthroughView.SEARCH },
            onContents = document.sections.takeIf { it.isNotEmpty() }?.let {
                { view = WalkthroughView.CONTENTS }
            },
            onMore = { showMore = true },
            onClose = onClose,
            immersive = immersive,
        ) {
            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                DropdownMenuItem(
                    text = { Text(if (currentSection == null) "Save paragraph to notes" else "Save section to notes") },
                    onClick = {
                        showMore = false
                        val passage = currentSection?.let { document.rawText.substring(it.startOffset, it.endOffset) }
                            ?: document.rawText.paragraphNear(currentOffset)
                        onSaveToNotes(passage, currentSection?.title)
                    },
                )
                DropdownMenuItem(
                    text = { Text(WALKTHROUGH_SETTINGS_LABEL) },
                    onClick = {
                        showMore = false
                        updateSettings(ReaderSettingsAction.Open)
                    },
                )
                DropdownMenuItem(text = { Text("Replace walkthrough") }, onClick = { showMore = false; onReplace() })
                DropdownMenuItem(
                    text = { Text("Remove walkthrough", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMore = false; onRemoved() },
                )
            }
        }
        HorizontalDivider(color = if (immersive) ImmersiveOutline else AdventurePadThemeTokens.colors.outline)
        if (settingsState.isOpen) ReaderSettingsPanel(
            preferences = preferences,
            onAction = ::updateSettings,
        )
        if (view == WalkthroughView.SEARCH) WalkthroughSearchBar(
            query = query,
            resultCount = results.size,
            currentResult = currentResult,
            result = results.getOrNull(currentResult),
            onQueryChanged = { query = it; currentResult = 0 },
            onPrevious = {
                if (results.isNotEmpty()) {
                    currentResult = moveWalkthroughSearchResult(currentResult, results.size, -1)
                }
            },
            onNext = {
                if (results.isNotEmpty()) {
                    currentResult = moveWalkthroughSearchResult(currentResult, results.size, 1)
                }
            },
            onResultSelected = { results.getOrNull(currentResult)?.let { jumpTo(it.offset, ReaderTargetAlignment.SEARCH_MATCH) } },
            onDismiss = { view = WalkthroughView.READER },
            immersive = immersive,
        )
        if (view == WalkthroughView.CONTENTS) {
            ContentsPanel(
                sections = document.sections,
                collapsedIds = collapsedIds,
                onToggle = { id -> collapsedIds = if (id in collapsedIds) collapsedIds - id else collapsedIds + id },
                onJump = {
                    view = WalkthroughView.READER
                    jumpTo(it.startOffset, ReaderTargetAlignment.HEADING)
                },
                immersive = immersive,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (immersive) Modifier.immersiveContentEdgeFade() else Modifier),
            )
        } else {
            val activeResult = results.getOrNull(currentResult).takeIf { view == WalkthroughView.SEARCH }
            val readerComponents = AdventurePadThemeTokens.components
            val styledText = remember(
                display, document.sections, palette, readerForeground, readerHeading, activeResult, readerComponents,
            ) {
                walkthroughReaderText(
                    display,
                    document.sections,
                    readerForeground,
                    readerHeading,
                    readerComponents.onSearchHighlight,
                    readerComponents.searchHighlight,
                    activeResult,
                )
            }
            Box(
                Modifier.fillMaxSize().background(if (immersive) Color.Transparent else palette.background)
                    .then(if (immersive) Modifier.immersiveContentEdgeFade() else Modifier)
                    .onSizeChanged { readerViewportHeight = it.height },
            ) {
                SelectionContainer {
                    Text(
                        text = styledText,
                        color = readerForeground,
                        fontFamily = readerFontFamily(preferences.font),
                        fontSize = (16f * preferences.textScale).sp,
                        lineHeight = (21f * preferences.textScale * preferences.lineSpacingScale).sp,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(
                            horizontal = AdventurePadDesign.spacingLg,
                            vertical = AdventurePadDesign.spacingMd,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WalkthroughToolbar(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onReader: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onContents: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    immersive: Boolean = false,
    moreContent: @Composable (() -> Unit)? = null,
) {
    val toolbarContentColor = if (immersive) ImmersiveInk else AdventurePadThemeTokens.colors.primary
    Row(
        Modifier.fillMaxWidth()
            .background(if (immersive) ImmersiveControlScrim else Color.Transparent)
            .padding(horizontal = AdventurePadDesign.spacingSm, vertical = AdventurePadDesign.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompanionBackButton(
            onClick = onBack,
            contentColor = toolbarContentColor,
            contentPadding = PaddingValues(horizontal = AdventurePadDesign.spacingSm),
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Text(
            "WALKTHROUGH",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = WALKTHROUGH_TITLE_MAX_LINES,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        onReader?.let { CompactToolbarButton("READER", "Show reader", toolbarContentColor, it) }
        onSearch?.let { CompactToolbarButton("SEARCH", "Search walkthrough", toolbarContentColor, it) }
        onContents?.let { CompactToolbarButton("CONTENTS", "Show contents", toolbarContentColor, it) }
        if (onMore != null) Box {
            TextButton(
                onClick = onMore,
                colors = ButtonDefaults.textButtonColors(contentColor = toolbarContentColor),
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text("⋯", fontSize = 22.sp) }
            moreContent?.invoke()
        }
        TextButton(
            onClick = onClose,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (immersive) ImmersiveSecondaryInk else AdventurePadThemeTokens.colors.textSecondary,
            ),
            modifier = Modifier.width(48.dp).heightIn(min = 48.dp)
                .semantics { contentDescription = "Close walkthrough" },
            contentPadding = PaddingValues(horizontal = AdventurePadDesign.spacingSm),
        ) { Text(WALKTHROUGH_CLOSE_LABEL) }
    }
}

@Composable
private fun CompactToolbarButton(label: String, description: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
    ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
}

@Composable
private fun ReaderSettingsPanel(
    preferences: WalkthroughReaderPreferences,
    onAction: (ReaderSettingsAction) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(AdventurePadThemeTokens.colors.surfaceRaised)
            .padding(horizontal = AdventurePadDesign.spacingMd, vertical = AdventurePadDesign.spacingSm),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingXs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reader Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { onAction(ReaderSettingsAction.Close) }) { Text("DONE") }
        }
        Text("Font", style = MaterialTheme.typography.labelMedium, color = AdventurePadThemeTokens.colors.textSecondary)
        ReaderSettingChoices(
            labels = ReaderFont.entries.map { it.label },
            selectedLabel = preferences.font.label,
            onSelected = { index -> onAction(ReaderSettingsAction.SelectFont(ReaderFont.entries[index])) },
        )
        Text("Text size", style = MaterialTheme.typography.labelMedium, color = AdventurePadThemeTokens.colors.textSecondary)
        ReaderSettingChoices(
            labels = ReaderTextSize.entries.map { it.label },
            selectedLabel = ReaderTextSize.closestTo(preferences.textScale).label,
            onSelected = { index -> onAction(ReaderSettingsAction.SelectTextSize(ReaderTextSize.entries[index])) },
        )
        Text("Background", style = MaterialTheme.typography.labelMedium, color = AdventurePadThemeTokens.colors.textSecondary)
        ReaderSettingChoices(
            labels = ReadingAppearance.entries.map { it.label },
            selectedLabel = preferences.appearance.label,
            columns = 4,
            onSelected = { index -> onAction(ReaderSettingsAction.SelectBackground(ReadingAppearance.entries[index])) },
        )
    }
}

@Composable
private fun ReaderSettingChoices(
    labels: List<String>,
    selectedLabel: String,
    columns: Int = 3,
    onSelected: (Int) -> Unit,
) {
    labels.indices.chunked(columns).forEach { rowIndices ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingXs),
        ) {
            rowIndices.forEach { index ->
                ReaderSettingChoice(
                    label = labels[index],
                    selected = labels[index] == selectedLabel,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(columns - rowIndices.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ReaderSettingChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
    val buttonModifier = modifier.heightIn(min = 40.dp)
    val contentPadding = PaddingValues(horizontal = 6.dp)
    if (selected) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = contentPadding,
            content = { content() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = contentPadding,
            content = { content() },
        )
    }
}

private fun readerFontFamily(font: ReaderFont): FontFamily = when (font) {
    ReaderFont.SANS_SERIF -> FontFamily.SansSerif
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.MONOSPACE -> FontFamily.Monospace
}

@Composable
private fun WalkthroughSearchBar(
    query: String,
    resultCount: Int,
    currentResult: Int,
    result: WalkthroughSearchResult?,
    onQueryChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResultSelected: () -> Unit,
    onDismiss: () -> Unit,
    immersive: Boolean,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(if (immersive) ImmersiveControlScrim else Color.Transparent)
            .padding(horizontal = AdventurePadDesign.spacingMd, vertical = AdventurePadDesign.spacingXs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingXs),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = { Text("Search walkthrough") },
                singleLine = true,
                colors = if (immersive) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ImmersiveInk,
                    unfocusedTextColor = ImmersiveInk,
                    focusedBorderColor = ImmersiveOutline,
                    unfocusedBorderColor = ImmersiveOutline,
                    cursorColor = ImmersiveInk,
                    focusedPlaceholderColor = ImmersiveSecondaryInk,
                    unfocusedPlaceholderColor = ImmersiveSecondaryInk,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ) else OutlinedTextFieldDefaults.colors(),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (query.isBlank()) "" else if (resultCount == 0) "0" else "${currentResult + 1}/$resultCount",
                style = MaterialTheme.typography.bodySmall,
                color = if (immersive) ImmersiveSecondaryInk else AdventurePadThemeTokens.colors.textSecondary,
            )
            TextButton(
                enabled = resultCount > 0,
                onClick = onPrevious,
                modifier = Modifier.width(48.dp).heightIn(min = 40.dp),
                contentPadding = PaddingValues(AdventurePadDesign.spacingXs),
            ) { Text("↑", fontSize = WALKTHROUGH_SEARCH_ARROW_SIZE, lineHeight = WALKTHROUGH_SEARCH_ARROW_SIZE) }
            TextButton(
                enabled = resultCount > 0,
                onClick = onNext,
                modifier = Modifier.width(48.dp).heightIn(min = 40.dp),
                contentPadding = PaddingValues(AdventurePadDesign.spacingXs),
            ) { Text("↓", fontSize = WALKTHROUGH_SEARCH_ARROW_SIZE, lineHeight = WALKTHROUGH_SEARCH_ARROW_SIZE) }
            TextButton(onClick = onDismiss) { Text("✕") }
        }
        result?.let {
            Text(
                text = listOfNotNull(it.sectionTitle, it.snippet).joinToString(" · "),
                color = if (immersive) ImmersiveSecondaryInk else AdventurePadThemeTokens.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onResultSelected)
                    .padding(horizontal = AdventurePadDesign.spacingSm, vertical = AdventurePadDesign.spacingXs),
            )
        }
    }
}

private fun walkthroughReaderText(
    display: WalkthroughDisplayText,
    sections: List<WalkthroughSection>,
    foreground: Color,
    heading: Color,
    onSearchHighlight: Color,
    searchHighlight: Color,
    activeResult: WalkthroughSearchResult?,
): AnnotatedString = AnnotatedString.Builder(display.text).apply {
    var searchFrom = 0
    sections.forEach { section ->
        val start = display.text.indexOf(section.title, searchFrom, ignoreCase = true)
        if (start >= 0) {
            addStyle(
                SpanStyle(color = heading, fontWeight = FontWeight.Bold),
                start,
                start + section.title.length,
            )
            searchFrom = start + section.title.length
        }
    }
    if (sections.isEmpty()) addStyle(SpanStyle(color = foreground), 0, display.text.length)
    activeResult?.let { result ->
        val range = display.displayRange(result.offset, result.length)
        if (!range.isEmpty()) addStyle(
            SpanStyle(
                color = onSearchHighlight,
                background = searchHighlight,
                fontWeight = FontWeight.ExtraBold,
            ),
            range.first,
            range.last + 1,
        )
    }
}.toAnnotatedString()

@Composable
private fun ContentsPanel(
    sections: List<WalkthroughSection>,
    collapsedIds: Set<String>,
    onToggle: (String) -> Unit,
    onJump: (WalkthroughSection) -> Unit,
    immersive: Boolean,
    modifier: Modifier = Modifier,
) {
    val parents = sections.mapNotNull { it.parentId }.toSet()
    val visible = visibleWalkthroughSections(sections, collapsedIds)
    AdventurePadScrollablePage(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = AdventurePadDesign.spacingMd,
            vertical = AdventurePadDesign.spacingSm,
        ),
    ) {
        visible.forEach { section ->
            Row(
                Modifier.fillMaxWidth().padding(start = ((section.level - 1) * 14).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (section.id in parents) TextButton(onClick = { onToggle(section.id) }, modifier = Modifier.width(44.dp)) {
                    Text(if (section.id in collapsedIds) "▶" else "▼")
                } else Spacer(Modifier.width(44.dp))
                Text(
                    section.title,
                    color = if (immersive) {
                        if (section.level == 1) Color(0xFF71461F) else ImmersiveInk
                    } else if (section.level == 1) {
                        AdventurePadThemeTokens.colors.primary
                    } else {
                        AdventurePadThemeTokens.colors.textPrimary
                    },
                    fontWeight = if (section.level == 1) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).clickable { onJump(section) }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaceholderSection(title: String, message: String, supportingText: String? = null) {
    AdventurePadScrollablePage(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingMd),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(
            Modifier.fillMaxWidth().background(AdventurePadThemeTokens.colors.surface, AdventurePadThemeTokens.shapes.medium)
                .border(AdventurePadThemeTokens.components.subtleBorderWidth, AdventurePadThemeTokens.colors.outline, AdventurePadThemeTokens.shapes.medium).padding(AdventurePadDesign.spacingLg),
            verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            supportingText?.let { Text(it, color = AdventurePadThemeTokens.colors.textSecondary) }
        }
    }
}

@Composable
private fun StatisticsSection(statistics: CompanionStatistics) {
    AdventurePadScrollablePage(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AdventurePadDesign.contentPadding),
        verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingSm),
    ) {
        StatisticRow("Current game / target", statistics.targetId.ifBlank { "Launcher / unknown" })
        StatisticRow("Current mode", if (statistics.displayMode == DisplayMode.INTERFACE) "Split View" else "Trackpad")
        StatisticRow("Split View profile", if (statistics.splitProfileConfigured) "Configured" else "Not configured")
        StatisticRow("Notes", if (statistics.notesPresent) "Present" else "None yet")
        StatisticRow("Time played", "Not available")
    }
}

@Composable
private fun StatisticRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().background(AdventurePadThemeTokens.colors.surface, AdventurePadThemeTokens.shapes.small)
            .padding(horizontal = AdventurePadDesign.spacingLg, vertical = AdventurePadDesign.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AdventurePadThemeTokens.colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(1.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun android.content.ContentResolver.displayName(uri: Uri): String {
    val cursor: Cursor? = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    return cursor?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
}

private fun String.paragraphNear(offset: Int): String {
    if (isEmpty()) return this
    val safe = offset.coerceIn(0, length)
    val start = lastIndexOf("\n\n", (safe - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 2 }
    val end = indexOf("\n\n", safe).let { if (it < 0) length else it }
    return substring(start, end)
}

internal fun readerViewAfterReaderAction(@Suppress("UNUSED_PARAMETER") current: WalkthroughView): WalkthroughView =
    WalkthroughView.READER

internal fun completeWalkthroughPasteEdit(text: String, dismissKeyboard: () -> Unit): String {
    dismissKeyboard()
    return text
}

internal fun dominantWalkthroughSection(
    bounds: List<WalkthroughSectionBounds>,
    viewportTop: Float,
    viewportBottom: Float,
    readingFocusFraction: Float = 0.55f,
): WalkthroughSection? {
    if (viewportBottom <= viewportTop) return null
    val focus = viewportTop + (viewportBottom - viewportTop) * readingFocusFraction.coerceIn(0f, 1f)
    return bounds.asSequence()
        .mapNotNull { candidate ->
            val overlap = minOf(candidate.bottom, viewportBottom) - maxOf(candidate.top, viewportTop)
            if (overlap <= 0f) null else Triple(candidate, overlap, kotlin.math.abs((candidate.top + candidate.bottom) / 2f - focus))
        }
        .maxWithOrNull(
            compareBy<Triple<WalkthroughSectionBounds, Float, Float>> { it.second }
                .thenBy { -it.third }
                .thenBy { it.first.section.startOffset },
        )
        ?.first
        ?.section
}

internal fun walkthroughSourceTypeForFileName(name: String): WalkthroughSourceType? = when {
    name.endsWith(".txt", ignoreCase = true) -> WalkthroughSourceType.TEXT_FILE
    name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true) ->
        WalkthroughSourceType.MARKDOWN_FILE
    else -> null
}

internal fun visibleWalkthroughSections(
    sections: List<WalkthroughSection>,
    collapsedIds: Set<String>,
): List<WalkthroughSection> {
    val hiddenParents = mutableSetOf<String>()
    return sections.filter { section ->
        val isHidden = section.parentId in hiddenParents
        if (isHidden || section.id in collapsedIds) hiddenParents += section.id
        !isHidden
    }
}
