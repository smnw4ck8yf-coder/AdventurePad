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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
