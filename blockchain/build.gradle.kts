plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
        publishLibraryVariants("release", "debug")
    }

    jvm("desktop") {
        jvmToolchain(17)
        // withJava() removed due to Android plugin compatibility
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Core Kotlin libraries
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                // Crypto libraries
                implementation(libs.bundles.crypto.common)

                // Corda dependencies will be added as they become available
                // implementation("net.corda:corda-core:${libs.versions.corda.get()}")
                // implementation("net.corda:corda-rpc:${libs.versions.corda.get()}")
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
                implementation(libs.lazysodium.android)
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
                implementation(libs.lazysodium.java)
            }
        }

        val desktopTest by getting
    }
}

android {
    namespace = "com.bigshotsoftware.thenet.blockchain"
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
