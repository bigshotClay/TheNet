package com.bigshotsoftware.thenet.p2p.discovery

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Event bus system for peer discovery events with comprehensive callback management.
 * 
 * Provides:
 * - Type-safe event handling
 * - Asynchronous event processing
 * - Event filtering and routing
 * - Callback priority management
 * - Event history and replay
 */
class DiscoveryEventBus {

    /**
     * Extended discovery events with more detailed information
     */
    sealed class DetailedDiscoveryEvent {
        abstract val timestamp: Long
        abstract val eventId: String
        
        data class PeerDiscovered(
            val peer: IPv8Manager.PeerInfo,
            val source: DiscoverySource,
            val discoveryMethod: DiscoveryMethod,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class PeerLost(
            val peerId: String,
            val reason: LossReason,
            val lastSeen: Long,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class PeerConnected(
            val peer: IPv8Manager.PeerInfo,
            val connectionTime: Long,
            val connectionMethod: ConnectionMethod,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class PeerDisconnected(
            val peerId: String,
            val reason: DisconnectionReason,
            val connectionDuration: Long,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class PeerStatusChanged(
            val peerId: String,
            val oldStatus: PeerStatus,
            val newStatus: PeerStatus,
            val statusChangeReason: String,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class DiscoveryStarted(
            val bootstrapNodes: List<IPv8Manager.PeerInfo>,
            val config: PeerDiscoveryService.DiscoveryConfig,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class DiscoveryStopped(
            val reason: String,
            val totalRuntime: Long,
            val peersDiscovered: Int,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class DiscoveryError(
            val error: String,
            val cause: Throwable?,
            val severity: ErrorSeverity,
            val recoverable: Boolean,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class NetworkPartition(
            val partitionedPeers: List<String>,
            val retainedPeers: List<String>,
            val partitionDuration: Long,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class NetworkMerge(
            val mergedPeers: List<String>,
            val conflictResolutions: Map<String, String>,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
        
        data class DHTOperation(
            val operation: KademliaDHT.Operation,
            val key: ByteArray?,
            val success: Boolean,
            val latency: Long,
            val nodeCount: Int,
            override val timestamp: Long = System.currentTimeMillis(),
            override val eventId: String = generateEventId()
        ) : DetailedDiscoveryEvent()
    }

    /**
     * Discovery source types
     */
    enum class DiscoverySource {
        DHT_LOOKUP,
        DHT_BOOTSTRAP,
        IPv8_DISCOVERY,
        MANUAL_ADD,
        PEER_REFERRAL,
        CACHE_RESTORE
    }

    /**
     * Discovery method types
     */
    enum class DiscoveryMethod {
        FIND_NODE,
        FIND_VALUE,
        PERIODIC_LOOKUP,
        BOOTSTRAP,
        GOSSIP,
        DIRECT_CONNECTION
    }

    /**
     * Connection method types
     */
    enum class ConnectionMethod {
        DIRECT_TCP,
        NAT_PUNCHING,
        RELAY,
        IPv8_OVERLAY
    }

    /**
     * Peer loss reasons
     */
    enum class LossReason {
        TIMEOUT,
        EXPLICIT_DISCONNECT,
        NETWORK_ERROR,
        PROTOCOL_ERROR,
        CACHE_EXPIRY,
        MANUAL_REMOVAL
    }

    /**
     * Disconnection reasons
     */
    enum class DisconnectionReason {
        GRACEFUL_SHUTDOWN,
        NETWORK_TIMEOUT,
        PROTOCOL_VIOLATION,
        RESOURCE_EXHAUSTION,
        SECURITY_VIOLATION,
        USER_INITIATED
    }

    /**
     * Peer status types
     */
    enum class PeerStatus {
        DISCOVERED,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        FAILED,
        BANNED,
        CACHED
    }

    /**
     * Error severity levels
     */
    enum class ErrorSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Event callback with priority and filtering
     */
    data class EventCallback(
        val id: String = generateCallbackId(),
        val callback: suspend (DetailedDiscoveryEvent) -> Unit,
        val priority: Int = 0, // Higher priority = executed first
        val eventFilter: (DetailedDiscoveryEvent) -> Boolean = { true },
        val async: Boolean = true
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Event flows
    private val _events = MutableSharedFlow<DetailedDiscoveryEvent>(
        replay = 100, // Keep last 100 events for late subscribers
        extraBufferCapacity = Channel.UNLIMITED
    )
    val events: SharedFlow<DetailedDiscoveryEvent> = _events.asSharedFlow()
    
    // Event history for replay
    private val eventHistory = mutableListOf<DetailedDiscoveryEvent>()
    private val maxHistorySize = 1000
    
    // Callback management
    private val callbacks = mutableMapOf<String, EventCallback>()
    private val eventChannel = Channel<DetailedDiscoveryEvent>(Channel.UNLIMITED)
    
    // Statistics
    private var totalEventsEmitted = 0L
    private var totalCallbacksExecuted = 0L
    private var averageCallbackLatency = 0L

    init {
        // Start event processing
        scope.launch {
            eventChannel.receiveAsFlow().collect { event ->
                processEvent(event)
            }
        }
    }

    /**
     * Emit a discovery event
     */
    suspend fun emit(event: DetailedDiscoveryEvent) {
        // Add to history
        synchronized(eventHistory) {
            eventHistory.add(event)
            if (eventHistory.size > maxHistorySize) {
                eventHistory.removeAt(0)
            }
        }
        
        // Emit to shared flow
        _events.emit(event)
        
        // Send to callback processing
        eventChannel.send(event)
        
        totalEventsEmitted++
    }

    /**
     * Register an event callback
     */
    fun registerCallback(
        callback: suspend (DetailedDiscoveryEvent) -> Unit,
        priority: Int = 0,
        eventFilter: (DetailedDiscoveryEvent) -> Boolean = { true },
        async: Boolean = true
    ): String {
        val eventCallback = EventCallback(
            callback = callback,
            priority = priority,
            eventFilter = eventFilter,
            async = async
        )
        
        callbacks[eventCallback.id] = eventCallback
        return eventCallback.id
    }

    /**
     * Unregister an event callback
     */
    fun unregisterCallback(callbackId: String): Boolean {
        return callbacks.remove(callbackId) != null
    }

    /**
     * Register typed callback for specific event types
     */
    inline fun <reified T : DetailedDiscoveryEvent> registerTypedCallback(
        noinline callback: suspend (T) -> Unit,
        priority: Int = 0,
        async: Boolean = true
    ): String {
        return registerCallback(
            callback = { event ->
                if (event is T) {
                    callback(event)
                }
            },
            priority = priority,
            eventFilter = { it is T },
            async = async
        )
    }

    /**
     * Get event history
     */
    fun getEventHistory(
        eventTypes: Set<kotlin.reflect.KClass<out DetailedDiscoveryEvent>>? = null,
        since: Long? = null,
        limit: Int? = null
    ): List<DetailedDiscoveryEvent> {
        return synchronized(eventHistory) {
            var filtered = eventHistory.asSequence()
            
            // Filter by event types
            if (eventTypes != null) {
                filtered = filtered.filter { event ->
                    eventTypes.any { type -> type.isInstance(event) }
                }
            }
            
            // Filter by timestamp
            if (since != null) {
                filtered = filtered.filter { it.timestamp >= since }
            }
            
            // Apply limit
            if (limit != null) {
                filtered = filtered.take(limit)
            }
            
            filtered.toList()
        }
    }

    /**
     * Replay events to a specific callback
     */
    suspend fun replayEvents(
        callbackId: String,
        eventTypes: Set<kotlin.reflect.KClass<out DetailedDiscoveryEvent>>? = null,
        since: Long? = null
    ) {
        val eventCallback = callbacks[callbackId] ?: return
        val eventsToReplay = getEventHistory(eventTypes, since)
        
        for (event in eventsToReplay) {
            if (eventCallback.eventFilter(event)) {
                try {
                    eventCallback.callback(event)
                } catch (e: Exception) {
                    // Log replay error but continue
                }
            }
        }
    }

    /**
     * Get event bus statistics
     */
    fun getStatistics(): EventBusStatistics {
        return EventBusStatistics(
            totalEventsEmitted = totalEventsEmitted,
            totalCallbacksExecuted = totalCallbacksExecuted,
            averageCallbackLatency = averageCallbackLatency,
            activeCallbacks = callbacks.size,
            eventHistorySize = eventHistory.size,
            pendingEvents = eventChannel.tryReceive().isSuccess
        )
    }

    /**
     * Clear event history
     */
    fun clearHistory() {
        synchronized(eventHistory) {
            eventHistory.clear()
        }
    }

    /**
     * Shutdown event bus
     */
    suspend fun shutdown() {
        eventChannel.close()
        scope.cancel()
        callbacks.clear()
        clearHistory()
    }

    private suspend fun processEvent(event: DetailedDiscoveryEvent) {
        val sortedCallbacks = callbacks.values.sortedByDescending { it.priority }
        
        for (eventCallback in sortedCallbacks) {
            if (eventCallback.eventFilter(event)) {
                val startTime = System.currentTimeMillis()
                
                try {
                    if (eventCallback.async) {
                        scope.launch {
                            eventCallback.callback(event)
                        }
                    } else {
                        eventCallback.callback(event)
                    }
                    
                    totalCallbacksExecuted++
                    
                    val latency = System.currentTimeMillis() - startTime
                    averageCallbackLatency = (averageCallbackLatency + latency) / 2
                    
                } catch (e: Exception) {
                    // Log callback execution error but continue with other callbacks
                    emit(DetailedDiscoveryEvent.DiscoveryError(
                        error = "Callback execution failed: ${e.message}",
                        cause = e,
                        severity = ErrorSeverity.LOW,
                        recoverable = true
                    ))
                }
            }
        }
    }

    companion object {
        private var eventIdCounter = 0L
        private var callbackIdCounter = 0L
        
        private fun generateEventId(): String {
            return "event_${System.currentTimeMillis()}_${eventIdCounter++}"
        }
        
        private fun generateCallbackId(): String {
            return "callback_${System.currentTimeMillis()}_${callbackIdCounter++}"
        }
    }

    /**
     * Event bus statistics
     */
    data class EventBusStatistics(
        val totalEventsEmitted: Long,
        val totalCallbacksExecuted: Long,
        val averageCallbackLatency: Long,
        val activeCallbacks: Int,
        val eventHistorySize: Int,
        val pendingEvents: Boolean
    )
}

/**
 * Convenience functions for common event patterns
 */

/**
 * Helper function to emit peer discovered event
 */
suspend fun DiscoveryEventBus.emitPeerDiscovered(
    peer: IPv8Manager.PeerInfo,
    source: DiscoveryEventBus.DiscoverySource,
    method: DiscoveryEventBus.DiscoveryMethod
) {
    emit(DiscoveryEventBus.DetailedDiscoveryEvent.PeerDiscovered(peer, source, method))
}

/**
 * Helper function to emit peer connected event
 */
suspend fun DiscoveryEventBus.emitPeerConnected(
    peer: IPv8Manager.PeerInfo,
    connectionMethod: DiscoveryEventBus.ConnectionMethod
) {
    emit(DiscoveryEventBus.DetailedDiscoveryEvent.PeerConnected(
        peer = peer,
        connectionTime = System.currentTimeMillis(),
        connectionMethod = connectionMethod
    ))
}

/**
 * Helper function to emit DHT operation event
 */
suspend fun DiscoveryEventBus.emitDHTOperation(
    operation: KademliaDHT.Operation,
    key: ByteArray?,
    success: Boolean,
    latency: Long,
    nodeCount: Int
) {
    emit(DiscoveryEventBus.DetailedDiscoveryEvent.DHTOperation(
        operation = operation,
        key = key,
        success = success,
        latency = latency,
        nodeCount = nodeCount
    ))
}