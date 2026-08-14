package com.coachplanner.api.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    fun findAllByUserIdAndRevokedAtIsNull(userId: UUID): List<RefreshToken>
}
