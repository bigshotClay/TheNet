package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.IPv8Manager.PeerInfo
import com.bigshotsoftware.thenet.p2p.config.IPv8Configuration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/**
 * High-level service that integrates bootstrap node management with peer discovery
 */
class BootstrapService(
    configuration: IPv8Configuration,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    private val nodeManager = BootstrapNodeManager(
        configuration = configuration,
        selector = CompositeSelector(
            listOf(
                ProximitySelector(getUserRegion()),
                PrioritySelector(),
                RoundRobinSelector(),
            ),
        ),
        coroutineScope = coroutineScope,
    )

    private val healthMonitor = BootstrapHealthMonitor(
        checkInterval = 30.seconds,
        coroutineScope = coroutineScope,
    )

    private val fallbackStrategy = CompositeFallbackStrategy(
        listOf(
            ExponentialBackoffStrategy(),
            DnsFallbackStrategy(),
            HardcodedFallbackStrategy(),
        ),
    )

    private val _connectionEvents = MutableSharedFlow<BootstrapConnectionEvent>()
    val connectionEvents: SharedFlow<BootstrapConnectionEvent> = _connectionEvents.asSharedFlow()

    private var isStarted = false

    /**
     * Start the bootstrap service
     */
    fun start() {
        if (isStarted) return

        nodeManager.startHealthMonitoring()
        healthMonitor.startMonitoring(nodeManager)

        // Monitor health alerts
        coroutineScope.launch {
            healthMonitor.alerts.collect { alert ->
                handleHealthAlert(alert)
            }
        }

        isStarted = true
    }

    /**
     * Stop the bootstrap service
     */
    fun stop() {
        if (!isStarted) return

        nodeManager.stopHealthMonitoring()
        healthMonitor.stopMonitoring()
        isStarted = false
    }

    /**
     * Connect to bootstrap nodes with automatic fallback
     */
    suspend fun connectToBootstrapNodes(count: Int = 3): List<PeerInfo> {
        val connectedPeers = mutableListOf<PeerInfo>()
        val failedNodes = mutableListOf<BootstrapNode>()

        // Try primary bootstrap nodes
        var nodes = nodeManager.getBootstrapNodes(count)

        while (connectedPeers.size < count && nodes.isNotEmpty()) {
            val connectionResults = connectToNodes(nodes)

            connectionResults.forEach { (node, peerInfo) ->
                if (peerInfo != null) {
                    connectedPeers.add(peerInfo)
                    _connectionEvents.emit(
                        BootstrapConnectionEvent.Connected(node, peerInfo),
                    )
                } else {
                    failedNodes.add(node)
                    _connectionEvents.emit(
                        BootstrapConnectionEvent.Failed(node, "Connection failed"),
                    )
                }
            }

            // If we need more connections, try fallback
            if (connectedPeers.size < count) {
                nodes = fallbackStrategy.handleFailure(
                    failedNodes = failedNodes,
                    availableNodes = nodeManager.getAvailableNodes()
                        .filter { it !in failedNodes },
                )

                if (nodes.isEmpty()) {
                    _connectionEvents.emit(
                        BootstrapConnectionEvent.AllFailed(
                            "Unable to connect to any bootstrap nodes",
                        ),
                    )
                    break
                }
            } else {
                break
            }
        }

        return connectedPeers
    }

    private suspend fun connectToNodes(
        nodes: List<BootstrapNode>,
    ): List<Pair<BootstrapNode, PeerInfo?>> {
        return coroutineScope {
            nodes.map { node ->
                async {
                    val peerInfo = attemptConnection(node)
                    node to peerInfo
                }
            }.awaitAll()
        }
    }

    private suspend fun attemptConnection(node: BootstrapNode): PeerInfo? {
        return withTimeoutOrNull(5.seconds) {
            // Simulate connection attempt
            // In production, this would use actual IPv8 connection logic
            delay((100..500).random().toLong())

            // 80% success rate for simulation
            if ((1..100).random() <= 80) {
                PeerInfo(
                    peerId = "peer-${node.id}",
                    address = node.peer.host,
                    port = node.peer.port,
                    isConnected = false,
                    lastSeen = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                )
            } else {
                null
            }
        }
    }

    private suspend fun handleHealthAlert(alert: HealthAlert) {
        when (alert.type) {
            AlertType.NODE_DOWN -> {
                // Node is down, remove it from active rotation
                alert.nodeId?.let { nodeId ->
                    coroutineScope.launch {
                        delay(5.seconds) // Give it a chance to recover
                        val node = nodeManager.nodes.value[nodeId]
                        if (node != null && node.consecutiveFailures >= 5) {
                            nodeManager.removeBootstrapNode(nodeId)
                        }
                    }
                }
            }
            AlertType.LOW_AVAILABILITY -> {
                // Try to discover new bootstrap nodes
                discoverNewBootstrapNodes()
            }
            else -> {
                // Log other alerts
            }
        }
    }

    private suspend fun discoverNewBootstrapNodes() {
        // Implement dynamic discovery logic
        // This could query DNS, use peer exchange, etc.
    }

    /**
     * Get current bootstrap service status
     */
    fun getStatus(): BootstrapServiceStatus {
        return BootstrapServiceStatus(
            isRunning = isStarted,
            statistics = nodeManager.getNodeStatistics(),
            healthReport = healthMonitor.getHealthReport(),
            metrics = healthMonitor.metrics.value,
        )
    }

    /**
     * Manually add a bootstrap node
     */
    suspend fun addBootstrapNode(host: String, port: Int, region: String? = null) {
        nodeManager.addBootstrapNode(
            peer = com.bigshotsoftware.thenet.p2p.config.BootstrapPeer(host, port),
            region = region,
        )
    }

    private fun getUserRegion(): String? {
        // In production, this would detect user's region
        // For now, return null to use global selection
        return null
    }
}

sealed class BootstrapConnectionEvent {
    data class Connected(val node: BootstrapNode, val peerInfo: PeerInfo) : BootstrapConnectionEvent()
    data class Failed(val node: BootstrapNode, val reason: String) : BootstrapConnectionEvent()
    data class AllFailed(val reason: String) : BootstrapConnectionEvent()
}

data class BootstrapServiceStatus(
    val isRunning: Boolean,
    val statistics: BootstrapStatistics,
    val healthReport: HealthReport,
    val metrics: BootstrapMetrics,
)
