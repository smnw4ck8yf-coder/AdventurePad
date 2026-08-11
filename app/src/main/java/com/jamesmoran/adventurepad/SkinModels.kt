package com.jamesmoran.adventurepad

import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeDefinition
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import java.io.File

internal const val SKIN_FORMAT_VERSION = 1
internal const val BUILTIN_DEFAULT_SKIN_ID = "builtin.default"
internal const val BUILTIN_OCEAN_SKIN_ID = "builtin.ocean"
internal const val BUILTIN_ADVENTURE_SKIN_ID = "builtin.adventure"

internal enum class SkinContext {
    LAUNCHER,
    GAMEPLAY,
    ADVANCED_SCUMMVM,
}

internal enum class SkinScaleMode {
    COVER,
    CONTAIN,
    FILL,
    NINE_SLICE,
    NONE,
}

internal data class SkinCanvas(val width: Int, val height: Int)
internal data class SkinInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class SkinHotspot(val x: Float, val y: Float)

internal data class SkinAsset(
    val slot: String,
    val path: String,
    val scale: SkinScaleMode,
    val sha256: String,
    val canvas: SkinCanvas? = null,
    val sliceInsets: SkinInsets? = null,
    val hotspot: SkinHotspot? = null,
)

internal data class SkinManifest(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val author: String,
    val packageVersion: String,
    val minimumAdventurePadVersionCode: Int,
    val features: Set<String>,
    val assets: Map<String, SkinAsset>,
    val colors: Map<String, String>,
    val metrics: Map<String, Float>,
    val extensions: Map<String, Any?>,
)

internal data class InstalledSkin(
    val manifest: SkinManifest,
    val root: File?,
    val builtInTheme: AdventurePadThemeDefinition? = null,
) {
    val isBuiltIn: Boolean get() = root == null

    fun assetFile(slot: String): File? {
        val base = root ?: return null
        val asset = manifest.assets[slot] ?: return null
        val file = File(base, asset.path)
        return file.takeIf { it.isFile && it.canonicalPath.startsWith(base.canonicalPath + File.separator) }
    }
}

internal data class ResolvedSkin(
    val skin: InstalledSkin,
    val theme: AdventurePadThemeDefinition,
) {
    val id: String get() = skin.manifest.id
    val displayName: String get() = skin.manifest.name
    fun assetFile(slot: String): File? = skin.assetFile(slot)

    /** Returns the first installed asset, allowing state and legacy fallbacks without inventing UI. */
    fun resolveAssetSlot(vararg candidates: String): String? = candidates.firstOrNull { slot ->
        skin.manifest.assets.containsKey(slot) && assetFile(slot) != null
    }
}

/** Game skins supply artwork; the selected colour theme remains authoritative for native UI. */
internal fun nativeThemeForGameSkin(
    selectedColourTheme: AdventurePadThemeDefinition,
    @Suppress("UNUSED_PARAMETER") selectedGameSkin: ResolvedSkin,
): AdventurePadThemeDefinition = selectedColourTheme

internal object SkinSlots {
    const val PREVIEW = "preview"
    const val LAUNCHER_BACKGROUND = "launcher.background"
    const val LAUNCHER_HEADER = "launcher.header"
    const val LAUNCHER_BRAND = "launcher.brand"
    const val TOP_SURROUND = "top.surround"
    const val BOTTOM_TRACKPAD_BACKGROUND = "bottom.trackpad.background"
    const val BOTTOM_SPLIT_BACKGROUND = "bottom.split.background"
    const val COMPANION_BACKGROUND = "companion.background"
    const val NOTES_BACKGROUND = "notes.background"
    const val WALKTHROUGH_BACKGROUND = "walkthrough.background"
    const val TRACKPAD_SURFACE = "trackpad.surface"
    const val BUTTON_LMB_NORMAL = "button.lmb.normal"
    const val BUTTON_LMB_PRESSED = "button.lmb.pressed"
    const val BUTTON_RMB_NORMAL = "button.rmb.normal"
    const val BUTTON_RMB_PRESSED = "button.rmb.pressed"
    const val BUTTON_COMPANION_NORMAL = "button.companion.normal"
    const val BUTTON_COMPANION_PRESSED = "button.companion.pressed"
    const val BUTTON_SETTINGS_NORMAL = "button.settings.normal"
    const val BUTTON_SETTINGS_PRESSED = "button.settings.pressed"
    const val BUTTON_NOTES_NORMAL = "button.notes.normal"
    const val BUTTON_NOTES_PRESSED = "button.notes.pressed"
    const val BUTTON_WALKTHROUGH_NORMAL = "button.walkthrough.normal"
    const val BUTTON_WALKTHROUGH_PRESSED = "button.walkthrough.pressed"
    const val PANEL_FRAME = "panel.frame"

    // Runtime-only compatibility for creator kits produced before Milestone 7.0.1.
    const val LEGACY_BOTTOM_BACKGROUND = "bottom.background"
    const val LEGACY_TRACKPAD_BUTTON_LEFT = "trackpad.button.left"
    const val LEGACY_TRACKPAD_BUTTON_RIGHT = "trackpad.button.right"

    val supportedV1 = setOf(
        PREVIEW, LAUNCHER_BACKGROUND, LAUNCHER_HEADER, LAUNCHER_BRAND, TOP_SURROUND,
        BOTTOM_TRACKPAD_BACKGROUND, BOTTOM_SPLIT_BACKGROUND,
        COMPANION_BACKGROUND, NOTES_BACKGROUND, WALKTHROUGH_BACKGROUND,
        TRACKPAD_SURFACE, PANEL_FRAME,
        BUTTON_LMB_NORMAL, BUTTON_LMB_PRESSED,
        BUTTON_RMB_NORMAL, BUTTON_RMB_PRESSED,
        BUTTON_COMPANION_NORMAL, BUTTON_COMPANION_PRESSED,
        BUTTON_SETTINGS_NORMAL, BUTTON_SETTINGS_PRESSED,
        BUTTON_NOTES_NORMAL, BUTTON_NOTES_PRESSED,
        BUTTON_WALKTHROUGH_NORMAL, BUTTON_WALKTHROUGH_PRESSED,
    )
}

internal enum class SkinnableButton {
    LMB,
    RMB,
    COMPANION,
    SETTINGS,
    NOTES,
    WALKTHROUGH,
}

internal fun SkinnableButton.artworkCandidates(pressed: Boolean): Array<String> = when (this) {
    SkinnableButton.LMB -> if (pressed) arrayOf(
        SkinSlots.BUTTON_LMB_PRESSED,
        SkinSlots.BUTTON_LMB_NORMAL,
        SkinSlots.LEGACY_TRACKPAD_BUTTON_LEFT,
    ) else arrayOf(SkinSlots.BUTTON_LMB_NORMAL, SkinSlots.LEGACY_TRACKPAD_BUTTON_LEFT)
    SkinnableButton.RMB -> if (pressed) arrayOf(
        SkinSlots.BUTTON_RMB_PRESSED,
        SkinSlots.BUTTON_RMB_NORMAL,
        SkinSlots.LEGACY_TRACKPAD_BUTTON_RIGHT,
    ) else arrayOf(SkinSlots.BUTTON_RMB_NORMAL, SkinSlots.LEGACY_TRACKPAD_BUTTON_RIGHT)
    SkinnableButton.COMPANION -> if (pressed) arrayOf(
        SkinSlots.BUTTON_COMPANION_PRESSED,
        SkinSlots.BUTTON_COMPANION_NORMAL,
    ) else arrayOf(SkinSlots.BUTTON_COMPANION_NORMAL)
    SkinnableButton.SETTINGS -> if (pressed) arrayOf(
        SkinSlots.BUTTON_SETTINGS_PRESSED,
        SkinSlots.BUTTON_SETTINGS_NORMAL,
    ) else arrayOf(SkinSlots.BUTTON_SETTINGS_NORMAL)
    SkinnableButton.NOTES -> if (pressed) arrayOf(
        SkinSlots.BUTTON_NOTES_PRESSED,
        SkinSlots.BUTTON_NOTES_NORMAL,
    ) else arrayOf(SkinSlots.BUTTON_NOTES_NORMAL)
    SkinnableButton.WALKTHROUGH -> if (pressed) arrayOf(
        SkinSlots.BUTTON_WALKTHROUGH_PRESSED,
        SkinSlots.BUTTON_WALKTHROUGH_NORMAL,
    ) else arrayOf(SkinSlots.BUTTON_WALKTHROUGH_NORMAL)
}

internal fun builtInSkins(): List<InstalledSkin> = listOf(
    builtInSkin(BUILTIN_DEFAULT_SKIN_ID, "Default", AdventurePadThemes.Default),
    builtInSkin(BUILTIN_OCEAN_SKIN_ID, "Ocean", AdventurePadThemes.Ocean),
    builtInSkin(BUILTIN_ADVENTURE_SKIN_ID, "AdventurePad Launcher", AdventurePadThemes.Adventure),
)

internal fun InstalledSkin.isGameplaySelectable(): Boolean =
    manifest.id != BUILTIN_ADVENTURE_SKIN_ID

internal fun gameplaySkinChoices(catalog: List<InstalledSkin>): List<InstalledSkin> =
    catalog.filterNot(InstalledSkin::isBuiltIn)

internal fun builtInSkinIdForTheme(theme: AdventurePadThemeDefinition): String = when (theme.id) {
    AdventurePadThemes.Ocean.id -> BUILTIN_OCEAN_SKIN_ID
    AdventurePadThemes.Adventure.id -> BUILTIN_ADVENTURE_SKIN_ID
    else -> BUILTIN_DEFAULT_SKIN_ID
}

private fun builtInSkin(
    id: String,
    name: String,
    theme: AdventurePadThemeDefinition,
) = InstalledSkin(
    manifest = SkinManifest(
        formatVersion = SKIN_FORMAT_VERSION,
        id = id,
        name = name,
        author = "AdventurePad",
        packageVersion = "1.0.0",
        minimumAdventurePadVersionCode = 1,
        features = emptySet(),
        assets = emptyMap(),
        colors = emptyMap(),
        metrics = emptyMap(),
        extensions = emptyMap(),
    ),
    root = null,
    builtInTheme = theme,
)

internal fun resolveSkinIdForContext(
    context: SkinContext,
    targetId: String?,
    gameplayAssignments: Map<String, String>,
    launcherSkinId: String = BUILTIN_ADVENTURE_SKIN_ID,
): String = when (context) {
    SkinContext.LAUNCHER -> launcherSkinId
    SkinContext.GAMEPLAY -> targetId?.trim()?.takeIf(String::isNotEmpty)
        ?.let(gameplayAssignments::get)
        ?.takeUnless { it == BUILTIN_ADVENTURE_SKIN_ID }
        ?: BUILTIN_DEFAULT_SKIN_ID
    SkinContext.ADVANCED_SCUMMVM -> BUILTIN_DEFAULT_SKIN_ID
}

/** Geometry is the authoritative signal that a persistent lower activity entered or left gameplay. */
internal fun lowerSkinContextForTarget(
    requestedContext: SkinContext,
    reportedTargetId: String,
): SkinContext = when {
    requestedContext == SkinContext.ADVANCED_SCUMMVM -> SkinContext.ADVANCED_SCUMMVM
    reportedTargetId.isBlank() -> SkinContext.LAUNCHER
    else -> SkinContext.GAMEPLAY
}
