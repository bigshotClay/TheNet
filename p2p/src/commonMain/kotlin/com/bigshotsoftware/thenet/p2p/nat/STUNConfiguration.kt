package com.bigshotsoftware.thenet.p2p.nat

import kotlinx.serialization.Serializable

/**
 * Configuration for STUN (Session Traversal Utilities for NAT) servers.
 * STUN servers help clients discover their public IP address and NAT type.
 */
@Serializable
data class STUNServer(
    /**
     * STUN server hostname or IP address
     */
    val host: String,
    
    /**
     * STUN server port (default is 3478 for STUN, 5349 for STUNS)
     */
    val port: Int = 3478,
    
    /**
     * Use TLS/DTLS for secure STUN communication
     */
    val secure: Boolean = false,
    
    /**
     * Username for authenticated STUN servers (optional)
     */
    val username: String? = null,
    
    /**
     * Password/credential for authenticated STUN servers (optional)
     */
    val credential: String? = null,
    
    /**
     * Server priority for selection (higher = preferred)
     */
    val priority: Int = 100,
    
    /**
     * Enable this STUN server
     */
    val enabled: Boolean = true
)

/**
 * Configuration for TURN (Traversal Using Relays around NAT) servers.
 * TURN servers act as relays when direct P2P connection fails.
 */
@Serializable
data class TURNServer(
    /**
     * TURN server hostname or IP address
     */
    val host: String,
    
    /**
     * TURN server port (default is 3478 for TURN, 5349 for TURNS)
     */
    val port: Int = 3478,
    
    /**
     * Use TLS/DTLS for secure TURN communication
     */
    val secure: Boolean = false,
    
    /**
     * Username for TURN server authentication (required for TURN)
     */
    val username: String,
    
    /**
     * Password/credential for TURN server authentication (required for TURN)
     */
    val credential: String,
    
    /**
     * Server priority for selection (higher = preferred)
     */
    val priority: Int = 100,
    
    /**
     * Enable this TURN server
     */
    val enabled: Boolean = true,
    
    /**
     * Maximum bandwidth allocation in bytes/second (0 = unlimited)
     */
    val maxBandwidth: Long = 0
)

/**
 * NAT traversal mode configuration
 */
@Serializable
enum class NATTraversalMode {
    /**
     * Automatically select best NAT traversal strategy
     */
    AUTO,
    
    /**
     * Only use direct connections (no NAT traversal)
     */
    DIRECT_ONLY,
    
    /**
     * Use STUN-assisted hole punching
     */
    STUN_ASSISTED,
    
    /**
     * Use TURN relay as fallback
     */
    TURN_RELAY,
    
    /**
     * Use all available NAT traversal methods
     */
    FULL_TRAVERSAL
}

/**
 * Enhanced NAT type classification following RFC 3489/5389
 */
@Serializable
enum class NATType {
    /**
     * No NAT - direct internet connection
     */
    PUBLIC,
    
    /**
     * Full Cone NAT - one-to-one mapping, no restrictions
     */
    FULL_CONE,
    
    /**
     * Restricted Cone NAT - one-to-one mapping, restricted by source IP
     */
    RESTRICTED_CONE,
    
    /**
     * Port Restricted Cone NAT - one-to-one mapping, restricted by source IP:port
     */
    PORT_RESTRICTED_CONE,
    
    /**
     * Symmetric NAT - different mapping for different destinations
     */
    SYMMETRIC,
    
    /**
     * Unable to determine NAT type
     */
    UNKNOWN,
    
    /**
     * Firewall blocking UDP traffic
     */
    UDP_BLOCKED
}

/**
 * NAT behavior characteristics
 */
@Serializable
data class NATBehavior(
    /**
     * Primary NAT type classification
     */
    val type: NATType,
    
    /**
     * Address mapping behavior
     */
    val mappingBehavior: MappingBehavior,
    
    /**
     * Filtering behavior for incoming packets
     */
    val filteringBehavior: FilteringBehavior,
    
    /**
     * Detected port mapping pattern (for symmetric NATs)
     */
    val portPattern: PortMappingPattern = PortMappingPattern.UNKNOWN,
    
    /**
     * Confidence level of detection (0-100)
     */
    val confidence: Int = 0
)

/**
 * NAT address mapping behavior
 */
@Serializable
enum class MappingBehavior {
    /**
     * Endpoint-Independent Mapping
     */
    ENDPOINT_INDEPENDENT,
    
    /**
     * Address-Dependent Mapping
     */
    ADDRESS_DEPENDENT,
    
    /**
     * Address and Port-Dependent Mapping
     */
    ADDRESS_PORT_DEPENDENT,
    
    /**
     * Unknown mapping behavior
     */
    UNKNOWN
}

/**
 * NAT filtering behavior for incoming packets
 */
@Serializable
enum class FilteringBehavior {
    /**
     * Endpoint-Independent Filtering
     */
    ENDPOINT_INDEPENDENT,
    
    /**
     * Address-Dependent Filtering
     */
    ADDRESS_DEPENDENT,
    
    /**
     * Address and Port-Dependent Filtering
     */
    ADDRESS_PORT_DEPENDENT,
    
    /**
     * Unknown filtering behavior
     */
    UNKNOWN
}

/**
 * Port mapping patterns observed in symmetric NATs
 */
@Serializable
enum class PortMappingPattern {
    /**
     * Sequential port allocation (predictable)
     */
    SEQUENTIAL,
    
    /**
     * Random port allocation (unpredictable)
     */
    RANDOM,
    
    /**
     * Fixed increment pattern
     */
    FIXED_INCREMENT,
    
    /**
     * Unknown or no clear pattern
     */
    UNKNOWN
}

/**
 * Default STUN servers for NAT discovery
 */
val defaultSTUNServers = listOf(
    STUNServer("stun.l.google.com", 19302, priority = 100),
    STUNServer("stun1.l.google.com", 19302, priority = 90),
    STUNServer("stun2.l.google.com", 19302, priority = 90),
    STUNServer("stun3.l.google.com", 19302, priority = 90),
    STUNServer("stun4.l.google.com", 19302, priority = 90),
    STUNServer("stun.cloudflare.com", 3478, priority = 80),
    STUNServer("stun.nextcloud.com", 3478, priority = 70)
)

/**
 * Default TURN servers (empty by default - requires authentication)
 */
val defaultTURNServers = emptyList<TURNServer>()