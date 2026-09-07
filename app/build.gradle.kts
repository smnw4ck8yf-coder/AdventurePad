import java.util.Properties

val adventurePadSigningProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val adventurePadSigningKeys = listOf(
    "ADVENTUREPAD_RELEASE_STORE_FILE",
    "ADVENTUREPAD_RELEASE_STORE_PASSWORD",
    "ADVENTUREPAD_RELEASE_KEY_ALIAS",
    "ADVENTUREPAD_RELEASE_KEY_PASSWORD",
)
val adventurePadSigningConfigured = adventurePadSigningKeys.all {
    !adventurePadSigningProperties.getProperty(it).isNullOrBlank()
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jamesmoran.adventurepad"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jamesmoran.adventurepad"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0-preview"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (adventurePadSigningConfigured) {
            create("adventurePadRelease") {
                storeFile = file(adventurePadSigningProperties.getProperty("ADVENTUREPAD_RELEASE_STORE_FILE"))
                storePassword = adventurePadSigningProperties.getProperty("ADVENTUREPAD_RELEASE_STORE_PASSWORD")
                keyAlias = adventurePadSigningProperties.getProperty("ADVENTUREPAD_RELEASE_KEY_ALIAS")
                keyPassword = adventurePadSigningProperties.getProperty("ADVENTUREPAD_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (adventurePadSigningConfigured) {
                signingConfig = signingConfigs.getByName("adventurePadRelease")
            }
        }
        release {
            if (adventurePadSigningConfigured) {
                signingConfig = signingConfigs.getByName("adventurePadRelease")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    sourceSets.getByName("main").assets.directories.add(
        layout.buildDirectory.dir("generated/authoring-spec-assets").get().asFile.absolutePath,
    )
}

val syncAuthoringTemplateSpec by tasks.registering(Copy::class) {
    from(rootProject.file("skin-authoring/AUTHORING_TEMPLATE_SPEC.json"))
    into(layout.buildDirectory.dir("generated/authoring-spec-assets/skin-authoring"))
}

tasks.named("preBuild").configure {
    dependsOn(syncAuthoringTemplateSpec)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
