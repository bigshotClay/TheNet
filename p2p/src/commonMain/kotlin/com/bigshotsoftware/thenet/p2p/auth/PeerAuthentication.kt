package com.bigshotsoftware.thenet.p2p.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Main interface for peer authentication functionality in TheNet.
 *
 * This interface provides secure peer-to-peer authentication using:
 * - Ed25519 public key cryptography
 * - Challenge-response protocols
 * - Identity verification mechanisms
 * - Session management with key rotation
 */
interface PeerAuthentication {

    /**
     * Authentication status for a peer session
     */
    enum class AuthStatus {
        UNAUTHENTICATED,
        PENDING_CHALLENGE,
        PENDING_RESPONSE,
        AUTHENTICATED,
        FAILED,
        EXPIRED
    }

    /**
     * Information about an authenticated peer
     */
    data class AuthenticatedPeer(
        val peerId: String,
        val publicKey: ByteArray,
        val status: AuthStatus,
        val sessionId: String,
        val authenticatedAt: Long,
        val expiresAt: Long,
        val trustLevel: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthenticatedPeer) return false
            return peerId == other.peerId &&
                publicKey.contentEquals(other.publicKey) &&
                status == other.status &&
                sessionId == other.sessionId
        }

        override fun hashCode(): Int {
            var result = peerId.hashCode()
            result = 31 * result + publicKey.contentHashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + sessionId.hashCode()
            return result
        }
    }

    /**
     * Authentication challenge data structure
     */
    data class AuthChallenge(
        val challengeId: String,
        val challengerPeerId: String,
        val targetPeerId: String,
        val challenge: ByteArray,
        val timestamp: Long,
        val ttl: Long = 60000L // 1 minute default TTL
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthChallenge) return false
            return challengeId == other.challengeId &&
                challengerPeerId == other.challengerPeerId &&
                targetPeerId == other.targetPeerId &&
                challenge.contentEquals(other.challenge)
        }

        override fun hashCode(): Int {
            var result = challengeId.hashCode()
            result = 31 * result + challengerPeerId.hashCode()
            result = 31 * result + targetPeerId.hashCode()
            result = 31 * result + challenge.contentHashCode()
            return result
        }
    }

    /**
     * Authentication response data structure
     */
    data class AuthResponse(
        val responseId: String,
        val challengeId: String,
        val responderPeerId: String,
        val response: ByteArray,
        val publicKey: ByteArray,
        val signature: ByteArray,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthResponse) return false
            return responseId == other.responseId &&
                challengeId == other.challengeId &&
                responderPeerId == other.responderPeerId &&
                response.contentEquals(other.response) &&
                publicKey.contentEquals(other.publicKey) &&
                signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            var result = responseId.hashCode()
            result = 31 * result + challengeId.hashCode()
            result = 31 * result + responderPeerId.hashCode()
            result = 31 * result + response.contentHashCode()
            result = 31 * result + publicKey.contentHashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }
    }

    /**
     * Current list of authenticated peers
     */
    val authenticatedPeers: StateFlow<List<AuthenticatedPeer>>

    /**
     * Number of authenticated connections
     */
    val authenticatedConnectionCount: StateFlow<Int>

    /**
     * Initiate authentication with a peer
     */
    suspend fun authenticatePeer(peerId: String): Boolean

    /**
     * Send a challenge to a peer for authentication
     */
    suspend fun challengePeer(peerId: String): AuthChallenge?

    /**
     * Respond to an authentication challenge
     */
    suspend fun respondToChallenge(challenge: AuthChallenge): AuthResponse?

    /**
     * Verify an authentication response
     */
    suspend fun verifyResponse(response: AuthResponse): Boolean

    /**
     * Get authentication status for a specific peer
     */
    suspend fun getAuthStatus(peerId: String): AuthStatus

    /**
     * Get authenticated peer information
     */
    suspend fun getAuthenticatedPeer(peerId: String): AuthenticatedPeer?

    /**
     * Revoke authentication for a peer (logout)
     */
    suspend fun revokeAuthentication(peerId: String): Boolean

    /**
     * Rotate keys for an authenticated session
     */
    suspend fun rotateSessionKeys(peerId: String): Boolean

    /**
     * Clean up expired authentication sessions
     */
    suspend fun cleanupExpiredSessions(): Int

    /**
     * Get our own peer ID
     */
    fun getOurPeerId(): String

    /**
     * Get our public key
     */
    fun getOurPublicKey(): ByteArray

    /**
     * Public key exchange data structure
     */
    data class PublicKeyExchange(
        val peerId: String,
        val publicKey: ByteArray,
        val timestamp: Long,
        val signature: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PublicKeyExchange) return false
            return peerId == other.peerId &&
                publicKey.contentEquals(other.publicKey) &&
                timestamp == other.timestamp &&
                signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            var result = peerId.hashCode()
            result = 31 * result + publicKey.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }
    }

    /**
     * Create a public key exchange message
     */
    suspend fun createPublicKeyExchange(): PublicKeyExchange

    /**
     * Verify a public key exchange message
     */
    suspend fun verifyPublicKeyExchange(exchange: PublicKeyExchange): Boolean
}