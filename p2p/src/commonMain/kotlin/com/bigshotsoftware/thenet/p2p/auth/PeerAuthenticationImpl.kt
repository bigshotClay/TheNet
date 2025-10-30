package com.bigshotsoftware.thenet.p2p.auth

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of PeerAuthentication that integrates with IPv8 networking.
 *
 * This class provides secure peer authentication using:
 * - Ed25519 digital signatures through IPv8's crypto provider
 * - Challenge-response protocol for mutual authentication
 * - Session management with automatic cleanup
 * - Integration with IPv8 message handling
 */
class PeerAuthenticationImpl(
    private val ipv8Manager: IPv8Manager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
) : PeerAuthentication {

    // Message types for authentication protocol
    companion object {
        const val MSG_TYPE_AUTH_CHALLENGE = "auth_challenge"
        const val MSG_TYPE_AUTH_RESPONSE = "auth_response"
        const val MSG_TYPE_AUTH_SUCCESS = "auth_success"
        const val MSG_TYPE_AUTH_FAILURE = "auth_failure"
        const val MSG_TYPE_PUBLIC_KEY_EXCHANGE = "public_key_exchange"
    }

    // State management
    private val _authenticatedPeers = MutableStateFlow<List<PeerAuthentication.AuthenticatedPeer>>(emptyList())
    override val authenticatedPeers: StateFlow<List<PeerAuthentication.AuthenticatedPeer>> = _authenticatedPeers.asStateFlow()

    private val _authenticatedConnectionCount = MutableStateFlow(0)
    override val authenticatedConnectionCount: StateFlow<Int> = _authenticatedConnectionCount.asStateFlow()

    // Internal storage
    private val pendingChallenges = ConcurrentHashMap<String, PeerAuthentication.AuthChallenge>()
    private val authenticatedPeerMap = ConcurrentHashMap<String, PeerAuthentication.AuthenticatedPeer>()
    private val authenticationJobs = ConcurrentHashMap<String, Job>()
    
    // Cryptographic keys
    private val ourKeyPair: AuthCrypto.Ed25519KeyPair = AuthCrypto.generateEd25519KeyPair()
    private val ourPeerId: String = AuthCrypto.hashPublicKey(ourKeyPair.publicKey)

    // Cleanup job for expired sessions
    private val cleanupJob: Job

    init {
        // Register message handlers with IPv8Manager
        ipv8Manager.registerMessageHandler(MSG_TYPE_AUTH_CHALLENGE) { peerId, message ->
            scope.launch { handleAuthChallenge(peerId, message) }
        }
        ipv8Manager.registerMessageHandler(MSG_TYPE_AUTH_RESPONSE) { peerId, message ->
            scope.launch { handleAuthResponse(peerId, message) }
        }
        ipv8Manager.registerMessageHandler(MSG_TYPE_AUTH_SUCCESS) { peerId, message ->
            scope.launch { handleAuthSuccess(peerId, message) }
        }
        ipv8Manager.registerMessageHandler(MSG_TYPE_AUTH_FAILURE) { peerId, message ->
            scope.launch { handleAuthFailure(peerId, message) }
        }
        ipv8Manager.registerMessageHandler(MSG_TYPE_PUBLIC_KEY_EXCHANGE) { peerId, message ->
            scope.launch { handlePublicKeyExchange(peerId, message) }
        }

        // Start periodic cleanup of expired sessions
        cleanupJob = scope.launch {
            while (isActive) {
                delay(60000) // Clean up every minute
                cleanupExpiredSessions()
            }
        }
    }

    override suspend fun authenticatePeer(peerId: String): Boolean {
        // Check if already authenticated
        val existingPeer = authenticatedPeerMap[peerId]
        if (existingPeer != null && AuthCrypto.isSessionValid(existingPeer)) {
            return true
        }

        // Start authentication process
        val challenge = challengePeer(peerId) ?: return false
        
        // Wait for authentication to complete (with timeout)
        return withTimeoutOrNull(30000L) { // 30 second timeout
            while (true) {
                val peer = authenticatedPeerMap[peerId]
                when (peer?.status) {
                    PeerAuthentication.AuthStatus.AUTHENTICATED -> return@withTimeoutOrNull true
                    PeerAuthentication.AuthStatus.FAILED -> return@withTimeoutOrNull false
                    null -> delay(100) // Wait and check again
                    else -> delay(100) // Still pending, continue waiting
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false // Timeout
    }

    override suspend fun challengePeer(peerId: String): PeerAuthentication.AuthChallenge? {
        try {
            // Generate challenge
            val challengeId = AuthCrypto.generateChallengeId()
            val challengeData = AuthCrypto.generateChallenge()
            val timestamp = System.currentTimeMillis()
            
            val challenge = PeerAuthentication.AuthChallenge(
                challengeId = challengeId,
                challengerPeerId = ourPeerId,
                targetPeerId = peerId,
                challenge = challengeData,
                timestamp = timestamp
            )

            // Store challenge for later verification
            pendingChallenges[challengeId] = challenge

            // Update peer status
            updatePeerAuthStatus(peerId, PeerAuthentication.AuthStatus.PENDING_CHALLENGE)

            // Serialize and send challenge
            val serializedChallenge = serializeChallenge(challenge)
            val sent = ipv8Manager.sendMessage(peerId, serializedChallenge)
            
            if (!sent) {
                pendingChallenges.remove(challengeId)
                updatePeerAuthStatus(peerId, PeerAuthentication.AuthStatus.FAILED)
                return null
            }

            return challenge
        } catch (e: Exception) {
            updatePeerAuthStatus(peerId, PeerAuthentication.AuthStatus.FAILED)
            return null
        }
    }

    override suspend fun respondToChallenge(challenge: PeerAuthentication.AuthChallenge): PeerAuthentication.AuthResponse? {
        try {
            if (!AuthCrypto.isChallengeValid(challenge)) {
                return null
            }

            val responseId = AuthCrypto.generateResponseId(challenge.challengeId)
            val response = AuthCrypto.createChallengeResponse(challenge.challenge, challenge.targetPeerId)
            val timestamp = System.currentTimeMillis()
            
            // Use our actual public key and create real signature
            val publicKey = ourKeyPair.publicKey
            val signatureData = AuthCrypto.createSignatureData(
                challenge.challengeId,
                challenge.challenge,
                response,
                challenge.targetPeerId,
                timestamp
            )
            val signature = AuthCrypto.signEd25519(ourKeyPair.privateKey, signatureData)
            
            val authResponse = PeerAuthentication.AuthResponse(
                responseId = responseId,
                challengeId = challenge.challengeId,
                responderPeerId = challenge.targetPeerId,
                response = response,
                publicKey = publicKey,
                signature = signature,
                timestamp = timestamp
            )

            // Send response
            val serializedResponse = serializeResponse(authResponse)
            val sent = ipv8Manager.sendMessage(challenge.challengerPeerId, serializedResponse)
            
            if (!sent) {
                return null
            }

            return authResponse
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun verifyResponse(response: PeerAuthentication.AuthResponse): Boolean {
        try {
            // Get the original challenge
            val challenge = pendingChallenges[response.challengeId] ?: return false
            
            // Verify timestamp
            if (!AuthCrypto.isTimestampValid(response.timestamp)) {
                return false
            }

            // Verify response matches challenge
            val expectedResponse = AuthCrypto.createChallengeResponse(challenge.challenge, challenge.targetPeerId)
            if (!response.response.contentEquals(expectedResponse)) {
                return false
            }

            // Verify signature using our crypto implementation
            val signatureData = AuthCrypto.createSignatureData(
                response.challengeId,
                challenge.challenge,
                response.response,
                response.responderPeerId,
                response.timestamp
            )
            val isValidSignature = AuthCrypto.verifyEd25519(response.publicKey, signatureData, response.signature)
            
            // Also validate public key format
            if (!AuthCrypto.isValidEd25519PublicKey(response.publicKey)) {
                return false
            }

            if (!isValidSignature) {
                return false
            }

            // Create authenticated peer
            val sessionId = AuthCrypto.generateSessionId()
            val authenticatedAt = System.currentTimeMillis()
            val expiresAt = AuthCrypto.calculateSessionExpiration(authenticatedAt)
            
            val authenticatedPeer = PeerAuthentication.AuthenticatedPeer(
                peerId = response.responderPeerId,
                publicKey = response.publicKey,
                status = PeerAuthentication.AuthStatus.AUTHENTICATED,
                sessionId = sessionId,
                authenticatedAt = authenticatedAt,
                expiresAt = expiresAt
            )

            // Store authenticated peer
            authenticatedPeerMap[response.responderPeerId] = authenticatedPeer
            updateAuthenticatedPeersList()

            // Clean up challenge
            pendingChallenges.remove(response.challengeId)

            // Send success message
            ipv8Manager.sendMessage(response.responderPeerId, serializeAuthSuccess(sessionId))

            return true
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun getAuthStatus(peerId: String): PeerAuthentication.AuthStatus {
        val peer = authenticatedPeerMap[peerId] ?: return PeerAuthentication.AuthStatus.UNAUTHENTICATED
        
        return if (AuthCrypto.isSessionValid(peer)) {
            peer.status
        } else {
            PeerAuthentication.AuthStatus.EXPIRED
        }
    }

    override suspend fun getAuthenticatedPeer(peerId: String): PeerAuthentication.AuthenticatedPeer? {
        val peer = authenticatedPeerMap[peerId] ?: return null
        return if (AuthCrypto.isSessionValid(peer)) peer else null
    }

    override suspend fun revokeAuthentication(peerId: String): Boolean {
        val removed = authenticatedPeerMap.remove(peerId)
        authenticationJobs[peerId]?.cancel()
        authenticationJobs.remove(peerId)
        
        if (removed != null) {
            updateAuthenticatedPeersList()
            return true
        }
        return false
    }

    override suspend fun rotateSessionKeys(peerId: String): Boolean {
        // TODO: Implement key rotation
        // This would generate new session keys while keeping the same authenticated session
        return false
    }

    override suspend fun cleanupExpiredSessions(): Int {
        val currentTime = System.currentTimeMillis()
        var cleanedUp = 0
        
        val iterator = authenticatedPeerMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val peer = entry.value
            
            if (!AuthCrypto.isSessionValid(peer)) {
                iterator.remove()
                authenticationJobs[entry.key]?.cancel()
                authenticationJobs.remove(entry.key)
                cleanedUp++
            }
        }
        
        // Clean up expired challenges
        val challengeIterator = pendingChallenges.iterator()
        while (challengeIterator.hasNext()) {
            val entry = challengeIterator.next()
            if (!AuthCrypto.isChallengeValid(entry.value)) {
                challengeIterator.remove()
            }
        }
        
        if (cleanedUp > 0) {
            updateAuthenticatedPeersList()
        }
        
        return cleanedUp
    }

    override fun getOurPeerId(): String = ourPeerId

    override fun getOurPublicKey(): ByteArray = ourKeyPair.publicKey.copyOf()

    override suspend fun createPublicKeyExchange(): PeerAuthentication.PublicKeyExchange {
        val timestamp = System.currentTimeMillis()
        val signatureData = createKeyExchangeSignatureData(ourPeerId, ourKeyPair.publicKey, timestamp)
        val signature = AuthCrypto.signEd25519(ourKeyPair.privateKey, signatureData)
        
        return PeerAuthentication.PublicKeyExchange(
            peerId = ourPeerId,
            publicKey = ourKeyPair.publicKey.copyOf(),
            timestamp = timestamp,
            signature = signature
        )
    }

    override suspend fun verifyPublicKeyExchange(exchange: PeerAuthentication.PublicKeyExchange): Boolean {
        try {
            // Verify timestamp is not too old
            if (!AuthCrypto.isTimestampValid(exchange.timestamp)) {
                return false
            }
            
            // Verify public key format
            if (!AuthCrypto.isValidEd25519PublicKey(exchange.publicKey)) {
                return false
            }
            
            // Verify signature
            val signatureData = createKeyExchangeSignatureData(
                exchange.peerId, 
                exchange.publicKey, 
                exchange.timestamp
            )
            
            return AuthCrypto.verifyEd25519(exchange.publicKey, signatureData, exchange.signature)
        } catch (e: Exception) {
            return false
        }
    }

    private fun createKeyExchangeSignatureData(peerId: String, publicKey: ByteArray, timestamp: Long): ByteArray {
        val peerIdBytes = peerId.toByteArray()
        val timestampBytes = timestamp.toString().toByteArray()
        
        val totalSize = peerIdBytes.size + publicKey.size + timestampBytes.size + 12
        val result = ByteArray(totalSize)
        var offset = 0
        
        // Add peer ID with length prefix
        result[offset++] = peerIdBytes.size.toByte()
        System.arraycopy(peerIdBytes, 0, result, offset, peerIdBytes.size)
        offset += peerIdBytes.size
        
        // Add public key with length prefix
        val keySize = publicKey.size
        result[offset++] = (keySize shr 24).toByte()
        result[offset++] = (keySize shr 16).toByte()
        result[offset++] = (keySize shr 8).toByte()
        result[offset++] = keySize.toByte()
        System.arraycopy(publicKey, 0, result, offset, publicKey.size)
        offset += publicKey.size
        
        // Add timestamp with length prefix
        result[offset++] = timestampBytes.size.toByte()
        System.arraycopy(timestampBytes, 0, result, offset, timestampBytes.size)
        
        return result
    }

    // Message handlers
    private suspend fun handleAuthChallenge(peerId: String, message: ByteArray) {
        try {
            val challenge = deserializeChallenge(message)
            val response = respondToChallenge(challenge)
            
            if (response == null) {
                // Send failure message
                ipv8Manager.sendMessage(peerId, serializeAuthFailure("Challenge response failed"))
            }
        } catch (e: Exception) {
            ipv8Manager.sendMessage(peerId, serializeAuthFailure("Invalid challenge format"))
        }
    }

    private suspend fun handleAuthResponse(peerId: String, message: ByteArray) {
        try {
            val response = deserializeResponse(message)
            val isValid = verifyResponse(response)
            
            if (!isValid) {
                ipv8Manager.sendMessage(peerId, serializeAuthFailure("Response verification failed"))
                updatePeerAuthStatus(peerId, PeerAuthentication.AuthStatus.FAILED)
            }
        } catch (e: Exception) {
            ipv8Manager.sendMessage(peerId, serializeAuthFailure("Invalid response format"))
            updatePeerAuthStatus(peerId, PeerAuthentication.AuthStatus.FAILED)
        }
    }

    private suspend fun handleAuthSuccess(peerId: String, message: ByteArray) {
        try {
            val sessionId = deserializeAuthSuccess(message)
            // Update local state to reflect successful authentication
            val existingPeer = authenticatedPeerMap[peerId]
            if (existingPeer != null) {
                val updatedPeer = existingPeer.copy(
                    status = PeerAuthentication.AuthStatus.AUTHENTICATED,
                    sessionId = sessionId
                )
                authenticatedPeerMap[peerId] = updatedPeer
                updateAuthenticatedPeersList()
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    private suspend fun handleAuthFailure(peerId: String, message: ByteArray) {
        try {
            val error = deserializeAuthFailure(message)
            updatePeerAuthStatus(peerId, PeerAuthentication.AuthStatus.FAILED)
            // Log the error
        } catch (e: Exception) {
            // Handle error
        }
    }

    private suspend fun handlePublicKeyExchange(peerId: String, message: ByteArray) {
        try {
            val exchange = deserializePublicKeyExchange(message)
            val isValid = verifyPublicKeyExchange(exchange)
            
            if (isValid) {
                // Store peer's public key for future authentication
                val currentPeer = authenticatedPeerMap[peerId]
                if (currentPeer != null) {
                    val updatedPeer = currentPeer.copy(publicKey = exchange.publicKey)
                    authenticatedPeerMap[peerId] = updatedPeer
                    updateAuthenticatedPeersList()
                }
                
                // Send our public key exchange in response if we haven't already
                val ourExchange = createPublicKeyExchange()
                val serializedExchange = serializePublicKeyExchange(ourExchange)
                ipv8Manager.sendMessage(peerId, serializedExchange)
            }
        } catch (e: Exception) {
            // Handle error - invalid key exchange format
        }
    }

    // Helper methods
    private fun updatePeerAuthStatus(peerId: String, status: PeerAuthentication.AuthStatus) {
        val existingPeer = authenticatedPeerMap[peerId]
        val updatedPeer = if (existingPeer != null) {
            existingPeer.copy(status = status)
        } else {
            PeerAuthentication.AuthenticatedPeer(
                peerId = peerId,
                publicKey = ByteArray(AuthCrypto.ED25519_PUBLIC_KEY_SIZE), // Empty placeholder
                status = status,
                sessionId = "",
                authenticatedAt = System.currentTimeMillis(),
                expiresAt = 0L
            )
        }
        authenticatedPeerMap[peerId] = updatedPeer
        updateAuthenticatedPeersList()
    }

    private fun updateAuthenticatedPeersList() {
        val peers = authenticatedPeerMap.values.toList()
        _authenticatedPeers.value = peers
        _authenticatedConnectionCount.value = peers.count { it.status == PeerAuthentication.AuthStatus.AUTHENTICATED }
    }

    // Binary message serialization using AuthMessage
    private fun serializeChallenge(challenge: PeerAuthentication.AuthChallenge): ByteArray {
        return AuthMessage.serializeChallenge(challenge)
    }

    private fun deserializeChallenge(data: ByteArray): PeerAuthentication.AuthChallenge {
        return AuthMessage.deserializeChallenge(data)
    }

    private fun serializeResponse(response: PeerAuthentication.AuthResponse): ByteArray {
        return AuthMessage.serializeResponse(response)
    }

    private fun deserializeResponse(data: ByteArray): PeerAuthentication.AuthResponse {
        return AuthMessage.deserializeResponse(data)
    }

    private fun serializeAuthSuccess(sessionId: String): ByteArray {
        return AuthMessage.serializeAuthSuccess(sessionId)
    }

    private fun deserializeAuthSuccess(data: ByteArray): String {
        return AuthMessage.deserializeAuthSuccess(data)
    }

    private fun serializeAuthFailure(error: String): ByteArray {
        return AuthMessage.serializeAuthFailure(error)
    }

    private fun deserializeAuthFailure(data: ByteArray): String {
        return AuthMessage.deserializeAuthFailure(data)
    }

    private fun serializePublicKeyExchange(exchange: PeerAuthentication.PublicKeyExchange): ByteArray {
        return AuthMessage.serializePublicKeyExchange(exchange)
    }

    private fun deserializePublicKeyExchange(data: ByteArray): PeerAuthentication.PublicKeyExchange {
        return AuthMessage.deserializePublicKeyExchange(data)
    }

    fun cleanup() {
        cleanupJob.cancel()
        ipv8Manager.unregisterMessageHandler(MSG_TYPE_AUTH_CHALLENGE)
        ipv8Manager.unregisterMessageHandler(MSG_TYPE_AUTH_RESPONSE)
        ipv8Manager.unregisterMessageHandler(MSG_TYPE_AUTH_SUCCESS)
        ipv8Manager.unregisterMessageHandler(MSG_TYPE_AUTH_FAILURE)
        ipv8Manager.unregisterMessageHandler(MSG_TYPE_PUBLIC_KEY_EXCHANGE)
    }
}