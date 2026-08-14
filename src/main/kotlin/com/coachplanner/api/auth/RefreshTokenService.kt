package com.coachplanner.api.auth

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

const val REFRESH_TOKEN_TTL_DAYS = 30L

/**
 * Refresh tokens are opaque random strings, not JWTs — the schema stores
 * only a SHA-256 hash (design.md), which is only meaningful for a token
 * the server can look up and revoke by hash. Rotation and reuse detection
 * (T17) build on top of issue().
 */
@Service
class RefreshTokenService(private val refreshTokenRepository: RefreshTokenRepository) {

    private val random = SecureRandom()

    /** Persists the token's hash and returns the raw token — the only time it's ever visible in plaintext. */
    fun issue(user: User): String {
        val raw = generateRawToken()
        val token = RefreshToken(
            userId = user.id,
            tokenHash = sha256(raw),
            expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS),
        )
        refreshTokenRepository.save(token)
        return raw
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
