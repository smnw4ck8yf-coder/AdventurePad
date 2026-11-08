package com.jamesmoran.adventurepad

internal const val COCOON_LAUNCHER_ID = "cocoon"
internal const val COCOON_PACKAGE_NAME = "rip.moth.cocoonshell"
internal const val ADVENTUREPAD_PLATFORM_FILE_NAME = "adventurepad-platform.json"
internal const val ADVENTUREPAD_PLATFORM_ID = "adventurepad-scummvm"
internal const val ADVENTUREPAD_PLAYER_ID = "scummvm.com.jamesmoran.adventurepad"

internal data class ExternalLauncherDefinition(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>,
)

internal interface ExternalLauncherAdapter {
    val definition: ExternalLauncherDefinition
    val platformFileName: String

    fun platformFileContent(): String
    fun setupInstructions(folderName: String): List<String>
}

internal object CocoonExternalLauncherAdapter : ExternalLauncherAdapter {
    override val definition = ExternalLauncherDefinition(
        id = COCOON_LAUNCHER_ID,
        displayName = "Cocoon",
        packageNames = setOf(COCOON_PACKAGE_NAME),
    )

    override val platformFileName = ADVENTUREPAD_PLATFORM_FILE_NAME

    override fun platformFileContent(): String = cocoonPlatformJson()

    override fun setupInstructions(folderName: String): List<String> = listOf(
        "In Cocoon, open Settings → Library & data and choose Add Platform.",
        "Use Import Custom Platform and select $ADVENTUREPAD_PLATFORM_FILE_NAME from $folderName.",
        "Open AdventurePad ScummVM and select $folderName as its games folder.",
        "Confirm AdventurePad is the selected player, then rescan only AdventurePad ScummVM.",
    )
}

internal val SupportedExternalLaunchers: List<ExternalLauncherAdapter> = listOf(
    CocoonExternalLauncherAdapter,
)

internal data class ExternalLauncherSelection(
    val selectedId: String?,
    val detectedIds: Set<String>,
    val needsManualSelection: Boolean,
)

internal fun selectExternalLauncher(
    supported: List<ExternalLauncherDefinition>,
    installedPackages: Set<String>,
    rememberedId: String?,
): ExternalLauncherSelection {
    val supportedById = supported.associateBy(ExternalLauncherDefinition::id)
    val detected = supported
        .filter { definition -> definition.packageNames.any(installedPackages::contains) }
        .mapTo(linkedSetOf(), ExternalLauncherDefinition::id)
    val remembered = rememberedId?.takeIf(supportedById::containsKey)
    val selected = remembered ?: detected.singleOrNull()
    return ExternalLauncherSelection(
        selectedId = selected,
        detectedIds = detected,
        needsManualSelection = selected == null,
    )
}

internal fun cocoonPlatformJson(): String = """
    {
      "databaseVersion": 14,
      "revisionNumber": 1,
      "platform": {
        "name": "AdventurePad ScummVM",
        "uniqueId": "$ADVENTUREPAD_PLATFORM_ID",
        "shortname": "$ADVENTUREPAD_PLATFORM_ID",
        "description": "Community integration for launching configured ScummVM targets through AdventurePad. Not affiliated with Cocoon.",
        "acceptedFilenameRegex": "^(.*)\\.(?:dpt)${'$'}",
        "scraperSourceList": [
          "LIBRETRO:ScummVM"
        ],
        "boxArtAspectRatioId": 0,
        "useCustomBoxArtAspectRatio": true,
        "customBoxArtAspectRatio": "2:3",
        "screenAspectRatioId": 3,
        "useCustomScreenAspectRatio": false,
        "customScreenAspectRatio": null,
        "retroAchievementsConsoleIdList": [],
        "extra": ""
      },
      "playerList": [
        {
          "name": "AdventurePad",
          "uniqueId": "$ADVENTUREPAD_PLAYER_ID",
          "description": "Supported extensions: dpt. Requires AdventurePad.",
          "acceptedFilenameRegex": "^(.*)\\.(?:dpt)${'$'}",
          "amStartArguments": "-n com.jamesmoran.adventurepad/com.jamesmoran.adventurepad.ExternalGameLaunchActivity\n -a com.jamesmoran.adventurepad.action.LAUNCH_SCUMMVM_TARGET\n -e com.jamesmoran.adventurepad.extra.TARGET_ID {tags.scummvm_id}",
          "killPackageProcesses": false,
          "killPackageProcessesWarning": false,
          "extra": ""
        }
      ]
    }
""".trimIndent() + "\n"

internal fun findExternalLauncher(
    id: String?,
    launchers: List<ExternalLauncherAdapter> = SupportedExternalLaunchers,
): ExternalLauncherAdapter? = launchers.firstOrNull { it.definition.id == id }
