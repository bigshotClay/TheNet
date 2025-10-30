package com.bigshotsoftware.thenet.p2p.nat

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Comprehensive NAT traversal manager that coordinates all NAT traversal strategies.
 * This is the main entry point for NAT traversal functionality in TheNet.
 */
class NATTraversalManager(
    private val configuration: NATTraversalConfiguration
) {
    private val stunClient: STUNClient = STUNClientFactory.createClient()
    private val turnClient: TURNClient = TURNClientFactory.createClient()
    private val natDetectionService: NATDetectionService = NATDetectionServiceFactory.createService(configuration, stunClient)
    private val puncturingStrategy: AdvancedPuncturingStrategy = AdvancedPuncturingStrategy(configuration, stunClient, turnClient)
    
    private var currentNATBehavior: NATBehavior? = null
    private val activeRelayAllocations = mutableSetOf<RelayAllocation>()
    
    // Event flow for NAT traversal events
    private val _events = MutableSharedFlow<NATTraversalEvent>()
    val events: SharedFlow<NATTraversalEvent> = _events.asSharedFlow()
    
    private var isInitialized = false
    private var backgroundJob: Job? = null
    
    /**
     * Initializes the NAT traversal manager and starts background services.
     */
    suspend fun initialize() {
        if (isInitialized) return
        
        // Perform initial NAT detection
        currentNATBehavior = natDetectionService.detectNATBehavior()
        _events.emit(NATTraversalEvent.NATDetected(currentNATBehavior!!))
        
        // Start background services
        startBackgroundServices()
        
        isInitialized = true
        _events.emit(NATTraversalEvent.ManagerInitialized)
    }
    
    /**
     * Shuts down the NAT traversal manager and cleans up resources.
     */
    suspend fun shutdown() {
        if (!isInitialized) return
        
        backgroundJob?.cancel()
        
        // Release all active relay allocations
        for (allocation in activeRelayAllocations.toList()) {
            turnClient.releaseAllocation(allocation)
            activeRelayAllocations.remove(allocation)
        }
        
        isInitialized = false
        _events.emit(NATTraversalEvent.ManagerShutdown)
    }
    
    /**
     * Establishes connectivity to a peer using the best available NAT traversal method.
     */
    suspend fun establishConnectivity(peer: Peer): ConnectivityEstablishmentResult {
        if (!isInitialized) {
            return ConnectivityEstablishmentResult.failure("NAT traversal manager not initialized")
        }
        
        _events.emit(NATTraversalEvent.ConnectivityAttemptStarted(peer))
        
        val natBehavior = getCurrentNATBehavior()
        
        return try {
            when (configuration.mode) {
                NATTraversalMode.AUTO -> performAutoConnectivity(peer, natBehavior)
                NATTraversalMode.DIRECT_ONLY -> performDirectConnectivity(peer)
                NATTraversalMode.STUN_ASSISTED -> performSTUNAssistedConnectivity(peer)
                NATTraversalMode.TURN_RELAY -> performRelayConnectivity(peer)
                NATTraversalMode.FULL_TRAVERSAL -> performFullTraversal(peer, natBehavior)
            }
        } catch (e: Exception) {
            val result = ConnectivityEstablishmentResult.failure("Connectivity establishment failed: ${e.message}")
            _events.emit(NATTraversalEvent.ConnectivityFailed(peer, e.message ?: "Unknown error"))
            result
        }
    }
    
    /**
     * Tests connectivity to a peer without establishing a persistent connection.
     */
    suspend fun testConnectivity(peer: Peer): ConnectivityResult {
        if (!isInitialized) {
            return ConnectivityResult(
                directConnectivity = false,
                stunAssisted = false,
                relayRequired = true,
                reflexiveAddresses = emptyList(),
                rttMeasurements = emptyMap(),
                errorMessages = listOf("NAT traversal manager not initialized")
            )
        }
        
        return stunClient.performConnectivityChecks(
            peer.address,
            configuration.stunServers.filter { it.enabled }
        )
    }
    
    /**
     * Gets the current NAT behavior, triggering detection if necessary.
     */
    suspend fun getCurrentNATBehavior(forceRefresh: Boolean = false): NATBehavior {
        if (currentNATBehavior == null || forceRefresh || natDetectionService.shouldRedetect()) {
            currentNATBehavior = natDetectionService.detectNATBehavior(forceRefresh)
            _events.emit(NATTraversalEvent.NATDetected(currentNATBehavior!!))
        }
        return currentNATBehavior!!
    }
    
    /**
     * Forces a refresh of NAT detection (e.g., when network changes).
     */
    suspend fun refreshNATDetection() {
        natDetectionService.clearCache()
        currentNATBehavior = natDetectionService.detectNATBehavior(forceRefresh = true)
        _events.emit(NATTraversalEvent.NATDetected(currentNATBehavior!!))
    }
    
    /**
     * Gets statistics about NAT traversal operations.
     */
    fun getStatistics(): NATTraversalStatistics {
        return NATTraversalStatistics(
            currentNATType = currentNATBehavior?.type ?: NATType.UNKNOWN,
            activeRelayAllocations = activeRelayAllocations.size,
            stunServersConfigured = configuration.stunServers.count { it.enabled },
            turnServersConfigured = configuration.turnServers.count { it.enabled }
        )
    }
    
    private suspend fun performAutoConnectivity(peer: Peer, natBehavior: NATBehavior): ConnectivityEstablishmentResult {
        return puncturingStrategy.performMultiStrategyPuncturing(peer, natBehavior).let { result ->
            if (result.success) {
                _events.emit(NATTraversalEvent.ConnectivityEstablished(peer, result.strategy?.name ?: "AUTO"))
                ConnectivityEstablishmentResult.success(result.message, result.strategy, result.relayAllocation)
            } else {
                _events.emit(NATTraversalEvent.ConnectivityFailed(peer, result.message))
                ConnectivityEstablishmentResult.failure(result.message)
            }
        }
    }
    
    private suspend fun performDirectConnectivity(peer: Peer): ConnectivityEstablishmentResult {
        // TODO: Implement direct connectivity attempt
        return ConnectivityEstablishmentResult.failure("Direct connectivity not yet implemented")
    }
    
    private suspend fun performSTUNAssistedConnectivity(peer: Peer): ConnectivityEstablishmentResult {
        val connectivity = testConnectivity(peer)
        
        return if (connectivity.stunAssisted) {
            _events.emit(NATTraversalEvent.ConnectivityEstablished(peer, "STUN_ASSISTED"))
            ConnectivityEstablishmentResult.success("STUN-assisted connectivity established")
        } else {
            _events.emit(NATTraversalEvent.ConnectivityFailed(peer, "STUN-assisted connectivity failed"))
            ConnectivityEstablishmentResult.failure("STUN-assisted connectivity not possible")
        }
    }
    
    private suspend fun performRelayConnectivity(peer: Peer): ConnectivityEstablishmentResult {
        val enabledTurnServers = configuration.turnServers.filter { it.enabled }
        if (enabledTurnServers.isEmpty()) {
            return ConnectivityEstablishmentResult.failure("No TURN servers configured")
        }
        
        for (server in enabledTurnServers) {
            val allocation = turnClient.allocateRelay(server, configuration.turnTimeoutMs)
            if (allocation != null) {
                activeRelayAllocations.add(allocation)
                _events.emit(NATTraversalEvent.ConnectivityEstablished(peer, "TURN_RELAY"))
                return ConnectivityEstablishmentResult.success("TURN relay allocated", relayAllocation = allocation)
            }
        }
        
        _events.emit(NATTraversalEvent.ConnectivityFailed(peer, "TURN relay allocation failed"))
        return ConnectivityEstablishmentResult.failure("Failed to allocate TURN relay")
    }
    
    private suspend fun performFullTraversal(peer: Peer, natBehavior: NATBehavior): ConnectivityEstablishmentResult {
        // Try all available methods in order of preference
        return performAutoConnectivity(peer, natBehavior)
    }
    
    private fun startBackgroundServices() {
        backgroundJob = CoroutineScope(Dispatchers.IO).launch {
            // Periodic NAT re-detection
            if (configuration.natDetectionInterval > 0) {
                launch {
                    while (isActive) {
                        delay(configuration.natDetectionInterval)
                        try {
                            if (natDetectionService.shouldRedetect()) {
                                refreshNATDetection()
                            }
                        } catch (e: Exception) {
                            // Log error but continue
                        }
                    }
                }
            }
            
            // Relay allocation maintenance
            launch {
                while (isActive) {
                    delay(60000) // Check every minute
                    try {
                        maintainRelayAllocations()
                    } catch (e: Exception) {
                        // Log error but continue
                    }
                }
            }
        }
    }
    
    private suspend fun maintainRelayAllocations() {
        val expiredAllocations = mutableListOf<RelayAllocation>()
        
        for (allocation in activeRelayAllocations) {
            if (!allocation.isValid) {
                if (allocation.remainingLifetime > 60) {
                    // Try to refresh if more than 1 minute remaining
                    val refreshed = turnClient.refreshAllocation(allocation)
                    if (refreshed != null) {
                        activeRelayAllocations.remove(allocation)
                        activeRelayAllocations.add(refreshed)
                    } else {
                        expiredAllocations.add(allocation)
                    }
                } else {
                    expiredAllocations.add(allocation)
                }
            }
        }
        
        // Clean up expired allocations
        for (allocation in expiredAllocations) {
            activeRelayAllocations.remove(allocation)
            _events.emit(NATTraversalEvent.RelayAllocationExpired(allocation))
        }
    }
}

/**
 * Events emitted by the NAT traversal manager
 */
sealed class NATTraversalEvent {
    object ManagerInitialized : NATTraversalEvent()
    object ManagerShutdown : NATTraversalEvent()
    data class NATDetected(val behavior: NATBehavior) : NATTraversalEvent()
    data class ConnectivityAttemptStarted(val peer: Peer) : NATTraversalEvent()
    data class ConnectivityEstablished(val peer: Peer, val method: String) : NATTraversalEvent()
    data class ConnectivityFailed(val peer: Peer, val reason: String) : NATTraversalEvent()
    data class RelayAllocationExpired(val allocation: RelayAllocation) : NATTraversalEvent()
}

/**
 * Result of connectivity establishment attempt
 */
data class ConnectivityEstablishmentResult(
    val success: Boolean,
    val message: String,
    val method: PuncturingStrategy? = null,
    val relayAllocation: RelayAllocation? = null,
    val establishedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun success(
            message: String,
            method: PuncturingStrategy? = null,
            relayAllocation: RelayAllocation? = null
        ) = ConnectivityEstablishmentResult(true, message, method, relayAllocation)
        
        fun failure(message: String) = ConnectivityEstablishmentResult(false, message)
    }
}

/**
 * Statistics about NAT traversal operations
 */
data class NATTraversalStatistics(
    val currentNATType: NATType,
    val activeRelayAllocations: Int,
    val stunServersConfigured: Int,
    val turnServersConfigured: Int,
    val generatedAt: Long = System.currentTimeMillis()
)