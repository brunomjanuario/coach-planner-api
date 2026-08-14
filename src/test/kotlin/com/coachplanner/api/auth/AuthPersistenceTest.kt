package com.coachplanner.api.auth

import com.coachplanner.api.TestcontainersConfiguration
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** tasks.md T7: round-trips User and RefreshToken against real Postgres 18. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class AuthPersistenceTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val entityManager: EntityManager,
) {

    @Test
    fun `a user round-trips with a version-managed row and a generated timestamp`() {
        val saved = userRepository.saveAndFlush(
            User(email = "coach@example.com", name = "Coach", passwordHash = "bcrypt-hash"),
        )
        entityManager.clear()

        val reloaded = userRepository.findById(saved.id).orElseThrow()
        assertEquals("coach@example.com", reloaded.email)
        assertEquals("Coach", reloaded.name)
        assertEquals("bcrypt-hash", reloaded.passwordHash)
        assertEquals(0, reloaded.version)
        assertNotNull(reloaded.createdAt)
    }

    @Test
    fun `a refresh token round-trips its user, hash, expiry and unset revocation`() {
        val user = userRepository.saveAndFlush(User(email = "coach2@example.com", name = "Coach", passwordHash = "hash"))
        val expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        val saved = refreshTokenRepository.saveAndFlush(
            RefreshToken(userId = user.id, tokenHash = "sha256-hash-value", expiresAt = expiresAt),
        )
        entityManager.clear()

        val reloaded = refreshTokenRepository.findById(saved.id).orElseThrow()
        assertEquals(user.id, reloaded.userId)
        assertEquals("sha256-hash-value", reloaded.tokenHash)
        assertEquals(expiresAt.truncatedTo(ChronoUnit.MICROS), reloaded.expiresAt.truncatedTo(ChronoUnit.MICROS))
        assertNull(reloaded.revokedAt)
    }

    @Test
    fun `a revoked refresh token round-trips its revocation timestamp`() {
        val user = userRepository.saveAndFlush(User(email = "coach3@example.com", name = "Coach", passwordHash = "hash"))
        val revokedAt = Instant.now()
        val saved = refreshTokenRepository.saveAndFlush(
            RefreshToken(
                userId = user.id,
                tokenHash = "another-hash",
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
                revokedAt = revokedAt,
            ),
        )
        entityManager.clear()

        val reloaded = refreshTokenRepository.findById(saved.id).orElseThrow()
        assertEquals(revokedAt.truncatedTo(ChronoUnit.MICROS), reloaded.revokedAt?.truncatedTo(ChronoUnit.MICROS))
    }
}
