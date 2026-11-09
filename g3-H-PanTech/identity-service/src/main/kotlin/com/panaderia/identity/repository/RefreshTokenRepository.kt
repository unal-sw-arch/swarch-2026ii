package com.panaderia.identity.repository

import com.panaderia.identity.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun findByUser_IdAndRevokedAtIsNull(userId: UUID): List<RefreshToken>

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    fun deleteExpired(cutoff: Instant): Int
}
