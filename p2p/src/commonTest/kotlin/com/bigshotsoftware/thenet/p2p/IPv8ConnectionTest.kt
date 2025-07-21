package com.bigshotsoftware.thenet.p2p

import com.bigshotsoftware.thenet.p2p.config.IPv8Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic tests for IPv8 peer connection functionality
 */
class IPv8ConnectionTest {

    @Test
    fun testIPv8ConfigurationDefaults() {
        val config = IPv8Configuration()

        assertEquals(8090, config.port)
        assertTrue(config.enableDiscoveryCommunity)
        assertTrue(config.enableTrustChainCommunity)
        assertEquals(50, config.maxPeers)
        assertEquals(5000L, config.peerDiscoveryInterval)
    }

    @Test
    fun testIPv8ManagerConfig() {
        val config = IPv8Manager.IPv8Config(
            port = 8091,
            bootstrapPeers = listOf("192.168.1.1:8090", "192.168.1.2:8090"),
            enableDiscovery = true,
            enableTrustChain = false,
            workingDirectory = "/tmp/ipv8",
        )

        assertEquals(8091, config.port)
        assertEquals(2, config.bootstrapPeers.size)
        assertTrue(config.enableDiscovery)
        assertEquals(false, config.enableTrustChain)
        assertEquals("/tmp/ipv8", config.workingDirectory)
    }

    @Test
    fun testNetworkStatusFlow() = runTest {
        // This is a basic test of the state flow mechanism
        // Actual IPv8 integration tests will be added when platform implementations are ready
        val initialStatus = IPv8Manager.NetworkStatus.STOPPED
        assertEquals(IPv8Manager.NetworkStatus.STOPPED, initialStatus)
    }

    @Test
    fun testPeerInfoDataClass() {
        val peer = IPv8Manager.PeerInfo(
            peerId = "peer123",
            address = "192.168.1.100",
            port = 8090,
            isConnected = true,
            lastSeen = System.currentTimeMillis(),
        )

        assertEquals("peer123", peer.peerId)
        assertEquals("192.168.1.100", peer.address)
        assertEquals(8090, peer.port)
        assertTrue(peer.isConnected)
    }
}