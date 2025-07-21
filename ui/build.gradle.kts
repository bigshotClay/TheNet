plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.dokka)
}

// Enable experimental Compose libraries
@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

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
                
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
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
                
                // Android-specific Compose
                implementation(libs.androidx.compose.bom)
                implementation(libs.bundles.compose.android)
                implementation(compose.preview)
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
                implementation(compose.desktop.currentOs)
                implementation(compose.preview)
            }
        }
        
        val desktopTest by getting
    }
}

android {
    namespace = "com.bigshotsoftware.thenet.ui"
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
                "proguard-rules.pro"
            )
        }
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}