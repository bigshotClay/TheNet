// Enable Gradle features for better build performance and type safety
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
        maven("https://repo.eclipse.org/content/repositories/jgit-releases/")
    }
}

// Configure build cache
buildCache {
    local {
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

rootProject.name = "TheNet"

// Core application modules
include(":shared")      // Shared Kotlin Multiplatform code
include(":android")     // Android application
include(":desktop")     // Desktop application (Compose for Desktop)

// Platform modules organized by functionality
include(":p2p")         // P2P networking layer (IPv8, peer discovery)
include(":blockchain")  // Blockchain layer (Corda, smart contracts)
include(":identity")    // Identity management (DIDs, KYC, credentials)
include(":content")     // Content management (storage, versioning, distribution)
include(":ui")          // Shared UI components (Compose Multiplatform)

// Composite builds for development tools (optional)
// These can be uncommented when we have separate tool projects
// includeBuild("tools/build-logic")
// includeBuild("tools/detekt-rules")

// Configure project structure
project(":shared").projectDir = file("shared")
project(":android").projectDir = file("android")
project(":desktop").projectDir = file("desktop")
project(":p2p").projectDir = file("p2p")
project(":blockchain").projectDir = file("blockchain")
project(":identity").projectDir = file("identity")
project(":content").projectDir = file("content")
project(":ui").projectDir = file("ui")