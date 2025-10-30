package com.bigshotsoftware.thenet.p2p.config

import com.bigshotsoftware.thenet.p2p.nat.*
import kotlinx.serialization.Serializable

/**
 * Configuration for the IPv8 P2P networking layer in TheNet
 */
@Serializable
data class IPv8Configuration(
    /**
     * Unique identifier for this peer
     */
    val peerId: String = "default-peer",

    /**
     * Port number for IPv8 communication
     */
    val port: Int = 8090,

    /**
     * List of bootstrap peer addresses for initial network discovery
     */
    val bootstrapPeers: List<BootstrapPeer> = defaultBootstrapPeers,

    /**
     * Enable the discovery community for peer discovery
     */
    val enableDiscoveryCommunity: Boolean = true,

    /**
     * Enable the TrustChain community for blockchain functionality
     */
    val enableTrustChainCommunity: Boolean = true,

    /**
     * Maximum number of peers to connect to
     */
    val maxPeers: Int = 50,

    /**
     * Peer discovery interval in milliseconds
     */
    val peerDiscoveryInterval: Long = 5000,

    /**
     * Timeout for peer responses in milliseconds
     */
    val peerResponseTimeout: Long = 10000,

    /**
     * Enable NAT puncturing for better connectivity
     */
    val enableNatPuncturing: Boolean = true,

    /**
     * NAT traversal configuration
     */
    val natTraversal: NATTraversalConfiguration = NATTraversalConfiguration(),

    /**
     * Directory for storing IPv8 data (keys, database, etc.)
     */
    val dataDirectory: String? = null,

    /**
     * Enable debug logging
     */
    val enableDebugLogging: Boolean = false,
)

/**
 * Bootstrap peer information
 */
@Serializable
data class BootstrapPeer(
    val host: String,
    val port: Int,
)

/**
 * Default bootstrap peers for TheNet network
 */
val defaultBootstrapPeers = listOf(
    // These will be actual TheNet bootstrap nodes in production
    // For now, using placeholder addresses
    BootstrapPeer("bootstrap1.thenet.app", 8090),
    BootstrapPeer("bootstrap2.thenet.app", 8090),
    BootstrapPeer("bootstrap3.thenet.app", 8090),
)

/**
 * Configuration for individual communities
 */
@Serializable
data class CommunityConfiguration(
    /**
     * Community identifier
     */
    val communityId: String,

    /**
     * Enable this community
     */
    val enabled: Boolean = true,

    /**
     * Community-specific settings as key-value pairs
     */
    val settings: Map<String, String> = emptyMap(),
)

/**
 * TrustChain specific configuration
 */
@Serializable
data class TrustChainConfiguration(
    /**
     * Maximum number of blocks to store locally
     */
    val maxLocalBlocks: Int = 10000,

    /**
     * Enable automatic block validation
     */
    val autoValidateBlocks: Boolean = true,

    /**
     * Block creation rate limit (minimum milliseconds between blocks)
     */
    val blockCreationRateLimit: Long = 1000,

    /**
     * Database file path for TrustChain storage
     */
    val databasePath: String? = null,
)

/**
 * NAT traversal configuration for enhanced peer connectivity
 */
@Serializable
data class NATTraversalConfiguration(
    /**
     * NAT traversal mode
     */
    val mode: NATTraversalMode = NATTraversalMode.AUTO,
    
    /**
     * STUN servers for NAT discovery
     */
    val stunServers: List<STUNServer> = defaultSTUNServers,
    
    /**
     * TURN servers for relay fallback
     */
    val turnServers: List<TURNServer> = defaultTURNServers,
    
    /**
     * Enable port prediction for symmetric NAT traversal
     */
    val enablePortPrediction: Boolean = true,
    
    /**
     * Maximum number of puncturing attempts before falling back to relay
     */
    val maxPuncturingAttempts: Int = 5,
    
    /**
     * Enable TURN relay fallback when hole punching fails
     */
    val enableRelayFallback: Boolean = true,
    
    /**
     * Timeout for individual STUN requests in milliseconds
     */
    val stunTimeoutMs: Long = 5000,
    
    /**
     * Timeout for TURN allocation requests in milliseconds
     */
    val turnTimeoutMs: Long = 10000,
    
    /**
     * Enable NAT type detection and caching
     */
    val enableNATDetection: Boolean = true,
    
    /**
     * Interval for NAT type re-detection in milliseconds (0 = disabled)
     */
    val natDetectionInterval: Long = 300000, // 5 minutes
    
    /**
     * Enable connectivity checks before establishing connections
     */
    val enableConnectivityChecks: Boolean = true,
    
    /**
     * Maximum bandwidth allocation for TURN relays in bytes/second (0 = unlimited)
     */
    val maxRelayBandwidth: Long = 0
)
