package com.coachplanner.api.auth

import com.coachplanner.api.common.UnauthorizedException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

const val REFRESH_TOKEN_TTL_DAYS = 30L
private const val INVALID_REFRESH_TOKEN = "invalid-refresh-token"
private const val INVALID_REFRESH_TOKEN_MESSAGE = "Refresh token is invalid, expired, or has already been used."

/**
 * Refresh tokens are opaque random strings, not JWTs — the schema stores
 * only a SHA-256 hash (design.md), which is only meaningful for a token
 * the server can look up and revoke by hash.
 */
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    transactionManager: PlatformTransactionManager,
) {

    private val random = SecureRandom()

    /**
     * Reuse-detection's mass revocation must survive even though rotate()
     * goes on to throw and roll back its own transaction — a REQUIRES_NEW
     * template commits it independently rather than relying on
     * @Transactional(propagation=...) on a private method, which Spring's
     * proxy-based AOP can't intercept on self-invocation anyway.
     */
    private val newTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

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

    /**
     * Rotation: the presented token is revoked and the caller (AuthService)
     * issues a fresh pair. Reuse detection: presenting a token that's
     * *already* revoked means it was replayed — either a client retry bug
     * or a stolen token — and the correct response to either is the same:
     * revoke every other still-valid token for that user, since we can no
     * longer trust which of this user's tokens are in honest hands.
     */
    @Transactional
    fun rotate(rawToken: String): UUID {
        val token = refreshTokenRepository.findByTokenHash(sha256(rawToken))
            ?: throw UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE, type = INVALID_REFRESH_TOKEN)

        if (token.revokedAt != null) {
            newTransaction.executeWithoutResult { revokeAllForUser(token.userId) }
            throw UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE, type = INVALID_REFRESH_TOKEN)
        }

        if (token.expiresAt.isBefore(Instant.now())) {
            throw UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE, type = INVALID_REFRESH_TOKEN)
        }

        token.revokedAt = Instant.now()
        refreshTokenRepository.save(token)
        return token.userId
    }

    /** Also called directly by AuthService.logout (AC AUTH-P3.1) — not just reuse detection's own retry path. */
    @Transactional
    fun revokeAllForUser(userId: UUID) {
        val now = Instant.now()
        val activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
        activeTokens.forEach { it.revokedAt = now }
        refreshTokenRepository.saveAll(activeTokens)
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
