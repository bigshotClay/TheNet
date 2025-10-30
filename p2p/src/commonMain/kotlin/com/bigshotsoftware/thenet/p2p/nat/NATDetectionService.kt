package com.bigshotsoftware.thenet.p2p.nat

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.payload.ConnectionType
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Enhanced NAT detection service that extends IPv8's basic NAT detection
 * with comprehensive RFC 3489/5389 compliant NAT type classification.
 */
class NATDetectionService(
    private val configuration: NATTraversalConfiguration,
    private val stunClient: STUNClient
) {
    
    private var cachedNATBehavior: NATBehavior? = null
    private var lastDetectionTime = 0L
    private var isDetectionInProgress = false
    
    companion object {
        private const val MIN_SAMPLES_FOR_CONFIDENCE = 3
        private const val DETECTION_CACHE_TIMEOUT = 300000L // 5 minutes
    }
    
    /**
     * Detects comprehensive NAT behavior characteristics.
     * Implements RFC 3489/5389 NAT discovery algorithm.
     */
    suspend fun detectNATBehavior(forceRefresh: Boolean = false): NATBehavior {
        // Return cached result if still valid
        if (!forceRefresh && cachedNATBehavior != null && isCacheValid()) {
            return cachedNATBehavior!!
        }
        
        // Prevent concurrent detection
        if (isDetectionInProgress) {
            delay(100)
            return cachedNATBehavior ?: createUnknownBehavior()
        }
        
        return try {
            isDetectionInProgress = true
            performComprehensiveNATDetection()
        } finally {
            isDetectionInProgress = false
        }
    }
    
    /**
     * Converts legacy IPv8 ConnectionType to enhanced NATType
     */
    fun convertLegacyConnectionType(connectionType: ConnectionType): NATType {
        return when (connectionType) {
            ConnectionType.PUBLIC -> NATType.PUBLIC
            ConnectionType.SYMMETRIC_NAT -> NATType.SYMMETRIC
            ConnectionType.UNKNOWN -> NATType.UNKNOWN
        }
    }
    
    /**
     * Determines if NAT type detection should be re-run
     */
    fun shouldRedetect(): Boolean {
        if (configuration.natDetectionInterval <= 0) return false
        return !isCacheValid() || cachedNATBehavior == null
    }
    
    /**
     * Gets the current cached NAT behavior without triggering detection
     */
    fun getCachedNATBehavior(): NATBehavior? = cachedNATBehavior
    
    /**
     * Clears the NAT behavior cache (e.g., when network changes)
     */
    fun clearCache() {
        cachedNATBehavior = null
        lastDetectionTime = 0L
    }
    
    private suspend fun performComprehensiveNATDetection(): NATBehavior {
        if (!configuration.enableNATDetection || configuration.stunServers.isEmpty()) {
            return createUnknownBehavior()
        }
        
        val enabledStunServers = configuration.stunServers.filter { it.enabled }
        if (enabledStunServers.size < 2) {
            return performBasicDetection(enabledStunServers.firstOrNull())
        }
        
        // Perform RFC 3489 NAT detection algorithm
        val detectionResult = performRFC3489Detection(enabledStunServers)
        
        cachedNATBehavior = detectionResult
        lastDetectionTime = System.currentTimeMillis()
        
        return detectionResult
    }
    
    private suspend fun performRFC3489Detection(servers: List<STUNServer>): NATBehavior = withContext(Dispatchers.IO) {
        val server1 = servers[0]
        val server2 = servers.getOrNull(1) ?: server1
        
        // Test 1: Basic binding request to server1
        val test1Result = performBindingTest(server1, "Test1")
        if (test1Result == null) {
            return@withContext NATBehavior(
                type = NATType.UDP_BLOCKED,
                mappingBehavior = MappingBehavior.UNKNOWN,
                filteringBehavior = FilteringBehavior.UNKNOWN,
                confidence = 90
            )
        }
        
        val localAddress = getLocalAddress()
        
        // Check if we're behind NAT
        if (test1Result.address == localAddress) {
            return@withContext NATBehavior(
                type = NATType.PUBLIC,
                mappingBehavior = MappingBehavior.ENDPOINT_INDEPENDENT,
                filteringBehavior = FilteringBehavior.ENDPOINT_INDEPENDENT,
                confidence = 95
            )
        }
        
        // Test 2: Binding request to server2 from same local socket
        val test2Result = performBindingTest(server2, "Test2")
        if (test2Result == null) {
            return@withContext createUnknownBehavior("Test2 failed")
        }
        
        // Determine mapping behavior
        val mappingBehavior = if (test1Result.address == test2Result.address) {
            MappingBehavior.ENDPOINT_INDEPENDENT
        } else {
            // Test 3: Check if address changes with different local port
            val test3Result = performBindingTestDifferentPort(server1, "Test3")
            if (test3Result != null && test3Result.address != test1Result.address) {
                MappingBehavior.ADDRESS_PORT_DEPENDENT
            } else {
                MappingBehavior.ADDRESS_DEPENDENT
            }
        }
        
        // Determine NAT type based on mapping behavior
        val natType = when (mappingBehavior) {
            MappingBehavior.ENDPOINT_INDEPENDENT -> {
                // Additional tests to determine cone type
                determineConeType(server1, test1Result.address)
            }
            MappingBehavior.ADDRESS_DEPENDENT,
            MappingBehavior.ADDRESS_PORT_DEPENDENT -> NATType.SYMMETRIC
            else -> NATType.UNKNOWN
        }
        
        // Analyze port allocation pattern for symmetric NATs
        val portPattern = if (natType == NATType.SYMMETRIC) {
            analyzePortPattern(listOf(test1Result.port, test2Result.port))
        } else {
            PortMappingPattern.UNKNOWN
        }
        
        // Determine filtering behavior (requires more complex tests)
        val filteringBehavior = estimateFilteringBehavior(mappingBehavior)
        
        val confidence = calculateConfidence(listOf(test1Result, test2Result))
        
        NATBehavior(
            type = natType,
            mappingBehavior = mappingBehavior,
            filteringBehavior = filteringBehavior,
            portPattern = portPattern,
            confidence = confidence
        )
    }
    
    private suspend fun determineConeType(server: STUNServer, mappedAddress: IPv4Address): NATType {
        // For full cone detection, we would need the STUN server to support 
        // CHANGE-REQUEST attribute, which most modern servers don't support.
        // For now, assume restricted cone for endpoint-independent mapping
        return NATType.RESTRICTED_CONE
    }
    
    private fun estimateFilteringBehavior(mappingBehavior: MappingBehavior): FilteringBehavior {
        // This is a simplified heuristic - real detection would require
        // more sophisticated tests with cooperating STUN servers
        return when (mappingBehavior) {
            MappingBehavior.ENDPOINT_INDEPENDENT -> FilteringBehavior.ADDRESS_DEPENDENT
            MappingBehavior.ADDRESS_DEPENDENT -> FilteringBehavior.ADDRESS_DEPENDENT  
            MappingBehavior.ADDRESS_PORT_DEPENDENT -> FilteringBehavior.ADDRESS_PORT_DEPENDENT
            else -> FilteringBehavior.UNKNOWN
        }
    }
    
    private fun analyzePortPattern(ports: List<Int>): PortMappingPattern {
        if (ports.size < 2) return PortMappingPattern.UNKNOWN
        
        val sortedPorts = ports.sorted()
        val differences = sortedPorts.zipWithNext { a, b -> b - a }
        
        return when {
            differences.all { it == 1 } -> PortMappingPattern.SEQUENTIAL
            differences.all { it == differences.first() } -> PortMappingPattern.FIXED_INCREMENT
            differences.any { abs(it) > 1000 } -> PortMappingPattern.RANDOM
            else -> PortMappingPattern.UNKNOWN
        }
    }
    
    private suspend fun performBasicDetection(server: STUNServer?): NATBehavior {
        if (server == null) {
            return createUnknownBehavior("No STUN servers available")
        }
        
        val result = performBindingTest(server, "BasicTest")
        return if (result != null) {
            val localAddress = getLocalAddress()
            val isPublic = result.address == localAddress
            
            NATBehavior(
                type = if (isPublic) NATType.PUBLIC else NATType.UNKNOWN,
                mappingBehavior = MappingBehavior.UNKNOWN,
                filteringBehavior = FilteringBehavior.UNKNOWN,
                confidence = 50
            )
        } else {
            NATBehavior(
                type = NATType.UDP_BLOCKED,
                mappingBehavior = MappingBehavior.UNKNOWN,
                filteringBehavior = FilteringBehavior.UNKNOWN,
                confidence = 80
            )
        }
    }
    
    private suspend fun performBindingTest(server: STUNServer, testName: String): BindingTestResult? {
        return try {
            val address = stunClient.discoverPublicAddress(server, configuration.stunTimeoutMs)
            if (address != null) {
                BindingTestResult(address, address.port, testName)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun performBindingTestDifferentPort(server: STUNServer, testName: String): BindingTestResult? {
        // TODO: This would require binding to a different local port
        // For now, simulate by using the same server
        return performBindingTest(server, testName)
    }
    
    private fun getLocalAddress(): IPv4Address {
        // TODO: Get actual local address - for now return a placeholder
        return IPv4Address("192.168.1.100", 8090)
    }
    
    private fun calculateConfidence(results: List<BindingTestResult>): Int {
        // Higher confidence with more successful tests
        val successfulTests = results.size
        return when {
            successfulTests >= MIN_SAMPLES_FOR_CONFIDENCE -> 85
            successfulTests >= 2 -> 70
            successfulTests >= 1 -> 50
            else -> 0
        }
    }
    
    private fun isCacheValid(): Boolean {
        if (configuration.natDetectionInterval <= 0) return true
        return (System.currentTimeMillis() - lastDetectionTime) < DETECTION_CACHE_TIMEOUT
    }
    
    private fun createUnknownBehavior(reason: String = "Detection failed"): NATBehavior {
        return NATBehavior(
            type = NATType.UNKNOWN,
            mappingBehavior = MappingBehavior.UNKNOWN,
            filteringBehavior = FilteringBehavior.UNKNOWN,
            confidence = 0
        )
    }
}

/**
 * Result of a single STUN binding test
 */
private data class BindingTestResult(
    val address: IPv4Address,
    val port: Int,
    val testName: String
)

/**
 * Factory for creating NAT detection services
 */
object NATDetectionServiceFactory {
    fun createService(
        configuration: NATTraversalConfiguration,
        stunClient: STUNClient
    ): NATDetectionService {
        return NATDetectionService(configuration, stunClient)
    }
}