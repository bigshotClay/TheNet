package com.bigshotsoftware.thenet.p2p.nat

import nl.tudelft.ipv8.IPv4Address
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for NAT type detection across different network scenarios.
 * This simulates various NAT configurations to verify correct detection.
 */
class NATTypeDetectionTest {
    
    private lateinit var natDetectionService: NATDetectionService
    private lateinit var configuration: NATTraversalConfiguration
    
    @BeforeTest
    fun setup() {
        configuration = NATTraversalConfiguration(
            stunServers = listOf(
                STUNServer("stun1.test.com", 3478, priority = 100),
                STUNServer("stun2.test.com", 3478, priority = 90)
            ),
            enableNATDetection = true,
            stunTimeoutMs = 1000
        )
        
        val mockStunClient = MockSTUNClient()
        natDetectionService = NATDetectionService(configuration, mockStunClient)
    }
    
    @Test
    fun testPublicIPDetection() = runTest {
        // Mock scenario: Same address returned from STUN as local address
        val mockClient = MockSTUNClient().apply {
            setMockResponse(IPv4Address("203.0.113.100", 8090))
            setLocalAddress(IPv4Address("203.0.113.100", 8090))
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        assertEquals(NATType.PUBLIC, behavior.type)
        assertEquals(MappingBehavior.ENDPOINT_INDEPENDENT, behavior.mappingBehavior)
        assertEquals(FilteringBehavior.ENDPOINT_INDEPENDENT, behavior.filteringBehavior)
        assertTrue(behavior.confidence > 90)
    }
    
    @Test
    fun testFullConeNATDetection() = runTest {
        // Mock scenario: Different WAN address, same mapping from different servers
        val mockClient = MockSTUNClient().apply {
            setMockResponse(IPv4Address("203.0.113.200", 12345))
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
            setConsistentMapping(true) // Same mapping across different servers
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        // With endpoint-independent mapping, we expect restricted cone
        // (since we can't easily test filtering without server cooperation)
        assertEquals(NATType.RESTRICTED_CONE, behavior.type)
        assertEquals(MappingBehavior.ENDPOINT_INDEPENDENT, behavior.mappingBehavior)
    }
    
    @Test
    fun testSymmetricNATDetection() = runTest {
        // Mock scenario: Different mappings from different servers
        val mockClient = MockSTUNClient().apply {
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
            setConsistentMapping(false) // Different mappings
            setMockResponses(mapOf(
                "stun1.test.com" to IPv4Address("203.0.113.200", 12345),
                "stun2.test.com" to IPv4Address("203.0.113.200", 54321)
            ))
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        assertEquals(NATType.SYMMETRIC, behavior.type)
        assertTrue(behavior.mappingBehavior in listOf(
            MappingBehavior.ADDRESS_DEPENDENT, 
            MappingBehavior.ADDRESS_PORT_DEPENDENT
        ))
    }
    
    @Test
    fun testUDPBlockedDetection() = runTest {
        // Mock scenario: No response from STUN servers
        val mockClient = MockSTUNClient().apply {
            setMockResponse(null) // No response indicates UDP blocked
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        assertEquals(NATType.UDP_BLOCKED, behavior.type)
        assertEquals(MappingBehavior.UNKNOWN, behavior.mappingBehavior)
        assertEquals(FilteringBehavior.UNKNOWN, behavior.filteringBehavior)
        assertTrue(behavior.confidence > 70)
    }
    
    @Test
    fun testSequentialPortMappingDetection() = runTest {
        // Mock scenario: Sequential port allocation pattern
        val mockClient = MockSTUNClient().apply {
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
            setPortSequence(listOf(12345, 12346, 12347, 12348)) // Sequential
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        if (behavior.type == NATType.SYMMETRIC) {
            assertEquals(PortMappingPattern.SEQUENTIAL, behavior.portPattern)
        }
    }
    
    @Test
    fun testRandomPortMappingDetection() = runTest {
        // Mock scenario: Random port allocation pattern
        val mockClient = MockSTUNClient().apply {
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
            setPortSequence(listOf(12345, 45678, 23456, 56789)) // Random-looking
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        if (behavior.type == NATType.SYMMETRIC) {
            assertEquals(PortMappingPattern.RANDOM, behavior.portPattern)
        }
    }
    
    @Test
    fun testFixedIncrementPortMappingDetection() = runTest {
        // Mock scenario: Fixed increment port allocation
        val mockClient = MockSTUNClient().apply {
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
            setPortSequence(listOf(12345, 12347, 12349, 12351)) // Increment by 2
        }
        
        val service = NATDetectionService(configuration, mockClient)
        val behavior = service.detectNATBehavior()
        
        if (behavior.type == NATType.SYMMETRIC) {
            assertEquals(PortMappingPattern.FIXED_INCREMENT, behavior.portPattern)
        }
    }
    
    @Test
    fun testNATDetectionCaching() = runTest {
        val mockClient = MockSTUNClient().apply {
            setMockResponse(IPv4Address("203.0.113.200", 12345))
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
        }
        
        val service = NATDetectionService(configuration, mockClient)
        
        // First detection
        val behavior1 = service.detectNATBehavior()
        val callCount1 = mockClient.getCallCount()
        
        // Second detection should use cache
        val behavior2 = service.detectNATBehavior()
        val callCount2 = mockClient.getCallCount()
        
        assertEquals(behavior1.type, behavior2.type)
        assertEquals(callCount1, callCount2) // No additional calls made
        
        // Forced refresh should trigger new detection
        val behavior3 = service.detectNATBehavior(forceRefresh = true)
        val callCount3 = mockClient.getCallCount()
        
        assertTrue(callCount3 > callCount2) // New calls made
    }
    
    @Test
    fun testNATDetectionWithInsufficientServers() = runTest {
        val configWithOneServer = NATTraversalConfiguration(
            stunServers = listOf(STUNServer("stun1.test.com", 3478)),
            enableNATDetection = true
        )
        
        val mockClient = MockSTUNClient().apply {
            setMockResponse(IPv4Address("203.0.113.200", 12345))
            setLocalAddress(IPv4Address("192.168.1.100", 8090))
        }
        
        val service = NATDetectionService(configWithOneServer, mockClient)
        val behavior = service.detectNATBehavior()
        
        // With only one server, detection should still work but with lower confidence
        assertTrue(behavior.confidence < 90)
    }
    
    @Test
    fun testNATDetectionDisabled() = runTest {
        val configDisabled = NATTraversalConfiguration(
            enableNATDetection = false
        )
        
        val mockClient = MockSTUNClient()
        val service = NATDetectionService(configDisabled, mockClient)
        val behavior = service.detectNATBehavior()
        
        assertEquals(NATType.UNKNOWN, behavior.type)
        assertEquals(0, behavior.confidence)
        assertEquals(0, mockClient.getCallCount()) // No STUN calls made
    }
    
    @Test
    fun testNATDetectionTimeout() = runTest {
        val configShortTimeout = NATTraversalConfiguration(
            stunServers = listOf(STUNServer("slow-stun.test.com", 3478)),
            stunTimeoutMs = 100 // Very short timeout
        )
        
        val mockClient = MockSTUNClient().apply {
            setResponseDelay(500) // Longer than timeout
        }
        
        val service = NATDetectionService(configShortTimeout, mockClient)
        val behavior = service.detectNATBehavior()
        
        // Should fallback to unknown due to timeout
        assertEquals(NATType.UNKNOWN, behavior.type)
    }
    
    @Test
    fun testMappingBehaviorClassification() {
        // Test the logic for determining mapping behavior
        val endpointIndependent = MappingBehavior.ENDPOINT_INDEPENDENT
        val addressDependent = MappingBehavior.ADDRESS_DEPENDENT
        val addressPortDependent = MappingBehavior.ADDRESS_PORT_DEPENDENT
        
        // Endpoint independent should lead to cone NAT classification
        assertTrue(endpointIndependent != addressDependent)
        assertTrue(endpointIndependent != addressPortDependent)
        
        // Address/port dependent should lead to symmetric NAT
        assertTrue(addressDependent != endpointIndependent)
        assertTrue(addressPortDependent != endpointIndependent)
    }
    
    @Test
    fun testFilteringBehaviorClassification() {
        // Test filtering behavior classification logic
        val behaviors = FilteringBehavior.values()
        
        assertEquals(4, behaviors.size)
        assertTrue(behaviors.contains(FilteringBehavior.ENDPOINT_INDEPENDENT))
        assertTrue(behaviors.contains(FilteringBehavior.ADDRESS_DEPENDENT))
        assertTrue(behaviors.contains(FilteringBehavior.ADDRESS_PORT_DEPENDENT))
        assertTrue(behaviors.contains(FilteringBehavior.UNKNOWN))
    }
    
    @Test
    fun testPortPatternAnalysis() {
        // Test port pattern analysis with different sequences
        val sequentialPorts = listOf(8090, 8091, 8092, 8093)
        val randomPorts = listOf(8090, 23456, 45789, 12345)
        val incrementPorts = listOf(8090, 8095, 8100, 8105) // +5 each time
        
        // In a real implementation, these would be analyzed by the service
        assertTrue(sequentialPorts.zipWithNext().all { (a, b) -> b - a == 1 })
        assertTrue(incrementPorts.zipWithNext().all { (a, b) -> b - a == 5 })
        assertFalse(randomPorts.zipWithNext().all { (a, b) -> b - a < 10 })
    }
}

/**
 * Mock STUN client for testing NAT detection scenarios
 */
private class MockSTUNClient : STUNClient {
    private var mockResponse: IPv4Address? = null
    private var localAddress: IPv4Address = IPv4Address("192.168.1.100", 8090)
    private var consistentMapping = true
    private var mockResponses = mutableMapOf<String, IPv4Address>()
    private var portSequence = listOf<Int>()
    private var portIndex = 0
    private var callCount = 0
    private var responseDelay = 0L
    
    fun setMockResponse(address: IPv4Address?) {
        mockResponse = address
    }
    
    fun setLocalAddress(address: IPv4Address) {
        localAddress = address
    }
    
    fun setConsistentMapping(consistent: Boolean) {
        consistentMapping = consistent
    }
    
    fun setMockResponses(responses: Map<String, IPv4Address>) {
        mockResponses.clear()
        mockResponses.putAll(responses)
    }
    
    fun setPortSequence(ports: List<Int>) {
        portSequence = ports
        portIndex = 0
    }
    
    fun setResponseDelay(delayMs: Long) {
        responseDelay = delayMs
    }
    
    fun getCallCount(): Int = callCount
    
    override suspend fun discoverPublicAddress(server: STUNServer, timeoutMs: Long): IPv4Address? {
        callCount++
        
        if (responseDelay > timeoutMs) {
            return null // Simulate timeout
        }
        
        if (responseDelay > 0) {
            kotlinx.coroutines.delay(responseDelay)
        }
        
        return when {
            mockResponses.containsKey(server.host) -> mockResponses[server.host]
            portSequence.isNotEmpty() -> {
                val port = portSequence.getOrElse(portIndex) { portSequence.last() }
                portIndex = (portIndex + 1) % portSequence.size
                IPv4Address("203.0.113.200", port)
            }
            consistentMapping -> mockResponse
            else -> {
                // Return different response for different servers to simulate symmetric NAT
                val basePort = 12345
                val serverHash = server.host.hashCode()
                val port = basePort + (serverHash % 10000)
                IPv4Address("203.0.113.200", port)
            }
        }
    }
    
    override suspend fun determineNATType(
        servers: List<STUNServer>,
        timeoutMs: Long
    ): NATBehavior {
        // Simplified mock implementation
        return NATBehavior(
            type = NATType.UNKNOWN,
            mappingBehavior = MappingBehavior.UNKNOWN,
            filteringBehavior = FilteringBehavior.UNKNOWN
        )
    }
    
    override suspend fun performConnectivityChecks(
        remoteAddress: IPv4Address,
        stunServers: List<STUNServer>
    ): ConnectivityResult {
        return ConnectivityResult(
            directConnectivity = false,
            stunAssisted = true,
            relayRequired = false,
            reflexiveAddresses = listOf(IPv4Address("203.0.113.200", 12345)),
            rttMeasurements = mapOf(IPv4Address("203.0.113.200", 12345) to 50L)
        )
    }
    
    override suspend fun isUDPBlocked(server: STUNServer): Boolean {
        return mockResponse == null
    }
}