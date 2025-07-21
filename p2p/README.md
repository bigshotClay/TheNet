# P2P Networking Module

This module provides the peer-to-peer networking functionality for TheNet using the IPv8 protocol.

## Overview

The P2P module integrates the [Kotlin IPv8](https://github.com/Tribler/kotlin-ipv8) library to provide:
- Decentralized peer discovery
- NAT traversal and puncturing
- Secure peer-to-peer communication
- Community-based networking overlays

## Architecture

### Key Components

1. **IPv8Manager**: Main interface for managing the P2P network
   - Controls network lifecycle (start/stop)
   - Manages peer connections
   - Handles message routing

2. **IPv8Community**: Base interface for network communities
   - DiscoveryCommunity: Handles peer discovery and basic connectivity
   - TrustChainCommunity: Provides blockchain/ledger functionality

3. **IPv8Configuration**: Configuration management
   - Network settings (port, bootstrap peers)
   - Community enable/disable flags
   - Performance tuning parameters

### Integration Approach

The IPv8 library is integrated as a Git submodule rather than a traditional dependency because:
- The library is not published to Maven Central
- We need to synchronize Kotlin and dependency versions
- This allows us to track a specific version and apply patches if needed

#### Build Configuration

To support IPv8's build requirements, we've:
- Updated to Kotlin 2.1.10 to match IPv8
- Upgraded Gradle to 8.10.2 for Android Gradle Plugin compatibility
- Added SQLDelight plugin support for IPv8's database needs
- Configured the project to use older Gradle plugin syntax where needed

#### Module Structure

```
p2p/
├── src/
│   ├── commonMain/           # Shared code across platforms
│   │   └── kotlin/
│   │       └── com/bigshotsoftware/thenet/p2p/
│   │           ├── IPv8Manager.kt         # Main P2P interface
│   │           ├── IPv8Community.kt       # Community interfaces
│   │           ├── config/                # Configuration classes
│   │           └── impl/                  # Implementation classes
│   ├── androidMain/          # Android-specific implementations
│   ├── desktopMain/          # Desktop-specific implementations
│   └── commonTest/           # Shared tests
└── build.gradle.kts
```

## Usage

### Basic Configuration

```kotlin
val config = IPv8Configuration(
    port = 8090,
    enableDiscoveryCommunity = true,
    enableTrustChainCommunity = true,
    maxPeers = 50
)
```

### Starting the Network

```kotlin
// Create platform-specific manager instance
val ipv8Manager: IPv8Manager = // platform implementation

// Configure and start
ipv8Manager.start(
    IPv8Manager.IPv8Config(
        port = 8090,
        bootstrapPeers = listOf("bootstrap.thenet.app:8090")
    )
)

// Monitor network status
ipv8Manager.networkStatus.collect { status ->
    when (status) {
        NetworkStatus.RUNNING -> println("Network is running")
        NetworkStatus.ERROR -> println("Network error occurred")
        // ...
    }
}
```

### Message Handling

```kotlin
// Register a message handler
ipv8Manager.registerMessageHandler("chat") { peerId, message ->
    val text = message.decodeToString()
    println("Received from $peerId: $text")
}

// Send a message
ipv8Manager.sendMessage(peerId, "Hello, peer!".encodeToByteArray())
```

## Testing

Run tests with:
```bash
./gradlew :p2p:test
```

The module includes:
- Unit tests for configuration and data classes
- Integration tests for peer connections (when platform implementations are ready)
- Mock implementations for testing

## Future Enhancements

1. **Platform Implementations**: Complete Android and Desktop IPv8Manager implementations
2. **Custom Communities**: Create TheNet-specific communities for social features
3. **Performance Optimization**: Tune for mobile battery efficiency
4. **Security Hardening**: Add additional encryption layers
5. **Analytics**: Network health monitoring and metrics

## Dependencies

- Kotlin IPv8 (via Git submodule)
- Kotlin Coroutines for async operations
- Kotlinx Serialization for configuration
- Platform-specific networking libraries