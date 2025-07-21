import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        jvmToolchain(17)
        withJava()
    }
    
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
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
            copyright = "© 2024 TheNet Project"
            vendor = "TheNet Project"
            
            licenseFile.set(project.file("../LICENSE"))
            
            windows {
                menuGroup = "TheNet"
                // upgradeUuid = "..."
            }
            
            macOS {
                bundleID = "app.thenet.desktop"
            }
            
            linux {
                packageName = "thenet"
            }
        }
    }
}