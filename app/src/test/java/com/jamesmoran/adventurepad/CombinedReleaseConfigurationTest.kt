package com.jamesmoran.adventurepad

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedReleaseConfigurationTest {
    @Test
    fun bundledEngineSupportsBackupImportRestartWithoutAddingLauncherIcon() {
        val root = repositoryRoot()
        val applicationManifest = parseXml(File(root, "app/src/main/AndroidManifest.xml"))
        val engineManifest = parseXml(File(root, "scummvm-engine/src/main/AndroidManifest.xml"))
        val androidNamespace = "http://schemas.android.com/apk/res/android"

        val launcherActivities = applicationManifest.getElementsByTagName("activity").asSequence()
            .filter { activity ->
                activity.childNodes.asSequence().any { child ->
                    child.nodeName == "intent-filter" && child.childNodes.asSequence().any { entry ->
                        entry.nodeName == "category" &&
                            entry.attributes?.getNamedItemNS(androidNamespace, "name")?.nodeValue ==
                            "android.intent.category.LAUNCHER"
                    }
                }
            }
            .toList()
        assertEquals(1, launcherActivities.size)

        val engineActivities = engineManifest.getElementsByTagName("activity").asSequence().toList()
        val splash = engineActivities.singleOrNull {
            it.attributes?.getNamedItemNS(androidNamespace, "name")?.nodeValue ==
                "org.scummvm.scummvm.SplashActivity"
        }
        assertNotNull("Backup import restarts through ScummVM's SplashActivity", splash)
        assertFalse(
            "The bundled engine must not add a second launcher entry",
            File(root, "scummvm-engine/src/main/AndroidManifest.xml")
                .readText()
                .contains("android.intent.category.LAUNCHER"),
        )
    }

    @Test
    fun enginePinIsAnExactCommit() {
        val properties = java.util.Properties().apply {
            File(repositoryRoot(), "scummvm-engine.properties").inputStream().use(::load)
        }
        assertTrue(properties.getProperty("revision").matches(Regex("[0-9a-f]{40}")))
        assertEquals(
            "https://github.com/smnw4ck8yf-coder/ScummVM-AdventurePad.git",
            properties.getProperty("repository"),
        )
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun repositoryRoot(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (!File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile ?: error("Could not find the AdventurePad repository root.")
        }
        return directory
    }
}

private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> = sequence {
    for (index in 0 until length) yield(item(index))
}
