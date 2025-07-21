# iOS Development Setup

Guide for setting up TheNet iOS development environment.

## Prerequisites

Complete the [Development Environment Setup](./environment.md) first, then follow this guide.

⚠️ **macOS Required**: iOS development requires macOS with Xcode. Windows/Linux developers can contribute to shared modules but cannot build iOS targets.

## macOS-Specific Requirements

### Required Software

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| **Xcode** | 15.0+ | iOS compilation, simulators | [App Store](https://apps.apple.com/app/xcode/id497799835) |
| **Xcode Command Line Tools** | Latest | Git, build tools | `xcode-select --install` |
| **CocoaPods** | 1.11+ | iOS dependency management | `sudo gem install cocoapods` |
| **Homebrew** | Latest | Package management | [brew.sh](https://brew.sh) |

### Optional Tools
- **iOS Simulator**: Included with Xcode
- **SF Symbols**: For iOS-native iconography
- **Instruments**: For performance profiling

## Xcode Setup

### 1. Install Xcode
```bash
# Download from App Store or Apple Developer portal
# After installation, accept license
sudo xcodebuild -license accept

# Install command line tools
xcode-select --install
```

### 2. Configure Xcode
1. Launch Xcode
2. Go to Xcode → Preferences → Accounts
3. Add your Apple ID (required for device deployment)
4. Go to Xcode → Preferences → Locations
5. Verify Command Line Tools path is set

### 3. Install CocoaPods
```bash
# Install CocoaPods
sudo gem install cocoapods

# Initialize CocoaPods
pod setup
```

## Project Configuration

### 1. iOS Module Structure
```
ios/
├── Podfile                   # CocoaPods dependencies
├── build.gradle.kts          # Kotlin Multiplatform iOS configuration
└── TheNet.xcworkspace        # Xcode workspace (generated)
```

### 2. CocoaPods Integration

The `ios/Podfile` configures iOS-specific dependencies:

```ruby
# ios/Podfile
platform :ios, '14.0'
use_frameworks!

target 'TheNet' do
  # Kotlin Multiplatform shared framework
  pod 'shared', :path => '../shared'
  
  # iOS-specific dependencies
  pod 'CryptoKit'              # Native iOS cryptography
  pod 'Network'                # iOS networking framework
  pod 'SwiftUI'                # Modern iOS UI framework
end
```

### 3. Build Configuration
```kotlin
// ios/build.gradle.kts
kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
}
```

## Development Workflow

### Building iOS Framework

```bash
# Build shared Kotlin framework for iOS
./gradlew :shared:linkDebugFrameworkIosX64          # Intel simulator
./gradlew :shared:linkDebugFrameworkIosArm64        # Device
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64  # Apple Silicon simulator

# Build for all iOS targets
./gradlew :shared:linkDebugFrameworkIos
```

### CocoaPods Integration

```bash
# Navigate to iOS directory
cd ios

# Install/update pods
pod install

# Open Xcode workspace (not .xcodeproj!)
open TheNet.xcworkspace
```

### Running on Simulator

```bash
# List available simulators
xcrun simctl list devices

# Build and run on simulator
./gradlew :shared:linkDebugFrameworkIosX64
cd ios && pod install
xcodebuild -workspace TheNet.xcworkspace -scheme TheNet -destination 'platform=iOS Simulator,name=iPhone 15' build
```

## Xcode Development

### Project Structure in Xcode
```
TheNet.xcworkspace/
├── TheNet/                   # iOS app target
│   ├── TheNetApp.swift       # SwiftUI app entry point
│   ├── ContentView.swift     # Main view
│   ├── ViewModels/           # MVVM pattern view models
│   ├── Views/                # SwiftUI views
│   └── Resources/            # Assets, localization
├── Pods/                     # CocoaPods dependencies
└── shared/                   # Kotlin Multiplatform framework
```

### Swift-Kotlin Interop

#### Calling Kotlin from Swift
```swift
// Import the shared framework
import shared

class MainViewModel: ObservableObject {
    private let greeting = Greeting()
    
    @Published var greetingText = ""
    
    func loadGreeting() {
        greetingText = greeting.greet()
    }
}
```

#### iOS-Specific Implementations
```kotlin
// shared/src/iosMain/kotlin/Platform.ios.kt
actual class Platform actual constructor() {
    actual val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}
```

## Testing

### Kotlin Tests on iOS
```bash
# Run shared module tests for iOS
./gradlew :shared:iosX64Test
./gradlew :shared:iosArm64Test
./gradlew :shared:iosSimulatorArm64Test
```

### iOS Unit Tests
```swift
// TheNetTests/TheNetTests.swift
import XCTest
@testable import TheNet
import shared

final class TheNetTests: XCTestCase {
    func testGreeting() throws {
        let greeting = Greeting()
        XCTAssertFalse(greeting.greet().isEmpty)
    }
}
```

### UI Tests with SwiftUI
```swift
// TheNetUITests/TheNetUITests.swift
import XCTest

final class TheNetUITests: XCTestCase {
    func testLaunch() throws {
        let app = XCUIApplication()
        app.launch()
        
        XCTAssertTrue(app.staticTexts["Welcome to TheNet"].exists)
    }
}
```

## iOS-Specific Features

### Platform Capabilities

#### Network Framework Integration
```kotlin
// For P2P networking on iOS
// shared/src/iosMain/kotlin/network/P2PNetworking.ios.kt
actual class P2PNetworking {
    // Use iOS Network framework for low-level networking
    // Integrate with Network.framework for better iOS integration
}
```

#### Keychain Services
```kotlin
// For secure key storage
// shared/src/iosMain/kotlin/security/KeyStorage.ios.kt
actual class KeyStorage {
    actual fun storePrivateKey(key: ByteArray) {
        // Use iOS Keychain Services
        // Integrate with SecKeychain* APIs
    }
}
```

#### Background Processing
```swift
// For maintaining P2P connections
// Configure background modes in Info.plist
// Use BGTaskScheduler for periodic sync
```

### Performance Optimizations

#### Memory Management
- iOS uses ARC (Automatic Reference Counting)
- Be mindful of retain cycles between Kotlin and Swift
- Use `weak` references appropriately in Swift

#### App Lifecycle Integration
```swift
// TheNetApp.swift
import SwiftUI
import shared

@main
struct TheNetApp: App {
    init() {
        // Initialize Kotlin Multiplatform components
        KotlinDependencies().initialize()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
                    // Handle app backgrounding
                    P2PManager.shared.pauseNetworking()
                }
        }
    }
}
```

## Troubleshooting

### Common iOS Issues

#### "Framework not found 'shared'"
**Problem**: Kotlin framework not built or not found by CocoaPods
**Solutions**:
```bash
# Build framework first
./gradlew :shared:linkDebugFrameworkIosX64

# Then update pods
cd ios && pod install

# Clean and rebuild if needed
./gradlew clean
rm -rf ios/Pods ios/*.xcworkspace
cd ios && pod install
```

#### "Module 'shared' was not compiled for testing"
**Problem**: Test framework not built
**Solution**:
```bash
# Build test framework
./gradlew :shared:linkDebugTestFrameworkIosX64
```

#### "CocoaPods could not find compatible versions"
**Problem**: Dependency version conflicts
**Solutions**:
```bash
# Update CocoaPods repository
pod repo update

# Clear CocoaPods cache
pod cache clean --all

# Update Podfile.lock
cd ios && pod update
```

#### "Xcode build fails with signing errors"
**Problem**: Code signing configuration
**Solutions**:
1. Select development team in Xcode project settings
2. Ensure Apple ID is added to Xcode accounts
3. For device deployment, ensure device is registered in developer portal

#### "P2P networking doesn't work on iOS"
**Problem**: iOS network permissions or restrictions
**Solutions**:
1. Add network usage descriptions to Info.plist
2. Test on device vs simulator (networking differences)
3. Check iOS network privacy settings
4. Verify background app refresh is enabled

### Performance Issues

#### Large Framework Size
**Problem**: Kotlin framework too large
**Solutions**:
```kotlin
// Enable dead code elimination
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xdce=true",
                "-opt-in=kotlin.ExperimentalStdlibApi"
            )
        }
    }
}
```

#### Slow Build Times
**Solutions**:
```bash
# Use incremental compilation
echo "kotlin.incremental.native=true" >> gradle.properties

# Parallel builds
echo "org.gradle.parallel=true" >> gradle.properties

# Build cache
echo "org.gradle.caching=true" >> gradle.properties
```

## Deployment

### Development Deployment
```bash
# Build and run on connected device
./gradlew :shared:linkDebugFrameworkIosArm64
cd ios && pod install
xcodebuild -workspace TheNet.xcworkspace -scheme TheNet -destination 'generic/platform=iOS' archive
```

### App Store Preparation

#### Release Configuration
```kotlin
// Build release framework
./gradlew :shared:linkReleaseFrameworkIosArm64
```

#### Archive and Upload
1. In Xcode: Product → Archive
2. Use Organizer to validate and upload to App Store Connect
3. Configure app metadata in App Store Connect
4. Submit for App Store review

### TestFlight Beta Testing
1. Archive release build in Xcode
2. Upload to App Store Connect
3. Configure TestFlight testing
4. Invite beta testers via email or public link

## Platform Considerations

### iOS Guidelines Compliance
- Follow Apple Human Interface Guidelines
- Ensure app respects user privacy preferences
- Handle background app refresh gracefully
- Support iOS accessibility features
- Consider Dark Mode and Dynamic Type

### App Store Review
- P2P networking may require explanation in review notes
- Blockchain functionality needs clear user benefit explanation
- Ensure content moderation policies are documented
- Privacy policy must detail data handling

---

**iOS Setup Complete!** 🍎

**Next Steps:**
- Start developing iOS-specific features in SwiftUI
- Review [Architecture Overview](../architecture/system-overview.md)
- Test P2P networking on iOS devices
- Check [Contributing Guidelines](../CONTRIBUTING.md)

**iOS Development Tips:**
- Use iOS Simulator for UI development
- Test P2P features on physical devices
- Monitor memory usage with Instruments
- Follow iOS-specific design patterns