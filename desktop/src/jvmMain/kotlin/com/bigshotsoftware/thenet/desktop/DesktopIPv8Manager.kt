package com.bigshotsoftware.thenet.desktop

import com.bigshotsoftware.thenet.p2p.IPv8Manager
import com.bigshotsoftware.thenet.p2p.impl.BaseIPv8Manager

class DesktopIPv8Manager : BaseIPv8Manager() {

    override suspend fun sendMessage(peerId: String, message: ByteArray): Boolean {
        // No-op placeholder for desktop demo
        return true
    }

    override suspend fun broadcastMessage(message: ByteArray): Int {
        // No-op placeholder for desktop demo
        return 0
    }

    override fun registerMessageHandler(
        messageType: String,
        handler: (peerId: String, message: ByteArray) -> Unit,
    ) {
        super.registerMessageHandler(messageType, handler)
    }

    override fun unregisterMessageHandler(messageType: String) {
        super.unregisterMessageHandler(messageType)
    }

    override suspend fun startPlatformSpecific(config: IPv8Manager.IPv8Config) {
        // Nothing platform-specific yet for demo; real IPv8 wiring would go here
    }

    override suspend fun stopPlatformSpecific() {
        // Cleanup resources when real transport is added
    }
}
