package com.panaderia.identity.auth

import com.panaderia.identity.config.JwtProperties
import com.panaderia.identity.domain.RefreshToken
import com.panaderia.identity.domain.User
import com.panaderia.identity.repository.RefreshTokenRepository
import com.panaderia.identity.web.ApiException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Refresh tokens opacos de 7 días.
 * PostgreSQL conserva el hash para auditoría y Redis acelera la validación.
 * Si Redis no está disponible, la base de datos sigue siendo la fuente de verdad.
 */
@Service
class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val properties: JwtProperties,
    private val redis: ObjectProvider<StringRedisTemplate>,
) {
    private val log = LoggerFactory.getLogger(RefreshTokenService::class.java)
    private val random = SecureRandom()

    @Transactional
    fun issue(user: User): String {
        val userId = requireNotNull(user.id) { "El usuario debe estar persistido" }
        val raw = ByteArray(32).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val hash = hash(raw)
        val expiresAt = Instant.now().plusSeconds(properties.refreshTtlSeconds)
        repository.save(RefreshToken(user = user, tokenHash = hash, expiresAt = expiresAt))
        cachePut(hash, userId, properties.refreshTtlSeconds)
        return raw
    }

    @Transactional
    fun consume(raw: String): User {
        val hash = hash(raw)
        val token = repository.findByTokenHash(hash)
            ?: throw invalidRefreshToken()
        if (token.revokedAt != null) throw invalidRefreshToken()
        if (!token.expiresAt.isAfter(Instant.now())) {
            token.revokedAt = Instant.now()
            repository.save(token)
            cacheDelete(hash)
            throw invalidRefreshToken()
        }
        val user = token.user ?: throw invalidRefreshToken()
        if (!user.active) throw invalidRefreshToken()

        // Rotación: el token usado no se puede reutilizar.
        token.revokedAt = Instant.now()
        repository.save(token)
        cacheDelete(hash)
        return user
    }

    @Transactional
    fun revoke(raw: String?) {
        if (raw.isNullOrBlank()) return
        val hash = hash(raw.trim())
        val token = repository.findByTokenHash(hash) ?: return
        if (token.revokedAt == null) {
            token.revokedAt = Instant.now()
            repository.save(token)
        }
        cacheDelete(hash)
    }

    @Transactional
    fun revokeAll(userId: UUID) {
        val active = repository.findByUser_IdAndRevokedAtIsNull(userId)
        val now = Instant.now()
        active.forEach { token ->
            token.revokedAt = now
            cacheDelete(token.tokenHash)
        }
        repository.saveAll(active)
    }

    private fun invalidRefreshToken(): ApiException = ApiException(
        HttpStatus.UNAUTHORIZED,
        "INVALID_REFRESH_TOKEN",
        "El refresh token no es válido o está vencido",
    )

    private fun hash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cachePut(hash: String, userId: UUID, ttlSeconds: Long) {
        try {
            redis.ifAvailable?.opsForValue()?.set(key(hash), userId.toString(), Duration.ofSeconds(ttlSeconds))
        } catch (ex: Exception) {
            log.warn("Redis no disponible para cachear el refresh token: {}", ex.message)
        }
    }

    private fun cacheDelete(hash: String) {
        try {
            redis.ifAvailable?.delete(key(hash))
        } catch (ex: Exception) {
            log.warn("Redis no disponible para revocar el refresh token: {}", ex.message)
        }
    }

    private fun key(hash: String): String = "identity:refresh:$hash"
}
