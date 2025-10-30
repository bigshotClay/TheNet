package com.bigshotsoftware.thenet.p2p.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthMessageTest {

    @Test
    fun testChallengeSerializationDeserialization() {
        val originalChallenge = PeerAuthentication.AuthChallenge(
            challengeId = "test_challenge_123",
            challengerPeerId = "challenger_peer_abc",
            targetPeerId = "target_peer_def",
            challenge = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            timestamp = System.currentTimeMillis(),
            ttl = 60000L
        )

        // Serialize
        val serialized = AuthMessage.serializeChallenge(originalChallenge)
        assertTrue(serialized.isNotEmpty())

        // Deserialize
        val deserialized = AuthMessage.deserializeChallenge(serialized)

        // Verify all fields match
        assertEquals(originalChallenge.challengeId, deserialized.challengeId)
        assertEquals(originalChallenge.challengerPeerId, deserialized.challengerPeerId)
        assertEquals(originalChallenge.targetPeerId, deserialized.targetPeerId)
        assertTrue(originalChallenge.challenge.contentEquals(deserialized.challenge))
        assertEquals(originalChallenge.timestamp, deserialized.timestamp)
        assertEquals(originalChallenge.ttl, deserialized.ttl)
    }

    @Test
    fun testResponseSerializationDeserialization() {
        val originalResponse = PeerAuthentication.AuthResponse(
            responseId = "response_456",
            challengeId = "challenge_123",
            responderPeerId = "responder_xyz",
            response = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1),
            publicKey = ByteArray(32) { it.toByte() },
            signature = ByteArray(64) { (it * 2).toByte() },
            timestamp = System.currentTimeMillis()
        )

        // Serialize
        val serialized = AuthMessage.serializeResponse(originalResponse)
        assertTrue(serialized.isNotEmpty())

        // Deserialize
        val deserialized = AuthMessage.deserializeResponse(serialized)

        // Verify all fields match
        assertEquals(originalResponse.responseId, deserialized.responseId)
        assertEquals(originalResponse.challengeId, deserialized.challengeId)
        assertEquals(originalResponse.responderPeerId, deserialized.responderPeerId)
        assertTrue(originalResponse.response.contentEquals(deserialized.response))
        assertTrue(originalResponse.publicKey.contentEquals(deserialized.publicKey))
        assertTrue(originalResponse.signature.contentEquals(deserialized.signature))
        assertEquals(originalResponse.timestamp, deserialized.timestamp)
    }

    @Test
    fun testPublicKeyExchangeSerializationDeserialization() {
        val originalExchange = PeerAuthentication.PublicKeyExchange(
            peerId = "peer_789",
            publicKey = ByteArray(32) { (255 - it).toByte() },
            timestamp = System.currentTimeMillis(),
            signature = ByteArray(64) { (it * 3).toByte() }
        )

        // Serialize
        val serialized = AuthMessage.serializePublicKeyExchange(originalExchange)
        assertTrue(serialized.isNotEmpty())

        // Deserialize
        val deserialized = AuthMessage.deserializePublicKeyExchange(serialized)

        // Verify all fields match
        assertEquals(originalExchange.peerId, deserialized.peerId)
        assertTrue(originalExchange.publicKey.contentEquals(deserialized.publicKey))
        assertEquals(originalExchange.timestamp, deserialized.timestamp)
        assertTrue(originalExchange.signature.contentEquals(deserialized.signature))
    }

    @Test
    fun testAuthSuccessSerializationDeserialization() {
        val sessionId = "session_successful_abc123"

        // Serialize
        val serialized = AuthMessage.serializeAuthSuccess(sessionId)
        assertTrue(serialized.isNotEmpty())

        // Deserialize
        val deserialized = AuthMessage.deserializeAuthSuccess(serialized)

        // Verify session ID matches
        assertEquals(sessionId, deserialized)
    }

    @Test
    fun testAuthFailureSerializationDeserialization() {
        val errorMessage = "Authentication failed: Invalid signature"

        // Serialize
        val serialized = AuthMessage.serializeAuthFailure(errorMessage)
        assertTrue(serialized.isNotEmpty())

        // Deserialize
        val deserialized = AuthMessage.deserializeAuthFailure(serialized)

        // Verify error message matches
        assertEquals(errorMessage, deserialized)
    }

    @Test
    fun testMessageTypeDetection() {
        // Test challenge message type
        val challenge = PeerAuthentication.AuthChallenge(
            challengeId = "test",
            challengerPeerId = "challenger",
            targetPeerId = "target",
            challenge = byteArrayOf(1, 2, 3),
            timestamp = System.currentTimeMillis()
        )
        val challengeSerialized = AuthMessage.serializeChallenge(challenge)
        assertEquals(AuthMessage.TYPE_AUTH_CHALLENGE, AuthMessage.getMessageType(challengeSerialized))

        // Test response message type
        val response = PeerAuthentication.AuthResponse(
            responseId = "resp",
            challengeId = "challenge",
            responderPeerId = "responder",
            response = byteArrayOf(1),
            publicKey = ByteArray(32),
            signature = ByteArray(64),
            timestamp = System.currentTimeMillis()
        )
        val responseSerialized = AuthMessage.serializeResponse(response)
        assertEquals(AuthMessage.TYPE_AUTH_RESPONSE, AuthMessage.getMessageType(responseSerialized))

        // Test public key exchange message type
        val exchange = PeerAuthentication.PublicKeyExchange(
            peerId = "peer",
            publicKey = ByteArray(32),
            timestamp = System.currentTimeMillis(),
            signature = ByteArray(64)
        )
        val exchangeSerialized = AuthMessage.serializePublicKeyExchange(exchange)
        assertEquals(AuthMessage.TYPE_PUBLIC_KEY_EXCHANGE, AuthMessage.getMessageType(exchangeSerialized))

        // Test success message type
        val successSerialized = AuthMessage.serializeAuthSuccess("session")
        assertEquals(AuthMessage.TYPE_AUTH_SUCCESS, AuthMessage.getMessageType(successSerialized))

        // Test failure message type
        val failureSerialized = AuthMessage.serializeAuthFailure("error")
        assertEquals(AuthMessage.TYPE_AUTH_FAILURE, AuthMessage.getMessageType(failureSerialized))
    }

    @Test
    fun testMessageValidation() {
        // Valid messages
        val validChallenge = AuthMessage.serializeChallenge(
            PeerAuthentication.AuthChallenge(
                challengeId = "test",
                challengerPeerId = "challenger",
                targetPeerId = "target",
                challenge = byteArrayOf(1, 2, 3),
                timestamp = System.currentTimeMillis()
            )
        )
        assertTrue(AuthMessage.isValidMessage(validChallenge))

        val validSuccess = AuthMessage.serializeAuthSuccess("session_123")
        assertTrue(AuthMessage.isValidMessage(validSuccess))

        // Invalid messages
        assertFalse(AuthMessage.isValidMessage(ByteArray(0))) // Empty
        assertFalse(AuthMessage.isValidMessage(byteArrayOf(1, 2))) // Too short
        assertFalse(AuthMessage.isValidMessage(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 10))) // Invalid version/type
    }

    @Test
    fun testInvalidMessageDeserialization() {
        // Test invalid challenge deserialization
        assertFailsWith<IllegalArgumentException> {
            AuthMessage.deserializeChallenge(byteArrayOf(1, 2, 3, 4, 5))
        }

        // Test invalid response deserialization
        assertFailsWith<IllegalArgumentException> {
            AuthMessage.deserializeResponse(byteArrayOf(1, 2, 3, 4, 5))
        }

        // Test invalid public key exchange deserialization
        assertFailsWith<IllegalArgumentException> {
            AuthMessage.deserializePublicKeyExchange(byteArrayOf(1, 2, 3, 4, 5))
        }

        // Test invalid auth success deserialization
        assertFailsWith<IllegalArgumentException> {
            AuthMessage.deserializeAuthSuccess(byteArrayOf(1, 2, 3, 4, 5))
        }

        // Test invalid auth failure deserialization
        assertFailsWith<IllegalArgumentException> {
            AuthMessage.deserializeAuthFailure(byteArrayOf(1, 2, 3, 4, 5))
        }
    }

    @Test
    fun testEmptyStringFields() {
        // Test with empty string fields
        val challengeWithEmptyFields = PeerAuthentication.AuthChallenge(
            challengeId = "",
            challengerPeerId = "",
            targetPeerId = "",
            challenge = byteArrayOf(),
            timestamp = System.currentTimeMillis()
        )

        val serialized = AuthMessage.serializeChallenge(challengeWithEmptyFields)
        val deserialized = AuthMessage.deserializeChallenge(serialized)

        assertEquals("", deserialized.challengeId)
        assertEquals("", deserialized.challengerPeerId)
        assertEquals("", deserialized.targetPeerId)
        assertEquals(0, deserialized.challenge.size)
    }

    @Test
    fun testLargeData() {
        // Test with large data arrays
        val largeChallenge = ByteArray(1024) { it.toByte() }
        val largePublicKey = ByteArray(1000) { (it % 256).toByte() }
        val largeSignature = ByteArray(2000) { ((it * 7) % 256).toByte() }

        val response = PeerAuthentication.AuthResponse(
            responseId = "large_response",
            challengeId = "large_challenge",
            responderPeerId = "responder",
            response = largeChallenge,
            publicKey = largePublicKey,
            signature = largeSignature,
            timestamp = System.currentTimeMillis()
        )

        val serialized = AuthMessage.serializeResponse(response)
        val deserialized = AuthMessage.deserializeResponse(serialized)

        assertTrue(response.response.contentEquals(deserialized.response))
        assertTrue(response.publicKey.contentEquals(deserialized.publicKey))
        assertTrue(response.signature.contentEquals(deserialized.signature))
    }

    @Test
    fun testUnicodeStringHandling() {
        // Test with Unicode characters
        val unicodePeerId = "peer_测试_🔒_αβγ"
        val unicodeChallengeId = "challenge_測試_🚀"

        val challenge = PeerAuthentication.AuthChallenge(
            challengeId = unicodeChallengeId,
            challengerPeerId = unicodePeerId,
            targetPeerId = "target_unicode_τεστ",
            challenge = byteArrayOf(1, 2, 3),
            timestamp = System.currentTimeMillis()
        )

        val serialized = AuthMessage.serializeChallenge(challenge)
        val deserialized = AuthMessage.deserializeChallenge(serialized)

        assertEquals(unicodeChallengeId, deserialized.challengeId)
        assertEquals(unicodePeerId, deserialized.challengerPeerId)
        assertEquals("target_unicode_τεστ", deserialized.targetPeerId)
    }

    @Test
    fun testMessageSizeConsistency() {
        val challenge = PeerAuthentication.AuthChallenge(
            challengeId = "test_challenge",
            challengerPeerId = "challenger",
            targetPeerId = "target",
            challenge = byteArrayOf(1, 2, 3, 4, 5),
            timestamp = System.currentTimeMillis(),
            ttl = 60000
        )

        val serialized = AuthMessage.serializeChallenge(challenge)
        
        // Message should include proper header
        assertTrue(serialized.size >= AuthMessage.HEADER_SIZE)
        
        // Protocol version should be correct
        assertEquals(AuthMessage.PROTOCOL_VERSION, serialized[0])
        
        // Message type should be correct
        assertEquals(AuthMessage.TYPE_AUTH_CHALLENGE, serialized[1])
    }
}