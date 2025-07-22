package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class BootstrapFallbackStrategyTest {

    private fun createFailedNodes(): List<BootstrapNode> {
        return listOf(
            BootstrapNode(peer = BootstrapPeer("failed1", 8090), isHealthy = false),
            BootstrapNode(peer = BootstrapPeer("failed2", 8090), isHealthy = false),
        )
    }

    private fun createAvailableNodes(): List<BootstrapNode> {
        return listOf(
            BootstrapNode(peer = BootstrapPeer("available1", 8090)),
            BootstrapNode(peer = BootstrapPeer("available2", 8090)),
            BootstrapNode(peer = BootstrapPeer("available3", 8090)),
            BootstrapNode(peer = BootstrapPeer("available4", 8090)),
        )
    }

    @Test
    fun testExponentialBackoffStrategy() = runTest {
        val strategy = ExponentialBackoffStrategy(
            initialDelay = 10.milliseconds,
            maxDelay = 100.milliseconds,
            maxRetries = 3,
        )

        val failed = createFailedNodes()
        val available = createAvailableNodes()

        // First retry
        val result1 = strategy.handleFailure(failed, available)
        assertEquals(3, result1.size)

        // Second retry
        val result2 = strategy.handleFailure(failed, available)
        assertEquals(3, result2.size)

        // Third retry
        val result3 = strategy.handleFailure(failed, available)
        assertEquals(3, result3.size)

        // After max retries, should return empty
        val result4 = strategy.handleFailure(failed, available)
        assertTrue(result4.isEmpty())
    }

    @Test
    fun testDnsFallbackStrategy() = runTest {
        val strategy = DnsFallbackStrategy()
        val failed = createFailedNodes()
        val available = emptyList<BootstrapNode>()

        val result = strategy.handleFailure(failed, available)

        // Should return DNS-discovered nodes
        assertEquals(2, result.size)
        assertEquals("dns-bootstrap1.thenet.io", result[0].peer.host)
        assertEquals("dns-bootstrap2.thenet.io", result[1].peer.host)
        assertEquals(75, result[0].priority)
    }

    @Test
    fun testHardcodedFallbackStrategy() = runTest {
        val strategy = HardcodedFallbackStrategy()
        val failed = listOf(
            BootstrapNode(peer = BootstrapPeer("emergency1.thenet.io", 8090)),
        )
        val available = emptyList<BootstrapNode>()

        val result = strategy.handleFailure(failed, available)

        // Should not include the failed node
        assertFalse(result.any { it.peer.host == "emergency1.thenet.io" })
        assertTrue(result.size > 0)

        // Should include emergency nodes
        assertTrue(result.any { it.peer.host == "emergency2.thenet.io" })
    }

    @Test
    fun testCompositeFallbackStrategy() = runTest {
        val strategy = CompositeFallbackStrategy(
            listOf(
                // First strategy returns 2 nodes
                object : BootstrapFallbackStrategy {
                    override suspend fun handleFailure(
                        failedNodes: List<BootstrapNode>,
                        availableNodes: List<BootstrapNode>,
                    ): List<BootstrapNode> {
                        return listOf(
                            BootstrapNode(peer = BootstrapPeer("strategy1-node1", 8090)),
                            BootstrapNode(peer = BootstrapPeer("strategy1-node2", 8090)),
                        )
                    }
                },
                // Second strategy returns 2 more nodes
                object : BootstrapFallbackStrategy {
                    override suspend fun handleFailure(
                        failedNodes: List<BootstrapNode>,
                        availableNodes: List<BootstrapNode>,
                    ): List<BootstrapNode> {
                        return listOf(
                            BootstrapNode(peer = BootstrapPeer("strategy2-node1", 8090)),
                            BootstrapNode(peer = BootstrapPeer("strategy2-node2", 8090)),
                        )
                    }
                },
            ),
        )

        val result = strategy.handleFailure(createFailedNodes(), createAvailableNodes())

        // Should stop after getting 3 nodes
        assertEquals(3, result.size)
        assertTrue(result.any { it.peer.host.startsWith("strategy1") })
        assertTrue(result.any { it.peer.host.startsWith("strategy2") })
    }

    @Test
    fun testCompositeFallbackWithEmptyStrategies() = runTest {
        val strategy = CompositeFallbackStrategy(
            listOf(
                // All strategies return empty
                object : BootstrapFallbackStrategy {
                    override suspend fun handleFailure(
                        failedNodes: List<BootstrapNode>,
                        availableNodes: List<BootstrapNode>,
                    ): List<BootstrapNode> = emptyList()
                },
                object : BootstrapFallbackStrategy {
                    override suspend fun handleFailure(
                        failedNodes: List<BootstrapNode>,
                        availableNodes: List<BootstrapNode>,
                    ): List<BootstrapNode> = emptyList()
                },
            ),
        )

        val result = strategy.handleFailure(createFailedNodes(), createAvailableNodes())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testPeerExchangeFallbackStrategy() = runTest {
        val strategy = PeerExchangeFallbackStrategy()

        // Currently returns empty (placeholder implementation)
        val result = strategy.handleFailure(createFailedNodes(), createAvailableNodes())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFallbackWithDuplicateNodes() = runTest {
        val strategy = CompositeFallbackStrategy(
            listOf(
                // Both strategies return the same node
                object : BootstrapFallbackStrategy {
                    override suspend fun handleFailure(
                        failedNodes: List<BootstrapNode>,
                        availableNodes: List<BootstrapNode>,
                    ): List<BootstrapNode> {
                        return listOf(
                            BootstrapNode(peer = BootstrapPeer("duplicate-node", 8090)),
                        )
                    }
                },
                object : BootstrapFallbackStrategy {
                    override suspend fun handleFailure(
                        failedNodes: List<BootstrapNode>,
                        availableNodes: List<BootstrapNode>,
                    ): List<BootstrapNode> {
                        return listOf(
                            BootstrapNode(peer = BootstrapPeer("duplicate-node", 8090)),
                        )
                    }
                },
            ),
        )

        val result = strategy.handleFailure(createFailedNodes(), createAvailableNodes())

        // Should remove duplicates
        assertEquals(1, result.size)
        assertEquals("duplicate-node", result[0].peer.host)
    }
}
