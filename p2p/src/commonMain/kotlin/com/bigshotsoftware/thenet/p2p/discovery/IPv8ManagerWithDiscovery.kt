package com.bigshotsoftware.thenet.p2p.discovery

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import com.bigshotsoftware.thenet.p2p.impl.BaseIPv8Manager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Enhanced IPv8Manager that integrates Kademlia DHT-based peer discovery.
 * 
 * This implementation extends the base IPv8Manager with:
 * - Kademlia DHT for distributed peer discovery
 * - Enhanced peer routing and caching
 * - Discovery events and callbacks
 * - Improved network resilience
 */
class IPv8ManagerWithDiscovery(
    private val baseManager: BaseIPv8Manager,
    private val discoveryConfig: PeerDiscoveryService.DiscoveryConfig = PeerDiscoveryService.DiscoveryConfig()
) : IPv8Manager {

    // DHT and discovery service
    private val dht = KademliaDHTImpl(
        KademliaDHT.KademliaConfig(
            k = 20,
            alpha = 3,
            bucketRefreshInterval = 3600000, // 1 hour
            republishInterval = 3600000, // 1 hour
            expireInterval = 86400000, // 24 hours
            pingTimeout = 5000, // 5 seconds
            maxRetries = 3
        )
    )
    
    private val discoveryService = PeerDiscoveryService(dht, discoveryConfig)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Combined peer list from base manager and discovery service
    override val discoveredPeers: StateFlow<List<IPv8Manager.PeerInfo>> = 
        combine(
            baseManager.discoveredPeers,
            discoveryService.discoveredPeers
        ) { basePeers, discoveryPeers ->
            // Merge and deduplicate peers
            val allPeers = mutableMapOf<String, IPv8Manager.PeerInfo>()
            
            // Add base peers first
            basePeers.forEach { peer ->
                allPeers[peer.peerId] = peer
            }
            
            // Add discovery peers, updating existing entries with more recent info
            discoveryPeers.forEach { peer ->
                val existing = allPeers[peer.peerId]
                if (existing == null || peer.lastSeen > existing.lastSeen) {
                    allPeers[peer.peerId] = peer
                }
            }
            
            allPeers.values.toList().sortedByDescending { it.lastSeen }
        }.stateIn(scope, SharingStarted.Lazily, emptyList())

    // Delegate basic functionality to base manager
    override val networkStatus: StateFlow<IPv8Manager.NetworkStatus> = baseManager.networkStatus
    override val connectionCount: StateFlow<Int> = baseManager.connectionCount

    /**
     * Enhanced configuration that includes DHT and discovery settings
     */
    data class EnhancedIPv8Config(
        val baseConfig: IPv8Manager.IPv8Config,
        val dhtConfig: KademliaDHT.KademliaConfig = KademliaDHT.KademliaConfig(),
        val discoveryConfig: PeerDiscoveryService.DiscoveryConfig = PeerDiscoveryService.DiscoveryConfig(),
        val enableDHTDiscovery: Boolean = true,
        val bootstrapDHTNodes: List<IPv8Manager.PeerInfo> = emptyList()
    )

    override suspend fun start(config: IPv8Manager.IPv8Config) {
        // Start base IPv8 manager
        baseManager.start(config)
        
        // If config is enhanced, start discovery service
        if (config is EnhancedIPv8Config && config.enableDHTDiscovery) {
            discoveryService.start(config.bootstrapDHTNodes)
            
            // Set up discovery event handling
            setupDiscoveryEventHandling()
        }
    }

    override suspend fun stop() {
        // Stop discovery service first
        discoveryService.stop()
        
        // Stop base manager
        baseManager.stop()
    }

    override suspend fun sendMessage(peerId: String, message: ByteArray): Boolean {
        return baseManager.sendMessage(peerId, message)
    }

    override suspend fun broadcastMessage(message: ByteArray): Int {
        return baseManager.broadcastMessage(message)
    }

    override fun registerMessageHandler(messageType: String, handler: (peerId: String, message: ByteArray) -> Unit) {
        baseManager.registerMessageHandler(messageType, handler)
    }

    override fun unregisterMessageHandler(messageType: String) {
        baseManager.unregisterMessageHandler(messageType)
    }

    /**
     * Get discovery service for advanced discovery operations
     */
    fun getDiscoveryService(): PeerDiscoveryService = discoveryService

    /**
     * Get DHT instance for direct DHT operations
     */
    fun getDHT(): KademliaDHT = dht

    /**
     * Manually trigger peer discovery
     */
    suspend fun discoverPeers(): List<IPv8Manager.PeerInfo> {
        return discoveryService.discoverPeers()
    }

    /**
     * Get discovery statistics
     */
    fun getDiscoveryStatistics(): StateFlow<PeerDiscoveryService.DiscoveryStatistics> {
        return discoveryService.discoveryStatistics
    }

    /**
     * Get connected peers from discovery service
     */
    fun getConnectedPeers(): StateFlow<List<IPv8Manager.PeerInfo>> {
        return discoveryService.connectedPeers
    }

    /**
     * Register callback for discovery events
     */
    fun registerDiscoveryCallback(callback: (PeerDiscoveryService.DiscoveryEvent) -> Unit) {
        discoveryService.registerDiscoveryCallback(callback)
    }

    /**
     * Store data in the DHT
     */
    suspend fun storeDHTValue(key: ByteArray, value: ByteArray): Boolean {
        return dht.store(key, value)
    }

    /**
     * Find value in the DHT
     */
    suspend fun findDHTValue(key: ByteArray): KademliaDHT.LookupResult {
        return dht.findValue(key)
    }

    /**
     * Find nodes close to a specific key
     */
    suspend fun findNodesNear(key: ByteArray): KademliaDHT.LookupResult {
        return dht.findNode(key)
    }

    private fun setupDiscoveryEventHandling() {
        discoveryService.registerDiscoveryCallback { event ->
            scope.launch {
                handleDiscoveryEvent(event)
            }
        }
    }

    private suspend fun handleDiscoveryEvent(event: PeerDiscoveryService.DiscoveryEvent) {
        when (event) {
            is PeerDiscoveryService.DiscoveryEvent.PeerDiscovered -> {
                // Attempt to connect to newly discovered peer through base manager
                // This would trigger actual network connection
                // For now, we'll just mark it as available
            }
            
            is PeerDiscoveryService.DiscoveryEvent.PeerConnected -> {
                // Update base manager about successful connection
                // This would sync the connection state
            }
            
            is PeerDiscoveryService.DiscoveryEvent.PeerDisconnected -> {
                // Update base manager about disconnection
                // This would trigger reconnection attempts if needed
            }
            
            is PeerDiscoveryService.DiscoveryEvent.PeerLost -> {
                // Remove peer from base manager if it's completely lost
            }
            
            is PeerDiscoveryService.DiscoveryEvent.DiscoveryError -> {
                // Log discovery errors and potentially trigger fallback mechanisms
                // Could also emit error events to application layer
            }
            
            is PeerDiscoveryService.DiscoveryEvent.DiscoveryStarted -> {
                // Discovery service started successfully
            }
            
            is PeerDiscoveryService.DiscoveryEvent.DiscoveryStopped -> {
                // Discovery service stopped
            }
        }
    }
}

/**
 * Factory function to create IPv8ManagerWithDiscovery
 */
fun createIPv8ManagerWithDiscovery(
    baseManager: BaseIPv8Manager,
    discoveryConfig: PeerDiscoveryService.DiscoveryConfig = PeerDiscoveryService.DiscoveryConfig()
): IPv8ManagerWithDiscovery {
    return IPv8ManagerWithDiscovery(baseManager, discoveryConfig)
}

/**
 * Extension function to convert IPv8Config to EnhancedIPv8Config
 */
fun IPv8Manager.IPv8Config.withDiscovery(
    dhtConfig: KademliaDHT.KademliaConfig = KademliaDHT.KademliaConfig(),
    discoveryConfig: PeerDiscoveryService.DiscoveryConfig = PeerDiscoveryService.DiscoveryConfig(),
    bootstrapDHTNodes: List<IPv8Manager.PeerInfo> = emptyList()
): IPv8ManagerWithDiscovery.EnhancedIPv8Config {
    return IPv8ManagerWithDiscovery.EnhancedIPv8Config(
        baseConfig = this,
        dhtConfig = dhtConfig,
        discoveryConfig = discoveryConfig,
        enableDHTDiscovery = true,
        bootstrapDHTNodes = bootstrapDHTNodes
    )
}