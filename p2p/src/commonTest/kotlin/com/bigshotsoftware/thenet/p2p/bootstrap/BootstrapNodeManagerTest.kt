package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import com.bigshotsoftware.thenet.p2p.config.IPv8Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BootstrapNodeManagerTest {

    private fun createTestConfiguration(): IPv8Configuration {
        return IPv8Configuration(
            peerId = "test-peer",
            port = 8090,
            bootstrapPeers = listOf(
                BootstrapPeer("bootstrap1.test", 8090),
                BootstrapPeer("bootstrap2.test", 8090),
                BootstrapPeer("bootstrap3.test", 8090),
            ),
        )
    }

    @Test
    fun testInitialization() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        val nodes = manager.nodes.first()
        assertEquals(3, nodes.size)
        assertTrue(nodes.containsKey("bootstrap1.test:8090"))
        assertTrue(nodes.containsKey("bootstrap2.test:8090"))
        assertTrue(nodes.containsKey("bootstrap3.test:8090"))

        manager.close()
    }

    @Test
    fun testGetBootstrapNodes() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        val selected = manager.getBootstrapNodes(2)
        assertEquals(2, selected.size)
        assertTrue(selected.all { it.isAvailable })

        manager.close()
    }

    @Test
    fun testAddBootstrapNode() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        val initialCount = manager.nodes.first().size

        manager.addBootstrapNode(
            peer = BootstrapPeer("new.bootstrap.test", 8090),
            region = "us",
            priority = 100,
        )

        // Wait for update to process
        delay(100)

        val updatedNodes = manager.nodes.first()
        assertEquals(initialCount + 1, updatedNodes.size)
        assertTrue(updatedNodes.containsKey("new.bootstrap.test:8090"))

        val newNode = updatedNodes["new.bootstrap.test:8090"]
        assertNotNull(newNode)
        assertEquals("us", newNode.region)
        assertEquals(100, newNode.priority)

        manager.close()
    }

    @Test
    fun testRemoveBootstrapNode() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        val nodeId = "bootstrap1.test:8090"
        assertTrue(manager.nodes.first().containsKey(nodeId))

        manager.removeBootstrapNode(nodeId)

        // Wait for update to process
        delay(100)

        assertFalse(manager.nodes.first().containsKey(nodeId))

        manager.close()
    }

    @Test
    fun testUpdateNodeHealth() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        val nodeId = "bootstrap1.test:8090"

        // Update health to unhealthy
        manager.updateNodeHealth(nodeId, healthy = false, responseTime = 5000)
        delay(100)

        val node = manager.nodes.first()[nodeId]
        assertNotNull(node)
        assertFalse(node.isHealthy)
        assertEquals(1, node.consecutiveFailures)
        assertEquals(1, node.failedConnections)

        // Update back to healthy
        manager.updateNodeHealth(nodeId, healthy = true, responseTime = 100)
        delay(100)

        val healthyNode = manager.nodes.first()[nodeId]
        assertNotNull(healthyNode)
        assertTrue(healthyNode.isHealthy)
        assertEquals(0, healthyNode.consecutiveFailures)
        assertEquals(1, healthyNode.successfulConnections)

        manager.close()
    }

    @Test
    fun testGetAvailableNodes() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        // Initially all nodes should be available
        var available = manager.getAvailableNodes()
        assertEquals(3, available.size)

        // Make one node unhealthy
        manager.updateNodeHealth("bootstrap1.test:8090", healthy = false)
        delay(100)

        available = manager.getAvailableNodes()
        assertEquals(2, available.size)
        assertFalse(available.any { it.id == "bootstrap1.test:8090" })

        manager.close()
    }

    @Test
    fun testNodeStatistics() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        // Update some nodes
        manager.updateNodeHealth("bootstrap1.test:8090", healthy = false)
        manager.updateNodeHealth("bootstrap2.test:8090", healthy = true, responseTime = 100)
        delay(100)

        val stats = manager.getNodeStatistics()
        assertEquals(3, stats.totalNodes)
        assertEquals(2, stats.healthyNodes)
        assertEquals(2, stats.availableNodes)
        assertTrue(stats.averageSuccessRate >= 0.0)
        assertTrue(stats.averageSuccessRate <= 1.0)

        manager.close()
    }

    @Test
    fun testHealthMonitoring() = runTest {
        val config = createTestConfiguration()
        val manager = BootstrapNodeManager(config)

        // Collect health events
        val events = mutableListOf<BootstrapHealthEvent>()
        val job = manager.healthStatus.collect { event ->
            events.add(event)
        }

        manager.startHealthMonitoring()

        // Wait for at least one health check cycle
        delay(2000)

        manager.stopHealthMonitoring()

        // Should have received health events
        assertTrue(events.isNotEmpty())

        manager.close()
    }

    @Test
    fun testCustomSelector() = runTest {
        val config = createTestConfiguration()
        val customSelector = object : BootstrapNodeSelector {
            override fun selectNodes(nodes: List<BootstrapNode>, count: Int): List<BootstrapNode> {
                // Always select the last node
                return nodes.takeLast(1)
            }
        }

        val manager = BootstrapNodeManager(
            configuration = config,
            selector = customSelector,
        )

        val selected = manager.getBootstrapNodes(3)
        assertEquals(1, selected.size)
        assertEquals("bootstrap3.test:8090", selected[0].id)

        manager.close()
    }
}
