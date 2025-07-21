# Development Environment Setup

This guide covers the core development environment setup required for TheNet development across all platforms.

## Prerequisites

### Required Software

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| **JDK** | 17+ | Java/Kotlin compilation, Corda | [OpenJDK](https://openjdk.org/) or [Oracle JDK](https://www.oracle.com/java/) |
| **Kotlin** | 1.9.22+ | Primary development language | Included with IntelliJ/Android Studio |
| **Git** | 2.30+ | Version control | [git-scm.com](https://git-scm.com/) |
| **Docker** | 20.10+ | Test networks, Corda nodes | [docker.com](https://www.docker.com/) |

### IDE Options

#### Primary: Android Studio (Recommended)
```bash
# Download from: https://developer.android.com/studio
# Includes Kotlin support, Android SDK, and excellent Kotlin Multiplatform support
```

#### Alternative: IntelliJ IDEA Ultimate
```bash
# Download from: https://www.jetbrains.com/idea/
# Full Kotlin Multiplatform support, but requires separate Android SDK setup
```

## Project Setup

### 1. Clone Repository
```bash
git clone git@github.com:bigshotClay/TheNet.git
cd TheNet
```

### 2. Verify Java Installation
```bash
java -version
# Should show Java 17 or higher
```

### 3. Verify Gradle Wrapper
```bash
./gradlew --version
# Should show Gradle 8.0+ and Kotlin 1.9.22+
```

### 4. Initial Build
```bash
# Build all modules
./gradlew build

# This will:
# - Download dependencies
# - Compile all Kotlin Multiplatform modules
# - Run initial code quality checks
# - Generate some initial artifacts
```

## Environment Configuration

### Gradle Properties
Create/update `local.properties` in the project root:
```properties
# Android SDK location (if using Android Studio, this should be auto-generated)
sdk.dir=/path/to/Android/Sdk

# Gradle optimization settings
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true

# Kotlin compiler optimizations
kotlin.compiler.execution.strategy=in-process
kotlin.daemon.jvm.options=-Xmx2g
```

### IDE Configuration

#### Android Studio Setup
1. Open the project root directory in Android Studio
2. Let it auto-configure the project structure
3. Install recommended plugins:
   - Kotlin Multiplatform Mobile (if not already installed)
   - Detekt (code quality)
   - Dokka (documentation generation)

#### IntelliJ IDEA Setup
1. Import project as Gradle project
2. Configure Android SDK path in Project Structure
3. Enable Kotlin Multiplatform support in plugins

## Verification

### Build System Check
```bash
# Test all modules compile
./gradlew compileKotlin

# Run all tests
./gradlew test

# Check code quality
./gradlew detekt ktlintCheck
```

### Module Verification
```bash
# Verify shared module
./gradlew :shared:build

# Verify platform modules
./gradlew :p2p:build :blockchain:build :identity:build :content:build :ui:build
```

## Development Tools

### Code Quality Tools

#### Detekt (Static Analysis)
```bash
# Run detekt on all modules
./gradlew detektAll

# Configuration: config/detekt/detekt.yml
```

#### KtLint (Code Formatting)
```bash
# Check formatting
./gradlew ktlintCheckAll

# Auto-fix formatting issues
./gradlew ktlintFormat
```

#### Dokka (Documentation Generation)
```bash
# Generate API documentation
./gradlew dokkaHtml

# Output: build/dokka/html/index.html
```

### Testing Tools
```bash
# Run all tests
./gradlew testAll

# Generate test reports
./gradlew testReport

# Run specific module tests
./gradlew :shared:test
```

## Troubleshooting

### Common Issues

#### "Could not find or load main class"
**Problem**: Java/Kotlin classpath issues
**Solution**: 
```bash
./gradlew clean build
# If persistent, check JAVA_HOME and PATH
```

#### "Gradle daemon stopped unexpectedly"
**Problem**: Insufficient memory allocation
**Solution**: Increase memory in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g
```

#### "Android SDK not found"
**Problem**: Missing or misconfigured Android SDK
**Solution**: 
1. Install Android Studio
2. Update `local.properties` with correct `sdk.dir`
3. Or set `ANDROID_HOME` environment variable

#### "Detekt/KtLint configuration errors"
**Problem**: Missing configuration files
**Solution**: 
```bash
# Create config directory structure if missing
mkdir -p config/detekt
# Copy default configs from project template
```

## Next Steps

After completing environment setup:

1. **Android Development**: See [Android Setup Guide](./android.md)
2. **iOS Development**: See [iOS Setup Guide](./ios.md) 
3. **Desktop Development**: See [Desktop Setup Guide](./desktop.md)
4. **Contributing**: Read [Contributing Guidelines](../CONTRIBUTING.md)
5. **Architecture**: Review [System Overview](../architecture/system-overview.md)

## Performance Tips

### Gradle Build Optimization
```bash
# Use build cache
./gradlew build --build-cache

# Use configuration cache (experimental)
./gradlew build --configuration-cache

# Parallel builds (already enabled in gradle.properties)
./gradlew build --parallel
```

### IDE Performance
- Allocate sufficient memory to IDE (4GB+ recommended)
- Exclude build directories from indexing
- Use "Power Save Mode" when not actively developing
- Close unused modules in multi-module view

---

**Environment Setup Complete!** 🎉

Next: Choose your platform-specific setup guide from the links above.