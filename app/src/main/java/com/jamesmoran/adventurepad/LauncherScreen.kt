package com.jamesmoran.adventurepad

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import com.jamesmoran.adventurepad.ui.theme.AdventurePadDesign
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeTokens
import kotlinx.coroutines.launch

@Composable
internal fun AdventurePadLauncherScreen(
    state: ScummVMLibraryState,
    metadata: LauncherLibraryMetadata,
    onRefresh: () -> Unit,
    onLaunchTarget: (ScummVMTarget) -> Unit,
    onResumeTarget: (ScummVMTarget) -> Unit,
    onLoadTarget: (ScummVMTarget) -> Unit,
    onAddGame: () -> Unit,
    onRemoveTarget: (ScummVMTarget) -> Unit,
    onOpenAdvancedScummVM: () -> Unit,
    onManualOrderChanged: (List<String>) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    val context = LocalContext.current
    val density = LocalDensity.current
    val artworkWidthPx = with(density) { AdventurePadDesign.launcherGameCardWidth.roundToPx() }
    val customArtworkRepository = remember(context.applicationContext) {
        CustomArtworkRepository.create(context)
    }
    val artworkResolver = remember(context.applicationContext, artworkWidthPx, customArtworkRepository) {
        ArtworkResolver.create(context, artworkWidthPx, customArtworkRepository)
    }
    val scope = rememberCoroutineScope()
    val artworkVersions = remember { mutableStateMapOf<String, Int>() }
    var pendingArtworkTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = pendingArtworkTargetId
        pendingArtworkTargetId = null
        if (uri != null && targetId != null) {
            scope.launch {
                val result = runCatching {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw CustomArtworkException("AdventurePad could not open that image.")
                    customArtworkRepository.import(targetId, input)
                }
                result.onSuccess {
                    artworkResolver.invalidateCustomArtwork(targetId)
                    artworkVersions[targetId] = (artworkVersions[targetId] ?: 0) + 1
                    Toast.makeText(context, "Custom artwork saved.", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "AdventurePad could not read that image.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    var sortMode by rememberSaveable { mutableStateOf(LauncherSortMode.MANUAL) }
    var editingOrder by rememberSaveable { mutableStateOf(false) }
    var draggedTargetId by remember { mutableStateOf<String?>(null) }
    var draggedOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartOrder by remember { mutableStateOf<List<String>?>(null) }
    var workingManualOrder by remember { mutableStateOf(metadata.manualOrder) }
    var openMenu by remember { mutableStateOf<LauncherMenu?>(null) }
    var pendingRemoval by remember { mutableStateOf<ScummVMTarget?>(null) }
    var sortBounds by remember { mutableStateOf(Rect.Zero) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val gridState = rememberLazyGridState()
    val headerState by remember {
        derivedStateOf {
            launcherHeaderState(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
        }
    }
    val headerCollapsed = headerState == LauncherHeaderState.COLLAPSED
    val headerHeight by animateDpAsState(
        targetValue = if (headerCollapsed) {
            AdventurePadDesign.launcherCollapsedHeaderHeight
        } else {
            AdventurePadDesign.launcherExpandedHeaderHeight
        },
        animationSpec = tween(durationMillis = 220),
        label = "launcher-header-height",
    )
    val toolbarHeight by animateDpAsState(
        targetValue = if (headerCollapsed) 0.dp else AdventurePadDesign.launcherExpandedToolbarHeight,
        animationSpec = tween(durationMillis = 220),
        label = "launcher-toolbar-height",
    )

    LaunchedEffect(metadata.manualOrder, state.targets, draggedTargetId) {
        if (draggedTargetId == null) {
            workingManualOrder = reconcileManualOrder(
                metadata.manualOrder,
                state.targets.map(ScummVMTarget::targetId),
            )
        }
    }
    val visibleTargets = sortLauncherTargets(
        state.targets,
        metadata.copy(manualOrder = workingManualOrder),
        sortMode,
    )
    val reorderEnabled = canReorderLibrary(sortMode, editingOrder)
    BackHandler(enabled = openMenu != null || pendingRemoval != null) {
        if (pendingRemoval != null) pendingRemoval = null else openMenu = null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("launcher-root")
            .background(palette.launcherAccent)
            .onSizeChanged { rootSize = it }
            .onPreviewKeyEvent {
                if (openMenu != null && it.key == Key.Escape) {
                    openMenu = null
                    true
                } else {
                    false
                }
            },
    ) {
        SkinArtwork(SkinSlots.LAUNCHER_BACKGROUND, Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            LauncherHeader(
                height = headerHeight,
                collapsed = headerCollapsed,
                sortMode = sortMode,
                editingOrder = editingOrder,
                onAddGame = onAddGame,
                onSettings = onOpenAdvancedScummVM,
                onSortBoundsChanged = { sortBounds = it },
                onOpenSortMenu = { bounds -> openMenu = LauncherMenu.Sort(bounds) },
                onEditingOrderChanged = { editingOrder = it },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AdventurePadDesign.launcherOuterMargin)
                    .clip(
                        RoundedCornerShape(
                            topStart = AdventurePadDesign.launcherLibraryPanelCornerRadius,
                            topEnd = AdventurePadDesign.launcherLibraryPanelCornerRadius,
                        ),
                    )
                    .background(palette.launcherContent),
            ) {
                LauncherLibraryToolbar(
                    height = toolbarHeight,
                    visible = !headerCollapsed,
                    sortMode = sortMode,
                    editingOrder = editingOrder,
                    onAddGame = onAddGame,
                    onSortBoundsChanged = { sortBounds = it },
                    onOpenSortMenu = { bounds -> openMenu = LauncherMenu.Sort(bounds) },
                    onEditingOrderChanged = { editingOrder = it },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        state.loading -> LauncherMessage("Loading your collection…")
                        state.error != null -> LauncherMessage(
                            message = state.error,
                            action = "Try again",
                            onAction = onRefresh,
                        )
                        state.targets.isEmpty() -> LauncherMessage(
                            message = "Your ScummVM library is empty. Add a game to get started.",
                            action = "Add Game",
                            onAction = onAddGame,
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(AdventurePadDesign.launcherGameCardWidth),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = AdventurePadDesign.launcherHeaderPadding,
                                    top = 2.dp,
                                    end = AdventurePadDesign.launcherHeaderPadding,
                                    bottom = 0.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.launcherGridSpacing),
                            verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.launcherRowSpacing),
                        ) {
                        items(visibleTargets, key = { it.targetId }) { target ->
                            val isDragged = draggedTargetId == target.targetId
                            GameBox(
                                target = target,
                                artworkResolver = artworkResolver,
                                customArtworkVersion = artworkVersions[target.targetId] ?: 0,
                                editingOrder = reorderEnabled,
                                onBoundsChanged = { cardBounds[target.targetId] = it },
                                onDisposed = { cardBounds.remove(target.targetId) },
                                onOpenContextMenu = { bounds ->
                                    openMenu = LauncherMenu.Context(
                                        target,
                                        bounds,
                                        artworkResolver.hasCustomArtwork(target.targetId),
                                    )
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragged) {
                                            translationX = draggedOffset.x
                                            translationY = draggedOffset.y
                                        }
                                    },
                                onClick = { onLaunchTarget(target) },
                                onDragStart = {
                                    dragStartOrder = workingManualOrder
                                    draggedTargetId = target.targetId
                                    draggedOffset = Offset.Zero
                                },
                                onDrag = { amount ->
                                    draggedOffset += amount
                                    val slots = gridState.layoutInfo.visibleItemsInfo.associate {
                                        it.index to Offset(
                                            it.offset.x + it.size.width / 2f,
                                            it.offset.y + it.size.height / 2f,
                                        )
                                    }
                                    val currentIndex = workingManualOrder.indexOf(target.targetId)
                                    val currentCenter = slots[currentIndex]
                                    val draggedCenter = currentCenter?.plus(draggedOffset)
                                    val closestIndex = draggedCenter?.let { point ->
                                        slots.minByOrNull { (_, center) ->
                                            (point - center).getDistanceSquared()
                                        }?.key
                                    }
                                    val nextIndex = progressiveReorderIndex(
                                        currentIndex = currentIndex,
                                        targetIndex = closestIndex ?: currentIndex,
                                        itemCount = workingManualOrder.size,
                                    )
                                    if (nextIndex != currentIndex) {
                                        val nextCenter = slots[nextIndex]
                                        workingManualOrder = reorderManualOrderOneStep(
                                            workingManualOrder,
                                            target.targetId,
                                            nextIndex,
                                        )
                                        if (currentCenter != null && nextCenter != null) {
                                            draggedOffset -= nextCenter - currentCenter
                                        }
                                    }
                                },
                                onDragFinished = { cancelled ->
                                    if (draggedTargetId != null) {
                                        if (cancelled) {
                                            dragStartOrder?.let { workingManualOrder = it }
                                        } else {
                                            onManualOrderChanged(workingManualOrder)
                                        }
                                    }
                                    dragStartOrder = null
                                    draggedTargetId = null
                                    draggedOffset = Offset.Zero
                                },
                            )
                        }
                        item(
                            key = "launcher-library-footer",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            LauncherLibraryFooter(
                                status = launcherLibraryStatus(state.targets, metadata),
                            )
                        }
                        }
                    }
                }
            }
        }
        openMenu?.let { menu ->
            LauncherMenuLayer(
                menu = menu,
                rootSize = rootSize,
                cardBounds = cardBounds,
                targets = visibleTargets,
                sortBounds = sortBounds,
                editingOrder = editingOrder,
                onDismiss = { openMenu = null },
                onReplaceContext = { target, bounds ->
                    openMenu = LauncherMenu.Context(
                        target,
                        bounds,
                        artworkResolver.hasCustomArtwork(target.targetId),
                    )
                },
                onReplaceSort = { bounds -> openMenu = LauncherMenu.Sort(bounds) },
                onSortModeChanged = { selected ->
                    sortMode = selected
                    if (selected != LauncherSortMode.MANUAL) editingOrder = false
                    openMenu = null
                },
                onLaunchTarget = { target ->
                    openMenu = null
                    onLaunchTarget(target)
                },
                onResumeTarget = { target ->
                    openMenu = null
                    onResumeTarget(target)
                },
                onLoadTarget = { target ->
                    openMenu = null
                    onLoadTarget(target)
                },
                onSetCustomArtwork = { target ->
                    openMenu = null
                    pendingArtworkTargetId = target.targetId
                    artworkPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                },
                onRemoveCustomArtwork = { target ->
                    openMenu = null
                    scope.launch {
                        if (customArtworkRepository.remove(target.targetId)) {
                            artworkResolver.invalidateCustomArtwork(target.targetId)
                            artworkVersions[target.targetId] =
                                (artworkVersions[target.targetId] ?: 0) + 1
                            Toast.makeText(context, "Custom artwork removed.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "AdventurePad could not remove the custom artwork.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                onRemoveTarget = { target ->
                    openMenu = null
                    pendingRemoval = target
                },
            )
        }
        pendingRemoval?.let { target ->
            RemoveGameConfirmationOverlay(
                target = target,
                onCancel = { pendingRemoval = null },
                onRemove = {
                    pendingRemoval = null
                    onRemoveTarget(target)
                },
            )
        }
    }
}

@Composable
private fun RemoveGameConfirmationOverlay(
    target: ScummVMTarget,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    val dialogShape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .testTag("remove-game-modal-layer"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(onClick = onCancel),
        )
        Surface(
            modifier = Modifier
                .width(620.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                )
                .testTag("remove-game-dialog"),
            shape = dialogShape,
            color = palette.launcherContent,
            contentColor = palette.launcherInk,
            border = BorderStroke(2.dp, palette.launcherAccent),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(AdventurePadDesign.spacingLg),
                verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.spacingLg),
            ) {
                Text(
                    text = "Remove ${target.title}?",
                    color = palette.launcherInk,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("remove-game-title"),
                )
                Text(
                    text = "This removes the game from AdventurePad and ScummVM. " +
                        "Your game files and saved games will not be deleted.",
                    color = palette.launcherInk,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("remove-game-message"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        AdventurePadDesign.spacingMd,
                        Alignment.End,
                    ),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = palette.launcherContent,
                            contentColor = palette.launcherInk,
                        ),
                        border = BorderStroke(2.dp, palette.launcherAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("remove-game-cancel"),
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.launcherAccent,
                            contentColor = palette.launcherInk,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("remove-game-confirm"),
                    ) {
                        Text("Remove", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private sealed interface LauncherMenu {
    val anchorBounds: Rect

    data class Sort(override val anchorBounds: Rect) : LauncherMenu
    data class Context(
        val target: ScummVMTarget,
        override val anchorBounds: Rect,
        val hasCustomArtwork: Boolean,
    ) : LauncherMenu
}

@Composable
private fun LauncherMenuLayer(
    menu: LauncherMenu,
    rootSize: androidx.compose.ui.unit.IntSize,
    cardBounds: Map<String, Rect>,
    targets: List<ScummVMTarget>,
    sortBounds: Rect,
    editingOrder: Boolean,
    onDismiss: () -> Unit,
    onReplaceContext: (ScummVMTarget, Rect) -> Unit,
    onReplaceSort: (Rect) -> Unit,
    onSortModeChanged: (LauncherSortMode) -> Unit,
    onLaunchTarget: (ScummVMTarget) -> Unit,
    onResumeTarget: (ScummVMTarget) -> Unit,
    onLoadTarget: (ScummVMTarget) -> Unit,
    onSetCustomArtwork: (ScummVMTarget) -> Unit,
    onRemoveCustomArtwork: (ScummVMTarget) -> Unit,
    onRemoveTarget: (ScummVMTarget) -> Unit,
) {
    val density = LocalDensity.current
    val focusRequester = remember(menu) { FocusRequester() }
    val menuWidth = 260.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }
    val estimatedHeightPx = with(density) {
        (if (menu is LauncherMenu.Sort) 144.dp else 352.dp).toPx()
    }
    val anchor = menu.anchorBounds
    val x = anchor.left.coerceIn(0f, (rootSize.width - menuWidthPx).coerceAtLeast(0f))
    val desiredY = if (anchor.bottom + estimatedHeightPx <= rootSize.height) {
        anchor.bottom
    } else {
        anchor.top - estimatedHeightPx
    }
    val y = desiredY.coerceIn(0f, (rootSize.height - estimatedHeightPx).coerceAtLeast(0f))

    LaunchedEffect(menu) { focusRequester.requestFocus() }
    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(menu, cardBounds.toMap()) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                val point = event.changes.firstOrNull()?.position
                                when (val target = launcherPopupTargetForPress(
                                    secondaryPressed = event.buttons.isSecondaryPressed,
                                    point = point,
                                    sortBounds = sortBounds,
                                    cardBounds = cardBounds,
                                )) {
                                    LauncherPopupTarget.Sort -> onReplaceSort(sortBounds)
                                    is LauncherPopupTarget.Context -> {
                                        val replacement = targets.firstOrNull {
                                            it.targetId == target.targetId
                                        }
                                        if (replacement == null) {
                                            onDismiss()
                                        } else {
                                            onReplaceContext(
                                                replacement,
                                                checkNotNull(cardBounds[replacement.targetId]),
                                            )
                                        }
                                    }
                                    null -> onDismiss()
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )
        Surface(
            modifier = Modifier
                .testTag("launcher-menu")
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(menuWidth),
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                when (menu) {
                    is LauncherMenu.Sort -> LauncherSortMode.entries.forEachIndexed { index, mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = { onSortModeChanged(mode) },
                            modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                        )
                    }
                    is LauncherMenu.Context -> {
                        DropdownMenuItem(
                            text = { Text("Play") },
                            enabled = !editingOrder,
                            onClick = { onLaunchTarget(menu.target) },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Resume Game")
                                    if (!menu.target.resumeGameAvailable) {
                                        Text(
                                            menu.target.resumeUnavailableReason
                                                ?: "No resumable save is available",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                            enabled = !editingOrder && menu.target.resumeGameAvailable,
                            onClick = { onResumeTarget(menu.target) },
                        )
                        DropdownMenuItem(
                            text = { Text("Load Game") },
                            enabled = !editingOrder && menu.target.loadGameAvailable,
                            onClick = { onLoadTarget(menu.target) },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (menu.hasCustomArtwork) "Replace Custom Artwork"
                                    else "Set Custom Artwork",
                                )
                            },
                            enabled = !editingOrder,
                            onClick = { onSetCustomArtwork(menu.target) },
                        )
                        if (menu.hasCustomArtwork) {
                            DropdownMenuItem(
                                text = { Text("Remove Custom Artwork") },
                                enabled = !editingOrder,
                                onClick = { onRemoveCustomArtwork(menu.target) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove Game") },
                            enabled = !editingOrder,
                            onClick = { onRemoveTarget(menu.target) },
                        )
                    }
                }
            }
        }
    }
}

internal sealed interface LauncherPopupTarget {
    data object Sort : LauncherPopupTarget
    data class Context(val targetId: String) : LauncherPopupTarget
}

internal fun launcherPopupTargetForPress(
    secondaryPressed: Boolean,
    point: Offset?,
    sortBounds: Rect,
    cardBounds: Map<String, Rect>,
): LauncherPopupTarget? {
    if (point == null) return null
    if (!secondaryPressed) return LauncherPopupTarget.Sort.takeIf { sortBounds.contains(point) }
    return cardBounds.entries.lastOrNull { (_, bounds) -> bounds.contains(point) }
        ?.let { LauncherPopupTarget.Context(it.key) }
}

internal enum class LauncherHeaderState { EXPANDED, COLLAPSED }

internal fun launcherHeaderState(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): LauncherHeaderState = if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
    LauncherHeaderState.EXPANDED
} else {
    LauncherHeaderState.COLLAPSED
}

internal fun shouldLaunchGameFromCard(editingOrder: Boolean): Boolean = !editingOrder

@Composable
private fun LauncherLibraryControls(
    sortMode: LauncherSortMode,
    editingOrder: Boolean,
    onAddGame: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onOpenMenu: (Rect) -> Unit,
    onEditingOrderChanged: (Boolean) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("launcher-library-controls"),
    ) {
        OutlinedButton(
            onClick = { onOpenMenu(anchorBounds) },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = palette.launcherContent,
                contentColor = palette.launcherInk,
            ),
            border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .testTag("launcher-sort")
                .onGloballyPositioned {
                    anchorBounds = it.boundsInRoot()
                    onBoundsChanged(anchorBounds)
                },
        ) {
            Text("Sort: ${sortMode.label}")
        }
        if (sortMode == LauncherSortMode.MANUAL) {
            OutlinedButton(
                onClick = { onEditingOrderChanged(!editingOrder) },
                modifier = Modifier.testTag("launcher-edit-order"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.launcherContent,
                    contentColor = palette.launcherInk,
                ),
                border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (editingOrder) "Done" else "Edit Order", fontWeight = FontWeight.Bold)
            }
        }
        LauncherAddGameButton(onClick = onAddGame)
    }
}

@Composable
private fun LauncherLibraryToolbar(
    height: androidx.compose.ui.unit.Dp,
    visible: Boolean,
    sortMode: LauncherSortMode,
    editingOrder: Boolean,
    onAddGame: () -> Unit,
    onSortBoundsChanged: (Rect) -> Unit,
    onOpenSortMenu: (Rect) -> Unit,
    onEditingOrderChanged: (Boolean) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AdventurePadDesign.launcherHeaderPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "My Games",
                    color = palette.launcherInk,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                LauncherLibraryControls(
                    sortMode = sortMode,
                    editingOrder = editingOrder,
                    onAddGame = onAddGame,
                    onBoundsChanged = onSortBoundsChanged,
                    onOpenMenu = onOpenSortMenu,
                    onEditingOrderChanged = onEditingOrderChanged,
                )
            }
        }
    }
}

@Composable
private fun LauncherHeader(
    height: androidx.compose.ui.unit.Dp,
    collapsed: Boolean,
    sortMode: LauncherSortMode,
    editingOrder: Boolean,
    onAddGame: () -> Unit,
    onSettings: () -> Unit,
    onSortBoundsChanged: (Rect) -> Unit,
    onOpenSortMenu: (Rect) -> Unit,
    onEditingOrderChanged: (Boolean) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AdventurePadDesign.launcherHeaderPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherBrand(compact = collapsed, modifier = Modifier.weight(1f))
            Crossfade(
                targetState = collapsed,
                animationSpec = tween(durationMillis = 160),
                label = "launcher-header-action",
            ) { compact ->
                if (compact) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LauncherLibraryControls(
                            sortMode = sortMode,
                            editingOrder = editingOrder,
                            onAddGame = onAddGame,
                            onBoundsChanged = onSortBoundsChanged,
                            onOpenMenu = onOpenSortMenu,
                            onEditingOrderChanged = onEditingOrderChanged,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onSettings,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.launcherContent,
                                contentColor = palette.launcherInk,
                            ),
                            border = BorderStroke(2.dp, palette.launcherAccentDark),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("ScummVM Advanced Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherAddGameButton(onClick: () -> Unit) {
    val palette = AdventurePadThemeTokens.components
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Add Game" }
            .testTag("launcher-add-game"),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.testTag("launcher-add-game-visible"),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = palette.launcherContent,
                contentColor = palette.launcherInk,
            ),
            border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        ) {
            Text(
                text = "+",
                fontSize = 20.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LauncherLibraryFooter(status: String) {
    val palette = AdventurePadThemeTokens.components
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                AdventurePadDesign.launcherLibraryPanelTerminationHeight +
                    AdventurePadDesign.launcherFooterHeight,
            )
            .expandEndOfLibraryFooter(
                horizontalExpansion = AdventurePadDesign.launcherFooterHorizontalExpansion,
            )
            .background(palette.launcherAccent)
            .testTag("launcher-library-footer"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AdventurePadDesign.launcherLibraryPanelTerminationHeight)
                .background(
                    color = palette.launcherContent,
                    shape = RoundedCornerShape(
                        bottomStart = AdventurePadDesign.launcherLibraryPanelCornerRadius,
                        bottomEnd = AdventurePadDesign.launcherLibraryPanelCornerRadius,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AdventurePadDesign.launcherFooterHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = status,
                color = palette.launcherContent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = AdventurePadDesign.launcherFooterTextVerticalOffset)
                    .padding(horizontal = AdventurePadDesign.spacingLg)
                    .testTag("launcher-library-status"),
            )
        }
    }
}

private fun Modifier.expandEndOfLibraryFooter(
    horizontalExpansion: androidx.compose.ui.unit.Dp,
): Modifier = layout { measurable, constraints ->
    val expansionPx = horizontalExpansion.roundToPx()
    val expandedWidth = constraints.maxWidth + expansionPx * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = expandedWidth,
            maxWidth = expandedWidth,
        ),
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(x = -expansionPx, y = 0)
    }
}

internal fun launcherLibraryStatus(
    targets: List<ScummVMTarget>,
    metadata: LauncherLibraryMetadata,
): String {
    val count = targets.size
    val countText = "$count ${if (count == 1) "Game" else "Games"}"
    val lastPlayedTarget = targets
        .filter { metadata.lastPlayed.containsKey(it.targetId) }
        .maxByOrNull { metadata.lastPlayed.getValue(it.targetId) }
    return lastPlayedTarget?.title?.takeIf(String::isNotBlank)
        ?.let { "$countText • Last played: $it" }
        ?: countText
}

/** The single replacement seam for the final SVG brand asset. */
@Composable
private fun LauncherBrand(compact: Boolean, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = compact,
        modifier = modifier,
        animationSpec = tween(durationMillis = 180),
        label = "launcher-brand",
    ) { isCompact ->
        Column {
            Text(
                text = "AdventurePad",
                color = Color.White,
                fontSize = if (isCompact) 24.sp else 36.sp,
                lineHeight = if (isCompact) 28.sp else 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            if (!isCompact) {
                Text(
                    text = "Your classic adventures. Anywhere.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun GameBox(
    target: ScummVMTarget,
    artworkResolver: ArtworkResolver,
    customArtworkVersion: Int,
    editingOrder: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onDisposed: () -> Unit,
    onOpenContextMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragFinished: (Boolean) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    var focused by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val artwork by produceState<ImageBitmap?>(
        null,
        target.targetId,
        target.gameId,
        artworkResolver,
        customArtworkVersion,
    ) {
        value = artworkResolver.resolve(target.targetId, target.gameId)
    }
    val cardShape = RoundedCornerShape(8.dp)
    DisposableEffect(target.targetId) {
        onDispose(onDisposed)
    }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .testTag("game-card-${target.targetId}")
                .onGloballyPositioned {
                    bounds = it.boundsInRoot()
                    onBoundsChanged(bounds)
                }
                .onFocusChanged { focused = it.isFocused }
                .pointerInput(target.targetId) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Enter -> hovered = true
                                PointerEventType.Exit -> hovered = false
                                PointerEventType.Press -> if (event.buttons.isSecondaryPressed) {
                                    onOpenContextMenu(bounds)
                                    event.changes.forEach { it.consume() }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                .clip(cardShape)
                .background(
                    if (hovered || editingOrder) palette.launcherCard.copy(alpha = 0.65f)
                    else Color.Transparent,
                )
                .clickable { if (shouldLaunchGameFromCard(editingOrder)) onClick() }
                .then(
                    if (editingOrder) {
                        Modifier.pointerInput(target.targetId) {
                            detectDragGestures(
                                onDragStart = { currentOnDragStart() },
                                onDragEnd = { currentOnDragFinished(false) },
                                onDragCancel = { currentOnDragFinished(true) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    currentOnDrag(amount)
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(AdventurePadDesign.launcherGameCardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(AdventurePadDesign.launcherGameBoxAspectRatio)
                    .background(palette.launcherContent, RoundedCornerShape(4.dp))
                    .then(
                        when {
                            focused -> Modifier.border(2.dp, palette.launcherAccentDark, RoundedCornerShape(4.dp))
                            hovered -> Modifier.border(1.dp, palette.launcherAccentDark.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                            else -> Modifier
                        },
                    )
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork == null) {
                    PlaceholderBoxArt(target)
                } else {
                    Image(
                        bitmap = artwork!!,
                        contentDescription = "${target.title} box artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Text(
                text = target.title,
                color = palette.launcherInk,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AdventurePadDesign.launcherGameTitleHeight)
                    .padding(
                        top = AdventurePadDesign.launcherArtworkTitleSpacing,
                        start = 3.dp,
                        end = 3.dp,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlaceholderBoxArt(target: ScummVMTarget) {
    val palette = AdventurePadThemeTokens.components
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF33251D), Color(0xFF181311)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        drawRect(Color(0xFF0F0C0A).copy(alpha = 0.58f), topLeft = Offset(size.width * 0.91f, 0f))
        drawRect(palette.launcherAccent, topLeft = Offset(0f, size.height * 0.08f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.18f))
        drawRect(palette.launcherAccentDark, topLeft = Offset(0f, size.height * 0.26f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.025f))
        drawLine(
            color = Color(0xFFE9C58C).copy(alpha = 0.28f),
            start = Offset(size.width * 0.08f, size.height * 0.73f),
            end = Offset(size.width * 0.82f, size.height * 0.59f),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = palette.launcherAccent.copy(alpha = 0.55f),
            start = Offset(size.width * 0.08f, size.height * 0.78f),
            end = Offset(size.width * 0.82f, size.height * 0.64f),
            strokeWidth = 3.dp.toPx(),
        )
        drawRect(Color.White.copy(alpha = 0.20f), style = Stroke(width = 1.dp.toPx()))
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ADVENTUREPAD",
            color = Color(0xFF24170E),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 11.dp),
        )
        Spacer(Modifier.weight(0.55f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .background(Color(0xFFF5E5C3).copy(alpha = 0.96f), RoundedCornerShape(2.dp))
                .border(1.dp, Color(0xFF8A6544), RoundedCornerShape(2.dp))
                .padding(horizontal = 7.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = target.title,
                color = Color(0xFF2A1C13),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "CLASSIC PC ADVENTURE",
            color = Color(0xFFEED7B1).copy(alpha = 0.80f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun LauncherMessage(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val palette = AdventurePadThemeTokens.components
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = palette.launcherInk, textAlign = TextAlign.Center)
        if (action != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = AdventurePadDesign.spacingLg)) {
                Text(action)
            }
        }
    }
}
