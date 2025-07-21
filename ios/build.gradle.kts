// iOS module configuration
// The actual iOS target is configured in shared/build.gradle.kts
// This file defines iOS-specific tasks and configurations

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // iOS targets are configured in the shared module
    // This is kept as a placeholder for iOS-specific build logic
    
    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += "-Xexpect-actual-classes"
            }
        }
    }
}

// iOS-specific tasks can be added here
tasks.register("syncPodsForIOS") {
    description = "Sync CocoaPods for iOS development"
    group = "ios"
    
    doLast {
        exec {
            workingDir = projectDir
            commandLine("pod", "install")
        }
    }
}