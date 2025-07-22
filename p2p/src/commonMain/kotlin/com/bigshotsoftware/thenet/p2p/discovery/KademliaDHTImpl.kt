package com.bigshotsoftware.thenet.p2p.discovery

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Implementation of the Kademlia Distributed Hash Table (DHT) for peer discovery.
 * 
 * This implementation provides:
 * - Distributed peer discovery using Kademlia protocol
 * - Key-value storage across the network
 * - Automatic routing table maintenance
 * - Recursive lookups for efficient peer finding
 */
class KademliaDHTImpl(
    override val config: KademliaDHT.KademliaConfig
) : KademliaDHT {

    override val localNode: KademliaDHT.DHTNode = KademliaDHT.DHTNode(
        nodeId = config.nodeId,
        address = "0.0.0.0", // Will be updated when networking starts
        port = 0
    )

    private val routingTable = RoutingTable(localNode.nodeId, config.k)
    private val dataStore = mutableMapOf<String, Pair<ByteArray, Long>>() // key -> (value, timestamp)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<KademliaDHT.DHTMessage>>()
    private val messageHandlers = mutableListOf<(KademliaDHT.DHTMessage) -> Unit>()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    
    // State flows for observing DHT state
    private val _routingTableSize = MutableStateFlow(0)
    override val routingTableSize: StateFlow<Int> = _routingTableSize.asStateFlow()
    
    private val _discoveredNodes = MutableStateFlow<List<KademliaDHT.DHTNode>>(emptyList())
    override val discoveredNodes: StateFlow<List<KademliaDHT.DHTNode>> = _discoveredNodes.asStateFlow()
    
    private val _statistics = MutableStateFlow(KademliaDHT.DHTStatistics())
    override val statistics: StateFlow<KademliaDHT.DHTStatistics> = _statistics.asStateFlow()

    private var refreshJob: Job? = null
    private var republishJob: Job? = null
    private var isRunning = false

    override suspend fun start(config: KademliaDHT.KademliaConfig) {
        if (isRunning) return
        
        isRunning = true
        
        // Start periodic maintenance tasks
        startMaintenanceTasks()
        
        // Update statistics
        updateStatistics()
    }

    override suspend fun stop() {
        if (!isRunning) return
        
        isRunning = false
        
        // Cancel maintenance tasks
        refreshJob?.cancel()
        republishJob?.cancel()
        
        // Cancel all pending requests
        mutex.withLock {
            pendingRequests.values.forEach { deferred ->
                deferred.cancel()
            }
            pendingRequests.clear()
        }
        
        scope.cancel()
    }

    override suspend fun store(key: ByteArray, value: ByteArray): Boolean {
        val keyString = key.toHexString()
        
        // Store locally
        mutex.withLock {
            dataStore[keyString] = value to System.currentTimeMillis()
        }
        
        // Find closest nodes and store on them
        val closestNodes = findNode(key).nodes.take(config.k)
        var successCount = 0
        
        for (node in closestNodes) {
            try {
                val storeMessage = KademliaDHT.DHTMessage(
                    operation = KademliaDHT.Operation.STORE,
                    requestId = generateRequestId(),
                    sourceNodeId = localNode.nodeId,
                    targetNodeId = node.nodeId,
                    key = key,
                    value = value
                )
                
                if (sendMessage(node, storeMessage)) {
                    successCount++
                }
            } catch (e: Exception) {
                // Continue with other nodes if one fails
            }
        }
        
        updateStatistics { copy(totalStores = totalStores + 1) }
        
        return successCount > 0
    }

    override suspend fun findValue(key: ByteArray): KademliaDHT.LookupResult {
        val keyString = key.toHexString()
        
        // Check local store first
        mutex.withLock {
            dataStore[keyString]?.let { (value, _) ->
                return KademliaDHT.LookupResult(
                    nodes = emptyList(),
                    value = value,
                    found = true
                )
            }
        }
        
        // Perform iterative lookup
        val result = performIterativeLookup(key, KademliaDHT.Operation.FIND_VALUE)
        
        updateStatistics { 
            copy(
                totalFinds = totalFinds + 1,
                successfulLookups = if (result.found) successfulLookups + 1 else successfulLookups
            )
        }
        
        return result
    }

    override suspend fun findNode(nodeId: ByteArray): KademliaDHT.LookupResult {
        val result = performIterativeLookup(nodeId, KademliaDHT.Operation.FIND_NODE)
        
        updateStatistics { copy(totalLookups = totalLookups + 1) }
        
        return result
    }

    override suspend fun ping(node: KademliaDHT.DHTNode): Boolean {
        return try {
            val pingMessage = KademliaDHT.DHTMessage(
                operation = KademliaDHT.Operation.PING,
                requestId = generateRequestId(),
                sourceNodeId = localNode.nodeId,
                targetNodeId = node.nodeId
            )
            
            withTimeout(config.pingTimeout) {
                val responseDeferred = CompletableDeferred<KademliaDHT.DHTMessage>()
                mutex.withLock {
                    pendingRequests[pingMessage.requestId] = responseDeferred
                }
                
                if (sendMessage(node, pingMessage)) {
                    val response = responseDeferred.await()
                    routingTable.updateLastSeen(node.nodeId)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun addNode(node: KademliaDHT.DHTNode): Boolean {
        if (node.nodeId.contentEquals(localNode.nodeId)) {
            return false
        }
        
        scope.launch {
            if (routingTable.addNode(node)) {
                updateDiscoveredNodes()
                updateStatistics()
            }
        }
        
        return true
    }

    override fun removeNode(nodeId: ByteArray): Boolean {
        scope.launch {
            if (routingTable.removeNode(nodeId)) {
                updateDiscoveredNodes()
                updateStatistics()
            }
        }
        
        return true
    }

    override fun getClosestNodes(key: ByteArray, count: Int): List<KademliaDHT.DHTNode> {
        return runBlocking {
            routingTable.getClosestNodes(key, count)
        }
    }

    override suspend fun bootstrap(bootstrapNodes: List<KademliaDHT.DHTNode>) {
        for (node in bootstrapNodes) {
            addNode(node)
        }
        
        // Perform lookup for our own node ID to populate routing table
        if (bootstrapNodes.isNotEmpty()) {
            findNode(localNode.nodeId)
        }
    }

    override suspend fun refreshBuckets() {
        val bucketsToRefresh = routingTable.getBucketsNeedingRefresh(config.bucketRefreshInterval)
        
        for (bucketIndex in bucketsToRefresh) {
            // Generate a random key for this bucket
            val randomKey = generateRandomKeyForBucket(bucketIndex)
            findNode(randomKey)
        }
    }

    override fun registerMessageHandler(handler: (KademliaDHT.DHTMessage) -> Unit) {
        messageHandlers.add(handler)
    }

    override suspend fun sendMessage(node: KademliaDHT.DHTNode, message: KademliaDHT.DHTMessage): Boolean {
        // This would integrate with the actual network transport layer
        // For now, we'll simulate message sending
        return try {
            // TODO: Integrate with IPv8 transport layer
            // For now, just notify message handlers for local testing
            if (message.isResponse) {
                handleIncomingMessage(message)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Handle incoming DHT messages
     */
    suspend fun handleIncomingMessage(message: KademliaDHT.DHTMessage) {
        // Check if this is a response to a pending request
        if (message.isResponse) {
            mutex.withLock {
                pendingRequests.remove(message.requestId)?.complete(message)
            }
            return
        }

        // Handle incoming requests
        val response = when (message.operation) {
            KademliaDHT.Operation.PING -> {
                handlePingRequest(message)
            }
            KademliaDHT.Operation.FIND_NODE -> {
                handleFindNodeRequest(message)
            }
            KademliaDHT.Operation.FIND_VALUE -> {
                handleFindValueRequest(message)
            }
            KademliaDHT.Operation.STORE -> {
                handleStoreRequest(message)
            }
        }

        // Send response
        if (response != null) {
            val sourceNode = KademliaDHT.DHTNode(
                nodeId = message.sourceNodeId,
                address = "unknown", // Would be filled from network layer
                port = 0
            )
            sendMessage(sourceNode, response)
        }

        // Add source node to routing table
        val sourceNode = KademliaDHT.DHTNode(
            nodeId = message.sourceNodeId,
            address = "unknown",
            port = 0
        )
        addNode(sourceNode)

        // Notify message handlers
        messageHandlers.forEach { handler ->
            try {
                handler(message)
            } catch (e: Exception) {
                // Continue with other handlers if one fails
            }
        }
    }

    private fun handlePingRequest(message: KademliaDHT.DHTMessage): KademliaDHT.DHTMessage {
        return KademliaDHT.DHTMessage(
            operation = KademliaDHT.Operation.PING,
            requestId = message.requestId,
            sourceNodeId = localNode.nodeId,
            targetNodeId = message.sourceNodeId,
            isResponse = true
        )
    }

    private suspend fun handleFindNodeRequest(message: KademliaDHT.DHTMessage): KademliaDHT.DHTMessage {
        val targetKey = message.targetNodeId
        val closestNodes = routingTable.getClosestNodes(targetKey, config.k)
        
        return KademliaDHT.DHTMessage(
            operation = KademliaDHT.Operation.FIND_NODE,
            requestId = message.requestId,
            sourceNodeId = localNode.nodeId,
            targetNodeId = message.sourceNodeId,
            nodes = closestNodes,
            isResponse = true
        )
    }

    private suspend fun handleFindValueRequest(message: KademliaDHT.DHTMessage): KademliaDHT.DHTMessage {
        val key = message.key ?: return handleFindNodeRequest(message)
        val keyString = key.toHexString()
        
        // Check if we have the value
        val storedValue = mutex.withLock { dataStore[keyString]?.first }
        
        return if (storedValue != null) {
            KademliaDHT.DHTMessage(
                operation = KademliaDHT.Operation.FIND_VALUE,
                requestId = message.requestId,
                sourceNodeId = localNode.nodeId,
                targetNodeId = message.sourceNodeId,
                key = key,
                value = storedValue,
                isResponse = true
            )
        } else {
            // Return closest nodes instead
            val closestNodes = routingTable.getClosestNodes(key, config.k)
            KademliaDHT.DHTMessage(
                operation = KademliaDHT.Operation.FIND_VALUE,
                requestId = message.requestId,
                sourceNodeId = localNode.nodeId,
                targetNodeId = message.sourceNodeId,
                key = key,
                nodes = closestNodes,
                isResponse = true
            )
        }
    }

    private suspend fun handleStoreRequest(message: KademliaDHT.DHTMessage): KademliaDHT.DHTMessage? {
        val key = message.key
        val value = message.value
        
        if (key != null && value != null) {
            val keyString = key.toHexString()
            mutex.withLock {
                dataStore[keyString] = value to System.currentTimeMillis()
            }
        }
        
        return KademliaDHT.DHTMessage(
            operation = KademliaDHT.Operation.STORE,
            requestId = message.requestId,
            sourceNodeId = localNode.nodeId,
            targetNodeId = message.sourceNodeId,
            isResponse = true
        )
    }

    private suspend fun performIterativeLookup(
        targetKey: ByteArray, 
        operation: KademliaDHT.Operation
    ): KademliaDHT.LookupResult {
        val shortlist = mutableSetOf<KademliaDHT.DHTNode>()
        val queried = mutableSetOf<ByteArray>()
        val closest = getClosestNodes(targetKey, config.alpha)
        
        shortlist.addAll(closest)
        
        while (true) {
            val unqueriedNodes = shortlist.filter { node ->
                queried.none { it.contentEquals(node.nodeId) }
            }.take(config.alpha)
            
            if (unqueriedNodes.isEmpty()) break
            
            val responses = unqueriedNodes.map { node ->
                scope.async {
                    queried.add(node.nodeId)
                    try {
                        queryNode(node, targetKey, operation)
                    } catch (e: Exception) {
                        null
                    }
                }
            }.map { it.await() }
            
            var foundValue: ByteArray? = null
            
            for (response in responses) {
                if (response != null) {
                    if (response.found && response.value != null) {
                        foundValue = response.value
                        break
                    }
                    shortlist.addAll(response.nodes)
                }
            }
            
            if (foundValue != null) {
                return KademliaDHT.LookupResult(
                    nodes = shortlist.toList(),
                    value = foundValue,
                    found = true
                )
            }
        }
        
        return KademliaDHT.LookupResult(
            nodes = shortlist.toList().sortedBy { node ->
                calculateDistance(targetKey, node.nodeId)
            }.take(config.k),
            found = false
        )
    }

    private suspend fun queryNode(
        node: KademliaDHT.DHTNode,
        targetKey: ByteArray,
        operation: KademliaDHT.Operation
    ): KademliaDHT.LookupResult? {
        val message = KademliaDHT.DHTMessage(
            operation = operation,
            requestId = generateRequestId(),
            sourceNodeId = localNode.nodeId,
            targetNodeId = targetKey,
            key = if (operation == KademliaDHT.Operation.FIND_VALUE) targetKey else null
        )
        
        return try {
            withTimeout(config.pingTimeout) {
                val responseDeferred = CompletableDeferred<KademliaDHT.DHTMessage>()
                mutex.withLock {
                    pendingRequests[message.requestId] = responseDeferred
                }
                
                if (sendMessage(node, message)) {
                    val response = responseDeferred.await()
                    KademliaDHT.LookupResult(
                        nodes = response.nodes,
                        value = response.value,
                        found = response.value != null
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun startMaintenanceTasks() {
        // Periodic bucket refresh
        refreshJob = scope.launch {
            while (isActive) {
                delay(config.bucketRefreshInterval)
                try {
                    refreshBuckets()
                } catch (e: Exception) {
                    // Continue with next refresh cycle
                }
            }
        }
        
        // Periodic data republishing
        republishJob = scope.launch {
            while (isActive) {
                delay(config.republishInterval)
                try {
                    republishData()
                } catch (e: Exception) {
                    // Continue with next republish cycle
                }
            }
        }
    }

    private suspend fun republishData() {
        val currentTime = System.currentTimeMillis()
        val dataToRepublish = mutex.withLock {
            dataStore.filter { (_, timestampPair) ->
                currentTime - timestampPair.second < config.expireInterval
            }
        }
        
        for ((keyString, valuePair) in dataToRepublish) {
            val key = keyString.hexToByteArray()
            store(key, valuePair.first)
        }
    }

    private suspend fun updateDiscoveredNodes() {
        val allNodes = routingTable.getAllNodes()
        _discoveredNodes.value = allNodes
    }

    private suspend fun updateStatistics() {
        val totalNodes = routingTable.size()
        val activeBuckets = routingTable.activeBucketCount()
        
        _routingTableSize.value = totalNodes
        _statistics.value = _statistics.value.copy(
            totalNodes = totalNodes,
            activeBuckets = activeBuckets
        )
    }

    private inline fun updateStatistics(update: KademliaDHT.DHTStatistics.() -> KademliaDHT.DHTStatistics) {
        _statistics.value = _statistics.value.update()
    }

    private fun generateRequestId(): String = Random.nextBytes(8).toHexString()

    private fun generateRandomKeyForBucket(bucketIndex: Int): ByteArray {
        // Generate a random key that would fall into the specified bucket
        val randomKey = Random.nextBytes(20)
        // TODO: Implement proper bucket targeting logic
        return randomKey
    }

    private fun calculateDistance(id1: ByteArray, id2: ByteArray): ULong {
        require(id1.size == id2.size) { "Node IDs must be same length" }
        val distance = ByteArray(id1.size)
        for (i in id1.indices) {
            distance[i] = (id1[i].toInt() xor id2[i].toInt()).toByte()
        }
        return distance.fold(0UL) { acc, byte -> (acc shl 8) or byte.toUByte().toULong() }
    }
}

// Extension functions for ByteArray conversion
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}