plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.9.20"
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }
        
        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }
        
        
        val desktopMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
            }
        }
        
        val desktopTest by getting
    }
}

android {
    namespace = "com.bigshotsoftware.thenet.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}