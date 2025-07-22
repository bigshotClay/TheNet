package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import com.bigshotsoftware.thenet.p2p.config.IPv8Configuration
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages bootstrap nodes lifecycle, health monitoring, and selection
 */
class BootstrapNodeManager(
    private val configuration: IPv8Configuration,
    private val selector: BootstrapNodeSelector = CompositeSelector(
        listOf(
            PrioritySelector(),
            RoundRobinSelector(),
        ),
    ),
    private val healthCheckInterval: Duration = 1.minutes,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : CoroutineScope by coroutineScope {
    private val _nodes = MutableStateFlow<Map<String, BootstrapNode>>(emptyMap())
    val nodes: StateFlow<Map<String, BootstrapNode>> = _nodes.asStateFlow()

    private val _healthStatus = MutableSharedFlow<BootstrapHealthEvent>()
    val healthStatus: SharedFlow<BootstrapHealthEvent> = _healthStatus.asSharedFlow()

    private val nodeUpdates = Channel<NodeUpdate>(Channel.UNLIMITED)
    private var healthCheckJob: Job? = null

    init {
        initializeNodes()
        startNodeUpdateProcessor()
    }

    private fun initializeNodes() {
        val initialNodes = configuration.bootstrapPeers.map { peer ->
            BootstrapNode(
                peer = peer,
                region = inferRegion(peer),
                priority = calculatePriority(peer),
            )
        }.associateBy { it.id }

        _nodes.value = initialNodes
    }

    private fun inferRegion(peer: BootstrapPeer): String? {
        // Simple region inference based on domain patterns
        return when {
            peer.host.contains("us-") -> "us"
            peer.host.contains("eu-") -> "eu"
            peer.host.contains("ap-") -> "ap"
            else -> null
        }
    }

    private fun calculatePriority(peer: BootstrapPeer): Int {
        // Assign priority based on known reliable nodes
        return when (peer.host) {
            "bootstrap1.thenet.io" -> 100
            "bootstrap2.thenet.io" -> 90
            "bootstrap3.thenet.io" -> 80
            else -> 50
        }
    }

    private fun startNodeUpdateProcessor() {
        coroutineScope.launch {
            for (update in nodeUpdates) {
                when (update) {
                    is NodeUpdate.HealthUpdate -> processHealthUpdate(update)
                    is NodeUpdate.AddNode -> processAddNode(update)
                    is NodeUpdate.RemoveNode -> processRemoveNode(update)
                }
            }
        }
    }

    private fun processHealthUpdate(update: NodeUpdate.HealthUpdate) {
        _nodes.update { currentNodes ->
            currentNodes[update.nodeId]?.let { node ->
                currentNodes + (
                    update.nodeId to node.withHealthUpdate(
                        healthy = update.healthy,
                        responseTime = update.responseTime,
                        timestamp = update.timestamp,
                    )
                    )
            } ?: currentNodes
        }
    }

    private fun processAddNode(update: NodeUpdate.AddNode) {
        _nodes.update { currentNodes ->
            currentNodes + (update.node.id to update.node)
        }
    }

    private fun processRemoveNode(update: NodeUpdate.RemoveNode) {
        _nodes.update { currentNodes ->
            currentNodes - update.nodeId
        }
    }

    /**
     * Start health monitoring for all bootstrap nodes
     */
    fun startHealthMonitoring() {
        healthCheckJob?.cancel()
        healthCheckJob = coroutineScope.launch {
            while (isActive) {
                checkAllNodesHealth()
                delay(healthCheckInterval)
            }
        }
    }

    /**
     * Stop health monitoring
     */
    fun stopHealthMonitoring() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private suspend fun checkAllNodesHealth() {
        val currentNodes = _nodes.value.values
        val healthChecks = currentNodes.map { node ->
            async {
                checkNodeHealth(node)
            }
        }

        healthChecks.awaitAll()
    }

    private suspend fun checkNodeHealth(node: BootstrapNode) {
        val startTime = Clock.System.now()
        val isHealthy = performHealthCheck(node)
        val responseTime = (Clock.System.now() - startTime).inWholeMilliseconds

        nodeUpdates.send(
            NodeUpdate.HealthUpdate(
                nodeId = node.id,
                healthy = isHealthy,
                responseTime = responseTime,
                timestamp = Clock.System.now(),
            ),
        )

        _healthStatus.emit(
            BootstrapHealthEvent(
                nodeId = node.id,
                healthy = isHealthy,
                responseTime = responseTime,
                timestamp = Clock.System.now(),
            ),
        )
    }

    private suspend fun performHealthCheck(node: BootstrapNode): Boolean {
        // This would be replaced with actual health check logic
        // For now, simulate health check
        return withTimeoutOrNull(5.seconds) {
            delay((100..500).random().toLong())
            true
        } ?: false
    }

    /**
     * Get bootstrap nodes using the configured selection strategy
     */
    fun getBootstrapNodes(count: Int = 3): List<BootstrapNode> {
        val currentNodes = _nodes.value.values.toList()
        return selector.selectNodes(currentNodes, count)
    }

    /**
     * Get all available bootstrap nodes
     */
    fun getAvailableNodes(): List<BootstrapNode> {
        return _nodes.value.values.filter { it.isAvailable }
    }

    /**
     * Add a new bootstrap node dynamically
     */
    suspend fun addBootstrapNode(peer: BootstrapPeer, region: String? = null, priority: Int = 50) {
        val node = BootstrapNode(
            peer = peer,
            region = region ?: inferRegion(peer),
            priority = priority,
        )
        nodeUpdates.send(NodeUpdate.AddNode(node))
    }

    /**
     * Remove a bootstrap node
     */
    suspend fun removeBootstrapNode(nodeId: String) {
        nodeUpdates.send(NodeUpdate.RemoveNode(nodeId))
    }

    /**
     * Update node health status manually
     */
    suspend fun updateNodeHealth(nodeId: String, healthy: Boolean, responseTime: Long? = null) {
        nodeUpdates.send(
            NodeUpdate.HealthUpdate(
                nodeId = nodeId,
                healthy = healthy,
                responseTime = responseTime,
                timestamp = Clock.System.now(),
            ),
        )
    }

    /**
     * Get node statistics
     */
    fun getNodeStatistics(): BootstrapStatistics {
        val allNodes = _nodes.value.values
        return BootstrapStatistics(
            totalNodes = allNodes.size,
            healthyNodes = allNodes.count { it.isHealthy },
            availableNodes = allNodes.count { it.isAvailable },
            averageSuccessRate = allNodes.map { it.successRate }.average(),
            nodesByRegion = allNodes.groupBy { it.region ?: "unknown" }
                .mapValues { it.value.size },
        )
    }

    fun close() {
        healthCheckJob?.cancel()
        nodeUpdates.close()
    }
}

private sealed class NodeUpdate {
    data class HealthUpdate(
        val nodeId: String,
        val healthy: Boolean,
        val responseTime: Long?,
        val timestamp: Instant,
    ) : NodeUpdate()

    data class AddNode(val node: BootstrapNode) : NodeUpdate()
    data class RemoveNode(val nodeId: String) : NodeUpdate()
}

data class BootstrapHealthEvent(
    val nodeId: String,
    val healthy: Boolean,
    val responseTime: Long,
    val timestamp: Instant,
)

data class BootstrapStatistics(
    val totalNodes: Int,
    val healthyNodes: Int,
    val availableNodes: Int,
    val averageSuccessRate: Double,
    val nodesByRegion: Map<String, Int>,
)
