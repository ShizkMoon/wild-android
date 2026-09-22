// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9.x built-in Kotlin: kotlin-android plugin must not be applied
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
