package com.jamesmoran.adventurepad

import java.io.File
import java.nio.file.Files
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkinImporterTest {
    private lateinit var workspace: File
    private lateinit var skinsRoot: File
    private lateinit var importer: SkinImporter

    @Before
    fun setUp() {
        workspace = Files.createTempDirectory("adventurepad-skin-import").toFile()
        skinsRoot = File(workspace, "skins").apply { mkdirs() }
        importer = SkinImporter(skinsRoot, versionCode = 1) { file ->
            testImageBounds(file.readBytes())
        }
    }

    @After
    fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test
    fun validPackageInstallsIntoManagedLibraryAndCanBeReloaded() {
        val packageFile = packageFile(manifest = manifest())

        val installed = install(packageFile).installedSkin

        assertEquals(SKIN_ID, installed.manifest.id)
        assertEquals("Test Skin", installed.manifest.name)
        assertTrue(File(skinsRoot, "$SKIN_ID/1.0.0/skin.json").isFile)
        assertEquals(SKIN_ID, importer.validateInstalled(requireNotNull(installed.root)).manifest.id)
    }

    @Test
    fun rejectsMissingAndMalformedManifest() {
        assertFailure(SkinImportFailure.MISSING_MANIFEST) {
            install(packageFile(manifest = null))
        }
        assertFailure(SkinImportFailure.MALFORMED_MANIFEST) {
            install(packageFile(manifest = "{not json"))
        }
    }

    @Test
    fun rejectsUnsupportedVersionAndReservedId() {
        assertFailure(SkinImportFailure.UNSUPPORTED_VERSION) {
            install(packageFile(manifest = manifest(formatVersion = 2)))
        }
        assertFailure(SkinImportFailure.RESERVED_ID) {
            install(packageFile(manifest = manifest(id = BUILTIN_DEFAULT_SKIN_ID)))
        }
    }

    @Test
    fun rejectsMissingDeclaredAssetAndInvalidImage() {
        assertFailure(SkinImportFailure.MISSING_ASSET) {
            install(packageFile(manifest = manifest(), includeAsset = false))
        }
        assertFailure(SkinImportFailure.INVALID_IMAGE) {
            install(packageFile(manifest = manifest(assetBytes = INVALID_IMAGE), assetBytes = INVALID_IMAGE))
        }
    }

    @Test
    fun duplicateSkinIdIsRejectedEvenForAnotherPackageVersion() {
        install(packageFile(manifest = manifest()))

        assertFailure(SkinImportFailure.DUPLICATE_ID) {
            install(packageFile(manifest = manifest(packageVersion = "2.0.0")))
        }
        assertFalse(File(skinsRoot, "$SKIN_ID/2.0.0").exists())
    }

    @Test
    fun rejectsTraversalCaseCollisionsAndUnsupportedFiles() {
        assertFailure(SkinImportFailure.INVALID_PACKAGE) {
            install(packageFile(manifest = manifest(), extras = mapOf("../escape.png" to IMAGE)))
        }
        assertFailure(SkinImportFailure.INVALID_PACKAGE) {
            install(packageFile(manifest = manifest(), extras = mapOf("Preview.png" to IMAGE)))
        }
        assertFailure(SkinImportFailure.INVALID_PACKAGE) {
            install(packageFile(manifest = manifest(), extras = mapOf("assets/payload.svg" to IMAGE)))
        }
    }

    @Test
    fun failedImportLeavesNoPartialManagedInstallation() {
        assertFailure(SkinImportFailure.INVALID_IMAGE) {
            install(packageFile(manifest = manifest(), previewBytes = INVALID_IMAGE))
        }

        assertFalse(File(skinsRoot, SKIN_ID).exists())
        assertTrue(skinsRoot.listFiles().orEmpty().none { !it.name.startsWith('.') })
    }

    @Test
    fun rainbowProofPackageStillPassesImporterValidation() {
        val proof = repositoryFile("proof-skin/RainbowProofSkin.apskin")

        val installed = install(proof).installedSkin

        assertEquals("org.adventurepad.proof.rainbow", installed.manifest.id)
        assertEquals(21, installed.manifest.assets.size)
    }

    private fun install(packageFile: File): SkinImportResult {
        val staging = File(workspace, "staging-${System.nanoTime()}").apply { mkdir() }
        return try {
            importer.installZip(packageFile, staging, replaceExisting = false)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun packageFile(
        manifest: String?,
        includeAsset: Boolean = true,
        previewBytes: ByteArray = IMAGE,
        assetBytes: ByteArray = IMAGE,
        extras: Map<String, ByteArray> = emptyMap(),
    ): File {
        val file = File(workspace, "package-${System.nanoTime()}.apskin")
        ZipOutputStream(file.outputStream()).use { output ->
            if (manifest != null) output.entry("skin.json", manifest.toByteArray())
            output.entry("preview.png", previewBytes)
            if (includeAsset) output.entry(ASSET_PATH, assetBytes)
            extras.forEach { (path, bytes) -> output.entry(path, bytes) }
        }
        return file
    }

    private fun ZipOutputStream.entry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun manifest(
        id: String = SKIN_ID,
        formatVersion: Int = 1,
        packageVersion: String = "1.0.0",
        assetBytes: ByteArray = IMAGE,
    ) = """
        {
          "formatVersion": $formatVersion,
          "id": "$id",
          "name": "Test Skin",
          "author": "Test Author",
          "packageVersion": "$packageVersion",
          "minimumAdventurePadVersionCode": 1,
          "assets": {
            "bottom.trackpad.background": {
              "path": "$ASSET_PATH",
              "scale": "cover",
              "sha256": "${assetBytes.sha256()}"
            }
          }
        }
    """.trimIndent()

    private fun assertFailure(expected: SkinImportFailure, block: () -> Unit) {
        val exception = assertThrows(SkinImportException::class.java, block)
        assertEquals(expected, exception.failure)
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun repositoryFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(3) {
            File(directory, relativePath).takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: directory
        }
        error("Could not locate $relativePath")
    }

    private fun testImageBounds(bytes: ByteArray): DecodedImageBounds? {
        if (bytes.contentEquals(INVALID_IMAGE)) return null
        val isPng = bytes.size >= 24 && bytes.sliceArray(0 until 8).contentEquals(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10),
        )
        return if (isPng) {
            val header = ByteBuffer.wrap(bytes, 16, 8)
            DecodedImageBounds(header.int, header.int)
        } else {
            DecodedImageBounds(32, 32)
        }
    }

    private companion object {
        const val SKIN_ID = "org.example.import-test"
        const val ASSET_PATH = "assets/background.png"
        val IMAGE = "valid-image".toByteArray()
        val INVALID_IMAGE = "invalid-image".toByteArray()
    }
}
