// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version libs.versions.agp.get() apply false
    alias(libs.plugins.kotlin.compose) apply false
}
