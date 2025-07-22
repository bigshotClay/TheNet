package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import com.bigshotsoftware.thenet.p2p.config.IPv8Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class BootstrapHealthMonitorTest {

    @Test
    fun testHealthCheckExecution() = runTest {
        val monitor = BootstrapHealthMonitor(
            checkInterval = 100.milliseconds,
            timeout = 50.milliseconds,
        )

        var checkCount = 0
        monitor.healthCheckFunction = { node ->
            checkCount++
            HealthCheckResult(
                success = true,
                responseTime = 10,
                error = null,
            )
        }

        val config = IPv8Configuration(
            peerId = "test",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("test1", 8090),
                BootstrapPeer("test2", 8090),
            ),
        )
        val manager = BootstrapNodeManager(config)

        monitor.startMonitoring(manager)
        delay(250) // Allow for 2 check cycles
        monitor.stopMonitoring()

        assertTrue(checkCount >= 4) // At least 2 nodes × 2 cycles

        manager.close()
    }

    @Test
    fun testMetricsCollection() = runTest {
        val monitor = BootstrapHealthMonitor()

        // Set up predictable health checks
        var callCount = 0
        monitor.healthCheckFunction = { node ->
            callCount++
            when (node.peer.host) {
                "healthy" -> HealthCheckResult(true, 100)
                "unhealthy" -> HealthCheckResult(false, 500, "Connection failed")
                else -> HealthCheckResult(true, 200)
            }
        }

        val config = IPv8Configuration(
            peerId = "test",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("healthy", 8090),
                BootstrapPeer("unhealthy", 8090),
            ),
        )
        val manager = BootstrapNodeManager(config)

        monitor.startMonitoring(manager)
        delay(150) // Wait for one check cycle
        monitor.stopMonitoring()

        val metrics = monitor.metrics.value
        assertEquals(2, metrics.totalChecks)
        assertEquals(1, metrics.successfulChecks)
        assertEquals(1, metrics.failedChecks)
        assertEquals(100L, metrics.averageResponseTime) // Only successful check
        assertNotNull(metrics.lastCheckTime)

        // Check node-specific metrics
        val healthyMetrics = metrics.nodeMetrics["healthy:8090"]
        assertNotNull(healthyMetrics)
        assertEquals(1, healthyMetrics.totalChecks)
        assertEquals(1, healthyMetrics.successfulChecks)
        assertEquals(0, healthyMetrics.consecutiveFailures)

        val unhealthyMetrics = metrics.nodeMetrics["unhealthy:8090"]
        assertNotNull(unhealthyMetrics)
        assertEquals(1, unhealthyMetrics.totalChecks)
        assertEquals(0, unhealthyMetrics.successfulChecks)
        assertEquals(1, unhealthyMetrics.consecutiveFailures)

        manager.close()
    }

    @Test
    fun testHealthAlerts() = runTest {
        val monitor = BootstrapHealthMonitor()
        val alerts = mutableListOf<HealthAlert>()

        // Set up health check to fail consistently
        monitor.healthCheckFunction = { node ->
            when (node.peer.host) {
                "failing" -> HealthCheckResult(false, 1000, "Failed")
                "slow" -> HealthCheckResult(true, 4000) // High latency
                else -> HealthCheckResult(true, 50)
            }
        }

        val config = IPv8Configuration(
            peerId = "test",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("failing", 8090),
                BootstrapPeer("slow", 8090),
                BootstrapPeer("normal", 8090),
            ),
        )
        val manager = BootstrapNodeManager(config)

        // Collect alerts
        val job = launch {
            monitor.alerts.collect { alert ->
                alerts.add(alert)
            }
        }

        monitor.startMonitoring(manager)

        // Simulate multiple failures to trigger consecutive failure alert
        repeat(3) {
            delay(100)
            manager.updateNodeHealth("failing:8090", false)
        }

        delay(200)
        monitor.stopMonitoring()
        job.cancel()

        // Should have alerts for high latency and consecutive failures
        assertTrue(alerts.any { it.type == AlertType.HIGH_LATENCY })
        assertTrue(alerts.any { it.nodeId == "slow:8090" })

        manager.close()
    }

    @Test
    fun testHealthReport() = runTest {
        val monitor = BootstrapHealthMonitor()

        monitor.healthCheckFunction = { node ->
            when (node.peer.host) {
                "healthy1" -> HealthCheckResult(true, 50)
                "healthy2" -> HealthCheckResult(true, 150)
                "unhealthy" -> HealthCheckResult(false, 500)
                else -> HealthCheckResult(true, 100)
            }
        }

        val config = IPv8Configuration(
            peerId = "test",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("healthy1", 8090),
                BootstrapPeer("healthy2", 8090),
                BootstrapPeer("unhealthy", 8090),
            ),
        )
        val manager = BootstrapNodeManager(config)

        monitor.startMonitoring(manager)
        delay(150) // One check cycle
        monitor.stopMonitoring()

        val report = monitor.getHealthReport()
        assertEquals(0.67, report.overallHealth, 0.01) // 2/3 healthy
        assertEquals(3, report.totalNodes)
        assertEquals(2, report.healthyNodes)
        assertEquals(100L, report.avgResponseTime) // Average of successful checks
        assertNotNull(report.lastCheckTime)

        // Check node reports
        assertEquals(3, report.nodeReports.size)

        val healthy1Report = report.nodeReports.find { it.nodeId == "healthy1:8090" }
        assertNotNull(healthy1Report)
        assertTrue(healthy1Report.isHealthy)
        assertEquals(1.0, healthy1Report.successRate)

        val unhealthyReport = report.nodeReports.find { it.nodeId == "unhealthy:8090" }
        assertNotNull(unhealthyReport)
        assertFalse(unhealthyReport.isHealthy)
        assertEquals(0.0, unhealthyReport.successRate)

        manager.close()
    }

    @Test
    fun testTimeoutHandling() = runTest {
        val monitor = BootstrapHealthMonitor(
            timeout = 50.milliseconds,
        )

        monitor.healthCheckFunction = { node ->
            // Simulate timeout by delaying longer than timeout
            delay(100)
            HealthCheckResult(true, 10)
        }

        val config = IPv8Configuration(
            peerId = "test",
            port = 8090,
            bootstrapPeers = listOf(BootstrapPeer("timeout-node", 8090)),
        )
        val manager = BootstrapNodeManager(config)

        monitor.startMonitoring(manager)
        delay(150)
        monitor.stopMonitoring()

        val metrics = monitor.metrics.value
        assertEquals(1, metrics.failedChecks)
        assertEquals(0, metrics.successfulChecks)

        val nodeMetrics = metrics.nodeMetrics["timeout-node:8090"]
        assertNotNull(nodeMetrics)
        assertFalse(nodeMetrics.lastSuccess)

        manager.close()
    }

    @Test
    fun testLowAvailabilityAlert() = runTest {
        val monitor = BootstrapHealthMonitor()
        val alerts = mutableListOf<HealthAlert>()

        // All nodes fail
        monitor.healthCheckFunction = { node ->
            HealthCheckResult(false, 100, "Failed")
        }

        val config = IPv8Configuration(
            peerId = "test",
            port = 8090,
            bootstrapPeers = (1..5).map { BootstrapPeer("node$it", 8090) },
        )
        val manager = BootstrapNodeManager(config)

        val job = launch {
            monitor.alerts.collect { alert ->
                alerts.add(alert)
            }
        }

        monitor.startMonitoring(manager)

        // Run multiple check cycles to accumulate enough failures
        repeat(3) {
            delay(100)
        }

        monitor.stopMonitoring()
        job.cancel()

        // Should have low availability alert
        assertTrue(alerts.any { it.type == AlertType.LOW_AVAILABILITY })

        manager.close()
    }
}
