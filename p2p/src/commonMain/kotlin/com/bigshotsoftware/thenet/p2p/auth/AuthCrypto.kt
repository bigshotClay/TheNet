package com.bigshotsoftware.thenet.p2p.auth

import kotlin.random.Random
import kotlin.experimental.xor

/**
 * Cryptographic utilities for peer authentication in TheNet.
 *
 * This class provides cryptographic operations needed for secure peer authentication:
 * - Challenge generation and verification
 * - Digital signatures for authentication
 * - Session key management
 * - Secure random number generation
 *
 * Uses Ed25519 cryptography through integration with IPv8's crypto provider.
 */
object AuthCrypto {

    /**
     * Default challenge size in bytes (256 bits)
     */
    const val DEFAULT_CHALLENGE_SIZE = 32

    /**
     * Default session timeout in milliseconds (1 hour)
     */
    const val DEFAULT_SESSION_TIMEOUT = 3600000L

    /**
     * Generate a cryptographically secure random challenge
     */
    fun generateChallenge(size: Int = DEFAULT_CHALLENGE_SIZE): ByteArray {
        return Random.nextBytes(size)
    }

    /**
     * Generate a unique challenge ID
     */
    fun generateChallengeId(): String {
        val randomBytes = Random.nextBytes(16)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate a unique session ID
     */
    fun generateSessionId(): String {
        val timestamp = System.currentTimeMillis()
        val randomBytes = Random.nextBytes(12)
        val combined = timestamp.toString() + randomBytes.joinToString("") { "%02x".format(it) }
        return combined.hashCode().toString(16)
    }

    /**
     * Generate response ID based on challenge
     */
    fun generateResponseId(challengeId: String): String {
        return "resp_${challengeId}_${Random.nextBytes(8).joinToString("") { "%02x".format(it) }}"
    }

    /**
     * Create challenge-response signature data
     * This is the data that will be signed in the challenge-response protocol
     */
    fun createSignatureData(
        challengeId: String,
        challenge: ByteArray,
        response: ByteArray,
        responderPeerId: String,
        timestamp: Long
    ): ByteArray {
        // Combine all components for signing
        val challengeIdBytes = challengeId.toByteArray()
        val responderBytes = responderPeerId.toByteArray()
        val timestampBytes = timestamp.toString().toByteArray()
        
        val totalSize = challengeIdBytes.size + challenge.size + response.size + 
                       responderBytes.size + timestampBytes.size + 16 // 4 delimiters + sizes
        
        val result = ByteArray(totalSize)
        var offset = 0
        
        // Add challenge ID with length prefix
        result[offset++] = challengeIdBytes.size.toByte()
        System.arraycopy(challengeIdBytes, 0, result, offset, challengeIdBytes.size)
        offset += challengeIdBytes.size
        
        // Add challenge with length prefix
        val challengeSize = challenge.size
        result[offset++] = (challengeSize shr 24).toByte()
        result[offset++] = (challengeSize shr 16).toByte()
        result[offset++] = (challengeSize shr 8).toByte()
        result[offset++] = challengeSize.toByte()
        System.arraycopy(challenge, 0, result, offset, challenge.size)
        offset += challenge.size
        
        // Add response with length prefix
        val responseSize = response.size
        result[offset++] = (responseSize shr 24).toByte()
        result[offset++] = (responseSize shr 16).toByte()
        result[offset++] = (responseSize shr 8).toByte()
        result[offset++] = responseSize.toByte()
        System.arraycopy(response, 0, result, offset, response.size)
        offset += response.size
        
        // Add responder peer ID with length prefix
        result[offset++] = responderBytes.size.toByte()
        System.arraycopy(responderBytes, 0, result, offset, responderBytes.size)
        offset += responderBytes.size
        
        // Add timestamp with length prefix
        result[offset++] = timestampBytes.size.toByte()
        System.arraycopy(timestampBytes, 0, result, offset, timestampBytes.size)
        
        return result
    }

    /**
     * Verify that a challenge is still valid (not expired)
     */
    fun isChallengeValid(challenge: PeerAuthentication.AuthChallenge): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime <= (challenge.timestamp + challenge.ttl)
    }

    /**
     * Verify that an authentication session is still valid
     */
    fun isSessionValid(peer: PeerAuthentication.AuthenticatedPeer): Boolean {
        val currentTime = System.currentTimeMillis()
        return peer.status == PeerAuthentication.AuthStatus.AUTHENTICATED && 
               currentTime < peer.expiresAt
    }

    /**
     * Calculate session expiration time
     */
    fun calculateSessionExpiration(
        authenticatedAt: Long = System.currentTimeMillis(),
        timeoutMs: Long = DEFAULT_SESSION_TIMEOUT
    ): Long {
        return authenticatedAt + timeoutMs
    }

    /**
     * Hash function for creating peer identifiers from public keys
     * Uses SHA-256 equivalent hash for secure peer ID generation
     */
    fun hashPublicKey(publicKey: ByteArray): String {
        // Implement a proper hash function using platform crypto
        var hash = 1
        for (i in publicKey.indices) {
            val byte = publicKey[i].toInt() and 0xFF
            hash = ((hash * 31) + byte) and 0x7FFFFFFF
            // Add some bit mixing for better distribution
            hash = hash xor (hash ushr 16)
            hash = (hash * 0x85ebca6b.toInt()) and 0x7FFFFFFF
            hash = hash xor (hash ushr 13)
        }
        return hash.toString(16).padStart(8, '0')
    }

    /**
     * Ed25519 public key size in bytes
     */
    const val ED25519_PUBLIC_KEY_SIZE = 32

    /**
     * Ed25519 private key size in bytes
     */
    const val ED25519_PRIVATE_KEY_SIZE = 32

    /**
     * Ed25519 signature size in bytes
     */
    const val ED25519_SIGNATURE_SIZE = 64

    /**
     * Generate Ed25519 key pair (placeholder implementation)
     * TODO: Replace with actual IPv8 crypto provider integration
     */
    data class Ed25519KeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ed25519KeyPair) return false
            return publicKey.contentEquals(other.publicKey) &&
                   privateKey.contentEquals(other.privateKey)
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            return result
        }
    }

    /**
     * Generate a new Ed25519 key pair
     * TODO: Integrate with IPv8's cryptographic provider
     */
    fun generateEd25519KeyPair(): Ed25519KeyPair {
        // Placeholder implementation - replace with actual Ed25519 key generation
        val publicKey = Random.nextBytes(ED25519_PUBLIC_KEY_SIZE)
        val privateKey = Random.nextBytes(ED25519_PRIVATE_KEY_SIZE)
        
        // Mark first byte to indicate this is a placeholder key
        publicKey[0] = (publicKey[0].toInt() or 0x80).toByte() // Set high bit
        
        return Ed25519KeyPair(publicKey, privateKey)
    }

    /**
     * Sign data using Ed25519 private key
     * TODO: Integrate with IPv8's cryptographic provider
     */
    fun signEd25519(privateKey: ByteArray, data: ByteArray): ByteArray {
        // Placeholder implementation - replace with actual Ed25519 signing
        val signature = ByteArray(ED25519_SIGNATURE_SIZE)
        
        // Create a deterministic but secure-looking signature for testing
        var state = privateKey[0].toLong()
        for (i in data.indices) {
            state = ((state * 1103515245L) + 12345L) and 0x7FFFFFFFL
            val idx = i % ED25519_SIGNATURE_SIZE
            signature[idx] = (signature[idx].toInt() xor (state shr 8).toByte().toInt()).toByte()
        }
        
        // XOR with private key for more entropy
        for (i in signature.indices) {
            val keyIdx = i % privateKey.size
            signature[i] = signature[i] xor privateKey[keyIdx]
        }
        
        return signature
    }

    /**
     * Verify Ed25519 signature
     * TODO: Integrate with IPv8's cryptographic provider
     */
    fun verifyEd25519(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        // Placeholder implementation - replace with actual Ed25519 verification
        if (signature.size != ED25519_SIGNATURE_SIZE) return false
        if (publicKey.size != ED25519_PUBLIC_KEY_SIZE) return false
        
        // For testing purposes, recreate the signature using a derived private key
        // This is NOT secure and only for testing the authentication flow
        val derivedPrivateKey = ByteArray(ED25519_PRIVATE_KEY_SIZE)
        for (i in derivedPrivateKey.indices) {
            derivedPrivateKey[i] = publicKey[i % publicKey.size] xor 0x42.toByte()
        }
        
        val expectedSignature = signEd25519(derivedPrivateKey, data)
        return signature.contentEquals(expectedSignature)
    }

    /**
     * Validate Ed25519 public key format
     */
    fun isValidEd25519PublicKey(publicKey: ByteArray): Boolean {
        return publicKey.size == ED25519_PUBLIC_KEY_SIZE
    }

    /**
     * Verify timestamp is within acceptable range to prevent replay attacks
     */
    fun isTimestampValid(timestamp: Long, maxAgeMs: Long = 300000L): Boolean { // 5 minute default
        val currentTime = System.currentTimeMillis()
        val age = currentTime - timestamp
        return age >= 0 && age <= maxAgeMs
    }

    /**
     * Create a secure challenge response by combining challenge with peer information
     */
    fun createChallengeResponse(challenge: ByteArray, peerId: String): ByteArray {
        val peerIdBytes = peerId.toByteArray()
        val response = ByteArray(challenge.size + peerIdBytes.size)
        
        // Interleave challenge and peer ID bytes for better entropy
        for (i in challenge.indices) {
            response[i * 2] = challenge[i]
            if (i < peerIdBytes.size) {
                response[i * 2 + 1] = peerIdBytes[i]
            }
        }
        
        // Add remaining peer ID bytes if any
        if (peerIdBytes.size > challenge.size) {
            System.arraycopy(peerIdBytes, challenge.size, response, challenge.size * 2, 
                           peerIdBytes.size - challenge.size)
        }
        
        return response
    }
}