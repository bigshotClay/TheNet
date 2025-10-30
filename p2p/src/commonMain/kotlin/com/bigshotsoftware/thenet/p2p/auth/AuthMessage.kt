package com.bigshotsoftware.thenet.p2p.auth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary message serialization for peer authentication protocol.
 * 
 * This provides secure, efficient binary serialization for authentication messages
 * instead of simple string-based formats which are vulnerable and inefficient.
 */
object AuthMessage {
    
    // Message type constants
    const val TYPE_AUTH_CHALLENGE: Byte = 0x01
    const val TYPE_AUTH_RESPONSE: Byte = 0x02
    const val TYPE_AUTH_SUCCESS: Byte = 0x03
    const val TYPE_AUTH_FAILURE: Byte = 0x04
    const val TYPE_PUBLIC_KEY_EXCHANGE: Byte = 0x05
    
    // Protocol version
    const val PROTOCOL_VERSION: Byte = 0x01
    
    // Message header size (version + type + length)
    const val HEADER_SIZE = 6
    
    /**
     * Serialize authentication challenge
     */
    fun serializeChallenge(challenge: PeerAuthentication.AuthChallenge): ByteArray {
        val challengerBytes = challenge.challengerPeerId.toByteArray(Charsets.UTF_8)
        val targetBytes = challenge.targetPeerId.toByteArray(Charsets.UTF_8)
        val challengeIdBytes = challenge.challengeId.toByteArray(Charsets.UTF_8)
        
        val totalSize = HEADER_SIZE + 
                       4 + challengeIdBytes.size + // challenge ID with length
                       4 + challengerBytes.size + // challenger peer ID with length
                       4 + targetBytes.size + // target peer ID with length
                       4 + challenge.challenge.size + // challenge data with length
                       8 + // timestamp
                       8   // TTL
        
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        // Header
        buffer.put(PROTOCOL_VERSION)
        buffer.put(TYPE_AUTH_CHALLENGE)
        buffer.putInt(totalSize - HEADER_SIZE)
        
        // Challenge ID
        buffer.putInt(challengeIdBytes.size)
        buffer.put(challengeIdBytes)
        
        // Challenger peer ID
        buffer.putInt(challengerBytes.size)
        buffer.put(challengerBytes)
        
        // Target peer ID
        buffer.putInt(targetBytes.size)
        buffer.put(targetBytes)
        
        // Challenge data
        buffer.putInt(challenge.challenge.size)
        buffer.put(challenge.challenge)
        
        // Timestamp and TTL
        buffer.putLong(challenge.timestamp)
        buffer.putLong(challenge.ttl)
        
        return buffer.array()
    }
    
    /**
     * Deserialize authentication challenge
     */
    fun deserializeChallenge(data: ByteArray): PeerAuthentication.AuthChallenge {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        
        // Verify header
        val version = buffer.get()
        val type = buffer.get()
        val length = buffer.getInt()
        
        if (version != PROTOCOL_VERSION || type != TYPE_AUTH_CHALLENGE) {
            throw IllegalArgumentException("Invalid challenge message format")
        }
        
        // Read challenge ID
        val challengeIdLength = buffer.getInt()
        val challengeIdBytes = ByteArray(challengeIdLength)
        buffer.get(challengeIdBytes)
        val challengeId = String(challengeIdBytes, Charsets.UTF_8)
        
        // Read challenger peer ID
        val challengerLength = buffer.getInt()
        val challengerBytes = ByteArray(challengerLength)
        buffer.get(challengerBytes)
        val challengerPeerId = String(challengerBytes, Charsets.UTF_8)
        
        // Read target peer ID
        val targetLength = buffer.getInt()
        val targetBytes = ByteArray(targetLength)
        buffer.get(targetBytes)
        val targetPeerId = String(targetBytes, Charsets.UTF_8)
        
        // Read challenge data
        val challengeLength = buffer.getInt()
        val challenge = ByteArray(challengeLength)
        buffer.get(challenge)
        
        // Read timestamp and TTL
        val timestamp = buffer.getLong()
        val ttl = buffer.getLong()
        
        return PeerAuthentication.AuthChallenge(
            challengeId = challengeId,
            challengerPeerId = challengerPeerId,
            targetPeerId = targetPeerId,
            challenge = challenge,
            timestamp = timestamp,
            ttl = ttl
        )
    }
    
    /**
     * Serialize authentication response
     */
    fun serializeResponse(response: PeerAuthentication.AuthResponse): ByteArray {
        val responseIdBytes = response.responseId.toByteArray(Charsets.UTF_8)
        val challengeIdBytes = response.challengeId.toByteArray(Charsets.UTF_8)
        val responderBytes = response.responderPeerId.toByteArray(Charsets.UTF_8)
        
        val totalSize = HEADER_SIZE + 
                       4 + responseIdBytes.size + // response ID with length
                       4 + challengeIdBytes.size + // challenge ID with length
                       4 + responderBytes.size + // responder peer ID with length
                       4 + response.response.size + // response data with length
                       4 + response.publicKey.size + // public key with length
                       4 + response.signature.size + // signature with length
                       8   // timestamp
        
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        // Header
        buffer.put(PROTOCOL_VERSION)
        buffer.put(TYPE_AUTH_RESPONSE)
        buffer.putInt(totalSize - HEADER_SIZE)
        
        // Response ID
        buffer.putInt(responseIdBytes.size)
        buffer.put(responseIdBytes)
        
        // Challenge ID
        buffer.putInt(challengeIdBytes.size)
        buffer.put(challengeIdBytes)
        
        // Responder peer ID
        buffer.putInt(responderBytes.size)
        buffer.put(responderBytes)
        
        // Response data
        buffer.putInt(response.response.size)
        buffer.put(response.response)
        
        // Public key
        buffer.putInt(response.publicKey.size)
        buffer.put(response.publicKey)
        
        // Signature
        buffer.putInt(response.signature.size)
        buffer.put(response.signature)
        
        // Timestamp
        buffer.putLong(response.timestamp)
        
        return buffer.array()
    }
    
    /**
     * Deserialize authentication response
     */
    fun deserializeResponse(data: ByteArray): PeerAuthentication.AuthResponse {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        
        // Verify header
        val version = buffer.get()
        val type = buffer.get()
        val length = buffer.getInt()
        
        if (version != PROTOCOL_VERSION || type != TYPE_AUTH_RESPONSE) {
            throw IllegalArgumentException("Invalid response message format")
        }
        
        // Read response ID
        val responseIdLength = buffer.getInt()
        val responseIdBytes = ByteArray(responseIdLength)
        buffer.get(responseIdBytes)
        val responseId = String(responseIdBytes, Charsets.UTF_8)
        
        // Read challenge ID
        val challengeIdLength = buffer.getInt()
        val challengeIdBytes = ByteArray(challengeIdLength)
        buffer.get(challengeIdBytes)
        val challengeId = String(challengeIdBytes, Charsets.UTF_8)
        
        // Read responder peer ID
        val responderLength = buffer.getInt()
        val responderBytes = ByteArray(responderLength)
        buffer.get(responderBytes)
        val responderPeerId = String(responderBytes, Charsets.UTF_8)
        
        // Read response data
        val responseLength = buffer.getInt()
        val response = ByteArray(responseLength)
        buffer.get(response)
        
        // Read public key
        val publicKeyLength = buffer.getInt()
        val publicKey = ByteArray(publicKeyLength)
        buffer.get(publicKey)
        
        // Read signature
        val signatureLength = buffer.getInt()
        val signature = ByteArray(signatureLength)
        buffer.get(signature)
        
        // Read timestamp
        val timestamp = buffer.getLong()
        
        return PeerAuthentication.AuthResponse(
            responseId = responseId,
            challengeId = challengeId,
            responderPeerId = responderPeerId,
            response = response,
            publicKey = publicKey,
            signature = signature,
            timestamp = timestamp
        )
    }
    
    /**
     * Serialize public key exchange
     */
    fun serializePublicKeyExchange(exchange: PeerAuthentication.PublicKeyExchange): ByteArray {
        val peerIdBytes = exchange.peerId.toByteArray(Charsets.UTF_8)
        
        val totalSize = HEADER_SIZE + 
                       4 + peerIdBytes.size + // peer ID with length
                       4 + exchange.publicKey.size + // public key with length
                       8 + // timestamp
                       4 + exchange.signature.size // signature with length
        
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        // Header
        buffer.put(PROTOCOL_VERSION)
        buffer.put(TYPE_PUBLIC_KEY_EXCHANGE)
        buffer.putInt(totalSize - HEADER_SIZE)
        
        // Peer ID
        buffer.putInt(peerIdBytes.size)
        buffer.put(peerIdBytes)
        
        // Public key
        buffer.putInt(exchange.publicKey.size)
        buffer.put(exchange.publicKey)
        
        // Timestamp
        buffer.putLong(exchange.timestamp)
        
        // Signature
        buffer.putInt(exchange.signature.size)
        buffer.put(exchange.signature)
        
        return buffer.array()
    }
    
    /**
     * Deserialize public key exchange
     */
    fun deserializePublicKeyExchange(data: ByteArray): PeerAuthentication.PublicKeyExchange {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        
        // Verify header
        val version = buffer.get()
        val type = buffer.get()
        val length = buffer.getInt()
        
        if (version != PROTOCOL_VERSION || type != TYPE_PUBLIC_KEY_EXCHANGE) {
            throw IllegalArgumentException("Invalid public key exchange message format")
        }
        
        // Read peer ID
        val peerIdLength = buffer.getInt()
        val peerIdBytes = ByteArray(peerIdLength)
        buffer.get(peerIdBytes)
        val peerId = String(peerIdBytes, Charsets.UTF_8)
        
        // Read public key
        val publicKeyLength = buffer.getInt()
        val publicKey = ByteArray(publicKeyLength)
        buffer.get(publicKey)
        
        // Read timestamp
        val timestamp = buffer.getLong()
        
        // Read signature
        val signatureLength = buffer.getInt()
        val signature = ByteArray(signatureLength)
        buffer.get(signature)
        
        return PeerAuthentication.PublicKeyExchange(
            peerId = peerId,
            publicKey = publicKey,
            timestamp = timestamp,
            signature = signature
        )
    }
    
    /**
     * Serialize authentication success message
     */
    fun serializeAuthSuccess(sessionId: String): ByteArray {
        val sessionIdBytes = sessionId.toByteArray(Charsets.UTF_8)
        
        val totalSize = HEADER_SIZE + 4 + sessionIdBytes.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        // Header
        buffer.put(PROTOCOL_VERSION)
        buffer.put(TYPE_AUTH_SUCCESS)
        buffer.putInt(totalSize - HEADER_SIZE)
        
        // Session ID
        buffer.putInt(sessionIdBytes.size)
        buffer.put(sessionIdBytes)
        
        return buffer.array()
    }
    
    /**
     * Deserialize authentication success message
     */
    fun deserializeAuthSuccess(data: ByteArray): String {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        
        // Verify header
        val version = buffer.get()
        val type = buffer.get()
        val length = buffer.getInt()
        
        if (version != PROTOCOL_VERSION || type != TYPE_AUTH_SUCCESS) {
            throw IllegalArgumentException("Invalid auth success message format")
        }
        
        // Read session ID
        val sessionIdLength = buffer.getInt()
        val sessionIdBytes = ByteArray(sessionIdLength)
        buffer.get(sessionIdBytes)
        
        return String(sessionIdBytes, Charsets.UTF_8)
    }
    
    /**
     * Serialize authentication failure message
     */
    fun serializeAuthFailure(error: String): ByteArray {
        val errorBytes = error.toByteArray(Charsets.UTF_8)
        
        val totalSize = HEADER_SIZE + 4 + errorBytes.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        // Header
        buffer.put(PROTOCOL_VERSION)
        buffer.put(TYPE_AUTH_FAILURE)
        buffer.putInt(totalSize - HEADER_SIZE)
        
        // Error message
        buffer.putInt(errorBytes.size)
        buffer.put(errorBytes)
        
        return buffer.array()
    }
    
    /**
     * Deserialize authentication failure message
     */
    fun deserializeAuthFailure(data: ByteArray): String {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        
        // Verify header
        val version = buffer.get()
        val type = buffer.get()
        val length = buffer.getInt()
        
        if (version != PROTOCOL_VERSION || type != TYPE_AUTH_FAILURE) {
            throw IllegalArgumentException("Invalid auth failure message format")
        }
        
        // Read error message
        val errorLength = buffer.getInt()
        val errorBytes = ByteArray(errorLength)
        buffer.get(errorBytes)
        
        return String(errorBytes, Charsets.UTF_8)
    }
    
    /**
     * Get message type from raw data
     */
    fun getMessageType(data: ByteArray): Byte? {
        if (data.size < 2) return null
        return data[1]
    }
    
    /**
     * Validate message header
     */
    fun isValidMessage(data: ByteArray): Boolean {
        if (data.size < HEADER_SIZE) return false
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get()
        val type = buffer.get()
        val length = buffer.getInt()
        
        return version == PROTOCOL_VERSION && 
               type in arrayOf(TYPE_AUTH_CHALLENGE, TYPE_AUTH_RESPONSE, 
                             TYPE_AUTH_SUCCESS, TYPE_AUTH_FAILURE, TYPE_PUBLIC_KEY_EXCHANGE) &&
               length > 0 && length <= (data.size - HEADER_SIZE)
    }
}