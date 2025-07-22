// Top-level build file for TheNet project
// Defines common configuration for all subprojects

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        classpath("app.cash.sqldelight:gradle-plugin:2.0.1")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.10")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.8.6")
    }
}

plugins {
    // Apply plugins to subprojects only, using version catalog
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Common configuration for all projects
allprojects {
    group = "com.bigshotsoftware.thenet"
    version = "1.0.0-SNAPSHOT"
}

// Common configuration for all subprojects
subprojects {
    // Apply common plugins
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    
    // Configure Kotlin compilation
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.ExperimentalUnsignedTypes",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }
    
    // Configure detekt when applied
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom("$rootDir/config/detekt/detekt.yml")
            baseline = file("$rootDir/config/detekt/baseline.xml")
        }
    }
    
    // Configure ktlint when applied
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("0.50.0")
            debug.set(false)
            verbose.set(true)
            android.set(false)
            outputToConsole.set(true)
            outputColorName.set("RED")
            ignoreFailures.set(false)
            enableExperimentalRules.set(true)
        }
    }
}

// Global tasks
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("detektAll") {
    dependsOn(subprojects.map { "${it.path}:detekt" })
}

tasks.register("ktlintCheckAll") {
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
}

tasks.register("testAll") {
    dependsOn(subprojects.map { "${it.path}:test" })
    description = "Run all tests across all subprojects"
    group = "verification"
}

tasks.register("testReport") {
    dependsOn("testAll")
    description = "Generate consolidated test report"
    group = "verification"
    
    doLast {
        println("Test results can be found in:")
        subprojects.forEach { project ->
            val testReportDir = File(project.layout.buildDirectory.get().asFile, "reports/tests")
            if (testReportDir.exists()) {
                println("  - ${testReportDir.absolutePath}")
            }
        }
    }
}
