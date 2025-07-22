package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fallback strategy for handling bootstrap connection failures
 */
interface BootstrapFallbackStrategy {
    /**
     * Execute fallback logic when bootstrap connection fails
     * @return Alternative bootstrap nodes to try
     */
    suspend fun handleFailure(
        failedNodes: List<BootstrapNode>,
        availableNodes: List<BootstrapNode>,
    ): List<BootstrapNode>
}

/**
 * Exponential backoff with jitter fallback strategy
 */
class ExponentialBackoffStrategy(
    private val initialDelay: Duration = 1.seconds,
    private val maxDelay: Duration = 30.seconds,
    private val maxRetries: Int = 5,
) : BootstrapFallbackStrategy {

    private var currentRetry = 0
    private var currentDelay = initialDelay

    override suspend fun handleFailure(
        failedNodes: List<BootstrapNode>,
        availableNodes: List<BootstrapNode>,
    ): List<BootstrapNode> {
        if (currentRetry >= maxRetries) {
            // Reset for next cycle
            currentRetry = 0
            currentDelay = initialDelay
            return emptyList()
        }

        // Apply exponential backoff with jitter
        val jitter = (0..1000).random() / 1000.0
        val delayWithJitter = currentDelay.times(1 + jitter)
        delay(delayWithJitter)

        currentRetry++
        currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)

        // Return next set of available nodes
        return availableNodes.take(3)
    }
}

/**
 * DNS-based fallback strategy that queries DNS for new bootstrap nodes
 */
class DnsFallbackStrategy(
    private val dnsSeeds: List<String> = listOf(
        "_bootstrap._tcp.thenet.io",
        "_bootstrap._tcp.backup.thenet.io",
    ),
) : BootstrapFallbackStrategy {

    override suspend fun handleFailure(
        failedNodes: List<BootstrapNode>,
        availableNodes: List<BootstrapNode>,
    ): List<BootstrapNode> {
        // Try DNS-based discovery
        val discoveredPeers = performDnsDiscovery()

        return discoveredPeers.map { peer ->
            BootstrapNode(
                peer = peer,
                priority = 75, // Medium priority for DNS-discovered nodes
            )
        }
    }

    private suspend fun performDnsDiscovery(): List<BootstrapPeer> {
        // Placeholder for actual DNS SRV record lookup
        // In production, this would query DNS SRV records
        return listOf(
            BootstrapPeer("dns-bootstrap1.thenet.io", 8090),
            BootstrapPeer("dns-bootstrap2.thenet.io", 8090),
        )
    }
}

/**
 * Hardcoded fallback strategy with emergency bootstrap nodes
 */
class HardcodedFallbackStrategy : BootstrapFallbackStrategy {

    private val emergencyNodes = listOf(
        BootstrapPeer("emergency1.thenet.io", 8090),
        BootstrapPeer("emergency2.thenet.io", 8090),
        BootstrapPeer("emergency3.thenet.io", 8090),
        // IPv4 addresses as last resort
        BootstrapPeer("198.51.100.1", 8090),
        BootstrapPeer("203.0.113.1", 8090),
    )

    override suspend fun handleFailure(
        failedNodes: List<BootstrapNode>,
        availableNodes: List<BootstrapNode>,
    ): List<BootstrapNode> {
        // Filter out already failed nodes
        val failedHosts = failedNodes.map { it.peer.host }.toSet()

        return emergencyNodes
            .filter { it.host !in failedHosts }
            .map { peer ->
                BootstrapNode(
                    peer = peer,
                    priority = 50, // Lower priority for emergency nodes
                )
            }
    }
}

/**
 * Composite fallback strategy that tries multiple strategies in sequence
 */
class CompositeFallbackStrategy(
    private val strategies: List<BootstrapFallbackStrategy>,
) : BootstrapFallbackStrategy {

    override suspend fun handleFailure(
        failedNodes: List<BootstrapNode>,
        availableNodes: List<BootstrapNode>,
    ): List<BootstrapNode> {
        val allFallbackNodes = mutableListOf<BootstrapNode>()

        for (strategy in strategies) {
            val fallbackNodes = strategy.handleFailure(failedNodes, availableNodes)
            if (fallbackNodes.isNotEmpty()) {
                allFallbackNodes.addAll(fallbackNodes)
                // If we have enough nodes, stop trying other strategies
                if (allFallbackNodes.size >= 3) {
                    break
                }
            }
        }

        return allFallbackNodes.distinctBy { it.id }
    }
}

/**
 * Peer exchange fallback strategy that gets bootstrap nodes from connected peers
 */
class PeerExchangeFallbackStrategy : BootstrapFallbackStrategy {

    override suspend fun handleFailure(
        failedNodes: List<BootstrapNode>,
        availableNodes: List<BootstrapNode>,
    ): List<BootstrapNode> {
        // In a real implementation, this would query connected peers
        // for their known bootstrap nodes
        return emptyList()
    }
}
