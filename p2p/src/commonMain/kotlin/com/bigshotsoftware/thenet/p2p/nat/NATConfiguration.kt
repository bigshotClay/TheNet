package com.bigshotsoftware.thenet.p2p.nat

import kotlinx.serialization.Serializable

/**
 * Configuration for NAT traversal strategies.
 * This is a placeholder implementation for compilation purposes.
 */
data class NATTraversalConfiguration(
    val enablePortPrediction: Boolean = false,
    val stunServers: List<STUNServer> = emptyList(),
    val stunTimeoutMs: Long = 5000L,
    val enableRelayFallback: Boolean = false,
    val turnServers: List<TURNServer> = emptyList(),
    val maxPuncturingAttempts: Int = 10,
    val natDetectionInterval: Long = 300000L, // 5 minutes
    val enableNATDetection: Boolean = true,
    val turnTimeoutMs: Long = 10000L,
    val mode: NATTraversalMode = NATTraversalMode.AUTO
)



