package com.jamesmoran.adventurepad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkinRepositoryInstrumentedTest {
    private val targetId = "instrumentation-skin-target"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository by lazy {
        SkinRepository.get(context)
    }
    private val installedTestIds = mutableSetOf<String>()

    @After
    fun clearAssignment() {
        repository.assignGameplaySkin(targetId, null)
        installedTestIds.forEach(repository::remove)
    }

    @Test
    fun activePerTargetSkinReachesLowerGameplayResolutionAndRefreshesLive() {
        val initialRevision = repository.selectionRevision.value

        repository.assignGameplaySkin(targetId, BUILTIN_OCEAN_SKIN_ID)

        assertTrue(repository.selectionRevision.value > initialRevision)
        val resolved = repository.resolve(SkinContext.GAMEPLAY, targetId)
        assertEquals(BUILTIN_OCEAN_SKIN_ID, resolved.id)
        assertEquals(AdventurePadThemes.Ocean.id, resolved.theme.id)

        repository.assignGameplaySkin(targetId, null)

        assertEquals(
            BUILTIN_DEFAULT_SKIN_ID,
            repository.resolve(SkinContext.GAMEPLAY, targetId).id,
        )
    }

    @Test
    fun selectedAdventureColourThemeIsTheNoSkinGameplayFallback() {
        repository.assignGameplaySkin(targetId, null)

        val resolved = repository.resolve(
            SkinContext.GAMEPLAY,
            targetId,
            defaultGameplayTheme = AdventurePadThemes.Adventure,
        )

        assertEquals(BUILTIN_ADVENTURE_SKIN_ID, resolved.id)
        assertEquals("adventure", resolved.theme.id)
    }

    @Test
    fun installedCustomSkinAppearsAndRemovingSelectionFallsBackToNoSkin() {
        val skinId = "org.adventurepad.test.${System.nanoTime()}".also(installedTestIds::add)
        installTestSkin(skinId)
        repository.refresh()

        assertTrue(repository.catalog.value.any { it.manifest.id == skinId && !it.isBuiltIn })
        repository.assignGameplaySkin(targetId, skinId)
        assertEquals(skinId, repository.resolve(SkinContext.GAMEPLAY, targetId).id)

        assertTrue(repository.remove(skinId))

        assertFalse(repository.catalog.value.any { it.manifest.id == skinId })
        assertNull(repository.assignedGameplaySkinId(targetId))
        assertEquals(BUILTIN_DEFAULT_SKIN_ID, repository.resolve(SkinContext.GAMEPLAY, targetId).id)
        installedTestIds.remove(skinId)
    }

    @Test
    fun corruptSelectedCustomSkinIsIgnoredAndGameplayFallsBackSafely() {
        val skinId = "org.adventurepad.test.${System.nanoTime()}".also(installedTestIds::add)
        val installedRoot = installTestSkin(skinId)
        repository.refresh()
        repository.assignGameplaySkin(targetId, skinId)

        installedRoot.resolve("assets/background.png").delete()
        repository.refresh()

        assertFalse(repository.catalog.value.any { it.manifest.id == skinId })
        assertEquals(BUILTIN_DEFAULT_SKIN_ID, repository.resolve(SkinContext.GAMEPLAY, targetId).id)
    }

    @Test
    fun malformedInstalledSkinDoesNotCrashCatalogRefresh() {
        val skinId = "org.adventurepad.test.${System.nanoTime()}".also(installedTestIds::add)
        val versionRoot = File(context.filesDir, "skins/$skinId/1.0.0").apply { mkdirs() }
        versionRoot.resolve("skin.json").writeText("{broken")

        repository.refresh()

        assertFalse(repository.catalog.value.any { it.manifest.id == skinId })
    }

    @Test
    fun validMasterPngInstallsRefreshesImmediatelyAndCanBeRemoved() {
        val source = createPng("GeneratedRainbow.png", 4720, 4040)
        try {
            val installed = repository.buildFromMasterPng(Uri.fromFile(source)).installedSkin
            installedTestIds += installed.manifest.id

            assertTrue(repository.catalog.value.any { it.manifest.id == installed.manifest.id })
            assertEquals(17, installed.manifest.assets.size)
            assertTrue(repository.remove(installed.manifest.id))
            assertFalse(repository.catalog.value.any { it.manifest.id == installed.manifest.id })
            installedTestIds -= installed.manifest.id
        } finally {
            source.delete()
        }
    }

    @Test
    fun masterPngRejectsWrongDimensionsAndCorruptPng() {
        val wrongSize = createPng("WrongSize.png", 64, 64)
        val corrupt = File(context.cacheDir, "Corrupt-${System.nanoTime()}.png").apply {
            writeBytes(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 1, 2, 3))
        }
        try {
            val dimensions = assertThrows(MasterPngBuildException::class.java) {
                repository.buildFromMasterPng(Uri.fromFile(wrongSize))
            }
            assertTrue(dimensions.message.orEmpty().contains("4720×4040"))

            val unreadable = assertThrows(MasterPngBuildException::class.java) {
                repository.buildFromMasterPng(Uri.fromFile(corrupt))
            }
            assertTrue(unreadable.message.orEmpty().contains("corrupt", ignoreCase = true))
        } finally {
            wrongSize.delete()
            corrupt.delete()
        }
    }

    @Test
    fun importingSameMasterPngAgainDoesNotOverwriteInstalledSkin() {
        val source = createPng("DuplicateRainbow.png", 4720, 4040)
        try {
            val first = repository.buildFromMasterPng(Uri.fromFile(source)).installedSkin
            installedTestIds += first.manifest.id
            val installedRoot = requireNotNull(first.root)
            val manifestTimestamp = installedRoot.resolve("skin.json").lastModified()

            val duplicate = assertThrows(MasterPngBuildException::class.java) {
                repository.buildFromMasterPng(Uri.fromFile(source))
            }

            assertTrue(duplicate.message.orEmpty().contains("already installed", ignoreCase = true))
            assertEquals(manifestTimestamp, installedRoot.resolve("skin.json").lastModified())
            assertEquals(1, repository.catalog.value.count { it.manifest.id == first.manifest.id })
        } finally {
            source.delete()
        }
    }

    private fun installTestSkin(skinId: String): File {
        val image = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).run {
                try {
                    compress(Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                } finally {
                    recycle()
                }
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(image)
            .joinToString("") { "%02x".format(it) }
        val manifest = """
            {
              "formatVersion": 1,
              "id": "$skinId",
              "name": "Instrumented Skin",
              "author": "AdventurePad Tests",
              "packageVersion": "1.0.0",
              "minimumAdventurePadVersionCode": 1,
              "assets": {
                "bottom.trackpad.background": {
                  "path": "assets/background.png",
                  "scale": "cover",
                  "sha256": "$hash"
                }
              }
            }
        """.trimIndent()
        val staging = File(context.cacheDir, "skin-test-${System.nanoTime()}").apply { mkdir() }
        val packageFile = staging.resolve("test.apskin")
        ZipOutputStream(packageFile.outputStream()).use { output ->
            output.writeEntry("skin.json", manifest.toByteArray())
            output.writeEntry("preview.png", image)
            output.writeEntry("assets/background.png", image)
        }
        return try {
            requireNotNull(
                repository.importer().import(
                    context.contentResolver,
                    Uri.fromFile(packageFile),
                    replaceExisting = false,
                ).installedSkin.root,
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun createPng(name: String, width: Int, height: Int): File {
        val file = File(context.cacheDir, "${System.nanoTime()}-$name")
        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).run {
            try {
                eraseColor(Color.MAGENTA)
                require(file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) })
            } finally {
                recycle()
            }
        }
        return file
    }

    private fun ZipOutputStream.writeEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }
}
