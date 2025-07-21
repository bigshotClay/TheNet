package com.bigshotsoftware.thenet.p2p

import kotlinx.coroutines.flow.StateFlow

/**
 * Main interface for IPv8 P2P networking functionality in TheNet.
 *
 * This class manages the IPv8 peer-to-peer networking stack, including:
 * - Peer discovery and connection management
 * - Community management (DiscoveryCommunity, TrustChainCommunity)
 * - Message routing and handling
 * - Network configuration and lifecycle management
 */
interface IPv8Manager {

    /**
     * Configuration for IPv8 networking
     */
    data class IPv8Config(
        val port: Int = 8090,
        val bootstrapPeers: List<String> = emptyList(),
        val enableDiscovery: Boolean = true,
        val enableTrustChain: Boolean = true,
        val workingDirectory: String? = null,
    )

    /**
     * Current status of the IPv8 networking layer
     */
    enum class NetworkStatus {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR,
    }

    /**
     * Information about a discovered peer
     */
    data class PeerInfo(
        val peerId: String,
        val address: String,
        val port: Int,
        val isConnected: Boolean,
        val lastSeen: Long,
    )

    /**
     * Current network status
     */
    val networkStatus: StateFlow<NetworkStatus>

    /**
     * List of discovered peers
     */
    val discoveredPeers: StateFlow<List<PeerInfo>>

    /**
     * Number of active connections
     */
    val connectionCount: StateFlow<Int>

    /**
     * Start the IPv8 networking with the given configuration
     */
    suspend fun start(config: IPv8Config)

    /**
     * Stop the IPv8 networking
     */
    suspend fun stop()

    /**
     * Send a message to a specific peer
     */
    suspend fun sendMessage(peerId: String, message: ByteArray): Boolean

    /**
     * Broadcast a message to all connected peers
     */
    suspend fun broadcastMessage(message: ByteArray): Int

    /**
     * Register a message handler for specific message types
     */
    fun registerMessageHandler(messageType: String, handler: (peerId: String, message: ByteArray) -> Unit)

    /**
     * Unregister a message handler
     */
    fun unregisterMessageHandler(messageType: String)
}
