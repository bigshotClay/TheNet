package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import kotlin.test.*

class BootstrapNodeSelectorTest {

    private fun createTestNodes(): List<BootstrapNode> {
        return listOf(
            BootstrapNode(
                peer = BootstrapPeer("node1", 8090),
                priority = 100,
                isHealthy = true,
                successfulConnections = 90,
                failedConnections = 10,
                averageResponseTime = 50,
            ),
            BootstrapNode(
                peer = BootstrapPeer("node2", 8090),
                priority = 80,
                isHealthy = true,
                successfulConnections = 80,
                failedConnections = 20,
                averageResponseTime = 100,
            ),
            BootstrapNode(
                peer = BootstrapPeer("node3", 8090),
                priority = 90,
                isHealthy = false,
                consecutiveFailures = 1,
            ),
            BootstrapNode(
                peer = BootstrapPeer("node4", 8090),
                priority = 70,
                isHealthy = true,
                consecutiveFailures = 3, // Max failures reached
            ),
        )
    }

    @Test
    fun testRoundRobinSelector() {
        val selector = RoundRobinSelector()
        val nodes = createTestNodes()

        // First selection
        val selected1 = selector.selectNodes(nodes, 2)
        assertEquals(2, selected1.size)
        assertEquals("node1", selected1[0].peer.host)
        assertEquals("node2", selected1[1].peer.host)

        // Second selection should continue from where it left off
        val selected2 = selector.selectNodes(nodes, 2)
        assertEquals(2, selected2.size)
        assertEquals("node1", selected2[0].peer.host)
        assertEquals("node2", selected2[1].peer.host)

        // Test with more nodes requested than available
        val selected3 = selector.selectNodes(nodes, 10)
        assertEquals(2, selected3.size) // Only 2 nodes are available
    }

    @Test
    fun testPrioritySelector() {
        val selector = PrioritySelector()
        val nodes = createTestNodes()

        val selected = selector.selectNodes(nodes, 3)
        assertEquals(2, selected.size) // Only 2 available nodes
        assertEquals("node1", selected[0].peer.host) // Highest priority (100)
        assertEquals("node2", selected[1].peer.host) // Next priority (80)
    }

    @Test
    fun testPrioritySelectorWithEqualPriority() {
        val selector = PrioritySelector()
        val nodes = listOf(
            BootstrapNode(
                peer = BootstrapPeer("node1", 8090),
                priority = 100,
                successfulConnections = 80,
                failedConnections = 20,
                averageResponseTime = 100,
            ),
            BootstrapNode(
                peer = BootstrapPeer("node2", 8090),
                priority = 100,
                successfulConnections = 90,
                failedConnections = 10,
                averageResponseTime = 50,
            ),
        )

        val selected = selector.selectNodes(nodes, 2)
        assertEquals(2, selected.size)
        // node2 should be first due to better success rate
        assertEquals("node2", selected[0].peer.host)
        assertEquals("node1", selected[1].peer.host)
    }

    @Test
    fun testProximitySelector() {
        val nodes = listOf(
            BootstrapNode(peer = BootstrapPeer("us-node1", 8090), region = "us"),
            BootstrapNode(peer = BootstrapPeer("eu-node1", 8090), region = "eu"),
            BootstrapNode(peer = BootstrapPeer("us-node2", 8090), region = "us"),
            BootstrapNode(peer = BootstrapPeer("ap-node1", 8090), region = "ap"),
        )

        val selector = ProximitySelector("us")
        val selected = selector.selectNodes(nodes, 3)

        assertEquals(3, selected.size)
        // US nodes should come first
        assertTrue(selected[0].region == "us")
        assertTrue(selected[1].region == "us")
        // Then other regions
        assertTrue(selected[2].region != "us")
    }

    @Test
    fun testProximitySelectorNoRegion() {
        val nodes = createTestNodes()
        val selector = ProximitySelector(null)

        val selected = selector.selectNodes(nodes, 2)
        assertEquals(2, selected.size)
    }

    @Test
    fun testCompositeSelector() {
        val nodes = listOf(
            BootstrapNode(
                peer = BootstrapPeer("high-priority", 8090),
                priority = 100,
                region = "eu",
            ),
            BootstrapNode(
                peer = BootstrapPeer("us-node", 8090),
                priority = 50,
                region = "us",
            ),
            BootstrapNode(
                peer = BootstrapPeer("another-node", 8090),
                priority = 60,
                region = "ap",
            ),
        )

        val selector = CompositeSelector(
            listOf(
                ProximitySelector("us"),
                PrioritySelector(),
            ),
        )

        val selected = selector.selectNodes(nodes, 3)
        assertEquals(3, selected.size)

        // First node should be from US (proximity selector)
        assertEquals("us-node", selected[0].peer.host)
        // Remaining nodes by priority
        assertTrue(
            selected.map { it.peer.host }.containsAll(
                listOf("high-priority", "another-node"),
            ),
        )
    }

    @Test
    fun testEmptyNodeList() {
        val selectors = listOf(
            RoundRobinSelector(),
            PrioritySelector(),
            ProximitySelector("us"),
            CompositeSelector(listOf(PrioritySelector())),
        )

        selectors.forEach { selector ->
            val selected = selector.selectNodes(emptyList(), 5)
            assertTrue(selected.isEmpty())
        }
    }

    @Test
    fun testAllNodesUnavailable() {
        val nodes = listOf(
            BootstrapNode(
                peer = BootstrapPeer("node1", 8090),
                isHealthy = false,
            ),
            BootstrapNode(
                peer = BootstrapPeer("node2", 8090),
                consecutiveFailures = 5,
            ),
        )

        val selector = PrioritySelector()
        val selected = selector.selectNodes(nodes, 5)
        assertTrue(selected.isEmpty())
    }
}
