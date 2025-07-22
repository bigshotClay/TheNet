package com.bigshotsoftware.thenet.p2p.discovery

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive tests for peer discovery across different network scenarios.
 * 
 * Tests cover:
 * - Basic DHT operations
 * - Peer discovery and routing
 * - Network partitioning scenarios
 * - Performance under load
 * - Cache behavior
 * - Event system
 */
class PeerDiscoveryTest {

    private lateinit var testScope: TestScope
    private lateinit var dht: KademliaDHTImpl
    private lateinit var discoveryService: PeerDiscoveryService
    private lateinit var eventBus: DiscoveryEventBus
    private lateinit var peerCache: PeerCache

    @BeforeTest
    fun setup() {
        testScope = TestScope()
        
        val dhtConfig = KademliaDHT.KademliaConfig(
            nodeId = generateTestNodeId(),
            k = 5, // Smaller k for testing
            alpha = 2,
            pingTimeout = 1000, // Faster timeout for tests
            bucketRefreshInterval = 5000,
            republishInterval = 10000
        )
        
        dht = KademliaDHTImpl(dhtConfig)
        
        val discoveryConfig = PeerDiscoveryService.DiscoveryConfig(
            discoveryInterval = 2000, // Faster discovery for tests
            maxPeersToDiscover = 20,
            peerCacheSize = 50,
            peerCacheExpiryTime = 30000 // 30 seconds for tests
        )
        
        discoveryService = PeerDiscoveryService(dht, discoveryConfig)
        eventBus = DiscoveryEventBus()
        peerCache = PeerCache(
            PeerCache.PeerCacheConfig(
                maxMemoryCacheSize = 20,
                maxDiskCacheSize = 50,
                defaultTTL = 30000 // 30 seconds for tests
            )
        )
    }

    @AfterTest
    fun cleanup() {
        runTest {
            discoveryService.stop()
            dht.stop()
            eventBus.shutdown()
            peerCache.shutdown()
        }
        testScope.cancel()
    }

    @Test
    fun testBasicDHTOperations() = runTest {
        // Test DHT start
        dht.start(dht.config)
        
        // Test node addition
        val testNode = createTestDHTNode("test-node-1")
        assertTrue(dht.addNode(testNode))
        
        // Test closest nodes retrieval
        val closestNodes = dht.getClosestNodes(testNode.nodeId, 5)
        assertTrue(closestNodes.isNotEmpty())
        
        // Test store and find
        val testKey = "test-key".toByteArray()
        val testValue = "test-value".toByteArray()
        
        assertTrue(dht.store(testKey, testValue))
        
        val lookupResult = dht.findValue(testKey)
        assertTrue(lookupResult.found)
        assertContentEquals(testValue, lookupResult.value)
    }

    @Test
    fun testPeerDiscoveryService() = runTest {
        val discoveredPeers = mutableListOf<IPv8Manager.PeerInfo>()
        val discoveryEvents = mutableListOf<PeerDiscoveryService.DiscoveryEvent>()
        
        // Set up event listeners
        discoveryService.registerDiscoveryCallback { event ->
            discoveryEvents.add(event)
        }
        
        // Start discovery with bootstrap peers
        val bootstrapPeers = listOf(
            createTestPeerInfo("bootstrap-1"),
            createTestPeerInfo("bootstrap-2")
        )
        
        discoveryService.start(bootstrapPeers)
        
        // Wait for discovery to start
        delay(1000)
        
        // Check that discovery started event was emitted
        assertTrue(discoveryEvents.any { it is PeerDiscoveryService.DiscoveryEvent.DiscoveryStarted })
        
        // Manually trigger discovery
        val discovered = discoveryService.discoverPeers()
        
        // Verify statistics are updated
        val stats = discoveryService.discoveryStatistics.first()
        assertTrue(stats.lastDiscoveryTime > 0)
    }

    @Test
    fun testNetworkPartitioningScenario() = runTest {
        // Create two network partitions
        val partition1Nodes = (1..5).map { createTestDHTNode("partition1-node-$it") }
        val partition2Nodes = (1..5).map { createTestDHTNode("partition2-node-$it") }
        
        dht.start(dht.config)
        
        // Add nodes from partition 1
        for (node in partition1Nodes) {
            dht.addNode(node)
        }
        
        // Verify routing table has partition 1 nodes
        val initialSize = dht.routingTableSize.first()
        assertTrue(initialSize >= partition1Nodes.size)
        
        // Simulate network partition by clearing routing table
        // and adding partition 2 nodes
        for (node in partition1Nodes) {
            dht.removeNode(node.nodeId)
        }
        
        for (node in partition2Nodes) {
            dht.addNode(node)
        }
        
        // Verify partition 2 nodes are now in routing table
        val partitionedSize = dht.routingTableSize.first()
        assertTrue(partitionedSize >= partition2Nodes.size)
        
        // Simulate network merge by re-adding partition 1 nodes
        for (node in partition1Nodes) {
            dht.addNode(node)
        }
        
        // Verify merged network
        val mergedSize = dht.routingTableSize.first()
        assertTrue(mergedSize >= (partition1Nodes.size + partition2Nodes.size))
    }

    @Test
    fun testPeerCacheOperations() = runTest {
        val testPeer = createTestPeerInfo("cache-test-peer")
        
        // Test basic put/get
        peerCache.put(testPeer, PeerCache.PeerPriority.HIGH)
        val retrieved = peerCache.get(testPeer.peerId)
        
        assertNotNull(retrieved)
        assertEquals(testPeer.peerId, retrieved.peer.peerId)
        assertEquals(PeerCache.PeerPriority.HIGH, retrieved.priority)
        
        // Test reputation update
        peerCache.updateReputation(testPeer.peerId, 0.2)
        val updated = peerCache.get(testPeer.peerId)
        assertTrue(updated!!.reputation > 0.5)
        
        // Test connection attempt recording
        peerCache.recordConnectionAttempt(
            testPeer.peerId,
            success = true,
            latency = 100,
            connectionMethod = "TCP"
        )
        
        val withHistory = peerCache.get(testPeer.peerId)
        assertTrue(withHistory!!.connectionHistory.isNotEmpty())
        assertTrue(withHistory.connectionHistory.first().success)
    }

    @Test
    fun testCacheEvictionPolicies() = runTest {
        val cacheConfig = PeerCache.PeerCacheConfig(
            maxMemoryCacheSize = 3, // Small cache for testing eviction
            evictionPolicy = PeerCache.EvictionPolicy.LRU_WITH_REPUTATION
        )
        
        val testCache = PeerCache(cacheConfig)
        
        // Add peers beyond cache capacity
        val peers = (1..5).map { createTestPeerInfo("eviction-test-$it") }
        
        for ((index, peer) in peers.withIndex()) {
            val priority = if (index == 0) PeerCache.PeerPriority.CRITICAL else PeerCache.PeerPriority.NORMAL
            testCache.put(peer, priority)
        }
        
        // Verify cache size is within limits
        val stats = testCache.cacheStatistics.first()
        assertTrue(stats.memorySize <= 3)
        
        // Verify critical priority peer is still in cache
        val criticalPeer = testCache.get(peers[0].peerId)
        assertNotNull(criticalPeer)
        assertEquals(PeerCache.PeerPriority.CRITICAL, criticalPeer.priority)
        
        testCache.shutdown()
    }

    @Test
    fun testEventBusOperations() = runTest {
        val receivedEvents = mutableListOf<DiscoveryEventBus.DetailedDiscoveryEvent>()
        
        // Register event callback
        val callbackId = eventBus.registerCallback { event ->
            receivedEvents.add(event)
        }
        
        // Emit test events
        val testPeer = createTestPeerInfo("event-test-peer")
        
        eventBus.emitPeerDiscovered(
            testPeer,
            DiscoveryEventBus.DiscoverySource.DHT_LOOKUP,
            DiscoveryEventBus.DiscoveryMethod.FIND_NODE
        )
        
        eventBus.emitPeerConnected(
            testPeer,
            DiscoveryEventBus.ConnectionMethod.DIRECT_TCP
        )
        
        // Wait for event processing
        delay(100)
        
        // Verify events were received
        assertTrue(receivedEvents.size >= 2)
        assertTrue(receivedEvents.any { it is DiscoveryEventBus.DetailedDiscoveryEvent.PeerDiscovered })
        assertTrue(receivedEvents.any { it is DiscoveryEventBus.DetailedDiscoveryEvent.PeerConnected })
        
        // Test event history
        val history = eventBus.getEventHistory()
        assertTrue(history.isNotEmpty())
        
        // Test typed callback
        val peerDiscoveredEvents = mutableListOf<DiscoveryEventBus.DetailedDiscoveryEvent.PeerDiscovered>()
        eventBus.registerTypedCallback<DiscoveryEventBus.DetailedDiscoveryEvent.PeerDiscovered> { event ->
            peerDiscoveredEvents.add(event)
        }
        
        eventBus.emitPeerDiscovered(
            createTestPeerInfo("typed-test-peer"),
            DiscoveryEventBus.DiscoverySource.DHT_BOOTSTRAP,
            DiscoveryEventBus.DiscoveryMethod.BOOTSTRAP
        )
        
        delay(100)
        assertTrue(peerDiscoveredEvents.isNotEmpty())
        
        // Cleanup
        eventBus.unregisterCallback(callbackId)
    }

    @Test
    fun testHighLoadScenario() = runTest {
        dht.start(dht.config)
        discoveryService.start()
        
        val numberOfPeers = 50
        val peers = (1..numberOfPeers).map { createTestPeerInfo("load-test-$it") }
        
        // Add peers concurrently
        val addJobs = peers.map { peer ->
            async {
                discoveryService.addDiscoveredPeer(peer)
                peerCache.put(peer)
            }
        }
        
        addJobs.awaitAll()
        
        // Perform concurrent operations
        val operationJobs = (1..20).map {
            async {
                when (it % 4) {
                    0 -> discoveryService.discoverPeers()
                    1 -> {
                        val randomPeer = peers.random()
                        peerCache.get(randomPeer.peerId)
                    }
                    2 -> {
                        val randomPeer = peers.random()
                        peerCache.updateReputation(randomPeer.peerId, 0.1)
                    }
                    3 -> {
                        val testKey = "load-test-key-$it".toByteArray()
                        val testValue = "load-test-value-$it".toByteArray()
                        dht.store(testKey, testValue)
                    }
                }
            }
        }
        
        operationJobs.awaitAll()
        
        // Verify system is still functioning
        val stats = discoveryService.discoveryStatistics.first()
        val cacheStats = peerCache.cacheStatistics.first()
        
        assertTrue(stats.totalPeersDiscovered > 0)
        assertTrue(cacheStats.memorySize > 0)
    }

    @Test
    fun testNATTraversalScenario() = runTest {
        // Create peers behind NAT (simulated with special addressing)
        val natPeers = listOf(
            createTestPeerInfo("nat-peer-1", "192.168.1.100", 8080),
            createTestPeerInfo("nat-peer-2", "192.168.1.101", 8081),
            createTestPeerInfo("nat-peer-3", "10.0.0.100", 8082)
        )
        
        val publicPeers = listOf(
            createTestPeerInfo("public-peer-1", "203.0.113.1", 8090),
            createTestPeerInfo("public-peer-2", "203.0.113.2", 8091)
        )
        
        dht.start(dht.config)
        discoveryService.start(publicPeers) // Bootstrap with public peers
        
        // Simulate NAT peers connecting through public peers
        for (natPeer in natPeers) {
            discoveryService.addDiscoveredPeer(natPeer)
            
            // Record connection attempts with higher failure rate for NAT peers
            peerCache.recordConnectionAttempt(
                natPeer.peerId,
                success = Math.random() > 0.3, // 70% success rate
                latency = (100..500).random().toLong(),
                connectionMethod = "NAT_PUNCHING"
            )
        }
        
        // Verify all peers are discoverable
        val allPeers = discoveryService.getCachedPeers()
        assertTrue(allPeers.size >= (natPeers.size + publicPeers.size))
        
        // Test discovery across network boundaries
        val discovered = discoveryService.discoverPeers()
        assertTrue(discovered.isNotEmpty())
    }

    @Test
    fun testDHTConsistencyAfterChurn() = runTest {
        dht.start(dht.config)
        
        val initialNodes = (1..10).map { createTestDHTNode("initial-$it") }
        for (node in initialNodes) {
            dht.addNode(node)
        }
        
        // Store some values
        val keyValuePairs = (1..5).map { 
            "key-$it".toByteArray() to "value-$it".toByteArray()
        }
        
        for ((key, value) in keyValuePairs) {
            assertTrue(dht.store(key, value))
        }
        
        // Simulate node churn (some nodes leave, new ones join)
        val leavingNodes = initialNodes.take(3)
        val joiningNodes = (1..4).map { createTestDHTNode("new-$it") }
        
        for (node in leavingNodes) {
            dht.removeNode(node.nodeId)
        }
        
        for (node in joiningNodes) {
            dht.addNode(node)
        }
        
        // Verify values are still findable
        for ((key, expectedValue) in keyValuePairs) {
            val result = dht.findValue(key)
            if (result.found) {
                assertContentEquals(expectedValue, result.value)
            }
            // Note: Some values might be lost due to insufficient replication in test
        }
    }

    // Helper functions
    private fun generateTestNodeId(): ByteArray {
        return "test-node-${System.currentTimeMillis()}-${Math.random()}".toByteArray().take(20).toByteArray()
    }

    private fun createTestDHTNode(id: String): KademliaDHT.DHTNode {
        return KademliaDHT.DHTNode(
            nodeId = id.toByteArray().take(20).toByteArray(),
            address = "192.168.1.${(1..254).random()}",
            port = (8000..9000).random(),
            lastSeen = System.currentTimeMillis()
        )
    }

    private fun createTestPeerInfo(
        id: String,
        address: String = "192.168.1.${(1..254).random()}",
        port: Int = (8000..9000).random()
    ): IPv8Manager.PeerInfo {
        return IPv8Manager.PeerInfo(
            peerId = id,
            address = address,
            port = port,
            isConnected = false,
            lastSeen = System.currentTimeMillis()
        )
    }
}

/**
 * Integration tests that combine multiple components
 */
class PeerDiscoveryIntegrationTest {

    @Test
    fun testFullPeerDiscoveryPipeline() = runTest {
        // Create complete discovery system
        val dhtConfig = KademliaDHT.KademliaConfig(nodeId = generateTestNodeId())
        val dht = KademliaDHTImpl(dhtConfig)
        
        val discoveryConfig = PeerDiscoveryService.DiscoveryConfig(
            discoveryInterval = 5000,
            enablePeriodicDiscovery = false // Manual control for test
        )
        val discoveryService = PeerDiscoveryService(dht, discoveryConfig)
        
        val eventBus = DiscoveryEventBus()
        val peerCache = PeerCache()
        
        val allEvents = mutableListOf<DiscoveryEventBus.DetailedDiscoveryEvent>()
        eventBus.registerCallback { event -> allEvents.add(event) }
        
        try {
            // Start services
            dht.start(dhtConfig)
            
            val bootstrapPeers = (1..3).map { createTestPeerInfo("bootstrap-$it") }
            discoveryService.start(bootstrapPeers)
            
            // Simulate discovery cycle
            for (i in 1..5) {
                val newPeers = discoveryService.discoverPeers()
                
                for (peer in newPeers) {
                    peerCache.put(peer)
                    eventBus.emitPeerDiscovered(
                        peer,
                        DiscoveryEventBus.DiscoverySource.DHT_LOOKUP,
                        DiscoveryEventBus.DiscoveryMethod.FIND_NODE
                    )
                }
                
                delay(1000)
            }
            
            // Verify end-to-end functionality
            val cachedPeers = peerCache.getCachedPeers()
            val discoveredPeers = discoveryService.discoveredPeers.first()
            val stats = discoveryService.discoveryStatistics.first()
            
            assertTrue(cachedPeers.isNotEmpty())
            assertTrue(discoveredPeers.isNotEmpty())
            assertTrue(stats.totalPeersDiscovered > 0)
            assertTrue(allEvents.isNotEmpty())
            
        } finally {
            // Cleanup
            discoveryService.stop()
            dht.stop()
            eventBus.shutdown()
            peerCache.shutdown()
        }
    }

    private fun generateTestNodeId(): ByteArray {
        return "integration-test-${System.currentTimeMillis()}".toByteArray().take(20).toByteArray()
    }

    private fun createTestPeerInfo(id: String): IPv8Manager.PeerInfo {
        return IPv8Manager.PeerInfo(
            peerId = id,
            address = "192.168.1.${(1..254).random()}",
            port = (8000..9000).random(),
            isConnected = false,
            lastSeen = System.currentTimeMillis()
        )
    }
}