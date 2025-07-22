package com.bigshotsoftware.thenet.p2p.discovery

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Peer discovery service that integrates Kademlia DHT with IPv8 for enhanced peer discovery.
 * 
 * This service provides:
 * - DHT-based peer discovery with Kademlia protocol
 * - Integration with IPv8's existing discovery mechanisms
 * - Peer caching and lifecycle management
 * - Discovery events and callbacks
 * - Network partitioning resilience
 */
class PeerDiscoveryService(
    private val dht: KademliaDHT,
    private val config: DiscoveryConfig = DiscoveryConfig()
) {

    /**
     * Configuration for peer discovery service
     */
    data class DiscoveryConfig(
        val discoveryInterval: Long = 30000, // 30 seconds
        val maxPeersToDiscover: Int = 50,
        val peerCacheSize: Int = 200,
        val peerCacheExpiryTime: Long = 3600000, // 1 hour
        val bootstrapRetryInterval: Long = 60000, // 1 minute
        val maxBootstrapRetries: Int = 5,
        val enablePeriodicDiscovery: Boolean = true,
        val enableBootstrapRetry: Boolean = true
    )

    /**
     * Peer discovery events
     */
    sealed class DiscoveryEvent {
        data class PeerDiscovered(val peer: IPv8Manager.PeerInfo) : DiscoveryEvent()
        data class PeerLost(val peerId: String) : DiscoveryEvent()
        data class PeerConnected(val peer: IPv8Manager.PeerInfo) : DiscoveryEvent()
        data class PeerDisconnected(val peerId: String) : DiscoveryEvent()
        data class DiscoveryStarted(val bootstrapNodes: List<IPv8Manager.PeerInfo>) : DiscoveryEvent()
        data class DiscoveryStopped(val reason: String) : DiscoveryEvent()
        data class DiscoveryError(val error: String, val cause: Throwable?) : DiscoveryEvent()
    }

    /**
     * Peer cache entry
     */
    private data class CachedPeer(
        val peer: IPv8Manager.PeerInfo,
        val discoveredAt: Long,
        val lastSeen: Long,
        val connectionAttempts: Int = 0,
        val isBootstrap: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val peerCache = mutableMapOf<String, CachedPeer>()
    private val discoveryCallbacks = mutableListOf<(DiscoveryEvent) -> Unit>()
    
    // Discovery state
    private var isRunning = false
    private var discoveryJob: Job? = null
    private var bootstrapRetryJob: Job? = null
    private var cleanupJob: Job? = null
    
    // State flows for observing discovery state
    private val _discoveredPeers = MutableStateFlow<List<IPv8Manager.PeerInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<IPv8Manager.PeerInfo>> = _discoveredPeers.asStateFlow()
    
    private val _connectedPeers = MutableStateFlow<List<IPv8Manager.PeerInfo>>(emptyList())
    val connectedPeers: StateFlow<List<IPv8Manager.PeerInfo>> = _connectedPeers.asStateFlow()
    
    private val _discoveryStatistics = MutableStateFlow(DiscoveryStatistics())
    val discoveryStatistics: StateFlow<DiscoveryStatistics> = _discoveryStatistics.asStateFlow()

    /**
     * Discovery statistics
     */
    data class DiscoveryStatistics(
        val totalPeersDiscovered: Int = 0,
        val activePeers: Int = 0,
        val cachedPeers: Int = 0,
        val connectionSuccessRate: Float = 0f,
        val averageDiscoveryTime: Long = 0,
        val lastDiscoveryTime: Long = 0
    )

    init {
        // Combine DHT discovered nodes with our peer list
        scope.launch {
            dht.discoveredNodes.collect { dhtNodes ->
                updatePeersFromDHT(dhtNodes)
            }
        }
    }

    /**
     * Start peer discovery service
     */
    suspend fun start(bootstrapPeers: List<IPv8Manager.PeerInfo> = emptyList()) {
        if (isRunning) return
        
        isRunning = true
        
        try {
            // Start DHT
            dht.start(dht.config)
            
            // Add bootstrap peers to cache and DHT
            for (peer in bootstrapPeers) {
                addBootstrapPeer(peer)
            }
            
            // Bootstrap DHT with initial peers
            if (bootstrapPeers.isNotEmpty()) {
                val dhtNodes = bootstrapPeers.map { peer ->
                    KademliaDHT.DHTNode(
                        nodeId = peer.peerId.toByteArray(), // Convert peerId to byte array
                        address = peer.address,
                        port = peer.port,
                        lastSeen = peer.lastSeen
                    )
                }
                dht.bootstrap(dhtNodes)
            }
            
            // Start periodic discovery
            if (config.enablePeriodicDiscovery) {
                startPeriodicDiscovery()
            }
            
            // Start bootstrap retry mechanism
            if (config.enableBootstrapRetry) {
                startBootstrapRetry()
            }
            
            // Start peer cache cleanup
            startCacheCleanup()
            
            // Register DHT message handler for peer discovery
            dht.registerMessageHandler { message ->
                handleDHTMessage(message)
            }
            
            emitEvent(DiscoveryEvent.DiscoveryStarted(bootstrapPeers))
            
        } catch (e: Exception) {
            isRunning = false
            emitEvent(DiscoveryEvent.DiscoveryError("Failed to start discovery service", e))
            throw e
        }
    }

    /**
     * Stop peer discovery service
     */
    suspend fun stop() {
        if (!isRunning) return
        
        isRunning = false
        
        // Cancel all jobs
        discoveryJob?.cancel()
        bootstrapRetryJob?.cancel()
        cleanupJob?.cancel()
        
        // Stop DHT
        dht.stop()
        
        // Clear cache
        synchronized(peerCache) {
            peerCache.clear()
        }
        
        emitEvent(DiscoveryEvent.DiscoveryStopped("Service stopped by user"))
    }

    /**
     * Manually trigger peer discovery
     */
    suspend fun discoverPeers(): List<IPv8Manager.PeerInfo> {
        if (!isRunning) return emptyList()
        
        val startTime = System.currentTimeMillis()
        val discoveredPeers = mutableListOf<IPv8Manager.PeerInfo>()
        
        try {
            // Use DHT to find random peers
            val randomKey = KademliaDHT.KademliaConfig.generateRandomNodeId()
            val lookupResult = dht.findNode(randomKey)
            
            // Convert DHT nodes to PeerInfo
            for (dhtNode in lookupResult.nodes) {
                val peer = IPv8Manager.PeerInfo(
                    peerId = dhtNode.nodeId.toHexString(),
                    address = dhtNode.address,
                    port = dhtNode.port,
                    isConnected = false,
                    lastSeen = dhtNode.lastSeen
                )
                
                addDiscoveredPeer(peer)
                discoveredPeers.add(peer)
            }
            
            // Update statistics
            updateDiscoveryStatistics(startTime, discoveredPeers.size)
            
        } catch (e: Exception) {
            emitEvent(DiscoveryEvent.DiscoveryError("Discovery failed", e))
        }
        
        return discoveredPeers
    }

    /**
     * Add a peer that was discovered through other means
     */
    fun addDiscoveredPeer(peer: IPv8Manager.PeerInfo) {
        synchronized(peerCache) {
            val existing = peerCache[peer.peerId]
            val cachedPeer = CachedPeer(
                peer = peer,
                discoveredAt = existing?.discoveredAt ?: System.currentTimeMillis(),
                lastSeen = peer.lastSeen,
                connectionAttempts = existing?.connectionAttempts ?: 0
            )
            
            peerCache[peer.peerId] = cachedPeer
            
            // Add to DHT if not already present
            val dhtNode = KademliaDHT.DHTNode(
                nodeId = peer.peerId.toByteArray(),
                address = peer.address,
                port = peer.port,
                lastSeen = peer.lastSeen
            )
            dht.addNode(dhtNode)
        }
        
        updatePeerLists()
        emitEvent(DiscoveryEvent.PeerDiscovered(peer))
    }

    /**
     * Mark a peer as connected
     */
    fun markPeerConnected(peerId: String) {
        synchronized(peerCache) {
            peerCache[peerId]?.let { cached ->
                val updatedPeer = cached.peer.copy(isConnected = true)
                peerCache[peerId] = cached.copy(peer = updatedPeer)
                
                updatePeerLists()
                emitEvent(DiscoveryEvent.PeerConnected(updatedPeer))
            }
        }
    }

    /**
     * Mark a peer as disconnected
     */
    fun markPeerDisconnected(peerId: String) {
        synchronized(peerCache) {
            peerCache[peerId]?.let { cached ->
                val updatedPeer = cached.peer.copy(isConnected = false)
                peerCache[peerId] = cached.copy(peer = updatedPeer)
                
                updatePeerLists()
                emitEvent(DiscoveryEvent.PeerDisconnected(peerId))
            }
        }
    }

    /**
     * Remove a peer from discovery cache
     */
    fun removePeer(peerId: String) {
        synchronized(peerCache) {
            peerCache.remove(peerId)?.let {
                dht.removeNode(peerId.toByteArray())
                updatePeerLists()
                emitEvent(DiscoveryEvent.PeerLost(peerId))
            }
        }
    }

    /**
     * Get all cached peers
     */
    fun getCachedPeers(): List<IPv8Manager.PeerInfo> {
        return synchronized(peerCache) {
            peerCache.values.map { it.peer }
        }
    }

    /**
     * Register a callback for discovery events
     */
    fun registerDiscoveryCallback(callback: (DiscoveryEvent) -> Unit) {
        discoveryCallbacks.add(callback)
    }

    /**
     * Unregister a discovery callback
     */
    fun unregisterDiscoveryCallback(callback: (DiscoveryEvent) -> Unit) {
        discoveryCallbacks.remove(callback)
    }

    private fun addBootstrapPeer(peer: IPv8Manager.PeerInfo) {
        synchronized(peerCache) {
            val cachedPeer = CachedPeer(
                peer = peer,
                discoveredAt = System.currentTimeMillis(),
                lastSeen = peer.lastSeen,
                isBootstrap = true
            )
            peerCache[peer.peerId] = cachedPeer
        }
    }

    private fun startPeriodicDiscovery() {
        discoveryJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    discoverPeers()
                    delay(config.discoveryInterval)
                } catch (e: Exception) {
                    emitEvent(DiscoveryEvent.DiscoveryError("Periodic discovery failed", e))
                    delay(config.discoveryInterval * 2) // Back off on error
                }
            }
        }
    }

    private fun startBootstrapRetry() {
        bootstrapRetryJob = scope.launch {
            var retryCount = 0
            
            while (isActive && isRunning && retryCount < config.maxBootstrapRetries) {
                delay(config.bootstrapRetryInterval)
                
                // Check if we have sufficient peers
                val currentPeerCount = synchronized(peerCache) { peerCache.size }
                
                if (currentPeerCount < 5) { // Minimum viable peer count
                    try {
                        // Retry with bootstrap peers
                        val bootstrapPeers = synchronized(peerCache) {
                            peerCache.values.filter { it.isBootstrap }.map { it.peer }
                        }
                        
                        if (bootstrapPeers.isNotEmpty()) {
                            val dhtNodes = bootstrapPeers.map { peer ->
                                KademliaDHT.DHTNode(
                                    nodeId = peer.peerId.toByteArray(),
                                    address = peer.address,
                                    port = peer.port,
                                    lastSeen = peer.lastSeen
                                )
                            }
                            dht.bootstrap(dhtNodes)
                        }
                        
                        retryCount++
                    } catch (e: Exception) {
                        emitEvent(DiscoveryEvent.DiscoveryError("Bootstrap retry failed", e))
                        retryCount++
                    }
                } else {
                    // We have enough peers, stop retrying
                    break
                }
            }
        }
    }

    private fun startCacheCleanup() {
        cleanupJob = scope.launch {
            while (isActive && isRunning) {
                delay(config.peerCacheExpiryTime / 4) // Check every quarter of expiry time
                
                try {
                    cleanupExpiredPeers()
                } catch (e: Exception) {
                    // Continue cleanup on next cycle
                }
            }
        }
    }

    private fun cleanupExpiredPeers() {
        val currentTime = System.currentTimeMillis()
        val expiredPeers = mutableListOf<String>()
        
        synchronized(peerCache) {
            // Remove expired non-bootstrap peers
            val iterator = peerCache.iterator()
            while (iterator.hasNext()) {
                val (peerId, cached) = iterator.next()
                
                val isExpired = (currentTime - cached.lastSeen) > config.peerCacheExpiryTime
                val isOverCapacity = peerCache.size > config.peerCacheSize
                
                if ((isExpired || isOverCapacity) && !cached.isBootstrap && !cached.peer.isConnected) {
                    iterator.remove()
                    expiredPeers.add(peerId)
                }
            }
        }
        
        // Remove from DHT and emit events
        for (peerId in expiredPeers) {
            dht.removeNode(peerId.toByteArray())
            emitEvent(DiscoveryEvent.PeerLost(peerId))
        }
        
        if (expiredPeers.isNotEmpty()) {
            updatePeerLists()
        }
    }

    private suspend fun updatePeersFromDHT(dhtNodes: List<KademliaDHT.DHTNode>) {
        for (dhtNode in dhtNodes) {
            val peer = IPv8Manager.PeerInfo(
                peerId = dhtNode.nodeId.toHexString(),
                address = dhtNode.address,
                port = dhtNode.port,
                isConnected = false,
                lastSeen = dhtNode.lastSeen
            )
            
            // Only add if not already in cache
            synchronized(peerCache) {
                if (!peerCache.containsKey(peer.peerId)) {
                    addDiscoveredPeer(peer)
                }
            }
        }
    }

    private fun updatePeerLists() {
        val allPeers = synchronized(peerCache) {
            peerCache.values.map { it.peer }.sortedByDescending { it.lastSeen }
        }
        
        _discoveredPeers.value = allPeers
        _connectedPeers.value = allPeers.filter { it.isConnected }
        
        updateStatistics()
    }

    private fun updateStatistics() {
        val cachedPeers = synchronized(peerCache) { peerCache.values.toList() }
        val totalPeers = cachedPeers.size
        val activePeers = cachedPeers.count { it.peer.isConnected }
        val totalAttempts = cachedPeers.sumOf { it.connectionAttempts }
        val successfulConnections = cachedPeers.count { it.peer.isConnected }
        
        val successRate = if (totalAttempts > 0) {
            successfulConnections.toFloat() / totalAttempts.toFloat()
        } else {
            0f
        }
        
        _discoveryStatistics.value = DiscoveryStatistics(
            totalPeersDiscovered = totalPeers,
            activePeers = activePeers,
            cachedPeers = totalPeers,
            connectionSuccessRate = successRate,
            lastDiscoveryTime = System.currentTimeMillis()
        )
    }

    private fun updateDiscoveryStatistics(startTime: Long, peersFound: Int) {
        val discoveryTime = System.currentTimeMillis() - startTime
        val current = _discoveryStatistics.value
        
        _discoveryStatistics.value = current.copy(
            averageDiscoveryTime = (current.averageDiscoveryTime + discoveryTime) / 2,
            lastDiscoveryTime = System.currentTimeMillis()
        )
    }

    private fun handleDHTMessage(message: KademliaDHT.DHTMessage) {
        // Extract peer information from DHT messages
        val sourcePeer = IPv8Manager.PeerInfo(
            peerId = message.sourceNodeId.toHexString(),
            address = "unknown", // Would be filled from network layer
            port = 0,
            isConnected = false,
            lastSeen = System.currentTimeMillis()
        )
        
        // Add peer if it's new
        synchronized(peerCache) {
            if (!peerCache.containsKey(sourcePeer.peerId)) {
                addDiscoveredPeer(sourcePeer)
            }
        }
    }

    private fun emitEvent(event: DiscoveryEvent) {
        discoveryCallbacks.forEach { callback ->
            try {
                callback(event)
            } catch (e: Exception) {
                // Continue with other callbacks if one fails
            }
        }
    }
}

// Extension function to convert ByteArray to hex string
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

// Extension function to convert String to ByteArray
private fun String.toByteArray(): ByteArray = this.toByteArray(Charsets.UTF_8)