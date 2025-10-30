package com.bigshotsoftware.thenet.p2p.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages authentication sessions for peers in TheNet P2P network.
 * 
 * This class handles:
 * - Session lifecycle management (creation, renewal, expiration)
 * - Session security policies and timeouts
 * - Automatic cleanup of expired sessions
 * - Session state persistence and recovery
 */
class AuthSessionManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
) {

    /**
     * Authentication session data
     */
    data class AuthSession(
        val sessionId: String,
        val peerId: String,
        val publicKey: ByteArray,
        val createdAt: Long,
        val lastActiveAt: Long,
        val expiresAt: Long,
        val sessionData: Map<String, Any> = emptyMap(),
        val securityLevel: SecurityLevel = SecurityLevel.STANDARD
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AuthSession) return false
            return sessionId == other.sessionId &&
                peerId == other.peerId &&
                publicKey.contentEquals(other.publicKey) &&
                createdAt == other.createdAt
        }

        override fun hashCode(): Int {
            var result = sessionId.hashCode()
            result = 31 * result + peerId.hashCode()
            result = 31 * result + publicKey.contentHashCode()
            result = 31 * result + createdAt.hashCode()
            return result
        }
    }

    /**
     * Security levels for authentication sessions
     */
    enum class SecurityLevel(val timeoutMs: Long, val maxIdleMs: Long) {
        BASIC(1800000L, 300000L),     // 30 min session, 5 min idle
        STANDARD(3600000L, 600000L),  // 1 hour session, 10 min idle
        HIGH(7200000L, 900000L),      // 2 hour session, 15 min idle
        CRITICAL(14400000L, 1800000L) // 4 hour session, 30 min idle
    }

    /**
     * Session statistics
     */
    data class SessionStats(
        val totalSessions: Int,
        val activeSessions: Int,
        val expiredSessions: Int,
        val averageSessionDuration: Long,
        val totalSessionsCreated: Long,
        val totalSessionsExpired: Long
    )

    // Internal state
    private val sessions = ConcurrentHashMap<String, AuthSession>()
    private val sessionsByPeer = ConcurrentHashMap<String, MutableSet<String>>()
    private val _sessionStats = MutableStateFlow(SessionStats(0, 0, 0, 0L, 0L, 0L))
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    // Statistics tracking
    private var totalSessionsCreated = 0L
    private var totalSessionsExpired = 0L
    private var totalSessionDuration = 0L

    // Cleanup job
    private val cleanupJob: Job

    init {
        // Start periodic cleanup of expired sessions
        cleanupJob = scope.launch {
            while (isActive) {
                delay(60000) // Clean up every minute
                cleanupExpiredSessions()
                updateStats()
            }
        }
    }

    /**
     * Create a new authentication session for a peer
     */
    suspend fun createSession(
        peerId: String,
        publicKey: ByteArray,
        securityLevel: SecurityLevel = SecurityLevel.STANDARD,
        sessionData: Map<String, Any> = emptyMap()
    ): AuthSession {
        val currentTime = System.currentTimeMillis()
        val sessionId = AuthCrypto.generateSessionId()
        
        val session = AuthSession(
            sessionId = sessionId,
            peerId = peerId,
            publicKey = publicKey,
            createdAt = currentTime,
            lastActiveAt = currentTime,
            expiresAt = currentTime + securityLevel.timeoutMs,
            sessionData = sessionData,
            securityLevel = securityLevel
        )

        // Store session
        sessions[sessionId] = session
        sessionsByPeer.computeIfAbsent(peerId) { mutableSetOf() }.add(sessionId)
        
        totalSessionsCreated++
        updateStats()
        
        return session
    }

    /**
     * Get an active session by session ID
     */
    suspend fun getSession(sessionId: String): AuthSession? {
        val session = sessions[sessionId] ?: return null
        
        if (!isSessionValid(session)) {
            expireSession(sessionId)
            return null
        }
        
        return session
    }

    /**
     * Get active session for a peer
     */
    suspend fun getSessionByPeer(peerId: String): AuthSession? {
        val sessionIds = sessionsByPeer[peerId] ?: return null
        
        // Find the most recent valid session
        var mostRecentSession: AuthSession? = null
        val sessionsToRemove = mutableSetOf<String>()
        
        for (sessionId in sessionIds) {
            val session = sessions[sessionId]
            if (session != null && isSessionValid(session)) {
                if (mostRecentSession == null || session.lastActiveAt > mostRecentSession.lastActiveAt) {
                    mostRecentSession = session
                }
            } else {
                sessionsToRemove.add(sessionId)
            }
        }
        
        // Clean up invalid sessions
        sessionsToRemove.forEach { sessionId ->
            sessions.remove(sessionId)
            sessionIds.remove(sessionId)
        }
        
        return mostRecentSession
    }

    /**
     * Update session last active time
     */
    suspend fun touchSession(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        
        if (!isSessionValid(session)) {
            expireSession(sessionId)
            return false
        }
        
        val updatedSession = session.copy(lastActiveAt = System.currentTimeMillis())
        sessions[sessionId] = updatedSession
        
        return true
    }

    /**
     * Renew a session (extend expiration time)
     */
    suspend fun renewSession(sessionId: String, additionalTimeMs: Long? = null): Boolean {
        val session = sessions[sessionId] ?: return false
        
        if (!isSessionValid(session)) {
            expireSession(sessionId)
            return false
        }
        
        val extensionTime = additionalTimeMs ?: session.securityLevel.timeoutMs
        val currentTime = System.currentTimeMillis()
        val updatedSession = session.copy(
            lastActiveAt = currentTime,
            expiresAt = currentTime + extensionTime
        )
        
        sessions[sessionId] = updatedSession
        return true
    }

    /**
     * Expire a session manually
     */
    suspend fun expireSession(sessionId: String): Boolean {
        val session = sessions.remove(sessionId) ?: return false
        
        // Remove from peer mapping
        sessionsByPeer[session.peerId]?.remove(sessionId)
        if (sessionsByPeer[session.peerId]?.isEmpty() == true) {
            sessionsByPeer.remove(session.peerId)
        }
        
        totalSessionsExpired++
        totalSessionDuration += (System.currentTimeMillis() - session.createdAt)
        updateStats()
        
        return true
    }

    /**
     * Expire all sessions for a peer
     */
    suspend fun expireAllSessionsForPeer(peerId: String): Int {
        val sessionIds = sessionsByPeer[peerId] ?: return 0
        var expiredCount = 0
        
        for (sessionId in sessionIds.toList()) {
            if (expireSession(sessionId)) {
                expiredCount++
            }
        }
        
        return expiredCount
    }

    /**
     * Check if a session is valid (not expired and within idle timeout)
     */
    fun isSessionValid(session: AuthSession): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Check absolute expiration
        if (currentTime > session.expiresAt) {
            return false
        }
        
        // Check idle timeout
        val idleTime = currentTime - session.lastActiveAt
        if (idleTime > session.securityLevel.maxIdleMs) {
            return false
        }
        
        return true
    }

    /**
     * Get all active sessions
     */
    suspend fun getActiveSessions(): List<AuthSession> {
        return sessions.values.filter { isSessionValid(it) }
    }

    /**
     * Get all sessions for a peer
     */
    suspend fun getSessionsForPeer(peerId: String): List<AuthSession> {
        val sessionIds = sessionsByPeer[peerId] ?: return emptyList()
        return sessionIds.mapNotNull { sessions[it] }.filter { isSessionValid(it) }
    }

    /**
     * Clean up expired sessions
     */
    suspend fun cleanupExpiredSessions(): Int {
        var cleanedUp = 0
        val currentTime = System.currentTimeMillis()
        
        val sessionsToRemove = mutableListOf<String>()
        
        for ((sessionId, session) in sessions) {
            if (!isSessionValid(session)) {
                sessionsToRemove.add(sessionId)
            }
        }
        
        for (sessionId in sessionsToRemove) {
            if (expireSession(sessionId)) {
                cleanedUp++
            }
        }
        
        return cleanedUp
    }

    /**
     * Update session data
     */
    suspend fun updateSessionData(sessionId: String, data: Map<String, Any>): Boolean {
        val session = sessions[sessionId] ?: return false
        
        if (!isSessionValid(session)) {
            expireSession(sessionId)
            return false
        }
        
        val updatedSession = session.copy(
            sessionData = session.sessionData + data,
            lastActiveAt = System.currentTimeMillis()
        )
        
        sessions[sessionId] = updatedSession
        return true
    }

    /**
     * Get session data
     */
    suspend fun getSessionData(sessionId: String, key: String): Any? {
        val session = getSession(sessionId) ?: return null
        return session.sessionData[key]
    }

    /**
     * Update statistics
     */
    private fun updateStats() {
        val activeSessions = sessions.values.count { isSessionValid(it) }
        val totalSessions = sessions.size
        val expiredSessions = totalSessions - activeSessions
        val avgDuration = if (totalSessionsExpired > 0) {
            totalSessionDuration / totalSessionsExpired
        } else {
            0L
        }
        
        _sessionStats.value = SessionStats(
            totalSessions = totalSessions,
            activeSessions = activeSessions,
            expiredSessions = expiredSessions,
            averageSessionDuration = avgDuration,
            totalSessionsCreated = totalSessionsCreated,
            totalSessionsExpired = totalSessionsExpired
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        cleanupJob.cancel()
        sessions.clear()
        sessionsByPeer.clear()
    }
}