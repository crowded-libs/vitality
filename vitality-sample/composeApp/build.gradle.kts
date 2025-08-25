import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Optimize iOS builds - only build for the current architecture during development
    val iosTargets = when {
        // Check for property to build all iOS targets
        project.hasProperty("ios.buildAll") -> listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
        // Check for specific target property
        project.hasProperty("ios.target") -> when (project.property("ios.target")) {
            "x64" -> listOf(iosX64())
            "arm64" -> listOf(iosArm64())
            "simulatorArm64" -> listOf(iosSimulatorArm64())
            else -> listOf(iosSimulatorArm64()) // Default to simulator on Apple Silicon
        }
        // Default: Build only for simulator on Apple Silicon (most common dev setup)
        else -> listOf(iosSimulatorArm64())
    }
    
    iosTargets.forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "io.github.crowdedlibs.vitality_sample")
            // Export the vitality library to make it available in iOS
            export(project(":vitality"))
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(project(":vitality"))
        }
    }
}

android {
    namespace = "io.github.crowdedlibs.vitality_sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.crowdedlibs.vitality_sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

