# Android Development Setup

Guide for setting up TheNet Android development environment.

## Prerequisites

Complete the [Development Environment Setup](./environment.md) first, then follow this guide.

## Android-Specific Requirements

### Android Studio
- **Version**: Latest stable (2023.1+)
- **Components Required**:
  - Android SDK Platform-Tools
  - Android SDK Build-Tools (latest)
  - Android SDK Platform (API 21+ for minimum, latest for development)
  - Android Emulator (optional, for testing)

### SDK Configuration

#### Automatic Setup (Recommended)
Android Studio typically configures everything automatically when you open the project.

#### Manual Setup
If needed, configure manually:

```bash
# Set Android SDK location
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Or on Windows
set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
set PATH=%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools
```

## Project Configuration

### 1. Android Module Structure
```
android/
├── build.gradle.kts          # Android-specific build configuration
├── src/
│   ├── main/
│   │   ├── kotlin/           # Android-specific Kotlin code
│   │   ├── res/              # Android resources
│   │   └── AndroidManifest.xml
│   └── test/                 # Android unit tests
└── proguard-rules.pro        # Obfuscation rules (for release builds)
```

### 2. Build Configuration
The Android module uses these key configurations:

```kotlin
// android/build.gradle.kts
android {
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.bigshotsoftware.thenet"
        minSdk = 21    // Android 5.0+ (supports modern crypto libraries)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-SNAPSHOT"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

## Development Workflow

### Building the Android App

```bash
# Build debug APK
./gradlew :android:assembleDebug

# Install on connected device/emulator
./gradlew :android:installDebug

# Build and install in one step
./gradlew :android:installDebug --continuous
```

### Running Tests

```bash
# Run Android unit tests
./gradlew :android:testDebugUnitTest

# Run Android instrumented tests (requires device/emulator)
./gradlew :android:connectedAndroidTest
```

### Code Quality

```bash
# Run Android-specific lint
./gradlew :android:lintDebug

# Run detekt on Android module
./gradlew :android:detekt

# Format Android code
./gradlew :android:ktlintFormat
```

## Debugging & Testing

### Device Setup

#### Physical Device
1. Enable Developer Options:
   - Go to Settings → About Phone
   - Tap Build Number 7 times
2. Enable USB Debugging in Developer Options
3. Connect via USB and accept debugging authorization

#### Emulator Setup
1. Open AVD Manager in Android Studio
2. Create Virtual Device:
   - **Device**: Pixel 7 or similar
   - **API Level**: 30+ (Android 11+)
   - **Target**: Google APIs (for Play Services)

### Logging & Debugging

```bash
# View logs from connected device
adb logcat | grep TheNet

# Filter for specific tags
adb logcat -s TheNet,P2P,Blockchain

# Clear logs
adb logcat -c
```

### Performance Profiling

#### Using Android Studio Profiler
1. Run app in debug mode
2. Open Android Studio → View → Tool Windows → Profiler
3. Select your app process
4. Monitor:
   - CPU usage (for P2P networking efficiency)
   - Memory usage (for content caching)
   - Network activity (for P2P traffic)

## Android-Specific Features

### Networking Permissions
TheNet requires extensive networking capabilities:

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

### Background Processing
For P2P networking and blockchain sync:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### Storage Permissions
For content caching and user data:

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Platform-Specific Considerations

### Security Features

#### Keystore Integration
- Private keys stored in Android Keystore
- Hardware-backed security when available
- Biometric authentication for key access

#### Network Security
```xml
<!-- network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">thenet.app</domain>
    </domain-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

### Performance Optimizations

#### ProGuard Configuration
For release builds, configure obfuscation:

```proguard
# proguard-rules.pro

# Keep TheNet core classes
-keep class com.bigshotsoftware.thenet.** { *; }

# Keep P2P networking classes
-keep class ** extends com.bigshotsoftware.thenet.p2p.** { *; }

# Keep blockchain classes  
-keep class ** extends com.bigshotsoftware.thenet.blockchain.** { *; }

# Crypto libraries
-keep class org.bouncycastle.** { *; }
-keep class com.goterl.lazysodium.** { *; }
```

## Troubleshooting

### Common Android Issues

#### "Failed to install APK"
**Problem**: Installation conflicts or permission issues
**Solutions**:
```bash
# Uninstall existing version
adb uninstall com.bigshotsoftware.thenet

# Clear app data
adb shell pm clear com.bigshotsoftware.thenet

# Try clean build
./gradlew :android:clean :android:assembleDebug
```

#### "P2P networking not working"
**Problem**: Network permissions or firewall issues
**Solutions**:
1. Check manifest permissions
2. Test on different network (mobile data vs WiFi)
3. Verify device allows background networking
4. Check Android battery optimization settings

#### "Compose rendering issues"
**Problem**: Compose Multiplatform compatibility
**Solutions**:
```bash
# Update Compose compiler
./gradlew :android:build --refresh-dependencies

# Clear Compose caches
rm -rf ~/.gradle/caches/modules-2/files-2.1/androidx.compose.*
```

#### "Blockchain sync failures"
**Problem**: Corda node connectivity
**Solutions**:
1. Verify network connectivity to bootstrap nodes
2. Check certificate trust issues
3. Review Corda logs in device storage
4. Test with emulator vs physical device

### Performance Issues

#### Memory Problems
**Symptoms**: OutOfMemoryError, app crashes
**Solutions**:
```bash
# Increase heap size for development
# In android/build.gradle.kts:
android {
    defaultConfig {
        // Increase heap size for development builds
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += ["room.incremental" to "true"]
            }
        }
    }
}
```

#### Slow Build Times
**Solutions**:
```bash
# Enable parallel builds
echo "org.gradle.parallel=true" >> gradle.properties
echo "org.gradle.configureondemand=true" >> gradle.properties

# Use build cache
echo "org.gradle.caching=true" >> gradle.properties
```

## Testing Strategy

### Unit Tests
```bash
# Run Android unit tests
./gradlew :android:testDebugUnitTest

# With coverage
./gradlew :android:testDebugUnitTestCoverage
```

### Integration Tests
```bash
# Run instrumented tests
./gradlew :android:connectedDebugAndroidTest

# Run on specific device
./gradlew :android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bigshotsoftware.thenet.P2PIntegrationTest
```

### UI Tests
```bash
# Run Compose UI tests
./gradlew :android:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.bigshotsoftware.thenet.ui
```

## Release Preparation

### Signing Configuration
```kotlin
// android/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            // Configure signing for release builds
            // Store keystore details in gradle.properties or environment variables
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### Release Build
```bash
# Build release APK
./gradlew :android:assembleRelease

# Build Android App Bundle (for Play Store)
./gradlew :android:bundleRelease
```

---

**Android Setup Complete!** 🤖

**Next Steps:**
- Start developing Android-specific features
- Review [Architecture Overview](../architecture/system-overview.md)
- Check [Contributing Guidelines](../CONTRIBUTING.md)
- Join [development discussions](https://github.com/bigshotClay/TheNet/discussions)