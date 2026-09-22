plugins {
    alias(libs.plugins.android.application)
    // kotlin-android must NOT be applied (AGP built-in Kotlin); the Compose compiler
    // plugin is still required when buildFeatures.compose = true.
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.wild.android"
    // Installed platform is android-37.2 (Android 17 minor release).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.wild.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.androidx.material3.window)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.work.runtime)

    // DI
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.navigation)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    // Images
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // Serialization / coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
