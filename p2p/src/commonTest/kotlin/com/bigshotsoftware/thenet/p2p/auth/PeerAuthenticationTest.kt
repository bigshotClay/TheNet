package com.bigshotsoftware.thenet.p2p.auth

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeerAuthenticationTest {

    private class MockIPv8Manager : IPv8Manager {
        private val _networkStatus = MutableStateFlow(IPv8Manager.NetworkStatus.STOPPED)
        override val networkStatus: StateFlow<IPv8Manager.NetworkStatus> = _networkStatus

        private val _discoveredPeers = MutableStateFlow<List<IPv8Manager.PeerInfo>>(emptyList())
        override val discoveredPeers: StateFlow<List<IPv8Manager.PeerInfo>> = _discoveredPeers

        private val _connectionCount = MutableStateFlow(0)
        override val connectionCount: StateFlow<Int> = _connectionCount

        private val messageHandlers = mutableMapOf<String, (String, ByteArray) -> Unit>()
        private val sentMessages = mutableMapOf<String, MutableList<ByteArray>>()

        override suspend fun start(config: IPv8Manager.IPv8Config) {
            _networkStatus.value = IPv8Manager.NetworkStatus.RUNNING
        }

        override suspend fun stop() {
            _networkStatus.value = IPv8Manager.NetworkStatus.STOPPED
        }

        override suspend fun sendMessage(peerId: String, message: ByteArray): Boolean {
            sentMessages.getOrPut(peerId) { mutableListOf() }.add(message)
            return true
        }

        override suspend fun broadcastMessage(message: ByteArray): Int {
            return _discoveredPeers.value.size
        }

        override fun registerMessageHandler(messageType: String, handler: (peerId: String, message: ByteArray) -> Unit) {
            messageHandlers[messageType] = handler
        }

        override fun unregisterMessageHandler(messageType: String) {
            messageHandlers.remove(messageType)
        }

        // Test helper methods
        fun simulateMessageReceived(peerId: String, messageType: String, message: ByteArray) {
            messageHandlers[messageType]?.invoke(peerId, message)
        }

        fun getMessagesFor(peerId: String): List<ByteArray> = sentMessages[peerId] ?: emptyList()

        fun clearSentMessages() = sentMessages.clear()

        fun addDiscoveredPeer(peerInfo: IPv8Manager.PeerInfo) {
            val currentPeers = _discoveredPeers.value.toMutableList()
            currentPeers.add(peerInfo)
            _discoveredPeers.value = currentPeers
            _connectionCount.value = currentPeers.size
        }
    }

    @Test
    fun testPeerAuthenticationInitialization() {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        // Should have our own peer ID and public key
        assertNotNull(auth.getOurPeerId())
        assertNotNull(auth.getOurPublicKey())
        assertTrue(auth.getOurPeerId().isNotEmpty())
        assertEquals(AuthCrypto.ED25519_PUBLIC_KEY_SIZE, auth.getOurPublicKey().size)

        // Initial state should be empty
        assertEquals(0, auth.authenticatedConnectionCount.value)
        assertTrue(auth.authenticatedPeers.value.isEmpty())

        auth.cleanup()
    }

    @Test
    fun testPublicKeyExchangeCreationAndVerification() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        // Create public key exchange
        val exchange = auth.createPublicKeyExchange()

        assertNotNull(exchange)
        assertEquals(auth.getOurPeerId(), exchange.peerId)
        assertTrue(exchange.publicKey.contentEquals(auth.getOurPublicKey()))
        assertTrue(exchange.timestamp > 0)
        assertEquals(AuthCrypto.ED25519_SIGNATURE_SIZE, exchange.signature.size)

        // Verify the exchange
        assertTrue(auth.verifyPublicKeyExchange(exchange))

        auth.cleanup()
    }

    @Test
    fun testInvalidPublicKeyExchangeVerification() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val exchange = auth.createPublicKeyExchange()

        // Test with corrupted signature
        val corruptedExchange = exchange.copy(
            signature = ByteArray(AuthCrypto.ED25519_SIGNATURE_SIZE) { 0 }
        )
        assertFalse(auth.verifyPublicKeyExchange(corruptedExchange))

        // Test with invalid public key size
        val invalidKeyExchange = exchange.copy(
            publicKey = ByteArray(16) // Wrong size
        )
        assertFalse(auth.verifyPublicKeyExchange(invalidKeyExchange))

        // Test with very old timestamp
        val oldTimestamp = System.currentTimeMillis() - 600000 // 10 minutes ago
        val oldExchange = exchange.copy(timestamp = oldTimestamp)
        assertFalse(auth.verifyPublicKeyExchange(oldExchange))

        auth.cleanup()
    }

    @Test
    fun testAuthenticationStatus() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val testPeerId = "test_peer_123"

        // Initially should be unauthenticated
        assertEquals(PeerAuthentication.AuthStatus.UNAUTHENTICATED, auth.getAuthStatus(testPeerId))

        // Should not have authenticated peer
        assertNull(auth.getAuthenticatedPeer(testPeerId))

        auth.cleanup()
    }

    @Test
    fun testChallengeCreationAndValidation() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val targetPeerId = "target_peer_abc"

        // Create challenge
        val challenge = auth.challengePeer(targetPeerId)

        assertNotNull(challenge)
        assertEquals(auth.getOurPeerId(), challenge.challengerPeerId)
        assertEquals(targetPeerId, challenge.targetPeerId)
        assertTrue(challenge.challengeId.isNotEmpty())
        assertEquals(AuthCrypto.DEFAULT_CHALLENGE_SIZE, challenge.challenge.size)
        assertTrue(challenge.timestamp > 0)

        // Should have sent a message to the target peer
        val sentMessages = mockIPv8.getMessagesFor(targetPeerId)
        assertEquals(1, sentMessages.size)

        // Peer status should be PENDING_CHALLENGE
        assertEquals(PeerAuthentication.AuthStatus.PENDING_CHALLENGE, auth.getAuthStatus(targetPeerId))

        auth.cleanup()
    }

    @Test
    fun testChallengeResponse() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        // Create a challenge from another peer
        val challenge = PeerAuthentication.AuthChallenge(
            challengeId = AuthCrypto.generateChallengeId(),
            challengerPeerId = "challenger_peer",
            targetPeerId = auth.getOurPeerId(),
            challenge = AuthCrypto.generateChallenge(),
            timestamp = System.currentTimeMillis()
        )

        // Respond to the challenge
        val response = auth.respondToChallenge(challenge)

        assertNotNull(response)
        assertEquals(challenge.challengeId, response.challengeId)
        assertEquals(challenge.targetPeerId, response.responderPeerId)
        assertTrue(response.publicKey.contentEquals(auth.getOurPublicKey()))
        assertEquals(AuthCrypto.ED25519_SIGNATURE_SIZE, response.signature.size)
        assertTrue(response.timestamp > 0)

        // Should have sent a response message
        val sentMessages = mockIPv8.getMessagesFor(challenge.challengerPeerId)
        assertEquals(1, sentMessages.size)

        auth.cleanup()
    }

    @Test
    fun testResponseVerification() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val targetPeerId = "target_peer_def"

        // First, create a challenge
        val challenge = auth.challengePeer(targetPeerId)
        assertNotNull(challenge)

        // Create a mock response using a different auth instance (simulating remote peer)
        val mockIPv8_2 = MockIPv8Manager()
        val testScope2 = TestScope()
        val remoteAuth = PeerAuthenticationImpl(mockIPv8_2, testScope2)

        // Remote peer responds to our challenge
        val adjustedChallenge = challenge.copy(targetPeerId = remoteAuth.getOurPeerId())
        val response = remoteAuth.respondToChallenge(adjustedChallenge)
        assertNotNull(response)

        // Now verify the response
        val verificationResult = auth.verifyResponse(response)
        assertTrue(verificationResult)

        // After successful verification, peer should be authenticated
        assertEquals(PeerAuthentication.AuthStatus.AUTHENTICATED, auth.getAuthStatus(response.responderPeerId))

        val authenticatedPeer = auth.getAuthenticatedPeer(response.responderPeerId)
        assertNotNull(authenticatedPeer)
        assertEquals(response.responderPeerId, authenticatedPeer.peerId)
        assertTrue(authenticatedPeer.publicKey.contentEquals(response.publicKey))

        auth.cleanup()
        remoteAuth.cleanup()
    }

    @Test
    fun testInvalidResponseVerification() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val targetPeerId = "target_peer_ghi"

        // Create a challenge
        val challenge = auth.challengePeer(targetPeerId)
        assertNotNull(challenge)

        // Create an invalid response (wrong challenge data)
        val invalidResponse = PeerAuthentication.AuthResponse(
            responseId = AuthCrypto.generateResponseId(challenge.challengeId),
            challengeId = challenge.challengeId,
            responderPeerId = targetPeerId,
            response = ByteArray(32) { 99 }, // Wrong response data
            publicKey = ByteArray(32) { 1 },
            signature = ByteArray(64) { 0 },
            timestamp = System.currentTimeMillis()
        )

        // Verification should fail
        assertFalse(auth.verifyResponse(invalidResponse))

        // Peer should still not be authenticated
        assertEquals(PeerAuthentication.AuthStatus.PENDING_CHALLENGE, auth.getAuthStatus(targetPeerId))

        auth.cleanup()
    }

    @Test
    fun testAuthenticationRevocation() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val testPeerId = "peer_to_revoke"

        // Manually create an authenticated peer for testing
        val keyPair = AuthCrypto.generateEd25519KeyPair()
        val sessionId = AuthCrypto.generateSessionId()
        val currentTime = System.currentTimeMillis()

        val authenticatedPeer = PeerAuthentication.AuthenticatedPeer(
            peerId = testPeerId,
            publicKey = keyPair.publicKey,
            status = PeerAuthentication.AuthStatus.AUTHENTICATED,
            sessionId = sessionId,
            authenticatedAt = currentTime,
            expiresAt = currentTime + 3600000 // 1 hour
        )

        // Insert the authenticated peer directly (for testing purposes)
        val field = PeerAuthenticationImpl::class.java.getDeclaredField("authenticatedPeerMap")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = field.get(auth) as java.util.concurrent.ConcurrentHashMap<String, PeerAuthentication.AuthenticatedPeer>
        peerMap[testPeerId] = authenticatedPeer

        // Trigger state update
        val updateMethod = PeerAuthenticationImpl::class.java.getDeclaredMethod("updateAuthenticatedPeersList")
        updateMethod.isAccessible = true
        updateMethod.invoke(auth)

        // Verify peer is authenticated
        assertEquals(PeerAuthentication.AuthStatus.AUTHENTICATED, auth.getAuthStatus(testPeerId))
        assertNotNull(auth.getAuthenticatedPeer(testPeerId))
        assertEquals(1, auth.authenticatedConnectionCount.value)

        // Revoke authentication
        assertTrue(auth.revokeAuthentication(testPeerId))

        // Verify peer is no longer authenticated
        assertEquals(PeerAuthentication.AuthStatus.UNAUTHENTICATED, auth.getAuthStatus(testPeerId))
        assertNull(auth.getAuthenticatedPeer(testPeerId))
        assertEquals(0, auth.authenticatedConnectionCount.value)

        // Revoking again should return false
        assertFalse(auth.revokeAuthentication(testPeerId))

        auth.cleanup()
    }

    @Test
    fun testSessionExpiration() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val testPeerId = "expiring_peer"

        // Create an expired authenticated peer
        val keyPair = AuthCrypto.generateEd25519KeyPair()
        val sessionId = AuthCrypto.generateSessionId()
        val currentTime = System.currentTimeMillis()

        val expiredPeer = PeerAuthentication.AuthenticatedPeer(
            peerId = testPeerId,
            publicKey = keyPair.publicKey,
            status = PeerAuthentication.AuthStatus.AUTHENTICATED,
            sessionId = sessionId,
            authenticatedAt = currentTime - 7200000, // 2 hours ago
            expiresAt = currentTime - 3600000 // Expired 1 hour ago
        )

        // Insert the expired peer directly
        val field = PeerAuthenticationImpl::class.java.getDeclaredField("authenticatedPeerMap")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = field.get(auth) as java.util.concurrent.ConcurrentHashMap<String, PeerAuthentication.AuthenticatedPeer>
        peerMap[testPeerId] = expiredPeer

        // Check status - should be EXPIRED
        assertEquals(PeerAuthentication.AuthStatus.EXPIRED, auth.getAuthStatus(testPeerId))

        // Getting authenticated peer should return null for expired peer
        assertNull(auth.getAuthenticatedPeer(testPeerId))

        // Clean up expired sessions
        val cleanedUp = auth.cleanupExpiredSessions()
        assertEquals(1, cleanedUp)

        // Peer should be completely removed now
        assertEquals(PeerAuthentication.AuthStatus.UNAUTHENTICATED, auth.getAuthStatus(testPeerId))

        auth.cleanup()
    }

    @Test
    fun testConcurrentAuthentication() = runBlocking {
        val mockIPv8 = MockIPv8Manager()
        val testScope = TestScope()
        val auth = PeerAuthenticationImpl(mockIPv8, testScope)

        val peers = listOf("peer_1", "peer_2", "peer_3")

        // Initiate authentication with multiple peers concurrently
        peers.forEach { peerId ->
            val challenge = auth.challengePeer(peerId)
            assertNotNull(challenge)
        }

        // All peers should be in pending state
        peers.forEach { peerId ->
            assertEquals(PeerAuthentication.AuthStatus.PENDING_CHALLENGE, auth.getAuthStatus(peerId))
        }

        // Messages should be sent to all peers
        peers.forEach { peerId ->
            val messages = mockIPv8.getMessagesFor(peerId)
            assertEquals(1, messages.size)
        }

        auth.cleanup()
    }
}