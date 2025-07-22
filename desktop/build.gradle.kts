import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.dokka)
}

kotlin {
    jvmToolchain(17)
    
    jvm {
        withJava()

        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // Project modules
                implementation(projects.shared)

                // Compose Desktop
                implementation(compose.desktop.currentOs)

                // Coroutines
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.testing.common)
                implementation(libs.bundles.testing.jvm)
            }
        }
    }
}

// Configure JUnit 5 for JVM tests
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

compose.desktop {
    application {
        mainClass = "com.bigshotsoftware.thenet.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TheNet"
            packageVersion = "1.0.0"

            description = "TheNet - Decentralized Social Platform"
            copyright = "© 2025 TheNet Project"
            vendor = "TheNet Project"

            // License file will be added later
            // licenseFile.set(project.file("../LICENSE"))

            windows {
                menuGroup = "TheNet"
                // Uncomment when ready for production
                // upgradeUuid = "12345678-1234-1234-1234-123456789012"
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.ico"))
                shortcut = true
                menu = true
            }

            macOS {
                bundleID = "app.thenet.desktop"
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.icns"))
            }

            linux {
                packageName = "thenet"
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.png"))
            }

            includeAllModules = true
        }

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

// Custom tasks
tasks.register("createAppBundle") {
    dependsOn("packageDistributionForCurrentOS")
    description = "Creates application bundle for current OS"
    group = "distribution"
}

tasks.register("createAllAppBundles") {
    dependsOn("packageDmg", "packageMsi", "packageDeb")
    description = "Creates application bundles for all supported platforms"
    group = "distribution"
}
