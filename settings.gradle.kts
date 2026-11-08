pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AdventurePad"
include(":app")

val bundledScummVM = providers.gradleProperty("adventurepad.bundledEngine")
    .orNull
    .equals("true", ignoreCase = true)
if (bundledScummVM) {
    include(":scummvmEngine")
    project(":scummvmEngine").projectDir = file("scummvm-engine")
}
