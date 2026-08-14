package com.coachplanner.api.game

import com.coachplanner.api.TestcontainersConfiguration
import com.coachplanner.api.auth.User
import com.coachplanner.api.auth.UserRepository
import com.coachplanner.api.common.newId
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** tasks.md T8: round-trips Game against real Postgres 18. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class GamePersistenceTest @Autowired constructor(
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    @Test
    fun `a scheduled game round-trips with both scores null`() {
        val owner = persistedOwner()
        val saved = gameRepository.saveAndFlush(
            Game(ownerId = owner.id, opponent = "Benfica", date = Instant.parse("2030-01-01T15:00:00Z"), isHome = true),
        )
        entityManager.clear()

        val reloaded = gameRepository.findById(saved.id).orElseThrow()
        assertNull(reloaded.usScore)
        assertNull(reloaded.themScore)
        assertEquals("Benfica", reloaded.opponent)
        assertNull(reloaded.competition)
    }

    @Test
    fun `a played game round-trips a 0-0 result, distinct from an unplayed one`() {
        val owner = persistedOwner()
        val saved = gameRepository.saveAndFlush(
            Game(
                ownerId = owner.id,
                opponent = "Sporting",
                competition = "District League",
                date = Instant.parse("2023-05-01T15:00:00Z"),
                isHome = false,
                usScore = 0,
                themScore = 0,
            ),
        )
        entityManager.clear()

        val reloaded = gameRepository.findById(saved.id).orElseThrow()
        assertEquals(0, reloaded.usScore)
        assertEquals(0, reloaded.themScore)
        assertEquals("District League", reloaded.competition)
    }
}
