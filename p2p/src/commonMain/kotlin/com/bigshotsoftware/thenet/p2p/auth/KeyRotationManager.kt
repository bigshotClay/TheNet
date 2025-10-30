package com.bigshotsoftware.thenet.p2p.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages cryptographic key rotation for authenticated peer sessions.
 * 
 * This class provides:
 * - Automatic key rotation based on time and usage policies
 * - Manual key rotation on demand
 * - Key generation and secure exchange protocols
 * - Backward compatibility during rotation periods
 * - Security event logging and monitoring
 */
class KeyRotationManager(
    private val sessionManager: AuthSessionManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
) {

    /**
     * Key rotation configuration
     */
    data class RotationConfig(
        val enableAutoRotation: Boolean = true,
        val rotationIntervalMs: Long = 86400000L, // 24 hours
        val maxKeyAge: Long = 604800000L, // 7 days
        val maxKeyUsage: Long = 10000L, // Max operations per key
        val gracePeriodMs: Long = 3600000L, // 1 hour grace period for old keys
        val batchRotationSize: Int = 10 // Max peers to rotate simultaneously
    )

    /**
     * Key pair information
     */
    data class KeyPair(
        val keyId: String,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
        val createdAt: Long,
        val expiresAt: Long,
        val usageCount: Long = 0L,
        val isActive: Boolean = true
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KeyPair) return false
            return keyId == other.keyId &&
                publicKey.contentEquals(other.publicKey) &&
                createdAt == other.createdAt
        }

        override fun hashCode(): Int {
            var result = keyId.hashCode()
            result = 31 * result + publicKey.contentHashCode()
            result = 31 * result + createdAt.hashCode()
            return result
        }
    }

    /**
     * Key rotation request
     */
    data class RotationRequest(
        val requestId: String,
        val peerId: String,
        val currentKeyId: String,
        val newKeyId: String,
        val newPublicKey: ByteArray,
        val timestamp: Long,
        val signature: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RotationRequest) return false
            return requestId == other.requestId &&
                peerId == other.peerId &&
                currentKeyId == other.currentKeyId &&
                newKeyId == other.newKeyId &&
                newPublicKey.contentEquals(other.newPublicKey)
        }

        override fun hashCode(): Int {
            var result = requestId.hashCode()
            result = 31 * result + peerId.hashCode()
            result = 31 * result + currentKeyId.hashCode()
            result = 31 * result + newKeyId.hashCode()
            result = 31 * result + newPublicKey.contentHashCode()
            return result
        }
    }

    /**
     * Key rotation status
     */
    enum class RotationStatus {
        NONE,
        INITIATED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    /**
     * Key rotation statistics
     */
    data class RotationStats(
        val totalRotations: Long,
        val successfulRotations: Long,
        val failedRotations: Long,
        val automaticRotations: Long,
        val manualRotations: Long,
        val averageRotationTime: Long
    )

    // Internal state
    private val config = RotationConfig()
    private val peerKeys = ConcurrentHashMap<String, KeyPair>()
    private val rotationHistory = ConcurrentHashMap<String, MutableList<Long>>()
    private val pendingRotations = ConcurrentHashMap<String, RotationRequest>()
    private val _rotationStats = MutableStateFlow(RotationStats(0L, 0L, 0L, 0L, 0L, 0L))
    val rotationStats: StateFlow<RotationStats> = _rotationStats.asStateFlow()

    // Statistics tracking
    private var totalRotations = 0L
    private var successfulRotations = 0L
    private var failedRotations = 0L
    private var automaticRotations = 0L
    private var manualRotations = 0L
    private var totalRotationTime = 0L

    // Background rotation job
    private val rotationJob: Job

    init {
        rotationJob = scope.launch {
            while (isActive) {
                delay(config.rotationIntervalMs / 10) // Check every 10% of rotation interval
                if (config.enableAutoRotation) {
                    performAutomaticRotation()
                }
                updateStats()
            }
        }
    }

    /**
     * Generate a new key pair for a peer
     */
    suspend fun generateKeyPair(peerId: String): KeyPair {
        val keyId = AuthCrypto.generateSessionId()
        val currentTime = System.currentTimeMillis()
        
        // Generate actual Ed25519 key pair
        val ed25519KeyPair = AuthCrypto.generateEd25519KeyPair()
        
        val keyPair = KeyPair(
            keyId = keyId,
            publicKey = ed25519KeyPair.publicKey,
            privateKey = ed25519KeyPair.privateKey,
            createdAt = currentTime,
            expiresAt = currentTime + config.maxKeyAge
        )
        
        peerKeys[peerId] = keyPair
        return keyPair
    }

    /**
     * Initiate key rotation for a specific peer
     */
    suspend fun initiateKeyRotation(peerId: String, force: Boolean = false): Boolean {
        try {
            val currentKey = peerKeys[peerId]
            if (currentKey == null && !force) {
                return false
            }

            // Check if rotation is needed
            if (!force && !isRotationNeeded(peerId)) {
                return false
            }

            // Generate new key pair
            val newKeyPair = generateKeyPair(peerId)
            
            // Create rotation request
            val requestId = AuthCrypto.generateChallengeId()
            val timestamp = System.currentTimeMillis()
            
            // Create proper signature using current private key
            val signatureData = createRotationSignatureData(requestId, peerId, 
                currentKey?.keyId ?: "", newKeyPair.keyId, newKeyPair.publicKey, timestamp)
            val signature = if (currentKey != null) {
                AuthCrypto.signEd25519(currentKey.privateKey, signatureData)
            } else {
                ByteArray(AuthCrypto.ED25519_SIGNATURE_SIZE) // Empty signature for initial key
            }
            
            val rotationRequest = RotationRequest(
                requestId = requestId,
                peerId = peerId,
                currentKeyId = currentKey?.keyId ?: "",
                newKeyId = newKeyPair.keyId,
                newPublicKey = newKeyPair.publicKey,
                timestamp = timestamp,
                signature = signature
            )

            pendingRotations[peerId] = rotationRequest
            
            // TODO: Send rotation request to peer
            // This would use the authenticated message channel
            
            totalRotations++
            if (force) manualRotations++ else automaticRotations++
            
            return true
        } catch (e: Exception) {
            failedRotations++
            return false
        }
    }

    /**
     * Process a key rotation request from a peer
     */
    suspend fun processRotationRequest(request: RotationRequest): Boolean {
        try {
            // Verify request timestamp
            if (!AuthCrypto.isTimestampValid(request.timestamp)) {
                return false
            }

            // Verify request signature
            val signatureData = createRotationSignatureData(
                request.requestId,
                request.peerId,
                request.currentKeyId,
                request.newKeyId,
                request.newPublicKey,
                request.timestamp
            )
            
            // Verify signature using peer's current public key
            val currentKey = peerKeys[request.peerId]
            val isValidSignature = if (currentKey != null && request.currentKeyId.isNotEmpty()) {
                AuthCrypto.verifyEd25519(currentKey.publicKey, signatureData, request.signature)
            } else {
                // For initial key exchange, allow empty signatures
                request.signature.all { it == 0.toByte() }
            }
            
            if (!isValidSignature) {
                return false
            }

            // Update peer's key
            val currentTime = System.currentTimeMillis()
            val newKeyPair = KeyPair(
                keyId = request.newKeyId,
                publicKey = request.newPublicKey,
                privateKey = ByteArray(AuthCrypto.ED25519_PRIVATE_KEY_SIZE), // We don't store peer's private key
                createdAt = currentTime,
                expiresAt = currentTime + config.maxKeyAge
            )
            
            peerKeys[request.peerId] = newKeyPair
            
            // Update rotation history
            rotationHistory.computeIfAbsent(request.peerId) { mutableListOf() }.add(currentTime)
            
            successfulRotations++
            return true
        } catch (e: Exception) {
            failedRotations++
            return false
        }
    }

    /**
     * Check if key rotation is needed for a peer
     */
    fun isRotationNeeded(peerId: String): Boolean {
        val keyPair = peerKeys[peerId] ?: return true
        val currentTime = System.currentTimeMillis()
        
        // Check age-based rotation
        if (currentTime - keyPair.createdAt > config.rotationIntervalMs) {
            return true
        }
        
        // Check expiration
        if (currentTime > keyPair.expiresAt) {
            return true
        }
        
        // Check usage-based rotation
        if (keyPair.usageCount > config.maxKeyUsage) {
            return true
        }
        
        return false
    }

    /**
     * Get current key for a peer
     */
    fun getCurrentKey(peerId: String): KeyPair? {
        val keyPair = peerKeys[peerId] ?: return null
        return if (isKeyValid(keyPair)) keyPair else null
    }

    /**
     * Check if a key is still valid
     */
    fun isKeyValid(keyPair: KeyPair): Boolean {
        val currentTime = System.currentTimeMillis()
        return keyPair.isActive && currentTime < keyPair.expiresAt
    }

    /**
     * Increment usage count for a peer's key
     */
    suspend fun incrementKeyUsage(peerId: String): Boolean {
        val currentKey = peerKeys[peerId] ?: return false
        val updatedKey = currentKey.copy(usageCount = currentKey.usageCount + 1)
        peerKeys[peerId] = updatedKey
        
        // Check if rotation is needed due to high usage
        if (updatedKey.usageCount > config.maxKeyUsage) {
            initiateKeyRotation(peerId)
        }
        
        return true
    }

    /**
     * Revoke a key (mark as inactive)
     */
    suspend fun revokeKey(peerId: String): Boolean {
        val currentKey = peerKeys[peerId] ?: return false
        val revokedKey = currentKey.copy(isActive = false)
        peerKeys[peerId] = revokedKey
        return true
    }

    /**
     * Get rotation history for a peer
     */
    fun getRotationHistory(peerId: String): List<Long> {
        return rotationHistory[peerId]?.toList() ?: emptyList()
    }

    /**
     * Perform automatic rotation for all eligible peers
     */
    private suspend fun performAutomaticRotation() {
        val eligiblePeers = peerKeys.entries
            .filter { isRotationNeeded(it.key) }
            .take(config.batchRotationSize)
            .map { it.key }
        
        for (peerId in eligiblePeers) {
            initiateKeyRotation(peerId)
            delay(100) // Small delay between rotations
        }
    }

    /**
     * Clean up expired keys and rotation data
     */
    suspend fun cleanupExpiredKeys(): Int {
        var cleanedUp = 0
        val currentTime = System.currentTimeMillis()
        
        val keysToRemove = mutableListOf<String>()
        
        for ((peerId, keyPair) in peerKeys) {
            if (!isKeyValid(keyPair) && 
                currentTime > keyPair.expiresAt + config.gracePeriodMs) {
                keysToRemove.add(peerId)
            }
        }
        
        for (peerId in keysToRemove) {
            peerKeys.remove(peerId)
            cleanedUp++
        }
        
        // Clean up old rotation history
        for ((peerId, history) in rotationHistory) {
            val recentRotations = history.filter { 
                currentTime - it < config.maxKeyAge * 2 
            }
            if (recentRotations.isEmpty()) {
                rotationHistory.remove(peerId)
            } else if (recentRotations.size < history.size) {
                rotationHistory[peerId] = recentRotations.toMutableList()
            }
        }
        
        return cleanedUp
    }

    /**
     * Create signature data for key rotation requests
     */
    private fun createRotationSignatureData(
        requestId: String,
        peerId: String,
        currentKeyId: String,
        newKeyId: String,
        newPublicKey: ByteArray,
        timestamp: Long
    ): ByteArray {
        val requestIdBytes = requestId.toByteArray()
        val peerIdBytes = peerId.toByteArray()
        val currentKeyIdBytes = currentKeyId.toByteArray()
        val newKeyIdBytes = newKeyId.toByteArray()
        val timestampBytes = timestamp.toString().toByteArray()
        
        val totalSize = requestIdBytes.size + peerIdBytes.size + currentKeyIdBytes.size +
                       newKeyIdBytes.size + newPublicKey.size + timestampBytes.size + 24
        
        val result = ByteArray(totalSize)
        var offset = 0
        
        // Pack all data with length prefixes
        result[offset++] = requestIdBytes.size.toByte()
        System.arraycopy(requestIdBytes, 0, result, offset, requestIdBytes.size)
        offset += requestIdBytes.size
        
        result[offset++] = peerIdBytes.size.toByte()
        System.arraycopy(peerIdBytes, 0, result, offset, peerIdBytes.size)
        offset += peerIdBytes.size
        
        result[offset++] = currentKeyIdBytes.size.toByte()
        System.arraycopy(currentKeyIdBytes, 0, result, offset, currentKeyIdBytes.size)
        offset += currentKeyIdBytes.size
        
        result[offset++] = newKeyIdBytes.size.toByte()
        System.arraycopy(newKeyIdBytes, 0, result, offset, newKeyIdBytes.size)
        offset += newKeyIdBytes.size
        
        val newKeySize = newPublicKey.size
        result[offset++] = (newKeySize shr 24).toByte()
        result[offset++] = (newKeySize shr 16).toByte()
        result[offset++] = (newKeySize shr 8).toByte()
        result[offset++] = newKeySize.toByte()
        System.arraycopy(newPublicKey, 0, result, offset, newPublicKey.size)
        offset += newPublicKey.size
        
        result[offset++] = timestampBytes.size.toByte()
        System.arraycopy(timestampBytes, 0, result, offset, timestampBytes.size)
        
        return result
    }

    /**
     * Update rotation statistics
     */
    private fun updateStats() {
        val avgRotationTime = if (successfulRotations > 0) {
            totalRotationTime / successfulRotations
        } else {
            0L
        }
        
        _rotationStats.value = RotationStats(
            totalRotations = totalRotations,
            successfulRotations = successfulRotations,
            failedRotations = failedRotations,
            automaticRotations = automaticRotations,
            manualRotations = manualRotations,
            averageRotationTime = avgRotationTime
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        rotationJob.cancel()
        peerKeys.clear()
        rotationHistory.clear()
        pendingRotations.clear()
    }
}