package com.bigshotsoftware.thenet.p2p.discovery

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Kademlia routing table implementation using k-buckets.
 * 
 * The routing table organizes nodes into buckets based on their XOR distance from the local node.
 * Each bucket can hold up to k nodes, and nodes are organized by distance prefix.
 */
class RoutingTable(
    private val localNodeId: ByteArray,
    private val k: Int = 20
) {
    companion object {
        private const val ID_LENGTH_BITS = 160 // 20 bytes * 8 bits
    }

    private val buckets = Array(ID_LENGTH_BITS) { KBucket(k) }
    private val mutex = Mutex()

    /**
     * K-bucket implementation for storing nodes at a specific distance range
     */
    private class KBucket(private val k: Int) {
        private val nodes = mutableListOf<KademliaDHT.DHTNode>()
        private val lastAccessed = mutableMapOf<ByteArray, Long>()

        fun addNode(node: KademliaDHT.DHTNode): Boolean {
            // Check if node already exists
            val existingIndex = nodes.indexOfFirst { it.nodeId.contentEquals(node.nodeId) }
            
            if (existingIndex != -1) {
                // Node exists, move to end (most recently seen)
                nodes.removeAt(existingIndex)
                nodes.add(node)
                lastAccessed[node.nodeId] = System.currentTimeMillis()
                return true
            }

            if (nodes.size < k) {
                // Bucket has space, add node
                nodes.add(node)
                lastAccessed[node.nodeId] = System.currentTimeMillis()
                return true
            } else {
                // Bucket is full, check if we should replace the least recently seen node
                val oldestNode = nodes.minByOrNull { lastAccessed[it.nodeId] ?: 0L }
                if (oldestNode != null && !oldestNode.isAlive) {
                    // Replace dead node
                    nodes.remove(oldestNode)
                    lastAccessed.remove(oldestNode.nodeId)
                    nodes.add(node)
                    lastAccessed[node.nodeId] = System.currentTimeMillis()
                    return true
                }
                return false
            }
        }

        fun removeNode(nodeId: ByteArray): Boolean {
            val index = nodes.indexOfFirst { it.nodeId.contentEquals(nodeId) }
            if (index != -1) {
                val removedNode = nodes.removeAt(index)
                lastAccessed.remove(removedNode.nodeId)
                return true
            }
            return false
        }

        fun getNodes(): List<KademliaDHT.DHTNode> = nodes.toList()

        fun size(): Int = nodes.size

        fun isFull(): Boolean = nodes.size >= k

        fun getOldestNode(): KademliaDHT.DHTNode? = 
            nodes.minByOrNull { lastAccessed[it.nodeId] ?: 0L }

        fun updateLastSeen(nodeId: ByteArray) {
            if (nodes.any { it.nodeId.contentEquals(nodeId) }) {
                lastAccessed[nodeId] = System.currentTimeMillis()
            }
        }
    }

    /**
     * Add a node to the appropriate bucket
     */
    suspend fun addNode(node: KademliaDHT.DHTNode): Boolean = mutex.withLock {
        if (node.nodeId.contentEquals(localNodeId)) {
            return@withLock false // Don't add ourselves
        }

        val bucketIndex = getBucketIndex(node.nodeId)
        return@withLock buckets[bucketIndex].addNode(node)
    }

    /**
     * Remove a node from the routing table
     */
    suspend fun removeNode(nodeId: ByteArray): Boolean = mutex.withLock {
        val bucketIndex = getBucketIndex(nodeId)
        return@withLock buckets[bucketIndex].removeNode(nodeId)
    }

    /**
     * Find the closest nodes to a given key
     */
    suspend fun getClosestNodes(key: ByteArray, count: Int): List<KademliaDHT.DHTNode> = mutex.withLock {
        val allNodes = mutableListOf<Pair<KademliaDHT.DHTNode, ULong>>()
        
        // Collect all nodes with their distances
        for (bucket in buckets) {
            for (node in bucket.getNodes()) {
                val distance = calculateDistance(key, node.nodeId)
                allNodes.add(node to distance)
            }
        }

        // Sort by distance and return closest nodes
        return@withLock allNodes
            .sortedBy { it.second }
            .take(count)
            .map { it.first }
    }

    /**
     * Get all nodes in the routing table
     */
    suspend fun getAllNodes(): List<KademliaDHT.DHTNode> = mutex.withLock {
        val allNodes = mutableListOf<KademliaDHT.DHTNode>()
        for (bucket in buckets) {
            allNodes.addAll(bucket.getNodes())
        }
        return@withLock allNodes
    }

    /**
     * Get total number of nodes in the routing table
     */
    suspend fun size(): Int = mutex.withLock {
        buckets.sumOf { it.size() }
    }

    /**
     * Get number of active buckets (buckets with at least one node)
     */
    suspend fun activeBucketCount(): Int = mutex.withLock {
        buckets.count { it.size() > 0 }
    }

    /**
     * Get nodes from a specific bucket index
     */
    suspend fun getBucketNodes(bucketIndex: Int): List<KademliaDHT.DHTNode> = mutex.withLock {
        if (bucketIndex in 0 until ID_LENGTH_BITS) {
            buckets[bucketIndex].getNodes()
        } else {
            emptyList()
        }
    }

    /**
     * Update last seen time for a node
     */
    suspend fun updateLastSeen(nodeId: ByteArray) = mutex.withLock {
        val bucketIndex = getBucketIndex(nodeId)
        buckets[bucketIndex].updateLastSeen(nodeId)
    }

    /**
     * Get buckets that need refreshing (haven't been accessed recently)
     */
    suspend fun getBucketsNeedingRefresh(maxAge: Long): List<Int> = mutex.withLock {
        val currentTime = System.currentTimeMillis()
        val bucketsToRefresh = mutableListOf<Int>()
        
        for (i in buckets.indices) {
            val bucket = buckets[i]
            if (bucket.size() > 0) {
                val oldestNode = bucket.getOldestNode()
                if (oldestNode != null && (currentTime - oldestNode.lastSeen) > maxAge) {
                    bucketsToRefresh.add(i)
                }
            }
        }
        
        return@withLock bucketsToRefresh
    }

    /**
     * Calculate the appropriate bucket index for a node ID
     */
    private fun getBucketIndex(nodeId: ByteArray): Int {
        val distance = calculateDistance(localNodeId, nodeId)
        
        // Find the position of the most significant bit
        if (distance == 0UL) {
            return ID_LENGTH_BITS - 1
        }
        
        // Count leading zeros to find the bit position
        val leadingZeros = distance.countLeadingZeroBits()
        val bitPosition = 64 - leadingZeros - 1 // 64-bit ULong
        
        return min(bitPosition, ID_LENGTH_BITS - 1)
    }

    /**
     * Calculate XOR distance between two node IDs
     */
    private fun calculateDistance(id1: ByteArray, id2: ByteArray): ULong {
        require(id1.size == id2.size) { "Node IDs must be same length" }
        
        val distance = ByteArray(id1.size)
        for (i in id1.indices) {
            distance[i] = (id1[i].toInt() xor id2[i].toInt()).toByte()
        }
        
        return distance.fold(0UL) { acc, byte -> (acc shl 8) or byte.toUByte().toULong() }
    }
}