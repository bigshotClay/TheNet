# Desktop Development Setup

Guide for setting up TheNet desktop development environment using Compose Multiplatform.

## Prerequisites

Complete the [Development Environment Setup](./environment.md) first, then follow this guide.

## Desktop-Specific Requirements

### Platform Support
TheNet desktop application supports:
- **Windows** 10/11 (x64)
- **macOS** 10.15+ (Intel and Apple Silicon)
- **Linux** (x64, ARM64 support planned)

### Required Software

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| **JDK** | 17+ | Runtime and development | [OpenJDK](https://openjdk.org/) |
| **Kotlin** | 1.9.22+ | Primary development language | Included with IDE |
| **Compose Desktop** | Latest | UI framework | Via Gradle dependencies |

### IDE Requirements
- **IntelliJ IDEA** 2023.2+ (Community or Ultimate)
- **Android Studio** (Alternatively, with Compose Desktop support)

## Project Configuration

### 1. Desktop Module Structure
```
desktop/
├── build.gradle.kts          # Desktop-specific build configuration
├── src/
│   ├── jvmMain/
│   │   └── kotlin/
│   │       └── com/bigshotsoftware/thenet/desktop/
│   │           └── main.kt   # Desktop application entry point
│   └── jvmTest/
│       └── kotlin/
│           └── DesktopAppTest.kt
└── resources/                # Desktop-specific resources
    ├── icons/                # Application icons
    └── assets/               # Static assets
```

### 2. Build Configuration
```kotlin
// desktop/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm("desktop")
}

compose.desktop {
    application {
        mainClass = "com.bigshotsoftware.thenet.desktop.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TheNet"
            packageVersion = "1.0.0"
            description = "Decentralized Social Platform"
            copyright = "© 2024 BigShot Software"
            vendor = "BigShot Software"
            
            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.icns"))
                bundleID = "com.bigshotsoftware.thenet"
            }
            
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.ico"))
                menuGroup = "TheNet"
                upgradeUuid = "unique-upgrade-uuid"
            }
            
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/app-icon.png"))
                debMaintainer = "hello@bigshotsoftware.com"
            }
        }
    }
}
```

## Development Workflow

### Building the Desktop App

```bash
# Build desktop JAR
./gradlew :desktop:build

# Run desktop application
./gradlew :desktop:run

# Run with continuous build (rebuilds on changes)
./gradlew :desktop:run --continuous
```

### Creating Native Distributions

```bash
# Create distributable packages for current platform
./gradlew :desktop:createDistributable

# Create runtime image (JVM + app bundled)
./gradlew :desktop:createRuntimeImage

# Platform-specific packages
./gradlew :desktop:packageDmg      # macOS
./gradlew :desktop:packageMsi      # Windows
./gradlew :desktop:packageDeb      # Linux
```

### Development Server Mode
```bash
# Run with hot reload (for UI development)
./gradlew :desktop:run -Ddevelopment=true
```

## Desktop Application Architecture

### Main Application Structure
```kotlin
// desktop/src/jvmMain/kotlin/main.kt
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.bigshotsoftware.thenet.ui.TheNetApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TheNet - Decentralized Social Platform",
        state = WindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(1200.dp, 800.dp)
        )
    ) {
        TheNetApp() // Shared UI from :ui module
    }
}
```

### Desktop-Specific Features

#### System Integration
```kotlin
// Desktop-specific implementations
// shared/src/desktopMain/kotlin/Platform.desktop.kt
actual class Platform actual constructor() {
    actual val name: String = 
        "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
}

actual class FileManager actual constructor() {
    actual fun getDataDirectory(): String {
        return when {
            System.getProperty("os.name").contains("Windows") -> 
                System.getenv("APPDATA") + "/TheNet"
            System.getProperty("os.name").contains("Mac") -> 
                System.getProperty("user.home") + "/Library/Application Support/TheNet"
            else -> 
                System.getProperty("user.home") + "/.thenet"
        }
    }
}
```

#### Native Dialogs
```kotlin
import androidx.compose.desktop.ui.tooling.preview.Preview
import java.awt.FileDialog
import java.io.File

fun openFileDialog(): File? {
    return FileDialog(null as java.awt.Frame?, "Select File", FileDialog.LOAD).apply {
        isVisible = true
    }.files.firstOrNull()
}
```

#### System Tray Integration
```kotlin
import androidx.compose.desktop.ui.tooling.preview.Preview
import java.awt.*
import java.awt.event.ActionListener

fun createSystemTray() {
    if (!SystemTray.isSupported()) return
    
    val systemTray = SystemTray.getSystemTray()
    val image = Toolkit.getDefaultToolkit().getImage("icon.png")
    
    val popupMenu = PopupMenu().apply {
        add(MenuItem("Show TheNet").apply {
            addActionListener { /* Show main window */ }
        })
        add(MenuItem("Exit").apply {
            addActionListener { exitProcess(0) }
        })
    }
    
    val trayIcon = TrayIcon(image, "TheNet", popupMenu)
    systemTray.add(trayIcon)
}
```

## Desktop-Specific Considerations

### Performance Optimizations

#### Memory Management
```bash
# JVM arguments for better performance
./gradlew :desktop:run -Dkotlin.compiler.execution.strategy=in-process -Xmx4g
```

#### Native Look and Feel
```kotlin
// Set system look and feel
import javax.swing.UIManager

fun configureNativeLookAndFeel() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel())
    } catch (e: Exception) {
        // Fall back to default
    }
}
```

### Platform-Specific Features

#### Windows Integration
- File associations for .thenet files
- Windows notifications
- Registry settings for auto-start
- Windows Store packaging (MSIX)

#### macOS Integration
- Menu bar integration
- Dock icon badge updates
- macOS notifications
- App Store packaging (if desired)

#### Linux Integration
- Desktop entry files
- D-Bus integration for notifications
- Flatpak/Snap packaging options
- XDG directory standards

## Testing

### Desktop Unit Tests
```bash
# Run desktop-specific tests
./gradlew :desktop:jvmTest

# Run with coverage
./gradlew :desktop:jvmTestCoverage
```

### UI Testing
```kotlin
// desktop/src/jvmTest/kotlin/DesktopUITest.kt
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class DesktopUITest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testMainWindow() {
        composeTestRule.setContent {
            TheNetApp()
        }
        
        composeTestRule
            .onNodeWithText("Welcome to TheNet")
            .assertIsDisplayed()
    }
}
```

### Integration Testing
```bash
# Test P2P networking on desktop
./gradlew :desktop:integrationTest

# Test blockchain integration
./gradlew :desktop:test --tests "*BlockchainIntegration*"
```

## Debugging & Profiling

### Development Debugging
```bash
# Run with debug logging
./gradlew :desktop:run -Dlogback.level=DEBUG

# Run with JVM debugging enabled
./gradlew :desktop:run --debug-jvm
```

### Performance Profiling

#### Using JProfiler
```bash
# Run with JProfiler agent
./gradlew :desktop:run -agentpath:/path/to/jprofiler/bin/agent.so=port=8849
```

#### Using VisualVM
```bash
# Enable JMX
./gradlew :desktop:run -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
```

## Deployment

### Local Distribution
```bash
# Create executable JAR with dependencies
./gradlew :desktop:shadowJar

# Create native installer for current platform
./gradlew :desktop:packageDmg       # macOS
./gradlew :desktop:packageMsi       # Windows  
./gradlew :desktop:packageDeb       # Linux
```

### Cross-Platform Distribution

#### GitHub Actions CI/CD
```yaml
# .github/workflows/desktop-build.yml
name: Desktop Build
on: [push, pull_request]

jobs:
  build-desktop:
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
    runs-on: ${{ matrix.os }}
    
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'
      
      - name: Build desktop
        run: ./gradlew :desktop:build
        
      - name: Create distribution
        run: ./gradlew :desktop:createDistributable
        
      - name: Upload artifacts
        uses: actions/upload-artifact@v3
        with:
          name: desktop-${{ matrix.os }}
          path: desktop/build/compose/binaries/
```

### Release Distribution

#### Automatic Updates
Consider integrating update mechanisms:
- Sparkle (macOS)
- WinSparkle (Windows)
- Custom update service

#### Code Signing
```bash
# macOS code signing
codesign --sign "Developer ID Application: Your Name" --deep desktop/build/compose/binaries/main/dmg/TheNet-1.0.0.dmg

# Windows code signing
signtool sign /f certificate.p12 /p password desktop/build/compose/binaries/main/msi/TheNet-1.0.0.msi
```

## Troubleshooting

### Common Desktop Issues

#### "Cannot find JDK"
**Problem**: JDK not found or wrong version
**Solutions**:
```bash
# Set JAVA_HOME explicitly
export JAVA_HOME=/path/to/jdk-17
./gradlew :desktop:run

# Or use Gradle JVM toolchain
# In build.gradle.kts:
kotlin {
    jvmToolchain(17)
}
```

#### "Compose Desktop resources not found"
**Problem**: Resource files not packaged correctly
**Solution**:
```kotlin
// Ensure resources are in correct location
// desktop/src/jvmMain/resources/
// Access via: this::class.java.getResourceAsStream("/path/to/resource")
```

#### "P2P networking fails on desktop"
**Problem**: Firewall blocking connections
**Solutions**:
1. Configure firewall exceptions
2. Test with different network configurations
3. Verify NAT traversal is working
4. Check desktop-specific networking implementations

#### "High memory usage"
**Problem**: Desktop app consuming too much memory
**Solutions**:
```bash
# Tune JVM parameters
./gradlew :desktop:run -Xms512m -Xmx2g -XX:+UseG1GC

# Enable memory profiling
./gradlew :desktop:run -XX:+PrintGCDetails -XX:+PrintMemoryLayout
```

### Platform-Specific Issues

#### Windows
- Antivirus software blocking P2P connections
- Windows Defender SmartScreen warnings
- Registry permissions for settings storage

#### macOS  
- Gatekeeper blocking unsigned applications
- Sandbox restrictions for network access
- Keychain access permissions

#### Linux
- Missing system libraries (Java FX, etc.)
- Different desktop environments (GNOME, KDE, etc.)
- Permission issues with user directories

## Performance Tips

### JVM Tuning
```bash
# Optimized JVM flags for desktop
./gradlew :desktop:run \
  -Xms1g -Xmx4g \
  -XX:+UseG1GC \
  -XX:+UseCompressedOops \
  -XX:+OptimizeStringConcat
```

### Compose Desktop Optimizations
- Use lazy loading for large lists
- Implement proper state management
- Optimize recomposition with keys and remember
- Use background threads for heavy operations

---

**Desktop Setup Complete!** 🖥️

**Next Steps:**
- Start developing desktop-specific features
- Test P2P networking across platforms
- Review [Architecture Overview](../architecture/system-overview.md)
- Implement desktop-specific UI patterns
- Check [Contributing Guidelines](../CONTRIBUTING.md)

**Desktop Development Benefits:**
- Full-featured development environment
- Easy debugging and profiling
- Native system integrations
- Powerful P2P networking capabilities