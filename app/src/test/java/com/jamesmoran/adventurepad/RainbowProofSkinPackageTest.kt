package com.jamesmoran.adventurepad

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RainbowProofSkinPackageTest {
    @Test
    fun generatedProofManifestAndHashesPassRuntimeContracts() {
        val packageFile = repositoryFile("proof-skin/RainbowProofSkin.apskin")
        ZipFile(packageFile).use { archive ->
            val manifest = SkinManifestParser.parse(
                archive.getInputStream(archive.getEntry("skin.json")).bufferedReader().use { it.readText() },
            )
            assertEquals("org.adventurepad.proof.rainbow", manifest.id)
            assertEquals(17, manifest.assets.size)
            assertTrue(manifest.assets.keys.containsAll(SkinSlots.supportedV1 - setOf(
                SkinSlots.LAUNCHER_BACKGROUND, SkinSlots.LAUNCHER_HEADER, SkinSlots.LAUNCHER_BRAND,
            )))
            manifest.assets.values.forEach { asset ->
                val entry = requireNotNull(archive.getEntry(asset.path))
                val digest = MessageDigest.getInstance("SHA-256")
                archive.getInputStream(entry).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                assertEquals(asset.sha256, digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
    }

    @Test
    fun rainbowCompanionFamilyResolvesItsExactSurfaceAssets() {
        val root = requireNotNull(repositoryFile("proof-skin/extracted/skin.json").parentFile)
        val installed = InstalledSkin(
            SkinManifestParser.parse(root.resolve("skin.json").readText()),
            root,
        )
        val resolved = ResolvedSkin(installed, com.jamesmoran.adventurepad.ui.theme.AdventurePadThemes.Default)

        listOf(
            CompanionSection.HOME to SkinSlots.COMPANION_BACKGROUND,
            CompanionSection.NOTES to SkinSlots.NOTES_BACKGROUND,
            CompanionSection.WALKTHROUGH to SkinSlots.WALKTHROUGH_BACKGROUND,
        ).forEach { (section, expectedSlot) ->
            assertArrayEquals(arrayOf(expectedSlot), companionBackgroundCandidates(section))
            assertEquals(expectedSlot, resolved.resolveAssetSlot(*companionBackgroundCandidates(section)))
            assertEquals(
                installed.manifest.assets.getValue(expectedSlot).path,
                resolved.assetFile(expectedSlot)?.relativeTo(root)?.invariantSeparatorsPath,
            )
        }
    }

    private fun repositoryFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(3) {
            File(directory, relativePath).takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: directory
        }
        error("Could not locate $relativePath")
    }
}
