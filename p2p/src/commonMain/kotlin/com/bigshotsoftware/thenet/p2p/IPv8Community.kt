package com.bigshotsoftware.thenet.p2p

/**
 * Base interface for IPv8 communities in TheNet.
 *
 * Communities represent different services/protocols running on the IPv8 network.
 * Each community has its own overlay network and message handlers.
 */
interface IPv8Community {

    /**
     * Unique identifier for this community type
     */
    val communityId: String

    /**
     * Human-readable name for this community
     */
    val communityName: String

    /**
     * Version of this community implementation
     */
    val version: Int

    /**
     * Initialize the community
     */
    suspend fun initialize()

    /**
     * Handle incoming messages for this community
     */
    suspend fun handleMessage(peerId: String, messageType: String, payload: ByteArray)

    /**
     * Cleanup resources when community is being destroyed
     */
    suspend fun destroy()
}

/**
 * Discovery community for peer discovery and basic connectivity
 */
interface DiscoveryCommunity : IPv8Community {

    /**
     * Ping a specific peer to check connectivity
     */
    suspend fun pingPeer(peerId: String): Boolean

    /**
     * Request peer list from a specific peer
     */
    suspend fun requestPeerList(peerId: String): List<String>

    /**
     * Announce ourselves to the network
     */
    suspend fun announcePresence()
}

/**
 * TrustChain community for blockchain/ledger functionality
 */
interface TrustChainCommunity : IPv8Community {

    /**
     * Data structure for a TrustChain block
     */
    data class TrustChainBlock(
        val sequenceNumber: Long,
        val previousHash: String,
        val publicKey: String,
        val linkPublicKey: String?,
        val linkSequenceNumber: Long?,
        val transaction: Map<String, Any>,
        val timestamp: Long,
        val signature: ByteArray,
    )

    /**
     * Create a new block in the trust chain
     */
    suspend fun createBlock(
        transaction: Map<String, Any>,
        linkPublicKey: String? = null,
    ): TrustChainBlock

    /**
     * Request blocks from a peer
     */
    suspend fun requestBlocks(
        peerId: String,
        startSequence: Long = 0,
        endSequence: Long = Long.MAX_VALUE,
    ): List<TrustChainBlock>

    /**
     * Verify a block's validity
     */
    suspend fun verifyBlock(block: TrustChainBlock): Boolean

    /**
     * Get our current chain length
     */
    suspend fun getChainLength(): Long
}
