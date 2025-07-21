package com.bigshotsoftware.thenet.p2p.impl

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import com.bigshotsoftware.thenet.p2p.IPv8Manager.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base implementation of IPv8Manager with common functionality
 */
abstract class BaseIPv8Manager : IPv8Manager {

    protected val scope = CoroutineScope(SupervisorJob())

    private val _networkStatus = MutableStateFlow(NetworkStatus.STOPPED)
    override val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    override val discoveredPeers: StateFlow<List<PeerInfo>> = _discoveredPeers.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    override val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    private val messageHandlers = mutableMapOf<String, (String, ByteArray) -> Unit>()

    override suspend fun start(config: IPv8Config) {
        _networkStatus.value = NetworkStatus.STARTING
        try {
            startPlatformSpecific(config)
            _networkStatus.value = NetworkStatus.RUNNING
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        _networkStatus.value = NetworkStatus.STOPPING
        try {
            stopPlatformSpecific()
            _networkStatus.value = NetworkStatus.STOPPED
        } catch (e: Exception) {
            _networkStatus.value = NetworkStatus.ERROR
            throw e
        }
    }

    override fun registerMessageHandler(messageType: String, handler: (peerId: String, message: ByteArray) -> Unit) {
        messageHandlers[messageType] = handler
    }

    override fun unregisterMessageHandler(messageType: String) {
        messageHandlers.remove(messageType)
    }

    /**
     * Platform-specific start implementation
     */
    protected abstract suspend fun startPlatformSpecific(config: IPv8Config)

    /**
     * Platform-specific stop implementation
     */
    protected abstract suspend fun stopPlatformSpecific()

    /**
     * Update the discovered peers list
     */
    protected fun updateDiscoveredPeers(peers: List<PeerInfo>) {
        scope.launch {
            _discoveredPeers.value = peers
            _connectionCount.value = peers.count { it.isConnected }
        }
    }

    /**
     * Handle incoming message
     */
    protected fun handleIncomingMessage(messageType: String, peerId: String, message: ByteArray) {
        messageHandlers[messageType]?.invoke(peerId, message)
    }
}