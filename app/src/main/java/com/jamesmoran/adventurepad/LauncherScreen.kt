package com.jamesmoran.adventurepad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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

@Composable
internal fun AdventurePadLauncherScreen(
    state: ScummVMLibraryState,
    metadata: LauncherLibraryMetadata,
    onRefresh: () -> Unit,
    onLaunchTarget: (ScummVMTarget) -> Unit,
    onOpenAdvancedScummVM: () -> Unit,
    onManualOrderChanged: (List<String>) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    val context = LocalContext.current
    val density = LocalDensity.current
    val artworkWidthPx = with(density) { AdventurePadDesign.launcherGameCardWidth.roundToPx() }
    val artworkResolver = remember(context.applicationContext, artworkWidthPx) {
        ArtworkResolver.create(context, artworkWidthPx)
    }
    var sortMode by rememberSaveable { mutableStateOf(LauncherSortMode.MANUAL) }
    var editingOrder by rememberSaveable { mutableStateOf(false) }
    var draggedTargetId by remember { mutableStateOf<String?>(null) }
    var draggedOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartOrder by remember { mutableStateOf<List<String>?>(null) }
    var workingManualOrder by remember { mutableStateOf(metadata.manualOrder) }
    var openMenu by remember { mutableStateOf<LauncherMenu?>(null) }
    var sortBounds by remember { mutableStateOf(Rect.Zero) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val gridState = rememberLazyGridState()

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
    BackHandler(enabled = openMenu != null) { openMenu = null }
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
            LauncherHeader(onOpenAdvancedScummVM)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AdventurePadDesign.launcherOuterMargin)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(palette.launcherContent),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "My Games",
                        color = palette.launcherInk,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    LauncherSortControl(
                        sortMode = sortMode,
                        editingOrder = editingOrder,
                        onBoundsChanged = { sortBounds = it },
                        onOpenMenu = { bounds -> openMenu = LauncherMenu.Sort(bounds) },
                        onEditingOrderChanged = { editingOrder = it },
                    )
                    Text(
                        text = when {
                            state.loading -> "Reading ScummVM library…"
                            state.targets.isEmpty() -> "No configured games"
                            else -> "${state.targets.size} configured ${if (state.targets.size == 1) "game" else "games"}"
                        },
                        color = palette.launcherInk.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                when {
                state.loading -> LauncherMessage("Loading your collection…")
                state.error != null -> LauncherMessage(
                    message = state.error,
                    action = "Try again",
                    onAction = onRefresh,
                )
                state.targets.isEmpty() -> LauncherMessage(
                    message = "Your ScummVM library is empty. Add games in ScummVM Advanced Settings, then refresh.",
                    action = "ScummVM Advanced Settings",
                    onAction = onOpenAdvancedScummVM,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(AdventurePadDesign.launcherGameCardWidth),
                    state = gridState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(AdventurePadDesign.launcherGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(AdventurePadDesign.launcherRowSpacing),
                ) {
                    items(visibleTargets, key = { it.targetId }) { target ->
                        val isDragged = draggedTargetId == target.targetId
                        GameBox(
                            target = target,
                            artworkResolver = artworkResolver,
                            editingOrder = reorderEnabled,
                            onBoundsChanged = { cardBounds[target.targetId] = it },
                            onDisposed = { cardBounds.remove(target.targetId) },
                            onOpenContextMenu = { bounds ->
                                openMenu = LauncherMenu.Context(target, bounds)
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
                    openMenu = LauncherMenu.Context(target, bounds)
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
            )
        }
    }
}

private sealed interface LauncherMenu {
    val anchorBounds: Rect

    data class Sort(override val anchorBounds: Rect) : LauncherMenu
    data class Context(
        val target: ScummVMTarget,
        override val anchorBounds: Rect,
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
) {
    val density = LocalDensity.current
    val focusRequester = remember(menu) { FocusRequester() }
    val menuWidth = 260.dp
    val menuWidthPx = with(density) { menuWidth.toPx() }
    val estimatedHeightPx = with(density) {
        (if (menu is LauncherMenu.Sort) 144.dp else 48.dp).toPx()
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
                    is LauncherMenu.Context -> DropdownMenuItem(
                        text = { Text("Play ${menu.target.title}") },
                        enabled = !editingOrder,
                        onClick = { onLaunchTarget(menu.target) },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
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

@Composable
private fun LauncherSortControl(
    sortMode: LauncherSortMode,
    editingOrder: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    onOpenMenu: (Rect) -> Unit,
    onEditingOrderChanged: (Boolean) -> Unit,
) {
    val palette = AdventurePadThemeTokens.components
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier = Modifier.padding(end = 12.dp)) {
        OutlinedButton(
            onClick = { onOpenMenu(anchorBounds) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.launcherInk),
            border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.onGloballyPositioned {
                anchorBounds = it.boundsInRoot()
                onBoundsChanged(anchorBounds)
            },
        ) {
            Text("Sort: ${sortMode.label}")
        }
    }
    if (sortMode == LauncherSortMode.MANUAL) {
        OutlinedButton(
            onClick = { onEditingOrderChanged(!editingOrder) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.launcherInk),
            border = BorderStroke(1.dp, palette.launcherInk.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Text(if (editingOrder) "Done" else "Edit Order", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LauncherHeader(onSettings: () -> Unit) {
    val palette = AdventurePadThemeTokens.components
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = AdventurePadDesign.launcherHeaderPadding,
                top = 8.dp,
                end = AdventurePadDesign.launcherHeaderPadding,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherBrand(modifier = Modifier.weight(1f))
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

/** The single replacement seam for the final SVG brand asset. */
@Composable
private fun LauncherBrand(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "AdventurePad",
            color = Color.White,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
        )
        Text(
            text = "Your classic adventures. Anywhere.",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GameBox(
    target: ScummVMTarget,
    artworkResolver: ArtworkResolver,
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
        initialValue = null,
        key1 = target.targetId,
        key2 = target.gameId,
        key3 = artworkResolver,
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
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused || hovered) palette.launcherAccentDark else palette.launcherInk.copy(alpha = 0.20f),
                shape = cardShape,
            )
            .clip(cardShape)
            .background(if (focused || hovered || editingOrder) palette.launcherCard else palette.launcherContent)
            .clickable { if (!editingOrder) onClick() }
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
                },
            )
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AdventurePadDesign.launcherGameBoxAspectRatio)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF241A15)),
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
                .height(40.dp)
                .padding(top = 6.dp, start = 3.dp, end = 3.dp),
            style = MaterialTheme.typography.bodyMedium,
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
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(action)
            }
        }
    }
}
