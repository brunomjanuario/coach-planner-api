package com.coachplanner.api.reference

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

/** tasks.md T9: round-trips Competition and Opponent — the same shape, deliberately one test class (AD-104/AD-016). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class)
class ReferenceListPersistenceTest @Autowired constructor(
    private val competitionRepository: CompetitionRepository,
    private val opponentRepository: OpponentRepository,
    private val userRepository: UserRepository,
    private val entityManager: EntityManager,
) {

    private fun persistedOwner(): User =
        userRepository.saveAndFlush(User(email = "coach-${newId()}@example.com", name = "Coach", passwordHash = "hash"))

    @Test
    fun `a competition round-trips its owner and name`() {
        val owner = persistedOwner()
        val saved = competitionRepository.saveAndFlush(Competition(ownerId = owner.id, name = "District League"))
        entityManager.clear()

        val reloaded = competitionRepository.findById(saved.id).orElseThrow()
        assertEquals(owner.id, reloaded.ownerId)
        assertEquals("District League", reloaded.name)
    }

    @Test
    fun `an opponent round-trips its owner and name`() {
        val owner = persistedOwner()
        val saved = opponentRepository.saveAndFlush(Opponent(ownerId = owner.id, name = "Benfica"))
        entityManager.clear()

        val reloaded = opponentRepository.findById(saved.id).orElseThrow()
        assertEquals(owner.id, reloaded.ownerId)
        assertEquals("Benfica", reloaded.name)
    }
}
