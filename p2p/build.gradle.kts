plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core Kotlin libraries
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                // Networking
                implementation(libs.bundles.networking.common)

                // P2P IPv8 dependencies
                implementation(project(":ipv8"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.testing.common)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.bundles.android.common)
                implementation(libs.kotlinx.coroutines.android)
                // Android-specific IPv8 implementation
                implementation(project(":ipv8-android"))
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.bundles.testing.android)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
                // Desktop-specific networking libraries
                implementation(libs.okhttp)
                // JVM-specific IPv8 implementation
                implementation(project(":ipv8-jvm"))
            }
        }

        val desktopTest by getting
    }
}

android {
    namespace = "com.bigshotsoftware.thenet.p2p"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}
