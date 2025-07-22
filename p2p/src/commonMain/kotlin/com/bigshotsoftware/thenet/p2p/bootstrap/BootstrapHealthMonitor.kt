package com.bigshotsoftware.thenet.p2p.bootstrap

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Health monitoring service for bootstrap nodes with metrics collection
 */
class BootstrapHealthMonitor(
    private val checkInterval: Duration = 30.seconds,
    private val timeout: Duration = 5.seconds,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

    private val _metrics = MutableStateFlow(BootstrapMetrics())
    val metrics: StateFlow<BootstrapMetrics> = _metrics.asStateFlow()

    private val _alerts = MutableSharedFlow<HealthAlert>()
    val alerts: SharedFlow<HealthAlert> = _alerts.asSharedFlow()

    private var monitoringJob: Job? = null

    /**
     * Health check function that can be overridden for testing
     */
    var healthCheckFunction: suspend (BootstrapNode) -> HealthCheckResult = ::defaultHealthCheck

    /**
     * Start monitoring the given nodes
     */
    fun startMonitoring(nodeManager: BootstrapNodeManager) {
        monitoringJob?.cancel()
        monitoringJob = coroutineScope.launch {
            while (isActive) {
                val nodes = nodeManager.nodes.value.values.toList()
                checkNodesHealth(nodes, nodeManager)
                delay(checkInterval)
            }
        }
    }

    /**
     * Stop health monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    private suspend fun checkNodesHealth(
        nodes: List<BootstrapNode>,
        nodeManager: BootstrapNodeManager,
    ) {
        val startTime = Clock.System.now()
        val results = coroutineScope {
            nodes.map { node ->
                async {
                    val result = performHealthCheck(node)
                    node to result
                }
            }.awaitAll()
        }

        // Update metrics
        updateMetrics(results, startTime)

        // Update node health in manager
        results.forEach { (node, result) ->
            nodeManager.updateNodeHealth(
                nodeId = node.id,
                healthy = result.success,
                responseTime = result.responseTime,
            )

            // Generate alerts for critical issues
            checkForAlerts(node, result)
        }
    }

    private suspend fun performHealthCheck(node: BootstrapNode): HealthCheckResult {
        return withTimeoutOrNull(timeout) {
            healthCheckFunction(node)
        } ?: HealthCheckResult(
            success = false,
            responseTime = timeout.inWholeMilliseconds,
            error = "Health check timeout",
        )
    }

    private suspend fun defaultHealthCheck(node: BootstrapNode): HealthCheckResult {
        val startTime = Clock.System.now()
        return try {
            // Simulate health check - in production this would:
            // 1. Attempt TCP connection
            // 2. Send ping/hello message
            // 3. Verify response
            delay((50..200).random().toLong())

            // Simulate 95% success rate
            val success = (1..100).random() <= 95

            HealthCheckResult(
                success = success,
                responseTime = (Clock.System.now() - startTime).inWholeMilliseconds,
                error = if (!success) "Connection failed" else null,
            )
        } catch (e: Exception) {
            HealthCheckResult(
                success = false,
                responseTime = (Clock.System.now() - startTime).inWholeMilliseconds,
                error = e.message,
            )
        }
    }

    private fun updateMetrics(
        results: List<Pair<BootstrapNode, HealthCheckResult>>,
        cycleStartTime: Instant,
    ) {
        val currentTime = Clock.System.now()
        val cycleTime = (currentTime - cycleStartTime).inWholeMilliseconds

        val successCount = results.count { it.second.success }
        val totalCount = results.size
        val avgResponseTime = results
            .filter { it.second.success }
            .map { it.second.responseTime }
            .average()
            .toLong()

        _metrics.update { current ->
            current.copy(
                totalChecks = current.totalChecks + totalCount,
                successfulChecks = current.successfulChecks + successCount,
                failedChecks = current.failedChecks + (totalCount - successCount),
                averageResponseTime = avgResponseTime,
                lastCheckTime = currentTime,
                lastCycleDuration = cycleTime,
                nodeMetrics = results.associate { (node, result) ->
                    node.id to NodeMetrics(
                        nodeId = node.id,
                        lastCheck = currentTime,
                        lastSuccess = result.success,
                        lastResponseTime = result.responseTime,
                        totalChecks = (current.nodeMetrics[node.id]?.totalChecks ?: 0) + 1,
                        successfulChecks = (current.nodeMetrics[node.id]?.successfulChecks ?: 0) +
                            if (result.success) 1 else 0,
                        consecutiveFailures = if (result.success) {
                            0
                        } else {
                            (current.nodeMetrics[node.id]?.consecutiveFailures ?: 0) + 1
                        },
                    )
                },
            )
        }
    }

    private suspend fun checkForAlerts(node: BootstrapNode, result: HealthCheckResult) {
        val nodeMetrics = _metrics.value.nodeMetrics[node.id]

        // Alert on consecutive failures
        if (nodeMetrics != null && nodeMetrics.consecutiveFailures >= 3) {
            _alerts.emit(
                HealthAlert(
                    type = AlertType.NODE_DOWN,
                    nodeId = node.id,
                    message = "Node ${node.id} has failed ${nodeMetrics.consecutiveFailures} consecutive health checks",
                    timestamp = Clock.System.now(),
                ),
            )
        }

        // Alert on high response time
        if (result.success && result.responseTime > 3000) {
            _alerts.emit(
                HealthAlert(
                    type = AlertType.HIGH_LATENCY,
                    nodeId = node.id,
                    message = "Node ${node.id} response time is ${result.responseTime}ms",
                    timestamp = Clock.System.now(),
                ),
            )
        }

        // Alert on low overall availability
        val overallSuccessRate = _metrics.value.let {
            if (it.totalChecks > 0) {
                it.successfulChecks.toDouble() / it.totalChecks
            } else {
                1.0
            }
        }

        if (overallSuccessRate < 0.5 && _metrics.value.totalChecks > 10) {
            _alerts.emit(
                HealthAlert(
                    type = AlertType.LOW_AVAILABILITY,
                    nodeId = null,
                    message = "Overall bootstrap availability is ${(overallSuccessRate * 100).toInt()}%",
                    timestamp = Clock.System.now(),
                ),
            )
        }
    }

    /**
     * Get health report for all nodes
     */
    fun getHealthReport(): HealthReport {
        val metrics = _metrics.value
        val overallHealth = if (metrics.totalChecks > 0) {
            metrics.successfulChecks.toDouble() / metrics.totalChecks
        } else {
            1.0
        }

        return HealthReport(
            overallHealth = overallHealth,
            totalNodes = metrics.nodeMetrics.size,
            healthyNodes = metrics.nodeMetrics.values.count { it.lastSuccess },
            avgResponseTime = metrics.averageResponseTime,
            lastCheckTime = metrics.lastCheckTime,
            nodeReports = metrics.nodeMetrics.values.map { nodeMetric ->
                NodeHealthReport(
                    nodeId = nodeMetric.nodeId,
                    isHealthy = nodeMetric.lastSuccess,
                    successRate = if (nodeMetric.totalChecks > 0) {
                        nodeMetric.successfulChecks.toDouble() / nodeMetric.totalChecks
                    } else {
                        0.0
                    },
                    avgResponseTime = nodeMetric.lastResponseTime,
                    consecutiveFailures = nodeMetric.consecutiveFailures,
                )
            },
        )
    }
}

data class HealthCheckResult(
    val success: Boolean,
    val responseTime: Long,
    val error: String? = null,
)

data class BootstrapMetrics(
    val totalChecks: Long = 0,
    val successfulChecks: Long = 0,
    val failedChecks: Long = 0,
    val averageResponseTime: Long = 0,
    val lastCheckTime: Instant? = null,
    val lastCycleDuration: Long = 0,
    val nodeMetrics: Map<String, NodeMetrics> = emptyMap(),
)

data class NodeMetrics(
    val nodeId: String,
    val lastCheck: Instant,
    val lastSuccess: Boolean,
    val lastResponseTime: Long,
    val totalChecks: Long,
    val successfulChecks: Long,
    val consecutiveFailures: Int,
)

data class HealthAlert(
    val type: AlertType,
    val nodeId: String?,
    val message: String,
    val timestamp: Instant,
)

enum class AlertType {
    NODE_DOWN,
    HIGH_LATENCY,
    LOW_AVAILABILITY,
    CONFIGURATION_ERROR,
}

data class HealthReport(
    val overallHealth: Double,
    val totalNodes: Int,
    val healthyNodes: Int,
    val avgResponseTime: Long,
    val lastCheckTime: Instant?,
    val nodeReports: List<NodeHealthReport>,
)

data class NodeHealthReport(
    val nodeId: String,
    val isHealthy: Boolean,
    val successRate: Double,
    val avgResponseTime: Long,
    val consecutiveFailures: Int,
)
