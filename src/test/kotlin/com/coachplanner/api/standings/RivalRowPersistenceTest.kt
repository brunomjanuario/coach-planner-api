package com.coachplanner.api.standings

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
import kotlin.test.assertEquals

/** tasks.md T8: round-trips RivalRow against real Postgres 18 — no points/goal_difference column, ever (AD-108). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class RivalRowPersistenceTest @Autowired constructor(
    private val rivalRowRepository: RivalRowRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    @Test
    fun `a rival row round-trips every recorded figure`() {
        val owner = persistedOwner()
        val saved = rivalRowRepository.saveAndFlush(
            RivalRow(
                ownerId = owner.id,
                name = "Sporting",
                played = 10,
                won = 6,
                drawn = 2,
                lost = 2,
                goalsFor = 18,
                goalsAgainst = 9,
            ),
        )
        entityManager.clear()

        val reloaded = rivalRowRepository.findById(saved.id).orElseThrow()
        assertEquals("Sporting", reloaded.name)
        assertEquals(10, reloaded.played)
        assertEquals(6, reloaded.won)
        assertEquals(2, reloaded.drawn)
        assertEquals(2, reloaded.lost)
        assertEquals(18, reloaded.goalsFor)
        assertEquals(9, reloaded.goalsAgainst)
        assertEquals(0, reloaded.version)
    }

    @Test
    fun `an all-zero rival row (not yet played) is valid`() {
        val owner = persistedOwner()
        val saved = rivalRowRepository.saveAndFlush(
            RivalRow(ownerId = owner.id, name = "Benfica", played = 0, won = 0, drawn = 0, lost = 0, goalsFor = 0, goalsAgainst = 0),
        )
        entityManager.clear()

        val reloaded = rivalRowRepository.findById(saved.id).orElseThrow()
        assertEquals(0, reloaded.played)
    }
}
