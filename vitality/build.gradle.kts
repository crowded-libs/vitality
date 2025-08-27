import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.spm)
}

group = "io.github.crowded-libs"
version = "0.1.0"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    val iosTargets = listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    )
    iosTargets.forEach { iosTarget ->
        iosTarget.compilations {
            val main by getting {
                cinterops.create("HealthKitBindings")
            }
        }
    }


    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.time.ExperimentalTime")
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val androidMain by getting {
            languageSettings.optIn("androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi")
            dependencies {
                implementation(libs.connect.client)
            }
        }
    }
}

android {
    namespace = "io.github.crowdedlibs.vitality"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // Use baseline to allow progressive API support for medical records
        baseline = file("lint-baseline.xml")
    }
}

dokka {
    moduleName = project.name
    dokkaSourceSets {
        named("commonMain")
    }
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
    description = "A HTML Documentation JAR containing Dokka HTML"
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-doc")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "vitality", version.toString())

    pom {
        name = "vitality"
        description = "A Kotlin Multiplatform library providing unified health data access across Apple HealthKit (iOS) and Health Connect (Android)"
        inceptionYear = "2025"
        url = "https://github.com/crowded-libs/vitality/"
        licenses {
            license {
                name = "Apache 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "coreykaylor"
                name = "Corey Kaylor"
                email = "corey@kaylors.net"
            }
        }
        scm {
            url = "https://github.com/crowded-libs/vitality/"
            connection = "scm:git:git://github.com/crowded-libs/vitality.git"
            developerConnection = "scm:git:ssh://git@github.com/crowded-libs/vitality.git"
        }
    }
}

swiftPackageConfig {
    create("HealthKitBindings") {
        minIos = "13.0"
    }
}
