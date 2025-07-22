package com.bigshotsoftware.thenet.p2p.discovery

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

/**
 * Advanced peer information caching system with persistence, TTL, and intelligent eviction.
 * 
 * Features:
 * - Multi-tier caching (memory, disk, network)
 * - LRU and LFU eviction policies
 * - Peer reputation tracking
 * - Connection history analysis
 * - Automatic cache warming
 * - Cross-session persistence
 */
class PeerCache(
    private val config: PeerCacheConfig = PeerCacheConfig()
) {

    /**
     * Configuration for peer cache
     */
    @Serializable
    data class PeerCacheConfig(
        val maxMemoryCacheSize: Int = 500,
        val maxDiskCacheSize: Int = 2000,
        val defaultTTL: Long = 86400000, // 24 hours
        val highPriorityTTL: Long = 604800000, // 7 days
        val cleanupInterval: Long = 3600000, // 1 hour
        val persistenceEnabled: Boolean = true,
        val compressionEnabled: Boolean = true,
        val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU_WITH_REPUTATION,
        val reputationDecayRate: Double = 0.1,
        val connectionHistorySize: Int = 10,
        val autoWarmingEnabled: Boolean = true
    )

    /**
     * Cache eviction policies
     */
    enum class EvictionPolicy {
        LRU,           // Least Recently Used
        LFU,           // Least Frequently Used
        TTL_BASED,     // Time To Live based
        REPUTATION,    // Reputation based
        LRU_WITH_REPUTATION, // Hybrid LRU + reputation
        NETWORK_DISTANCE     // Network distance based
    }

    /**
     * Peer priority levels
     */
    enum class PeerPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    /**
     * Serializable peer info for caching
     */
    @Serializable
    data class SerializablePeerInfo(
        val peerId: String,
        val address: String,
        val port: Int,
        val isConnected: Boolean,
        val lastSeen: Long
    ) {
        fun toIPv8PeerInfo(): IPv8Manager.PeerInfo {
            return IPv8Manager.PeerInfo(peerId, address, port, isConnected, lastSeen)
        }
        
        companion object {
            fun fromIPv8PeerInfo(peer: IPv8Manager.PeerInfo): SerializablePeerInfo {
                return SerializablePeerInfo(peer.peerId, peer.address, peer.port, peer.isConnected, peer.lastSeen)
            }
        }
    }

    /**
     * Enhanced cached peer information
     */
    @Serializable
    data class CachedPeerInfo(
        val peer: SerializablePeerInfo,
        val cachedAt: Long = System.currentTimeMillis(),
        val lastAccessed: Long = System.currentTimeMillis(),
        val accessCount: Int = 1,
        val ttl: Long = 86400000, // Default TTL
        val priority: PeerPriority = PeerPriority.NORMAL,
        val reputation: Double = 0.5, // 0.0 to 1.0
        val isBootstrap: Boolean = false,
        val connectionHistory: List<ConnectionAttempt> = emptyList(),
        val networkDistance: Int = Int.MAX_VALUE, // DHT distance
        val tags: Set<String> = emptySet(),
        val metadata: Map<String, String> = emptyMap()
    ) {
        /**
         * Check if peer is expired
         */
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - cachedAt > ttl
        }

        /**
         * Calculate effective score for eviction decisions
         */
        fun calculateScore(): Double {
            val currentTime = System.currentTimeMillis()
            val age = (currentTime - cachedAt).toDouble()
            val recency = (currentTime - lastAccessed).toDouble()
            
            // Normalize values
            val normalizedAge = min(age / ttl, 1.0)
            val normalizedRecency = min(recency / 86400000.0, 1.0) // 24h max
            val normalizedFrequency = min(accessCount.toDouble() / 100.0, 1.0)
            
            // Calculate composite score
            val reputationWeight = 0.3
            val frequencyWeight = 0.25
            val recencyWeight = 0.25
            val ageWeight = 0.2
            
            return (reputation * reputationWeight) +
                   (normalizedFrequency * frequencyWeight) +
                   ((1.0 - normalizedRecency) * recencyWeight) +
                   ((1.0 - normalizedAge) * ageWeight)
        }
    }

    /**
     * Connection attempt record
     */
    @Serializable
    data class ConnectionAttempt(
        val timestamp: Long,
        val success: Boolean,
        val latency: Long = 0,
        val errorMessage: String? = null,
        val connectionMethod: String = "unknown"
    )

    /**
     * Cache statistics
     */
    data class CacheStatistics(
        val memorySize: Int = 0,
        val diskSize: Int = 0,
        val hitRate: Float = 0f,
        val missRate: Float = 0f,
        val evictionCount: Long = 0,
        val persistenceOperations: Long = 0,
        val averageRetrievalTime: Long = 0,
        val reputationDistribution: Map<PeerPriority, Int> = emptyMap()
    )

    // Cache storage
    private val memoryCache = mutableMapOf<String, CachedPeerInfo>()
    private val diskCache = mutableMapOf<String, CachedPeerInfo>() // Simulated disk cache
    private val mutex = Mutex()
    
    // Cache metrics
    private var hitCount = 0L
    private var missCount = 0L
    private var evictionCount = 0L
    private var persistenceOperations = 0L
    private val retrievalTimes = mutableListOf<Long>()
    
    // State flows
    private val _cacheStatistics = MutableStateFlow(CacheStatistics())
    val cacheStatistics: StateFlow<CacheStatistics> = _cacheStatistics.asStateFlow()
    
    private val _cachedPeers = MutableStateFlow<List<CachedPeerInfo>>(emptyList())
    val cachedPeers: StateFlow<List<CachedPeerInfo>> = _cachedPeers.asStateFlow()
    
    // Background tasks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cleanupJob: Job? = null
    private var reputationDecayJob: Job? = null
    
    init {
        startBackgroundTasks()
    }

    /**
     * Store a peer in the cache
     */
    suspend fun put(
        peer: IPv8Manager.PeerInfo,
        priority: PeerPriority = PeerPriority.NORMAL,
        ttl: Long? = null,
        tags: Set<String> = emptySet(),
        metadata: Map<String, String> = emptyMap()
    ) = mutex.withLock {
        val effectiveTTL = ttl ?: when (priority) {
            PeerPriority.CRITICAL, PeerPriority.HIGH -> config.highPriorityTTL
            else -> config.defaultTTL
        }
        
        val existing = memoryCache[peer.peerId]
        val serializablePeer = SerializablePeerInfo.fromIPv8PeerInfo(peer)
        val cachedPeer = CachedPeerInfo(
            peer = serializablePeer,
            cachedAt = existing?.cachedAt ?: System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            accessCount = (existing?.accessCount ?: 0) + 1,
            ttl = effectiveTTL,
            priority = priority,
            reputation = existing?.reputation ?: 0.5,
            isBootstrap = existing?.isBootstrap ?: false,
            connectionHistory = existing?.connectionHistory ?: emptyList(),
            networkDistance = existing?.networkDistance ?: Int.MAX_VALUE,
            tags = tags,
            metadata = metadata
        )
        
        // Store in memory cache
        memoryCache[peer.peerId] = cachedPeer
        
        // Evict if necessary
        evictIfNecessary()
        
        // Persist to disk if enabled
        if (config.persistenceEnabled) {
            persistToDisk(cachedPeer)
        }
        
        updateStatistics()
        updateCachedPeersList()
    }

    /**
     * Retrieve a peer from the cache
     */
    suspend fun get(peerId: String): CachedPeerInfo? {
        val startTime = System.currentTimeMillis()
        
        return mutex.withLock {
            // Check memory cache first
            var cachedPeer = memoryCache[peerId]
            
            if (cachedPeer != null) {
                hitCount++
                
                // Check if expired
                if (cachedPeer.isExpired()) {
                    memoryCache.remove(peerId)
                    diskCache.remove(peerId)
                    missCount++
                    null
                } else {
                    // Update access information
                    val updatedPeer = cachedPeer.copy(
                        lastAccessed = System.currentTimeMillis(),
                        accessCount = cachedPeer.accessCount + 1
                    )
                    memoryCache[peerId] = updatedPeer
                    updatedPeer
                }
            } else {
                // Check disk cache
                cachedPeer = diskCache[peerId]
                
                if (cachedPeer != null && !cachedPeer.isExpired()) {
                    hitCount++
                    
                    // Promote to memory cache
                    val updatedPeer = cachedPeer.copy(
                        lastAccessed = System.currentTimeMillis(),
                        accessCount = cachedPeer.accessCount + 1
                    )
                    memoryCache[peerId] = updatedPeer
                    
                    // Evict if necessary
                    evictIfNecessary()
                    
                    updatedPeer
                } else {
                    missCount++
                    null
                }
            }
        }.also {
            val retrievalTime = System.currentTimeMillis() - startTime
            synchronized(retrievalTimes) {
                retrievalTimes.add(retrievalTime)
                if (retrievalTimes.size > 100) {
                    retrievalTimes.removeAt(0)
                }
            }
            updateStatistics()
        }
    }

    /**
     * Update peer reputation
     */
    suspend fun updateReputation(peerId: String, reputationDelta: Double) = mutex.withLock {
        memoryCache[peerId]?.let { cached ->
            val newReputation = max(0.0, min(1.0, cached.reputation + reputationDelta))
            val updated = cached.copy(reputation = newReputation)
            memoryCache[peerId] = updated
            
            if (config.persistenceEnabled) {
                persistToDisk(updated)
            }
        }
        
        updateCachedPeersList()
    }

    /**
     * Record a connection attempt
     */
    suspend fun recordConnectionAttempt(
        peerId: String,
        success: Boolean,
        latency: Long = 0,
        errorMessage: String? = null,
        connectionMethod: String = "unknown"
    ) = mutex.withLock {
        memoryCache[peerId]?.let { cached ->
            val attempt = ConnectionAttempt(
                timestamp = System.currentTimeMillis(),
                success = success,
                latency = latency,
                errorMessage = errorMessage,
                connectionMethod = connectionMethod
            )
            
            val updatedHistory = (cached.connectionHistory + attempt)
                .takeLast(config.connectionHistorySize)
            
            val reputationChange = if (success) 0.1 else -0.1
            val newReputation = max(0.0, min(1.0, cached.reputation + reputationChange))
            
            val updated = cached.copy(
                connectionHistory = updatedHistory,
                reputation = newReputation,
                lastAccessed = System.currentTimeMillis()
            )
            
            memoryCache[peerId] = updated
            
            if (config.persistenceEnabled) {
                persistToDisk(updated)
            }
        }
        
        updateCachedPeersList()
    }

    /**
     * Update network distance for a peer
     */
    suspend fun updateNetworkDistance(peerId: String, distance: Int) = mutex.withLock {
        memoryCache[peerId]?.let { cached ->
            val updated = cached.copy(networkDistance = distance)
            memoryCache[peerId] = updated
            
            if (config.persistenceEnabled) {
                persistToDisk(updated)
            }
        }
        
        updateCachedPeersList()
    }

    /**
     * Get peers by priority
     */
    suspend fun getPeersByPriority(priority: PeerPriority): List<CachedPeerInfo> = mutex.withLock {
        memoryCache.values.filter { it.priority == priority && !it.isExpired() }
    }

    /**
     * Get peers by tags
     */
    suspend fun getPeersByTags(tags: Set<String>): List<CachedPeerInfo> = mutex.withLock {
        memoryCache.values.filter { cached ->
            tags.any { tag -> cached.tags.contains(tag) } && !cached.isExpired()
        }
    }

    /**
     * Get bootstrap peers
     */
    suspend fun getBootstrapPeers(): List<CachedPeerInfo> = mutex.withLock {
        memoryCache.values.filter { it.isBootstrap && !it.isExpired() }
    }

    /**
     * Get peers sorted by reputation
     */
    suspend fun getPeersByReputation(limit: Int = 10): List<CachedPeerInfo> = mutex.withLock {
        memoryCache.values
            .filter { !it.isExpired() }
            .sortedByDescending { it.reputation }
            .take(limit)
    }

    /**
     * Remove a peer from cache
     */
    suspend fun remove(peerId: String) = mutex.withLock {
        memoryCache.remove(peerId)
        diskCache.remove(peerId)
        updateStatistics()
        updateCachedPeersList()
    }

    /**
     * Clear all cache
     */
    suspend fun clear() = mutex.withLock {
        memoryCache.clear()
        diskCache.clear()
        updateStatistics()
        updateCachedPeersList()
    }

    /**
     * Warm cache with high-priority peers
     */
    suspend fun warmCache(peers: List<IPv8Manager.PeerInfo>) {
        if (!config.autoWarmingEnabled) return
        
        for (peer in peers) {
            put(peer, PeerPriority.HIGH)
        }
    }

    /**
     * Export cache for backup
     */
    suspend fun exportCache(): String = mutex.withLock {
        val exportData = mapOf(
            "memory" to memoryCache.values.toList(),
            "disk" to diskCache.values.toList(),
            "timestamp" to System.currentTimeMillis()
        )
        
        Json.encodeToString(
            kotlinx.serialization.serializer<Map<String, Any>>(),
            exportData as Map<String, Any>
        )
    }

    /**
     * Import cache from backup
     */
    suspend fun importCache(data: String) = mutex.withLock {
        try {
            // TODO: Implement proper import logic
            // This would parse the JSON and restore cache state
            updateStatistics()
            updateCachedPeersList()
        } catch (e: Exception) {
            // Handle import error
        }
    }

    private suspend fun evictIfNecessary() {
        if (memoryCache.size <= config.maxMemoryCacheSize) return
        
        val peersToEvict = when (config.evictionPolicy) {
            EvictionPolicy.LRU -> {
                memoryCache.values.sortedBy { it.lastAccessed }
            }
            EvictionPolicy.LFU -> {
                memoryCache.values.sortedBy { it.accessCount }
            }
            EvictionPolicy.TTL_BASED -> {
                memoryCache.values.sortedBy { it.cachedAt + it.ttl }
            }
            EvictionPolicy.REPUTATION -> {
                memoryCache.values.sortedBy { it.reputation }
            }
            EvictionPolicy.LRU_WITH_REPUTATION -> {
                memoryCache.values.sortedBy { it.calculateScore() }
            }
            EvictionPolicy.NETWORK_DISTANCE -> {
                memoryCache.values.sortedByDescending { it.networkDistance }
            }
        }
        
        val evictCount = memoryCache.size - config.maxMemoryCacheSize + 1
        val toEvict = peersToEvict.take(evictCount)
        
        for (peer in toEvict) {
            // Don't evict critical priority peers
            if (peer.priority != PeerPriority.CRITICAL) {
                memoryCache.remove(peer.peer.peerId)
                
                // Move to disk cache if there's space
                if (diskCache.size < config.maxDiskCacheSize) {
                    diskCache[peer.peer.peerId] = peer
                }
                
                evictionCount++
            }
        }
    }

    private suspend fun persistToDisk(cachedPeer: CachedPeerInfo) {
        // In a real implementation, this would write to actual disk storage
        diskCache[cachedPeer.peer.peerId] = cachedPeer
        persistenceOperations++
    }

    private fun startBackgroundTasks() {
        // Cleanup expired peers
        cleanupJob = scope.launch {
            while (isActive) {
                delay(config.cleanupInterval)
                cleanupExpiredPeers()
            }
        }
        
        // Reputation decay
        reputationDecayJob = scope.launch {
            while (isActive) {
                delay(86400000) // Daily
                applyReputationDecay()
            }
        }
    }

    private suspend fun cleanupExpiredPeers() = mutex.withLock {
        val currentTime = System.currentTimeMillis()
        val expiredPeers = mutableListOf<String>()
        
        for ((peerId, cached) in memoryCache) {
            if (cached.isExpired()) {
                expiredPeers.add(peerId)
            }
        }
        
        for (peerId in expiredPeers) {
            memoryCache.remove(peerId)
            diskCache.remove(peerId)
        }
        
        if (expiredPeers.isNotEmpty()) {
            updateStatistics()
            updateCachedPeersList()
        }
    }

    private suspend fun applyReputationDecay() = mutex.withLock {
        for ((peerId, cached) in memoryCache) {
            val decayedReputation = cached.reputation * (1.0 - config.reputationDecayRate)
            val updated = cached.copy(reputation = max(0.0, decayedReputation))
            memoryCache[peerId] = updated
        }
        
        updateCachedPeersList()
    }

    private fun updateStatistics() {
        val totalRequests = hitCount + missCount
        val hitRate = if (totalRequests > 0) hitCount.toFloat() / totalRequests else 0f
        val missRate = if (totalRequests > 0) missCount.toFloat() / totalRequests else 0f
        
        val avgRetrievalTime = synchronized(retrievalTimes) {
            if (retrievalTimes.isNotEmpty()) {
                retrievalTimes.average().toLong()
            } else {
                0L
            }
        }
        
        val reputationDist = memoryCache.values.groupingBy { it.priority }.eachCount()
        
        _cacheStatistics.value = CacheStatistics(
            memorySize = memoryCache.size,
            diskSize = diskCache.size,
            hitRate = hitRate,
            missRate = missRate,
            evictionCount = evictionCount,
            persistenceOperations = persistenceOperations,
            averageRetrievalTime = avgRetrievalTime,
            reputationDistribution = reputationDist
        )
    }

    private fun updateCachedPeersList() {
        _cachedPeers.value = memoryCache.values.toList()
    }

    /**
     * Shutdown cache and cleanup resources
     */
    suspend fun shutdown() {
        cleanupJob?.cancel()
        reputationDecayJob?.cancel()
        scope.cancel()
    }
}