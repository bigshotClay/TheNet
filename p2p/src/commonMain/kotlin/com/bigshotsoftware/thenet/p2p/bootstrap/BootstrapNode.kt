package com.bigshotsoftware.thenet.p2p.bootstrap

import com.bigshotsoftware.thenet.p2p.config.BootstrapPeer
import kotlinx.datetime.Instant

/**
 * Enhanced bootstrap node representation with health tracking and metadata
 */
data class BootstrapNode(
    val peer: BootstrapPeer,
    val id: String = "${peer.host}:${peer.port}",
    val region: String? = null,
    val priority: Int = 0,
    val maxConnections: Int = 1000,
    val protocolVersion: String = "1.0.0",
    val publicKey: ByteArray? = null,
    val lastHealthCheck: Instant? = null,
    val isHealthy: Boolean = true,
    val consecutiveFailures: Int = 0,
    val successfulConnections: Long = 0,
    val failedConnections: Long = 0,
    val averageResponseTime: Long = 0,
    val lastSuccessfulConnection: Instant? = null,
) {
    val successRate: Double
        get() = if (successfulConnections + failedConnections > 0) {
            successfulConnections.toDouble() / (successfulConnections + failedConnections)
        } else {
            0.0
        }

    val isAvailable: Boolean
        get() = isHealthy && consecutiveFailures < MAX_CONSECUTIVE_FAILURES

    fun withHealthUpdate(
        healthy: Boolean,
        responseTime: Long? = null,
        timestamp: Instant,
    ): BootstrapNode {
        return if (healthy) {
            copy(
                isHealthy = true,
                consecutiveFailures = 0,
                successfulConnections = successfulConnections + 1,
                lastHealthCheck = timestamp,
                lastSuccessfulConnection = timestamp,
                averageResponseTime = if (responseTime != null && averageResponseTime > 0) {
                    ((averageResponseTime * successfulConnections) + responseTime) / (successfulConnections + 1)
                } else {
                    responseTime ?: averageResponseTime
                },
            )
        } else {
            copy(
                isHealthy = false,
                consecutiveFailures = consecutiveFailures + 1,
                failedConnections = failedConnections + 1,
                lastHealthCheck = timestamp,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BootstrapNode

        if (peer != other.peer) return false
        if (id != other.id) return false
        if (publicKey != null) {
            if (other.publicKey == null) return false
            if (!publicKey.contentEquals(other.publicKey)) return false
        } else if (other.publicKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = peer.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (publicKey?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val MAX_CONSECUTIVE_FAILURES = 3
    }
}
