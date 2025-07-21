package com.bigshotsoftware.thenet.p2p.config

import kotlinx.serialization.Serializable

/**
 * Configuration for the IPv8 P2P networking layer in TheNet
 */
@Serializable
data class IPv8Configuration(
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
    val address: String,
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