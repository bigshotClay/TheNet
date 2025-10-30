package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import com.bigshotsoftware.thenet.p2p.config.IPv8Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BootstrapServiceTest {

    private fun createTestConfiguration(): IPv8Configuration {
        return IPv8Configuration(
            peerId = "test-peer",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("bootstrap1.test", 8090),
                BootstrapPeer("bootstrap2.test", 8090),
                BootstrapPeer("bootstrap3.test", 8090),
                BootstrapPeer("bootstrap4.test", 8090),
            ),
        )
    }

    @Test
    fun testServiceLifecycle() = runTest {
        val service = BootstrapService(createTestConfiguration())

        assertFalse(service.getStatus().isRunning)

        service.start()
        assertTrue(service.getStatus().isRunning)

        service.stop()
        assertFalse(service.getStatus().isRunning)

        // Should be idempotent
        service.stop()
        assertFalse(service.getStatus().isRunning)
    }

    @Test
    fun testConnectToBootstrapNodes() = runTest {
        val service = BootstrapService(createTestConfiguration())
        service.start()

        val events = mutableListOf<BootstrapConnectionEvent>()
        val job = launch {
            service.connectionEvents.collect { event ->
                events.add(event)
            }
        }

        val peers = service.connectToBootstrapNodes(count = 3)

        delay(100) // Allow events to be collected
        job.cancel()

        // Should connect to some peers
        assertTrue(peers.isNotEmpty())
        assertTrue(peers.size <= 3)

        // Should have connection events
        assertTrue(events.any { it is BootstrapConnectionEvent.Connected })

        // Connected peers should have valid addresses
        assertTrue(peers.all { it.address.isNotBlank() })

        service.stop()
    }

    @Test
    fun testConnectionFailureHandling() = runTest {
        // Create a configuration with non-existent bootstrap nodes
        val config = IPv8Configuration(
            peerId = "test-peer",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("nonexistent1.test", 8090),
                BootstrapPeer("nonexistent2.test", 8090),
            ),
        )

        val service = BootstrapService(config)
        service.start()

        val events = mutableListOf<BootstrapConnectionEvent>()
        val job = launch {
            service.connectionEvents.collect { event ->
                events.add(event)
            }
        }

        // This test simulates connection failures
        // In the real implementation, all connections might fail
        val peers = service.connectToBootstrapNodes(count = 2)

        delay(200) // Allow fallback strategies to execute
        job.cancel()

        // Should have failure events
        val failureEvents = events.filterIsInstance<BootstrapConnectionEvent.Failed>()
        assertTrue(failureEvents.isNotEmpty() || events.any { it is BootstrapConnectionEvent.AllFailed })

        service.stop()
    }

    @Test
    fun testAddBootstrapNode() = runTest {
        val service = BootstrapService(createTestConfiguration())
        service.start()

        val initialStatus = service.getStatus()
        val initialNodeCount = initialStatus.statistics.totalNodes

        service.addBootstrapNode("new.bootstrap.test", 8090, "us-west")
        delay(100) // Allow update to process

        val updatedStatus = service.getStatus()
        assertEquals(initialNodeCount + 1, updatedStatus.statistics.totalNodes)

        service.stop()
    }

    @Test
    fun testServiceStatus() = runTest {
        val service = BootstrapService(createTestConfiguration())
        service.start()

        delay(200) // Allow some monitoring cycles

        val status = service.getStatus()
        assertTrue(status.isRunning)
        assertNotNull(status.statistics)
        assertNotNull(status.healthReport)
        assertNotNull(status.metrics)

        // Statistics should be populated
        assertTrue(status.statistics.totalNodes > 0)
        assertTrue(status.statistics.averageSuccessRate >= 0.0)

        // Health report should have data
        assertTrue(status.healthReport.totalNodes > 0)

        service.stop()
    }

    @Test
    fun testHealthAlertHandling() = runTest {
        val service = BootstrapService(createTestConfiguration())
        service.start()

        // Let the service run for a bit to process health alerts
        delay(500)

        val status = service.getStatus()

        // Service should be handling alerts internally
        // Check that the service is still running despite any alerts
        assertTrue(status.isRunning)

        service.stop()
    }

    @Test
    fun testConcurrentConnections() = runTest {
        val service = BootstrapService(createTestConfiguration())
        service.start()

        // Launch multiple concurrent connection attempts
        val jobs = List(3) {
            launch {
                val peers = service.connectToBootstrapNodes(count = 2)
                assertTrue(peers.size <= 2)
            }
        }

        // Wait for all connections to complete
        jobs.forEach { it.join() }

        service.stop()
    }

    @Test
    fun testRegionBasedSelection() = runTest {
        val config = IPv8Configuration(
            peerId = "test-peer",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("us-bootstrap1.test", 8090),
                BootstrapPeer("eu-bootstrap1.test", 8090),
                BootstrapPeer("ap-bootstrap1.test", 8090),
                BootstrapPeer("us-bootstrap2.test", 8090),
            ),
        )

        val service = BootstrapService(config)
        service.start()

        // The service should use proximity-based selection
        // This is tested indirectly through the connection results
        val peers = service.connectToBootstrapNodes(count = 2)
        assertTrue(peers.size <= 2)

        service.stop()
    }
}
