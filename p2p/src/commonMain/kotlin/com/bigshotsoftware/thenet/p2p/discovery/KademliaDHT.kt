package com.bigshotsoftware.thenet.p2p.discovery

import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * Kademlia Distributed Hash Table (DHT) interface for peer discovery and key-value storage.
 * 
 * Implements the Kademlia protocol for distributed peer discovery with:
 * - XOR-based distance metric for node organization
 * - K-bucket routing table for efficient peer lookup
 * - DHT operations: FIND_NODE, FIND_VALUE, STORE
 * - Automatic peer discovery through recursive lookups
 */
interface KademliaDHT {

    /**
     * Configuration for Kademlia DHT
     */
    data class KademliaConfig(
        val nodeId: ByteArray = generateRandomNodeId(),
        val k: Int = 20, // Bucket size (number of peers per bucket)
        val alpha: Int = 3, // Number of parallel lookups
        val bucketRefreshInterval: Long = 3600000, // 1 hour in milliseconds
        val republishInterval: Long = 3600000, // 1 hour in milliseconds
        val expireInterval: Long = 86400000, // 24 hours in milliseconds
        val pingTimeout: Long = 5000, // 5 seconds
        val maxRetries: Int = 3
    ) {
        companion object {
            fun generateRandomNodeId(): ByteArray = Random.nextBytes(20) // 160-bit node ID
        }
    }

    /**
     * DHT node information
     */
    data class DHTNode(
        val nodeId: ByteArray,
        val address: String,
        val port: Int,
        val lastSeen: Long = System.currentTimeMillis(),
        val isAlive: Boolean = true
    ) {
        /**
         * Calculate XOR distance between this node and another node
         */
        fun distanceTo(other: ByteArray): ULong {
            require(nodeId.size == other.size) { "Node IDs must be same length" }
            val distance = ByteArray(nodeId.size)
            for (i in nodeId.indices) {
                distance[i] = (nodeId[i].toInt() xor other[i].toInt()).toByte()
            }
            return distance.fold(0UL) { acc, byte -> (acc shl 8) or byte.toUByte().toULong() }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DHTNode) return false
            return nodeId.contentEquals(other.nodeId)
        }

        override fun hashCode(): Int {
            return nodeId.contentHashCode()
        }
    }

    /**
     * DHT lookup result
     */
    data class LookupResult(
        val nodes: List<DHTNode>,
        val value: ByteArray? = null,
        val found: Boolean = value != null
    )

    /**
     * DHT operation types
     */
    enum class Operation {
        FIND_NODE,
        FIND_VALUE,
        STORE,
        PING
    }

    /**
     * DHT message for network communication
     */
    data class DHTMessage(
        val operation: Operation,
        val requestId: String,
        val sourceNodeId: ByteArray,
        val targetNodeId: ByteArray,
        val key: ByteArray? = null,
        val value: ByteArray? = null,
        val nodes: List<DHTNode> = emptyList(),
        val isResponse: Boolean = false
    )

    /**
     * Current DHT configuration
     */
    val config: KademliaConfig

    /**
     * Local node information
     */
    val localNode: DHTNode

    /**
     * Number of nodes in the routing table
     */
    val routingTableSize: StateFlow<Int>

    /**
     * Recently discovered nodes
     */
    val discoveredNodes: StateFlow<List<DHTNode>>

    /**
     * DHT statistics
     */
    val statistics: StateFlow<DHTStatistics>

    /**
     * DHT statistics data class
     */
    data class DHTStatistics(
        val totalNodes: Int = 0,
        val activeBuckets: Int = 0,
        val totalLookups: Long = 0,
        val successfulLookups: Long = 0,
        val totalStores: Long = 0,
        val totalFinds: Long = 0,
        val averageLatency: Long = 0
    )

    /**
     * Start the DHT with the given configuration
     */
    suspend fun start(config: KademliaConfig)

    /**
     * Stop the DHT
     */
    suspend fun stop()

    /**
     * Store a key-value pair in the DHT
     */
    suspend fun store(key: ByteArray, value: ByteArray): Boolean

    /**
     * Find a value for the given key
     */
    suspend fun findValue(key: ByteArray): LookupResult

    /**
     * Find the closest nodes to the given node ID
     */
    suspend fun findNode(nodeId: ByteArray): LookupResult

    /**
     * Ping a node to check if it's alive
     */
    suspend fun ping(node: DHTNode): Boolean

    /**
     * Add a node to the routing table
     */
    fun addNode(node: DHTNode): Boolean

    /**
     * Remove a node from the routing table
     */
    fun removeNode(nodeId: ByteArray): Boolean

    /**
     * Get the closest nodes to a given key from the routing table
     */
    fun getClosestNodes(key: ByteArray, count: Int = config.k): List<DHTNode>

    /**
     * Bootstrap the DHT with initial nodes
     */
    suspend fun bootstrap(bootstrapNodes: List<DHTNode>)

    /**
     * Refresh buckets that haven't been accessed recently
     */
    suspend fun refreshBuckets()

    /**
     * Register a handler for incoming DHT messages
     */
    fun registerMessageHandler(handler: (DHTMessage) -> Unit)

    /**
     * Send a DHT message to a specific node
     */
    suspend fun sendMessage(node: DHTNode, message: DHTMessage): Boolean
}

