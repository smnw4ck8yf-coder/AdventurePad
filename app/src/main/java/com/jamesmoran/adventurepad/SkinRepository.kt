package com.jamesmoran.adventurepad

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemeDefinition
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class SkinRepository private constructor(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val skinsRoot = File(context.filesDir, "skins")
    private val skinImporter = SkinImporter(
        skinsRoot,
        versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt(),
    )
    private val _catalog = MutableStateFlow(loadCatalog())
    val catalog: StateFlow<List<InstalledSkin>> = _catalog
    private val _selectionRevision = MutableStateFlow(0L)
    val selectionRevision: StateFlow<Long> = _selectionRevision

    fun resolve(
        skinContext: SkinContext,
        targetId: String? = null,
        defaultGameplayTheme: AdventurePadThemeDefinition = AdventurePadThemes.Default,
    ): ResolvedSkin {
        val id = resolveSkinIdForContext(
            context = skinContext,
            targetId = targetId,
            gameplayAssignments = gameplayAssignments(),
        )
        val usesColourThemeFallback =
            skinContext == SkinContext.GAMEPLAY &&
            id == BUILTIN_DEFAULT_SKIN_ID &&
            assignedGameplaySkinId(targetId.orEmpty()) == null
        val effectiveId = if (usesColourThemeFallback) {
            builtInSkinIdForTheme(defaultGameplayTheme)
        } else {
            id
        }
        val skin = _catalog.value.firstOrNull { it.manifest.id == effectiveId }
            ?: _catalog.value.first { it.manifest.id == BUILTIN_DEFAULT_SKIN_ID }
        return ResolvedSkin(
            skin = skin,
            theme = if (usesColourThemeFallback) defaultGameplayTheme else skin.toTheme(),
        )
    }

    fun assignedGameplaySkinId(targetId: String): String? =
        preferences.getString(gameplayKey(targetId), null)

    fun assignGameplaySkin(targetId: String, skinId: String?) {
        require(targetId.isNotBlank())
        val editor = preferences.edit()
        if (skinId == null || skinId == BUILTIN_DEFAULT_SKIN_ID) {
            editor.remove(gameplayKey(targetId))
        } else {
            require(_catalog.value.any { it.manifest.id == skinId && it.isGameplaySelectable() }) {
                "Skin '$skinId' is not an installed gameplay skin"
            }
            editor.putString(gameplayKey(targetId), skinId)
        }
        editor.apply()
        _selectionRevision.value++
        context.contentResolver.notifyChange(ActiveSkinProvider.CHANGES_URI, null)
    }

    fun refresh() {
        _catalog.value = loadCatalog()
    }

    fun importSkin(uri: Uri): SkinImportResult {
        val result = skinImporter.import(context.contentResolver, uri)
        refresh()
        return result
    }

    fun remove(skinId: String): Boolean {
        if (skinId.startsWith("builtin.")) return false
        val root = File(skinsRoot, skinId)
        if (!root.isDirectory || !root.canonicalPath.startsWith(skinsRoot.canonicalPath + File.separator)) return false
        if (!root.deleteRecursively()) return false
        val editor = preferences.edit()
        preferences.all.keys.filter { key ->
            key.startsWith(KEY_GAMEPLAY_PREFIX) && preferences.getString(key, null) == skinId
        }.forEach(editor::remove)
        editor.apply()
        refresh()
        _selectionRevision.value++
        context.contentResolver.notifyChange(ActiveSkinProvider.CHANGES_URI, null)
        return true
    }

    fun importer() = skinImporter

    fun buildFromMasterPng(uri: Uri): MasterPngBuildResult {
        val result = MasterPngSkinBuilder(
            assets = context.assets,
            contentResolver = context.contentResolver,
            importer = importer(),
            cacheRoot = context.cacheDir,
        ).buildAndInstall(uri)
        refresh()
        return result
    }

    private fun gameplayAssignments(): Map<String, String> = preferences.all
        .filterKeys { it.startsWith(KEY_GAMEPLAY_PREFIX) }
        .mapNotNull { (key, value) ->
            (value as? String)?.let { key.removePrefix(KEY_GAMEPLAY_PREFIX) to it }
        }
        .toMap()

    private fun loadCatalog(): List<InstalledSkin> {
        val external = skinsRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .mapNotNull { idRoot ->
                idRoot.listFiles().orEmpty()
                    .filter { it.isDirectory && !it.name.startsWith('.') }
                    .sortedByDescending(File::getName)
                    .firstNotNullOfOrNull { versionRoot ->
                        runCatching { skinImporter.validateInstalled(versionRoot) }
                            .onFailure { Log.w(TAG, "Ignoring invalid installed skin at $versionRoot", it) }
                            .getOrNull()
                    }
            }
        return builtInSkins() + external.sortedBy { it.manifest.name.lowercase() }
    }

    private fun InstalledSkin.toTheme(): AdventurePadThemeDefinition {
        builtInTheme?.let { return it }
        val base = AdventurePadThemes.Default
        fun color(key: String, fallback: Color): Color = manifest.colors[key]?.toComposeColor() ?: fallback
        val colors = base.colors.copy(
            background = color("background", base.colors.background),
            surface = color("surface", base.colors.surface),
            surfaceRaised = color("surfaceRaised", base.colors.surfaceRaised),
            surfacePressed = color("surfacePressed", base.colors.surfacePressed),
            outline = color("outline", base.colors.outline),
            outlineStrong = color("outlineStrong", base.colors.outlineStrong),
            primary = color("primary", base.colors.primary),
            onPrimary = color("onPrimary", base.colors.onPrimary),
            textPrimary = color("textPrimary", base.colors.textPrimary),
            textSecondary = color("textSecondary", base.colors.textSecondary),
            connected = color("connected", base.colors.connected),
            disconnected = color("disconnected", base.colors.disconnected),
        )
        val components = base.components.copy(
            trackpadBackground = color("trackpadBackground", colors.surface),
            mirrorBackdrop = color("mirrorBackdrop", base.components.mirrorBackdrop),
            trackpadOverlayTint = color("controlTint", colors.surfaceRaised).copy(alpha = 0.62f),
            trackpadOverlaySeparator = color("controlOutline", colors.outline).copy(alpha = 0.78f),
        )
        return base.copy(
            id = manifest.id,
            displayName = manifest.name,
            colors = colors,
            components = components,
        )
    }

    private fun String.toComposeColor(): Color {
        val value = removePrefix("#").toLong(16)
        val argb = if (length == 7) value or 0xFF000000 else value
        return Color(argb)
    }

    private fun gameplayKey(targetId: String) = KEY_GAMEPLAY_PREFIX + targetId.trim()

    companion object {
        private const val PREFERENCES_NAME = "adventurepad_skins_v1"
        private const val KEY_GAMEPLAY_PREFIX = "gameplay_skin."
        private const val TAG = "AdventurePadSkins"

        @Volatile private var instance: SkinRepository? = null

        fun get(context: Context): SkinRepository = instance ?: synchronized(this) {
            instance ?: SkinRepository(context.applicationContext).also { instance = it }
        }
    }
}
