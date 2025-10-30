package com.bigshotsoftware.thenet.p2p.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthCryptoTest {

    @Test
    fun testChallengeGeneration() {
        val challenge1 = AuthCrypto.generateChallenge()
        val challenge2 = AuthCrypto.generateChallenge()
        
        // Challenges should be of correct size
        assertEquals(AuthCrypto.DEFAULT_CHALLENGE_SIZE, challenge1.size)
        assertEquals(AuthCrypto.DEFAULT_CHALLENGE_SIZE, challenge2.size)
        
        // Challenges should be different
        assertFalse(challenge1.contentEquals(challenge2))
    }

    @Test
    fun testChallengeIdGeneration() {
        val id1 = AuthCrypto.generateChallengeId()
        val id2 = AuthCrypto.generateChallengeId()
        
        // IDs should be different
        assertNotEquals(id1, id2)
        
        // IDs should be valid hex strings
        assertTrue(id1.matches(Regex("[0-9a-f]+")))
        assertTrue(id2.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun testSessionIdGeneration() {
        val sessionId1 = AuthCrypto.generateSessionId()
        val sessionId2 = AuthCrypto.generateSessionId()
        
        // Session IDs should be different
        assertNotEquals(sessionId1, sessionId2)
        assertNotNull(sessionId1)
        assertNotNull(sessionId2)
    }

    @Test
    fun testResponseIdGeneration() {
        val challengeId = "test_challenge_123"
        val responseId1 = AuthCrypto.generateResponseId(challengeId)
        val responseId2 = AuthCrypto.generateResponseId(challengeId)
        
        // Response IDs should be different even for same challenge
        assertNotEquals(responseId1, responseId2)
        
        // Response IDs should contain the challenge reference
        assertTrue(responseId1.contains("resp_"))
        assertTrue(responseId2.contains("resp_"))
    }

    @Test
    fun testSignatureDataCreation() {
        val challengeId = "test_challenge"
        val challenge = byteArrayOf(1, 2, 3, 4)
        val response = byteArrayOf(5, 6, 7, 8)
        val peerId = "test_peer"
        val timestamp = 1234567890L
        
        val signatureData = AuthCrypto.createSignatureData(
            challengeId, challenge, response, peerId, timestamp
        )
        
        assertNotNull(signatureData)
        assertTrue(signatureData.isNotEmpty())
        
        // Same inputs should produce same signature data
        val signatureData2 = AuthCrypto.createSignatureData(
            challengeId, challenge, response, peerId, timestamp
        )
        assertTrue(signatureData.contentEquals(signatureData2))
        
        // Different inputs should produce different signature data
        val signatureData3 = AuthCrypto.createSignatureData(
            challengeId, challenge, response, peerId, timestamp + 1
        )
        assertFalse(signatureData.contentEquals(signatureData3))
    }

    @Test
    fun testEd25519KeyPairGeneration() {
        val keyPair1 = AuthCrypto.generateEd25519KeyPair()
        val keyPair2 = AuthCrypto.generateEd25519KeyPair()
        
        // Key pairs should have correct sizes
        assertEquals(AuthCrypto.ED25519_PUBLIC_KEY_SIZE, keyPair1.publicKey.size)
        assertEquals(AuthCrypto.ED25519_PRIVATE_KEY_SIZE, keyPair1.privateKey.size)
        assertEquals(AuthCrypto.ED25519_PUBLIC_KEY_SIZE, keyPair2.publicKey.size)
        assertEquals(AuthCrypto.ED25519_PRIVATE_KEY_SIZE, keyPair2.privateKey.size)
        
        // Key pairs should be different
        assertFalse(keyPair1.publicKey.contentEquals(keyPair2.publicKey))
        assertFalse(keyPair1.privateKey.contentEquals(keyPair2.privateKey))
        
        // Public keys should be valid
        assertTrue(AuthCrypto.isValidEd25519PublicKey(keyPair1.publicKey))
        assertTrue(AuthCrypto.isValidEd25519PublicKey(keyPair2.publicKey))
    }

    @Test
    fun testEd25519SigningAndVerification() {
        val keyPair = AuthCrypto.generateEd25519KeyPair()
        val data = "Test message for signing".toByteArray()
        
        // Sign the data
        val signature = AuthCrypto.signEd25519(keyPair.privateKey, data)
        
        // Signature should have correct size
        assertEquals(AuthCrypto.ED25519_SIGNATURE_SIZE, signature.size)
        
        // Verify the signature
        assertTrue(AuthCrypto.verifyEd25519(keyPair.publicKey, data, signature))
        
        // Verification should fail with different data
        val differentData = "Different message".toByteArray()
        assertFalse(AuthCrypto.verifyEd25519(keyPair.publicKey, differentData, signature))
        
        // Verification should fail with different public key
        val differentKeyPair = AuthCrypto.generateEd25519KeyPair()
        assertFalse(AuthCrypto.verifyEd25519(differentKeyPair.publicKey, data, signature))
        
        // Verification should fail with corrupted signature
        val corruptedSignature = signature.copyOf()
        corruptedSignature[0] = (corruptedSignature[0].toInt() xor 0xFF).toByte()
        assertFalse(AuthCrypto.verifyEd25519(keyPair.publicKey, data, corruptedSignature))
    }

    @Test
    fun testPublicKeyValidation() {
        // Valid key size
        val validKey = ByteArray(AuthCrypto.ED25519_PUBLIC_KEY_SIZE) { it.toByte() }
        assertTrue(AuthCrypto.isValidEd25519PublicKey(validKey))
        
        // Invalid key sizes
        assertFalse(AuthCrypto.isValidEd25519PublicKey(ByteArray(31)))
        assertFalse(AuthCrypto.isValidEd25519PublicKey(ByteArray(33)))
        assertFalse(AuthCrypto.isValidEd25519PublicKey(ByteArray(0)))
    }

    @Test
    fun testHashPublicKey() {
        val publicKey1 = ByteArray(32) { 1 }
        val publicKey2 = ByteArray(32) { 2 }
        
        val hash1 = AuthCrypto.hashPublicKey(publicKey1)
        val hash2 = AuthCrypto.hashPublicKey(publicKey2)
        
        // Hashes should be different for different keys
        assertNotEquals(hash1, hash2)
        
        // Same key should produce same hash
        val hash1Duplicate = AuthCrypto.hashPublicKey(publicKey1)
        assertEquals(hash1, hash1Duplicate)
        
        // Hash should be valid hex string
        assertTrue(hash1.matches(Regex("[0-9a-f]+")))
        assertTrue(hash2.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun testChallengeValidation() {
        val currentTime = System.currentTimeMillis()
        
        // Valid challenge (recent timestamp)
        val validChallenge = PeerAuthentication.AuthChallenge(
            challengeId = "test",
            challengerPeerId = "challenger",
            targetPeerId = "target",
            challenge = byteArrayOf(1, 2, 3),
            timestamp = currentTime - 1000, // 1 second ago
            ttl = 60000 // 1 minute TTL
        )
        assertTrue(AuthCrypto.isChallengeValid(validChallenge))
        
        // Expired challenge
        val expiredChallenge = validChallenge.copy(
            timestamp = currentTime - 120000, // 2 minutes ago
            ttl = 60000 // 1 minute TTL
        )
        assertFalse(AuthCrypto.isChallengeValid(expiredChallenge))
    }

    @Test
    fun testSessionValidation() {
        val currentTime = System.currentTimeMillis()
        
        // Valid session
        val validSession = PeerAuthentication.AuthenticatedPeer(
            peerId = "test_peer",
            publicKey = ByteArray(32),
            status = PeerAuthentication.AuthStatus.AUTHENTICATED,
            sessionId = "session_123",
            authenticatedAt = currentTime - 1000,
            expiresAt = currentTime + 3600000 // 1 hour from now
        )
        assertTrue(AuthCrypto.isSessionValid(validSession))
        
        // Expired session
        val expiredSession = validSession.copy(
            expiresAt = currentTime - 1000 // 1 second ago
        )
        assertFalse(AuthCrypto.isSessionValid(expiredSession))
        
        // Unauthenticated session
        val unauthenticatedSession = validSession.copy(
            status = PeerAuthentication.AuthStatus.PENDING_CHALLENGE
        )
        assertFalse(AuthCrypto.isSessionValid(unauthenticatedSession))
    }

    @Test
    fun testTimestampValidation() {
        val currentTime = System.currentTimeMillis()
        
        // Current timestamp should be valid
        assertTrue(AuthCrypto.isTimestampValid(currentTime))
        
        // Recent timestamp should be valid
        assertTrue(AuthCrypto.isTimestampValid(currentTime - 10000)) // 10 seconds ago
        
        // Very old timestamp should be invalid
        assertFalse(AuthCrypto.isTimestampValid(currentTime - 600000)) // 10 minutes ago
        
        // Future timestamp should be invalid
        assertFalse(AuthCrypto.isTimestampValid(currentTime + 60000)) // 1 minute from now
    }

    @Test
    fun testChallengeResponse() {
        val challenge = byteArrayOf(1, 2, 3, 4, 5)
        val peerId = "test_peer_123"
        
        val response = AuthCrypto.createChallengeResponse(challenge, peerId)
        
        assertNotNull(response)
        assertTrue(response.isNotEmpty())
        
        // Same inputs should produce same response
        val response2 = AuthCrypto.createChallengeResponse(challenge, peerId)
        assertTrue(response.contentEquals(response2))
        
        // Different inputs should produce different responses
        val response3 = AuthCrypto.createChallengeResponse(challenge, "different_peer")
        assertFalse(response.contentEquals(response3))
    }

    @Test
    fun testSessionExpiration() {
        val baseTime = System.currentTimeMillis()
        
        // Default timeout
        val expiration1 = AuthCrypto.calculateSessionExpiration(baseTime)
        assertEquals(baseTime + AuthCrypto.DEFAULT_SESSION_TIMEOUT, expiration1)
        
        // Custom timeout
        val customTimeout = 1800000L // 30 minutes
        val expiration2 = AuthCrypto.calculateSessionExpiration(baseTime, customTimeout)
        assertEquals(baseTime + customTimeout, expiration2)
    }
}