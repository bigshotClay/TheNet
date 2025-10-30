package com.bigshotsoftware.thenet.p2p.nat

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PublicKey
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NATTraversalTest {
    
    private lateinit var configuration: NATTraversalConfiguration
    private lateinit var natTraversalManager: NATTraversalManager
    private lateinit var stunClient: STUNClient
    private lateinit var turnClient: TURNClient
    
    @BeforeTest
    fun setup() {
        configuration = NATTraversalConfiguration(
            mode = NATTraversalMode.AUTO,
            stunServers = listOf(
                STUNServer("test-stun1.example.com", 3478, priority = 100),
                STUNServer("test-stun2.example.com", 3478, priority = 90)
            ),
            turnServers = listOf(
                TURNServer("test-turn.example.com", 3478, secure = false, 
                          username = "testuser", credential = "testpass", priority = 100)
            ),
            enablePortPrediction = true,
            maxPuncturingAttempts = 3,
            stunTimeoutMs = 1000,
            turnTimeoutMs = 2000
        )
        
        natTraversalManager = NATTraversalManager(configuration)
        stunClient = STUNClientFactory.createClient()
        turnClient = TURNClientFactory.createClient()
    }
    
    @Test
    fun testSTUNServerConfiguration() {
        val stunServer = STUNServer(
            host = "stun.example.com",
            port = 3478,
            secure = false,
            priority = 100,
            enabled = true
        )
        
        assertEquals("stun.example.com", stunServer.host)
        assertEquals(3478, stunServer.port)
        assertFalse(stunServer.secure)
        assertEquals(100, stunServer.priority)
        assertTrue(stunServer.enabled)
    }
    
    @Test
    fun testTURNServerConfiguration() {
        val turnServer = TURNServer(
            host = "turn.example.com",
            port = 3478,
            secure = false,
            username = "testuser",
            credential = "testpass",
            priority = 100,
            enabled = true
        )
        
        assertEquals("turn.example.com", turnServer.host)
        assertEquals(3478, turnServer.port)
        assertEquals("testuser", turnServer.username)
        assertEquals("testpass", turnServer.credential)
    }
    
    @Test
    fun testNATTypeClassification() {
        // Test all NAT types
        val natTypes = listOf(
            NATType.PUBLIC,
            NATType.FULL_CONE,
            NATType.RESTRICTED_CONE,
            NATType.PORT_RESTRICTED_CONE,
            NATType.SYMMETRIC,
            NATType.UNKNOWN,
            NATType.UDP_BLOCKED
        )
        
        natTypes.forEach { natType ->
            val behavior = NATBehavior(
                type = natType,
                mappingBehavior = MappingBehavior.ENDPOINT_INDEPENDENT,
                filteringBehavior = FilteringBehavior.ADDRESS_DEPENDENT,
                confidence = 85
            )
            
            assertEquals(natType, behavior.type)
            assertTrue(behavior.confidence > 0)
        }
    }
    
    @Test
    fun testPortMappingPatterns() {
        val patterns = listOf(
            PortMappingPattern.SEQUENTIAL,
            PortMappingPattern.RANDOM,
            PortMappingPattern.FIXED_INCREMENT,
            PortMappingPattern.UNKNOWN
        )
        
        patterns.forEach { pattern ->
            val behavior = NATBehavior(
                type = NATType.SYMMETRIC,
                mappingBehavior = MappingBehavior.ADDRESS_PORT_DEPENDENT,
                filteringBehavior = FilteringBehavior.ADDRESS_PORT_DEPENDENT,
                portPattern = pattern
            )
            
            assertEquals(pattern, behavior.portPattern)
        }
    }
    
    @Test
    fun testPuncturingStrategies() {
        val strategies = listOf(
            PuncturingStrategy.DIRECT,
            PuncturingStrategy.STUN_ASSISTED,
            PuncturingStrategy.SYMMETRIC_PREDICTION,
            PuncturingStrategy.SIMULTANEOUS_OPEN,
            PuncturingStrategy.RELAY_FALLBACK
        )
        
        // Test that all strategies are available
        assertEquals(5, strategies.size)
        assertTrue(strategies.contains(PuncturingStrategy.DIRECT))
        assertTrue(strategies.contains(PuncturingStrategy.RELAY_FALLBACK))
    }
    
    @Test
    fun testPuncturingResultSuccess() {
        val result = PuncturingResult.success(
            message = "Connection established",
            strategy = PuncturingStrategy.STUN_ASSISTED,
            establishedPorts = listOf(8090, 8091),
            latencyMs = 150
        )
        
        assertTrue(result.success)
        assertEquals("Connection established", result.message)
        assertEquals(PuncturingStrategy.STUN_ASSISTED, result.strategy)
        assertEquals(listOf(8090, 8091), result.establishedPorts)
        assertEquals(150L, result.latencyMs)
    }
    
    @Test
    fun testPuncturingResultFailure() {
        val result = PuncturingResult.failure(
            message = "Connection failed",
            strategy = PuncturingStrategy.DIRECT
        )
        
        assertFalse(result.success)
        assertEquals("Connection failed", result.message)
        assertEquals(PuncturingStrategy.DIRECT, result.strategy)
        assertTrue(result.establishedPorts.isEmpty())
        assertEquals(0L, result.latencyMs)
    }
    
    @Test
    fun testRelayAllocationValidity() {
        val validAllocation = RelayAllocation(
            relayAddress = IPv4Address("192.168.1.100", 8090),
            turnServer = TURNServer("turn.example.com", 3478, false, "user", "pass"),
            lifetimeSeconds = 600,
            createdAt = System.currentTimeMillis(),
            token = byteArrayOf(1, 2, 3, 4),
            maxBandwidth = 1000000
        )
        
        assertTrue(validAllocation.isValid)
        assertTrue(validAllocation.remainingLifetime > 0)
        
        val expiredAllocation = RelayAllocation(
            relayAddress = IPv4Address("192.168.1.100", 8090),
            turnServer = TURNServer("turn.example.com", 3478, false, "user", "pass"),
            lifetimeSeconds = 1,
            createdAt = System.currentTimeMillis() - 2000, // 2 seconds ago
            token = byteArrayOf(1, 2, 3, 4)
        )
        
        assertFalse(expiredAllocation.isValid)
        assertTrue(expiredAllocation.remainingLifetime <= 0)
    }
    
    @Test
    fun testConnectivityResult() {
        val result = ConnectivityResult(
            directConnectivity = false,
            stunAssisted = true,
            relayRequired = false,
            reflexiveAddresses = listOf(IPv4Address("203.0.113.1", 8090)),
            rttMeasurements = mapOf(IPv4Address("203.0.113.1", 8090) to 50L)
        )
        
        assertFalse(result.directConnectivity)
        assertTrue(result.stunAssisted)
        assertFalse(result.relayRequired)
        assertEquals(1, result.reflexiveAddresses.size)
        assertEquals(50L, result.rttMeasurements[IPv4Address("203.0.113.1", 8090)])
    }
    
    @Test
    fun testNATTraversalConfiguration() {
        val config = NATTraversalConfiguration(
            mode = NATTraversalMode.FULL_TRAVERSAL,
            stunServers = defaultSTUNServers,
            turnServers = emptyList(),
            enablePortPrediction = true,
            maxPuncturingAttempts = 10,
            stunTimeoutMs = 3000,
            turnTimeoutMs = 5000,
            enableNATDetection = true,
            natDetectionInterval = 600000
        )
        
        assertEquals(NATTraversalMode.FULL_TRAVERSAL, config.mode)
        assertTrue(config.stunServers.isNotEmpty())
        assertTrue(config.turnServers.isEmpty())
        assertTrue(config.enablePortPrediction)
        assertEquals(10, config.maxPuncturingAttempts)
        assertEquals(3000L, config.stunTimeoutMs)
        assertEquals(5000L, config.turnTimeoutMs)
        assertTrue(config.enableNATDetection)
        assertEquals(600000L, config.natDetectionInterval)
    }
    
    @Test
    fun testDefaultSTUNServers() {
        assertTrue(defaultSTUNServers.isNotEmpty())
        
        // Check Google STUN servers are included
        val googleServers = defaultSTUNServers.filter { it.host.contains("google.com") }
        assertTrue(googleServers.isNotEmpty())
        
        // Check Cloudflare STUN server is included  
        val cloudflareServers = defaultSTUNServers.filter { it.host.contains("cloudflare.com") }
        assertTrue(cloudflareServers.isNotEmpty())
        
        // Verify all servers have valid ports
        defaultSTUNServers.forEach { server ->
            assertTrue(server.port in 1..65535)
            assertTrue(server.host.isNotEmpty())
        }
    }
    
    @Test
    fun testNATTraversalModes() {
        val modes = NATTraversalMode.values()
        
        assertEquals(5, modes.size)
        assertTrue(modes.contains(NATTraversalMode.AUTO))
        assertTrue(modes.contains(NATTraversalMode.DIRECT_ONLY))
        assertTrue(modes.contains(NATTraversalMode.STUN_ASSISTED))
        assertTrue(modes.contains(NATTraversalMode.TURN_RELAY))
        assertTrue(modes.contains(NATTraversalMode.FULL_TRAVERSAL))
    }
    
    @Test
    fun testMappingAndFilteringBehaviors() {
        val mappingBehaviors = MappingBehavior.values()
        val filteringBehaviors = FilteringBehavior.values()
        
        assertEquals(4, mappingBehaviors.size)
        assertEquals(4, filteringBehaviors.size)
        
        assertTrue(mappingBehaviors.contains(MappingBehavior.ENDPOINT_INDEPENDENT))
        assertTrue(mappingBehaviors.contains(MappingBehavior.ADDRESS_DEPENDENT))
        assertTrue(mappingBehaviors.contains(MappingBehavior.ADDRESS_PORT_DEPENDENT))
        assertTrue(mappingBehaviors.contains(MappingBehavior.UNKNOWN))
        
        assertTrue(filteringBehaviors.contains(FilteringBehavior.ENDPOINT_INDEPENDENT))
        assertTrue(filteringBehaviors.contains(FilteringBehavior.ADDRESS_DEPENDENT))
        assertTrue(filteringBehaviors.contains(FilteringBehavior.ADDRESS_PORT_DEPENDENT))
        assertTrue(filteringBehaviors.contains(FilteringBehavior.UNKNOWN))
    }
    
    @Test
    fun testNATTraversalStatistics() {
        val stats = NATTraversalStatistics(
            currentNATType = NATType.SYMMETRIC,
            activeRelayAllocations = 2,
            stunServersConfigured = 5,
            turnServersConfigured = 1
        )
        
        assertEquals(NATType.SYMMETRIC, stats.currentNATType)
        assertEquals(2, stats.activeRelayAllocations)
        assertEquals(5, stats.stunServersConfigured)
        assertEquals(1, stats.turnServersConfigured)
        assertTrue(stats.generatedAt > 0)
    }
    
    @Test
    fun testReceivedDataEquality() {
        val data1 = ReceivedData(
            data = byteArrayOf(1, 2, 3, 4),
            senderAddress = IPv4Address("192.168.1.1", 8090)
        )
        
        val data2 = ReceivedData(
            data = byteArrayOf(1, 2, 3, 4),
            senderAddress = IPv4Address("192.168.1.1", 8090)
        )
        
        val data3 = ReceivedData(
            data = byteArrayOf(5, 6, 7, 8),
            senderAddress = IPv4Address("192.168.1.1", 8090)
        )
        
        assertEquals(data1, data2)
        assertNotEquals(data1, data3)
        assertEquals(data1.hashCode(), data2.hashCode())
    }
    
    private fun createTestPeer(): Peer {
        return Peer(
            key = createTestKey(),
            address = IPv4Address("192.168.1.100", 8090)
        )
    }
    
    private fun createTestKey(): PublicKey {
        // This is a simplified test key - in real tests you'd use proper key generation
        return object : PublicKey {
            override fun keyToBin(): ByteArray = byteArrayOf(1, 2, 3, 4)
            override fun keyToHash(): ByteArray = byteArrayOf(5, 6, 7, 8)
            override fun verify(signature: ByteArray, msg: ByteArray): Boolean = true
            override fun encrypt(msg: ByteArray): ByteArray = msg
            override fun getSignatureLength(): Int = 64
        }
    }
}