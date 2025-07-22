package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import kotlinx.datetime.Clock
import kotlin.test.*

class BootstrapNodeTest {

    private fun createTestNode(
        host: String = "test.bootstrap.io",
        port: Int = 8090,
        isHealthy: Boolean = true,
        consecutiveFailures: Int = 0,
    ): BootstrapNode {
        return BootstrapNode(
            peer = BootstrapPeer(host, port),
            isHealthy = isHealthy,
            consecutiveFailures = consecutiveFailures,
            successfulConnections = 100,
            failedConnections = 10,
        )
    }

    @Test
    fun testBootstrapNodeCreation() {
        val peer = BootstrapPeer("bootstrap.test", 8090)
        val node = BootstrapNode(
            peer = peer,
            region = "us-east",
            priority = 100,
        )

        assertEquals("bootstrap.test:8090", node.id)
        assertEquals("us-east", node.region)
        assertEquals(100, node.priority)
        assertTrue(node.isHealthy)
        assertEquals(0, node.consecutiveFailures)
    }

    @Test
    fun testSuccessRateCalculation() {
        val node1 = createTestNode(
            successfulConnections = 80,
            failedConnections = 20,
        )
        assertEquals(0.8, node1.successRate, 0.01)

        val node2 = createTestNode(
            successfulConnections = 0,
            failedConnections = 0,
        )
        assertEquals(0.0, node2.successRate)
    }

    @Test
    fun testIsAvailable() {
        val healthyNode = createTestNode(
            isHealthy = true,
            consecutiveFailures = 0,
        )
        assertTrue(healthyNode.isAvailable)

        val unhealthyNode = createTestNode(
            isHealthy = false,
            consecutiveFailures = 1,
        )
        assertFalse(unhealthyNode.isAvailable)

        val maxFailuresNode = createTestNode(
            isHealthy = true,
            consecutiveFailures = BootstrapNode.MAX_CONSECUTIVE_FAILURES,
        )
        assertFalse(maxFailuresNode.isAvailable)
    }

    @Test
    fun testHealthUpdate() {
        val node = createTestNode(
            successfulConnections = 100,
            failedConnections = 10,
            averageResponseTime = 100,
        )

        val timestamp = Clock.System.now()

        // Test successful health update
        val successNode = node.withHealthUpdate(
            healthy = true,
            responseTime = 150,
            timestamp = timestamp,
        )

        assertEquals(101, successNode.successfulConnections)
        assertEquals(10, successNode.failedConnections)
        assertEquals(0, successNode.consecutiveFailures)
        assertTrue(successNode.isHealthy)
        assertEquals(timestamp, successNode.lastHealthCheck)
        assertEquals(timestamp, successNode.lastSuccessfulConnection)

        // Test failed health update
        val failedNode = node.withHealthUpdate(
            healthy = false,
            responseTime = null,
            timestamp = timestamp,
        )

        assertEquals(100, failedNode.successfulConnections)
        assertEquals(11, failedNode.failedConnections)
        assertEquals(1, failedNode.consecutiveFailures)
        assertFalse(failedNode.isHealthy)
        assertEquals(timestamp, failedNode.lastHealthCheck)
    }

    @Test
    fun testAverageResponseTimeCalculation() {
        var node = createTestNode(
            successfulConnections = 2,
            averageResponseTime = 100,
        )

        // Add new response time
        node = node.withHealthUpdate(
            healthy = true,
            responseTime = 200,
            timestamp = Clock.System.now(),
        )

        // (100 * 2 + 200) / 3 = 133.33
        assertEquals(133, node.averageResponseTime)
    }

    @Test
    fun testEqualsAndHashCode() {
        val peer1 = BootstrapPeer("host1", 8090)
        val peer2 = BootstrapPeer("host2", 8090)

        val node1 = BootstrapNode(peer = peer1)
        val node2 = BootstrapNode(peer = peer1)
        val node3 = BootstrapNode(peer = peer2)

        assertEquals(node1, node2)
        assertNotEquals(node1, node3)
        assertEquals(node1.hashCode(), node2.hashCode())
        assertNotEquals(node1.hashCode(), node3.hashCode())
    }

    @Test
    fun testPublicKeyEquality() {
        val peer = BootstrapPeer("host", 8090)
        val key1 = byteArrayOf(1, 2, 3)
        val key2 = byteArrayOf(1, 2, 3)
        val key3 = byteArrayOf(4, 5, 6)

        val node1 = BootstrapNode(peer = peer, publicKey = key1)
        val node2 = BootstrapNode(peer = peer, publicKey = key2)
        val node3 = BootstrapNode(peer = peer, publicKey = key3)

        assertEquals(node1, node2)
        assertNotEquals(node1, node3)
    }
}
