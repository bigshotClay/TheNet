package com.bigshotsoftware.thenet.p2p.auth

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

/**
 * Extended IPv8Manager interface that includes peer authentication capabilities.
 * 
 * This interface combines standard IPv8 networking functionality with secure
 * peer authentication, providing a unified API for authenticated P2P communication.
 */
interface AuthenticatedIPv8Manager : IPv8Manager {

    /**
     * The peer authentication component
     */
    val peerAuthentication: PeerAuthentication

    /**
     * Extended peer information including authentication status
     */
    data class AuthenticatedPeerInfo(
        val peerId: String,
        val address: String,
        val port: Int,
        val isConnected: Boolean,
        val lastSeen: Long,
        val authStatus: PeerAuthentication.AuthStatus,
        val isAuthenticated: Boolean,
        val sessionId: String?,
        val publicKey: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthenticatedPeerInfo) return false
            return peerId == other.peerId &&
                address == other.address &&
                port == other.port &&
                isConnected == other.isConnected &&
                authStatus == other.authStatus &&
                isAuthenticated == other.isAuthenticated &&
                sessionId == other.sessionId &&
                publicKey?.contentEquals(other.publicKey ?: ByteArray(0)) == true
        }

        override fun hashCode(): Int {
            var result = peerId.hashCode()
            result = 31 * result + address.hashCode()
            result = 31 * result + port
            result = 31 * result + isConnected.hashCode()
            result = 31 * result + authStatus.hashCode()
            result = 31 * result + isAuthenticated.hashCode()
            result = 31 * result + (sessionId?.hashCode() ?: 0)
            result = 31 * result + (publicKey?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Authentication configuration
     */
    data class AuthConfig(
        val requireAuthentication: Boolean = false,
        val sessionTimeout: Long = 3600000L, // 1 hour
        val challengeTimeout: Long = 60000L, // 1 minute
        val maxAuthAttempts: Int = 3,
        val autoCleanupInterval: Long = 300000L // 5 minutes
    )

    /**
     * List of peers with authentication information
     */
    val authenticatedPeers: StateFlow<List<AuthenticatedPeerInfo>>

    /**
     * Number of authenticated connections
     */
    val authenticatedConnectionCount: StateFlow<Int>

    /**
     * Start IPv8 networking with authentication enabled
     */
    suspend fun startWithAuth(config: IPv8Manager.IPv8Config, authConfig: AuthConfig = AuthConfig())

    /**
     * Authenticate with a specific peer
     */
    suspend fun authenticatePeer(peerId: String): Boolean

    /**
     * Send message to authenticated peer only
     */
    suspend fun sendAuthenticatedMessage(peerId: String, message: ByteArray): Boolean

    /**
     * Broadcast message to all authenticated peers only
     */
    suspend fun broadcastToAuthenticated(message: ByteArray): Int

    /**
     * Get authentication status for a peer
     */
    suspend fun getAuthStatus(peerId: String): PeerAuthentication.AuthStatus

    /**
     * Revoke authentication for a peer
     */
    suspend fun revokeAuthentication(peerId: String): Boolean

    /**
     * Get list of peers requiring authentication
     */
    suspend fun getPendingAuthPeers(): List<String>
}

/**
 * Implementation of AuthenticatedIPv8Manager that wraps a standard IPv8Manager
 * with authentication capabilities.
 */
class AuthenticatedIPv8ManagerImpl(
    private val baseManager: IPv8Manager
) : AuthenticatedIPv8Manager {

    override val peerAuthentication: PeerAuthentication = PeerAuthenticationImpl(baseManager)
    
    private var authConfig = AuthenticatedIPv8Manager.AuthConfig()
    private val scope = CoroutineScope(SupervisorJob())
    
    // Delegate standard IPv8Manager functionality
    override val networkStatus: StateFlow<IPv8Manager.NetworkStatus> = baseManager.networkStatus
    override val discoveredPeers: StateFlow<List<IPv8Manager.PeerInfo>> = baseManager.discoveredPeers
    override val connectionCount: StateFlow<Int> = baseManager.connectionCount

    // Authentication-specific state flows
    override val authenticatedPeers: StateFlow<List<AuthenticatedIPv8Manager.AuthenticatedPeerInfo>> =
        combine(
            baseManager.discoveredPeers,
            peerAuthentication.authenticatedPeers
        ) { peers, authPeers ->
            peers.map { peer ->
                val authPeer = authPeers.find { it.peerId == peer.peerId }
                AuthenticatedIPv8Manager.AuthenticatedPeerInfo(
                    peerId = peer.peerId,
                    address = peer.address,
                    port = peer.port,
                    isConnected = peer.isConnected,
                    lastSeen = peer.lastSeen,
                    authStatus = authPeer?.status ?: PeerAuthentication.AuthStatus.UNAUTHENTICATED,
                    isAuthenticated = authPeer?.status == PeerAuthentication.AuthStatus.AUTHENTICATED,
                    sessionId = authPeer?.sessionId,
                    publicKey = authPeer?.publicKey
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val authenticatedConnectionCount: StateFlow<Int> = peerAuthentication.authenticatedConnectionCount

    override suspend fun start(config: IPv8Manager.IPv8Config) {
        startWithAuth(config)
    }

    override suspend fun startWithAuth(
        config: IPv8Manager.IPv8Config,
        authConfig: AuthenticatedIPv8Manager.AuthConfig
    ) {
        this.authConfig = authConfig
        baseManager.start(config)
    }

    override suspend fun stop() {
        if (peerAuthentication is PeerAuthenticationImpl) {
            peerAuthentication.cleanup()
        }
        baseManager.stop()
    }

    override suspend fun sendMessage(peerId: String, message: ByteArray): Boolean {
        if (authConfig.requireAuthentication) {
            return sendAuthenticatedMessage(peerId, message)
        }
        return baseManager.sendMessage(peerId, message)
    }

    override suspend fun sendAuthenticatedMessage(peerId: String, message: ByteArray): Boolean {
        val authStatus = peerAuthentication.getAuthStatus(peerId)
        if (authStatus != PeerAuthentication.AuthStatus.AUTHENTICATED) {
            // Try to authenticate first
            val authenticated = authenticatePeer(peerId)
            if (!authenticated) {
                return false
            }
        }
        return baseManager.sendMessage(peerId, message)
    }

    override suspend fun broadcastMessage(message: ByteArray): Int {
        if (authConfig.requireAuthentication) {
            return broadcastToAuthenticated(message)
        }
        return baseManager.broadcastMessage(message)
    }

    override suspend fun broadcastToAuthenticated(message: ByteArray): Int {
        val authenticatedPeerIds = peerAuthentication.authenticatedPeers.value
            .filter { it.status == PeerAuthentication.AuthStatus.AUTHENTICATED }
            .map { it.peerId }
        
        var successCount = 0
        for (peerId in authenticatedPeerIds) {
            if (baseManager.sendMessage(peerId, message)) {
                successCount++
            }
        }
        return successCount
    }

    override suspend fun authenticatePeer(peerId: String): Boolean {
        return peerAuthentication.authenticatePeer(peerId)
    }

    override suspend fun getAuthStatus(peerId: String): PeerAuthentication.AuthStatus {
        return peerAuthentication.getAuthStatus(peerId)
    }

    override suspend fun revokeAuthentication(peerId: String): Boolean {
        return peerAuthentication.revokeAuthentication(peerId)
    }

    override suspend fun getPendingAuthPeers(): List<String> {
        return peerAuthentication.authenticatedPeers.value
            .filter { 
                it.status == PeerAuthentication.AuthStatus.PENDING_CHALLENGE ||
                it.status == PeerAuthentication.AuthStatus.PENDING_RESPONSE
            }
            .map { it.peerId }
    }

    override fun registerMessageHandler(messageType: String, handler: (peerId: String, message: ByteArray) -> Unit) {
        if (authConfig.requireAuthentication) {
            // Wrap handler to check authentication first
            val wrappedHandler = { peerId: String, message: ByteArray ->
                kotlinx.coroutines.runBlocking {
                    val authStatus = peerAuthentication.getAuthStatus(peerId)
                    if (authStatus == PeerAuthentication.AuthStatus.AUTHENTICATED) {
                        handler(peerId, message)
                    }
                    // Silently drop messages from unauthenticated peers if auth is required
                }
            }
            baseManager.registerMessageHandler(messageType, wrappedHandler)
        } else {
            baseManager.registerMessageHandler(messageType, handler)
        }
    }

    override fun unregisterMessageHandler(messageType: String) {
        baseManager.unregisterMessageHandler(messageType)
    }
}