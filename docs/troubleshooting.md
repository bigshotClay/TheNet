# Troubleshooting Guide

Comprehensive troubleshooting guide for common issues in TheNet development and deployment.

## 🚨 General Troubleshooting Approach

### Before You Start
1. **Check Recent Changes**: What was changed recently that might have caused the issue?
2. **Read Error Messages**: Full error messages often contain the solution
3. **Check Logs**: Look at application logs, build logs, and system logs
4. **Reproduce Consistently**: Can you reproduce the issue reliably?
5. **Isolate the Problem**: Is it platform-specific, environment-specific, or universal?

### Getting Help
- **GitHub Issues**: Search existing issues, create new ones with full details
- **GitHub Discussions**: Ask questions and share solutions
- **Documentation**: Check relevant setup and architecture docs
- **Community**: Connect with other developers working on similar issues

## 🏗️ Build & Development Issues

### Gradle Build Problems

#### "Could not find or load main class"
**Symptoms**: 
- Build fails with classpath errors
- "Main class not found" errors

**Solutions**:
```bash
# Clean and rebuild
./gradlew clean build

# Check JAVA_HOME
echo $JAVA_HOME
# Should point to JDK 17+ directory

# Verify Java version
java -version
javac -version
# Both should be 17+

# Reset Gradle daemon
./gradlew --stop
./gradlew build
```

#### "Out of Memory" During Build
**Symptoms**:
- Build fails with OutOfMemoryError
- Gradle daemon crashes
- Slow build performance

**Solutions**:
```bash
# Increase Gradle memory in gradle.properties
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+HeapDumpOnOutOfMemoryError

# Increase Kotlin daemon memory
kotlin.daemon.jvm.options=-Xmx3g

# Disable parallel builds if memory-constrained
# org.gradle.parallel=false

# Use Gradle build cache
org.gradle.caching=true
```

#### "Dependency Resolution Failed"
**Symptoms**:
- Cannot resolve dependencies
- Version conflicts
- Repository not found errors

**Solutions**:
```bash
# Refresh dependencies
./gradlew build --refresh-dependencies

# Clear Gradle caches
rm -rf ~/.gradle/caches/
./gradlew build

# Check repositories in settings.gradle.kts
# Ensure all required repositories are listed

# For corporate networks, configure proxy
./gradlew build -Dhttp.proxyHost=proxy.company.com -Dhttp.proxyPort=8080
```

### Kotlin Multiplatform Issues

#### "No such file or directory" for iOS Framework
**Symptoms**:
- iOS build fails with missing framework
- Xcode can't find shared framework

**Solutions**:
```bash
# Build Kotlin framework for iOS first
./gradlew :shared:linkDebugFrameworkIosX64
./gradlew :shared:linkDebugFrameworkIosArm64

# Verify framework location
ls -la shared/build/bin/ios/

# Update CocoaPods
cd ios && pod install --repo-update

# Clean Xcode build folder
# In Xcode: Product → Clean Build Folder
```

#### "Expect/Actual" Declaration Issues
**Symptoms**:
- Compile errors about missing actual declarations
- Platform-specific code not found

**Solutions**:
```kotlin
// Ensure expect declarations in commonMain
// shared/src/commonMain/kotlin/Platform.kt
expect class Platform() {
    val name: String
}

// Ensure actual declarations in platform sources
// shared/src/androidMain/kotlin/Platform.android.kt
actual class Platform actual constructor() {
    actual val name: String = "Android ${android.os.Build.VERSION.RELEASE}"
}

// shared/src/iosMain/kotlin/Platform.ios.kt
actual class Platform actual constructor() {
    actual val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}
```

### Code Quality Issues

#### Detekt Failures
**Symptoms**:
- Build fails on detekt static analysis
- Code quality violations

**Solutions**:
```bash
# Run detekt to see specific issues
./gradlew detekt

# Auto-fix some issues
./gradlew detektFormat

# Baseline current issues (temporary)
./gradlew detektBaseline

# Configure detekt rules in config/detekt/detekt.yml
# Disable specific rules if needed (sparingly)
```

#### KtLint Formatting Issues
**Symptoms**:
- Build fails on formatting checks
- Inconsistent code formatting

**Solutions**:
```bash
# Auto-fix formatting issues
./gradlew ktlintFormat

# Check what needs formatting
./gradlew ktlintCheck

# Configure .editorconfig for project-specific rules
[*.{kt,kts}]
indent_size = 4
max_line_length = 120
```

## 📱 Android Issues

### Android Build Problems

#### "Android SDK not found"
**Symptoms**:
- Build fails with SDK location errors
- Android plugin configuration issues

**Solutions**:
```bash
# Set Android SDK location
# In local.properties:
sdk.dir=/path/to/Android/Sdk

# Or set environment variable
export ANDROID_HOME=/path/to/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Verify SDK installation
$ANDROID_HOME/tools/bin/sdkmanager --list_installed
```

#### "Failed to install APK"
**Symptoms**:
- APK installation fails on device
- Permission denied errors
- Signature conflicts

**Solutions**:
```bash
# Uninstall existing version
adb uninstall com.bigshotsoftware.thenet

# Clear app data
adb shell pm clear com.bigshotsoftware.thenet

# Check device connection
adb devices

# Enable USB debugging on device
# Settings → Developer Options → USB Debugging

# Try different installation method
./gradlew :android:installDebug --info
```

### Android Runtime Issues

#### "Network Security Configuration" Errors
**Symptoms**:
- Network requests fail on Android 9+
- Cleartext traffic not permitted

**Solutions**:
```xml
<!-- android/src/main/res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>
</network-security-config>

<!-- android/src/main/AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config">
```

#### P2P Networking Issues on Android
**Symptoms**:
- Cannot connect to peers
- Network discovery fails
- Background networking doesn't work

**Solutions**:
```xml
<!-- Ensure required permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

```kotlin
// Handle network state changes
class NetworkStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        
        if (activeNetwork?.isConnected == true) {
            // Restart P2P networking
            P2PManager.reconnect()
        }
    }
}
```

## 🖥️ Desktop Issues

### Desktop Build Problems

#### "Module not found" Errors
**Symptoms**:
- Desktop build fails with module resolution errors
- Compose Desktop dependencies not found

**Solutions**:
```kotlin
// Ensure proper Compose configuration in desktop/build.gradle.kts
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
    }
}
```

#### "JavaFX runtime components are missing"
**Symptoms**:
- Desktop app fails to start
- Missing JavaFX errors

**Solutions**:
```bash
# Install JavaFX runtime
# On macOS with Homebrew:
brew install openjfx

# On Ubuntu/Debian:
sudo apt-get install openjfx

# Or use bundled runtime
./gradlew :desktop:createRuntimeImage
```

### Desktop Runtime Issues

#### High Memory Usage
**Symptoms**:
- Desktop app consumes excessive memory
- Performance degradation over time
- OutOfMemoryError during operation

**Solutions**:
```bash
# Tune JVM parameters
./gradlew :desktop:run -Xms1g -Xmx4g -XX:+UseG1GC

# Profile memory usage
./gradlew :desktop:run -XX:+PrintGCDetails -Xloggc:gc.log

# Use memory profiler (JProfiler, VisualVM, etc.)
```

#### Window/UI Issues
**Symptoms**:
- Window doesn't appear
- UI elements not rendering
- Platform-specific UI problems

**Solutions**:
```kotlin
// Check window state configuration
Window(
    onCloseRequest = ::exitApplication,
    title = "TheNet",
    state = WindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(1200.dp, 800.dp)
    )
) {
    // Application content
}

// Enable desktop debugging
System.setProperty("compose.desktop.verbose", "true")
```

## 🌐 Networking & P2P Issues

### IPv8 P2P Problems

#### "No peers discovered"
**Symptoms**:
- P2P network shows no connected peers
- Peer discovery not working
- Network appears isolated

**Solutions**:
```kotlin
// Check bootstrap nodes
class P2PConfig {
    val bootstrapNodes = listOf(
        "bootstrap1.thenet.app:8080",
        "bootstrap2.thenet.app:8080"
    )
}

// Verify network connectivity
suspend fun testNetworkConnectivity() {
    try {
        val socket = Socket()
        socket.connect(InetSocketAddress("bootstrap.thenet.app", 8080), 5000)
        socket.close()
        println("Network connectivity OK")
    } catch (e: Exception) {
        println("Network connectivity failed: $e")
    }
}
```

#### "NAT traversal failed"
**Symptoms**:
- Can't connect to peers behind NAT
- Only local network peers visible
- Firewall blocking connections

**Solutions**:
```bash
# Check firewall settings
# Windows: Windows Defender Firewall
# macOS: System Preferences → Security & Privacy → Firewall
# Linux: ufw status / iptables -L

# Test port accessibility
telnet yourip.com 8080

# Configure port forwarding on router
# Forward port 8080 to your machine

# Use alternative ports
# Try different port ranges in P2P configuration
```

### Blockchain Connectivity Issues

#### "Corda node connection failed"
**Symptoms**:
- Cannot connect to Corda network
- Blockchain operations timeout
- Node discovery issues

**Solutions**:
```bash
# Check Corda node status
curl http://localhost:10005/network-map/network-parameters

# Verify certificates
ls -la certificates/

# Check Corda node logs
tail -f logs/node-*.log

# Test RPC connection
curl -u user:password http://localhost:10006/api/rest/info
```

## 🔐 Security & Cryptography Issues

### Key Management Problems

#### "Private key not found"
**Symptoms**:
- Cryptographic operations fail
- Key storage/retrieval issues
- Signature verification fails

**Solutions**:
```kotlin
// Verify key storage implementation
class SecureKeyStorage(private val context: Context) {
    fun storePrivateKey(alias: String, privateKey: PrivateKey) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            // Store key with proper protection
            val entry = KeyStore.PrivateKeyEntry(privateKey, null)
            keyStore.setEntry(alias, entry, 
                KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
        } catch (e: Exception) {
            // Handle key storage failure
            logger.error("Failed to store private key", e)
        }
    }
}
```

#### "Certificate validation failed"
**Symptoms**:
- SSL/TLS connection errors
- Certificate trust issues
- Blockchain node authentication fails

**Solutions**:
```kotlin
// Custom certificate validation
class CustomTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // Implement custom validation logic
    }
    
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // Implement custom validation logic
    }
    
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf()
    }
}
```

## 🧪 Testing Issues

### Unit Test Failures

#### "Mock not configured" Errors
**Symptoms**:
- Tests fail with uninitialized mock objects
- Mockk configuration issues

**Solutions**:
```kotlin
@Test
fun `test should handle network error`() = runTest {
    // Properly configure mocks
    coEvery { networkService.sendMessage(any()) } throws NetworkException("Connection failed")
    
    // Test the error handling
    val result = messageService.sendMessage("test message")
    
    assertTrue(result.isFailure)
    verify(exactly = 1) { networkService.sendMessage(any()) }
}
```

#### "Coroutine test timeout"
**Symptoms**:
- Tests hang indefinitely
- Coroutine tests don't complete

**Solutions**:
```kotlin
@Test
fun `test coroutine operation`() = runTest {
    // Use TestScope for coroutine tests
    val testScope = TestScope()
    
    testScope.launch {
        // Your async operation
        val result = suspendingFunction()
        assertEquals(expected, result)
    }
    
    testScope.advanceUntilIdle()
}
```

### Integration Test Issues

#### "Test network unreachable"
**Symptoms**:
- Integration tests can't connect to test services
- Network-dependent tests fail in CI

**Solutions**:
```kotlin
// Use test containers for integration tests
@Testcontainers
class P2PIntegrationTest {
    @Container
    val mockServer = MockServerContainer(DockerImageName.parse("mockserver/mockserver"))
    
    @Test
    fun `should connect to mock peer`() {
        val baseUrl = "http://${mockServer.host}:${mockServer.serverPort}"
        // Test with controlled mock server
    }
}
```

## 🚀 Performance Issues

### Memory Leaks

#### "Memory usage grows over time"
**Symptoms**:
- Application memory usage increases continuously
- OutOfMemoryError after extended use
- Performance degradation

**Solutions**:
```kotlin
// Use weak references for listeners
class EventManager {
    private val listeners = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<EventListener, Boolean>())
    )
    
    fun addListener(listener: EventListener) {
        listeners.add(listener)
    }
    
    // Listeners will be garbage collected automatically
}

// Properly dispose of resources
class ResourceManager : Closeable {
    override fun close() {
        // Clean up resources
        connections.forEach { it.close() }
        executors.forEach { it.shutdown() }
    }
}
```

### Slow Performance

#### "UI freezes during operations"
**Symptoms**:
- UI becomes unresponsive
- Long-running operations block main thread
- Poor user experience

**Solutions**:
```kotlin
// Move heavy operations to background threads
@Composable
fun MessageList(viewModel: MessageViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    LaunchedEffect(Unit) {
        // Load messages in background
        viewModel.loadMessages()
    }
    
    if (isLoading) {
        CircularProgressIndicator()
    } else {
        LazyColumn {
            items(messages) { message ->
                MessageItem(message)
            }
        }
    }
}
```

## 📊 Logging & Debugging

### Enable Debug Logging
```bash
# Set log levels
export LOG_LEVEL=DEBUG

# Platform-specific logging
# Android: Use adb logcat
adb logcat -s TheNet

# Desktop: Console output
./gradlew :desktop:run --info

# iOS: Xcode console
# View → Debug Area → Show Debug Area
```

### Structured Logging
```kotlin
// Use structured logging
class Logger(private val tag: String) {
    fun debug(message: String, vararg args: Pair<String, Any>) {
        if (isDebugEnabled) {
            val context = args.joinToString(", ") { "${it.first}=${it.second}" }
            println("[$tag] DEBUG: $message [$context]")
        }
    }
}

// Usage
logger.debug("P2P connection established", 
    "peerId" to peer.id,
    "address" to peer.address,
    "latency" to connectionLatency
)
```

## 🆘 Emergency Procedures

### Complete Environment Reset
```bash
# Nuclear option: reset everything
./gradlew clean
rm -rf ~/.gradle/caches/
rm -rf ~/.gradle/wrapper/
rm -rf build/
rm -rf */build/

# Re-download everything
./gradlew build --refresh-dependencies
```

### Rollback Strategy
```bash
# Revert to last known good state
git log --oneline -10
git checkout <last-good-commit>

# Create hotfix branch
git checkout -b hotfix/critical-fix

# Apply minimal fix
# Test thoroughly
# Merge and deploy
```

---

## 📞 Getting Additional Help

### Before Asking for Help
1. Search existing GitHub issues and discussions
2. Check this troubleshooting guide thoroughly
3. Try the solutions provided above
4. Gather complete error messages and logs
5. Create a minimal reproduction case

### Creating Effective Bug Reports
```markdown
## Bug Description
Clear description of what's wrong

## Expected Behavior
What should happen

## Actual Behavior  
What actually happens

## Steps to Reproduce
1. Step one
2. Step two
3. Step three

## Environment
- OS: macOS 13.5
- Kotlin: 1.9.22
- Gradle: 8.0
- Device: iPhone 15 Pro

## Logs/Screenshots
[Attach relevant logs and screenshots]
```

### Community Support
- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: Questions and community help
- **Documentation**: Always check docs first
- **Code Reviews**: Learn from pull request discussions

Remember: The community is here to help, but help us help you by providing complete information and showing that you've tried to solve the problem yourself first.

---

**Happy Debugging!** 🐛🔧

Most issues have solutions - it's just a matter of finding the right approach. Don't hesitate to ask for help when you need it.