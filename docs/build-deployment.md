# Build & Deployment Guide

Comprehensive guide for building, testing, and deploying TheNet across all platforms and environments.

## 🏗️ Build System Overview

TheNet uses Gradle with Kotlin DSL for all build operations across the multiplatform project.

### Build Tools
- **Gradle**: 8.0+ with Kotlin DSL
- **Kotlin Multiplatform**: 1.9.22+
- **Android Gradle Plugin**: 8.1+
- **Compose Multiplatform**: Latest stable
- **Detekt**: Static code analysis
- **KtLint**: Code formatting
- **Dokka**: API documentation generation

## 🚀 Local Development Builds

### Basic Build Commands
```bash
# Build all modules
./gradlew build

# Clean build (removes all build artifacts)
./gradlew clean build

# Build specific module
./gradlew :shared:build
./gradlew :android:build
./gradlew :desktop:build

# Build with detailed output
./gradlew build --info --stacktrace
```

### Testing Commands
```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew testCoverage

# Run tests for specific module
./gradlew :shared:test
./gradlew :p2p:test
./gradlew :blockchain:test

# Run integration tests
./gradlew integrationTest

# Generate test report
./gradlew testReport
```

### Code Quality Commands
```bash
# Run all quality checks
./gradlew check

# Static code analysis
./gradlew detekt
./gradlew detektAll  # All modules

# Code formatting check
./gradlew ktlintCheck
./gradlew ktlintCheckAll  # All modules

# Auto-fix formatting
./gradlew ktlintFormat

# Generate API documentation
./gradlew dokkaHtml
```

## 📱 Platform-Specific Builds

### Android Build
```bash
# Build debug APK
./gradlew :android:assembleDebug

# Build release APK
./gradlew :android:assembleRelease

# Build Android App Bundle (for Play Store)
./gradlew :android:bundleRelease

# Install on connected device
./gradlew :android:installDebug

# Run Android tests
./gradlew :android:testDebugUnitTest
./gradlew :android:connectedDebugAndroidTest
```

### Desktop Build
```bash
# Run desktop application
./gradlew :desktop:run

# Create executable JAR
./gradlew :desktop:build

# Create native distributions
./gradlew :desktop:createDistributable

# Create platform-specific installers
./gradlew :desktop:packageDmg      # macOS
./gradlew :desktop:packageMsi      # Windows
./gradlew :desktop:packageDeb      # Linux

# Create runtime image (JVM + app bundled)
./gradlew :desktop:createRuntimeImage
```

### iOS Build (Future)
```bash
# Build Kotlin framework for iOS
./gradlew :shared:linkDebugFrameworkIosX64
./gradlew :shared:linkReleaseFrameworkIosArm64

# Install CocoaPods dependencies
cd ios && pod install

# Build iOS app (requires Xcode)
xcodebuild -workspace ios/TheNet.xcworkspace -scheme TheNet build
```

## 🔧 Build Configuration

### Gradle Properties
Optimize builds with `gradle.properties`:

```properties
# Build performance
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true

# Kotlin optimizations
kotlin.compiler.execution.strategy=in-process
kotlin.daemon.jvm.options=-Xmx2g
kotlin.incremental=true
kotlin.incremental.native=true

# Android optimizations
android.useAndroidX=true
android.enableJetifier=true
android.enableR8.fullMode=true

# Development flags
development.enableLogging=true
development.mockNetworking=false
```

### Version Catalog
Dependencies managed in `gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "1.9.22"
compose = "1.5.11"
corda = "4.9"
ipv8 = "2.0.0"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
compose-ui = { module = "androidx.compose.ui:ui", version.ref = "compose" }
corda-core = { module = "net.corda:corda-core", version.ref = "corda" }
ipv8-kotlin = { module = "nl.tudelft.ipv8:ipv8-kotlin", version.ref = "ipv8" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
```

### Build Variants

#### Development
```kotlin
// build.gradle.kts
buildTypes {
    getByName("debug") {
        isDebuggable = true
        applicationIdSuffix = ".debug"
        buildConfigField("String", "P2P_BOOTSTRAP_NODE", "\"dev.thenet.app\"")
        buildConfigField("String", "BLOCKCHAIN_NETWORK", "\"dev-network\"")
    }
}
```

#### Staging
```kotlin
buildTypes {
    create("staging") {
        initWith(getByName("debug"))
        isMinifyEnabled = true
        applicationIdSuffix = ".staging"
        buildConfigField("String", "P2P_BOOTSTRAP_NODE", "\"staging.thenet.app\"")
        buildConfigField("String", "BLOCKCHAIN_NETWORK", "\"staging-network\"")
    }
}
```

#### Production
```kotlin
buildTypes {
    getByName("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        buildConfigField("String", "P2P_BOOTSTRAP_NODE", "\"bootstrap.thenet.app\"")
        buildConfigField("String", "BLOCKCHAIN_NETWORK", "\"production-network\"")
    }
}
```

## 🤖 Continuous Integration

### GitHub Actions Workflow
`.github/workflows/ci.yml`:

```yaml
name: CI Build and Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
          
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Run code quality checks
      run: ./gradlew detektAll ktlintCheckAll
      
    - name: Run tests
      run: ./gradlew testAll
      
    - name: Build all modules
      run: ./gradlew build
      
    - name: Upload test reports
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-reports
        path: |
          **/build/reports/tests/
          **/build/reports/detekt/
```

### Android CI/CD
```yaml
android-build:
  runs-on: ubuntu-latest
  needs: test
  
  steps:
  - uses: actions/checkout@v3
  
  - name: Set up JDK 17
    uses: actions/setup-java@v3
    with:
      java-version: '17'
      distribution: 'temurin'
      
  - name: Build Android APK
    run: ./gradlew :android:assembleDebug
    
  - name: Run Android tests
    run: ./gradlew :android:testDebugUnitTest
    
  - name: Upload APK
    uses: actions/upload-artifact@v3
    with:
      name: debug-apk
      path: android/build/outputs/apk/debug/
```

### Desktop CI/CD
```yaml
desktop-build:
  strategy:
    matrix:
      os: [ubuntu-latest, windows-latest, macos-latest]
  runs-on: ${{ matrix.os }}
  needs: test
  
  steps:
  - uses: actions/checkout@v3
  
  - name: Set up JDK 17
    uses: actions/setup-java@v3
    with:
      java-version: '17'
      distribution: 'temurin'
      
  - name: Build desktop application
    run: ./gradlew :desktop:createDistributable
    
  - name: Upload desktop artifacts
    uses: actions/upload-artifact@v3
    with:
      name: desktop-${{ matrix.os }}
      path: desktop/build/compose/binaries/main/
```

## 🚢 Deployment

### Environment Setup

#### Development Environment
```bash
# Local development with mock services
export THENET_ENV=development
export P2P_BOOTSTRAP_NODE=localhost:8080
export BLOCKCHAIN_NETWORK=dev-network
export LOG_LEVEL=DEBUG
```

#### Staging Environment
```bash
# Staging environment with real services
export THENET_ENV=staging
export P2P_BOOTSTRAP_NODE=staging.thenet.app:8080
export BLOCKCHAIN_NETWORK=staging-network
export LOG_LEVEL=INFO
```

#### Production Environment
```bash
# Production environment
export THENET_ENV=production
export P2P_BOOTSTRAP_NODE=bootstrap.thenet.app:8080
export BLOCKCHAIN_NETWORK=mainnet
export LOG_LEVEL=WARN
```

### Android Deployment

#### Google Play Store
```bash
# Build signed release bundle
./gradlew :android:bundleRelease

# Upload to Play Console (manual or automated)
# Configure Play Console for staged rollout
# Monitor crash reports and user feedback
```

#### F-Droid (Open Source)
```bash
# Build reproducible release APK
./gradlew :android:assembleRelease --build-cache

# Submit to F-Droid repository
# Follow F-Droid metadata requirements
# Ensure all dependencies are FOSS-compatible
```

### Desktop Deployment

#### Direct Download
```bash
# Create platform-specific installers
./gradlew :desktop:packageDmg      # macOS
./gradlew :desktop:packageMsi      # Windows
./gradlew :desktop:packageDeb      # Linux

# Sign installers (production)
codesign --sign "Developer ID" TheNet.dmg  # macOS
signtool sign /f cert.p12 TheNet.msi       # Windows

# Upload to download servers
# Configure auto-update mechanism
```

#### Package Managers
```bash
# Homebrew (macOS)
brew tap bigshotClay/thenet
brew install thenet

# Chocolatey (Windows)
choco install thenet

# Snap (Linux)
snap install thenet
```

### Infrastructure Deployment

#### Bootstrap Nodes
```bash
# Deploy P2P bootstrap nodes
docker run -d \
  --name thenet-bootstrap \
  -p 8080:8080 \
  -e THENET_ENV=production \
  thenet/bootstrap:latest

# Configure load balancing
# Set up monitoring and alerting
# Plan for geographic distribution
```

#### Corda Network
```bash
# Deploy Corda nodes
docker-compose up -d corda-node

# Configure network map service
# Set up certificate management
# Plan for node scaling and maintenance
```

## 🔍 Build Optimization

### Build Performance
```bash
# Parallel builds
echo "org.gradle.parallel=true" >> gradle.properties

# Build cache
echo "org.gradle.caching=true" >> gradle.properties

# Configuration cache (experimental)
./gradlew build --configuration-cache

# Daemon optimization
echo "org.gradle.daemon=true" >> gradle.properties
echo "org.gradle.jvmargs=-Xmx4g" >> gradle.properties
```

### Module Dependencies
```kotlin
// Optimize module dependencies
dependencies {
    // Use API dependencies sparingly
    api("com.essential:library")
    
    // Prefer implementation dependencies
    implementation("com.internal:module")
    
    // Use compileOnly for build-time dependencies
    compileOnly("com.buildtime:tool")
}
```

### Build Scripts
```kotlin
// Optimize build scripts
tasks.register("quickBuild") {
    dependsOn("compileKotlin")
    // Skip tests for quick iteration
}

tasks.register("fullCheck") {
    dependsOn("build", "detektAll", "ktlintCheckAll", "testAll")
    description = "Run all checks and tests"
}
```

## 🐛 Troubleshooting Builds

### Common Issues

#### "Out of Memory" Errors
```bash
# Increase Gradle memory
echo "org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g" >> gradle.properties

# Increase Kotlin compiler memory
echo "kotlin.daemon.jvm.options=-Xmx3g" >> gradle.properties
```

#### "Gradle Daemon" Issues
```bash
# Stop all Gradle daemons
./gradlew --stop

# Clean and rebuild
./gradlew clean build --no-daemon
```

#### "Dependency Resolution" Failures
```bash
# Refresh dependencies
./gradlew build --refresh-dependencies

# Clear Gradle caches
rm -rf ~/.gradle/caches/
./gradlew build
```

#### "Multiplatform" Configuration Issues
```bash
# Check multiplatform configuration
./gradlew tasks --all | grep link

# Debug platform targets
./gradlew :shared:targets
```

### Build Debugging
```bash
# Verbose build output
./gradlew build --info --stacktrace --debug

# Profile build performance
./gradlew build --profile

# Scan build for issues
./gradlew build --scan
```

## 📊 Build Metrics

### Performance Monitoring
- Build time tracking per module
- Test execution time analysis
- Code quality metrics trending
- Dependency update impact assessment

### Quality Gates
- Minimum test coverage: 80%
- Maximum build time: 10 minutes
- Zero high-priority detekt issues
- All ktlint formatting checks pass
- All security scans pass

---

**Build & Deployment Complete!** 🚀

This guide covers all aspects of building and deploying TheNet. For specific issues, check the [Troubleshooting Guide](./troubleshooting.md) or create a GitHub issue.